package com.bettermap.gui;

import com.bettermap.config.BetterMapConfig;
import com.bettermap.map.MapTextureManager;
import com.bettermap.model.SyncedPlayer;
import com.bettermap.model.Waypoint;
import com.bettermap.network.PlayerSyncManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class WorldMapScreen extends Screen {
    private final BetterMapConfig config;
    private final MapTextureManager textureManager;
    private final PlayerSyncManager syncManager;

    private double cameraX;
    private double cameraZ;
    private float zoom = 1.0f;

    private boolean showPlayerList = true;

    public WorldMapScreen(BetterMapConfig config, MapTextureManager textureManager, PlayerSyncManager syncManager) {
        super(Component.literal("BetterMap World Navigator"));
        this.config = config;
        this.textureManager = textureManager;
        this.syncManager = syncManager;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            this.cameraX = player.getX();
            this.cameraZ = player.getZ();
        }
        this.zoom = config.getWorldMapDefaultZoom();
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        // Top control buttons
        int btnY = 6;
        int rightX = this.width - 90;

        // Settings button
        this.addRenderableWidget(Button.builder(Component.literal("⚙ Settings"), btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(new BetterMapConfigScreen(this, config, syncManager));
            }
        }).bounds(rightX, btnY, 80, 20).build());

        // Center on local player
        this.addRenderableWidget(Button.builder(Component.literal("🎯 Recenter"), btn -> {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p != null) {
                this.cameraX = p.getX();
                this.cameraZ = p.getZ();
            }
        }).bounds(rightX - 85, btnY, 80, 20).build());

        // Toggle player list sidebar
        this.addRenderableWidget(Button.builder(Component.literal("👥 Players"), btn -> {
            this.showPlayerList = !this.showPlayerList;
        }).bounds(rightX - 170, btnY, 80, 20).build());

        // Manage waypoints button
        this.addRenderableWidget(Button.builder(Component.literal("📍 Waypoints"), btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(new WaypointListScreen(this, config));
            }
        }).bounds(rightX - 265, btnY, 90, 20).build());

        // Quick add waypoint button
        this.addRenderableWidget(Button.builder(Component.literal("➕ New"), btn -> {
            LocalPlayer p = Minecraft.getInstance().player;
            double px = p != null ? p.getX() : cameraX;
            double py = p != null ? p.getY() : 64;
            double pz = p != null ? p.getZ() : cameraZ;
            String dim = p != null ? p.level().dimension().identifier().toString() : "minecraft:overworld";
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(new NewWaypointScreen(this, config, dim, px, py, pz));
            }
        }).bounds(rightX - 325, btnY, 55, 20).build());

        // Zoom In (+)
        this.addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
            this.zoom = Math.min(4.0f, this.zoom * 1.25f);
        }).bounds(this.width - 30, this.height - 55, 20, 20).build());

        // Zoom Out (-)
        this.addRenderableWidget(Button.builder(Component.literal("-"), btn -> {
            this.zoom = Math.max(0.2f, this.zoom / 1.25f);
        }).bounds(this.width - 30, this.height - 30, 20, 20).build());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        // Pan the camera based on drag delta and current zoom
        float worldUnitsPerPixel = (MapTextureManager.TEXTURE_SIZE / (float) Math.min(this.width, this.height)) / zoom;
        this.cameraX -= deltaX * worldUnitsPerPixel;
        this.cameraZ -= deltaY * worldUnitsPerPixel;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount > 0) {
            this.zoom = Math.min(4.0f, this.zoom * 1.15f);
        } else if (verticalAmount < 0) {
            this.zoom = Math.max(0.2f, this.zoom / 1.15f);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        // Right-click or double-click opens the New Waypoint creation modal
        if (event.button() == 1 || (doubleClick && event.button() == 0)) {
            double clickX = event.x();
            double clickY = event.y();

            // Ignore clicks inside top bar or side panel
            if (clickY > 35 && clickY < this.height - 24 && (!showPlayerList || clickX > 150)) {
                int cx = this.width / 2;
                int cy = this.height / 2;
                float worldSpan = (MapTextureManager.TEXTURE_SIZE / 2.0f) / Math.max(0.2f, zoom);

                double worldX = cameraX + ((clickX - cx) / (float) (Math.min(this.width, this.height) / 2)) * worldSpan;
                double worldZ = cameraZ + ((clickY - cy) / (float) (Math.min(this.width, this.height) / 2)) * worldSpan;

                LocalPlayer player = Minecraft.getInstance().player;
                String dim = player != null ? player.level().dimension().identifier().toString() : "minecraft:overworld";
                double py = player != null ? player.getY() : 64;

                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new NewWaypointScreen(this, config, dim, worldX, py, worldZ));
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE || event.key() == InputConstants.KEY_M) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false; // Live game continues running in background
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft == null || this.minecraft.level == null) return;

        // 1. Dark background canvas
        extractor.fill(0, 0, this.width, this.height, 0xFF121216);

        // 2. Update terrain texture centered on current camera
        textureManager.updateMap(this.minecraft.level, cameraX, cameraZ, zoom, false);

        int mapRenderSize = Math.min(this.width, this.height);
        int mapX = (this.width - mapRenderSize) / 2;
        int mapY = (this.height - mapRenderSize) / 2;

        // 3. Render terrain texture
        extractor.blit(textureManager.getTextureId(), mapX, mapY, mapRenderSize, mapRenderSize, 0f, 0f, 1f, 1f);

        // 4. Subtle center crosshair for camera focus
        int cx = this.width / 2;
        int cy = this.height / 2;
        extractor.fill(cx - 4, cy, cx + 5, cy + 1, 0x55FFFFFF);
        extractor.fill(cx, cy - 4, cx + 1, cy + 5, 0x55FFFFFF);

        float halfWorldSpan = (MapTextureManager.TEXTURE_SIZE / 2.0f) / Math.max(0.2f, zoom);
        LocalPlayer localPlayer = this.minecraft.player;
        String currentDim = localPlayer != null ? localPlayer.level().dimension().identifier().toString() : "minecraft:overworld";

        // 5. Render Waypoints
        if (config.isShowWaypoints()) {
            for (Waypoint wp : config.getWaypoints()) {
                if (!wp.isEnabled() || !currentDim.equals(wp.getDimension())) continue;

                double dx = wp.getX() - cameraX;
                double dz = wp.getZ() - cameraZ;

                int screenPx = cx + (int) ((dx / halfWorldSpan) * (mapRenderSize / 2.0f));
                int screenPy = cy + (int) ((dz / halfWorldSpan) * (mapRenderSize / 2.0f));

                if (screenPx >= 0 && screenPx <= this.width && screenPy >= 32 && screenPy <= this.height - 24) {
                    int col = wp.getColor() != 0 ? wp.getColor() : 0xFFFFD700;
                    extractor.fill(screenPx - 3, screenPy - 3, screenPx + 4, screenPy + 4, col);
                    extractor.outline(screenPx - 4, screenPy - 4, screenPx + 5, screenPy + 5, 0xFF000000);
                    extractor.centeredText(this.font, wp.getName(), screenPx, screenPy - 12, 0xFFFFFFFF);
                }
            }
        }

        // 6. Render Synced Other Players
        for (SyncedPlayer other : syncManager.getSyncedPlayers()) {
            if (!currentDim.equals(other.getDimension())) continue;

            double dx = other.getX() - cameraX;
            double dz = other.getZ() - cameraZ;

            int screenPx = cx + (int) ((dx / halfWorldSpan) * (mapRenderSize / 2.0f));
            int screenPy = cy + (int) ((dz / halfWorldSpan) * (mapRenderSize / 2.0f));

            if (screenPx >= 0 && screenPx <= this.width && screenPy >= 32 && screenPy <= this.height - 24) {
                // Diamond player marker
                extractor.fill(screenPx - 3, screenPy - 3, screenPx + 4, screenPy + 4, 0xFF00E5FF);
                extractor.outline(screenPx - 4, screenPy - 4, screenPx + 5, screenPy + 5, 0xFF000000);

                // Elevation indicator: ▲ if above local player, ▼ if below
                String elevation = "";
                if (localPlayer != null) {
                    double dy = other.getY() - localPlayer.getY();
                    if (dy > 3) elevation = " ▲";
                    else if (dy < -3) elevation = " ▼";
                }

                // Name and elevation tag
                String label = other.getName() + elevation;
                int textW = this.font.width(label);
                extractor.fill(screenPx - textW / 2 - 2, screenPy - 13, screenPx + textW / 2 + 2, screenPy - 3, 0xAA000000);
                extractor.centeredText(this.font, label, screenPx, screenPy - 12, 0xFF00FFFF);

                // Health bar
                float healthRatio = Math.max(0f, Math.min(1f, other.getHealth() / 20.0f));
                int barW = 16;
                int fillW = (int) (barW * healthRatio);
                extractor.fill(screenPx - barW / 2, screenPy + 5, screenPx + barW / 2, screenPy + 7, 0xFF550000);
                extractor.fill(screenPx - barW / 2, screenPy + 5, screenPx - barW / 2 + fillW, screenPy + 7, 0xFF00FF55);
            }
        }

        // 7. Render Local Player
        if (localPlayer != null) {
            double dx = localPlayer.getX() - cameraX;
            double dz = localPlayer.getZ() - cameraZ;

            int localPx = cx + (int) ((dx / halfWorldSpan) * (mapRenderSize / 2.0f));
            int localPy = cy + (int) ((dz / halfWorldSpan) * (mapRenderSize / 2.0f));

            if (localPx >= 0 && localPx <= this.width && localPy >= 32 && localPy <= this.height - 24) {
                // Local player marker: white dot with direction indicator
                extractor.fill(localPx - 3, localPy - 3, localPx + 4, localPy + 4, 0xFFFFFFFF);
                extractor.outline(localPx - 4, localPy - 4, localPx + 5, localPy + 5, 0xFF000000);

                double rad = Math.toRadians(localPlayer.getYRot());
                int tipX = (int) (localPx - Math.sin(rad) * 9);
                int tipY = (int) (localPy + Math.cos(rad) * 9);
                extractor.fill(tipX - 1, tipY - 1, tipX + 2, tipY + 2, 0xFFFF0055);

                String name = localPlayer.getName().getString() + " (You)";
                int textW = this.font.width(name);
                extractor.fill(localPx - textW / 2 - 2, localPy - 13, localPx + textW / 2 + 2, localPy - 3, 0xAA000000);
                extractor.centeredText(this.font, name, localPx, localPy - 12, 0xFFFFFFFF);
            }
        }

        // 8. Top Navigation & Telemetry Header Bar
        extractor.fill(0, 0, this.width, 32, 0xEE1A1A24);
        extractor.fill(0, 31, this.width, 32, 0xFF3A3A4E);

        extractor.text(this.font, "🧭 BetterMap World Navigator", 12, 11, 0xFFFFFFFF);

        // Cursor World Coordinates
        float worldSpan = (MapTextureManager.TEXTURE_SIZE / 2.0f) / Math.max(0.2f, zoom);
        double cursorWorldX = cameraX + ((mouseX - cx) / (float) (mapRenderSize / 2.0f)) * worldSpan;
        double cursorWorldZ = cameraZ + ((mouseY - cy) / (float) (mapRenderSize / 2.0f)) * worldSpan;

        String cursorInfo = String.format("Cursor: X: %d, Z: %d   |   Zoom: %d%%", (int) cursorWorldX, (int) cursorWorldZ, (int) (zoom * 100));
        extractor.centeredText(this.font, cursorInfo, cx, 11, 0xFF55FFFF);

        // 9. Left Synced Player List Sidebar (if toggled)
        if (showPlayerList) {
            int panelW = 140;
            int panelH = this.height - 32;
            extractor.fill(0, 32, panelW, this.height, 0xCC111116);
            extractor.fill(panelW - 1, 32, panelW, this.height, 0xFF2A2A38);

            extractor.text(this.font, "SYNCED PLAYERS", 10, 42, 0xFFFFAA00);

            int listY = 58;
            if (localPlayer != null) {
                extractor.text(this.font, "● " + localPlayer.getName().getString() + " (You)", 10, listY, 0xFFFFFFFF);
                extractor.text(this.font, String.format("  %d, %d, %d", (int) localPlayer.getX(), (int) localPlayer.getY(), (int) localPlayer.getZ()), 10, listY + 10, 0xFF888888);
                listY += 24;
            }

            for (SyncedPlayer other : syncManager.getSyncedPlayers()) {
                if (listY > this.height - 30) break;
                double dist = localPlayer != null ? other.distanceTo(localPlayer.getX(), localPlayer.getZ()) : 0;
                extractor.text(this.font, "● " + other.getName(), 10, listY, 0xFF00E5FF);
                extractor.text(this.font, String.format("  %d, %d, %d (%.0fm)", (int) other.getX(), (int) other.getY(), (int) other.getZ(), dist), 10, listY + 10, 0xFF888888);
                listY += 24;
            }

            if (syncManager.getSyncedPlayers().isEmpty()) {
                extractor.text(this.font, "No remote players", 10, listY, 0xFF666666);
                extractor.text(this.font, "in current room.", 10, listY + 10, 0xFF666666);
            }
        }

        // 10. Bottom Status & Quick Instructions Bar
        extractor.fill(0, this.height - 22, this.width, this.height, 0xEE1A1A24);
        extractor.fill(0, this.height - 22, this.width, this.height - 21, 0xFF3A3A4E);

        String instructions = "[Drag] Pan Map   [Scroll / + / -] Zoom   [Double-Click] Add Waypoint   [M / ESC] Close";
        extractor.text(this.font, instructions, 12, this.height - 15, 0xFFAAAAAA);

        // Render UI buttons on top
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }
}
