"""Exit diagnostics exercised with owned child processes, without a graphical desktop."""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from smoke_kmp_deb import wait_for_test_exit


class ExitDiagnosticsTest(unittest.TestCase):
    def run_child(self, source, check):
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / "startup.log"
            with log.open("w") as output:
                process = subprocess.Popen([sys.executable, "-u", "-c", source], stdout=output, stderr=output)
                try:
                    check(process, log)
                finally:
                    if process.poll() is None:
                        process.terminate()
                    process.wait(timeout=5)

    def test_agent_failure_is_reported_while_process_is_still_alive(self):
        def check(process, log):
            with self.assertRaisesRegex(RuntimeError, "HUD engine control gauge not visible"):
                wait_for_test_exit(process, log, timeout=3)
            self.assertIsNone(process.poll())
        self.run_child("import time; print('[VoidMei exit test] FAILED: java.lang.AssertionError: HUD engine control gauge not visible'); time.sleep(10)", check)

    def test_success_and_nonzero_exit_codes_are_preserved(self):
        for code in (0, 3):
            self.run_child("raise SystemExit(%d)" % code,
                           lambda process, log: self.assertEqual(code, wait_for_test_exit(process, log)))

    def test_timeout_identifies_the_log(self):
        def check(process, log):
            with self.assertRaises(RuntimeError) as failure:
                wait_for_test_exit(process, log, timeout=.05)
            self.assertIn(str(log), str(failure.exception))
            self.assertIn("timed out", str(failure.exception))
        self.run_child("import time; time.sleep(10)", check)


if __name__ == "__main__":
    unittest.main()
