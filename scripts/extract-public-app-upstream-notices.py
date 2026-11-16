#!/usr/bin/env python3
"""Extract component-specific upstream legal texts for review; never approve release."""
import argparse
import hashlib
from io import BytesIO
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
    p.add_argument("--shaded-jar", type=Path, default=Path("app/target/embasic-public-desktop-0.1.0-SNAPSHOT-all.jar"))
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
            with ZipFile(BytesIO(jar_bytes)) as archive:
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
    # Compare upstream legal records against the actual shaded runtime archive.
    # Missing/changed records are findings, never an automatic legal conclusion.
    shaded = {"status": "NOT_VERIFIED", "path": str(args.shaded_jar), "findings": []}
    if args.shaded_jar.is_file() and not args.shaded_jar.is_symlink():
        if not 0 < args.shaded_jar.stat().st_size <= MAX_SOURCE_JAR_BYTES:
            shaded["status"] = "INVALID_SIZE"
        else:
            try:
                shaded_bytes = args.shaded_jar.read_bytes()
                if len(shaded_bytes) > MAX_SOURCE_JAR_BYTES:
                    shaded["status"] = "INVALID_SIZE"
                else:
                    shaded["sha256"] = hashlib.sha256(shaded_bytes).hexdigest()
                    with ZipFile(BytesIO(shaded_bytes)) as bundle:
                        names = bundle.namelist()
                        for record in evidence:
                            member = record["member"]
                            count = names.count(member)
                            finding = {"component": record["component"], "member": member}
                            if count != 1:
                                finding["status"] = "ABSENT" if count == 0 else "AMBIGUOUS"
                            else:
                                info = bundle.getinfo(member)
                                if not 0 < info.file_size <= MAX_LEGAL_MEMBER_BYTES:
                                    finding["status"] = "INVALID_SIZE"
                                else:
                                    data = bundle.read(member)
                                    finding["status"] = ("EXACT" if hashlib.sha256(data).hexdigest()
                                                         == record["sha256"] else "DIFFERENT")
                            shaded["findings"].append(finding)
                    shaded["status"] = "REVIEW_REQUIRED"
            except (BadZipFile, OSError, RuntimeError, ValueError) as exc:
                shaded["status"] = "UNREADABLE"
                shaded["error_type"] = type(exc).__name__
    # Shade merges component-level META-INF paths into one shared namespace.
    # Record actual upstream collisions explicitly so a matching single NOTICE
    # cannot be mistaken for complete component-specific attribution.
    collisions = []
    for member in MEMBERS:
        records = [r for r in evidence if r["member"] == member]
        hashes = {r["sha256"] for r in records}
        collisions.append({
            "member": member,
            "upstream_components": sorted(r["component"] for r in records),
            "distinct_upstream_sha256": len(hashes),
            "component_attribution_review_required": len(records) > 1,
            "shaded_single_path_cannot_prove_all_attributions": len(hashes) > 1,
        })
    args.out.mkdir(parents=True, exist_ok=True)
    manifest = {"release_approved": False, "legal_review": "NOT_VERIFIED",
                "purpose": "upstream attribution evidence only; do not distribute",
                "members": evidence, "source_jars": source_jars, "missing": missing,\n                "shared_meta_inf_collision_review": collisions,
                "shaded_reconciliation": shaded}
    (args.out / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"extracted": len(evidence), "missing": len(missing), "release_approved": False}))
    return 1 if missing else 0

if __name__ == "__main__":
    raise SystemExit(main())
