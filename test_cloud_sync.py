"""
End-to-End Live Public Cloud WebSocket Sync Verification
=========================================================
Tests the BetterMap sync server over the live public internet endpoint.
"""

import asyncio
import json
import websockets
import sys

PUBLIC_WSS_URL = "wss://wretched-skeleton-977v66x5j9pq2xvgg-8080.app.github.dev"

async def test_live_sync():
    print(f"Connecting to live public WebSocket server: {PUBLIC_WSS_URL}")

    # 1. Connect Client 1 (Player: Steve_Alpha)
    async with websockets.connect(PUBLIC_WSS_URL) as ws1:
        print("[Client 1: Steve_Alpha] Connected.")
        await ws1.send(json.dumps({
            "type": "join",
            "room": "default",
            "uuid": "player-111",
            "name": "Steve_Alpha"
        }))
        init1 = json.loads(await ws1.recv())
        print(f"[Client 1] Received init: {init1}")
        assert init1.get("type") == "init"

        # 2. Connect Client 2 (Player: Alex_Beta)
        async with websockets.connect(PUBLIC_WSS_URL) as ws2:
            print("[Client 2: Alex_Beta] Connected.")
            await ws2.send(json.dumps({
                "type": "join",
                "room": "default",
                "uuid": "player-222",
                "name": "Alex_Beta"
            }))
            init2 = json.loads(await ws2.recv())
            print(f"[Client 2] Received init: {init2}")
            assert init2.get("type") == "init"

            # 3. Client 1 sends position telemetry
            pos_packet = {
                "type": "pos",
                "uuid": "player-111",
                "name": "Steve_Alpha",
                "x": 1250.5,
                "y": 72.0,
                "z": -840.25,
                "yaw": 180.0,
                "pitch": -5.0,
                "dimension": "minecraft:overworld",
                "health": 18.5
            }
            print(f"[Client 1] Sending position: {pos_packet['name']} at ({pos_packet['x']}, {pos_packet['y']}, {pos_packet['z']})")
            await ws1.send(json.dumps(pos_packet))

            # 4. Client 2 receives the update packet
            msg_for_c2 = json.loads(await asyncio.wait_for(ws2.recv(), timeout=5.0))
            print(f"[Client 2] Successfully received relayed telemetry: {msg_for_c2}")
            assert msg_for_c2.get("type") == "update"
            assert msg_for_c2["player"]["uuid"] == "player-111"
            assert msg_for_c2["player"]["x"] == 1250.5
            assert msg_for_c2["player"]["z"] == -840.25

            # 5. Connect Client 3 (Web Map Viewer)
            async with websockets.connect(PUBLIC_WSS_URL) as ws3:
                print("[Client 3: WebMapViewer] Connected.")
                await ws3.send(json.dumps({
                    "type": "join",
                    "room": "default",
                    "uuid": None,
                    "name": "WebMapViewer"
                }))
                init3 = json.loads(await ws3.recv())
                print(f"[Client 3] Received init with players: {init3.get('players')}")

                # Query all active players
                await ws3.send(json.dumps({"type": "query"}))
                sync3 = json.loads(await asyncio.wait_for(ws3.recv(), timeout=5.0))
                print(f"[Client 3] Received sync response: {sync3}")
                assert sync3.get("type") == "sync"
                active_uuids = [p["uuid"] for p in sync3.get("players", [])]
                print(f"[Client 3] Active synced UUIDs in room: {active_uuids}")
                assert "player-111" in active_uuids

                # 6. Waypoint sync test
                wp_packet = {
                    "type": "waypoint_add",
                    "room": "default",
                    "id": "wp-stronghold-01",
                    "name": "Ender Portal Stronghold",
                    "x": 2048,
                    "y": 32,
                    "z": -1500,
                    "color": -65536,
                    "symbol": "portal"
                }
                print(f"[Client 1] Broadcasting waypoint: '{wp_packet['name']}'")
                await ws1.send(json.dumps(wp_packet))

                wp_c2 = json.loads(await asyncio.wait_for(ws2.recv(), timeout=5.0))
                print(f"[Client 2] Received shared waypoint: {wp_c2}")
                assert wp_c2.get("type") == "waypoint_add"
                assert wp_c2.get("name") == "Ender Portal Stronghold"

                wp_c3 = json.loads(await asyncio.wait_for(ws3.recv(), timeout=5.0))
                print(f"[Client 3] Web Viewer received shared waypoint: {wp_c3}")
                assert wp_c3.get("type") == "waypoint_add"

    print("\n=======================================================")
    print(" ALL LIVE REMOTE WEBSOCKET TESTS PASSED SUCCESSFULLY! ")
    print("=======================================================")

if __name__ == "__main__":
    asyncio.run(test_live_sync())
