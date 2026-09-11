#!/usr/bin/env python3
"""Verify packaged auto recording against isolated synthetic HTTP telemetry, with a display."""
import argparse
import csv
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import threading
import time

from smoke_kmp_deb import smoke


def recording_smoke(package, timeout, renderer="OPENGL", hud=True, check_ui=False, compatible_hud=False, graceful_exit=False, display_scale=1, jet=False, wep=False, wep_dropout=False, tray_recovery=False, tray_background=False, hud_renderer=None, poll_interval_ms=100):
    if not isinstance(poll_interval_ms, int) or isinstance(poll_interval_ms, bool) or not 10 <= poll_interval_ms <= 5000:
        raise ValueError("poll interval must be an integer between 10 and 5000 ms")
    flying = threading.Event()
    flying.set()
    requests = {}

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def do_GET(self):
            requests[self.path] = requests.get(self.path, 0) + 1
            data = {"valid": False}
            if flying.is_set():
                if self.path == "/state":
                    data = {"valid": True, "IAS, km/h": 240, "TAS, km/h": 260,
                            "H, m": 1500, "RPM 1": 2200, "power 1, hp": 900, "throttle 1, %": 100,
                            "water temp 1, C": 90, "oil temp 1, C": 80}
                    data.update({"Mfuel, kg": 500, "Mfuel0, kg": 800, "Mfuel 10, kg": 999})
                    data.update((
                        {"Mfuel 1, kg": 50, "Mfuel0 1, kg": 200},
                        {"Mfuel 1, kg": 0, "Mfuel0 1, kg": 200},
                        {"Mfuel 1, kg": -65535, "Mfuel0 1, kg": None},
                    )[(requests[self.path] - 1) % 3])
                    if wep:
                        data["throttle 1, %"] = 110
                        if wep_dropout and 20 <= requests[self.path] <= 22:
                            del data["throttle 1, %"]
                    if jet:
                        data.update({"power 1, hp": 0, "thrust 1, kgs": 1000,
                                     "magneto 1": -1, "pitch 1, deg": -65535})
                elif self.path == "/indicators":
                    data = {"valid": True, "type": "smoke-plane"}
                    phase = (requests[self.path] - 1) % 3
                    data.update((
                        {"water_temperature": 100, "head_temperature": 200, "oil_temperature": -20, "altitude_10k": 4921.26},
                        {"water_temperature": 0, "oil_temperature": 0, "altitude_10k": 0},
                        {"water_temperature": -65535, "head_temperature": None, "oil_temperature": "invalid", "altitude_10k": -65535},
                    )[phase])
            body = json.dumps(data).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()

    def prepare(root):
        settings = {"version": 1, "recordingAutoStart": True, "hudCompatibilityMode": compatible_hud,
                    "hudEnabled": True, "pollIntervalMs": poll_interval_ms}
        if tray_background:
            settings["startInTray"] = True
            (root / "require-tray-background").touch()
        if tray_recovery:
            settings["startInTray"] = True
            (root / "require-visible").touch()
        for key, leaf in (("fmDataRoot", "data"), ("recordingDirectory", "records"), ("voiceDirectory", "voice")):
            settings[key] = str(root / "userdata/voidmei" / leaf)
        if wep:
            directory = Path(settings["fmDataRoot"]) / "aces/gamedata/flightmodels"
            (directory / "fm").mkdir(parents=True)
            (directory / "smoke-plane.blkx").write_text('fmFile:t="fm/smoke-plane.blk"', encoding="utf-8")
            (directory / "fm/smoke-plane.blkx").write_text(
                "Mass { MaxNitro:r=10 }\n"
                "Engine0 { Main { Type:t=Inline } Afterburner { NitroConsumption:r=0.5 } }\n",
                encoding="utf-8")
            settings["hudFields"] = ["wep_fuel", "wep_time"]
        config = root / "config/settings-kmp.json"
        config.parent.mkdir()
        config.write_text(json.dumps(settings), encoding="utf-8")
        return {"VOIDMEI_ENDPOINT": "http://127.0.0.1:%d" % server.server_port}

    def rows(path):
        with path.open(encoding="utf-8", newline="") as source:
            return list(csv.DictReader(source))

    stopped_at = None

    def ready(root):
        nonlocal stopped_at
        files = list((root / "userdata/voidmei/records").glob("*.csv"))
        if len(files) != 2 or any(len(rows(path)) < 5 for path in files):
            return False
        flight_rows = rows(next(path for path in files if not path.name.endswith("-engines.csv")))
        if not any(row.get("engine_response_percent_per_s") for row in flight_rows):
            return False  # Wait for the full-throttle reference and a subsequent derivative sample.
        if wep:
            estimates = [float(row["wep_fuel_upper_kg"]) for row in flight_rows if row.get("wep_fuel_upper_kg")]
            if len(estimates) < 3 or estimates[0] - estimates[-1] < 0.1:
                return False
        if graceful_exit:
            return True  # Keep telemetry flowing so normal exit must stop an active recording.
        if stopped_at is None:
            flying.clear()
            stopped_at = time.monotonic()
        return time.monotonic() - stopped_at >= 2

    try:
        root = smoke(package, timeout, prepare=prepare, ready_check=ready, renderer=renderer, hud=hud, check_ui=check_ui,
                     graceful_exit=graceful_exit, display_scale=display_scale, hud_renderer=hud_renderer)
    finally:
        print("Synthetic HTTP requests: " + json.dumps(requests), flush=True)
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)
    if tray_recovery:
        saved = json.loads((root / "config/settings-kmp.json").read_text())
        if saved.get("startInTray") is not True or "[VoidMei exit test] recovery main window visible" not in (root / "startup.log").read_text():
            raise RuntimeError("Tray recovery did not retain preference and display the main window")
    if tray_background:
        saved = json.loads((root / "config/settings-kmp.json").read_text())
        if saved.get("startInTray") is not True or "[VoidMei exit test] background main hidden with tray entry" not in (root / "startup.log").read_text():
            raise RuntimeError("Background startup did not hide the main window with a tray entry")
    if compatible_hud:
        saved = json.loads((root / "config/settings-kmp.json").read_text())
        if saved.get("hudCompatibilityMode") is not True or "Presentation: SwingGraphics; full HUD" not in (root / "startup.log").read_text():
            raise RuntimeError("Persisted compatibility setting did not activate the full HUD")
    files = list((root / "userdata/voidmei/records").glob("*.csv"))
    flight_file = next(path for path in files if not path.name.endswith("-engines.csv"))
    engine_file = flight_file.with_name(flight_file.stem + "-engines.csv")
    flight, engines = rows(flight_file), rows(engine_file)
    if len(flight) < 5 or len(flight) != len(engines):
        raise RuntimeError("Expected at least five matching single-engine samples")
    wep_values = []
    wep_runs = [[]]
    wep_missing_throttle = 0
    booster_values = set()
    response_values = set()
    temperature_patterns = set()
    expected_patterns = {(100.0, 200.0, -20.0), (0.0, None, 0.0), (None, None, None)}
    for index, (sample, engine) in enumerate(zip(flight, engines)):
        if (sample["sample_id"] != str(index) or engine["sample_id"] != str(index) or
                sample["utc_epoch_ms"] != engine["utc_epoch_ms"] or sample["aircraft"] != "smoke-plane" or
                float(sample["ias_kmh"]) != 240 or engine["engine_index"] != "1" or
                float(engine["rpm"]) != 2200 or float(engine["power_hp"]) != (0 if jet else 900) or
                float(engine["water_temp_c"]) != 90 or float(engine["oil_temp_c"]) != 80):
            raise RuntimeError("Unexpected recorded telemetry at sample %d" % index)
        if jet and (float(engine["thrust_kgf"]) != 1000 or float(engine["magneto"]) != -1):
            raise RuntimeError("Jet thrust or negative magneto changed at sample %d" % index)
        temperatures = tuple(float(sample[key]) if sample[key] else None for key in
                             ("water_temperature_raw", "head_temperature_raw", "oil_temperature_raw"))
        if temperatures not in expected_patterns:
            raise RuntimeError("Cockpit temperature sources were mixed or changed at sample %d: %r" % (index, temperatures))
        altimeter = float(sample["altimeter_raw"]) if sample["altimeter_raw"] else None
        expected_altimeter = {(100.0, 200.0, -20.0): 4921.26, (0.0, None, 0.0): 0.0,
                              (None, None, None): None}[temperatures]
        if altimeter != expected_altimeter or float(sample["altitude_m"]) != 1500:
            raise RuntimeError("Altimeter raw value changed or contaminated metre altitude at sample %d" % index)
        wep_mass = float(sample["wep_fuel_upper_kg"]) if sample["wep_fuel_upper_kg"] else None
        wep_time = float(sample["wep_time_upper_s"]) if sample["wep_time_upper_s"] else None
        if wep:
            if not engine["throttle_percent"]:
                wep_missing_throttle += 1
                if wep_mass is not None or wep_time is not None:
                    raise RuntimeError("WEP bounds survived a missing throttle sample")
            if (wep_mass is None) != (wep_time is None):
                raise RuntimeError("Partial WEP estimate at sample %d" % index)
            if wep_mass is not None:
                if not 0 <= wep_mass <= 10 or abs(wep_time - wep_mass / 0.5) > 1e-8:
                    raise RuntimeError("WEP bounds disagree with the loaded FM at sample %d" % index)
                point = (float(sample["elapsed_ms"]), wep_mass)
                wep_values.append(point)
                wep_runs[-1].append(point)
            elif wep_runs[-1]:
                wep_runs.append([])
        elif wep_mass is not None or wep_time is not None:
            raise RuntimeError("WEP estimate invented without an FM")
        booster = tuple(float(sample[key]) if sample[key] else None for key in
                        ("booster_fuel_kg", "booster_fuel_capacity_kg"))
        if booster not in {(50.0, 200.0), (0.0, 200.0), (None, None)} or float(sample["fuel_kg"]) != 500 or float(sample["fuel_capacity_kg"]) != 800:
            raise RuntimeError("Booster fuel was lost or mixed with another fuel channel at sample %d" % index)
        booster_values.add(booster)
        response = float(sample["engine_response_percent_per_s"]) if sample["engine_response_percent_per_s"] else None
        if response not in (None, 0.0):
            raise RuntimeError("Constant engine output produced a nonzero response at sample %d" % index)
        response_values.add(response)
        temperature_patterns.add(temperatures)
    if temperature_patterns != expected_patterns:
        raise RuntimeError("Recording did not cover valid, zero and missing cockpit temperatures")
    if wep:
        if len(wep_values) < 3 or wep_values[0][1] - wep_values[-1][1] < 0.1:
            raise RuntimeError("Packaged FM did not produce a decreasing WEP fuel bound")
        for run in wep_runs:
            for (start_ms, start_mass), (end_ms, end_mass) in zip(run, run[1:]):
                expected_mass = max(0, start_mass - (end_ms - start_ms) / 1000 * 0.5)
                if end_mass > start_mass or abs(expected_mass - end_mass) > 0.05:
                    raise RuntimeError("WEP consumption disagrees with elapsed recorded time")
        if wep_dropout:
            runs = [run for run in wep_runs if run]
            if wep_missing_throttle != 3 or len(runs) != 2 or runs[1][0][1] != 10.0:
                raise RuntimeError("Missing throttle did not clear and restart the WEP upper bound")
            if runs[0][-1][1] >= 10 or runs[1][-1][1] >= 9.9:
                raise RuntimeError("WEP consumption was not observed on both sides of the dropout")
    if booster_values != {(50.0, 200.0), (0.0, 200.0), (None, None)}:
        raise RuntimeError("Booster recording did not cover positive, zero and missing samples")
    if response_values != {None, 0.0}:
        raise RuntimeError("Recording did not preserve the transition from unknown to zero engine response")
    saved = json.loads((root / "config/settings-kmp.json").read_text())
    if saved.get("pollIntervalMs") != poll_interval_ms:
        raise RuntimeError("Configured poll interval was not preserved")
    report = {"poll_interval_ms_checked": poll_interval_ms, "samples": len(flight), "flight_csv": str(flight_file), "engine_csv": str(engine_file),
              "graceful_exit_checked": graceful_exit,
              "tray_recovery_checked": tray_recovery,
              "tray_background_checked": tray_background,
              "temperature_sources_checked": True,
              "altimeter_source_checked": True,
              "engine_response_checked": True,
              "booster_channels_checked": True,
              "wep_fm_checked": wep,
              "wep_estimates_checked": len(wep_values),
              "wep_missing_throttle_samples": wep_missing_throttle,
              "wep_dropout_checked": wep_dropout,
              "jet_negative_magneto_checked": jet,
              "temperature_patterns_checked": len(temperature_patterns),
              "verification": "Synthetic HTTP to packaged runtime to paired CSV; no real game or physical UI interaction claim"}
    (root / "recording-report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("package", type=Path)
    parser.add_argument("--poll-interval-ms", type=int, default=100, help="Configured polling delay, 10–5000 ms")
    parser.add_argument("--timeout", type=float, default=60)
    parser.add_argument("--renderer", choices=("OPENGL", "SOFTWARE_FAST"), default="OPENGL")
    parser.add_argument("--hud-renderer", choices=("OPENGL", "SOFTWARE_FAST"), help="Expected HUD renderer; defaults to --renderer")
    parser.add_argument("--no-hud", action="store_true", help="Isolate main-window rendering from the transparent HUD")
    parser.add_argument("--compatible-hud", action="store_true", help="Enable the full HUD through its persisted compatibility setting")
    parser.add_argument("--check-ui", action="store_true", help="Require five timely AWT heartbeats and fail on a two-second event-thread stall")
    parser.add_argument("--graceful-exit", action="store_true", help="Use a test-only Java agent to dispatch normal window closure while recording; requires javac and jar")
    parser.add_argument("--display-scale", type=int, choices=(1, 2), default=1,
                        help="AWT scale requested and verified by the normal-exit agent")
    parser.add_argument("--jet", action="store_true", help="Exercise jet history using zero shaft power, negative magneto and thrust")
    parser.add_argument("--wep", action="store_true", help="Load an isolated synthetic FM and verify WEP consumption")
    parser.add_argument("--tray-background", action="store_true", help="Verify saved background startup with a real tray manager")
    parser.add_argument("--tray-recovery", action="store_true", help="Verify --no-hud shows the main window despite the saved tray startup preference")
    parser.add_argument("--wep-dropout", action="store_true", help="Temporarily omit throttle while flying; requires --wep")
    args = parser.parse_args()
    if not 10 <= args.poll_interval_ms <= 5000:
        parser.error("--poll-interval-ms must be between 10 and 5000")
    if args.wep_dropout and not args.wep:
        parser.error("--wep-dropout requires --wep")
    if args.wep and args.jet:
        parser.error("--wep and --jet select separate synthetic engine scenarios")
    if not 1 <= args.timeout <= 300:
        parser.error("timeout must be between 1 and 300 seconds")
    if args.compatible_hud and args.no_hud:
        parser.error("--compatible-hud requires the HUD")
    if args.display_scale != 1 and not args.graceful_exit:
        parser.error("--display-scale requires --graceful-exit")
    if args.tray_recovery and not (args.no_hud and args.graceful_exit):
        parser.error("--tray-recovery requires --no-hud and --graceful-exit")
    if args.tray_background and (not args.graceful_exit or args.no_hud or args.tray_recovery):
        parser.error("--tray-background requires --graceful-exit and cannot use --no-hud or --tray-recovery")
    recording_smoke(args.package.resolve(), args.timeout, args.renderer, not args.no_hud, args.check_ui, args.compatible_hud, args.graceful_exit, args.display_scale, args.jet, args.wep, args.wep_dropout, args.tray_recovery, args.tray_background, args.hud_renderer, args.poll_interval_ms)
