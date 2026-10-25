package com.bettermap.network;

import com.bettermap.config.BetterMapConfig;
import com.bettermap.model.SyncedPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSyncManager {
    private final BetterMapConfig config;
    private final Map<UUID, SyncedPlayer> syncedPlayers = new ConcurrentHashMap<>();

    private final WebSocketSyncClient webSocketClient;
    private final UdpBroadcastSync udpSync;
    private final StealthChatSync stealthChatSync;

    private int tickCounter = 0;

    public PlayerSyncManager(BetterMapConfig config) {
        this.config = config;
        this.webSocketClient = new WebSocketSyncClient(this, config);
        this.udpSync = new UdpBroadcastSync(this, config);
        this.stealthChatSync = new StealthChatSync(this, config);
        this.stealthChatSync.registerEvents();
    }

    public void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) return;

        tickCounter++;

        // Prune stale players every 5 seconds (100 ticks)
        if (tickCounter % 100 == 0) {
            pruneStalePlayers(15000);
        }

        // Check if it is time to dispatch a position update
        int interval = Math.max(2, config.getSyncIntervalTicks());
        if (tickCounter % interval != 0) return;

        UUID uuid = player.getUUID();
        String name = player.getName().getString();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        String dimension = player.level().dimension().identifier().toString();
        float health = player.getHealth();

        BetterMapConfig.SyncMode mode = config.getSyncMode();

        switch (mode) {
            case THIRD_PARTY_SERVER -> {
                if (!webSocketClient.isConnected()) {
                    webSocketClient.connect();
                }
                webSocketClient.sendPosition(uuid, name, x, y, z, yaw, pitch, dimension, health);
            }
            case LAN_BROADCAST -> {
                if (!udpSync.isRunning()) {
                    udpSync.start();
                }
                udpSync.sendPosition(uuid, name, x, y, z, yaw, pitch, dimension, health);
            }
            case STEALTH_CHAT -> {
                stealthChatSync.sendPosition(uuid, name, x, y, z, yaw, pitch, dimension, health);
            }
            case DISABLED -> {
                // Do nothing
            }
        }
    }

    public void updateRemotePlayer(UUID uuid, String name, double x, double y, double z, float yaw, float pitch, String dimension, float health) {
        syncedPlayers.compute(uuid, (key, existing) -> {
            if (existing == null) {
                return new SyncedPlayer(uuid, name, x, y, z, yaw, pitch, dimension, health);
            } else {
                existing.update(name, x, y, z, yaw, pitch, dimension, health);
                return existing;
            }
        });
    }

    public void removePlayer(UUID uuid) {
        syncedPlayers.remove(uuid);
    }

    public void pruneStalePlayers(long maxAgeMs) {
        syncedPlayers.values().removeIf(p -> p.isStale(maxAgeMs));
    }

    public Collection<SyncedPlayer> getSyncedPlayers() {
        return Collections.unmodifiableCollection(syncedPlayers.values());
    }

    public UUID getLocalPlayerUuid() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null ? player.getUUID() : null;
    }

    public String getLocalPlayerName() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null ? player.getName().getString() : "Player";
    }

    public WebSocketSyncClient getWebSocketClient() {
        return webSocketClient;
    }

    public void shutdown() {
        webSocketClient.disconnect();
        udpSync.stop();
        syncedPlayers.clear();
    }
}
