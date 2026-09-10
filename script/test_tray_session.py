#!/usr/bin/env python3
"""Run a command with a temporary tray manager on an already isolated X11 display."""
import os
from pathlib import Path
import subprocess
import sys
import time


def main():
    if os.environ.get("VOIDMEI_TEST_ISOLATED_X11") != "1" or not os.environ.get("DISPLAY"):
        raise SystemExit("A dedicated X11 display and VOIDMEI_TEST_ISOLATED_X11=1 are required")
    if len(sys.argv) < 2:
        raise SystemExit("Usage: test_tray_session.py COMMAND [ARGS...]")
    log_dir = Path("desktop/build/native-tray")
    log_dir.mkdir(parents=True, exist_ok=True)
    with (log_dir / "session-manager.log").open("w") as log:
        manager = subprocess.Popen([os.environ.get("VOIDMEI_TRAY_MANAGER", "stalonetray"), "--config", "/dev/null"],
                                   stdout=log, stderr=log)
        try:
            time.sleep(.5)
            if manager.poll() is not None:
                raise RuntimeError("Tray manager exited; inspect " + str(log_dir / "session-manager.log"))
            result = subprocess.run(sys.argv[1:])
        finally:
            manager.terminate()
            try:
                manager.wait(timeout=3)
            except subprocess.TimeoutExpired:
                manager.kill()
                manager.wait(timeout=3)
    return result.returncode


if __name__ == "__main__":
    sys.exit(main())
