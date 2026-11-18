#!/usr/bin/env python3
"""Offline golden UDF/PDF custody gate; not a renderer equivalence test.

The owner supplies rights-cleared files and a reviewed manifest. This gate
binds those exact bytes to a declared reference, without reading UYAP data
from a network or silently approving a release.
"""
import argparse
import hashlib
import json
import os
import sys
import stat
from pathlib import Path

MAX_MANIFEST = 64 * 1024
MAX_UDF = 64 * 1024 * 1024
MAX_PDF = 256 * 1024 * 1024


def digest_file(path, limit, required_prefix=None):
    # Bind the hash to one opened file descriptor, not a path that may be
    # swapped between stat() and open(). Never read beyond the size limit.
    flags = os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0)
    if path.is_symlink():
        raise ValueError("input must not be a symlink")
    fd = os.open(path, flags)
    try:
        before = os.fstat(fd)
        if not stat.S_ISREG(before.st_mode):
            raise ValueError("input must be a regular file")
        size = before.st_size
        if size <= 0 or size > limit:
            raise ValueError("input size outside accepted bounds")
        digest = hashlib.sha256()
        read_bytes = 0
        prefix_checked = required_prefix is None
        with os.fdopen(fd, "rb", closefd=False) as stream:
            while read_bytes < size:
                chunk = stream.read(min(1024 * 1024, size - read_bytes))
                if not chunk:
                    raise ValueError("input truncated during hashing")
                if not prefix_checked:
                    if not chunk.startswith(required_prefix):
                        raise ValueError("reference PDF signature missing")
                    prefix_checked = True
                read_bytes += len(chunk)
                digest.update(chunk)
            if stream.read(1):
                raise ValueError("input grew during hashing")
        after = os.fstat(fd)
        if (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns, before.st_ctime_ns) != (
                after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns, after.st_ctime_ns):
            raise ValueError("input changed during hashing")
        return digest.hexdigest(), size
    finally:
        os.close(fd)


def read_manifest(path):
    # Open once: path.stat() followed by read_text() could read a replaced,
    # unbounded or non-regular file. Keep the same descriptor for both checks.
    flags = os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0)
    if path.is_symlink():
        raise ValueError("manifest must not be a symlink")
    fd = os.open(path, flags)
    try:
        before = os.fstat(fd)
        if not stat.S_ISREG(before.st_mode) or before.st_size <= 0 or before.st_size > MAX_MANIFEST:
            raise ValueError("manifest must be a nonempty bounded regular file")
        with os.fdopen(fd, "rb", closefd=False) as stream:
            data = stream.read(MAX_MANIFEST + 1)
        after = os.fstat(fd)
        if len(data) != before.st_size or (
            before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns, before.st_ctime_ns
        ) != (
            after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns, after.st_ctime_ns
        ):
            raise ValueError("manifest changed during bounded read")
        # Reject ambiguous duplicate JSON keys at every nesting level. Different
        # parsers may otherwise disagree about rights or reference approvals.
        def unique_object(pairs):
            result = {}
            for key, value in pairs:
                if key in result:
                    raise ValueError("duplicate manifest JSON key: " + key)
                result[key] = value
            return result
        manifest = json.loads(data.decode("utf-8"), object_pairs_hook=unique_object)
        if not isinstance(manifest, dict):
            raise ValueError("manifest root must be an object")
        return manifest
    finally:
        os.close(fd)


