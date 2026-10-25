package com.bettermap.model;

public class Waypoint {
    private String id;
    private String name;
    private double x;
    private double y;
    private double z;
    private String dimension;
    private int color;
    private boolean enabled;

    public Waypoint() {}

    public Waypoint(String id, String name, double x, double y, double z, String dimension, int color) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.color = color;
        this.enabled = true;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public String getDimension() { return dimension; }
    public int getColor() { return color; }
    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setName(String name) { this.name = name; }
    public void setColor(int color) { this.color = color; }
}
