package com.bettermap.network;

import com.bettermap.config.BetterMapConfig;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class StealthChatSync {
    // Magic prefix with zero-width spaces for stealth
    public static final String MAGIC_PREFIX = "\u200B\u200B[BM]";

    private final PlayerSyncManager syncManager;
    private final BetterMapConfig config;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    public StealthChatSync(PlayerSyncManager syncManager, BetterMapConfig config) {
        this.syncManager = syncManager;
        this.config = config;
    }

    public synchronized void registerEvents() {
        if (registered.getAndSet(true)) return;

        // Catch and suppress player chat messages containing stealth telemetry
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, bound, timestamp) -> {
            if (message != null && handleIncoming(message.getString())) {
                return false; // Suppress from Chat HUD
            }
            return true;
        });

        // Catch game/system messages containing stealth telemetry
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (message != null && handleIncoming(message.getString())) {
                return false; // Suppress from Chat HUD
            }
            return true;
        });
    }

    private boolean handleIncoming(String rawText) {
        int idx = rawText.indexOf("[BM]");
        if (idx == -1) return false;

        try {
            String payload = rawText.substring(idx + 4);
            // Payload format: room:uuid:name:x:y:z:yaw:dim:health
            String[] parts = payload.split(":");
            if (parts.length < 8) return false;

            String room = parts[0];
            if (!room.equals(config.getRoomKey())) return true; // Handled/suppressed even if different room

            UUID uuid = UUID.fromString(parts[1]);
            if (uuid.equals(syncManager.getLocalPlayerUuid())) return true;

            String name = parts[2];
            double x = Double.parseDouble(parts[3]);
            double y = Double.parseDouble(parts[4]);
            double z = Double.parseDouble(parts[5]);
            float yaw = Float.parseFloat(parts[6]);
            String dim = parts[7];
            float health = parts.length > 8 ? Float.parseFloat(parts[8]) : 20.0f;

            syncManager.updateRemotePlayer(uuid, name, x, y, z, yaw, 0f, dim, health);
            return true; // Successfully intercepted and parsed
        } catch (Exception ignored) {
            return false;
        }
    }

    public void sendPosition(UUID uuid, String name, double x, double y, double z, float yaw, float pitch, String dimension, float health) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null || client.player == null) return;

        try {
            String payload = String.format("%s%s:%s:%s:%.1f:%.1f:%.1f:%.0f:%s:%.0f",
                    MAGIC_PREFIX,
                    config.getRoomKey(),
                    uuid.toString(),
                    name,
                    x, y, z, yaw, dimension, health);

            client.getConnection().sendChat(payload);
        } catch (Exception ignored) {}
    }
}
