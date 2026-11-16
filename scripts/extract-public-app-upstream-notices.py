#!/usr/bin/env python3
"""Extract component-specific upstream legal texts for review; never approve release."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import BadZipFile, ZipFile

MAX_SOURCE_JAR_BYTES = 64 * 1024 * 1024
MAX_LEGAL_MEMBER_BYTES = 1024 * 1024

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
    source_jars = []
    for component in COMPONENTS:
        jar = args.m2 / "org/apache/pdfbox" / component / args.version / f"{component}-{args.version}.jar"
        if jar.is_symlink() or not jar.is_file():
            missing.append(str(jar))
            continue
        if not 0 < jar.stat().st_size <= MAX_SOURCE_JAR_BYTES:
            missing.append(f"{jar}:source archive size outside bounded limit")
            continue
        jar_bytes = jar.read_bytes()
        if len(jar_bytes) > MAX_SOURCE_JAR_BYTES:
            missing.append(f"{jar}:source archive changed during read")
            continue
        source_jars.append({"component": component, "path": str(jar),
                            "sha256": hashlib.sha256(jar_bytes).hexdigest(),
                            "bytes": len(jar_bytes)})
        try:
            with ZipFile(jar) as archive:
                names = archive.namelist()
                for member in MEMBERS:
                    # Ambiguous duplicate ZIP names cannot be trusted as legal evidence.
                    if names.count(member) != 1:
                        missing.append(f"{jar}:{member}:expected exactly one ZIP entry")
                        continue
                    info = archive.getinfo(member)
                    if not 0 < info.file_size <= MAX_LEGAL_MEMBER_BYTES:
                        missing.append(f"{jar}:{member}:empty or oversized legal record")
                        continue
                    payload = archive.read(member)
                    if len(payload) != info.file_size:
                        missing.append(f"{jar}:{member}:size mismatch")
                        continue
                    destination = args.out / component / member
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    destination.write_bytes(payload)
                    evidence.append({"component": component, "source_jar": str(jar),
                                     "member": member, "sha256": hashlib.sha256(payload).hexdigest(),
                                     "bytes": len(payload), "extracted_path": str(destination)})
        except (BadZipFile, OSError, RuntimeError, ValueError) as exc:
            missing.append(f"{jar}:unreadable archive ({type(exc).__name__})")
    args.out.mkdir(parents=True, exist_ok=True)
    manifest = {"release_approved": False, "legal_review": "NOT_VERIFIED",
                "purpose": "upstream attribution evidence only; do not distribute",
                "members": evidence, "source_jars": source_jars, "missing": missing}
    (args.out / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"extracted": len(evidence), "missing": len(missing), "release_approved": False}))
    return 1 if missing else 0

if __name__ == "__main__":
    raise SystemExit(main())
