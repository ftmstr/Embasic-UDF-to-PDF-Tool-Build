#!/usr/bin/env python3
"""Only fabricated data. No real logos, personal records or legal approvals."""
from __future__ import annotations
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

target = Path(__file__).with_name("verify-private-rights-evidence.py")
spec = importlib.util.spec_from_file_location("private_evidence_auditor", target)
auditor = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(auditor)

class PrivateRightsTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.base = Path(self.tmp.name)
        self.root = self.base / "prepared"
        self.root.mkdir()
        self.manifest = self.base / "snapshot-sha.json"
        self.approval = self.base / "private-review.json"
        self.commit = "a" * 40
        self.items = {
            "LICENSE": b"fictional project license sample",
            "THIRD-PARTY-iText.txt": b"fictional dependency license sample",
            "THIRD-PARTY-NOTICES.txt": b"fictional notices",
            "src/main/java/com/embasic/udf2pdf/App.java": b"// fictional Java stub",
        }
        for i, name in enumerate(auditor.BRAND):
            self.items[name] = ("synthetic byte asset " + str(i)).encode()
        for name, content in self.items.items():
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
        self.manifest.write_text(json.dumps({
            "schema_version": 1,
            "files": [{"path": p, "sha256": self.sha(data)}
                      for p, data in sorted(self.items.items())],
        }), "utf-8")
        self.rights = {
            "schema_version": 1, "private_commit_sha": self.commit,
            "snapshot_manifest_sha256": self.sha(self.manifest.read_bytes()),
            "project": {
                "rights_confirmed": True, "license_text_reviewed": True,
                "license_sha256": self.sha(self.items["LICENSE"]),
            },
            "itext": {
                "route": "MPL-1.1", "license_compliance_reviewed": True,
                "source_plan_reviewed": True, "relinking_plan_reviewed": False,
                "notice_sha256": self.sha(self.items["THIRD-PARTY-iText.txt"]),
            },
            "bouncycastle": {
                "license_reviewed": True, "six_coordinate_inventory_reviewed": True,
                "notice_sha256": self.sha(self.items["THIRD-PARTY-NOTICES.txt"]),
            },
            "branding": {
                "assets": {
                    name: {"sha256": self.sha(self.items[name]),
                           "origin_reviewed": True, "public_distribution_authorized": True}
                    for name in auditor.BRAND
                }
            },
            "signoff": {
                "owner_review_confirmed": True,
                "license_review_confirmed": True,
                "brand_review_confirmed": True,
                "private_record_reference": "FICTIONAL-REVIEW-001",
            },
        }
        self.write()

    @staticmethod
    def sha(data):
        return hashlib.sha256(data).hexdigest()

    def write(self):
        self.approval.write_text(json.dumps(self.rights, sort_keys=True), "utf-8")

    def check(self):
        return auditor.audit(self.root, self.manifest, self.approval, self.commit)

    def denied(self, message=None):
        with self.assertRaises(auditor.ApprovalError) as result:
            self.check()
        if message is not None:
            self.assertEqual(str(result.exception), message)

    def test_synthetic_mpl_review_consistent(self):
        result = self.check()
        self.assertEqual(result["reviewed_asset_count"], 4)
        self.assertFalse(result["release_or_publication_executed"])

    def test_synthetic_lgpl_with_relinking_acknowledgment(self):
        self.rights["itext"]["route"] = "LGPL-historical-reviewed"
        self.rights["itext"]["relinking_plan_reviewed"] = True
        self.write()
        self.assertEqual(self.check()["reviewed_notice_count"], 2)

    def test_lgpl_without_relinking_rejected(self):
        self.rights["itext"]["route"] = "LGPL-historical-reviewed"
        self.write()
        self.denied("itext_relinking_plan_not_attested")

    def test_arbitrary_unverified_lgpl_version_is_rejected(self):
        self.rights["itext"]["route"] = "LGPL-2.1"
        self.write()
        self.denied("itext_route_missing")

    def test_license_route_not_implicitly_chosen(self):
        self.rights["itext"]["route"] = "UNKNOWN"
        self.write()
        self.denied("itext_route_missing")

    def test_missing_owner_authorization_rejected(self):
        self.rights["project"]["rights_confirmed"] = False
        self.write()
        self.denied("project_rights_not_attested")

    def test_review_missing_license_file_rejected(self):
        (self.root / "LICENSE").unlink()
        self.denied("snapshot_integrity_failed")

    def test_license_hash_tamper_rejected(self):
        self.rights["project"]["license_sha256"] = "f" * 64
        self.write()
        self.denied("license_hash_mismatch")

    def test_notice_hash_change_rejected(self):
        self.rights["bouncycastle"]["notice_sha256"] = "f" * 64
        self.write()
        self.denied("bouncycastle_notice_hash_mismatch")

    def test_brand_redistribution_not_inferred_from_sha(self):
        name = auditor.BRAND[0]
        self.rights["branding"]["assets"][name]["public_distribution_authorized"] = False
        self.write()
        self.denied("brand_distribution_not_attested")

    def test_brand_origin_not_inferred_from_sha(self):
        name = auditor.BRAND[1]
        self.rights["branding"]["assets"][name]["origin_reviewed"] = False
        self.write()
        self.denied("brand_origin_not_attested")

    def test_brand_asset_must_match_bytes(self):
        name = auditor.BRAND[2]
        self.rights["branding"]["assets"][name]["sha256"] = "f" * 64
        self.write()
        self.denied("brand_asset_hash_mismatch")

    def test_each_brand_asset_required(self):
        del self.rights["branding"]["assets"][auditor.BRAND[3]]
        self.write()
        self.denied("brand_asset_set_incomplete")

    def test_sha_bound_snapshot_manifest_required(self):
        self.rights["snapshot_manifest_sha256"] = "f" * 64
        self.write()
        self.denied("snapshot_manifest_hash_mismatch")

    def test_exact_private_commit_required(self):
        self.commit = "b" * 40
        self.denied("private_commit_sha_mismatch")

    def test_private_record_signoff_required(self):
        self.rights["signoff"]["private_record_reference"] = ""
        self.write()
        self.denied("private_review_record_reference_missing")

    def test_brand_signoff_required(self):
        self.rights["signoff"]["brand_review_confirmed"] = False
        self.write()
        self.denied("brand_review_confirmed_not_attested")

    def test_approval_must_not_be_inside_snapshot(self):
        dest = self.root / "approvals.json"
        dest.write_bytes(self.approval.read_bytes())
        with self.assertRaises(auditor.ApprovalError):
            auditor.audit(self.root, self.manifest, dest, self.commit)

    def test_duplicate_json_key_rejected(self):
        self.approval.write_text('{"schema_version":1,"schema_version":1}', "utf-8")
        self.denied("external_approval_invalid_json")

    def test_snapshot_file_tampering_rejected_before_rights(self):
        (self.root / "THIRD-PARTY-NOTICES.txt").write_bytes(b"mutated")
        self.denied("snapshot_integrity_failed")

    def test_no_genuine_private_source_is_used(self):
        self.assertIn(b"fictional", self.items["LICENSE"])
        self.assertEqual(len(self.rights["branding"]["assets"]), 4)

if __name__ == "__main__":
    unittest.main(verbosity=2)
