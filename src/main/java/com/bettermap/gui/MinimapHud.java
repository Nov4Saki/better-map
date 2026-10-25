package com.bettermap.gui;

import com.bettermap.config.BetterMapConfig;
import com.bettermap.map.MapTextureManager;
import com.bettermap.model.SyncedPlayer;
import com.bettermap.model.Waypoint;
import com.bettermap.network.PlayerSyncManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

public class MinimapHud implements HudElement {
    private final BetterMapConfig config;
    private final MapTextureManager textureManager;
    private final PlayerSyncManager syncManager;

    public MinimapHud(BetterMapConfig config, MapTextureManager textureManager, PlayerSyncManager syncManager) {
        this.config = config;
        this.textureManager = textureManager;
        this.syncManager = syncManager;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null) return;

        if (!config.isMinimapEnabled()) return;
        if (client.getDebugOverlay().showDebugScreen()) return; // Hide when F3 debug screen is active

        int size = config.getMinimapSize();
        int margin = 10;
        int x, y;

        switch (config.getMinimapCorner()) {
            case TOP_LEFT -> {
                x = margin;
                y = margin;
            }
            case BOTTOM_LEFT -> {
                x = margin;
                y = extractor.guiHeight() - size - 24;
            }
            case BOTTOM_RIGHT -> {
                x = extractor.guiWidth() - size - margin;
                y = extractor.guiHeight() - size - 24;
            }
            case TOP_RIGHT -> {
                x = extractor.guiWidth() - size - margin;
                y = margin;
            }
            default -> {
                x = extractor.guiWidth() - size - margin;
                y = margin;
            }
        }

        // Trigger texture update for player's current position and zoom
        float zoom = config.getMinimapZoom();
        textureManager.updateMap(client.level, player.getX(), player.getZ(), zoom, false);

