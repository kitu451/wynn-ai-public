package net.natga999.wynn_ai.managers;

import net.natga999.wynn_ai.render.BoxMarkerRenderer;
import net.natga999.wynn_ai.render.ItemMarkerRenderer;
import net.natga999.wynn_ai.render.MarkerRenderer;
import net.natga999.wynn_ai.render.RenderHUD;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Manages all rendering-related settings and operations
 */
public class RenderManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderManager.class);

    // Singleton instance
    private static RenderManager INSTANCE;

    // Rendering configuration flags
    private static boolean hudEnabled = false;
    private static boolean boxesEnabled = false;

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
    public static boolean isBoxEnabled() {
        return boxesEnabled;
    }
    public static void setBoxEnabled(boolean value) {
        boxesEnabled = value;
    }
    public void toggleBox() {
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
//            else if (entity instanceof DisplayEntity.ItemDisplayEntity itemDisplayEntity) {
//                NbtCompound nbt = itemDisplayEntity.writeNbt(new NbtCompound());
//                LOGGER.error("nbt: " + nbt);
//            }
        }
    }
}