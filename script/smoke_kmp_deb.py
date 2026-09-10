#!/usr/bin/env python3
"""Launch a Debian or standalone Nix KMP package with isolated paths; requires a display.

This checks the package on the current host, not portability to another Linux distribution.
By default it terminates only its own process. The recording smoke can request normal
window closure through a test agent; neither mode physically clicks the close button.
"""
import argparse
import hashlib
import io
import json
import os
import re
from pathlib import Path
import subprocess
import tempfile
import time
import wave
import zipfile


def smoke(package, timeout, prepare=None, ready_check=None, renderer="OPENGL", hud=True, check_ui=False,
          graceful_exit=False, display_scale=1):
    root = Path(tempfile.mkdtemp(prefix="voidmei-package-smoke-"))
    print("Smoke artifacts: %s" % root, flush=True)
    nix_package = package.is_dir()
    if nix_package:
        application = package / "lib/voidmei"
        executable = package / "bin/voidmei-kotlin"
        if not executable.is_file() or not (application / "lib/app").is_dir():
            raise RuntimeError("Not a standalone VoidMei Nix package: " + str(package))
    else:
        subprocess.run(["dpkg-deb", "-x", str(package), str(root)], check=True)
        application = root / "opt/voidmei"
        executable = application / "bin/VoidMei"
    voices = 0
    for jar in (application / "lib/app").glob("*.jar"):
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                if name.startswith("voice/") and name.endswith(".wav"):
                    with wave.open(io.BytesIO(archive.read(name))) as audio:
                        duration = audio.getnframes() / audio.getframerate()
                        if not 0 < duration <= 30:
                            raise RuntimeError("Invalid packaged voice duration: " + name)
                    voices += 1
    if voices == 0:
        raise RuntimeError("Package contains no default voices")
    cwd = root / "unrelated-cwd"
    cwd.mkdir()
    environment = dict(os.environ)
    environment.pop("VOIDMEI_HOME", None)
    environment.update(VOIDMEI_CONFIG_HOME=str(root / "config"),
                       XDG_DATA_HOME=str(root / "userdata"),
                       VOIDMEI_ENDPOINT="http://127.0.0.1:1")
    settings_file = root / "config/settings-kmp.json"
    if prepare is not None:
        environment.update(prepare(root))
    if check_ui:
        environment["JAVA_TOOL_OPTIONS"] = environment.get("JAVA_TOOL_OPTIONS", "") + " -Dvoidmei.diagnostics.uiHeartbeat=true"
    if graceful_exit:
        # Skiko's Linux auto-DPI setup otherwise replaces the requested AWT scale.
        environment["JAVA_TOOL_OPTIONS"] = environment.get("JAVA_TOOL_OPTIONS", "") + (
            " -Dskiko.linux.autodpi=false -Dsun.java2d.uiScale.enabled=true -Dsun.java2d.uiScale=%d" % display_scale)
        agent_dir = root / "exit-agent"
        agent_dir.mkdir()
        source = Path(__file__).resolve().parent / "fixtures/CloseWindowAgent.java"
        subprocess.run(["javac", "--release", "17", "-encoding", "UTF-8", "-d", str(agent_dir), str(source)], check=True)
        manifest = agent_dir / "MANIFEST.MF"
        manifest.write_text("Premain-Class: CloseWindowAgent\n\n", encoding="utf-8")
        agent = agent_dir / "close-window.jar"
        subprocess.run(["jar", "--create", "--file", str(agent), "--manifest", str(manifest),
                        "-C", str(agent_dir), "CloseWindowAgent.class"], check=True)
        option = "-javaagent:%s=%s" % (agent, root / "request-close")
        if any(char in option for char in '\n\r"'):
            raise RuntimeError("Unsupported character in exit-agent path")
        environment["JAVA_TOOL_OPTIONS"] = environment.get("JAVA_TOOL_OPTIONS", "") + ' "' + option + '"'
    log = root / "startup.log"
    ready = False
    with log.open("w") as output:
        process = subprocess.Popen([str(executable)] + (["--hud"] if hud else ["--no-hud"]),
                                   cwd=cwd, env=environment, stdout=output, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + timeout
            while time.monotonic() < deadline and process.poll() is None:
                text = log.read_text(errors="replace")
                latencies = [int(value) for value in re.findall(r"\[VoidMei UI\] latency_ms=(\d+)", text)]
                if check_ui and ("[VoidMei UI] stalled_ms=" in text or any(value >= 2000 for value in latencies)):
                    raise RuntimeError("AWT event thread stalled for at least two seconds; inspect " + str(log))
                if ((not check_ui or len(latencies) >= 5) and settings_file.exists() and
                        ("[VoidMei · Kotlin] 绘制后端：" + renderer) in text and
                        (not hud or ("[VoidMei HUD] 绘制后端：" + renderer) in text) and
                        (ready_check is None or ready_check(root))):
                    ready = True
                    break
                time.sleep(0.25)
            if not ready:
                raise RuntimeError("Package did not satisfy settings, renderer or additional readiness checks; inspect " + str(log))
            if graceful_exit:
                (root / "request-close").touch()
                exit_code = process.wait(timeout=15)
                if exit_code != 0 or "[VoidMei exit test] dispatch WINDOW_CLOSING" not in log.read_text(errors="replace"):
                    raise RuntimeError("Normal window closure failed; inspect " + str(log))
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
    settings = json.loads(settings_file.read_text())
    final_position = None
    if graceful_exit:
        match = re.search(r"\[VoidMei exit test\] position=(-?\d+),(-?\d+) scale=([\d.]+),([\d.]+)",
                          log.read_text(errors="replace"))
        if match is None or tuple(map(float, match.groups()[2:])) != (display_scale, display_scale):
            raise RuntimeError("Actual AWT display scale did not match the requested scale")
        final_position = dict(zip(("x", "y"), map(int, match.groups()[:2])))
        before_close = json.loads((root / "pre-close-settings.json").read_text())
        if before_close.get("mainPosition") == final_position:
            raise RuntimeError("Position was already saved before close; final-save check is inconclusive")
        if settings.get("mainPosition") != final_position:
            raise RuntimeError("Normal exit did not save the final window position")
    if not hud and (settings.get("hudEnabled") is not False or "[VoidMei HUD]" in log.read_text(errors="replace")):
        raise RuntimeError("Recovery startup did not disable the saved HUD")
    for key, leaf in (("fmDataRoot", "data"), ("recordingDirectory", "records"), ("voiceDirectory", "voice")):
        expected = str(root / "userdata/voidmei" / leaf)
        if settings[key] != expected:
            raise RuntimeError("Unexpected %s: %s" % (key, settings[key]))
    if any(cwd.iterdir()):
        raise RuntimeError("Application wrote files into its unrelated working directory")
    report = {"package": str(package), "package_type": "nix" if nix_package else "deb",
              "sha256": None if nix_package else hashlib.sha256(package.read_bytes()).hexdigest(),
              "packaged_voices": voices, "main_renderer": renderer, "hud_renderer": renderer if hud else None,
              "isolated_settings": str(settings_file), "log": str(log),
              "awt_heartbeat_checked": check_ui, "awt_latency_ms": latencies if check_ui else None,
              "presentation_probe_only": "Presentation probe: SwingGraphics; full HUD bypassed" in text,
              "graceful_exit_checked": graceful_exit,
              "normal_exit_code": exit_code if graceful_exit else None,
              "final_position_checked": final_position,
              "awt_display_scale_checked": display_scale if graceful_exit else None,
              "limitation": "Current host only; no game connection, full UI responsiveness, audio playback or physical close-button verification"}
    (root / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
    return root


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("package", type=Path)
    parser.add_argument("--timeout", type=float, default=45)
    args = parser.parse_args()
    if not 1 <= args.timeout <= 300:
        parser.error("timeout must be between 1 and 300 seconds")
    smoke(args.package.resolve(), args.timeout)
