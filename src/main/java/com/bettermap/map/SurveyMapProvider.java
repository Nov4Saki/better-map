package com.bettermap.map;

import com.bettermap.config.BetterMapConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public class SurveyMapProvider {
    private static final SurveyMapProvider INSTANCE = new SurveyMapProvider();

    private final AtomicBoolean loaded = new AtomicBoolean(false);
    private final AtomicBoolean loading = new AtomicBoolean(false);

    // Canvas & Transform
    private double canvasWidth = 28748.0;
    private double canvasHeight = 28818.0;
    private double scaleX = 0.28;
    private double scaleZ = 0.28;
    private double offsetX = -3200.0;
    private double offsetZ = -3200.0;

    // Loaded Image Buffer
    private int imgWidth = 0;
    private int imgHeight = 0;
    private int[] abgrPixels = null;
    private String loadedSource = "None";

    private SurveyMapProvider() {}

    public static SurveyMapProvider getInstance() {
        return INSTANCE;
    }

    public boolean isLoaded() {
        return loaded.get();
    }

    public String getLoadedSource() {
        return loadedSource;
    }

    public void initializeAsync(BetterMapConfig config) {
        if (loaded.get() || loading.get()) return;
        loading.set(true);

        Thread loadThread = new Thread(() -> {
            try {
                load(config);
            } finally {
                loading.set(false);
            }
        }, "BetterMap-Survey-Loader");
        loadThread.setDaemon(true);
        loadThread.start();
    }

    private void load(BetterMapConfig config) {
        // Potential search locations
        Path[] candidateDirs = new Path[] {
                FabricLoader.getInstance().getGameDir().resolve("better_map"),
                FabricLoader.getInstance().getConfigDir().resolve("better_map"),
                Path.of("."),
                Path.of("c:/projects/Web/better map")
        };

        File metaFile = null;
        File imageFile = null;

        // 1. Find metadata
        for (Path dir : candidateDirs) {
            File testMeta = dir.resolve("map_metadata.json").toFile();
            if (testMeta.exists() && testMeta.isFile()) {
                metaFile = testMeta;
                break;
            }
        }

        if (metaFile != null) {
            try (FileReader reader = new FileReader(metaFile)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root.has("width")) this.canvasWidth = root.get("width").getAsDouble();
                if (root.has("height")) this.canvasHeight = root.get("height").getAsDouble();
                if (root.has("mc_transform")) {
                    JsonObject tf = root.getAsJsonObject("mc_transform");
                    if (tf.has("scale_x")) this.scaleX = tf.get("scale_x").getAsDouble();
                    if (tf.has("scale_z")) this.scaleZ = tf.get("scale_z").getAsDouble();
                    if (tf.has("offset_x")) this.offsetX = tf.get("offset_x").getAsDouble();
                    if (tf.has("offset_z")) this.offsetZ = tf.get("offset_z").getAsDouble();
                }
            } catch (Exception ignored) {}
        }

        // 2. Find map image (prefer 2k for fast memory/load, or 8k if configured)
        for (Path dir : candidateDirs) {
            File f8k = dir.resolve("stitched_map_8k.jpg").toFile();
            File f2k = dir.resolve("stitched_map_2k.jpg").toFile();
            File fFull = dir.resolve("stitched_map.jpg").toFile();

            // Default to 2K for smooth 60fps performance and instant loading
            if (f2k.exists()) {
                imageFile = f2k;
                break;
            } else if (f8k.exists()) {
                imageFile = f8k;
                break;
            } else if (fFull.exists()) {
                imageFile = fFull;
                break;
            }
        }

        if (imageFile == null || !imageFile.exists()) {
            return;
        }

        try {
            BufferedImage img = ImageIO.read(imageFile);
            if (img == null) return;

            this.imgWidth = img.getWidth();
            this.imgHeight = img.getHeight();

            int totalPixels = imgWidth * imgHeight;
            int[] rgbData = new int[totalPixels];
            img.getRGB(0, 0, imgWidth, imgHeight, rgbData, 0, imgWidth);

            // Pre-convert to ABGR format for instant NativeImage blitting
            this.abgrPixels = new int[totalPixels];
            for (int i = 0; i < totalPixels; i++) {
                int rgb = rgbData[i];
                int a = 0xFF;
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                this.abgrPixels[i] = (a << 24) | (b << 16) | (g << 8) | r;
            }

            this.loadedSource = imageFile.getName() + " (" + imgWidth + "x" + imgHeight + ")";
            this.loaded.set(true);
            System.out.println("[BetterMap] Successfully loaded survey map from: " + loadedSource);
        } catch (Exception e) {
            System.err.println("[BetterMap] Failed to load survey map image: " + e.getMessage());
        }
    }

    /**
     * Samples the survey map color at Minecraft world coordinate (worldX, worldZ).
     * Returns 0 if outside surveyed boundaries or not loaded.
     */
    public int sampleColorABGR(int worldX, int worldZ) {
        if (!loaded.get() || abgrPixels == null) return 0;

        double canvasX = (worldX - offsetX) / scaleX;
        double canvasY = (worldZ - offsetZ) / scaleZ;

        if (canvasX < 0 || canvasX >= canvasWidth || canvasY < 0 || canvasY >= canvasHeight) {
            return 0; // Outside surveyed map
        }

        int px = (int) (canvasX * (imgWidth / canvasWidth));
        int py = (int) (canvasY * (imgHeight / canvasHeight));

        if (px >= 0 && px < imgWidth && py >= 0 && py < imgHeight) {
            return abgrPixels[py * imgWidth + px];
        }

        return 0;
    }

    public double getScaleX() { return scaleX; }
    public double getScaleZ() { return scaleZ; }
    public double getOffsetX() { return offsetX; }
    public double getOffsetZ() { return offsetZ; }
    public double getCanvasWidth() { return canvasWidth; }
    public double getCanvasHeight() { return canvasHeight; }
}
