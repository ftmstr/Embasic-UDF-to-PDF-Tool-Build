#!/usr/bin/env python3
"""Rights-clean synthetic archive fixtures for exact shaded runtime audit."""
from __future__ import annotations
import importlib.util
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile

spec = importlib.util.spec_from_file_location("public_shade", Path(__file__).with_name("audit-public-shaded-runtime.py"))
audit = importlib.util.module_from_spec(spec)
assert spec is not None and spec.loader is not None
spec.loader.exec_module(audit)

def pom(group, artifact, version):
    return ('<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>'
            '<groupId>' + group + '</groupId><artifactId>' + artifact + '</artifactId>'
            '<version>' + version + '</version><licenses><license><name>Fixture License</name>'
            '</license></licenses></project>').encode()

class AuditSyntheticTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.cache = self.root / "m2"
        self.cache.mkdir()
        self.shade = self.root / "target" / "app-all.jar"
        self.shade.parent.mkdir()
        self.lines = []
        shaded = {"META-INF/LICENSE.txt": b"synthetic license marker",
                  "META-INF/NOTICE.txt": b"synthetic notice marker"}
        for i, (coordinate, selected) in enumerate(sorted(audit.CLASSES.items())):
            group, artifact, version = coordinate
            base = self.cache.joinpath(*group.split("."), artifact, version)
            base.mkdir(parents=True)
            (base / (artifact + "-" + version + ".pom")).write_bytes(pom(*coordinate))
            value = bytes((i, 99, 77, 23))
            with ZipFile(base / (artifact + "-" + version + ".jar"), "w") as jar:
                jar.writestr(selected, value)
                jar.writestr("META-INF/LICENSE.txt", "synthetic component license " + artifact)
                jar.writestr("META-INF/NOTICE.txt", "synthetic")
            shaded[selected] = value
            self.lines.append("[INFO] Including " + group + ":" + artifact + ":jar:" + version + " in the shaded jar.")
        with ZipFile(self.shade, "w") as archive:
            for key, value in shaded.items():
                archive.writestr(key, value)

    def tearDown(self):
        self.tmp.cleanup()

    def test_shared_legal_namespace_detects_distinct_and_identical_sources(self):
        report = audit.inspect(self.lines, self.cache, self.shade)
        paths = {e["member"]: e for e in report["shared_legal_namespace_collisions"]}
        self.assertEqual(report["shared_legal_namespace_collision_count"], 2)
        self.assertEqual(paths["META-INF/LICENSE.txt"]["source_count"], 4)
        self.assertEqual(paths["META-INF/LICENSE.txt"]["status"],
                         "DISTINCT_UPSTREAM_LEGAL_BYTES_COLLIDE")
        self.assertEqual(paths["META-INF/NOTICE.txt"]["status"],
                         "SHARED_LEGAL_PATH_ATTRIBUTION_REVIEW_REQUIRED")
        self.assertFalse(paths["META-INF/NOTICE.txt"]["shaded_exact_for_all"])
        self.assertEqual(report["legal_attribution_clearance"],
                         "BLOCKED_SHARED_NAMESPACE_OR_INDEPENDENT_REVIEW")
        self.assertEqual(report["unresolved_upstream_license_notice_count"], 8)
        self.assertFalse(report["publication_allowed"])

    def test_missing_upstream_notice_fails_closed(self):
        coord, selected = sorted(audit.CLASSES.items())[0]
        group, artifact, version = coord
        path = self.cache.joinpath(*group.split("."), artifact, version,
                                   artifact + "-" + version + ".jar")
        with ZipFile(path) as existing:
            original_class = existing.read(selected)
        with ZipFile(path, "w") as archive:
            archive.writestr(selected, original_class)
            archive.writestr("META-INF/LICENSE.txt", b"synthetic license")
        with self.assertRaisesRegex(audit.AuditError, "upstream_license_or_notice_missing"):
            audit.inspect(self.lines, self.cache, self.shade)

    def test_missing_shaded_notice_fails_closed(self):
        with ZipFile(self.shade, "w") as archive:
            archive.writestr("META-INF/LICENSE.txt", b"synthetic license")
        with self.assertRaisesRegex(audit.AuditError, "shaded_license_or_notice_missing"):
            audit.inspect(self.lines, self.cache, self.shade)

    def test_empty_upstream_legal_member_fails_closed(self):
        coord, selected = sorted(audit.CLASSES.items())[0]
        group, artifact, version = coord
        path = self.cache.joinpath(*group.split("."), artifact, version,
                                   artifact + "-" + version + ".jar")
        with ZipFile(path) as existing:
            original_class = existing.read(selected)
        with ZipFile(path, "w") as archive:
            archive.writestr(selected, original_class)
            archive.writestr("META-INF/LICENSE.txt", b"")
            archive.writestr("META-INF/NOTICE.txt", b"synthetic")
        with self.assertRaisesRegex(audit.AuditError, "license_notice_member_empty_or_too_large"):
            audit.inspect(self.lines, self.cache, self.shade)

    def test_platform_maven_launcher(self):
        self.assertEqual(audit.maven_launcher("win32"), "mvn.cmd")
        self.assertEqual(audit.maven_launcher("linux"), "mvn")

    def test_four_runtime_components_exact_hashes_block_release(self):
        result = audit.inspect(self.lines, self.cache, self.shade)
        self.assertEqual(result["resolved_shade_component_count"], 4)
        self.assertEqual(len(result["resolved_shade_components"]), 4)
        self.assertEqual(result["status"], "BLOCKED")
        self.assertFalse(result["publication_allowed"])
        self.assertEqual(result["native_uyap_golden"], "NOT_VERIFIED")
        self.assertRegex(result["resolved_evidence_sha256"], r"^[a-f0-9]{64}$")
        self.assertEqual(result["resolved_shade_components"][0]["declared_pom_licenses_unreviewed"], ["Fixture License"])

    def test_unexpected_dependency_fails_closed(self):
        lines = self.lines + ["[INFO] Including extra:unexpected:jar:1.0 in the shaded jar."]
        with self.assertRaisesRegex(audit.AuditError, "unexpected_or_missing_shaded_dependency"):
            audit.inspect(lines, self.cache, self.shade)

    def test_missing_dependency_fails_closed(self):
        with self.assertRaisesRegex(audit.AuditError, "unexpected_or_missing_shaded_dependency"):
            audit.inspect(self.lines[1:], self.cache, self.shade)

    def test_upstream_class_drift_fails_closed(self):
        coord, selected = sorted(audit.CLASSES.items())[0]
        group, name, version = coord
        path = self.cache.joinpath(*group.split("."), name, version, name + "-" + version + ".jar")
        with ZipFile(path, "w") as jar:
            jar.writestr(selected, b"changed")
        with self.assertRaisesRegex(audit.AuditError, "selected_shaded_class_differs"):
            audit.inspect(self.lines, self.cache, self.shade)

    def test_symlinked_cached_pom_rejected(self):
        coord = sorted(audit.CLASSES)[0]
        group, name, version = coord
        base = self.cache.joinpath(*group.split("."), name, version)
        path = base / (name + "-" + version + ".pom")
        other = self.root / "other-pom"
        other.write_bytes(path.read_bytes())
        path.unlink()
        try:
            path.symlink_to(other)
        except (OSError, NotImplementedError):
            self.skipTest("symlink unsupported")
        with self.assertRaisesRegex(audit.AuditError, "symlink_artifact"):
            audit.inspect(self.lines, self.cache, self.shade)

    def test_malformed_jar_rejected(self):
        self.shade.write_bytes(b"not a zip")
        with self.assertRaisesRegex(audit.AuditError, "invalid_jar_archive"):
            audit.inspect(self.lines, self.cache, self.shade)

    def test_duplicate_shade_trace_rejected(self):
        with self.assertRaisesRegex(audit.AuditError, "duplicate_shaded_dependency"):
            audit.inspect(self.lines + self.lines[:1], self.cache, self.shade)

    def test_pom_metadata_absence_never_grants_license(self):
        coord = sorted(audit.CLASSES)[0]
        group, name, version = coord
        path = self.cache.joinpath(*group.split("."), name, version, name + "-" + version + ".pom")
        path.write_bytes(pom(*coord).replace(b"<licenses><license><name>Fixture License</name></license></licenses>", b""))
        result = audit.inspect(self.lines, self.cache, self.shade)
        self.assertEqual(result["status"], "BLOCKED")
        self.assertFalse(result["publication_allowed"])

if __name__ == "__main__":
    unittest.main(verbosity=2)
