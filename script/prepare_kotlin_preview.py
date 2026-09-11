#!/usr/bin/env python3
"""Collect the three tested desktop installers into a reviewable Kotlin preview bundle."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil

PLATFORMS = {"ubuntu-latest": ("linux", ".deb"), "windows-latest": ("windows", ".msi"), "macos-latest": ("macos", ".dmg")}


def prepare(artifacts: Path, output: Path, build_file: Path, revision: str):
    if not re.fullmatch(r"[0-9a-f]{40}", revision):
        raise ValueError("Expected a full Git commit SHA")
    versions = re.findall(r'packageVersion\s*=\s*"(\d+\.\d+\.\d+)"', build_file.read_text())
    if len(versions) != 1:
        raise ValueError("Expected one literal desktop packageVersion")
    version = versions[0]
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
        selected.append((source, platform, extension))
    output.mkdir(parents=True, exist_ok=False)
    packages = []
    for source, platform, extension in selected:
        destination = output / f"VoidMei-Kotlin-{version}-{platform}{extension}"
        shutil.copyfile(source, destination)
        digest = hashlib.sha256()
        with destination.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
        packages.append({"file": destination.name, "platform": platform, "source_file": source.name,
                         "bytes": destination.stat().st_size, "sha256": digest.hexdigest()})
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
