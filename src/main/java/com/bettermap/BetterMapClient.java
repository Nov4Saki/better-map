package com.bettermap;

import com.bettermap.config.BetterMapConfig;
import com.bettermap.gui.MinimapHud;
import com.bettermap.gui.NewWaypointScreen;
import com.bettermap.gui.WorldMapScreen;
import com.bettermap.map.MapTextureManager;
import com.bettermap.map.SurveyMapProvider;
import com.bettermap.network.PlayerSyncManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class BetterMapClient implements ClientModInitializer {
    public static final String MOD_ID = "better_map";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static BetterMapClient instance;

    private BetterMapConfig config;
    private Path configPath;
    private MapTextureManager textureManager;
    private PlayerSyncManager syncManager;
    private MinimapHud minimapHud;

    private KeyMapping openMapKey;
    private KeyMapping toggleMinimapKey;
    private KeyMapping newWaypointKey;

    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("[BetterMap] Initializing standalone Minimap & World Map mod for Minecraft 26.2 / 1.21.11+...");

        // 1. Load Configuration
        configPath = FabricLoader.getInstance().getConfigDir().resolve("better_map.json");
        config = BetterMapConfig.load(configPath);

        // 2. Initialize Texture & Sync Subsystems & Survey Map Engine
        textureManager = new MapTextureManager();
        syncManager = new PlayerSyncManager(config);
        SurveyMapProvider.getInstance().initializeAsync(config);

        // 3. Register Key Mappings
        openMapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.better_map.open_map",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_M,
                KeyMapping.Category.MISC
        ));

        toggleMinimapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.better_map.toggle_minimap",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_N,
                KeyMapping.Category.MISC
        ));

        newWaypointKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.better_map.new_waypoint",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_B,
                KeyMapping.Category.MISC
        ));

        // 4. Register Minimap HUD Element
        minimapHud = new MinimapHud(config, textureManager, syncManager);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "minimap"), minimapHud);

        // 5. Register Client Tick Events (Key inputs & sync heartbeats)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // Handle World Map Open Key
            while (openMapKey.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.setScreenAndShow(new WorldMapScreen(config, textureManager, syncManager));
                }
            }

            // Handle Minimap Toggle Key
            while (toggleMinimapKey.consumeClick()) {
                config.setMinimapEnabled(!config.isMinimapEnabled());
                saveConfig();
            }

            // Handle Quick Waypoint Key (B)
            while (newWaypointKey.consumeClick()) {
                if (client.gui.screen() == null && client.player != null) {
                    var p = client.player;
                    String dim = p.level().dimension().identifier().toString();
                    client.setScreenAndShow(new NewWaypointScreen(null, config, dim, p.getX(), p.getY(), p.getZ()));
                }
            }

            // Dispatch sync ticks
            syncManager.tick(client);
        });

        // 6. Register Lifecycle Shutdown Hook
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            LOGGER.info("[BetterMap] Client stopping. Saving config & shutting down sync channels...");
            saveConfig();
            syncManager.shutdown();
            textureManager.close();
        });
    }

    public static void saveConfig() {
        if (instance != null && instance.config != null && instance.configPath != null) {
            instance.config.save(instance.configPath);
        }
    }

    public static BetterMapClient getInstance() { return instance; }
    public BetterMapConfig getConfig() { return config; }
    public MapTextureManager getTextureManager() { return textureManager; }
    public PlayerSyncManager getSyncManager() { return syncManager; }
}
