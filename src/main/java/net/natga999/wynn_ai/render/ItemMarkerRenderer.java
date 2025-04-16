package net.natga999.wynn_ai.render;

import net.natga999.wynn_ai.item_boxes.ItemConfig;
import net.natga999.wynn_ai.item_boxes.ItemConfigRegistry;

import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Renderer for ItemEntity markers.
 * Renders a box around the position of the entity, with customizable size and color.
 */
public class ItemMarkerRenderer implements ItemRenderer {

    /**
     * Renders a marker for a given ItemEntity.
     *
     * @param itemEntity      The ItemEntity to render a marker for.
     * @param camera          The camera for relative positioning.
     * @param matrices        The current transformation matrix.
     * @param vertexConsumers The vertex consumers for rendering the entity.
     */
    public void renderMarker(ItemEntity itemEntity, Camera camera, MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        // Get the item's position
        Vec3d position = itemEntity.getPos();

        // Translate the position relative to the camera
        Vec3d relativePos = position.subtract(camera.getPos());

        // Get the item's display name
        String name = itemEntity.getStack().getName().getString();

        // Complex Item
        NbtCompound nbt = itemEntity.writeNbt(new NbtCompound());
        int Damage = nbt.getCompound("Item").getCompound("components").getInt("minecraft:damage");
        String itemId = nbt.getCompound("Item").getString("id");

        switch (itemId) {
            case "minecraft:diamond_axe" -> {
                switch (Damage) {
                    case 96 -> name = String.valueOf(18); // Discarded Scrap (damage=96, after pickup -> CustomModelData=18)
                    case 61 -> name = String.valueOf(68); // Missing Coinage (damage=61, after pickup -> CustomModelData=68)
                    case 28 -> name = String.valueOf(85); // Fossilized Starfish (damage=28, after pickup -> CustomModelData=85)
                    case 66 -> name = String.valueOf(73); // Elestial Voidstone (damage=66, after pickup -> CustomModelData=73)
                    case 53 -> name = String.valueOf(59); // Precious Mineral (damage=53, after pickup -> CustomModelData=59)
                    case 65 -> name = String.valueOf(72); // Small Ruby (damage=65, after pickup -> CustomModelData=72)
                }
            }
            case "minecraft:diamond_shovel" -> {
                switch (Damage) {
                    case 99 -> name = String.valueOf(542); // Hobby Horse (damage=99, after pickup -> CustomModelData=542)
                    case 25 -> name = String.valueOf(306); // Frying Pan (damage=25, after pickup -> CustomModelData=306)
                }
            }
        }

        // Check for Custom Model cases
        int CustomModelData = nbt.getCompound("Item").getCompound("components").getInt("minecraft:custom_model_data");
        if (CustomModelData != 0) {
            name = String.valueOf(CustomModelData);
        }

        // Find a matching box configuration for this item's name
        ItemConfig matchingConfig = null;
        for (String keyword : ItemConfigRegistry.getRegisteredKeywords()) {
            if (name.contains(keyword)) {
                // If the item's name contains a keyword, use its config
                matchingConfig = ItemConfigRegistry.getConfig(keyword);
                break;
            }
        }

        // Use a default configuration if no match is found
        if (matchingConfig == null) {
            matchingConfig = ItemConfigRegistry.getDefaultConfig();
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