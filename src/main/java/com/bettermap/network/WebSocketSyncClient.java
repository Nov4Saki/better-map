package com.bettermap.network;

import com.bettermap.config.BetterMapConfig;
import com.bettermap.model.SyncedPlayer;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

public class WebSocketSyncClient implements WebSocket.Listener {
    private static final Gson GSON = new Gson();

    private final PlayerSyncManager syncManager;
    private final BetterMapConfig config;
    private final HttpClient httpClient;
    private WebSocket webSocket;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final StringBuilder messageBuffer = new StringBuilder();

    public WebSocketSyncClient(PlayerSyncManager syncManager, BetterMapConfig config) {
        this.syncManager = syncManager;
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();
    }

    public synchronized void connect() {
        if (connected.get() || connecting.get()) return;
        connecting.set(true);

        String url = config.getServerUrl();
        try {
            URI uri = URI.create(url);
            httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .buildAsync(uri, this)
                    .whenComplete((ws, throwable) -> {
                        connecting.set(false);
                        if (throwable != null) {
                            connected.set(false);
                            // Connection failed, will retry next tick cycle
                        } else {
                            this.webSocket = ws;
                            connected.set(true);
                            sendJoinHandshake();
                        }
                    });
        } catch (Exception e) {
            connecting.set(false);
            connected.set(false);
        }
    }

    public synchronized void disconnect() {
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "disconnect").join();
            } catch (Exception ignored) {}
            webSocket = null;
        }
        connected.set(false);
        connecting.set(false);
    }

    public boolean isConnected() {
        return connected.get();
    }

    private void sendJoinHandshake() {
        if (!connected.get() || webSocket == null) return;
        JsonObject join = new JsonObject();
        join.addProperty("type", "join");
        join.addProperty("room", config.getRoomKey());
        UUID localUuid = syncManager.getLocalPlayerUuid();
        String localName = syncManager.getLocalPlayerName();
        join.addProperty("uuid", localUuid != null ? localUuid.toString() : UUID.randomUUID().toString());
        join.addProperty("name", localName != null ? localName : "Player");

        sendJson(join);
    }

    public void sendPosition(UUID uuid, String name, double x, double y, double z, float yaw, float pitch, String dimension, float health) {
        if (!connected.get() || webSocket == null) return;

        JsonObject pos = new JsonObject();
        pos.addProperty("type", "pos");
        pos.addProperty("uuid", uuid.toString());
        pos.addProperty("name", name);
        pos.addProperty("x", Math.round(x * 100.0) / 100.0);
        pos.addProperty("y", Math.round(y * 100.0) / 100.0);
        pos.addProperty("z", Math.round(z * 100.0) / 100.0);
        pos.addProperty("yaw", Math.round(yaw * 10.0) / 10.0f);
        pos.addProperty("pitch", Math.round(pitch * 10.0) / 10.0f);
        pos.addProperty("dimension", dimension);
        pos.addProperty("health", health);

        sendJson(pos);
    }

    private void sendJson(JsonObject obj) {
        if (webSocket != null && connected.get()) {
            webSocket.sendText(GSON.toJson(obj), true);
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        this.webSocket = webSocket;
        this.connected.set(true);
        WebSocket.Listener.super.onOpen(webSocket);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        messageBuffer.append(data);
        if (last) {
            String fullMessage = messageBuffer.toString();
            messageBuffer.setLength(0);
            processIncomingMessage(fullMessage);
        }
        return WebSocket.Listener.super.onText(webSocket, data, last);
    }

    private void processIncomingMessage(String jsonStr) {
        try {
            JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
            String type = root.has("type") ? root.get("type").getAsString() : "";

            if ("init".equals(type) || "sync".equals(type)) {
                if (root.has("players")) {
                    JsonArray players = root.getAsJsonArray("players");
                    for (JsonElement el : players) {
                        applyPlayerJson(el.getAsJsonObject());
                    }
                }
            } else if ("update".equals(type)) {
                if (root.has("player")) {
                    applyPlayerJson(root.getAsJsonObject("player"));
                }
            } else if ("leave".equals(type)) {
                if (root.has("uuid")) {
                    try {
                        UUID uuid = UUID.fromString(root.get("uuid").getAsString());
                        syncManager.removePlayer(uuid);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }

    private void applyPlayerJson(JsonObject p) {
        try {
            UUID uuid = UUID.fromString(p.get("uuid").getAsString());
            if (uuid.equals(syncManager.getLocalPlayerUuid())) {
                return; // Don't track self as remote
            }
            String name = p.has("name") ? p.get("name").getAsString() : "Player";
            double x = p.get("x").getAsDouble();
            double y = p.get("y").getAsDouble();
            double z = p.get("z").getAsDouble();
            float yaw = p.has("yaw") ? p.get("yaw").getAsFloat() : 0f;
            float pitch = p.has("pitch") ? p.get("pitch").getAsFloat() : 0f;
            String dim = p.has("dimension") ? p.get("dimension").getAsString() : "minecraft:overworld";
            float health = p.has("health") ? p.get("health").getAsFloat() : 20.0f;

            syncManager.updateRemotePlayer(uuid, name, x, y, z, yaw, pitch, dim, health);
        } catch (Exception ignored) {}
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        connected.set(false);
        this.webSocket = null;
        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        connected.set(false);
        this.webSocket = null;
        WebSocket.Listener.super.onError(webSocket, error);
    }
}
