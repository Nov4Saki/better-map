/**
 * BetterMap WebSocket Sync Relay & Web Viewer Server
 * ===================================================
 * Runs anywhere Node.js is supported:
 *  - Kerit Cloud / Pterodactyl Discord Bot hosting
 *  - Render, Glitch, Railway, Koyeb, Heroku, VPS, or local PC
 *
 * Usage:
 *   node server.js
 *   PORT=8080 node server.js
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const { WebSocketServer, WebSocket } = require('ws');

const PORT = parseInt(process.env.PORT || process.argv[2] || '8080', 10);
const HOST = process.env.HOST || '0.0.0.0';

// Room states: room_id -> Set of WebSockets
const ROOM_CONNECTIONS = new Map();
// Room players: room_id -> Map of uuid -> playerObject
const ROOM_PLAYERS = new Map();
// Socket metadata: ws -> { room, uuid, name }
const CLIENT_META = new WeakMap();

const MIME_TYPES = {
    '.html': 'text/html; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.js': 'application/javascript; charset=utf-8',
    '.json': 'application/json; charset=utf-8',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.jpeg': 'image/jpeg',
    '.svg': 'image/svg+xml',
    '.ico': 'image/x-icon'
};

// HTTP Server for Health Checks & Web Map Viewer
const server = http.createServer((req, res) => {
    // Enable CORS
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');

    if (req.method === 'OPTIONS') {
        res.writeHead(204);
        res.end();
        return;
    }

    const parsedUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    let reqPath = decodeURIComponent(parsedUrl.pathname);

    // Health check endpoint
    if (reqPath === '/health') {
        let totalClients = 0;
        for (const set of ROOM_CONNECTIONS.values()) {
            totalClients += set.size;
        }
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            status: 'ok',
            service: 'BetterMap-Sync-Relay',
            uptime: Math.floor(process.uptime()),
            rooms: ROOM_CONNECTIONS.size,
            clients: totalClients
        }));
        return;
    }

    if (reqPath === '/') {
        reqPath = '/index.html';
    }

    // Serve static files safely
    const safePath = path.normalize(path.join(__dirname, reqPath));
    if (!safePath.startsWith(__dirname)) {
        res.writeHead(403);
        res.end('Forbidden');
        return;
    }

    fs.stat(safePath, (err, stats) => {
        if (err || !stats.isFile()) {
            res.writeHead(404, { 'Content-Type': 'text/plain' });
            res.end('404 Not Found');
            return;
        }

        const ext = path.extname(safePath).toLowerCase();
        const contentType = MIME_TYPES[ext] || 'application/octet-stream';

        res.writeHead(200, {
            'Content-Type': contentType,
            'Content-Length': stats.size,
            'Cache-Control': ext === '.jpg' || ext === '.png' ? 'public, max-age=86400' : 'no-cache'
        });

        fs.createReadStream(safePath).pipe(res);
    });
});

// WebSocket Server attached to same HTTP Server & Port
const wss = new WebSocketServer({ server });

function broadcastToRoom(room, message, senderWs = null) {
    const clients = ROOM_CONNECTIONS.get(room);
    if (!clients) return;

    const payload = typeof message === 'string' ? message : JSON.stringify(message);
    for (const ws of clients) {
        if (ws !== senderWs && ws.readyState === WebSocket.OPEN) {
            try {
                ws.send(payload);
            } catch (e) {
                // Ignore send errors; cleanup happens on close
            }
        }
    }
}

wss.on('connection', (ws, req) => {
    let currentRoom = 'default';
    let clientUuid = null;
    let clientName = 'Player';

    ws.on('message', (raw) => {
        let data;
        try {
            data = JSON.parse(raw.toString('utf8'));
        } catch (e) {
            return;
        }

        const type = data.type;

        if (type === 'join') {
            currentRoom = String(data.room || 'default');
            clientUuid = data.uuid || null;
            clientName = data.name || 'Player';

            if (!ROOM_CONNECTIONS.has(currentRoom)) {
                ROOM_CONNECTIONS.set(currentRoom, new Set());
                ROOM_PLAYERS.set(currentRoom, new Map());
            }

            ROOM_CONNECTIONS.get(currentRoom).add(ws);
            CLIENT_META.set(ws, { room: currentRoom, uuid: clientUuid, name: clientName });

            console.log(`[+] Client '${clientName}' (${clientUuid || 'viewer'}) joined room '${currentRoom}'`);

            // Send active players in room
            const players = Array.from(ROOM_PLAYERS.get(currentRoom).values());
            ws.send(JSON.stringify({
                type: 'init',
                players: players
            }));
        } else if (type === 'pos') {
            const pUuid = data.uuid || clientUuid;
            const pName = data.name || clientName;
            if (!pUuid) return;

            const playerData = {
                uuid: pUuid,
                name: pName,
                x: Math.round(Number(data.x || 0) * 100) / 100,
                y: Math.round(Number(data.y || 0) * 100) / 100,
                z: Math.round(Number(data.z || 0) * 100) / 100,
                yaw: Math.round(Number(data.yaw || 0) * 10) / 10,
                pitch: Math.round(Number(data.pitch || 0) * 10) / 10,
                dimension: String(data.dimension || 'minecraft:overworld'),
                health: Number(data.health ?? 20.0),
                last_seen: Date.now() / 1000
            };

            const roomPlayers = ROOM_PLAYERS.get(currentRoom);
            if (roomPlayers) {
                roomPlayers.set(pUuid, playerData);
            }

            broadcastToRoom(currentRoom, {
                type: 'update',
                player: playerData
            }, ws);
        } else if (type === 'query') {
            const roomPlayers = ROOM_PLAYERS.get(currentRoom);
            const players = roomPlayers ? Array.from(roomPlayers.values()) : [];
            ws.send(JSON.stringify({
                type: 'sync',
                players: players
            }));
        } else if (type === 'waypoint_add' || type === 'waypoint_remove') {
            broadcastToRoom(currentRoom, data, ws);
        }
    });

    ws.on('close', () => {
        const clients = ROOM_CONNECTIONS.get(currentRoom);
        if (clients) {
            clients.delete(ws);
            if (clients.size === 0) {
                ROOM_CONNECTIONS.delete(currentRoom);
            }
        }

        if (clientUuid) {
            const roomPlayers = ROOM_PLAYERS.get(currentRoom);
            if (roomPlayers) {
                roomPlayers.delete(clientUuid);
                if (roomPlayers.size === 0 && (!clients || clients.size === 0)) {
                    ROOM_PLAYERS.delete(currentRoom);
                }
            }
            broadcastToRoom(currentRoom, {
                type: 'leave',
                uuid: clientUuid,
                name: clientName
            });
        }

        console.log(`[-] Client '${clientName}' left room '${currentRoom}'`);
    });
});

// Periodically prune stale players (> 15 seconds without heartbeat)
setInterval(() => {
    const now = Date.now() / 1000;
    for (const [room, players] of ROOM_PLAYERS.entries()) {
        const staleUuids = [];
        for (const [uuid, p] of players.entries()) {
            if (now - (p.last_seen || 0) > 15.0) {
                staleUuids.push({ uuid, name: p.name });
            }
        }

        for (const stale of staleUuids) {
            players.delete(stale.uuid);
            broadcastToRoom(room, {
                type: 'leave',
                uuid: stale.uuid,
                name: stale.name
            });
            console.log(`[!] Pruned stale player '${stale.name}' (${stale.uuid}) in room '${room}'`);
        }
    }
}, 5000);

server.listen(PORT, HOST, () => {
    console.log('=====================================================');
    console.log(`  BetterMap Node.js Sync Relay & Web Server`);
    console.log(`  Listening on http://${HOST}:${PORT}`);
    console.log(`  WebSocket endpoint: ws://${HOST === '0.0.0.0' ? 'localhost' : HOST}:${PORT}`);
    console.log(`  Web Map UI: http://${HOST === '0.0.0.0' ? 'localhost' : HOST}:${PORT}`);
    console.log(`  Health Check: http://${HOST === '0.0.0.0' ? 'localhost' : HOST}:${PORT}/health`);
    console.log('=====================================================');
});
