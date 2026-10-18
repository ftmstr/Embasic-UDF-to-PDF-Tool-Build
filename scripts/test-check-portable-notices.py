#!/usr/bin/env python3
"""Synthetic portable notice byte-integrity tests; no actual legal text bundled."""
from __future__ import annotations
import importlib.util
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path

loc = Path(__file__).with_name("check-portable-notices.py")
spec = importlib.util.spec_from_file_location("portable_notices", loc)
mod = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(mod)

class NoticeTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.zip = Path(self.tmp.name)/"portable.zip"
        self.contents = {mod.REQUIRED[0]: b"synthetic first notice",
                         mod.REQUIRED[1]: b"synthetic second notice"}
        self.makezip({f"Portable/{k}":v for k,v in self.contents.items()})

    def makezip(self, entries):
        with zipfile.ZipFile(self.zip,"w") as archive:
            for name,content in entries.items():
                archive.writestr(name,content)

    def test_both_notices_exact_match(self):
        result=mod.verify(self.zip,self.contents)
        self.assertEqual(len(result["notice_sha256"]),2)

    def test_missing_notice_rejected(self):
        self.makezip({"Portable/THIRD-PARTY-iText.txt":self.contents[mod.REQUIRED[0]]})
        with self.assertRaises(mod.NoticeError):
            mod.verify(self.zip,self.contents)

    def test_changed_notice_rejected(self):
        items=dict(self.contents)
        items[mod.REQUIRED[1]]=b"modified"
        with self.assertRaises(mod.NoticeError):
            mod.verify(self.zip,items)

    def test_ambiguous_copy_rejected(self):
        entries={f"one/{k}":v for k,v in self.contents.items()}
        entries["two/THIRD-PARTY-iText.txt"]=self.contents[mod.REQUIRED[0]]
        self.makezip(entries)
        with self.assertRaises(mod.NoticeError):
            mod.verify(self.zip,self.contents)

    def test_path_traversal_rejected(self):
        self.makezip({**{f"Portable/{k}":v for k,v in self.contents.items()},
                      "../escape":b"bad"})
        with self.assertRaises(mod.NoticeError):
            mod.verify(self.zip,self.contents)

    def test_duplicate_archive_entry_rejected(self):
        with warnings.catch_warnings():
            warnings.simplefilter("ignore",UserWarning)
            with zipfile.ZipFile(self.zip,"w") as z:
                for k,v in self.contents.items(): z.writestr(k,v)
                z.writestr(mod.REQUIRED[0],self.contents[mod.REQUIRED[0]])
        with self.assertRaises(mod.NoticeError):
            mod.verify(self.zip,self.contents)

    def test_unapproved_missing_expected_set_rejected(self):
        with self.assertRaises(mod.NoticeError):
            mod.verify(self.zip,{mod.REQUIRED[0]:self.contents[mod.REQUIRED[0]]})

    def test_corrupt_archive_rejected(self):
        self.zip.write_bytes(b"not an archive")
        with self.assertRaises(mod.NoticeError):
            mod.verify(self.zip,self.contents)

if __name__=="__main__":
    unittest.main(verbosity=2)
