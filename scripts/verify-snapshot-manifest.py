#!/usr/bin/env python3
"""Read-only SHA-256 check for a separately prepared, owner-reviewed snapshot.

This is NOT permission to publish: legal approvals and private source scans
must already have completed outside the public GitHub repository.
No snapshot files, paths or private manifest contents are printed.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import sys

MAX_FILE_COUNT = 400
MAX_FILE_BYTES = 12 * 1024 * 1024
MAX_TOTAL_BYTES = 200 * 1024 * 1024
MAX_MANIFEST_BYTES = 256 * 1024
DENIED_SUFFIXES = {
    ".udf", ".pdf", ".jar", ".class", ".zip", ".exe", ".dll", ".p12",
    ".pfx", ".jks", ".keystore", ".pem", ".key", ".db", ".sqlite",
    ".log", ".pyc", ".env", ".bak", ".tmp",
}
DENIED_PARTS = {".git", "__pycache__", "target", ".idea", ".vscode"}
HEX64 = re.compile(r"[a-f0-9]{64}\Z")

class SnapshotError(Exception):
    pass

def digest_file(path: Path) -> tuple[str, int]:
    total = 0
    hash_object = hashlib.sha256()
    with path.open("rb") as data:
        while part := data.read(1024 * 1024):
            total += len(part)
            if total > MAX_FILE_BYTES:
                raise SnapshotError("file_size_limit")
            hash_object.update(part)
    return hash_object.hexdigest(), total

def check_name(name: str) -> str:
    if not isinstance(name, str) or not name or "\\" in name or "\x00" in name:
        raise SnapshotError("invalid_relative_path")
    p = PurePosixPath(name)
    if (p.is_absolute() or ":" in name or
        any(x in {".", ".."} for x in name.split("/")) or
        any(not x for x in name.split("/")) or
        any(x in DENIED_PARTS for x in p.parts)):
        raise SnapshotError("unsafe_snapshot_path")
    if p.suffix.lower() in DENIED_SUFFIXES:
        raise SnapshotError("restricted_snapshot_extension")
    if p.name.lower() in {".env", "id_rsa", "id_ed25519"}:
        raise SnapshotError("restricted_snapshot_name")
    if any(ord(ch) < 32 for ch in name):
        raise SnapshotError("invalid_path_control_character")
    return name

def no_duplicate_object_keys(pairs):
    result = {}
    for k, v in pairs:
        if k in result:
            raise SnapshotError("duplicate_manifest_key")
        result[k] = v
    return result

def inspect_snapshot(root: Path, manifest: Path) -> dict:
    if root.is_symlink() or not root.is_dir():
        raise SnapshotError("snapshot_root_missing_or_symlink")
    if manifest.is_symlink() or not manifest.is_file():
        raise SnapshotError("external_manifest_missing_or_symlink")
    source = root.resolve()
    approval_path = manifest.resolve()
    if approval_path == source or source in approval_path.parents:
        raise SnapshotError("manifest_must_remain_outside_snapshot")
    if manifest.stat().st_size > MAX_MANIFEST_BYTES:
        raise SnapshotError("manifest_size_limit")
    try:
        payload = json.loads(manifest.read_text("utf-8"),
                             object_pairs_hook=no_duplicate_object_keys)
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise SnapshotError("manifest_invalid_json") from exc
    if not isinstance(payload, dict) or set(payload) != {"schema_version", "files"}:
        raise SnapshotError("manifest_schema_invalid")
    if type(payload["schema_version"]) is not int or payload["schema_version"] != 1:
        raise SnapshotError("manifest_version_invalid")
    records = payload["files"]
    if not isinstance(records, list) or not 0 < len(records) <= MAX_FILE_COUNT:
        raise SnapshotError("manifest_file_count_invalid")
    expected = {}
    for item in records:
        if not isinstance(item, dict) or set(item) != {"path", "sha256"}:
            raise SnapshotError("manifest_entry_invalid")
        name = check_name(item["path"])
        hash_value = item["sha256"]
        if name in expected:
            raise SnapshotError("duplicate_manifest_path")
        if not isinstance(hash_value, str) or not HEX64.fullmatch(hash_value):
            raise SnapshotError("manifest_hash_invalid")
        expected[name] = hash_value
    found = set()
    combined = 0
    for parent, dirs, files in os.walk(source, topdown=True, followlinks=False):
        parent_path = Path(parent)
        for directory in dirs:
            relative = (parent_path / directory).relative_to(source).as_posix()
            check_name(relative)
            if (parent_path / directory).is_symlink():
                raise SnapshotError("symlink_directory")
        for filename in files:
            path = parent_path / filename
            relative = check_name(path.relative_to(source).as_posix())
            if path.is_symlink() or not path.is_file():
                raise SnapshotError("symlink_or_nonregular_file")
            if relative not in expected:
                raise SnapshotError("unlisted_snapshot_file")
            value, size = digest_file(path)
            if value != expected[relative]:
                raise SnapshotError("snapshot_sha256_mismatch")
            found.add(relative)
            combined += size
            if combined > MAX_TOTAL_BYTES:
                raise SnapshotError("snapshot_total_byte_limit")
            if len(found) > MAX_FILE_COUNT:
                raise SnapshotError("snapshot_file_count_limit")
    if found != set(expected):
        raise SnapshotError("manifest_file_missing")
    return {
        "status": "SNAPSHOT_BYTES_MATCH_ONLY_NOT_PUBLICATION_APPROVED",
        "file_count": len(found),
        "total_bytes": combined,
        "manifest_sha256": digest_file(manifest)[0],
    }

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot-root", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(inspect_snapshot(args.snapshot_root, args.manifest),
                         sort_keys=True))
        return 0
    except (SnapshotError, OSError, ValueError) as exc:
        # Never echo snapshot paths, identities or source content.
        reason = str(exc) if isinstance(exc, SnapshotError) else "file_system_error"
        print("SNAPSHOT_CHECK: BLOCKED (" + reason + ")", file=sys.stderr)
        return 2

if __name__ == "__main__":
    sys.exit(main())
