package net.natga999.wynn_ai.render;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

public class EntityOutliner {
    // Flag to enable or disable entity outlining
    public static boolean outlineAllEntities = false;

    // Map to store entity types that should be outlined (for potential future filtering)
    public static Map<EntityType<?>, Integer> outlinedEntityTypes = new HashMap<>();

    // Colors for different entity types (optional)
    public static final int DEFAULT_OUTLINE_COLOR = 0xFFFFFF; // White

    // Initialize with all entity types if needed
    public static void init() {
        // This could be expanded to add specific entity types with different colors
    }

    /**
     * Toggles the outlining of all entities
     * @return The new state
     */
    public static boolean toggleOutlining() {
        outlineAllEntities = !outlineAllEntities;
        return outlineAllEntities;
    }

    /**
     * Checks if an entity should be outlined
     * @param entity The entity to check
     * @return True if the entity should be outlined
     */
    public static boolean shouldOutline(Entity entity) {
        return outlineAllEntities;
    }
}