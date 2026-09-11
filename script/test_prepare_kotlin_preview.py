#!/usr/bin/env python3
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from prepare_kotlin_preview import prepare, PLATFORMS


class PreviewTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.artifacts = self.root / "artifacts"
        self.output = self.root / "preview"
        self.build = self.root / "desktop.gradle.kts"
        self.build.write_text('packageVersion = "2.0.0"')
        self.sha = "1234567890abcdef" * 2 + "12345678"
        self.files = []
        for runner, (_, extension) in PLATFORMS.items():
            path = self.artifacts / ("VoidMei-Kotlin-" + runner) / extension[1:] / ("VoidMei-2.0.0" + extension)
            path.parent.mkdir(parents=True)
            path.write_bytes((runner + " installer fixture").encode())
            self.files.append(path)

    def run_prepare(self):
        return prepare(self.artifacts, self.output, self.build, self.sha)

    def test_collects_only_installers_and_hashes_the_published_bytes(self):
        (self.artifacts / "unrelated.txt").write_text("not a release asset")
        result = self.run_prepare()
        self.assertEqual(self.sha, result["revision"])
        self.assertEqual("kotlin-2.0.0-preview-1234567890ab", result["tag"])
        self.assertEqual(result, json.loads((self.output / "manifest.json").read_text()))
        self.assertEqual(3, len(result["packages"]))
        for package, source in zip(result["packages"], self.files):
            data = (self.output / package["file"]).read_bytes()
            self.assertEqual(source.read_bytes(), data)
            self.assertEqual(len(data), package["bytes"])
            self.assertEqual(hashlib.sha256(data).hexdigest(), package["sha256"])
            self.assertIn(package["sha256"] + "  " + package["file"], (self.output / "SHA256SUMS").read_text())
        self.assertFalse((self.output / "unrelated.txt").exists())

    def test_missing_duplicate_empty_and_wrong_version_fail_before_creating_output(self):
        original = self.files[0].read_bytes()
        self.files[0].unlink()
        with self.assertRaises(ValueError): self.run_prepare()
        self.assertFalse(self.output.exists())
        self.files[0].write_bytes(b"")
        with self.assertRaises(ValueError): self.run_prepare()
        self.files[0].write_bytes(original)
        duplicate = self.files[0].with_name("other-2.0.0.deb")
        duplicate.write_bytes(original)
        with self.assertRaises(ValueError): self.run_prepare()
        duplicate.unlink()
        self.files[0].rename(self.files[0].with_name("VoidMei-1.0.0.deb"))
        with self.assertRaises(ValueError): self.run_prepare()
        self.assertFalse(self.output.exists())

    def test_existing_output_and_invalid_revision_are_not_overwritten(self):
        self.output.mkdir()
        sentinel = self.output / "keep"
        sentinel.write_text("keep")
        with self.assertRaises(FileExistsError): self.run_prepare()
        self.assertEqual("keep", sentinel.read_text())
        self.sha = "master"
        with self.assertRaises(ValueError): self.run_prepare()
        self.assertEqual([sentinel], list(self.output.iterdir()))

    def test_version_must_be_single_and_explicit(self):
        for source in ['packageVersion = version', 'packageVersion = "2.0.0"\npackageVersion = "3.0.0"']:
            self.build.write_text(source)
            with self.assertRaises(ValueError): self.run_prepare()
            self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
