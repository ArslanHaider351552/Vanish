import json, unittest
from relay_core import Relay, ALL


def j(d): return json.dumps(d)
HELLO = {"type": "hello", "version": 0, "name": "Ali", "device_id": "d1"}


class RelayTests(unittest.TestCase):
    def setUp(self):
        self.r = Relay(ttl_ms=10_000)
        self.r.handle("a", j(HELLO), 1000)
        self.r.handle("b", j({**HELLO, "name": "Bo"}), 1000)

    def test_hello_ok_has_server_time(self):
        out = Relay().handle("x", j(HELLO), 5555)
        self.assertEqual(out[0][1]["server_time"], 5555)

    def test_wrong_version_rejected(self):
        out = Relay().handle("x", j({**HELLO, "version": 9}), 0)
        self.assertEqual(out[0][1]["reason"], "version")

    def test_msg_gets_relay_timestamps_and_real_sender(self):
        out = self.r.handle("a", j({"type": "msg", "id": "1", "sender": "FAKE", "text": "hi"}), 2000)
        ack, bc = out
        self.assertEqual(ack[0], "a"); self.assertEqual(bc[0], ALL)
        self.assertEqual(bc[1]["sender"], "Ali")
        self.assertEqual(bc[1]["expires_at"], 12_000)

    def test_duplicate_id_not_rebroadcast(self):
        self.r.handle("a", j({"type": "msg", "id": "1", "text": "hi"}), 2000)
        out = self.r.handle("a", j({"type": "msg", "id": "1", "text": "hi"}), 3000)
        self.assertEqual(len(out), 1); self.assertEqual(out[0][1]["type"], "ack")

    def test_expiry_announced_and_deleted(self):
        self.r.handle("a", j({"type": "msg", "id": "1", "text": "hi"}), 2000)
        self.assertEqual(self.r.tick(11_999), [])
        out = self.r.tick(12_000)
        self.assertEqual(out, [(ALL, {"type": "expired", "id": "1"})])
        self.assertEqual(self.r.live, {})

    def test_late_duplicate_after_expiry_is_ignored(self):
        self.r.handle("a", j({"type": "msg", "id": "1", "text": "hi"}), 2000)
        self.r.tick(12_000)
        out = self.r.handle("a", j({"type": "msg", "id": "1", "text": "hi"}), 20_000)
        self.assertEqual([p["type"] for _, p in out], ["ack"])
        self.assertEqual(self.r.live, {})

    def test_reconnect_replays_only_live(self):
        self.r.handle("a", j({"type": "msg", "id": "1", "text": "old"}), 2000)
        self.r.handle("a", j({"type": "msg", "id": "2", "text": "new"}), 9000)
        out = self.r.handle("c", j({**HELLO, "name": "Cy"}), 13_000)
        ids = [p["id"] for _, p in out if p["type"] == "msg"]
        self.assertEqual(ids, ["2"])

    def test_msg_before_hello_rejected(self):
        out = self.r.handle("zzz", j({"type": "msg", "id": "1", "text": "hi"}), 0)
        self.assertEqual(out[0][1]["reason"], "no_hello")

    def test_garbage_ignored(self):
        self.assertEqual(self.r.handle("a", "not json", 0), [])
        self.assertEqual(self.r.handle("a", "[1,2]", 0), [])


if __name__ == "__main__":
    unittest.main()
