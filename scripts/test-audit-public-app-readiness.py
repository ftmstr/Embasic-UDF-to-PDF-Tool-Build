#!/usr/bin/env python3
"""Synthetic Git fixtures only: exact-commit license/NOTICE inventory tests."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("public_app_audit",
    Path(__file__).with_name("audit-public-app-readiness.py"))
audit = importlib.util.module_from_spec(spec)
assert spec is not None and spec.loader is not None
spec.loader.exec_module(audit)
POM = ('<project xmlns="http://maven.apache.org/POM/4.0.0">'
       '<modelVersion>4.0.0</modelVersion><dependencies><dependency>'
       '<groupId>org.apache.pdfbox</groupId><artifactId>pdfbox</artifactId>'
       '<version>3.0.8</version></dependency></dependencies></project>')

class PublicAppInventoryTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.git("init", "-q")
        self.git("config", "user.name", "Synthetic CI")
        self.git("config", "user.email", "synthetic@example.invalid")
        self.write("app/pom.xml", POM)
        self.write("app/src/main/java/Synthetic.java", "class Synthetic {}\n")
        self.git("add", "app")
        self.git("commit", "-qm", "initial synthetic app")

    def tearDown(self):
        self.tmp.cleanup()

    def git(self, *args):
        return subprocess.run(["git", "-C", str(self.root), *args], check=True,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout

    def write(self, name, content):
        file = self.root / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(content, encoding="utf-8")

    def test_commit_and_dependency_inventory_always_blocked(self):
        manifest, result = audit.inventory(self.root)
        self.assertEqual(manifest["commit_sha"], self.git("rev-parse", "HEAD").decode().strip())
        self.assertEqual(result["tracked_app_file_count"], 2)
        self.assertEqual(manifest["direct_maven_dependencies"][0]["version"], "3.0.8")
        self.assertRegex(result["exact_commit_manifest_sha256"], r"^[0-9a-f]{64}$")
        self.assertEqual(result["source_license_document"], "MISSING")
        self.assertEqual(result["status"], "BLOCKED")
        self.assertFalse(result["publication_allowed"])

    def test_working_tree_mutation_cannot_change_exact_commit(self):
        original = audit.inventory(self.root)
        self.write("app/src/main/java/Synthetic.java", "class Mutated {}\n")
        self.write("app/src/main/java/Untracked.java", "class Untracked {}\n")
        self.assertEqual(audit.inventory(self.root), original)

    def test_notice_presence_is_not_license_approval(self):
        self.write("LICENSE", "synthetic placeholder")
        self.write("app/THIRD-PARTY-NOTICES.txt", "synthetic placeholder")
        self.git("add", "LICENSE", "app/THIRD-PARTY-NOTICES.txt")
        self.git("commit", "-qm", "placeholder")
        _, result = audit.inventory(self.root)
        self.assertEqual(result["source_license_document"], "PRESENT_UNREVIEWED")
        self.assertEqual(result["third_party_notice_document"], "PRESENT_UNREVIEWED")
        self.assertFalse(result["publication_allowed"])

    def test_reject_symlink(self):
        link = self.root / "app/symlink"
        try:
            link.symlink_to("pom.xml")
        except (OSError, NotImplementedError):
            self.skipTest("symlinks unsupported")
        self.git("add", "app/symlink")
        self.git("commit", "-qm", "bad symlink")
        with self.assertRaisesRegex(audit.AuditError, "symlink_or_nonregular_tree_entry"):
            audit.inventory(self.root)

    def test_reject_binary_artifact(self):
        self.write("app/bad.exe", "synthetic text only")
        self.git("add", "app/bad.exe")
        self.git("commit", "-qm", "bad artifact")
        with self.assertRaisesRegex(audit.AuditError, "restricted_app_artifact"):
            audit.inventory(self.root)

    def test_reject_missing_pom(self):
        self.git("rm", "-q", "app/pom.xml")
        self.git("commit", "-qm", "missing pom")
        with self.assertRaisesRegex(audit.AuditError, "app_source_or_pom_missing"):
            audit.inventory(self.root)

    def test_reject_unresolved_dependency(self):
        self.write("app/pom.xml", POM.replace("<version>3.0.8</version>", ""))
        self.git("add", "app/pom.xml")
        self.git("commit", "-qm", "no version")
        with self.assertRaisesRegex(audit.AuditError, "unresolved_dependency"):
            audit.inventory(self.root)

    def test_reject_manifest_export_inside_public_tree(self):
        manifest, _ = audit.inventory(self.root)
        with self.assertRaisesRegex(audit.AuditError, "manifest_output_inside_public_repo"):
            audit.export_local(self.root, self.root / "report.json", manifest)
        self.assertFalse((self.root / "report.json").exists())

    def test_deterministic_manifest(self):
        self.assertEqual(audit.inventory(self.root), audit.inventory(self.root))

if __name__ == "__main__":
    unittest.main(verbosity=2)
