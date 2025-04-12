package net.natga999.wynn_ai.boxes;

public class BoxConfig {
    public final double minY; // Minimum Y value
    public final double maxY; // Maximum Y value
    public final double sizeXZ; // Size in the XZ plane
    public final int color; // Box color

    public BoxConfig(double minY, double maxY, double sizeXZ, int color) {
        this.minY = minY;
        this.maxY = maxY;
        this.sizeXZ = sizeXZ;
        this.color = color;
    }
}
