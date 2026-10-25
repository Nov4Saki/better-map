package com.bettermap.config;

import com.bettermap.model.Waypoint;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class BetterMapConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public enum SyncMode {
        THIRD_PARTY_SERVER,
        LAN_BROADCAST,
        STEALTH_CHAT,
        DISABLED
    }

    public enum MinimapCorner {
        TOP_RIGHT,
        TOP_LEFT,
        BOTTOM_RIGHT,
        BOTTOM_LEFT
    }

    public enum MinimapShape {
        RECTANGLE,
        CIRCLE
    }

    // Networking
    private SyncMode syncMode = SyncMode.THIRD_PARTY_SERVER;
    private String serverUrl = "ws://localhost:8080";
    private String roomKey = "default";
    private int lanPort = 24454;
    private int syncIntervalTicks = 5; // 250ms (4 times / sec)

    // Minimap Display
    private boolean minimapEnabled = true;
    private MinimapCorner minimapCorner = MinimapCorner.TOP_RIGHT;
    private MinimapShape minimapShape = MinimapShape.RECTANGLE;
    private int minimapSize = 120;
    private float minimapZoom = 1.0f;
    private boolean showCoordinates = true;
    private boolean showPlayerNames = true;
    private boolean showWaypoints = true;
    private boolean showCompass = true;

    // World Map
    private float worldMapDefaultZoom = 1.0f;

    // Waypoints
    private List<Waypoint> waypoints = new ArrayList<>();

    public BetterMapConfig() {}

    public static BetterMapConfig load(Path configPath) {
        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                BetterMapConfig config = GSON.fromJson(reader, BetterMapConfig.class);
                if (config != null) {
                    if (config.waypoints == null) config.waypoints = new ArrayList<>();
                    return config;
                }
            } catch (Exception e) {
                System.err.println("[BetterMap] Failed to load config, using defaults: " + e.getMessage());
            }
        }
        BetterMapConfig config = new BetterMapConfig();
        config.save(configPath);
        return config;
    }

    public void save(Path configPath) {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            System.err.println("[BetterMap] Failed to save config: " + e.getMessage());
        }
    }

    // Getters and Setters
    public SyncMode getSyncMode() { return syncMode; }
    public void setSyncMode(SyncMode syncMode) { this.syncMode = syncMode; }

    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }

    public String getRoomKey() { return roomKey; }
    public void setRoomKey(String roomKey) { this.roomKey = roomKey; }

    public int getLanPort() { return lanPort; }
    public void setLanPort(int lanPort) { this.lanPort = lanPort; }

    public int getSyncIntervalTicks() { return syncIntervalTicks; }
    public void setSyncIntervalTicks(int syncIntervalTicks) { this.syncIntervalTicks = syncIntervalTicks; }

    public boolean isMinimapEnabled() { return minimapEnabled; }
    public void setMinimapEnabled(boolean minimapEnabled) { this.minimapEnabled = minimapEnabled; }

    public MinimapCorner getMinimapCorner() { return minimapCorner; }
    public void setMinimapCorner(MinimapCorner minimapCorner) { this.minimapCorner = minimapCorner; }

    public MinimapShape getMinimapShape() { return minimapShape; }
    public void setMinimapShape(MinimapShape minimapShape) { this.minimapShape = minimapShape; }

    public int getMinimapSize() { return minimapSize; }
    public void setMinimapSize(int minimapSize) { this.minimapSize = minimapSize; }

    public float getMinimapZoom() { return minimapZoom; }
    public void setMinimapZoom(float minimapZoom) { this.minimapZoom = minimapZoom; }

    public boolean isShowCoordinates() { return showCoordinates; }
    public void setShowCoordinates(boolean showCoordinates) { this.showCoordinates = showCoordinates; }

    public boolean isShowPlayerNames() { return showPlayerNames; }
    public void setShowPlayerNames(boolean showPlayerNames) { this.showPlayerNames = showPlayerNames; }

    public boolean isShowWaypoints() { return showWaypoints; }
    public void setShowWaypoints(boolean showWaypoints) { this.showWaypoints = showWaypoints; }

    public boolean isShowCompass() { return showCompass; }
    public void setShowCompass(boolean showCompass) { this.showCompass = showCompass; }

    public float getWorldMapDefaultZoom() { return worldMapDefaultZoom; }
    public void setWorldMapDefaultZoom(float worldMapDefaultZoom) { this.worldMapDefaultZoom = worldMapDefaultZoom; }

    public List<Waypoint> getWaypoints() { return waypoints; }
}
