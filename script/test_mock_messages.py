"""HTTP regression checks for synthetic HUD messages; run with python script/test_mock_messages.py."""
import json
import tempfile
from pathlib import Path
from types import SimpleNamespace
import threading
import unittest
import urllib.error
import urllib.request

import mock_8111 as mock


class GameMessagesTest(unittest.TestCase):
    def setUp(self):
        self.snapshots = mock.SnapshotStore(mock.DEFAULT_SNAPSHOTS_DIR).load_all()
        self.engine = mock.MockEngine()
        self.engine.set_snapshot("messages_more")
        self.server = mock.MockServer(("127.0.0.1", 0), mock.MockRequestHandler,
                                      mock.MockState(self.engine, self.snapshots), {})
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base = "http://127.0.0.1:%d" % self.server.server_address[1]

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def read(self, path):
        with urllib.request.urlopen(self.base + path, timeout=2) as response:
            return json.load(response)

    def test_independent_cursors_and_restart_do_not_mutate_snapshots(self):
        result = self.read("/hudmsg?lastEvt=1&lastDmg=0")
        self.assertEqual([2], [item["id"] for item in result["events"]])
        self.assertEqual([1, 3], [item["id"] for item in result["damage"]])
        self.assertIn('"温度"', result["damage"][0]["msg"])
        self.assertEqual({"events": [], "damage": []}, self.read("/hudmsg?lastEvt=2&lastDmg=3"))
        self.assertEqual(2, len(self.snapshots["messages_more"]["/hudmsg"]["events"]))
        self.engine.set_snapshot("messages_reset")
        self.assertEqual({"events": [], "damage": []}, self.read("/hudmsg?lastEvt=2&lastDmg=3"))
        self.assertEqual("模拟事件：新一局", self.read("/hudmsg")["events"][0]["msg"])

    def test_bad_queries_return_400_and_raw_override_remains_raw(self):
        for query in ("lastEvt=-1", "lastDmg=", "lastEvt=1&lastEvt=2", "lastDmg=2147483648"):
            with self.assertRaises(urllib.error.HTTPError) as error:
                self.read("/hudmsg?" + query)
            self.assertEqual(400, error.exception.code)
            error.exception.close()
        self.engine.set_raw_override("/hudmsg", "broken json")
        with urllib.request.urlopen(self.base + "/hudmsg?lastEvt=99", timeout=2) as response:
            self.assertEqual(b"broken json", response.read())

    def test_disconnect_and_recovery_retain_cursor_semantics(self):
        self.engine.set_scenario(mock.Scenario("offline", [
            {"snapshot": "messages_more", "duration_ms": 60000, "behavior": {"disconnect": True}}
        ]))
        with self.assertRaises(ConnectionError):
            self.read("/hudmsg?lastEvt=1&lastDmg=1")
        self.engine.set_snapshot("messages_more")
        result = self.read("/hudmsg?lastEvt=1&lastDmg=1")
        self.assertEqual([2], [item["id"] for item in result["events"]])
        self.assertEqual([3], [item["id"] for item in result["damage"]])

    def test_binary_map_capture_and_replay_preserve_bytes(self):
        self.engine.set_snapshot("map_wide")
        expected = mock.decode_map_image(self.snapshots["map_wide"]["/map.img"])
        with urllib.request.urlopen(self.base + "/map.img", timeout=2) as response:
            self.assertEqual(expected, response.read())
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "capture.json"
            self.assertEqual(0, mock.run_capture(SimpleNamespace(
                source_port=self.server.server_address[1], save_as=None, file=path)))
            captured = json.loads(path.read_text())
            self.assertEqual(expected, mock.decode_map_image(captured["/map.img"]))
        self.engine.set_snapshot("map_tall")
        self.assertEqual(102, self.read("/map_info.json")["map_generation"])
        with urllib.request.urlopen(self.base + "/map.img", timeout=2) as response:
            self.assertNotEqual(expected, response.read())
        self.engine.set_raw_override("/map.img", "broken image")
        with urllib.request.urlopen(self.base + "/map.img", timeout=2) as response:
            self.assertEqual(b"broken image", response.read())

    def test_map_snapshot_rejects_invalid_encoding_and_excess_size(self):
        for content in ({}, {"base64": "!bad"}, {"base64": ""},
                        {"base64": "A" * (((mock.MAX_MAP_IMAGE_BYTES + 2) // 3) * 4 + 4)}):
            with self.assertRaises(ValueError):
                mock.decode_map_image(content)

    def test_existing_endpoints_and_legacy_response_envelope_are_preserved(self):
        self.assertEqual(self.snapshots["messages_more"]["/state"], self.read("/state"))
        kind, raw = self.server.mock_state.game_response("/hudmsg")
        self.assertEqual("bytes", kind)
        headers, body = raw.split(b"\r\n\r\n", 1)
        self.assertEqual(5, len(headers.split(b"\r\n")))
        self.assertNotIn(b"Content-Type", headers)
        self.assertIn(b'"events": ', body)
        scenario = mock.load_scenarios(mock.DEFAULT_SCENARIOS_DIR)["game_messages"]
        self.assertTrue(all(step["snapshot"] in self.snapshots for step in scenario.steps))


if __name__ == "__main__":
    unittest.main()
