#!/usr/bin/env python3
"""Record the revision and installer bytes produced by a desktop build job."""
import argparse
import hashlib
import json
from pathlib import Path
import re

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


def record(root, build_file, revision, platform, architecture):
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
