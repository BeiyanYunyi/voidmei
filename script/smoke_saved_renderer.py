#!/usr/bin/env python3
"""Verify a Linux package uses its saved software preference without renderer overrides."""
import argparse
import json
import os
from pathlib import Path
import re

from smoke_kmp_deb import smoke


def saved_renderer_smoke(package, timeout=60):
    def prepare(root):
        target = root / "config/settings-kmp.json"
        target.parent.mkdir()
        settings = {"version": 1, "softwareRendering": True, "hudCompatibilityMode": True}
        for key, leaf in (("fmDataRoot", "data"), ("recordingDirectory", "records"), ("voiceDirectory", "voice")):
            settings[key] = str(root / "userdata/voidmei" / leaf)
        target.write_text(json.dumps(settings), encoding="utf-8")
        return {}

    # The compositor helper sets a backend. Remove it and JVM injections so the
    # application must obtain this preference from the isolated settings file.
    overrides = ("SKIKO_RENDER_API", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")
    previous = {key: os.environ.pop(key, None) for key in overrides}
    try:
        root = smoke(package, timeout, prepare=prepare, renderer="SOFTWARE_FAST",
                     hud=True, check_ui=True, graceful_exit=True)
    finally:
        for key, value in previous.items():
            if value is not None:
                os.environ[key] = value

    text = (root / "startup.log").read_text(encoding="utf-8")
    for window in ("VoidMei · Kotlin", "VoidMei HUD"):
        pattern = r"\[" + re.escape(window) + r"\] 绘制后端：SOFTWARE_FAST\r?\n请求后端：SOFTWARE_FAST(?:\r?\n|$)"
        if not re.search(pattern, text):
            raise RuntimeError("Saved renderer preference not confirmed for " + window)
    if json.loads((root / "config/settings-kmp.json").read_text())["softwareRendering"] is not True:
        raise RuntimeError("Software rendering preference was not preserved on normal exit")
    report_path = root / "report.json"
    report = json.loads(report_path.read_text())
    report["saved_software_renderer_checked"] = True
    report_path.write_text(json.dumps(report, indent=2) + "\n")
    print("Saved software renderer verified for main window and compatible HUD.")
    return root


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("package", type=Path)
    parser.add_argument("--timeout", type=float, default=60)
    args = parser.parse_args()
    if not 1 <= args.timeout <= 300:
        parser.error("timeout must be between 1 and 300 seconds")
    saved_renderer_smoke(args.package.resolve(), args.timeout)
