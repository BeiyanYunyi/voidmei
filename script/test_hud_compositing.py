#!/usr/bin/env python3
"""Run a test command on an isolated X11 display with alpha compositing.

Requires Xvfb and xcompmgr in PATH. Uses the software renderer by default;
this verifies compositing, not physical GPU acceleration.
VOIDMEI_TEST_SCREEN_SIZE selects WIDTHxHEIGHT (default 1280x900).
"""
import os
import re
import select
import subprocess
import sys
import time


def run(command):
    if not command:
        raise ValueError("Provide the Gradle test command")
    screen = os.environ.get("VOIDMEI_TEST_SCREEN_SIZE", "1280x900")
    dimensions = re.fullmatch(r"([1-9][0-9]*)x([1-9][0-9]*)", screen)
    if not dimensions or any(not 120 <= int(value) <= 8192 for value in dimensions.groups()):
        raise ValueError("VOIDMEI_TEST_SCREEN_SIZE must be WIDTHxHEIGHT, each 120..8192")
    read_fd, write_fd = os.pipe()
    server = None
    compositor = None
    try:
        server = subprocess.Popen(["Xvfb", "-displayfd", str(write_fd), "-screen", "0",
                                   screen + "x24", "-nolisten", "tcp"], pass_fds=(write_fd,))
        os.close(write_fd)
        write_fd = None
        if not select.select([read_fd], [], [], 10)[0]:
            raise RuntimeError("Xvfb startup timed out")
        display = os.read(read_fd, 100).decode().strip()
        if not display.isdecimal():
            raise RuntimeError("Xvfb did not return a display number")
        environment = dict(os.environ, DISPLAY=":" + display, XDG_SESSION_TYPE="x11",
                           VOIDMEI_TEST_COMPOSITED_X11="1", SKIKO_RENDER_API="SOFTWARE_FAST",
                           VOIDMEI_TEST_EXPECT_RENDERER="SOFTWARE_FAST")
        environment.pop("WAYLAND_DISPLAY", None)
        environment.pop("VOIDMEI_TEST_DESKTOP_PIXELS", None)
        # The -a server-automatic mode does not provide the alpha behavior tested here.
        compositor = subprocess.Popen(["xcompmgr"], env=environment)
        time.sleep(0.5)
        if compositor.poll() is not None:
            raise RuntimeError("The compositing manager exited during startup")
        return subprocess.run(command, env=environment).returncode
    finally:
        os.close(read_fd)
        if write_fd is not None:
            os.close(write_fd)
        for process in (compositor, server):
            if process is not None and process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()


if __name__ == "__main__":
    raise SystemExit(run(sys.argv[1:]))
