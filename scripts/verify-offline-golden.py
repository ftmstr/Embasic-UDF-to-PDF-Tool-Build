#!/usr/bin/env python3
"""Offline-only golden evidence verifier. Never uploads or prints case data."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

FIELDS = ("case_id", "authorized_source", "schema_version", "permission_record",
          "input_sha256", "reference_pdf_sha256", "output_sha256",
          "engine_commit", "app_commit", "OS_build", "java_version",
          "page_count_expected", "page_count_actual", "page_geometry",
          "text_unicode", "table_cells", "images", "fonts",
          "pixel_diff_method", "pixel_diff_threshold", "reviewer",
          "result", "reason", "timestamp")
CHECKS = ("page_geometry", "text_unicode", "table_cells", "images", "fonts")
HEX64 = re.compile(r"[0-9a-f]{64}\Z")
HEX40 = re.compile(r"[0-9a-f]{40}\Z")

def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()

def verify(record: dict, source: Path, reference: Path, output: Path) -> str:
    if not isinstance(record, dict) or set(record) != set(FIELDS):
        return "BLOCKED"
    if record["result"] not in ("PASS", "FAIL", "NOT_RUN", "BLOCKED"):
        return "BLOCKED"
    if not all(isinstance(record[k], str) and record[k].strip()
               for k in FIELDS if k not in ("page_count_expected", "page_count_actual")):
        return "BLOCKED"
    if not all(isinstance(record[k], int) and not isinstance(record[k], bool)
               and record[k] > 0 for k in ("page_count_expected", "page_count_actual")):
        return "BLOCKED"
    if not all(HEX40.fullmatch(record[k]) for k in ("engine_commit", "app_commit")):
        return "BLOCKED"
    if not all(HEX64.fullmatch(record[k]) for k in
               ("input_sha256", "reference_pdf_sha256", "output_sha256")):
        return "BLOCKED"
    if not all(record[k] in ("PASS", "FAIL", "NOT_RUN", "BLOCKED") for k in CHECKS):
        return "BLOCKED"
    # A claimed PASS cannot conceal an unreviewed metric or page mismatch.
    if record["result"] == "PASS" and (any(record[k] != "PASS" for k in CHECKS)
            or record["page_count_expected"] != record["page_count_actual"]):
        return "BLOCKED"
    try:
        paths = [p.resolve(strict=True) for p in (source, reference, output)]
        if len(set(paths)) != 3 or not all(p.is_file() and not p.is_symlink()
                                           for p in (source, reference, output)):
            return "BLOCKED"
        if any(p.stat().st_size == 0 for p in paths):
            return "BLOCKED"
        if any(digest(p) != record[k] for p, k in zip(paths,
               ("input_sha256", "reference_pdf_sha256", "output_sha256"))):
            return "BLOCKED"
    except (OSError, ValueError):
        return "BLOCKED"
    # An offline record is evidence only; never asserts native compatibility.
    return record["result"]

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--record", required=True, type=Path)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--reference", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        if args.record.is_symlink() or args.record.stat().st_size > 64 * 1024:
            raise ValueError("unsafe record")
        record = json.loads(args.record.read_text(encoding="utf-8"))
        status = verify(record, args.source, args.reference, args.output)
    except (OSError, ValueError, UnicodeError, json.JSONDecodeError):
        status = "BLOCKED"
    print("OFFLINE_GOLDEN_EVIDENCE=" + status)
    return 0 if status == "PASS" else 2

if __name__ == "__main__":
    sys.exit(main())
