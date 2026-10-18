#!/usr/bin/env python3
"""Synthetic-only, offline regressions for historical Maven dependency inspection."""
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
import warnings
import zipfile

target = Path(__file__).with_name("audit-cached-maven.py")
spec = importlib.util.spec_from_file_location("upstream_audit", target)
audit = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(audit)

def pom(coord, *, license_name=None, bc_deps=True):
    group, artifact, version = coord
    if license_name is None:
        license_name = "Mozilla Public License" if group == "com.lowagie" else "Bouncy Castle License"
    dep_text = ""
    if group == "com.lowagie" and bc_deps:
        for g, a, v in sorted(audit.BC):
            dep_text += f"<dependency><groupId>{g}</groupId><artifactId>{a}</artifactId><version>{v}</version></dependency>"
    return (f'<project xmlns="http://maven.apache.org/POM/4.0.0">'
        f"<groupId>{group}</groupId><artifactId>{artifact}</artifactId><version>{version}</version>"
        f"<licenses><license><name>{license_name}</name></license></licenses>"
        f"<dependencies>{dep_text}</dependencies></project>").encode()

def jar(members=None):
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w") as z:
        for name, content in (members or [("org/example/Sentinel.class", b"synthetic"),
                                          ("META-INF/LICENSE.txt", b"synthetic notice")]):
            z.writestr(name, content)
    return out.getvalue()

class DependencyAuditTests(unittest.TestCase):
    def test_known_itext_metadata(self):
        self.assertTrue(audit.inspect_pom(pom(audit.COMPONENTS[0]), audit.COMPONENTS[0])["declares_expected_bc_dependencies"])

    def test_itext_missing_transitives_rejected(self):
        with self.assertRaises(audit.AuditError):
            audit.inspect_pom(pom(audit.COMPONENTS[0], bc_deps=False), audit.COMPONENTS[0])

    def test_itext_wrong_license_rejected(self):
        with self.assertRaises(audit.AuditError):
            audit.inspect_pom(pom(audit.COMPONENTS[0], license_name="Other"), audit.COMPONENTS[0])

    def test_bc_wrong_license_rejected(self):
        with self.assertRaises(audit.AuditError):
            audit.inspect_pom(pom(audit.COMPONENTS[1], license_name="Other"), audit.COMPONENTS[1])

    def test_wrong_pom_version_rejected(self):
        with self.assertRaises(audit.AuditError):
            audit.inspect_pom(pom(("com.lowagie", "itext", "2.1.8")), audit.COMPONENTS[0])

    def test_corrupt_pom_rejected(self):
        with self.assertRaises(audit.AuditError):
            audit.inspect_pom(b"<not-valid", audit.COMPONENTS[0])

    def test_jar_notice_member_inventory(self):
        self.assertIn("META-INF/LICENSE.txt", audit.inspect_jar(jar())["notice_member_names"])

    def test_jar_unsafe_member_rejected(self):
        with self.assertRaises(audit.AuditError):
            audit.inspect_jar(jar([("../escape", b"x")]))

    def test_jar_duplicate_member_rejected(self):
        out = io.BytesIO()
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(out, "w") as z:
                z.writestr("item.class", b"one")
                z.writestr("item.class", b"two")
        with self.assertRaises(audit.AuditError):
            audit.inspect_jar(out.getvalue())

    def test_jar_corrupt_rejected(self):
        with self.assertRaises(audit.AuditError):
            audit.inspect_jar(b"not-a-jar")

    def test_all_four_upstream_file_hashes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for coord in audit.COMPONENTS:
                for ext, data in (("pom", pom(coord)), ("jar", jar())):
                    path = audit.artifact_path(root, coord, ext)
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(data)
            result = audit.read_inventory(root)
            self.assertEqual(len(result["components"]), 6)
            self.assertEqual(result["status"], "INSPECTED_NOT_DISTRIBUTION_APPROVED")
            self.assertTrue(all(len(c["jar_sha256"]) == 64 for c in result["components"]))

    def test_missing_upstream_file_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(audit.AuditError):
                audit.read_inventory(Path(tmp))

if __name__ == "__main__":
    unittest.main(verbosity=2)
