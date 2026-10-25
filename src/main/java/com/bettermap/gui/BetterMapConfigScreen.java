package com.bettermap.gui;

import com.bettermap.BetterMapClient;
import com.bettermap.config.BetterMapConfig;
import com.bettermap.network.PlayerSyncManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public class BetterMapConfigScreen extends Screen {
    private final Screen parentScreen;
    private final BetterMapConfig config;
    private final PlayerSyncManager syncManager;

    public BetterMapConfigScreen(Screen parentScreen, BetterMapConfig config, PlayerSyncManager syncManager) {
        super(Component.literal("BetterMap Settings"));
        this.parentScreen = parentScreen;
        this.config = config;
        this.syncManager = syncManager;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        int centerX = this.width / 2;
        int startY = 45;
        int gap = 24;

        // 1. Sync Mode Toggle
        this.addRenderableWidget(Button.builder(
                Component.literal("Sync Mode: " + config.getSyncMode().name()),
                btn -> {
                    BetterMapConfig.SyncMode[] modes = BetterMapConfig.SyncMode.values();
                    int nextIdx = (config.getSyncMode().ordinal() + 1) % modes.length;
                    config.setSyncMode(modes[nextIdx]);
                    btn.setMessage(Component.literal("Sync Mode: " + config.getSyncMode().name()));
                }
        ).bounds(centerX - 120, startY, 240, 20).build());

        // 2. Minimap Enabled
        this.addRenderableWidget(Button.builder(
                Component.literal("Minimap: " + (config.isMinimapEnabled() ? "ON" : "OFF")),
                btn -> {
                    config.setMinimapEnabled(!config.isMinimapEnabled());
                    btn.setMessage(Component.literal("Minimap: " + (config.isMinimapEnabled() ? "ON" : "OFF")));
                }
        ).bounds(centerX - 120, startY + gap, 240, 20).build());

        // 3. Minimap Corner
        this.addRenderableWidget(Button.builder(
                Component.literal("Corner: " + config.getMinimapCorner().name()),
                btn -> {
                    BetterMapConfig.MinimapCorner[] corners = BetterMapConfig.MinimapCorner.values();
                    int nextIdx = (config.getMinimapCorner().ordinal() + 1) % corners.length;
                    config.setMinimapCorner(corners[nextIdx]);
                    btn.setMessage(Component.literal("Corner: " + config.getMinimapCorner().name()));
                }
        ).bounds(centerX - 120, startY + gap * 2, 240, 20).build());

        // 4. Show Coordinates
        this.addRenderableWidget(Button.builder(
                Component.literal("Coordinates: " + (config.isShowCoordinates() ? "SHOW" : "HIDE")),
                btn -> {
                    config.setShowCoordinates(!config.isShowCoordinates());
                    btn.setMessage(Component.literal("Coordinates: " + (config.isShowCoordinates() ? "SHOW" : "HIDE")));
                }
        ).bounds(centerX - 120, startY + gap * 3, 240, 20).build());

        // 5. Show Player Names
        this.addRenderableWidget(Button.builder(
                Component.literal("Player Markers: " + (config.isShowPlayerNames() ? "SHOW" : "HIDE")),
                btn -> {
                    config.setShowPlayerNames(!config.isShowPlayerNames());
                    btn.setMessage(Component.literal("Player Markers: " + (config.isShowPlayerNames() ? "SHOW" : "HIDE")));
                }
        ).bounds(centerX - 120, startY + gap * 4, 240, 20).build());

        // 6. Show Waypoints
        this.addRenderableWidget(Button.builder(
                Component.literal("Waypoints: " + (config.isShowWaypoints() ? "SHOW" : "HIDE")),
                btn -> {
                    config.setShowWaypoints(!config.isShowWaypoints());
                    btn.setMessage(Component.literal("Waypoints: " + (config.isShowWaypoints() ? "SHOW" : "HIDE")));
                }
        ).bounds(centerX - 120, startY + gap * 5, 240, 20).build());

        // 7. Show Compass
        this.addRenderableWidget(Button.builder(
                Component.literal("Compass: " + (config.isShowCompass() ? "SHOW" : "HIDE")),
                btn -> {
                    config.setShowCompass(!config.isShowCompass());
                    btn.setMessage(Component.literal("Compass: " + (config.isShowCompass() ? "SHOW" : "HIDE")));
                }
        ).bounds(centerX - 120, startY + gap * 6, 240, 20).build());

        // Save & Return Button
        this.addRenderableWidget(Button.builder(
                Component.literal("Done"),
                btn -> {
                    BetterMapClient.saveConfig();
                    if (this.minecraft != null) {
                        this.minecraft.setScreenAndShow(parentScreen);
                    }
                }
        ).bounds(centerX - 60, startY + gap * 8, 120, 20).build());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            BetterMapClient.saveConfig();
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(parentScreen);
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        // Dark translucent background
        extractor.fill(0, 0, this.width, this.height, 0xEE14141A);

        int centerX = this.width / 2;
        extractor.centeredText(this.font, "⚙ BetterMap Configuration", centerX, 18, 0xFFFFFFFF);

        String info = "Server: " + config.getServerUrl() + "  |  Room: " + config.getRoomKey();
        extractor.centeredText(this.font, info, centerX, 30, 0xFF888888);

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }
}
