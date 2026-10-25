"""
BetterMap Third-Party WebSocket Sync Relay Server
==================================================
Relays real-time Minecraft player positions between BetterMap clients
and optionally streams to the web map viewer (index.html).

Usage:
    python sync_server.py [--port 8080] [--host 0.0.0.0]
"""

import asyncio
import json
import argparse
import time
from typing import Dict, Set

# Connected clients by room: room_id -> set of websockets
ROOM_CONNECTIONS: Dict[str, Set] = {}

# Active player states by room: room_id -> { uuid -> player_dict }
ROOM_PLAYERS: Dict[str, Dict[str, dict]] = {}

# WebSocket to metadata mapping: ws -> { room, uuid, name }
CLIENT_META = {}


async def broadcast(room: str, message: dict, sender_ws=None):
    """Broadcast JSON message to all clients in a room except the sender."""
    if room not in ROOM_CONNECTIONS:
        return
    payload = json.dumps(message)
    dead_sockets = set()
    for ws in list(ROOM_CONNECTIONS[room]):
        if ws == sender_ws:
            continue
        try:
            await ws.send(payload)
        except Exception:
            dead_sockets.add(ws)
    for dead in dead_sockets:
        ROOM_CONNECTIONS[room].discard(dead)


async def handle_connection(websocket):
    room = "default"
    client_uuid = None
    client_name = "Player"

    try:
        async for message in websocket:
            try:
                data = json.loads(message)
            except json.JSONDecodeError:
                continue

            msg_type = data.get("type")

            if msg_type == "join":
                room = str(data.get("room", "default"))
                client_uuid = data.get("uuid")
                client_name = data.get("name", "Player")

                if room not in ROOM_CONNECTIONS:
                    ROOM_CONNECTIONS[room] = set()
                    ROOM_PLAYERS[room] = {}

                ROOM_CONNECTIONS[room].add(websocket)
                CLIENT_META[websocket] = {"room": room, "uuid": client_uuid, "name": client_name}

                print(f"[+] Client '{client_name}' ({client_uuid}) joined room '{room}'")

                # Send initial state of all currently active players in room
                active_players = list(ROOM_PLAYERS[room].values())
                await websocket.send(json.dumps({
                    "type": "init",
                    "players": active_players
                }))

            elif msg_type == "pos":
                p_uuid = data.get("uuid", client_uuid)
                p_name = data.get("name", client_name)
                if not p_uuid:
                    continue

                player_data = {
                    "uuid": p_uuid,
                    "name": p_name,
                    "x": round(float(data.get("x", 0)), 2),
                    "y": round(float(data.get("y", 0)), 2),
                    "z": round(float(data.get("z", 0)), 2),
                    "yaw": round(float(data.get("yaw", 0)), 1),
                    "pitch": round(float(data.get("pitch", 0)), 1),
                    "dimension": data.get("dimension", "minecraft:overworld"),
                    "health": float(data.get("health", 20.0)),
                    "last_seen": time.time()
                }

                if room in ROOM_PLAYERS:
                    ROOM_PLAYERS[room][p_uuid] = player_data

                # Relay position update to others in room
                await broadcast(room, {
                    "type": "update",
                    "player": player_data
                }, sender_ws=websocket)

            elif msg_type == "query":
                # For web map viewers requesting current player list
                players = list(ROOM_PLAYERS.get(room, {}).values())
                await websocket.send(json.dumps({
                    "type": "sync",
                    "players": players
                }))

            elif msg_type in ("waypoint_add", "waypoint_remove"):
                # Relay waypoint events to all other players & web viewers in the room
                await broadcast(room, data, sender_ws=websocket)

    except Exception as e:
        pass
    finally:
        if room in ROOM_CONNECTIONS:
            ROOM_CONNECTIONS[room].discard(websocket)
            if client_uuid and room in ROOM_PLAYERS:
                ROOM_PLAYERS[room].pop(client_uuid, None)
                await broadcast(room, {
                    "type": "leave",
                    "uuid": client_uuid,
                    "name": client_name
                })
        CLIENT_META.pop(websocket, None)
        print(f"[-] Client '{client_name}' left room '{room}'")


async def prune_stale_players():
    """Periodically removes players who haven't sent a heartbeat for 15s."""
    while True:
        await asyncio.sleep(5)
        now = time.time()
        for room, players in list(ROOM_PLAYERS.items()):
            stale_uuids = [u for u, p in players.items() if now - p.get("last_seen", 0) > 15.0]
            for u in stale_uuids:
                p = players.pop(u)
                await broadcast(room, {
                    "type": "leave",
                    "uuid": u,
                    "name": p.get("name")
                })


def start_web_server(host, web_port):
    import http.server
    import threading
    class QuietHandler(http.server.SimpleHTTPRequestHandler):
        def log_message(self, format, *args):
            pass  # Suppress HTTP access logging to keep console clean

    try:
        httpd = http.server.ThreadingHTTPServer((host, web_port), QuietHandler)
        t = threading.Thread(target=httpd.serve_forever, daemon=True)
        t.start()
        display_host = 'localhost' if host in ('0.0.0.0', '') else host
        print(f"  Web Map UI available at: http://{display_host}:{web_port}")
    except Exception as e:
        print(f"  [Notice] Web server on port {web_port} could not start: {e}")


async def main():
    import websockets
    parser = argparse.ArgumentParser(description="BetterMap WebSocket Sync Relay Server")
    parser.add_argument("--host", default="0.0.0.0", help="Host address (default: 0.0.0.0)")
    parser.add_argument("--port", type=int, default=8080, help="WebSocket Port (default: 8080)")
    parser.add_argument("--web-port", type=int, default=8000, help="Web HTTP Server Port (default: 8000)")
    args = parser.parse_args()

    print(f"=====================================================")
    print(f"  BetterMap WebSocket Relay Server running on {args.host}:{args.port}")
    start_web_server(args.host, args.web_port)
    print(f"  Supports Minecraft BetterMap clients & Web Viewers")
    print(f"=====================================================")

    asyncio.create_task(prune_stale_players())

    async with websockets.serve(handle_connection, args.host, args.port):
        await asyncio.Future()  # run forever


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nServer stopped.")
