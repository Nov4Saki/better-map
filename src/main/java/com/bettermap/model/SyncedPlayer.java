package com.bettermap.model;

import java.util.UUID;

/**
 * Represents another player with the mod synchronized across the network.
 */
public class SyncedPlayer {
    private final UUID uuid;
    private String name;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private String dimension;
    private float health;
    private long lastSeenMs;

    public SyncedPlayer(UUID uuid, String name, double x, double y, double z, float yaw, float pitch, String dimension, float health) {
        this.uuid = uuid;
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.dimension = dimension != null ? dimension : "minecraft:overworld";
        this.health = health;
        this.lastSeenMs = System.currentTimeMillis();
    }

    public void update(String name, double x, double y, double z, float yaw, float pitch, String dimension, float health) {
        if (name != null && !name.isEmpty()) this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        if (dimension != null) this.dimension = dimension;
        this.health = health;
        this.lastSeenMs = System.currentTimeMillis();
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name != null ? name : "Player"; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public String getDimension() { return dimension; }
    public float getHealth() { return health; }
    public long getLastSeenMs() { return lastSeenMs; }

    public double distanceTo(double targetX, double targetZ) {
        double dx = this.x - targetX;
        double dz = this.z - targetZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public boolean isStale(long maxAgeMs) {
        return (System.currentTimeMillis() - lastSeenMs) > maxAgeMs;
    }
}
