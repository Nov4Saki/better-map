package com.bettermap.gui;

import com.bettermap.BetterMapClient;
import com.bettermap.config.BetterMapConfig;
import com.bettermap.model.Waypoint;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.List;

public class WaypointListScreen extends Screen {
    private final Screen parentScreen;
    private final BetterMapConfig config;
    private int page = 0;
    private static final int ITEMS_PER_PAGE = 5;

    public WaypointListScreen(Screen parentScreen, BetterMapConfig config) {
        super(Component.literal("Manage Waypoints"));
        this.parentScreen = parentScreen;
        this.config = config;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        int centerX = this.width / 2;
        int startY = 40;
        int rowH = 26;

        List<Waypoint> waypoints = config.getWaypoints();
        int totalPages = Math.max(1, (int) Math.ceil(waypoints.size() / (double) ITEMS_PER_PAGE));
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(waypoints.size(), startIndex + ITEMS_PER_PAGE);

        for (int i = startIndex; i < endIndex; i++) {
            final Waypoint wp = waypoints.get(i);
            int rowY = startY + (i - startIndex) * rowH;

            // Visibility toggle
            this.addRenderableWidget(Button.builder(Component.literal(wp.isEnabled() ? "👁" : "Ø"), btn -> {
                wp.setEnabled(!wp.isEnabled());
                BetterMapClient.saveConfig();
                this.init();
            }).bounds(centerX + 65, rowY, 24, 20).build());

            // Teleport button
            this.addRenderableWidget(Button.builder(Component.literal("TP"), btn -> {
                if (this.minecraft != null && this.minecraft.getConnection() != null) {
                    this.minecraft.getConnection().sendCommand(String.format("tp %.0f %.0f %.0f", wp.getX(), wp.getY(), wp.getZ()));
                    this.minecraft.setScreenAndShow(null);
                }
            }).bounds(centerX + 93, rowY, 28, 20).build());

            // Delete button
            this.addRenderableWidget(Button.builder(Component.literal("✖"), btn -> {
                config.getWaypoints().remove(wp);
                BetterMapClient.saveConfig();
                this.init();
            }).bounds(centerX + 125, rowY, 22, 20).build());
        }

        // Pagination controls
        int navY = startY + ITEMS_PER_PAGE * rowH + 6;
        if (totalPages > 1) {
            this.addRenderableWidget(Button.builder(Component.literal("◀ Prev"), btn -> {
                if (page > 0) { page--; this.init(); }
            }).bounds(centerX - 100, navY, 60, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("Next ▶"), btn -> {
                if (page < totalPages - 1) { page++; this.init(); }
            }).bounds(centerX + 40, navY, 60, 20).build());
        }

        // Add New Waypoint & Back buttons
        int bottomY = this.height - 35;
        this.addRenderableWidget(Button.builder(Component.literal("➕ New Waypoint"), btn -> {
            LocalPlayer p = Minecraft.getInstance().player;
            double px = p != null ? p.getX() : 0;
            double py = p != null ? p.getY() : 64;
            double pz = p != null ? p.getZ() : 0;
            String dim = p != null ? p.level().dimension().identifier().toString() : "minecraft:overworld";
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(new NewWaypointScreen(this, config, dim, px, py, pz));
            }
        }).bounds(centerX - 120, bottomY, 115, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Back"), btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(parentScreen);
            }
        }).bounds(centerX + 5, bottomY, 115, 20).build());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(parentScreen);
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        // Dark backdrop
        extractor.fill(0, 0, this.width, this.height, 0xEE111116);

        int centerX = this.width / 2;
        int startY = 40;
        int rowH = 26;

        extractor.centeredText(this.font, "📍 Waypoints Manager", centerX, 16, 0xFFFFAA00);

        List<Waypoint> waypoints = config.getWaypoints();
        if (waypoints.isEmpty()) {
            extractor.centeredText(this.font, "No waypoints created yet.", centerX, startY + 40, 0xFF888888);
            extractor.centeredText(this.font, "Press [B] in game or click '+ New Waypoint' below.", centerX, startY + 55, 0xFF666666);
        } else {
            int startIndex = page * ITEMS_PER_PAGE;
            int endIndex = Math.min(waypoints.size(), startIndex + ITEMS_PER_PAGE);

            for (int i = startIndex; i < endIndex; i++) {
                Waypoint wp = waypoints.get(i);
                int rowY = startY + (i - startIndex) * rowH;

                // Row background card
                extractor.fill(centerX - 150, rowY - 2, centerX + 150, rowY + 22, 0x551E1E2A);
                extractor.outline(centerX - 150, rowY - 2, centerX + 150, rowY + 22, 0xFF2A2A3E);

                // Waypoint color swatch
                extractor.fill(centerX - 144, rowY + 4, centerX - 134, rowY + 14, wp.getColor() != 0 ? wp.getColor() : 0xFFFFD700);
                extractor.outline(centerX - 144, rowY + 4, centerX - 134, rowY + 14, 0xFF000000);

                // Waypoint name & coords
                String name = wp.getName();
                String coords = String.format("%d, %d, %d", (int) wp.getX(), (int) wp.getY(), (int) wp.getZ());
                extractor.text(this.font, name, centerX - 128, rowY + 1, wp.isEnabled() ? 0xFFFFFFFF : 0xFF777777);
                extractor.text(this.font, coords, centerX - 128, rowY + 11, 0xFF888888);
            }
        }

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }
}
