package net.natga999.wynn_ai.render;

import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.natga999.wynn_ai.boxes.BoxConfig;
import net.natga999.wynn_ai.boxes.BoxConfigRegistry;

/**
 * Renderer for ItemEntity markers.
 * Renders a box around the position of the entity, with customizable size and color.
 */
public class ItemMarkerRenderer {

    /**
     * Renders a marker for a given ItemEntity.
     *
     * @param itemEntity      The ItemEntity to render a marker for.
     * @param camera          The camera for relative positioning.
     * @param matrices        The current transformation matrix.
     * @param vertexConsumers The vertex consumers for rendering the entity.
     */
    public void renderMarkerForItem(ItemEntity itemEntity, Camera camera, MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        // Get the item's position
        Vec3d position = itemEntity.getPos();

        // Translate the position relative to the camera
        Vec3d relativePos = position.subtract(camera.getPos());

        // Get the item's display name
        String name = itemEntity.getStack().getName().getString();

        // Find a matching box configuration for this item's name
        BoxConfig matchingConfig = null;
        for (String keyword : BoxConfigRegistry.getRegisteredKeywords()) {
            if (name.contains(keyword)) {
                // If the item's name contains a keyword, use its config
                matchingConfig = BoxConfigRegistry.getConfig(keyword);
                break;
            }
        }

        // Use a default configuration if no match is found
        if (matchingConfig == null) {
            matchingConfig = BoxConfigRegistry.getDefaultConfig();
        }

        // Define the bounding box around the item
        Box box = new Box(
                relativePos.x - matchingConfig.sizeXZ(), relativePos.y + matchingConfig.minY(), relativePos.z - matchingConfig.sizeXZ(),
                relativePos.x + matchingConfig.sizeXZ(), relativePos.y + matchingConfig.maxY(), relativePos.z + matchingConfig.sizeXZ()
        );

        // Get a line vertex consumer for box rendering
        VertexConsumer lines = vertexConsumers.getBuffer(RenderLayer.getLines());

        // Render the box outline
        drawBoxOutline(matrices, lines, box, matchingConfig.color(), 1.0f);
    }

    /**
     * Draws an outlined box at the specified position with the given color and transparency.
     */
    private void drawBoxOutline(MatrixStack matrices, VertexConsumer lines, Box box, int color, float alpha) {
        // Split the color into RGB components
        float r = (color >> 16 & 0xFF) / 255.0f;
        float g = (color >> 8 & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        // Use the WorldRenderer class to draw the box
        WorldRenderer.drawBox(
                matrices, // Matrix stack for transformations
                lines,    // VertexConsumer for rendering the lines
                box.minX, box.minY, box.minZ, // Box minimum bounds
                box.maxX, box.maxY, box.maxZ, // Box maximum bounds
                r, g, b, alpha // Box color and transparency
        );
    }
}