#!/usr/bin/env python3
"""Verify two reviewed notice files survive Windows portable ZIP byte-for-byte.

This is a packaging *integrity* control, NOT an evaluation of licensing terms.
Only synthetic notices are exercised in the public bootstrap workflow.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import sys
import zipfile

REQUIRED = ("THIRD-PARTY-iText.txt", "THIRD-PARTY-NOTICES.txt")
ARCHIVE_LIMIT = 1024 * 1024 * 1024
MEMBER_LIMIT = 400 * 1024 * 1024
MAX_EXPANDED = 2 * 1024 * 1024 * 1024
NOTICE_LIMIT = 1024 * 1024

class NoticeError(Exception):
    pass

def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def verify(portable: Path, notice_bytes: dict[str, bytes]) -> dict:
    if set(notice_bytes) != set(REQUIRED):
        raise NoticeError("exact_notice_set_required")
    for data in notice_bytes.values():
        if not data or len(data) > NOTICE_LIMIT:
            raise NoticeError("reviewed_notice_size_limit")
    if portable.is_symlink() or not portable.is_file() or portable.stat().st_size > ARCHIVE_LIMIT:
        raise NoticeError("portable_missing_or_too_large")
    try:
        with zipfile.ZipFile(portable) as z:
            info = z.infolist()
            if not info or len(info) > 100000:
                raise NoticeError("archive_entry_limit")
            names = [n.filename for n in info]
            if len(set(names)) != len(names):
                raise NoticeError("duplicate_archive_member")
            if sum(n.file_size for n in info) > MAX_EXPANDED:
                raise NoticeError("archive_expanded_limit")
            for n in info:
                if (n.filename.startswith("/") or "\\" in n.filename or
                    any(t in {".", ".."} for t in n.filename.split("/")) or
                    n.file_size > MEMBER_LIMIT):
                    raise NoticeError("unsafe_or_oversized_member")
            matched = {}
            for wanted in REQUIRED:
                candidates = [name for name in names
                              if name == wanted or name.endswith("/" + wanted)]
                if len(candidates) != 1:
                    raise NoticeError("notice_missing_or_ambiguous")
                if z.getinfo(candidates[0]).file_size > NOTICE_LIMIT:
                    raise NoticeError("embedded_notice_size_limit")
                try:
                    data = z.read(candidates[0])
                except (OSError, RuntimeError, ValueError, zipfile.BadZipFile) as exc:
                    raise NoticeError("notice_unreadable") from exc
                if data != notice_bytes[wanted]:
                    raise NoticeError("notice_contents_differ")
                matched[wanted] = sha256(data)
            return {"status": "NOTICE_BYTE_INTEGRITY_VERIFIED_NOT_LICENSE_APPROVED",
                    "notice_sha256": matched}
    except (OSError, ValueError, zipfile.BadZipFile) as exc:
        raise NoticeError("archive_invalid") from exc

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--portable-zip", type=Path, required=True)
    parser.add_argument("--itext-notice", type=Path, required=True)
    parser.add_argument("--third-party-notices", type=Path, required=True)
    args = parser.parse_args()
    try:
        data = {}
        for path, name in ((args.itext_notice, REQUIRED[0]),
                           (args.third_party_notices, REQUIRED[1])):
            if path.is_symlink() or not path.is_file() or path.stat().st_size > NOTICE_LIMIT:
                raise NoticeError("reviewed_notice_unavailable")
            data[name] = path.read_bytes()
        print(json.dumps(verify(args.portable_zip,data),sort_keys=True,indent=2))
        return 0
    except (OSError, NoticeError) as exc:
        reason = str(exc) if isinstance(exc,NoticeError) else "filesystem_error"
        print("NOTICE_INTEGRITY: FAIL (" + reason + ")",file=sys.stderr)
        return 2

if __name__ == "__main__":
    sys.exit(main())
