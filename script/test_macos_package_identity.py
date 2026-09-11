import plistlib
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from macos_package_identity import inspect_dmg, validate_identity


class IdentityTest(unittest.TestCase):
    def test_detach_failure_preserves_mount_contents(self):
        # This is a plain fixture directory, never a real mounted volume.
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "inspection"
            root.mkdir()
            sentinel = root / "mounted" / "keep"

            def run(command, **kwargs):
                if command[1] == "attach":
                    sentinel.write_text("must survive failed detach")
                    return subprocess.CompletedProcess(command, 0)
                raise subprocess.CalledProcessError(1, command)

            with patch("macos_package_identity.tempfile.mkdtemp", return_value=str(root)), \
                    patch("macos_package_identity.subprocess.run", side_effect=run):
                with self.assertRaises(subprocess.CalledProcessError):
                    inspect_dmg(Path("fixture.dmg"), "2.0.0", "arm64")
            self.assertEqual("must survive failed detach", sentinel.read_text())

    def test_identity_accepts_native_or_universal_and_rejects_mismatches(self):
        identity = {"CFBundleName": "VoidMei", "CFBundleShortVersionString": "2.0.0", "ExecutableArchitectures": ["arm64", "x86_64"]}
        self.assertEqual(identity, validate_identity(identity, "2.0.0", "arm64"))
        for field, value in [("CFBundleName", "other"), ("CFBundleShortVersionString", "1.0.0"), ("ExecutableArchitectures", ["i386"]), ("ExecutableArchitectures", "arm64"), ("ExecutableArchitectures", [])]:
            with self.assertRaises(ValueError): validate_identity(dict(identity, **{field: value}), "2.0.0", "arm64")

    def test_mount_is_readonly_and_detached_on_success_or_bad_bundle(self):
        for name in ("VoidMei", "other"):
            mount = None
            commands = []
            def run(command, **kwargs):
                nonlocal mount
                commands.append(command)
                if command[1] == "attach":
                    self.assertIn("-readonly", command)
                    self.assertIn("-nobrowse", command)
                    mount = Path(command[command.index("-mountpoint") + 1])
                    contents = mount / "VoidMei.app/Contents"
                    (contents / "MacOS").mkdir(parents=True)
                    (contents / "Info.plist").write_bytes(plistlib.dumps({"CFBundleName": name,
                        "CFBundleShortVersionString": "2.0.0", "CFBundleExecutable": "VoidMei"}))
                    (contents / "MacOS/VoidMei").write_bytes(b"fixture")
                else:
                    self.assertEqual(["hdiutil", "detach", str(mount)], command)
                    shutil.rmtree(mount / "VoidMei.app")
                return subprocess.CompletedProcess(command, 0)
            with patch("macos_package_identity.subprocess.run", side_effect=run), patch("macos_package_identity.subprocess.check_output", return_value="arm64\n"):
                if name == "VoidMei":
                    self.assertEqual(["arm64"], inspect_dmg(Path("fixture.dmg"), "2.0.0", "arm64")["ExecutableArchitectures"])
                else:
                    with self.assertRaises(ValueError): inspect_dmg(Path("fixture.dmg"), "2.0.0", "arm64")
            self.assertEqual(2, len(commands))
            self.assertFalse(mount.parent.exists())


if __name__ == "__main__":
    unittest.main()
