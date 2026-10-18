#!/usr/bin/env python3
"""Synthetic fixtures for isolated shaded-JAR and duplicate legacy components."""
from __future__ import annotations
import importlib.util
import tempfile
import unittest
import zipfile
from pathlib import Path

here = Path(__file__).with_name("audit-isolated-shade.py")
spec = importlib.util.spec_from_file_location("isolated_shade", here)
audit = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(audit)

def archive(path: Path, entries: dict[str, bytes]):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as zf:
        for name, data in entries.items():
            zf.writestr(name, data)

class ShadedAuditTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.jars = {}
        self.classes = {cls: ("class-fixture:" + cls).encode()
                        for cls in set(audit.DEPENDENCIES.values())}
        for coordinate, cls in audit.DEPENDENCIES.items():
            path = self.root / ("_".join(coordinate) + ".jar")
            archive(path, {cls: self.classes[cls]})
            self.jars[coordinate] = path
        self.merged = self.root / "synthetic-shaded.jar"
        self.make_shade()

    def tearDown(self):
        self.tmp.cleanup()

    def make_shade(self, overrides=None):
        payload = {audit.AUDIT_MAIN: b"synthetic audit only", **self.classes}
        payload.update(overrides or {})
        archive(self.merged, payload)

    def test_valid_all_six_classes(self):
        result = audit.check_shaded(self.merged, self.jars)
        self.assertEqual(len(result["verified_components"]), 6)
        self.assertTrue(result["legacy_relocated_jar_pairs_identical"])

    def test_missing_class_rejected(self):
        archive(self.merged, {audit.AUDIT_MAIN: b"synthetic"})
        with self.assertRaises(audit.AuditFailure):
            audit.check_shaded(self.merged, self.jars)

    def test_different_class_bytes_rejected(self):
        target = next(iter(self.classes))
        self.make_shade({target: b"altered"})
        with self.assertRaises(audit.AuditFailure):
            audit.check_shaded(self.merged, self.jars)

    def test_private_application_marker_rejected(self):
        self.make_shade({audit.FORBIDDEN_APP: b"not allowed"})
        with self.assertRaises(audit.AuditFailure):
            audit.check_shaded(self.merged, self.jars)

    def test_missing_isolated_probe_rejected(self):
        archive(self.merged, self.classes)
        with self.assertRaises(audit.AuditFailure):
            audit.check_shaded(self.merged, self.jars)

    def test_incomplete_dependency_map_rejected(self):
        with self.assertRaises(audit.AuditFailure):
            audit.check_shaded(self.merged, dict(list(self.jars.items())[:-1]))

    def test_relocated_dependency_bytes_must_match(self):
        c = ("org.bouncycastle", "bcprov-jdk14", "1.38")
        archive(self.jars[c], {audit.DEPENDENCIES[c]: self.classes[audit.DEPENDENCIES[c]],
                               "another-entry": b"different"})
        with self.assertRaises(audit.AuditFailure):
            audit.check_shaded(self.merged, self.jars)

    def test_unsafe_archive_path_rejected(self):
        archive(self.merged, {"../escape": b"bad", audit.AUDIT_MAIN: b"synthetic"})
        with self.assertRaises(audit.AuditFailure):
            audit.check_shaded(self.merged, self.jars)

    def test_corrupt_archive_rejected(self):
        self.merged.write_bytes(b"not-a-jar")
        with self.assertRaises(audit.AuditFailure):
            audit.check_shaded(self.merged, self.jars)

if __name__ == "__main__":
    unittest.main(verbosity=2)
