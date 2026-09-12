#!/usr/bin/env python3
"""Check saved model-window startup preferences in a standalone package on this host."""
import argparse
import json
import os
from pathlib import Path
from smoke_kmp_deb import smoke


def model_window_smoke(package, timeout=60):
    expected = {
        "modelWindowEnabled": True,
        "modelWindowAlwaysOnTop": False,
        "modelWindowPosition": {"x": 120, "y": 90},
        "modelWindowHotkeyEnabled": False,
        "modelWindowHotkey": "Alt+LEFT",
        "hudAttitudeRefreshMs": 75,
        "softwareRendering": True,
    }

    def prepare(root):
        target = root / "config/settings-kmp.json"
        target.parent.mkdir()
        settings = dict(expected, version=1, hudCompatibilityMode=True)
        for key, leaf in (("fmDataRoot", "data"), ("recordingDirectory", "records"), ("voiceDirectory", "voice")):
            settings[key] = str(root / "userdata/voidmei" / leaf)
        target.write_text(json.dumps(settings), encoding="utf-8")
        (root / "require-model-window").touch()
        return {}

    overrides = ("SKIKO_RENDER_API", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")
    previous = {key: os.environ.pop(key, None) for key in overrides}
    try:
        root = smoke(package, timeout, prepare=prepare, renderer="SOFTWARE_FAST", hud=True,
                     check_ui=True, graceful_exit=True)
    finally:
        for key, value in previous.items():
            if value is not None:
                os.environ[key] = value
    settings = json.loads((root / "config/settings-kmp.json").read_text())
    for key, value in expected.items():
        if settings.get(key) != value:
            raise RuntimeError("Saved model window setting differs: " + key)
    observed = json.loads((root / "model-window-probe.json").read_text())
    if observed != dict(visible=True, count=1, alwaysOnTop=False, x=120, y=90):
        raise RuntimeError("Unexpected model window probe result")
    report_path = root / "report.json"
    report = json.loads(report_path.read_text())
    report.update(model_window_startup_checked=observed, saved_model_settings_checked=expected)
    report_path.write_text(json.dumps(report, indent=2) + "\n")
    print("Saved model window startup and settings verified:", root)
    return root


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("package", type=Path)
    parser.add_argument("--timeout", type=float, default=60)
    args = parser.parse_args()
    if not 1 <= args.timeout <= 300:
        parser.error("timeout must be between 1 and 300 seconds")
    model_window_smoke(args.package.resolve(), args.timeout)
