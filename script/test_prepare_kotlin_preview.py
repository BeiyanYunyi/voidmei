#!/usr/bin/env python3
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from prepare_kotlin_preview import prepare, PLATFORMS
from kotlin_package_metadata import record, verify_deb


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
        for runner, (platform, extension) in PLATFORMS.items():
            path = self.artifacts / ("VoidMei-Kotlin-" + runner) / extension[1:] / ("VoidMei-2.0.0" + extension)
            path.parent.mkdir(parents=True)
            path.write_bytes((runner + " installer fixture").encode())
            self.files.append(path)
            record(path.parent.parent, self.build, self.sha, platform, "ARM64" if platform == "macos" else "X64")

    def run_prepare(self):
        return prepare(self.artifacts, self.output, self.build, self.sha, inspect_deb=lambda *_: {"Package": "voidmei", "Version": "2.0.0", "Architecture": "amd64"})

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

    def test_mixed_revision_architecture_and_modified_installers_are_rejected(self):
        metadata = self.files[0].parent.parent / "kotlin-build.json"
        original = json.loads(metadata.read_text())
        for key, invalid in [("revision", "a" * 40), ("version", "1.0.0"), ("platform", "macos"), ("architecture", "unknown"), ("sha256", "0" * 64)]:
            metadata.write_text(json.dumps(dict(original, **{key: invalid})))
            with self.assertRaises(ValueError): self.run_prepare()
            self.assertFalse(self.output.exists())
        metadata.write_text(json.dumps(original))
        self.files[0].write_bytes(b"changed package")
        with self.assertRaises(ValueError): self.run_prepare()
        self.assertFalse(self.output.exists())

    def test_deb_control_validation_rejects_wrong_package_version_or_architecture(self):
        valid = ["voidmei\n", "2.0.0-1\n", "amd64\n"]
        with patch("kotlin_package_metadata.subprocess.check_output", side_effect=valid):
            self.assertEqual("2.0.0-1", verify_deb(self.files[0], "2.0.0", "x64")["Version"])
        for values in [["other", "2.0.0", "amd64"], ["voidmei", "2.0.1", "amd64"], ["voidmei", "2.0.0", "arm64"]]:
            with patch("kotlin_package_metadata.subprocess.check_output", side_effect=values):
                with self.assertRaises(ValueError): verify_deb(self.files[0], "2.0.0", "x64")
        def rejected(*_):
            raise ValueError("wrong package control data")
        with self.assertRaises(ValueError):
            prepare(self.artifacts, self.output, self.build, self.sha, inspect_deb=rejected)
        self.assertFalse(self.output.exists())

    def test_version_must_be_single_and_explicit(self):
        for source in ['packageVersion = version', 'packageVersion = "2.0.0"\npackageVersion = "3.0.0"']:
            self.build.write_text(source)
            with self.assertRaises(ValueError): self.run_prepare()
            self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
