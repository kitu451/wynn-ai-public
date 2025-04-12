package net.natga999.wynn_ai;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.natga999.wynn_ai.boxes.BoxConfig;
import net.natga999.wynn_ai.boxes.BoxConfigRegistry;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TestRender implements ClientModInitializer {

    private static final int detectionRadius = 5; // Radius to detect entities
    //private static final Vec3d MARK_POSITION = new Vec3d(-1041.75, 50, -5223.5); // Marker position in the world
    private static final int MARK_COLOR = 0x00FF00; // Color of the marker (green)

    // Store already processed entities
    private static final Set<Entity> processedEntities = new HashSet<>();

    @Override
    public void onInitializeClient() {
        // Register a World Render callback for custom rendering
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register((context) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null || client.player == null) {
                return;
            }

            // Assuming we're focusing on specific entities (e.g., DisplayEntity), you can extract their NBT:
            client.world.getEntities().forEach(entity -> {
                if (entity instanceof DisplayEntity.TextDisplayEntity displayEntity) {
                    try {
                        NbtCompound nbt = new NbtCompound();
                        displayEntity.writeNbt(nbt); // Extract the NBT data for this entity
                        drawMarkerFromNbt(nbt, context.camera(), context.matrixStack(), context.consumers());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        });

        // Register a HUD callback to display detected entities
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> detectAndRenderNearbyEntities(drawContext));
    }

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

    private void drawMarkerFromNbt(NbtCompound nbt, Camera camera, MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        // Extract marker position from NBT
        Vec3d markerPosition = extractPositionFromNbt(nbt);
        if (markerPosition == null) {
            return; // If no position is present, skip rendering
        }

        // Extract text from NBT
        String text = nbt.contains("text") ? nbt.getString("text") : "";

        // Iterate through all registered keywords in the BoxConfigRegistry
        BoxConfig matchingConfig = null;
        for (String keyword : BoxConfigRegistry.getRegisteredKeywords()) {
            if (text.contains(keyword)) { // Check if the text contains the keyword
                matchingConfig = BoxConfigRegistry.getConfig(keyword);
                break; // Stop at the first matching keyword
            }
        }

        if (matchingConfig == null) {
            return; // If no matching keyword was found, skip rendering
        }

        // Use the configuration to define the box dimensions
        Vec3d cameraPos = camera.getPos(); // Player's camera position
        Vec3d relativePos = markerPosition.subtract(cameraPos); // Adjust the position relative to the camera

        // Define the box based on the configuration
        Box box = new Box(
                relativePos.x - matchingConfig.sizeXZ, relativePos.y + matchingConfig.minY, relativePos.z - matchingConfig.sizeXZ, // Bottom-left corner
                relativePos.x + matchingConfig.sizeXZ, relativePos.y + matchingConfig.maxY, relativePos.z + matchingConfig.sizeXZ  // Top-right corner
        );

        // Use a line strip to draw the outline of the box
        VertexConsumer lines = vertexConsumers.getBuffer(RenderLayer.getLines());
        drawBoxOutline(matrices, lines, box, matchingConfig.color, 1.0f);
    }

    private void drawBoxOutline(MatrixStack matrices, VertexConsumer lines, Box box, int color, float alpha) {
        // Convert the color to RGB float components
        float r = (color >> 16 & 0xFF) / 255.0f;
        float g = (color >> 8 & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        MatrixStack.Entry entry = matrices.peek();
        WorldRenderer.drawBox(
                matrices, // Pass the entire MatrixStack object
                lines,    // VertexConsumer for the lines
                box.minX, box.minY, box.minZ, // Box minimum
                box.maxX, box.maxY, box.maxZ, // Box maximum
                r, g, b, alpha // Color and alpha
        );

    }

    private void detectAndRenderNearbyEntities(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client == null || client.world == null || client.player == null) {
            return; // Exit if the client, world, or player is null
        }

        // Define the detection box for entities
        double playerX = client.player.getX();
        double playerY = client.player.getY();
        double playerZ = client.player.getZ();
        Box detectionBox = new Box(
                playerX - detectionRadius, playerY - detectionRadius, playerZ - detectionRadius,
                playerX + detectionRadius, playerY + detectionRadius, playerZ + detectionRadius
        );

        // Get all entities within the detection range
        List<Entity> nearbyEntities = new ArrayList<>(
                client.world.getEntitiesByClass(Entity.class, detectionBox, (entity) -> true)
        );

        // Get screen dimensions for rendering
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        int yOffset = centerY - (nearbyEntities.size() * 10) / 2;

        for (Entity entity : nearbyEntities) {
            String displayName;

            // Handle `ItemEntity` (item drops or item-based custom entities)
            if (entity instanceof ItemEntity itemEntity) {
                // Get the name of the item in this `ItemEntity`
                displayName = itemEntity.getStack().getName().getString();

                // Handle `MobEntity` (standard mobs)
            } else if (entity instanceof MobEntity) {
                // Use the entity's display name, fallback to class name if null
                displayName = entity.getName() != null ? entity.getName().getString() : "Unnamed Mob";

                // Fallback for other entity types
            } else {
                displayName = "Unknown Entity (" + entity.getClass().getSimpleName() + ")";
            }

            if (entity instanceof DisplayEntity.TextDisplayEntity displayEntity) {
                // First: Directly read the NBT data
                try {
                    NbtCompound nbt = new NbtCompound();
                    displayEntity.writeNbt(nbt); // Save the entity's data into the NBT compound

                    // Extract specific fields as needed
                    if (nbt.contains("text")) {
                        String text = nbt.getString("text");
                        displayName = text;
                        //System.out.println("Extracted Text: " + text);
                    } else {
                        System.out.println("No 'text' field found in NBT Data.");
                    }

                } catch (Exception e) {
                    System.err.println("Error reading NBT data: " + e.getMessage());
                }
            }

            // Render the entity name or text above the player HUD
            drawContext.drawText(
                    client.textRenderer,
                    displayName + " (" + (int) entity.distanceTo(client.player) + "m)", // Render text with distance
                    centerX - (client.textRenderer.getWidth(displayName) / 2),
                    yOffset,
                    0xFFFFFF, // Text color: white
                    false
            );

            yOffset += 10; // Increment Y position for the next line
        }
    }
}