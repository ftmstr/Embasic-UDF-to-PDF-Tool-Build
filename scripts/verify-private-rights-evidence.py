#!/usr/bin/env python3
"""Fail-closed, OFFLINE review of *external* source/brand/license attestations.

This is a consistency check, NOT an ownership, signature or legal opinion.
No application content, approval record, reviewer name, or logo is printed.
Do not put an actual approval JSON or private snapshot into a Public repo.
"""
from __future__ import annotations
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys

SIBLING = Path(__file__).with_name("verify-snapshot-manifest.py")
spec = importlib.util.spec_from_file_location("snapshot_verify_private", SIBLING)
snapshot = importlib.util.module_from_spec(spec)
assert spec is not None and spec.loader is not None
spec.loader.exec_module(snapshot)

BRAND = (
    "app-icon.ico",
    "src/main/resources/com/embasic/udf2pdf/brand/app-icon.png",
    "src/main/resources/com/embasic/udf2pdf/brand/logo-dark.png",
    "src/main/resources/com/embasic/udf2pdf/brand/logo-light.png",
)
REQUIRED_FILES = ("LICENSE", "THIRD-PARTY-iText.txt", "THIRD-PARTY-NOTICES.txt", *BRAND)
SHA40 = re.compile(r"[a-f0-9]{40}\Z")
SHA64 = re.compile(r"[a-f0-9]{64}\Z")
MAX_APPROVAL = 128 * 1024

class ApprovalError(Exception):
    pass

def strict_dict(value, fields, category):
    if not isinstance(value, dict) or set(value) != set(fields):
        raise ApprovalError(category + "_schema_invalid")
    return value

def yes(value, category):
    if value is not True:
        raise ApprovalError(category + "_not_attested")

def same_hash(recorded, expected, category):
    if not isinstance(recorded, str) or not SHA64.fullmatch(recorded) or recorded != expected:
        raise ApprovalError(category + "_hash_mismatch")

def full_hash(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

def audit(root: Path, manifest: Path, approval: Path, private_commit: str) -> dict:
    if not isinstance(private_commit, str) or not SHA40.fullmatch(private_commit):
        raise ApprovalError("private_commit_sha_invalid")
    # Execute the full prior SHA manifest check; no scan result is treated as rights evidence.
    try:
        checked = snapshot.inspect_snapshot(root, manifest)
    except snapshot.SnapshotError as exc:
        raise ApprovalError("snapshot_integrity_failed") from exc
    if approval.is_symlink() or not approval.is_file() or approval.stat().st_size > MAX_APPROVAL:
        raise ApprovalError("external_approval_missing_or_oversized")
    if approval.resolve() == root.resolve() or root.resolve() in approval.resolve().parents:
        raise ApprovalError("approval_must_remain_outside_snapshot")
    try:
        rights = json.loads(approval.read_text("utf-8"),
                            object_pairs_hook=snapshot.no_duplicate_object_keys)
    except (UnicodeError, json.JSONDecodeError, snapshot.SnapshotError) as exc:
        raise ApprovalError("external_approval_invalid_json") from exc
    strict_dict(rights, ("schema_version", "private_commit_sha",
        "snapshot_manifest_sha256", "project", "itext", "bouncycastle",
        "branding", "signoff"), "top_level")
    if type(rights["schema_version"]) is not int or rights["schema_version"] != 1:
        raise ApprovalError("approval_version_invalid")
    if rights["private_commit_sha"] != private_commit:
        raise ApprovalError("private_commit_sha_mismatch")
    same_hash(rights["snapshot_manifest_sha256"], checked["manifest_sha256"],
              "snapshot_manifest")
    # The complete prepared snapshot was already hash-verified.
    # Check all reviewed files are present and bind their byte hashes.
    for name in REQUIRED_FILES:
        if not (root / name).is_file():
            raise ApprovalError("reviewed_required_file_missing")
    project = strict_dict(rights["project"],
        ("rights_confirmed", "license_text_reviewed", "license_sha256"),
        "project")
    yes(project["rights_confirmed"], "project_rights")
    yes(project["license_text_reviewed"], "project_license")
    same_hash(project["license_sha256"], full_hash(root / "LICENSE"), "license")
    itext = strict_dict(rights["itext"],
        ("route", "license_compliance_reviewed", "source_plan_reviewed",
         "relinking_plan_reviewed", "notice_sha256"), "itext")
    if itext["route"] not in ("MPL-1.1", "LGPL-2.1"):
        raise ApprovalError("itext_route_missing")
    yes(itext["license_compliance_reviewed"], "itext_compliance")
    yes(itext["source_plan_reviewed"], "itext_source_plan")
    if itext["route"] == "LGPL-2.1":
        yes(itext["relinking_plan_reviewed"], "itext_relinking_plan")
    elif type(itext["relinking_plan_reviewed"]) is not bool:
        raise ApprovalError("itext_relinking_review_value_invalid")
    same_hash(itext["notice_sha256"], full_hash(root / "THIRD-PARTY-iText.txt"),
              "itext_notice")
    bc = strict_dict(rights["bouncycastle"],
        ("license_reviewed", "six_coordinate_inventory_reviewed",
         "notice_sha256"), "bouncycastle")
    yes(bc["license_reviewed"], "bouncycastle_license")
    yes(bc["six_coordinate_inventory_reviewed"], "bouncycastle_inventory")
    same_hash(bc["notice_sha256"],
              full_hash(root / "THIRD-PARTY-NOTICES.txt"), "bouncycastle_notice")
    brand = strict_dict(rights["branding"], ("assets",), "branding")
    assets = brand["assets"]
    if not isinstance(assets, dict) or set(assets) != set(BRAND):
        raise ApprovalError("brand_asset_set_incomplete")
    for name in BRAND:
        item = strict_dict(assets[name],
            ("sha256", "origin_reviewed", "public_distribution_authorized"),
            "brand_asset")
        yes(item["origin_reviewed"], "brand_origin")
        yes(item["public_distribution_authorized"], "brand_distribution")
        same_hash(item["sha256"], full_hash(root / name), "brand_asset")
    signoff = strict_dict(rights["signoff"],
        ("owner_review_confirmed", "license_review_confirmed",
         "brand_review_confirmed", "private_record_reference"), "signoff")
    for name in ("owner_review_confirmed", "license_review_confirmed",
                 "brand_review_confirmed"):
        yes(signoff[name], name)
    record = signoff["private_record_reference"]
    if not isinstance(record, str) or not 4 <= len(record) <= 128 or any(ord(c) < 32 for c in record):
        raise ApprovalError("private_review_record_reference_missing")
    return {
        "status": "EXTERNAL_ATTESTATIONS_AND_SNAPSHOT_HASHES_MATCH_NOT_LEGAL_CERTIFICATION",
        "reviewed_asset_count": len(BRAND),
        "snapshot_file_count": checked["file_count"],
        "reviewed_notice_count": 2,
        "release_or_publication_executed": False,
    }

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot-root", type=Path, required=True)
    parser.add_argument("--snapshot-manifest", type=Path, required=True)
    parser.add_argument("--rights-approval", type=Path, required=True)
    parser.add_argument("--private-commit", required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(audit(args.snapshot_root, args.snapshot_manifest,
                               args.rights_approval, args.private_commit),
                         sort_keys=True))
        return 0
    except (OSError, ValueError, ApprovalError) as exc:
        code = str(exc) if isinstance(exc, ApprovalError) else "filesystem_or_format_error"
        print("RIGHTS_EVIDENCE: BLOCKED (" + code + ")", file=sys.stderr)
        return 2

if __name__ == "__main__":
    sys.exit(main())
