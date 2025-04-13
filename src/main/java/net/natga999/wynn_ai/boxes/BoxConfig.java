package net.natga999.wynn_ai.boxes;

/**
 * @param minY   Minimum Y value
 * @param maxY   Maximum Y value
 * @param sizeXZ Size in the XZ plane
 * @param color  Box color
 */
public record BoxConfig(double minY, double maxY, double sizeXZ, int color) {
}
