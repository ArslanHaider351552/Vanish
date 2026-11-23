"""Vanish relay: pure protocol logic (no sockets, no clock of its own, no logging of content).

Assumed protocol v0 -- replace after the class protocol review.

Client -> relay (JSON text frames)
  {"type":"hello","version":0,"name":"Ali","device_id":"abc"}
  {"type":"msg","id":"<uuid>","sender":"Ali","text":"hi"}      (relay ignores 'sender' and uses the hello name)
  {"type":"ping"}

Relay -> client
  {"type":"hello_ok","server_time":<ms>,"ttl_ms":10000}
  {"type":"msg","id":..,"sender":..,"text":..,"sent_at":<ms>,"expires_at":<ms>}   (broadcast to everyone, sender included)
  {"type":"ack","id":..,"sent_at":..,"expires_at":..}                              (to the sender only)
  {"type":"expired","id":..}                                                       (broadcast)
  {"type":"pong","server_time":<ms>}
  {"type":"error","reason":..}

All times are the relay's clock, in epoch milliseconds.
"""
import json

TTL_MS = 10_000
MAX_TEXT = 500
MAX_NAME = 32
SEEN_GRACE_MS = 60_000   # remember ids a while after expiry so late duplicates are ignored
VERSION = 0
ALL = "*"


class Relay:
    def __init__(self, ttl_ms=TTL_MS):
        self.ttl_ms = ttl_ms
        self.clients = {}   # client_id -> name
        self.live = {}      # message id -> message dict (in memory only)
        self.seen = {}      # message id -> forget_at

    def disconnect(self, cid):
        self.clients.pop(cid, None)

    def handle(self, cid, raw, now):
        """Return a list of (target, payload). target is a client id or ALL."""
        try:
            data = json.loads(raw)
        except (ValueError, TypeError):
            return []
        if not isinstance(data, dict):
            return []
        kind = data.get("type")

        if kind == "hello":
            name = data.get("name")
            if data.get("version") != VERSION:
                return [(cid, {"type": "error", "reason": "version"})]
            if not isinstance(name, str) or not name.strip():
                return [(cid, {"type": "error", "reason": "name"})]
            self.clients[cid] = name.strip()[:MAX_NAME]
            out = [(cid, {"type": "hello_ok", "server_time": now, "ttl_ms": self.ttl_ms})]
            # Replay only messages that are still alive, so a reconnecting phone catches up.
            for m in self.live.values():
                if m["expires_at"] > now:
                    out.append((cid, m))
            return out

        if kind == "ping":
            return [(cid, {"type": "pong", "server_time": now})]

        if kind == "msg":
            if cid not in self.clients:
                return [(cid, {"type": "error", "reason": "no_hello"})]
            mid, text = data.get("id"), data.get("text")
            if not isinstance(mid, str) or not mid or len(mid) > 64:
                return [(cid, {"type": "error", "reason": "id"})]
            if not isinstance(text, str) or not text.strip() or len(text) > MAX_TEXT:
                return [(cid, {"type": "error", "reason": "text"})]
            if mid in self.seen:
                # Duplicate (e.g. a retry after a timeout): confirm again, never re-broadcast.
                m = self.live.get(mid)
                if m:
                    return [(cid, {"type": "ack", "id": mid, "sent_at": m["sent_at"], "expires_at": m["expires_at"]})]
                return [(cid, {"type": "ack", "id": mid, "sent_at": now, "expires_at": now})]
            msg = {"type": "msg", "id": mid, "sender": self.clients[cid], "text": text,
                   "sent_at": now, "expires_at": now + self.ttl_ms}
            self.live[mid] = msg
            self.seen[mid] = msg["expires_at"] + SEEN_GRACE_MS
            ack = {"type": "ack", "id": mid, "sent_at": msg["sent_at"], "expires_at": msg["expires_at"]}
            return [(cid, ack), (ALL, msg)]

        return []

    def tick(self, now):
        """Delete expired messages from memory and announce them."""
        out = []
        for mid in [i for i, m in self.live.items() if m["expires_at"] <= now]:
            del self.live[mid]
            out.append((ALL, {"type": "expired", "id": mid}))
        for mid in [i for i, t in self.seen.items() if t <= now]:
            del self.seen[mid]
        return out
