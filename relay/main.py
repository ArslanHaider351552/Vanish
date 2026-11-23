"""Thin WebSocket wrapper around relay_core. Run:  python main.py [port]
Logs only connect/disconnect counts. Message text is never logged or written to disk."""
import asyncio, json, sys, time, uuid
from websockets.asyncio.server import serve
from websockets.exceptions import ConnectionClosed
from relay_core import Relay, ALL

relay = Relay()
sockets = {}  # client_id -> websocket


def now_ms():
    return int(time.time() * 1000)


async def deliver(actions):
    for target, payload in actions:
        data = json.dumps(payload)
        for cid in (list(sockets) if target == ALL else [target]):
            ws = sockets.get(cid)
            if ws is not None:
                try:
                    await ws.send(data)
                except ConnectionClosed:
                    pass


async def handler(ws):
    cid = uuid.uuid4().hex
    sockets[cid] = ws
    print(f"client connected ({len(sockets)} online)")
    try:
        async for raw in ws:
            if isinstance(raw, str):
                await deliver(relay.handle(cid, raw, now_ms()))
    except ConnectionClosed:
        pass
    finally:
        sockets.pop(cid, None)
        relay.disconnect(cid)
        print(f"client left ({len(sockets)} online)")


async def ticker():
    while True:
        await asyncio.sleep(0.25)
        await deliver(relay.tick(now_ms()))


async def main(port):
    task = asyncio.create_task(ticker())  # keep a reference so it is not garbage collected
    async with serve(handler, "0.0.0.0", port, max_size=4096):
        print(f"Vanish relay on ws://0.0.0.0:{port}/ws  (ttl {relay.ttl_ms} ms)")
        await asyncio.Future()
    task.cancel()


if __name__ == "__main__":
    asyncio.run(main(int(sys.argv[1]) if len(sys.argv) > 1 else 8080))
