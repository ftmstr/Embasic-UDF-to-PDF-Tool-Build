#!/usr/bin/env python3
"""Fully synthetic regressions for identical and divergent class collisions."""
from __future__ import annotations
import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

base = Path(__file__).parent
def load(name: str, key: str):
    spec = importlib.util.spec_from_file_location(key, base / name)
    obj = importlib.util.module_from_spec(spec)
    assert spec is not None and spec.loader is not None
    spec.loader.exec_module(obj)
    return obj

probe = load("audit-isolated-shade.py", "shade_probe_tests")
full = load("audit-shaded-classes.py", "shade_full_tests")

def make_zip(path: Path, members: dict[str,bytes]):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,"w") as z:
        for name, value in members.items():
            z.writestr(name,value)

class FullClassesTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name)
        self.deps={}
        self.classes={c:("bytecode:"+c).encode() for c in set(probe.DEPENDENCIES.values())}
        for coord, classname in probe.DEPENDENCIES.items():
            path=probe.maven_path(self.root,coord)
            make_zip(path,{classname:self.classes[classname]})
            self.deps[coord]=path
        self.shaded=self.root/"audit-shade.jar"
        self.rebuild()

    def rebuild(self, modifications=None):
        merged={probe.AUDIT_MAIN:b"minimal synthetic Java"}
        merged.update(self.classes)
        merged.update(modifications or {})
        make_zip(self.shaded,merged)

    def test_every_class_verified(self):
        report=full.class_inventory(self.shaded,self.deps)
        self.assertEqual(report["unique_upstream_classes_verified"],4)
        self.assertEqual(report["identical_duplicate_class_occurrences"],2)

    def test_application_candidate_mode_with_synthetic_local_classes(self):
        fake = {"com/embasic/udf2pdf/App.class": b"synthetic-app-marker",
                "com/embasic/udf2pdf/Model.class": b"synthetic-model-marker"}
        make_zip(self.shaded, {**self.classes, **fake})
        result = full.class_inventory(self.shaded, self.deps, mode="application")
        self.assertEqual(result["mode"], "application")
        self.assertFalse(result["license_or_distribution_approved"])

    def test_application_candidate_requires_app_entry(self):
        make_zip(self.shaded, self.classes)
        with self.assertRaises(full.AuditFailure):
            full.class_inventory(self.shaded, self.deps, mode="application")

    def test_application_candidate_rejects_foreign_class(self):
        fake = {"com/embasic/udf2pdf/App.class": b"synthetic-app-marker",
                "com/example/Unknown.class": b"unexpected"}
        make_zip(self.shaded, {**self.classes, **fake})
        with self.assertRaises(full.AuditFailure):
            full.class_inventory(self.shaded, self.deps, mode="application")

    def test_application_candidate_rejects_audit_probe(self):
        fake = {"com/embasic/udf2pdf/App.class": b"synthetic-app-marker",
                probe.AUDIT_MAIN: b"synthetic-probe"}
        make_zip(self.shaded, {**self.classes, **fake})
        with self.assertRaises(full.AuditFailure):
            full.class_inventory(self.shaded, self.deps, mode="application")

    def test_divergent_duplicate_class_rejected(self):
        new=("org.bouncycastle","bcmail-jdk14","1.38")
        cls=probe.DEPENDENCIES[new]
        make_zip(self.deps[new],{cls:b"not the same as legacy"})
        with self.assertRaises(full.AuditFailure):
            full.class_inventory(self.shaded,self.deps)

    def test_changed_shaded_class_rejected(self):
        classname=probe.DEPENDENCIES[("com.lowagie","itext","2.1.7")]
        self.rebuild({classname:b"tampered"})
        with self.assertRaises(full.AuditFailure):
            full.class_inventory(self.shaded,self.deps)

    def test_missing_shaded_class_rejected(self):
        make_zip(self.shaded,{probe.AUDIT_MAIN:b"synthetic"})
        with self.assertRaises(full.AuditFailure):
            full.class_inventory(self.shaded,self.deps)

    def test_extra_shaded_class_rejected(self):
        self.rebuild({"com/example/Unknown.class":b"unknown"})
        with self.assertRaises(full.AuditFailure):
            full.class_inventory(self.shaded,self.deps)

    def test_forbidden_real_application_marker_rejected(self):
        self.rebuild({probe.FORBIDDEN_APP:b"forbidden"})
        with self.assertRaises(full.AuditFailure):
            full.class_inventory(self.shaded,self.deps)

    def test_missing_component_rejected(self):
        self.deps.pop(("org.bouncycastle","bctsp-jdk14","1.38"))
        with self.assertRaises(full.AuditFailure):
            full.class_inventory(self.shaded,self.deps)

    def test_corrupt_component_rejected(self):
        next(iter(self.deps.values())).write_bytes(b"not-a-jar")
        with self.assertRaises(full.AuditFailure):
            full.class_inventory(self.shaded,self.deps)

if __name__ == "__main__":
    unittest.main(verbosity=2)
