#!/usr/bin/env python3
"""Extract component-specific upstream legal texts for review; never approve release."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

COMPONENTS = ("fontbox", "pdfbox-io")
MEMBERS = ("META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/NOTICE")

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--m2", type=Path, default=Path.home() / ".m2/repository")
    p.add_argument("--out", type=Path, required=True)
    p.add_argument("--version", default="3.0.8")
    args = p.parse_args()
    if not args.version.replace(".", "").isdigit():
        p.error("invalid version")
    evidence = []
    missing = []
    for component in COMPONENTS:
        jar = args.m2 / "org/apache/pdfbox" / component / args.version / f"{component}-{args.version}.jar"
        if not jar.is_file():
            missing.append(str(jar))
            continue
        with ZipFile(jar) as archive:
            for member in MEMBERS:
                try:
                    payload = archive.read(member)
                except KeyError:
                    missing.append(f"{jar}:{member}")
                    continue
                destination = args.out / component / member
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(payload)
                evidence.append({"component": component, "source_jar": str(jar),
                                 "member": member, "sha256": hashlib.sha256(payload).hexdigest(),
                                 "bytes": len(payload), "extracted_path": str(destination)})
    args.out.mkdir(parents=True, exist_ok=True)
    manifest = {"release_approved": False, "legal_review": "NOT_VERIFIED",
                "purpose": "upstream attribution evidence only; do not distribute",
                "members": evidence, "missing": missing}
    (args.out / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"extracted": len(evidence), "missing": len(missing), "release_approved": False}))
    return 1 if missing else 0

if __name__ == "__main__":
    raise SystemExit(main())
