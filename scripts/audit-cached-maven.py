#!/usr/bin/env python3
"""Offline, source-free inventory of four Maven-resolved legacy PDF libraries.

Reads only pinned upstream POM and JAR files already resolved by Maven.
No network operations, release authorization, binary copying or file uploads.
"""
from __future__ import annotations
import argparse
import hashlib
import io
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
import zipfile

COMPONENTS = (
    ("com.lowagie", "itext", "2.1.7"),
    ("bouncycastle", "bcmail-jdk14", "138"),
    ("bouncycastle", "bcprov-jdk14", "138"),
    ("org.bouncycastle", "bctsp-jdk14", "1.38"),
    ("org.bouncycastle", "bcprov-jdk14", "1.38"),
    ("org.bouncycastle", "bcmail-jdk14", "1.38"),
)
BC = {('bouncycastle','bcmail-jdk14','138'),('bouncycastle','bcprov-jdk14','138'),('bouncycastle','bctsp-jdk14','138')}
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
MAX_JAR_BYTES = 32 * 1024 * 1024
MAX_EXPANDED_BYTES = 180 * 1024 * 1024
MAX_FILES = 50000

class AuditError(Exception):
    pass

def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()

def artifact_path(m2: Path, component: tuple[str, str, str], extension: str) -> Path:
    if component not in COMPONENTS or extension not in {"pom", "jar"}:
        raise AuditError("unexpected_artifact")
    group, artifact, version = component
    return m2.joinpath(*group.split("."), artifact, version,
                       artifact + "-" + version + "." + extension)

def inspect_pom(raw: bytes, component: tuple[str, str, str]) -> dict:
    if len(raw) > 1024 * 1024:
        raise AuditError("pom_limit")
    try:
        root = ET.fromstring(raw)
    except ET.ParseError as exc:
        raise AuditError("invalid_pom") from exc
    for node in root.iter():
        node.tag = node.tag.rsplit("}", 1)[-1]
    for tag, expected in zip(("groupId", "artifactId", "version"), component):
        if root.findtext(tag) != expected:
            raise AuditError("unexpected_pom_identity")
    licenses = sorted({(node.findtext("name", "") or "").strip()
        for node in root.findall("./licenses/license")}) 
    licenses = [v for v in licenses if v]
    if not licenses:
        raise AuditError("license_metadata_absent")
    if component[0] == "com.lowagie":
        if not any("mozilla public license" in v.lower() for v in licenses):
            raise AuditError("itext_license_metadata_mismatch")
    elif not any("bouncy castle" in v.lower() for v in licenses):
        raise AuditError("bc_license_metadata_mismatch")
    dependencies = {(d.findtext("groupId", ""),
        d.findtext("artifactId", ""), d.findtext("version", ""))
        for d in root.findall("./dependencies/dependency")}
    if component[0] == "com.lowagie" and not BC.issubset(dependencies):
        raise AuditError("expected_bc_transitives_absent")
    return {"pom_licenses": licenses, "declares_expected_bc_dependencies": component[0] == "com.lowagie"}

def inspect_jar(raw: bytes) -> dict:
    if not raw or len(raw) > MAX_JAR_BYTES:
        raise AuditError("jar_byte_limit")
    try:
        with zipfile.ZipFile(io.BytesIO(raw)) as jar:
            entries = jar.infolist()
            if not entries or len(entries) > MAX_FILES:
                raise AuditError("jar_entry_limit")
            if len({e.filename for e in entries}) != len(entries):
                raise AuditError("duplicate_archive_entry")
            notices, expanded = [], 0
            for item in entries:
                name = item.filename
                if (name.startswith("/") or "\\" in name or
                    ".." in name.split("/") or "." in name.split("/")):
                    raise AuditError("unsafe_archive_name")
                expanded += item.file_size
                if item.file_size > MAX_JAR_BYTES or expanded > MAX_EXPANDED_BYTES:
                    raise AuditError("archive_expansion_limit")
                leaf = name.rsplit("/", 1)[-1].lower()
                if not item.is_dir() and leaf.startswith(("license", "notice", "copying")):
                    notices.append(name)
            return {"entries": len(entries),
                    "notice_member_names": sorted(notices)[:20],
                    "total_notice_member_count": len(notices)}
    except (OSError, ValueError, zipfile.BadZipFile) as exc:
        raise AuditError("invalid_jar") from exc

def read_inventory(m2: Path) -> dict:
    items = []
    for coord in COMPONENTS:
        component = {"coordinate": ":".join(coord)}
        for ext in ("pom", "jar"):
            path = artifact_path(m2, coord, ext)
            if path.is_symlink() or not path.is_file() or path.stat().st_size > MAX_JAR_BYTES:
                raise AuditError("required_upstream_file_unavailable")
            raw = path.read_bytes()
            component[ext + "_sha256"] = sha(raw)
            if ext == "pom":
                component.update(inspect_pom(raw, coord))
            else:
                component["jar_bytes"] = len(raw)
                component.update(inspect_jar(raw))
        items.append(component)
    return {"status": "INSPECTED_NOT_DISTRIBUTION_APPROVED",
            "components": items,
            "limits": ["No shaded Embasic application binary inspected",
                       "Notice filenames do not prove license contents",
                       "No signature/provenance or vulnerability audit",
                       "No legal review or public redistribution authorization"]}

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--maven-cache", type=Path, required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(read_inventory(args.maven_cache), indent=2, sort_keys=True))
        return 0
    except (AuditError, OSError) as exc:
        code = str(exc) if isinstance(exc, AuditError) else "filesystem_error"
        print("DEPENDENCY_AUDIT: FAIL (" + code + ")", file=sys.stderr)
        return 2

if __name__ == "__main__":
    sys.exit(main())
