"""Read an app from a DMG without launching or installing it (macOS build host only)."""
import plistlib
from pathlib import Path
import subprocess
import tempfile


def validate_identity(identity, version, architecture):
    expected = {"x64": "x86_64", "arm64": "arm64", "x86": "i386"}.get(architecture)
    if not isinstance(identity, dict):
        raise ValueError("Missing macOS application identity")
    architectures = identity.get("ExecutableArchitectures")
    if (identity.get("CFBundleName") != "VoidMei" or identity.get("CFBundleShortVersionString") != version or
            not isinstance(architectures, list) or not architectures or
            any(value not in ("x86_64", "arm64", "i386") for value in architectures) or expected not in architectures):
        raise ValueError(f"macOS app identity does not match version/architecture: {identity}")
    return identity


def inspect_dmg(path, version, architecture):
    root = Path(tempfile.mkdtemp(prefix="voidmei-dmg-check-"))
    mount = root / "mounted"
    mount.mkdir()
    attached = False
    try:
        subprocess.run(["hdiutil", "attach", "-readonly", "-nobrowse", "-mountpoint", str(mount), str(path.resolve())],
                       check=True, timeout=60, stdout=subprocess.DEVNULL)
        attached = True
        apps = list(mount.glob("*.app"))
        if len(apps) != 1 or apps[0].is_symlink():
            raise ValueError("Expected exactly one application in the DMG")
        contents = apps[0] / "Contents"
        info_path = contents / "Info.plist"
        info_path.resolve().relative_to(contents.resolve())
        with info_path.open("rb") as source:
            raw = source.read(1024 * 1024 + 1)
        if len(raw) > 1024 * 1024:
            raise ValueError("Application Info.plist is too large")
        info = plistlib.loads(raw)
        executable = info.get("CFBundleExecutable")
        if not isinstance(executable, str) or not executable or executable in (".", "..") or any(c in executable for c in "/\\\x00"):
            raise ValueError("Invalid application executable name")
        binary = contents / "MacOS" / executable
        binary.resolve().relative_to(contents.resolve())
        architectures = subprocess.check_output(["lipo", "-archs", str(binary)], text=True, timeout=15).split()
        identity = {key: info.get(key) for key in ("CFBundleName", "CFBundleShortVersionString", "CFBundleVersion", "CFBundleExecutable")}
        identity["ExecutableArchitectures"] = architectures
        return validate_identity(identity, version, architecture)
    finally:
        # Never recursively remove a mount point. A failed detach leaves the directory for diagnosis.
        if attached or mount.is_mount():
            subprocess.run(["hdiutil", "detach", str(mount)], check=True, timeout=30, stdout=subprocess.DEVNULL)
        if mount.exists():
            mount.rmdir()
        root.rmdir()
