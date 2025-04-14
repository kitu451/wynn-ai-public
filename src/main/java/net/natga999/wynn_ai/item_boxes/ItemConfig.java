package net.natga999.wynn_ai.item_boxes;

/**
 * @param minY   Minimum Y value
 * @param maxY   Maximum Y value
 * @param sizeXZ Size in the XZ plane
 * @param color  Item_box color
 */
public record ItemConfig(double minY, double maxY, double sizeXZ, int color) {
}