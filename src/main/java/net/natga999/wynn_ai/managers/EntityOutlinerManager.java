package net.natga999.wynn_ai.managers;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.natga999.wynn_ai.TestRender;

import java.util.HashMap;
import java.util.Map;

public class EntityOutlinerManager {
    // Flag to enable or disable entity outlining
    public static boolean outlineAllEntities = false;

    // Map to store entity types that should be outlined (for potential future filtering)
    public static Map<EntityType<?>, Integer> outlinedEntityTypes = new HashMap<>();

    // Map to store specific entity instances that should be outlined with their colors
    public static Map<Integer, Integer> outlinedEntities = new HashMap<>();

    // Colors for different entity types (optional)
    public static final int DEFAULT_OUTLINE_COLOR = 0xFFFFFF; // White

    // Initialize with all entity types if needed
    public static void init() {
        // This could be expanded to add specific entity types with different colors
    }

    public static boolean isOutliningEnabled() {
        return outlineAllEntities;
    }
    public static void setOutliningEnabled(boolean value) {
        outlineAllEntities = value;
    }
    public static void toggleOutlining() {
        outlineAllEntities = !outlineAllEntities;

        if (!outlineAllEntities) {
            outlinedEntityTypes.clear();
            outlinedEntities.clear();
        }
    }

    /**
     * Adds an entity type to be outlined with a specific color
     * @param entityType The entity type to outline
     * @param color The outline color (RGB)
     */
    public static void addEntityType(EntityType<?> entityType, int color) {
        outlinedEntityTypes.put(entityType, color);
    }

    /**
     * Removes an entity type from being outlined
     * @param entityType The entity type to remove
     */
    public static void removeEntityType(EntityType<?> entityType) {
        outlinedEntityTypes.remove(entityType);
    }

    /**
     * Adds a specific entity to be outlined with a color
     * @param entity The entity to outline
     * @param color The outline color (RGB)
     */
    public static void addEntity(Entity entity, int color) {
        outlinedEntities.put(entity.getId(), color);
    }

    /**
     * Removes a specific entity from being outlined
     * @param entity The entity to remove
     */
    public static void removeEntity(Entity entity) {
        outlinedEntities.remove(entity.getId());
    }


    /**
     * Checks if an entity should be outlined
     * @param entity The entity to check
     * @return True if the entity should be outlined
     */
    public static boolean shouldOutline(Entity entity) {
        if (outlineAllEntities && TestRender.getCachedNearbyEntities().contains(entity)) {
            return true;
        }

        // Restrict outlined entity types to nearby entities
        if (outlinedEntityTypes.containsKey(entity.getType())
                && TestRender.getCachedNearbyEntities().contains(entity)) {
            return true;
        }

        // Check for manually added specific entities
        return outlinedEntities.containsKey(entity.getId());
    }

    /**
     * Gets the outline color for an entity
     * @param entity The entity to get the color for
     * @return The color as RGB int
     */
    public static int getOutlineColor(Entity entity) {
        // First check if this specific entity has a color
        if (outlinedEntities.containsKey(entity.getId())) {
            return outlinedEntities.get(entity.getId());
        }

        // Then check if its entity type has a color
        if (outlinedEntityTypes.containsKey(entity.getType())) {
            return outlinedEntityTypes.get(entity.getType());
        }

        // Default color
        return DEFAULT_OUTLINE_COLOR;
    }

}