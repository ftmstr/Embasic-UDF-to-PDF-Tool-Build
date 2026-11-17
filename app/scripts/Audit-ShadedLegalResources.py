#!/usr/bin/env python3
"""Audit exact upstream legal-resource bytes in a shaded public app JAR.

This is an offline release gate, not legal approval. No network access or
automatic artifact publication. The caller supplies independently acquired,
rights-reviewed upstream JARs and the produced shaded JAR.
"""
import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path

COMPONENTS = ("pdfbox", "fontbox", "pdfbox-io")
MEMBERS = ("META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/NOTICE")


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def unique_member(jar, name):
    matches = [entry for entry in jar.infolist() if entry.filename == name]
    if len(matches) != 1 or matches[0].is_dir():
        raise ValueError(f"{name}: expected one regular ZIP member; found {len(matches)}")
    return jar.read(matches[0])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pdfbox", required=True, type=Path)
    parser.add_argument("--fontbox", required=True, type=Path)
    parser.add_argument("--pdfbox-io", required=True, type=Path)
    parser.add_argument("--shaded", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--namespace", default="META-INF/embasic-upstream")
    args = parser.parse_args()
    report = {"schema": 1, "status": "FAIL", "source_approval": "NOT_VERIFIED",
              "legal_approval": "NOT_VERIFIED", "checks": []}
    try:
        with zipfile.ZipFile(args.shaded) as shaded:
            for component in COMPONENTS:
                with zipfile.ZipFile(getattr(args, component.replace("-", "_"))) as original:
                    for member in MEMBERS:
                        target = f"{args.namespace.rstrip('/')}/{component}/{member}"
                        entry = {"component": component, "source_member": member,
                                 "shaded_member": target, "status": "FAIL"}
                        report["checks"].append(entry)
                        try:
                            source = unique_member(original, member)
                            dest = unique_member(shaded, target)
                            entry.update(source_sha256=sha256(source),
                                         shaded_sha256=sha256(dest),
                                         status="PASS" if source == dest else "FAIL")
                        except (KeyError, ValueError, RuntimeError, zipfile.BadZipFile) as exc:
                            entry["error"] = str(exc)
        if len(report["checks"]) == 9 and all(e["status"] == "PASS" for e in report["checks"]):
            report["status"] = "BYTE_PARITY_PASS_LEGAL_NO_GO"
    except (OSError, ValueError, zipfile.BadZipFile) as exc:
        report["error"] = str(exc)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"{report['status']}: {len(report['checks'])}/9 checked; {args.report}")
    return 0 if report["status"] == "BYTE_PARITY_PASS_LEGAL_NO_GO" else 1


if __name__ == "__main__":
    sys.exit(main())
