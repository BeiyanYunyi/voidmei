import json
from pathlib import Path
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from unittest.mock import patch
import capture_flight_sequence as recorder
import mock_8111 as mock


class CaptureFlightSequenceTest(unittest.TestCase):
    def test_recorded_changes_failures_and_replay_schema(self):
        counts = {}
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args): pass
            def do_GET(self):
                counts[self.path] = counts.get(self.path, 0) + 1
                index = counts[self.path]
                if self.path == '/state' and index == 2:
                    self.send_error(503)
                    return
                body = json.dumps({'valid': True, 'IAS, km/h': 380 + index, 'type': 'p-51c-10-nt'}).encode()
                self.send_response(200)
                self.send_header('Content-Length', str(len(body)))
                self.end_headers()
                self.wfile.write(body)
        server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with tempfile.TemporaryDirectory() as root:
                output = Path(root) / 'capture'
                count = recorder.capture(output, server.server_port, duration=.35, interval_ms=50)
                snapshots = mock.SnapshotStore(output / 'snapshots').load_all()
                scenarios = mock.load_scenarios(output / 'scenarios')
                scenario = scenarios['recording']
                self.assertGreaterEqual(count, 3)
                self.assertEqual(count, len(scenario.steps))
                self.assertEqual([], scenario.missing_snapshots(snapshots))
                self.assertEqual({'valid': False}, snapshots['flight-000001']['/state'])
                self.assertTrue(snapshots['flight-000002']['/state']['valid'])
                self.assertNotEqual(snapshots['flight-000000']['/state'], snapshots['flight-000002']['/state'])
                report = json.loads((output / 'capture.json').read_text())
                self.assertEqual(1, len(report['errors']))
                self.assertTrue(all(step['duration_ms'] > 0 for step in scenario.steps))
                self.assertAlmostEqual(report['elapsed_ms'], scenario.total_ms(), delta=count + 5)
                with self.assertRaises(FileExistsError):
                    recorder.capture(output, server.server_port, duration=.1)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_interrupt_preserves_completed_samples(self):
        with tempfile.TemporaryDirectory() as root:
            output = Path(root) / 'capture'
            with patch.object(recorder, 'read_endpoint', return_value=({'valid': True}, None)), patch.object(recorder.time, 'sleep', side_effect=KeyboardInterrupt):
                self.assertEqual(1, recorder.capture(output, duration=1))
            report = json.loads((output / 'capture.json').read_text())
            self.assertTrue(report['interrupted'])
            self.assertEqual(1, len(mock.load_scenarios(output / 'scenarios')['recording'].steps))

    def test_output_cap_stops_without_invalid_scenario_references(self):
        with tempfile.TemporaryDirectory() as root:
            with patch.object(recorder, 'MAX_OUTPUT', 180), patch.object(recorder, 'read_endpoint', return_value=({'valid': True}, None)):
                output = Path(root) / 'capture'
                count = recorder.capture(output, duration=1, interval_ms=50)
            report = json.loads((output / 'capture.json').read_text())
            self.assertTrue(report['capacity_reached'])
            self.assertGreater(count, 0)
            self.assertLessEqual(report['snapshot_bytes'], 180)
            self.assertEqual(count, len(list((output / 'snapshots').glob('*.json'))))


if __name__ == '__main__':
    unittest.main()
