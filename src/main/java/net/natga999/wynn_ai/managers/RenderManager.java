package net.natga999.wynn_ai.managers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.natga999.wynn_ai.render.BoxMarkerRenderer;
import net.natga999.wynn_ai.render.ItemMarkerRenderer;
import net.natga999.wynn_ai.render.MarkerRenderer;
import net.natga999.wynn_ai.render.RenderHUD;

import java.util.List;

/**
 * Manages all rendering-related settings and operations
 */
public class RenderManager {
    // Singleton instance
    private static RenderManager INSTANCE;

    // Rendering configuration flags
    private static boolean hudEnabled = false;
    private static boolean boxesEnabled = true;

    // Renderers
    private final RenderHUD hudRenderer = new RenderHUD();
    private final MarkerRenderer boxMarkerRenderer = new BoxMarkerRenderer();
    private final ItemMarkerRenderer itemMarkerRenderer = new ItemMarkerRenderer();

    private RenderManager() {
        // Private constructor for singleton
    }

    public static RenderManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new RenderManager();
        }
        return INSTANCE;
    }

    /**
     * Initialize renderers and settings
     */
    public static void init() {
        getInstance(); // Ensure instance is created
    }

    // HUD visibility management
    public static boolean isHudEnabled() {
        return hudEnabled;
    }

    public static void setHudEnabled(boolean value) {
        hudEnabled = value;
    }

    public void toggleHud() {
        hudEnabled = !hudEnabled;
    }

    // Box rendering management
    public static boolean isBoxRenderingEnabled() {
        return boxesEnabled;
    }

    public static void setBoxRenderingEnabled(boolean value) {
        boxesEnabled = value;
    }

    public void toggleBoxRendering() {
        boxesEnabled = !boxesEnabled;
    }

    /**
     * Render the HUD with entity information
     */
    public void renderEntityHud(DrawContext drawContext, MinecraftClient client, List<Entity> entities) {
        if (!hudEnabled || entities.isEmpty()) return;

        hudRenderer.renderDetectedEntitiesOnHud(drawContext, client, entities, entities.size());
    }

    /**
     * Render boxes around entities in the world
     */
    public void renderEntityBoxes(WorldRenderContext context, MinecraftClient client, List<Entity> entities) {
        if (!boxesEnabled || entities.isEmpty()) return;

        for (Entity entity : entities) {
            if (entity instanceof net.minecraft.entity.ItemEntity itemEntity) {
                itemMarkerRenderer.renderMarker(
                        itemEntity,
                        context.camera(),
                        context.matrixStack(),
                        context.consumers()
                );
            } else if (entity instanceof DisplayEntity.TextDisplayEntity displayEntity){
                NbtCompound nbt = displayEntity.writeNbt(new NbtCompound());
                boxMarkerRenderer.renderMarker(
                        nbt,
                        context.camera(),
                        context.matrixStack(),
                        context.consumers()
                );
            }
        }
    }

    /**
     * Create an NBT compound with entity data for rendering
     */
    private net.minecraft.nbt.NbtCompound createEntityNbt(Entity entity) {
        net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
        nbt.putDouble("x", entity.getX());
        nbt.putDouble("y", entity.getY());
        nbt.putDouble("z", entity.getZ());
        nbt.putFloat("width", entity.getWidth());
        nbt.putFloat("height", entity.getHeight());
        return nbt;
    }
}