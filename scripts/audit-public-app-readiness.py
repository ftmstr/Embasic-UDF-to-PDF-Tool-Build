#!/usr/bin/env python3
"""Public app exact-HEAD inventory; no source, license or release approval."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

class AuditError(Exception):
    pass

def git(repo, *args):
    try:
        return subprocess.run(["git", "-C", str(repo), *args], check=True,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                              timeout=20).stdout
    except (OSError, subprocess.TimeoutExpired, subprocess.CalledProcessError) as exc:
        raise AuditError("git_read_failed") from exc

def inventory(repo: Path):
    if not repo.is_dir():
        raise AuditError("checkout_missing")
    sha = git(repo, "rev-parse", "--verify", "HEAD^{commit}").decode("ascii").strip()
    if not re.fullmatch(r"[a-f0-9]{40}", sha):
        raise AuditError("invalid_commit_sha")
    entries = git(repo, "ls-tree", "--full-tree", "-r", "-z", "HEAD", "--", "app/")
    files, pom, total = [], None, 0
    denied = {".udf", ".pdf", ".exe", ".dll", ".jar", ".zip",
              ".pfx", ".p12", ".pem", ".key", ".db", ".sqlite", ".log"}
    for raw in filter(None, entries.split(b"\0")):
        try:
            meta, encoded_name = raw.split(b"\t", 1)
            mode, kind, oid = meta.decode("ascii").split(" ")
            name = encoded_name.decode("utf-8")
        except (UnicodeDecodeError, ValueError) as exc:
            raise AuditError("invalid_tree_entry") from exc
        path = PurePosixPath(name)
        if (not name.startswith("app/") or path.is_absolute() or "\\" in name
                or any(piece in ("", ".", "..") for piece in name.split("/"))
                or any(ord(ch) < 32 for ch in name) or len(name) > 512):
            raise AuditError("unsafe_app_path")
        if path.suffix.lower() in denied or path.name.lower() in (".env", "id_rsa", "id_ed25519"):
            raise AuditError("restricted_app_artifact")
        if mode not in ("100644", "100755") or kind != "blob" or not re.fullmatch(r"[a-f0-9]{40}", oid):
            raise AuditError("symlink_or_nonregular_tree_entry")
        try:
            size = int(git(repo, "cat-file", "-s", oid).decode("ascii"))
        except (ValueError, UnicodeDecodeError) as exc:
            raise AuditError("invalid_blob_size") from exc
        if size < 0 or size > 2 * 1024 * 1024:
            raise AuditError("file_size_limit")
        content = git(repo, "cat-file", "blob", oid)
        if len(content) != size:
            raise AuditError("blob_size_mismatch")
        total += size
        if total > 100 * 1024 * 1024 or len(files) >= 300:
            raise AuditError("inventory_limit")
        files.append({"path": name, "git_mode": mode, "git_blob_sha1": oid,
                      "sha256": hashlib.sha256(content).hexdigest(), "size": size})
        if name == "app/pom.xml":
            pom = content
    if pom is None or not any(f["path"].startswith("app/src/main/") for f in files):
        raise AuditError("app_source_or_pom_missing")
    if b"<!DOCTYPE" in pom.upper() or b"<!ENTITY" in pom.upper():
        raise AuditError("unsafe_pom")
    try:
        root = ET.fromstring(pom)
    except ET.ParseError as exc:
        raise AuditError("invalid_pom") from exc
    ns = "{http://maven.apache.org/POM/4.0.0}"
    if root.tag != ns + "project":
        raise AuditError("invalid_pom_namespace")
    dependencies, seen = [], set()
    for dep in root.findall(f"{ns}dependencies/{ns}dependency"):
        def val(field):
            element = dep.find(ns + field)
            return "" if element is None or element.text is None else element.text.strip()
        group, artifact, version, scope = val("groupId"), val("artifactId"), val("version"), val("scope") or "compile"
        if not group or not artifact or not version or any("$" + "{" in part for part in (group, artifact, version)):
            raise AuditError("unresolved_dependency")
        if scope not in ("compile", "test", "runtime", "provided", "system"):
            raise AuditError("unknown_dependency_scope")
        if (group, artifact, scope) in seen:
            raise AuditError("duplicate_dependency")
        seen.add((group, artifact, scope))
        dependencies.append({"group_id": group, "artifact_id": artifact,
                             "version": version, "scope": scope})
    dependencies.sort(key=lambda x: (x["group_id"], x["artifact_id"], x["scope"]))
    # Build plugins and shade configuration affect what the distributable contains.
    # Record their exact declared coordinates; this is NOT a transitive SBOM.
    plugins = []
    seen_plugins = set()
    for plugin in root.findall(f"{ns}build/{ns}plugins/{ns}plugin"):
        def plugin_val(field):
            element = plugin.find(ns + field)
            return "" if element is None or element.text is None else element.text.strip()
        group = plugin_val("groupId") or "org.apache.maven.plugins"
        artifact, version = plugin_val("artifactId"), plugin_val("version")
        if not artifact or not version or any("${" in x for x in (group, artifact, version)):
            raise AuditError("unresolved_build_plugin")
        if (group, artifact) in seen_plugins:
            raise AuditError("duplicate_build_plugin")
        seen_plugins.add((group, artifact))
        plugins.append({"group_id": group, "artifact_id": artifact, "version": version})
    plugins.sort(key=lambda x: (x["group_id"], x["artifact_id"]))
    shade = [p for p in plugins if p["artifact_id"] == "maven-shade-plugin"
             and p["group_id"] == "org.apache.maven.plugins"]
    if any(p["artifact_id"] == "maven-shade-plugin" and p["group_id"] != "org.apache.maven.plugins"
           for p in plugins):
        raise AuditError("unexpected_shade_plugin_group")
    shade_config = "NOT_CONFIGURED"
    if shade:
        shade_node = next(p for p in root.findall(f"{ns}build/{ns}plugins/{ns}plugin")
                          if (p.findtext(ns + "artifactId") or "").strip() == "maven-shade-plugin")
        executions = shade_node.findall(f"{ns}executions/{ns}execution")
        shade_config = "DECLARED_UNREVIEWED"
        if not any("shade" in [(g.text or "").strip() for g in e.findall(f"{ns}goals/{ns}goal")]
                   for e in executions):
            raise AuditError("shade_goal_missing")
    present = set(git(repo, "ls-tree", "-r", "--name-only", "HEAD").decode("utf-8").splitlines())
    files.sort(key=lambda x: x["path"])
    manifest = {"schema_version": 1, "commit_sha": sha, "app_files": files,
                "direct_maven_dependencies": dependencies,
                "declared_build_plugins": plugins, "shade_configuration": shade_config}
    digest = hashlib.sha256(json.dumps(manifest, sort_keys=True, ensure_ascii=False,
                            separators=(",", ":")).encode("utf-8")).hexdigest()
    summary = {"commit_sha": sha, "tracked_app_file_count": len(files),
               "tracked_app_bytes": total, "exact_commit_manifest_sha256": digest,
               "direct_dependency_count": len(dependencies),
               "declared_build_plugin_count": len(plugins),
               "shade_configuration": shade_config,
               "source_license_document": "PRESENT_UNREVIEWED" if present & {"LICENSE", "app/LICENSE"} else "MISSING",
               "third_party_notice_document": "PRESENT_UNREVIEWED" if present & {"THIRD-PARTY-NOTICES.txt", "app/THIRD-PARTY-NOTICES.txt"} else "MISSING",
               "shaded_transitive_dependencies": "NOT_REVIEWED",
               "owner_and_source_rights": "NOT_VERIFIED",
               "font_and_brand_redistribution": "NOT_VERIFIED",
               "native_uyap_golden": "NOT_VERIFIED",
               "publication_allowed": False, "status": "BLOCKED"}
    return manifest, summary

def export_local(repo: Path, target: Path, manifest: dict):
    target = target.absolute()
    # Windows may expose the same temporary directory through two aliases;
    # check canonical existing parent before choosing the output file.
    canonical_target = target.parent.resolve(strict=True) / target.name
    if canonical_target.is_relative_to(repo.resolve()):
        raise AuditError("manifest_output_inside_public_repo")
    try:
        fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            json.dump(manifest, stream, ensure_ascii=False, sort_keys=True, indent=2)
            stream.write("\n")
    except OSError as exc:
        raise AuditError("manifest_export_failed") from exc

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    ap.add_argument("--manifest-out", type=Path)
    ap.add_argument("--expect-blocked", action="store_true")
    args = ap.parse_args()
    try:
        repo = args.repo.resolve()
        manifest, summary = inventory(repo)
        if args.manifest_out:
            export_local(repo, args.manifest_out, manifest)
        if args.expect_blocked and (summary["status"] != "BLOCKED" or summary["publication_allowed"] is not False):
            raise AuditError("unexpected_release_authorization")
        print(json.dumps(summary, sort_keys=True))
        return 0
    except AuditError as exc:
        print("PUBLIC_APP_INVENTORY: ERROR (" + str(exc) + ")", file=sys.stderr)
        return 2

if __name__ == "__main__":
    sys.exit(main())
