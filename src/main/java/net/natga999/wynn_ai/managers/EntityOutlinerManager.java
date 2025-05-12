package net.natga999.wynn_ai.managers;

import net.natga999.wynn_ai.WynnAIClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;

public class EntityOutlinerManager {
    private static EntityOutlinerManager INSTANCE;

    public static EntityOutlinerManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new EntityOutlinerManager();
        }
        return INSTANCE;
    }

    /**
     * Initialize renderers and settings
     */
    public static void init() {
        getInstance(); // Ensure instance is created
    }

    private RenderConfig getConfig() {
        return RenderManager.getInstance().getRenderConfig();
    }

    public void toggleOutlining() {
        boolean newValue = !getConfig().isOutlineAllEntities();
        getConfig().setOutlineAllEntities(newValue);

        if (!newValue) {
            getConfig().clearOutlineData();
        }
    }

    public void addEntityType(EntityType<?> entityType, int color) {
        getConfig().addOutlineEntityType(entityType, color);
    }

    public void removeEntityType(EntityType<?> entityType) {
        getConfig().removeOutlineEntityType(entityType);
    }

    public void addEntity(Entity entity, int color) {
        getConfig().addOutlineEntity(entity.getId(), color);
    }

    public void removeEntity(Entity entity) {
        getConfig().removeOutlineEntity(entity.getId());
    }

    public boolean shouldOutline(Entity entity) {
        RenderConfig config = getConfig();
        if (config.isOutlineAllEntities() && isEntityNearby(entity)) {
            return true;
        }
        if (config.hasEntityTypeOutline(entity.getType()) && isEntityNearby(entity)) {
            return true;
        }
        return config.hasEntityOutline(entity.getId());
    }

    public int getOutlineColor(Entity entity) {
        RenderConfig config = getConfig();
        Integer specificColor = config.getEntityOutlineColor(entity.getId());
        if (specificColor != null) return specificColor;

        Integer typeColor = config.getEntityTypeOutlineColor(entity.getType());
        return typeColor != null ? typeColor : RenderConfig.DEFAULT_OUTLINE_COLOR;
    }

    private boolean isEntityNearby(Entity entity) {
        return WynnAIClient.getCachedNearbyEntities().contains(entity);
    }
}