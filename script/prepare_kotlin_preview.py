#!/usr/bin/env python3
"""Collect the three tested desktop installers into a reviewable Kotlin preview bundle."""
import argparse
import json
from pathlib import Path
import re
import shutil
from macos_package_identity import validate_identity as validate_macos_identity
from kotlin_package_metadata import package_version, digest, ARCHITECTURES, verify_deb, validate_msi_identity

PLATFORMS = {"ubuntu-latest": ("linux", ".deb"), "windows-latest": ("windows", ".msi"), "macos-latest": ("macos", ".dmg")}


def prepare(artifacts: Path, output: Path, build_file: Path, revision: str, inspect_deb=verify_deb):
    if not re.fullmatch(r"[0-9a-f]{40}", revision):
        raise ValueError("Expected a full Git commit SHA")
    version = package_version(build_file)
    selected = []
    for runner, (platform, extension) in PLATFORMS.items():
        directory = artifacts / ("VoidMei-Kotlin-" + runner)
        matches = list(directory.rglob("*" + extension))
        if len(matches) != 1:
            raise ValueError(f"Expected exactly one {extension} installer for {runner}; found {len(matches)}")
        source = matches[0]
        if source.is_symlink() or not source.is_file() or source.stat().st_size == 0:
            raise ValueError(f"Not a nonempty regular installer: {source}")
        source.resolve().relative_to(directory.resolve())
        if not re.search(r"(?:^|[_.-])" + re.escape(version) + r"(?:[_.-]|$)", source.name):
            raise ValueError(f"Installer name does not match package version {version}: {source.name}")
        metadata = json.loads((directory / "kotlin-build.json").read_text())
        if (metadata.get("revision") != revision or metadata.get("version") != version or
                metadata.get("platform") != platform or metadata.get("architecture") not in ARCHITECTURES.values() or
                metadata.get("file") != source.name or metadata.get("bytes") != source.stat().st_size or
                metadata.get("sha256") != digest(source)):
            raise ValueError(f"Build metadata does not match the requested revision or installer: {runner}")
        if platform == "macos":
            validate_macos_identity(metadata.get("installer_control"), version, metadata["architecture"])
        if platform == "windows":
            validate_msi_identity(metadata.get("installer_control"), version, metadata["architecture"])
        if platform == "linux":
            metadata["installer_control"] = inspect_deb(source, version, metadata["architecture"])
        selected.append((source, platform, extension, metadata))
    output.mkdir(parents=True, exist_ok=False)
    packages = []
    for source, platform, extension, metadata in selected:
        destination = output / f"VoidMei-Kotlin-{version}-{platform}-{metadata['architecture']}{extension}"
        shutil.copyfile(source, destination)
        checksum = digest(destination)
        if checksum != metadata["sha256"] or destination.stat().st_size != metadata["bytes"]:
            raise ValueError("Installer changed while preparing preview")
        packages.append({**({"installer_control": metadata["installer_control"]} if "installer_control" in metadata else {}),
                         "file": destination.name, "platform": platform, "architecture": metadata["architecture"],
                         "source_file": source.name, "bytes": destination.stat().st_size, "sha256": checksum})
    tag = f"kotlin-{version}-preview-{revision[:12]}"
    manifest = {"version": version, "revision": revision, "tag": tag, "packages": packages}
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    (output / "SHA256SUMS").write_text("".join(f"{p['sha256']}  {p['file']}\n" for p in packages))
    (output / "release-notes.md").write_text(
        f"Kotlin Multiplatform preview {version}\n\nCommit: `{revision}`\n\n"
        "These installers come from the same workflow run after the shared JVM/JS and desktop tests pass. "
        "Linux also runs the configured GUI and packaged-runtime checks. "
        "This is a migration preview, not a complete replacement acceptance or a claim of manual validation on all platforms.\n\n"
        "See `doc/kotlin-quick-start.md` and `doc/kotlin-migration.md` at this commit for setup and remaining limitations. "
        "FM data is not included. Verify downloads with SHA256SUMS; manifest.json records file sizes and the source revision.\n"
    )
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifacts", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--build-file", type=Path, default=Path("desktop/build.gradle.kts"))
    parser.add_argument("--revision", required=True)
    args = parser.parse_args()
    print(json.dumps(prepare(args.artifacts, args.output, args.build_file, args.revision)))
