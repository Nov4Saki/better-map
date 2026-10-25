package com.bettermap.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * Samples surface colors using a hybrid pipeline:
 * 1. Live chunk blocks & hillshading for loaded areas
 * 2. High-resolution surveyed map for explored / distant areas
 */
public class TerrainSampler {

    public static int sampleColorABGR(ClientLevel level, int x, int z) {
        if (level != null) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 64, z);
            if (level.isLoaded(pos)) {
                int minY = level.getMinY();
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                if (y > minY) {
                    pos.set(x, y - 1, z);
                    BlockState state = level.getBlockState(pos);

                    // Step through empty air blocks if necessary
                    int step = 0;
                    while (state.isAir() && pos.getY() > minY && step < 10) {
                        pos.setY(pos.getY() - 1);
                        state = level.getBlockState(pos);
                        step++;
                    }

                    MapColor mapColor = state.getMapColor(level, pos);
                    if (mapColor != MapColor.NONE) {
                        int northY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - 1);
                        MapColor.Brightness brightness;
                        if (y > northY) {
                            brightness = MapColor.Brightness.HIGH;
                        } else if (y < northY) {
                            brightness = MapColor.Brightness.LOW;
                        } else {
                            brightness = MapColor.Brightness.NORMAL;
                        }

                        int argb = mapColor.calculateARGBColor(brightness);
                        int a = (argb >> 24) & 0xFF;
                        int r = (argb >> 16) & 0xFF;
                        int g = (argb >> 8) & 0xFF;
                        int b = argb & 0xFF;

                        return (a << 24) | (b << 16) | (g << 8) | r;
                    }
                }
            }
        }

        // Fallback to high-resolution pre-rendered survey map
        int surveyColor = SurveyMapProvider.getInstance().sampleColorABGR(x, z);
        if (surveyColor != 0) {
            return surveyColor;
        }

        return 0xFF141414; // Default void
    }
}