def verify(manifest, udf, pdf):
    if set(manifest) != {"schema", "rights", "udf_sha256", "pdf_sha256", "reference"}:
        raise ValueError("unexpected or missing manifest fields")
    if manifest["schema"] != "embasic-offline-golden-v1":
        raise ValueError("unsupported manifest schema")
    rights = manifest["rights"]
    if not isinstance(rights, dict) or set(rights) != {"owner_reviewed", "evidence_id"}:
        raise ValueError("rights evidence missing or malformed")
    if rights["owner_reviewed"] is not True or not isinstance(rights["evidence_id"], str) or not rights["evidence_id"].strip():
        raise ValueError("rights clearance not explicitly evidenced")
    reference = manifest["reference"]
    if not isinstance(reference, dict) or set(reference) != {"origin", "review_id"}:
        raise ValueError("reference evidence missing")
    if reference["origin"] not in ("rights-cleared-offline", "original-synthetic"):
        raise ValueError("unapproved reference origin")
    if not isinstance(reference["review_id"], str) or not reference["review_id"].strip():
        raise ValueError("reference review ID missing")
    actual_udf, udf_size = digest_file(udf, MAX_UDF)
    actual_pdf, pdf_size = digest_file(pdf, MAX_PDF, required_prefix=b"%PDF-")
    for key, actual in (("udf_sha256", actual_udf), ("pdf_sha256", actual_pdf)):
        expected = manifest[key]
        if not isinstance(expected, str) or len(expected) != 64 or any(c not in "0123456789abcdef" for c in expected):
            raise ValueError("invalid SHA-256 declaration: " + key)
        if expected != actual:
            raise ValueError("golden byte mismatch: " + key)
    # A hash match proves file identity only, not UDF compatibility or visual parity.
    return {"udf_sha256": actual_udf, "udf_bytes": udf_size,
            "pdf_sha256": actual_pdf, "pdf_bytes": pdf_size,
            "rights_evidence_id": rights["evidence_id"],
            "reference_review_id": reference["review_id"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--udf", type=Path, required=True)
    parser.add_argument("--reference-pdf", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    # Never allow a report destination to replace evidence or traverse a symlink.
    # resolve() also catches aliases such as ./evidence/../evidence/file.udf.
    evidence = [args.manifest, args.udf, args.reference_pdf]
    output = args.report
    if output.is_symlink() or output.resolve() in {item.resolve() for item in evidence}:
        print("NO_GO: report path aliases evidence or is a symlink", file=sys.stderr)
        return 1
    report = {"schema": 1, "status": "NO_GO", "native_uyap": "NOT_VERIFIED",
              "visual_equivalence": "NOT_VERIFIED", "release_approval": "NOT_VERIFIED"}
    try:
        manifest = read_manifest(args.manifest)
        report.update(verify(manifest, args.udf, args.reference_pdf))
        report["custody"] = "BYTE_IDENTITY_VERIFIED_ONLY"
        # A verified custody result must not retain the initial failure status.
        # This does NOT approve rendering parity, native UYAP, or publication.
        report["status"] = "CUSTODY_VERIFIED_ONLY"
    except (OSError, ValueError, TypeError, UnicodeError, json.JSONDecodeError) as exc:
        report["error"] = str(exc)
    # A symlink in ANY parent component can redirect the report outside the
    # intended offline evidence directory. Checking only the immediate parent
    # misses e.g. evidence/link/subdir/report.json. Reject before mkdir/open.
    parent = args.report.parent.absolute()
    for ancestor in (parent, *parent.parents):
        if ancestor.is_symlink():
            print("NO_GO: report ancestor is a symlink", file=sys.stderr)
            return 1
    args.report.parent.mkdir(parents=True, exist_ok=True)
    # Exclusive creation prevents clobbering an existing report or evidence file.
    # O_NOFOLLOW is available on POSIX; O_EXCL also rejects existing symlinks.
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0)
    try:
        fd = os.open(args.report, flags, 0o600)
    except OSError as exc:
        print("NO_GO: cannot exclusively create report: " + str(exc), file=sys.stderr)
        return 1
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            stream.write(json.dumps(report, indent=2, sort_keys=True) + "\n")
    except OSError as exc:
        print("NO_GO: report write failed: " + str(exc), file=sys.stderr)
        return 1
    print(report.get("custody", "NO_GO") + ": " + str(args.report))
    return 0 if report.get("custody") == "BYTE_IDENTITY_VERIFIED_ONLY" else 1


if __name__ == "__main__":
    sys.exit(main())
