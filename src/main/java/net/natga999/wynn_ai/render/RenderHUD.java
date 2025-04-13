package net.natga999.wynn_ai.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;

import java.util.List;

public class RenderHUD {
    // Render logic for the detected entities, receives the list of entities as a parameter
    public void renderDetectedEntitiesOnHud(DrawContext drawContext, MinecraftClient client, List<Entity> detectedEntities, int entityCount)
    {
        int screenWidth = client.getWindow().getScaledWidth();  // Screen width
        int screenHeight = client.getWindow().getScaledHeight(); // Screen height
        int centerX = screenWidth / 2;                         // Center X-coordinate
        int centerY = screenHeight / 2;                        // Center Y-coordinate

        // Dynamic offset (adjust depending on the entity count to space elements properly)
        int yOffset = centerY - (entityCount * 10) / 2;

        // Iterate through the detected entities and render them on the HUD
        for (Entity entity : detectedEntities) {
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
                        displayName = nbt.getString("text");
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
