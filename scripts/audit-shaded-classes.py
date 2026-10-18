#!/usr/bin/env python3
"""Full .class byte-integrity gate for an isolated, non-distributable shade.

Uses all 6 effective Maven dependencies, rejects divergent duplicate
class names and verifies every expected class is unchanged in the shade.
Does NOT verify non-class resources, licensing, or real Embasic application.
"""
from __future__ import annotations
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import zipfile

SIBLING = Path(__file__).with_name("audit-isolated-shade.py")
spec = importlib.util.spec_from_file_location("shade_probe", SIBLING)
shade = importlib.util.module_from_spec(spec)
assert spec is not None and spec.loader is not None
spec.loader.exec_module(shade)
AuditFailure = shade.AuditFailure

def class_inventory(shaded: Path, dependencies: dict[tuple[str,str,str],Path]) -> dict:
    if set(dependencies) != set(shade.DEPENDENCIES):
        raise AuditFailure("dependency_set_incomplete")
    expected: dict[str, str] = {}
    occurrences = 0
    duplicates = 0
    for coordinate in shade.DEPENDENCIES:
        with shade.checked_zip(dependencies[coordinate]) as archive:
            for member in archive.infolist():
                if member.is_dir() or not member.filename.endswith(".class"):
                    continue
                occurrences += 1
                digest = shade.member_digest(archive, member.filename)
                if member.filename in expected:
                    if digest != expected[member.filename]:
                        raise AuditFailure("divergent_duplicate_class_bytes")
                    duplicates += 1
                else:
                    expected[member.filename] = digest
    if not expected:
        raise AuditFailure("upstream_class_set_empty")
    with shade.checked_zip(shaded) as archive:
        names = set(archive.namelist())
        if shade.AUDIT_MAIN not in names or shade.FORBIDDEN_APP in names:
            raise AuditFailure("not_isolated_audit_artifact")
        for name, value in expected.items():
            if name not in names:
                raise AuditFailure("upstream_class_missing_from_shade")
            if shade.member_digest(archive, name) != value:
                raise AuditFailure("upstream_class_changed_by_shade")
        extra = sorted(x for x in names if x.endswith(".class")
                       and x not in expected and x != shade.AUDIT_MAIN)
        if extra:
            raise AuditFailure("unexpected_shaded_classes")
    return {
        "status": "ALL_UPSTREAM_CLASS_BYTES_MATCH_NOT_REDISTRIBUTION_APPROVED",
        "upstream_class_occurrences": occurrences,
        "unique_upstream_classes_verified": len(expected),
        "identical_duplicate_class_occurrences": duplicates,
        "divergent_duplicate_classes": 0,
        "scope": "Synthetic independent shade and class entries only; excludes legal notices, packaging and real Embasic application",
    }

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--maven-cache", type=Path, required=True)
    parser.add_argument("--shaded-jar", type=Path, required=True)
    args = parser.parse_args()
    try:
        data = class_inventory(args.shaded_jar,
                {c:shade.maven_path(args.maven_cache,c) for c in shade.DEPENDENCIES})
        print(json.dumps(data,indent=2,sort_keys=True))
        return 0
    except (OSError, zipfile.BadZipFile, AuditFailure) as exc:
        reason = str(exc) if isinstance(exc,AuditFailure) else "unreadable_jar"
        print("ALL_CLASS_AUDIT: FAIL (" + reason + ")",file=sys.stderr)
        return 2
if __name__ == "__main__":
    sys.exit(main())
