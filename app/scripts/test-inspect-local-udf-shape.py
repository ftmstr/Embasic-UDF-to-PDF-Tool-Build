#!/usr/bin/env python3
"""Rights-clean local ZIP/XML shape tests. No native UYAP fixtures."""
import importlib.util
import json
import stat
from pathlib import Path
import tempfile
import unittest
import zipfile

file = Path(__file__).with_name("Inspect-LocalUdfShape.py")
spec = importlib.util.spec_from_file_location("embasic_local_shape", file)
inspect = importlib.util.module_from_spec(spec)
spec.loader.exec_module(inspect)
SENTINEL = "PRIVATE_CASE_TEXT_MUST_NEVER_APPEAR"


class ShapeTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory(prefix="public-shape-synthetic-")
        self.addCleanup(self.folder.cleanup)
        self.root = Path(self.folder.name)

    def make(self, entries):
        target = self.root / "example.udf"
        with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
            for name, data in entries:
                archive.writestr(name, data)
        return target

    def test_known_structure_and_private_text_not_printed(self):
        xml = ('<template format_id="1.0"><content>' + SENTINEL +
               '</content><elements><paragraph/></elements></template>')
        output = inspect.inventory(self.make([
            ("content.xml", xml), ("media/private-filename.png", "binary")]))
        serialized = json.dumps(output)
        self.assertEqual(output["status"], "SHAPE_ONLY_REVIEW_REQUIRED")
        self.assertEqual(output["version_class"], "SYNTHETIC_1_0")
        self.assertEqual(output["native_uyap"], "NOT_VERIFIED")
        self.assertEqual(output["release"], "NO_GO")
        self.assertNotIn(SENTINEL, serialized)
        self.assertNotIn("private-filename", serialized)
        self.assertNotIn("example.udf", serialized)

    def test_unknown_native_like_shape_never_means_compatibility(self):
        text = ('<otherSecretName secretAttr="' + SENTINEL + '">'
                '<secretChild>' + SENTINEL + '</secretChild></otherSecretName>')
        result = inspect.inventory(self.make([("content.xml", text)]))
        self.assertEqual(result["root_class"], "OTHER")
        self.assertEqual(result["unknown_elements"], 2)
        self.assertEqual(result["all_versions"], "NOT_VERIFIED")
        self.assertNotIn("secretChild", json.dumps(result))
        self.assertNotIn(SENTINEL, json.dumps(result))

    def test_zip_symlink_member_is_blocked(self):
        target = self.root / "linked.udf"
        link = zipfile.ZipInfo("content.xml")
        link.create_system = 3
        link.external_attr = (stat.S_IFLNK | 0o777) << 16
        with zipfile.ZipFile(target, "w") as archive:
            archive.writestr(link, "private-target")
        self.assertEqual(inspect.inventory(target)["code"], "ZIP_LINK_MEMBER")

    def test_unsupported_compression_is_blocked(self):
        target = self.root / "bzip.udf"
        with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_BZIP2) as archive:
            archive.writestr("content.xml", "<template/>")
        self.assertEqual(inspect.inventory(target)["code"], "UNSUPPORTED_COMPRESSION")

    def test_zip_traversal_is_blocked(self):
        result = inspect.inventory(self.make([
            ("../" + SENTINEL + ".xml", "x"), ("content.xml", "<template/>")]))
        self.assertEqual(result["code"], "UNSAFE_ZIP_PATH")
        self.assertNotIn(SENTINEL, json.dumps(result))

    def test_duplicate_paths_are_blocked(self):
        result = inspect.inventory(self.make([
            ("Content.xml", "<template/>"), ("content.xml", "<template/>")]))
        self.assertEqual(result["code"], "DUPLICATE_MEMBER")

    def test_doctype_never_expands(self):
        result = inspect.inventory(self.make([
            ("content.xml", '<!DOCTYPE x [<!ENTITY a "foo">]><template>&a;</template>')]))
        self.assertEqual(result["code"], "DTD_OR_ENTITY_NOT_ALLOWED")

    def test_missing_content_is_not_supported(self):
        result = inspect.inventory(self.make([("private-asset.png", "x")]))
        self.assertEqual(result["code"], "CONTENT_XML_NOT_FOUND")
        self.assertNotIn("private-asset", json.dumps(result))

    def test_unsupported_zip_bytes(self):
        source = self.root / "invalid.udf"
        source.write_bytes(b"not zip")
        self.assertEqual(inspect.inventory(source)["code"], "INVALID_ZIP")

    def test_namespaced_template_is_never_proof(self):
        result = inspect.inventory(self.make([
            ("content.xml", '<p:template xmlns:p="urn:test" format_id="1.0"/>')]))
        self.assertTrue(result["namespace_present"])
        self.assertEqual(result["native_uyap"], "NOT_VERIFIED")

    def test_large_archive_rejected_before_decompression(self):
        target = self.root / "giant.udf"
        with target.open("wb") as stream:
            stream.seek(inspect.MAX_ARCHIVE)
            stream.write(b"X")
        self.assertEqual(inspect.inventory(target)["code"], "COMPRESSED_LIMIT")

    def test_symlink_rejected_if_supported(self):
        original = self.make([("content.xml", "<template/>")])
        link = self.root / "link.udf"
        try:
            link.symlink_to(original)
        except (OSError, NotImplementedError):
            self.skipTest("This CI filesystem does not allow creating a symlink")
        self.assertEqual(inspect.inventory(link)["code"], "SYMLINK_PATH")


if __name__ == "__main__":
    unittest.main()
