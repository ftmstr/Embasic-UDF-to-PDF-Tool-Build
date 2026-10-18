#!/usr/bin/env python3
"""Synthetic-only tests for public source validation; never require real UDFs."""
from __future__ import annotations
import importlib.util
import tempfile
import unittest
from pathlib import Path

source = Path(__file__).with_name("public-ci-gate.py")
spec = importlib.util.spec_from_file_location("public_ci_gate", source)
gate = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(gate)
HEAD = "a" * 40

class PublicGateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.paths = []

    def tearDown(self):
        self.tmp.cleanup()

    def add(self, path: str, content: str = "clean sample"):
        file = self.root / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(content, encoding="utf-8")
        self.paths.append(path)

    def run_gate(self, approval=""):
        return gate.evaluate(self.root, self.paths, approval, HEAD)

    def test_empty_bootstrap(self):
        self.add("README.md")
        self.assertFalse(self.run_gate())

    def test_partial_snapshot_fails(self):
        self.add("pom.xml")
        with self.assertRaises(gate.GateError):
            self.run_gate()

    def test_prohibited_udf_fails(self):
        self.add("example.udf")
        with self.assertRaises(gate.GateError):
            self.run_gate()

    def test_encoded_private_contact_fails(self):
        self.add("notes.md", "contact: " + "name" + "@" + "example.net")
        with self.assertRaises(gate.GateError):
            self.run_gate()

    def test_credentials_fails(self):
        self.add("README.md", "password = " + chr(34) + "very-secret-value" + chr(34))
        with self.assertRaises(gate.GateError):
            self.run_gate()

    def test_all_approved_exact_commit(self):
        for name in sorted(gate.REQUIRED):
            self.add(name)
        self.assertTrue(self.run_gate(HEAD))

    def test_wrong_commit_rejected(self):
        for name in sorted(gate.REQUIRED):
            self.add(name)
        with self.assertRaises(gate.GateError):
            self.run_gate("b" * 40)

    def test_symlink_rejected(self):
        self.add("README.md")
        link = self.root / "link.txt"
        try:
            link.symlink_to(self.root / "README.md")
        except (OSError, NotImplementedError):
            self.skipTest("symlink not supported")
        self.paths.append("link.txt")
        with self.assertRaises(gate.GateError):
            self.run_gate()

    def test_duplicate_tracked_path_rejected(self):
        self.add("README.md")
        self.paths.append("README.md")
        with self.assertRaises(gate.GateError):
            self.run_gate()

if __name__ == "__main__":
    unittest.main(verbosity=2)
