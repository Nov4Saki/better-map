# BetterMap 🧭
### High-Resolution Minecraft World Navigator, Standalone Fabric Mod & Real-Time Player Sync

**BetterMap** is a high-performance, standalone mapping and player telemetry ecosystem for modern Minecraft Java Edition (**1.21.11 through 26.2**). It provides an in-game Minimap HUD, a fullscreen interactive World Map, an in-game Waypoint management system, and real-time player synchronization across both Minecraft and the web—with **zero external mod dependencies**.

---

## 🌟 Key Features

### 1. In-Game Minimap HUD
* **Authentic Cartography**: Renders top-down terrain with hillside slope shading derived directly from block states and heightmaps.
* **Zero Fog-of-War**: Seamlessly blends live loaded chunks with the surveyed high-resolution world map, allowing you to see the surrounding continent beyond render distance.
* **Compass & Telemetry**: Cardinal direction markers (N, S, E, W), real-time coordinates `(X, Y, Z)`, and network sync status.
* **Player & Waypoint Tracking**: Friends with the mod appear as distinct colored markers with name tags and distance.

### 2. Fullscreen Interactive World Map (`M` Key)
* **Smooth Navigation**: Pan by dragging the mouse, zoom smoothly from 20% to 400% with the mouse wheel.
* **Live Player Telemetry**: Displays synchronized players with live health bars and elevation indicators (`▲` above you, `▼` below you).
* **Synced Players Sidebar**: Collapsible roster displaying all online players, their coordinates, and meter distance from you.
* **Interactive Waypoint Placement**: Double-click or right-click anywhere on the map to set a waypoint at that exact world coordinate.

### 3. Waypoint System (`B` Key)
* **Quick Waypoint**: Press `B` anywhere in-game to create a waypoint at your current position.
* **Waypoint Manager**: View all waypoints, toggle visibility (`👁 / Ø`), teleport directly (`TP`), or delete.
* **Color Customization**: 8 vibrant theme colors (Red, Green, Blue, Gold, Purple, Cyan, Orange, White).

### 4. Multi-Tier Networking Engine
* **Tier 1 (Third-Party Server)**: Connects to a lightweight WebSocket relay (`sync_server.py`) using pure standard Java 25 (`java.net.http.WebSocket`).
* **Tier 2 (Serverless LAN)**: Auto-discovers and syncs players over the local network via UDP datagrams on port `24454`.
* **Tier 3 (Serverless Vanilla Chat)**: Communicates discreetly on unmodded vanilla servers using suppressed chat beacons (`ClientReceiveMessageEvents.ALLOW_CHAT`), keeping chat 100% clean.

### 5. Multi-Level High-Resolution Web Map
* **Surveyed World Canvas**: Over **64 square kilometers** (8,050 × 8,070 blocks) surveyed at 0.28 scale.
* **Deep-Zoom Slippy Tiles**: 3,249 full-resolution 512×512 tiles with an interactive canvas streamer (`index.html` / `viewer.js`).
* **Live Web Telemetry**: Streams in-game players and their look directions onto the web map in real time.

---

## 🚀 Quick Start

### Installing the Fabric Mod
1. Ensure you have **Fabric Loader** (0.19.3+) and **Fabric API** installed for Minecraft 1.21.11 / 26.2.
2. Download the pre-built mod JAR from [`build/libs/better_map-1.0.0.jar`](build/libs/better_map-1.0.0.jar) into your `.minecraft/mods/` folder.
3. Launch Minecraft and press:
   * **`M`**: Open / close World Map
   * **`N`**: Toggle Minimap HUD
   * **`B`**: Quick Waypoint at current location

### Running the Relay Server & Web Map
BetterMap includes a live, 24/7 public third-party cloud relay server hosted on GitHub Codespaces (Azure):
* **Live Public WebSocket Endpoint**: `wss://wretched-skeleton-977v66x5j9pq2xvgg-8080.app.github.dev`
* **Health Check & Telemetry Status**: `https://wretched-skeleton-977v66x5j9pq2xvgg-8080.app.github.dev/health`

To run your own local instance:
```bash
node server.js 8080
```
* **Local WebSocket Relay**: `ws://localhost:8080` (relays player positions between clients)
* **Local Web Map UI**: `http://localhost:8080/index.html` (live multi-level map viewer)

### Building from Source
Built using Gradle 9.5.1 and Java 25:
```bash
./gradlew build
```

---

## 📄 License
MIT License.
