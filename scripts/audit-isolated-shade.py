#!/usr/bin/env python3
"""Audit a synthetic Maven Shade output against actual resolved upstream JARs.

The input application is an independent public synthetic probe, NEVER private
Embasic source. Passing checks prove selected class-byte preservation only.
"""
from __future__ import annotations
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import zipfile

DEPENDENCIES = {
    ("com.lowagie", "itext", "2.1.7"): "com/lowagie/text/Document.class",
    ("bouncycastle", "bcmail-jdk14", "138"): "org/bouncycastle/mail/smime/SMIMEEnvelopedGenerator.class",
    ("bouncycastle", "bcprov-jdk14", "138"): "org/bouncycastle/jce/provider/BouncyCastleProvider.class",
    ("org.bouncycastle", "bctsp-jdk14", "1.38"): "org/bouncycastle/tsp/TimeStampToken.class",
    ("org.bouncycastle", "bcprov-jdk14", "1.38"): "org/bouncycastle/jce/provider/BouncyCastleProvider.class",
    ("org.bouncycastle", "bcmail-jdk14", "1.38"): "org/bouncycastle/mail/smime/SMIMEEnvelopedGenerator.class",
}
MAX_JAR = 50 * 1024 * 1024
MAX_EXPANDED = 250 * 1024 * 1024
MAX_MEMBER = 8 * 1024 * 1024
AUDIT_MAIN = "com/embasic/audit/IsolatedProbe.class"
FORBIDDEN_APP = "com/embasic/udf2pdf/App.class"

class AuditFailure(Exception):
    pass

def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def checked_zip(path: Path) -> zipfile.ZipFile:
    if path.is_symlink() or not path.is_file():
        raise AuditFailure("missing_or_symlink_jar")
    if not 0 < path.stat().st_size <= MAX_JAR:
        raise AuditFailure("jar_file_size_limit")
    try:
        zf = zipfile.ZipFile(path)
        members = zf.infolist()
        if len(members) > 50000 or not members:
            raise AuditFailure("archive_entry_count")
        seen = set()
        total = 0
        for m in members:
            n = m.filename
            if n in seen:
                raise AuditFailure("duplicate_archive_member")
            seen.add(n)
            if n.startswith("/") or "\\\\" in n or any(p in {".", ".."} for p in n.split("/")):
                raise AuditFailure("unsafe_archive_path")
            if m.file_size > MAX_MEMBER or total + m.file_size > MAX_EXPANDED:
                raise AuditFailure("archive_expansion_limit")
            total += m.file_size
        return zf
    except (ValueError, OSError, zipfile.BadZipFile) as exc:
        raise AuditFailure("invalid_archive") from exc

def member_digest(zf: zipfile.ZipFile, name: str) -> str:
    if name not in zf.namelist():
        raise AuditFailure("required_class_missing")
    try:
        return digest(zf.read(name))
    except (OSError, ValueError, RuntimeError, zipfile.BadZipFile) as exc:
        raise AuditFailure("required_class_unreadable") from exc

def check_shaded(merged: Path, upstream: dict[tuple[str, str, str], Path]) -> dict:
    if set(upstream) != set(DEPENDENCIES):
        raise AuditFailure("dependency_coordinates_incomplete")
    matched = {}
    with checked_zip(merged) as shade:
        names = set(shade.namelist())
        if AUDIT_MAIN not in names or FORBIDDEN_APP in names:
            raise AuditFailure("not_isolated_audit_artifact")
        for coordinate, class_name in DEPENDENCIES.items():
            with checked_zip(upstream[coordinate]) as original:
                expected = member_digest(original, class_name)
            actual = member_digest(shade, class_name)
            if actual != expected:
                raise AuditFailure("shaded_dependency_class_differs")
            matched[":".join(coordinate)] = expected
    for old, new in (
        (("bouncycastle", "bcprov-jdk14", "138"), ("org.bouncycastle", "bcprov-jdk14", "1.38")),
        (("bouncycastle", "bcmail-jdk14", "138"), ("org.bouncycastle", "bcmail-jdk14", "1.38")),
    ):
        if digest(upstream[old].read_bytes()) != digest(upstream[new].read_bytes()):
            raise AuditFailure("legacy_relocated_jar_bytes_differ")
    return {
        "status": "SELECTED_SHADE_CLASSES_MATCH_NOT_LICENSE_APPROVED",
        "shaded_jar_sha256": digest(merged.read_bytes()),
        "verified_components": sorted(matched),
        "selected_class_sha256": matched,
        "legacy_relocated_jar_pairs_identical": True,
        "cannot_prove": [
            "Full byte-for-byte JAR inclusion or absence of other modifications",
            "MPL/LGPL license-election, source provision or LGPL relinking",
            "Bouncy Castle attribution and notice text in an application release",
            "Actual Embasic UDF converter build or correctness",
        ],
    }

def maven_path(cache: Path, c: tuple[str, str, str]) -> Path:
    group, artifact, version = c
    return cache.joinpath(*group.split("."), artifact, version, artifact + "-" + version + ".jar")

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--maven-cache", required=True, type=Path)
    parser.add_argument("--shaded-jar", required=True, type=Path)
    args = parser.parse_args()
    try:
        upstream = {c: maven_path(args.maven_cache, c) for c in DEPENDENCIES}
        print(json.dumps(check_shaded(args.shaded_jar, upstream), sort_keys=True, indent=2))
        return 0
    except (AuditFailure, OSError, zipfile.BadZipFile) as exc:
        reason = str(exc) if isinstance(exc, AuditFailure) else "read_error"
        print("ISOLATED_SHADE_AUDIT: FAIL (" + reason + ")", file=sys.stderr)
        return 2

if __name__ == "__main__":
    sys.exit(main())
