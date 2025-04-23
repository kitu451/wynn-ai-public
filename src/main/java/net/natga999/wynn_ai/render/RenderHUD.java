package net.natga999.wynn_ai.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class RenderHUD {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderHUD.class);

    private static final int Y_OFFSET_INCREMENT = 10;
    private static final int DEFAULT_TEXT_COLOR = 0xFFFFFF;

    // Caching display names and distances for performance
    private final Map<Entity, CachedEntityInfo> entityCache = new HashMap<>();

    // Immutable record to cache display name, distance, and name width
    private record CachedEntityInfo(
            String displayName,  // Cached display name
            int nameWidth        // Cached width of the display name
    ) {}

    public void renderDetectedEntitiesOnHud(DrawContext drawContext, MinecraftClient client, List<Entity> detectedEntities, int entityCount) {
        try {
            // Ensure client player exists
            if (client.player == null) {
                LOGGER.warn("Client player is not available. Skipping HUD rendering.");
                return;
            }

            int screenWidth = client.getWindow().getScaledWidth();  // Screen width
            int screenHeight = client.getWindow().getScaledHeight(); // Screen height
            int centerX = screenWidth / 2;                         // Center X-coordinate
            int centerY = screenHeight / 2;                        // Center Y-coordinate

            // Dynamic offset (adjust depending on the entity count to space elements properly)
            int yOffset = centerY - (entityCount * Y_OFFSET_INCREMENT) / 2;

            // Iterate through detected entities and render them on the HUD
            for (Entity entity : detectedEntities) {
                try {

                    CachedEntityInfo cachedInfo = entityCache.get(entity);

                    // Cache the display name and name width if not already cached
                    if (cachedInfo == null) {
                        String displayName = getEntityDisplayName(entity);
                        int nameWidth = client.textRenderer.getWidth(displayName);
                        cachedInfo = new CachedEntityInfo(displayName, nameWidth);
                        entityCache.put(entity, cachedInfo);
                    }

                    // Dynamically calculate the distance to the player
                    float distance = entity.distanceTo(client.player);

                    // Use cached values for rendering
                    drawContext.drawText(
                        client.textRenderer,
                        cachedInfo.displayName + " (" + String.format("%.2f", distance) + "m)", // Combine cached name and dynamic distance
                        centerX - (cachedInfo.nameWidth / 2), // Align text to the center using cached width
                        yOffset,
                        DEFAULT_TEXT_COLOR, // Text color: white
                        false
                    );
                    yOffset += Y_OFFSET_INCREMENT; // Increment Y position for the next line
                } catch (Exception e) {
                    // Log an error for an individual entity but continue processing others
                    LOGGER.error("Failed to render entity {} on HUD: {}", entity.getType().toString(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            // Catch any unexpected errors at a higher level
            LOGGER.error("Failed to render HUD: {}", e.getMessage(), e);
        }

        // Clean up cache by removing entities no longer rendered
        entityCache.keySet().removeIf(entity -> !detectedEntities.contains(entity));
    }

    // Helper method to calculate the display name
    private String getEntityDisplayName(Entity entity) {
        try {
            if (entity instanceof ItemEntity) {
                return "Item: " + ((ItemEntity) entity).getStack().getName().getString();
            } else if (entity instanceof DisplayEntity.TextDisplayEntity displayEntity) {
                return extractTextFromNBT(displayEntity);
            } else if (entity instanceof MobEntity) {
                return "Mob: " + entity.getName().getString();
            }
            return "Entity: " + entity.getName().getString();
        } catch (Exception e) {
            LOGGER.warn("Failed to determine display name for entity {}: {}", entity.getType().toString(), e.getMessage(), e);
            return "Unknown Entity";
        }
    }

    // Extract "text" field from NBT for TextDisplayEntity, or fallback
    private String extractTextFromNBT(DisplayEntity.TextDisplayEntity displayEntity) {
        try {
            NbtCompound nbt = displayEntity.writeNbt(new NbtCompound());
            if (nbt.contains("text")) {
                return nbt.getString("text");
            }
            return "Unnamed Text Entity";
        } catch (Exception e) {
            LOGGER.warn("Failed to extract text from NBT for TextDisplayEntity: {}", e.getMessage(), e);
            return "Invalid Text Entity";
        }
    }
}