        // 1. Background bezel & frame
        extractor.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0xAA000000);
        extractor.outline(x - 2, y - 2, x + size + 2, y + size + 2, 0xFF333333);

        // 2. Render terrain dynamic texture
        extractor.blit(textureManager.getTextureId(), x, y, size, size, 0f, 0f, 1f, 1f);

        // 3. Compass indicators
        if (config.isShowCompass()) {
            Font font = client.font;
            extractor.centeredText(font, "N", x + size / 2, y + 2, 0xFFFF5555);
            extractor.centeredText(font, "S", x + size / 2, y + size - 10, 0xFFAAAAAA);
            extractor.centeredText(font, "W", x + 5, y + size / 2 - 4, 0xFFAAAAAA);
            extractor.centeredText(font, "E", x + size - 5, y + size / 2 - 4, 0xFFAAAAAA);
        }

        int centerX = x + size / 2;
        int centerY = y + size / 2;
        float halfWorldSpan = (MapTextureManager.TEXTURE_SIZE / 2.0f) / Math.max(0.2f, zoom);

        // 4. Render Waypoints
        if (config.isShowWaypoints()) {
            String currentDim = player.level().dimension().identifier().toString();
            for (Waypoint wp : config.getWaypoints()) {
                if (!wp.isEnabled() || !currentDim.equals(wp.getDimension())) continue;

                double dx = wp.getX() - player.getX();
                double dz = wp.getZ() - player.getZ();

                int wpPx = centerX + (int) ((dx / halfWorldSpan) * (size / 2.0f));
                int wpPy = centerY + (int) ((dz / halfWorldSpan) * (size / 2.0f));

                if (wpPx >= x + 4 && wpPx <= x + size - 4 && wpPy >= y + 4 && wpPy <= y + size - 4) {
                    int col = wp.getColor() != 0 ? wp.getColor() : 0xFFFFD700;
                    extractor.fill(wpPx - 2, wpPy - 2, wpPx + 3, wpPy + 3, col);
                    extractor.outline(wpPx - 3, wpPy - 3, wpPx + 4, wpPy + 4, 0xFF000000);
                }
            }
        }

        // 5. Render Synced Other Players
        if (config.isShowPlayerNames()) {
            String currentDim = player.level().dimension().identifier().toString();
            Font font = client.font;

            for (SyncedPlayer other : syncManager.getSyncedPlayers()) {
                if (!currentDim.equals(other.getDimension())) continue;

                double dx = other.getX() - player.getX();
                double dz = other.getZ() - player.getZ();

                int otherPx = centerX + (int) ((dx / halfWorldSpan) * (size / 2.0f));
                int otherPy = centerY + (int) ((dz / halfWorldSpan) * (size / 2.0f));

                // Check if within visible bounds or clamped to perimeter
                boolean inBounds = (otherPx >= x + 4 && otherPx <= x + size - 4 && otherPy >= y + 4 && otherPy <= y + size - 4);
                int clampedX = Math.max(x + 4, Math.min(x + size - 4, otherPx));
                int clampedY = Math.max(y + 4, Math.min(y + size - 4, otherPy));

                // Draw player marker (Cyan diamond for friends)
                int markerColor = inBounds ? 0xFF00E5FF : 0xFFFF9800; // Orange if outside bounds
                extractor.fill(clampedX - 2, clampedY - 2, clampedX + 3, clampedY + 3, markerColor);
                extractor.outline(clampedX - 3, clampedY - 3, clampedX + 4, clampedY + 4, 0xFF000000);

                if (inBounds) {
                    // Small nametag
                    String name = other.getName();
                    int textWidth = font.width(name);
                    extractor.fill(clampedX - textWidth / 2 - 1, clampedY - 11, clampedX + textWidth / 2 + 1, clampedY - 2, 0x88000000);
                    extractor.centeredText(font, name, clampedX, clampedY - 10, 0xFFFFFFFF);
                }
            }
        }

        // 6. Render Local Player (Center)
        drawLocalPlayerArrow(extractor, centerX, centerY, player.getYRot());

        // 7. Telemetry HUD Bar (Coordinates & Connection status)
        if (config.isShowCoordinates()) {
            Font font = client.font;
            String coords = String.format("%d, %d, %d", (int) player.getX(), (int) player.getY(), (int) player.getZ());
            int coordY = y + size + 3;

            extractor.fill(x - 2, coordY - 1, x + size + 2, coordY + 11, 0xAA000000);
            extractor.outline(x - 2, coordY - 1, x + size + 2, coordY + 11, 0xFF333333);
            extractor.centeredText(font, coords, centerX, coordY + 1, 0xFF55FFFF);

            // Sync badge
            int activePlayersCount = syncManager.getSyncedPlayers().size();
            String badge;
            int badgeColor;
            switch (config.getSyncMode()) {
                case THIRD_PARTY_SERVER -> {
                    boolean connected = syncManager.getWebSocketClient().isConnected();
                    badge = connected ? ("● " + (activePlayersCount + 1) + " Online") : "○ Connecting...";
                    badgeColor = connected ? 0xFF55FF55 : 0xFFFF5555;
                }
                case LAN_BROADCAST -> {
                    badge = "● LAN (" + (activePlayersCount + 1) + ")";
                    badgeColor = 0xFF55FF55;
                }
                case STEALTH_CHAT -> {
                    badge = "● Chat Sync (" + (activePlayersCount + 1) + ")";
                    badgeColor = 0xFFFFAA00;
                }
                default -> {
                    badge = "○ Solo";
                    badgeColor = 0xFFAAAAAA;
                }
            }
            extractor.centeredText(font, badge, centerX, coordY + 13, badgeColor);
        }
    }

    private void drawLocalPlayerArrow(GuiGraphicsExtractor extractor, int cx, int cy, float yaw) {
        // Center dot
        extractor.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFFFF);
        extractor.outline(cx - 3, cy - 3, cx + 4, cy + 4, 0xFF000000);

        // Direction indicator pointer
        double rad = Math.toRadians(yaw);
        int tipX = (int) (cx - Math.sin(rad) * 6);
        int tipY = (int) (cy + Math.cos(rad) * 6);

        extractor.fill(tipX - 1, tipY - 1, tipX + 2, tipY + 2, 0xFFFF0055);
    }
}
