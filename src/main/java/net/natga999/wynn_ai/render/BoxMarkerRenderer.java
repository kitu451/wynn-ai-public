package net.natga999.wynn_ai.render;

import net.natga999.wynn_ai.boxes.BoxConfig;
import net.natga999.wynn_ai.boxes.BoxConfigRegistry;

import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class BoxMarkerRenderer implements MarkerRenderer {

    @Override
    public void renderMarker(NbtCompound nbt, Camera camera, MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        // Extract marker position and text from NBT
        Vec3d position = extractPositionFromNbt(nbt);
        if (position == null) return; // Skip if no position available

        String text = nbt.contains("text") ? nbt.getString("text") : "";


        // Drew boxes for specific ItemModel of Dropped item
        if (nbt.getCompound("Item").getCompound("components").contains("minecraft:custom_model_data")) {
            text = String.valueOf(nbt.getCompound("Item").getCompound("components").getInt("minecraft:custom_model_data"));
        }

        BoxConfig matchingConfig = null;

        // Iterate through all registered keywords in BoxConfigRegistry
        for (String keyword : BoxConfigRegistry.getRegisteredKeywords()) {
            if (text.contains(keyword)) {
                matchingConfig = BoxConfigRegistry.getConfig(keyword);
                break;
            }
        }

        // If no matching box configuration, skip rendering
        if (matchingConfig == null) return;

        // Adjust position relative to camera
        Vec3d relativePos = position.subtract(camera.getPos());

        // Define the box
        Box box = new Box(
                relativePos.x - matchingConfig.sizeXZ(), relativePos.y + matchingConfig.minY(), relativePos.z - matchingConfig.sizeXZ(), // Bottom-left
                relativePos.x + matchingConfig.sizeXZ(), relativePos.y + matchingConfig.maxY(), relativePos.z + matchingConfig.sizeXZ()  // Top-right
        );

        // Draw the box outline
        VertexConsumer lines = vertexConsumers.getBuffer(RenderLayer.getLines());
        drawBoxOutline(matrices, lines, box, matchingConfig.color(), 1.0f);
    }

    // Helper methods
    private Vec3d extractPositionFromNbt(NbtCompound nbt) {
        if (nbt.contains("Pos")) { // Check if the NBT has a "Pos" element
            List<Double> posList = nbt.getList("Pos", 6).stream()
                    .map(tag -> ((NbtDouble) tag).doubleValue())
                    .toList(); // Convert the NBTList to a list of doubles
            if (posList.size() == 3) {
                return new Vec3d(posList.get(0), posList.get(1), posList.get(2));
            }
        }
        return null; // Return null if position is not found
    }

    private void drawBoxOutline(MatrixStack matrices, VertexConsumer lines, Box box, int color, float alpha) {
        // Convert the color to RGB float components
        float r = (color >> 16 & 0xFF) / 255.0f;
        float g = (color >> 8 & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        WorldRenderer.drawBox(
                matrices, // Pass the entire MatrixStack object
                lines,    // VertexConsumer for the lines
                box.minX, box.minY, box.minZ, // Box minimum
                box.maxX, box.maxY, box.maxZ, // Box maximum
                r, g, b, alpha // Color and alpha
        );
    }
}