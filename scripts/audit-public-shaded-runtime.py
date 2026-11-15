#!/usr/bin/env python3
"""Inspect actual Public MVP shaded Java dependencies; never authorize release."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

# Existing public SHA 859a087... Maven shade output (not a future whitelist).
# Any runtime dependency drift requires independent explicit review.
CLASSES = {
    ("org.apache.pdfbox", "pdfbox", "3.0.8"): "org/apache/pdfbox/pdmodel/PDDocument.class",
    ("org.apache.pdfbox", "pdfbox-io", "3.0.8"): "org/apache/pdfbox/io/RandomAccessRead.class",
    ("org.apache.pdfbox", "fontbox", "3.0.8"): "org/apache/fontbox/ttf/TrueTypeFont.class",
    ("commons-logging", "commons-logging", "1.4.0"): "org/apache/commons/logging/Log.class",
}
INCLUDE = re.compile(r"Including ([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+):jar:([A-Za-z0-9_.-]+) in the shaded jar\.")
MAX_JAR = 64 * 1024 * 1024
MAX_MEMBERS = 50000
MAX_CLASS = 4 * 1024 * 1024
NS = "{http://maven.apache.org/POM/4.0.0}"

class AuditError(Exception):
    pass

def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def safe_file(path: Path, root: Path, limit: int) -> bytes:
    """No symlink hops, missing files, or unbounded reads."""
    # Windows temporary paths can have different long/short aliases. Resolve
    # both sides before membership comparison, but reject symlink hops first.
    lexical = path.absolute()
    root_lexical = root.absolute()
    if root_lexical.is_symlink():
        raise AuditError("symlink_artifact")
    for parent in (lexical, *lexical.parents):
        if parent == root_lexical:
            break
        if parent.is_symlink():
            raise AuditError("symlink_artifact")
    if not lexical.is_file() or lexical.is_symlink():
        raise AuditError("artifact_missing_or_symlink")
    allowed = root.resolve(strict=True)
    candidate = lexical.resolve(strict=True)
    if not candidate.is_relative_to(allowed):
        raise AuditError("path_outside_audit_root")
    size = candidate.stat().st_size
    if size <= 0 or size > limit:
        raise AuditError("artifact_size_limit")
    raw = candidate.read_bytes()
    if len(raw) != size:
        raise AuditError("artifact_changed_during_read")
    return raw

def licenses_from_pom(raw: bytes, coordinate: tuple[str, str, str]) -> list[str]:
    if b"<!DOCTYPE" in raw.upper() or b"<!ENTITY" in raw.upper():
        raise AuditError("unsafe_pom_xml")
    try:
        root = ET.fromstring(raw)
    except ET.ParseError as exc:
        raise AuditError("unreadable_pom_xml") from exc
    if root.tag != NS + "project" or root.findtext(NS + "artifactId") != coordinate[1]:
        raise AuditError("pom_artifact_identity_mismatch")
    group = root.findtext(NS + "groupId") or root.findtext(NS + "parent/" + NS + "groupId")
    version = root.findtext(NS + "version") or root.findtext(NS + "parent/" + NS + "version")
    if group != coordinate[0] or version != coordinate[2]:
        raise AuditError("pom_group_version_identity_mismatch")
    names = []
    for item in root.findall(NS + "licenses/" + NS + "license"):
        name = item.findtext(NS + "name")
        if name and name.strip():
            names.append(name.strip()[:160])
    return sorted(set(names))

def open_jar(path: Path, root: Path) -> zipfile.ZipFile:
    # Bounded ZIP inspection; never unpack executable code.
    raw = safe_file(path, root, MAX_JAR)
    try:
        z = zipfile.ZipFile(__import__("io").BytesIO(raw))
        entries = z.infolist()
        if not entries or len(entries) > MAX_MEMBERS:
            raise AuditError("jar_entry_count_limit")
        seen = set()
        for item in entries:
            name = item.filename
            if (name in seen or name.startswith("/") or "\\" in name
                or any(seg in (".", "..", "") for seg in name.rstrip("/").split("/"))):
                raise AuditError("unsafe_or_duplicate_jar_entry")
            seen.add(name)
            if item.file_size > MAX_JAR:
                raise AuditError("unbounded_jar_member")
        return z
    except (OSError, ValueError, zipfile.BadZipFile) as exc:
        raise AuditError("invalid_jar_archive") from exc

def extract_class(z: zipfile.ZipFile, name: str) -> bytes:
    try:
        member = z.getinfo(name)
        if member.file_size <= 0 or member.file_size > MAX_CLASS:
            raise AuditError("selected_class_size_limit")
        data = z.read(member)
        if len(data) != member.file_size:
            raise AuditError("selected_class_size_mismatch")
        return data
    except (KeyError, OSError, RuntimeError, zipfile.BadZipFile) as exc:
        raise AuditError("selected_class_missing_or_corrupt") from exc

def is_legal_text_member(name: str) -> bool:
    """Recognize common dependency LICENSE/NOTICE variants, including SPDX suffixes."""
    leaf = name.rsplit("/", 1)[-1].lower()
    stems = ("license", "licence", "notice", "copying")
    return leaf in stems or leaf.startswith(tuple(
        stem + separator for stem in stems for separator in (".", "-", "_")
    ))

def notice_paths(z: zipfile.ZipFile) -> list[str]:
    return sorted(item.filename for item in z.infolist()
                  if not item.is_dir() and is_legal_text_member(item.filename))[:40]

def notice_content_evidence(z: zipfile.ZipFile) -> list[dict]:
    """Hash actual notice/license bytes without publishing their text.

    This is an evidence inventory, NOT a license-compatibility decision.
    Refuse truncated inventories and oversized legal text rather than omit it.
    """
    paths = notice_paths(z)
    total = sum(1 for item in z.infolist()
                if not item.is_dir() and is_legal_text_member(item.filename))
    if total != len(paths):
        raise AuditError("truncated_license_notice_inventory")
    evidence = []
    for name in paths:
        member = z.getinfo(name)
        if member.file_size > 1024 * 1024:
            raise AuditError("license_notice_member_too_large")
        try:
            data = z.read(member)
        except (OSError, RuntimeError, zipfile.BadZipFile) as exc:
            raise AuditError("unreadable_license_notice_member") from exc
        if len(data) != member.file_size:
            raise AuditError("license_notice_member_size_mismatch")
        evidence.append({"member": name, "sha256": sha256(data), "bytes": len(data)})
    return evidence

def parse_shade_lines(lines: list[str]) -> set[tuple[str, str, str]]:
    observed = []
    for line in lines:
        if "Including " not in line or " in the shaded jar." not in line:
            continue
        match = INCLUDE.search(line)
        if not match:
            raise AuditError("unrecognized_shade_trace")
        observed.append(tuple(match.groups()))
    if len(observed) != len(set(observed)):
        raise AuditError("duplicate_shaded_dependency")
    if set(observed) != set(CLASSES):
        raise AuditError("unexpected_or_missing_shaded_dependency")
    return set(observed)

def inspect(lines: list[str], repository: Path, shaded: Path) -> dict:
    observed = parse_shade_lines(lines)
    cache = repository.resolve(strict=True)
    bundle_root = shaded.parent.resolve(strict=True)
    bundle_bytes = safe_file(shaded, bundle_root, MAX_JAR)
    components = []
    with open_jar(shaded, bundle_root) as bundle:
        shaded_notices = notice_paths(bundle)
        shaded_notice_hashes = notice_content_evidence(bundle)
        # Match exact bytes regardless of path; Shade may relocate or merge
        # META-INF notices. Missing bytes remain an explicit review finding.
        shaded_notice_digests = {entry["sha256"] for entry in shaded_notice_hashes}
        for group, name, version in sorted(observed):
            coord = (group, name, version)
            relative = Path(*group.split(".")) / name / version / (name + "-" + version)
            jar_path = cache / (str(relative) + ".jar")
            pom_path = cache / (str(relative) + ".pom")
            pom_raw = safe_file(pom_path, cache, 1024 * 1024)
            jar_raw = safe_file(jar_path, cache, MAX_JAR)
            licenses = licenses_from_pom(pom_raw, coord)
            selected = CLASSES[coord]
            with open_jar(jar_path, cache) as upstream:
                original = extract_class(upstream, selected)
                copied = extract_class(bundle, selected)
                if original != copied:
                    raise AuditError("selected_shaded_class_differs")
                notices = notice_paths(upstream)
                notice_hashes = notice_content_evidence(upstream)
            components.append({
                "maven_coordinate": ":".join(coord),
                "pom_sha256": sha256(pom_raw),
                "jar_sha256": sha256(jar_raw),
                "declared_pom_licenses_unreviewed": licenses,
                "upstream_license_notice_member_names": notices,
                "upstream_license_notice_content_sha256_unreviewed": notice_hashes,
                "upstream_notice_exact_byte_match_status": (
                    "NO_UPSTREAM_NOTICE_FOUND" if not notice_hashes else
                    "ALL_EXACT_BYTES_PRESENT" if all(
                        entry["sha256"] in shaded_notice_digests for entry in notice_hashes
                    ) else "MISSING_OR_TRANSFORMED_NOTICE_BYTES"
                ),
                "upstream_notice_exact_byte_matches": [
                    {"sha256": entry["sha256"],
                     "exact_bytes_present": entry["sha256"] in shaded_notice_digests}
                    for entry in notice_hashes
                ],
                "sample_class_sha256_identical": sha256(original),
                "license_or_notice_review": "NOT_VERIFIED",
            })
    # Actionable, exact-byte review queue for the independent app/ release gate.
    # Missing or transformed legal text must never silently disappear into a
    # component-level summary; matching bytes still require human legal review.
    unresolved_notice_members = [
        {"maven_coordinate": component["maven_coordinate"],
         "member": entry["member"], "sha256": entry["sha256"],
         "bytes": entry["bytes"], "review": "REQUIRES_HUMAN_NOTICE_REVIEW"}
        for component in components
        for entry in component["upstream_license_notice_content_sha256_unreviewed"]
        if not any(match["sha256"] == entry["sha256"] and match["exact_bytes_present"]
                   for match in component["upstream_notice_exact_byte_matches"])
    ]
    # Even if every upstream legal-text byte survives shading, the actual
    # declared licenses and redistribution duties remain unapproved. A JAR
    # without recognizable legal text must be queued, not silently treated
    # as an empty set of obligations.
    component_legal_review_queue = [
        {
            "maven_coordinate": component["maven_coordinate"],
            "declared_pom_licenses": component["declared_pom_licenses_unreviewed"],
            "upstream_legal_text_member_count": len(
                component["upstream_license_notice_content_sha256_unreviewed"]
            ),
            "missing_or_transformed_legal_text_member_count": sum(
                not match["exact_bytes_present"]
                for match in component["upstream_notice_exact_byte_matches"]
            ),
            "review": "REQUIRES_INDEPENDENT_LICENSE_AND_NOTICE_CLEARANCE",
        }
        for component in components
    ]
    digest = sha256(json.dumps(components, sort_keys=True, separators=(",", ":")).encode("utf-8"))
    return {
        "status": "BLOCKED", "publication_allowed": False,
        "resolved_shade_component_count": len(components),
        "resolved_shade_components": components,
        "resolved_evidence_sha256": digest,
        "unresolved_upstream_license_notice_members": unresolved_notice_members,
        "unresolved_upstream_license_notice_count": len(unresolved_notice_members),
        "component_legal_review_queue": component_legal_review_queue,
        "component_legal_review_count": len(component_legal_review_queue),
        "independent_app_license_notice_clearance": "NOT_VERIFIED",
        "shaded_jar_sha256": sha256(bundle_bytes),
        "shaded_license_notice_member_names": shaded_notices,
        "shaded_license_notice_content_sha256_unreviewed": shaded_notice_hashes,
        "complete_shaded_byte_provenance": "NOT_VERIFIED",
        "transitive_source_and_license_approval": "NOT_VERIFIED",
        "source_notice_artwork_redistribution": "NO_GO",
        "native_uyap_golden": "NOT_VERIFIED",
        "physical_exfat": "NOT_RUN",
        "hostile_cas": "NO_GO",
    }

def maven_launcher(platform: str) -> str:
    return "mvn.cmd" if platform == "win32" else "mvn"

def run_maven() -> list[str]:
    command = [maven_launcher(sys.platform), "-B", "-ntp", "-f", "app/pom.xml",
               "-Dembasic.diagnostics.fontTiming=true", "clean", "verify"]
    try:
        proc = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                encoding="utf-8", errors="replace", bufsize=1)
        assert proc.stdout is not None
        shade = []
        for line in proc.stdout:
            sys.stdout.write(line)
            sys.stdout.flush()
            if "Including " in line and " in the shaded jar." in line:
                shade.append(line)
        if proc.wait() != 0:
            raise AuditError("maven_build_failed")
        return shade
    except OSError as exc:
        raise AuditError("maven_not_available") from exc

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--build", action="store_true", help="perform normal Maven verification and audit its actual runtime shade")
    args = parser.parse_args()
    try:
        if not args.build:
            raise AuditError("build_required_for_real_shade_trace")
        logs = run_maven()
        cache = Path.home() / ".m2" / "repository"
        archive = Path("app/target/embasic-public-desktop-0.1.0-SNAPSHOT-all.jar")
        report = inspect(logs, cache, archive)
        if report["publication_allowed"] is not False or report["status"] != "BLOCKED":
            raise AuditError("release_gate_incorrectly_opened")
        print("PUBLIC_SHADED_RUNTIME_EVIDENCE=" + json.dumps(report, sort_keys=True))
        return 0
    except (AuditError, OSError, ValueError) as exc:
        reason = str(exc) if isinstance(exc, AuditError) else "filesystem_read_error"
        print("PUBLIC_SHADED_RUNTIME_EVIDENCE: FAIL (" + reason + ")", file=sys.stderr)
        return 2

if __name__ == "__main__":
    sys.exit(main())
