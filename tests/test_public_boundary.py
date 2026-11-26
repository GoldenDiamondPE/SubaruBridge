"""Regressions for excluded artifacts and source-export path handling."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('audit',Path(__file__).resolve().parents[1]/'scripts/audit_public_source.py')
audit=importlib.util.module_from_spec(spec);spec.loader.exec_module(audit)


class Boundary(unittest.TestCase):
    def setUp(self):self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)
    def put(self,name,content=b'original source'):
        file=self.root/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(content)
    def test_original_text_allowed(self):self.put('README.md');self.assertEqual(len(audit.audit(self.root)),1)
    def test_proprietary_binary_and_capture_rejected(self):
        for name in ('STARLINK.apk','vendor/lib.so','decompiled/Main.java','trace.pcapng','private.key'):
            with self.subTest(name=name):
                self.put(name)
                with self.assertRaises(ValueError):audit.audit(self.root)
                (self.root/name).unlink()
    def test_identifier_rejected(self):
        self.put('notes.md',('00:11'+':22:33:44:55').encode())
        with self.assertRaises(ValueError):audit.audit(self.root)
    def test_secret_rejected(self):
        self.put('settings.toml',('token = "gh'+'p_'+'A'*40+'"').encode())
        with self.assertRaises(ValueError):audit.audit(self.root)
    def test_unreviewed_image_rejected(self):
        self.put('screenshot.png')
        with self.assertRaises(ValueError):audit.audit(self.root)
    def test_vendor_source_rejected(self):
        self.put('Main.java',('package com.'+'uievolution.example;').encode())
        with self.assertRaises(ValueError):audit.audit(self.root)
    def test_replaced_pattern_rejected(self):
        self.put('android/src/main/assets/subarubridge-lab/pattern.png',b'unreviewed bytes')
        with self.assertRaises(ValueError):audit.audit(self.root)


if __name__=='__main__':unittest.main()
