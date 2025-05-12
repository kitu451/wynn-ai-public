package net.natga999.wynn_ai.managers;

import net.minecraft.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

public class RenderConfig {
    private boolean hudEnabled = false;
    private boolean boxesEnabled = false;
    private boolean menuHUDVisible = false;
    private boolean interactionMode = false;

    // Outline configuration
    private boolean outlineAllEntities = false;
    private final Map<EntityType<?>, Integer> outlinedEntityTypes = new HashMap<>();
    private final Map<Integer, Integer> outlinedEntities = new HashMap<>();
    public static final int DEFAULT_OUTLINE_COLOR = 0xFFFFFF;

    public boolean isHudEnabled() {
        return hudEnabled;
    }
    public void setHudEnabled(boolean value) {
        hudEnabled = value;
    }

    public boolean isBoxesEnabled() {
        return boxesEnabled;
    }
    public void setBoxesEnabled(boolean value) {
        boxesEnabled = value;
    }

    public boolean isMenuHUDVisible() {
        return menuHUDVisible;
    }
    public void setMenuHUDVisible(boolean value) {
        menuHUDVisible = value;
    }

    public boolean isInteractionMode() {
        return interactionMode;
    }
    public void setInteractionMode(boolean value) {
        interactionMode = value;
    }

    // Outline configuration methods
    public boolean isOutlineAllEntities() {
        return outlineAllEntities;
    }
    public void setOutlineAllEntities(boolean value) {
        outlineAllEntities = value;
    }

    public void addOutlineEntityType(EntityType<?> entityType, int color) {
        outlinedEntityTypes.put(entityType, color);
    }

    public void removeOutlineEntityType(EntityType<?> entityType) {
        outlinedEntityTypes.remove(entityType);
    }

    public void addOutlineEntity(int entityId, int color) {
        outlinedEntities.put(entityId, color);
    }

    public void removeOutlineEntity(int entityId) {
        outlinedEntities.remove(entityId);
    }

    public void clearOutlineData() {
        outlinedEntityTypes.clear();
        outlinedEntities.clear();
    }

    public boolean hasEntityTypeOutline(EntityType<?> entityType) {
        return outlinedEntityTypes.containsKey(entityType);
    }

    public boolean hasEntityOutline(int entityId) {
        return outlinedEntities.containsKey(entityId);
    }

    public Integer getEntityTypeOutlineColor(EntityType<?> entityType) {
        return outlinedEntityTypes.get(entityType);
    }

    public Integer getEntityOutlineColor(int entityId) {
        return outlinedEntities.get(entityId);
    }
}