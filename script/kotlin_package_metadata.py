#!/usr/bin/env python3
"""Record the revision and installer bytes produced by a desktop build job."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
from macos_package_identity import inspect_dmg

EXTENSIONS = {"linux": ".deb", "windows": ".msi", "macos": ".dmg"}
ARCHITECTURES = {"X64": "x64", "ARM64": "arm64", "X86": "x86"}


def package_version(build_file):
    versions = re.findall(r'packageVersion\s*=\s*"(\d+\.\d+\.\d+)"', build_file.read_text())
    if len(versions) != 1:
        raise ValueError("Expected one literal desktop packageVersion")
    return versions[0]


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def verify_deb(path, version, architecture):
    fields = {key: subprocess.check_output(["dpkg-deb", "--field", str(path), key],
                text=True, timeout=15).strip() for key in ("Package", "Version", "Architecture")}
    expected_arch = {"x64": "amd64", "arm64": "arm64", "x86": "i386"}.get(architecture)
    if (fields["Package"] != "voidmei" or fields["Architecture"] != expected_arch or
            not re.fullmatch(re.escape(version) + r"(?:-[0-9A-Za-z.+~]+)?", fields["Version"])):
        raise ValueError(f"Deb control fields do not match requested version/architecture: {fields}")
    return fields


def validate_msi_identity(identity, version, architecture):
    if not isinstance(identity, dict) or not isinstance(identity.get("Template"), str):
        raise ValueError("Missing MSI identity or platform template")
    platform = identity["Template"].split(";", 1)[0].strip().lower()
    actual_arch = {"": "x86", "intel": "x86", "x64": "x64", "arm64": "arm64"}.get(platform)
    if identity.get("ProductName") != "VoidMei" or identity.get("ProductVersion") != version or actual_arch != architecture:
        raise ValueError(f"MSI identity does not match requested version/architecture: {identity}")
    return identity


def inspect_msi(path, version, architecture):
    script = Path(__file__).with_name("read_msi_identity.ps1")
    output = subprocess.check_output(["powershell.exe", "-NoProfile", "-NonInteractive", "-File", str(script.resolve()),
                                      "-Package", str(path.resolve())], encoding="utf-8-sig", timeout=30)
    return validate_msi_identity(json.loads(output), version, architecture)


def record(root, build_file, revision, platform, architecture, read_msi=inspect_msi, read_dmg=inspect_dmg):
    if not re.fullmatch(r"[0-9a-f]{40}", revision):
        raise ValueError("Expected a full Git commit SHA")
    if platform not in EXTENSIONS or architecture not in ARCHITECTURES:
        raise ValueError("Unsupported build platform or architecture")
    files = list(root.rglob("*" + EXTENSIONS[platform]))
    if len(files) != 1 or files[0].is_symlink() or not files[0].is_file() or files[0].stat().st_size == 0:
        raise ValueError("Expected exactly one nonempty installer")
    package = files[0]
    metadata = {"revision": revision, "version": package_version(build_file), "platform": platform,
                "architecture": ARCHITECTURES[architecture], "file": package.name,
                "bytes": package.stat().st_size, "sha256": digest(package)}
    if platform == "windows":
        metadata["installer_control"] = read_msi(package, metadata["version"], metadata["architecture"])
    if platform == "macos":
        metadata["installer_control"] = read_dmg(package, metadata["version"], metadata["architecture"])
    with (root / "kotlin-build.json").open("x", encoding="utf-8") as stream:
        json.dump(metadata, stream, indent=2)
        stream.write("\n")
    return metadata


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path)
    parser.add_argument("--build-file", type=Path, default=Path("desktop/build.gradle.kts"))
    parser.add_argument("--revision", required=True)
    parser.add_argument("--platform", required=True, choices=EXTENSIONS)
    parser.add_argument("--architecture", required=True, choices=ARCHITECTURES)
    args = parser.parse_args()
    record(args.root, args.build_file, args.revision, args.platform, args.architecture)
