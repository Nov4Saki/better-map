package com.bettermap.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

public class MapTextureManager {
    public static final int TEXTURE_SIZE = 160;
    public static final Identifier MAP_TEXTURE_ID = Identifier.fromNamespaceAndPath("better_map", "dynamic_map");

    private DynamicTexture dynamicTexture;
    private boolean initialized = false;

    private int lastCenterX = Integer.MIN_VALUE;
    private int lastCenterZ = Integer.MIN_VALUE;
    private float lastZoom = -1f;
    private long lastUpdateTime = 0;

    public void initialize() {
        if (initialized) return;
        dynamicTexture = new DynamicTexture("better_map_dynamic", TEXTURE_SIZE, TEXTURE_SIZE, false);
        Minecraft.getInstance().getTextureManager().register(MAP_TEXTURE_ID, dynamicTexture);
        initialized = true;
    }

    public Identifier getTextureId() {
        if (!initialized) initialize();
        return MAP_TEXTURE_ID;
    }

    public void updateMap(ClientLevel level, double centerWorldX, double centerWorldZ, float zoom, boolean force) {
        if (!initialized) initialize();
        if (level == null) return;

        int intCenterX = (int) Math.floor(centerWorldX);
        int intCenterZ = (int) Math.floor(centerWorldZ);

        long now = System.currentTimeMillis();
        // Update at most 5 times per second unless forced, and only if moved or zoomed
        if (!force && (now - lastUpdateTime < 200)) {
            return;
        }

        if (!force && Math.abs(intCenterX - lastCenterX) < 2 && Math.abs(intCenterZ - lastCenterZ) < 2 && Math.abs(zoom - lastZoom) < 0.05f) {
            return;
        }

        lastCenterX = intCenterX;
        lastCenterZ = intCenterZ;
        lastZoom = zoom;
        lastUpdateTime = now;

        NativeImage image = dynamicTexture.getPixels();
        if (image == null) return;

        int halfSize = TEXTURE_SIZE / 2;
        float step = 1.0f / Math.max(0.2f, zoom);

        BlockPos.MutableBlockPos testPos = new BlockPos.MutableBlockPos();

        for (int py = 0; py < TEXTURE_SIZE; py++) {
            float relZ = (py - halfSize) * step;
            int worldZ = (int) (intCenterZ + relZ);

            for (int px = 0; px < TEXTURE_SIZE; px++) {
                float relX = (px - halfSize) * step;
                int worldX = (int) (intCenterX + relX);

                int abgr = TerrainSampler.sampleColorABGR(level, worldX, worldZ);
                image.setPixel(px, py, abgr);
            }
        }

        dynamicTexture.upload();
    }

    public void close() {
        if (dynamicTexture != null) {
            dynamicTexture.close();
            dynamicTexture = null;
        }
        initialized = false;
    }
}
