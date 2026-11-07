#!/usr/bin/env python3
"""Synthetic-only checks. No private documents or approval manifests."""
from __future__ import annotations
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

target = Path(__file__).with_name("verify-snapshot-manifest.py")
spec = importlib.util.spec_from_file_location("snapshot_verifier", target)
verifier = importlib.util.module_from_spec(spec)
assert spec is not None and spec.loader is not None
spec.loader.exec_module(verifier)

class ManifestRegressionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "prepared"
        self.root.mkdir()
        self.manifest = Path(self.temp.name) / "reviewed-manifest.json"
        self.fixture = {
            "src/main/java/Synthetic.java": b"// generated dummy test only",
            "LICENSE": b"synthetic fixture terms",
        }
        self.refresh()

    def refresh(self):
        for name, payload in self.fixture.items():
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(payload)
        items = [
            {"path": name, "sha256": hashlib.sha256(value).hexdigest()}
            for name, value in sorted(self.fixture.items())
        ]
        self.manifest.write_text(json.dumps({"schema_version":1,"files":items}), "utf-8")

    def check(self):
        return verifier.inspect_snapshot(self.root, self.manifest)

    def test_exact_prepared_snapshot_pass(self):
        result = self.check()
        self.assertEqual(result["file_count"], 2)
        self.assertEqual(result["status"], "SNAPSHOT_BYTES_MATCH_ONLY_NOT_PUBLICATION_APPROVED")

    def test_any_byte_change_fails(self):
        (self.root / "LICENSE").write_bytes(b"different bytes")
        with self.assertRaises(verifier.SnapshotError):
            self.check()

    def test_unlisted_file_fails(self):
        (self.root / "unexpected.txt").write_text("other", "utf-8")
        with self.assertRaisesRegex(verifier.SnapshotError, "unlisted_snapshot_file"):
            self.check()

    def test_missing_file_fails(self):
        (self.root / "LICENSE").unlink()
        with self.assertRaisesRegex(verifier.SnapshotError, "manifest_file_missing"):
            self.check()

    def test_path_traversal_fails(self):
        payload = json.loads(self.manifest.read_text("utf-8"))
        payload["files"][0]["path"] = "../elsewhere"
        self.manifest.write_text(json.dumps(payload), "utf-8")
        with self.assertRaises(verifier.SnapshotError):
            self.check()

    def test_duplicate_path_fails(self):
        payload = json.loads(self.manifest.read_text("utf-8"))
        payload["files"].append(payload["files"][0])
        self.manifest.write_text(json.dumps(payload), "utf-8")
        with self.assertRaisesRegex(verifier.SnapshotError, "duplicate_manifest_path"):
            self.check()

    def test_duplicate_json_key_fails(self):
        self.manifest.write_text('{"schema_version":1,"schema_version":1,"files":[]}', "utf-8")
        with self.assertRaisesRegex(verifier.SnapshotError, "duplicate_manifest_key"):
            self.check()

    def test_manifest_inside_snapshot_fails(self):
        nested = self.root / "review.json"
        nested.write_bytes(self.manifest.read_bytes())
        with self.assertRaisesRegex(verifier.SnapshotError, "manifest_must_remain_outside_snapshot"):
            verifier.inspect_snapshot(self.root, nested)

    def test_git_metadata_in_snapshot_fails(self):
        (self.root / ".git").mkdir()
        with self.assertRaisesRegex(verifier.SnapshotError, "unsafe_snapshot_path"):
            self.check()

    def test_genuine_document_extension_rejected(self):
        self.fixture["fixtures/sample.udf"] = b"synthetic"
        self.refresh()
        with self.assertRaisesRegex(verifier.SnapshotError, "restricted_snapshot_extension"):
            self.check()

    def test_symlink_in_snapshot_fails(self):
        link = self.root / "other-link.txt"
        try:
            link.symlink_to(self.root / "LICENSE")
        except (OSError, NotImplementedError):
            self.skipTest("symlinks unavailable")
        with self.assertRaisesRegex(verifier.SnapshotError, "symlink_or_nonregular_file"):
            self.check()

    def test_invalid_manifest_hash_fails(self):
        payload = json.loads(self.manifest.read_text("utf-8"))
        payload["files"][0]["sha256"] = "ff"
        self.manifest.write_text(json.dumps(payload), "utf-8")
        with self.assertRaisesRegex(verifier.SnapshotError, "manifest_hash_invalid"):
            self.check()

    def test_empty_manifest_does_not_approve(self):
        self.manifest.write_text('{"schema_version":1,"files":[]}', "utf-8")
        with self.assertRaisesRegex(verifier.SnapshotError, "manifest_file_count_invalid"):
            self.check()

if __name__ == "__main__":
    unittest.main(verbosity=2)
