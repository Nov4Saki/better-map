package com.bettermap.gui;

import com.bettermap.BetterMapClient;
import com.bettermap.config.BetterMapConfig;
import com.bettermap.model.Waypoint;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class NewWaypointScreen extends Screen {
    private final Screen parentScreen;
    private final BetterMapConfig config;
    private final String dimension;
    private final double initialX;
    private final double initialY;
    private final double initialZ;

    private EditBox nameField;
    private EditBox xField;
    private EditBox yField;
    private EditBox zField;

    private int selectedColor = 0xFF00E5FF; // Default Cyan

    private static final int[] PALETTE = new int[] {
            0xFFFF3333, // Red
            0xFF00FF66, // Green
            0xFF3399FF, // Blue
            0xFFFFD700, // Gold
            0xFFAA00FF, // Purple
            0xFF00E5FF, // Cyan
            0xFFFF8800, // Orange
            0xFFFFFFFF  // White
    };

    public NewWaypointScreen(Screen parentScreen, BetterMapConfig config, String dimension, double x, double y, double z) {
        super(Component.literal("Create Waypoint"));
        this.parentScreen = parentScreen;
        this.config = config;
        this.dimension = dimension != null ? dimension : "minecraft:overworld";
        this.initialX = x;
        this.initialY = y;
        this.initialZ = z;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        int centerX = this.width / 2;
        int startY = 40;

        // Name input
        this.nameField = new EditBox(this.font, centerX - 100, startY + 15, 200, 20, Component.literal("Waypoint Name"));
        this.nameField.setValue("Waypoint " + (config.getWaypoints().size() + 1));
        this.addRenderableWidget(this.nameField);
        this.setInitialFocus(this.nameField);

        // Coordinate inputs
        int coordY = startY + 55;
        this.xField = new EditBox(this.font, centerX - 100, coordY, 60, 20, Component.literal("X"));
        this.xField.setValue(String.valueOf((int) initialX));
        this.addRenderableWidget(this.xField);

        this.yField = new EditBox(this.font, centerX - 30, coordY, 60, 20, Component.literal("Y"));
        this.yField.setValue(String.valueOf((int) initialY));
        this.addRenderableWidget(this.yField);

        this.zField = new EditBox(this.font, centerX + 40, coordY, 60, 20, Component.literal("Z"));
        this.zField.setValue(String.valueOf((int) initialZ));
        this.addRenderableWidget(this.zField);

        // Color selector buttons
        int colorY = startY + 100;
        int colorW = 22;
        int totalColorsW = PALETTE.length * (colorW + 3);
        int colorStartX = centerX - totalColorsW / 2;

        for (int i = 0; i < PALETTE.length; i++) {
            final int col = PALETTE[i];
            int bx = colorStartX + i * (colorW + 3);
            this.addRenderableWidget(Button.builder(Component.literal("■"), btn -> {
                this.selectedColor = col;
            }).bounds(bx, colorY, colorW, 20).build());
        }

        // Save & Cancel buttons
        int btnY = startY + 145;
        this.addRenderableWidget(Button.builder(Component.literal("Save Waypoint"), btn -> saveAndClose())
                .bounds(centerX - 105, btnY, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> closeScreen())
                .bounds(centerX + 5, btnY, 100, 20).build());
    }

    private void saveAndClose() {
        String name = nameField.getValue().trim();
        if (name.isEmpty()) name = "Waypoint " + (config.getWaypoints().size() + 1);

        double x = initialX;
        double y = initialY;
        double z = initialZ;

        try { x = Double.parseDouble(xField.getValue().trim()); } catch (Exception ignored) {}
        try { y = Double.parseDouble(yField.getValue().trim()); } catch (Exception ignored) {}
        try { z = Double.parseDouble(zField.getValue().trim()); } catch (Exception ignored) {}

        Waypoint wp = new Waypoint(UUID.randomUUID().toString(), name, x, y, z, dimension, selectedColor);
        config.getWaypoints().add(wp);
        BetterMapClient.saveConfig();

        closeScreen();
    }

    private void closeScreen() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(parentScreen);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == 257) { // Enter key
            saveAndClose();
            return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE) {
            closeScreen();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        // Dark modal backdrop
        extractor.fill(0, 0, this.width, this.height, 0xDD111116);

        int centerX = this.width / 2;
        int startY = 40;

        // Card frame
        extractor.fill(centerX - 120, startY - 15, centerX + 120, startY + 185, 0xEE1A1A24);
        extractor.outline(centerX - 120, startY - 15, centerX + 120, startY + 185, 0xFF3A3A4E);

        extractor.centeredText(this.font, "📍 Create New Waypoint", centerX, startY - 5, 0xFFFFAA00);

        // Field labels
        extractor.text(this.font, "Waypoint Name:", centerX - 100, startY + 4, 0xFFAAAAAA);
        extractor.text(this.font, "X", centerX - 100, startY + 44, 0xFFAAAAAA);
        extractor.text(this.font, "Y", centerX - 30, startY + 44, 0xFFAAAAAA);
        extractor.text(this.font, "Z", centerX + 40, startY + 44, 0xFFAAAAAA);

        extractor.text(this.font, "Color Theme:", centerX - 100, startY + 88, 0xFFAAAAAA);

        // Preview selected color swatch
        extractor.fill(centerX + 80, startY + 86, centerX + 98, startY + 98, selectedColor);
        extractor.outline(centerX + 80, startY + 86, centerX + 98, startY + 98, 0xFFFFFFFF);

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }
}
