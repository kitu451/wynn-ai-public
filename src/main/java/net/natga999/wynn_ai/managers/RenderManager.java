package net.natga999.wynn_ai.managers;

import net.natga999.wynn_ai.render.BoxMarkerRenderer;
import net.natga999.wynn_ai.render.ItemMarkerRenderer;
import net.natga999.wynn_ai.render.MarkerRenderer;
import net.natga999.wynn_ai.render.RenderHUD;

import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

import static net.natga999.wynn_ai.managers.ResourceNodeManager.scanAndStore;

/**
 * Manages all rendering-related settings and operations
 */
public class RenderManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderManager.class);

    // Singleton instance
    private static RenderManager INSTANCE;

    private final RenderConfig config = new RenderConfig();

    // Renderers
    private final RenderHUD hudRenderer = new RenderHUD();
    private final MarkerRenderer boxMarkerRenderer = new BoxMarkerRenderer();
    private final ItemMarkerRenderer itemMarkerRenderer = new ItemMarkerRenderer();

    private RenderManager() {
        // Private constructor for singleton
    }

    public RenderConfig getRenderConfig() {
        return config;
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
    public boolean isHudEnabled() {
        return config.isHudEnabled();
    }
    public void setHudEnabled(boolean value) {
        config.setHudEnabled(value);
    }
    public void toggleHud() {
        config.setHudEnabled(!config.isHudEnabled());
    }

    // Box rendering management
    public boolean isBoxEnabled() {
        return config.isBoxesEnabled();
    }
    public void setBoxEnabled(boolean value) {
        config.setBoxesEnabled(value);
    }
    public void toggleBox() {
        config.setBoxesEnabled(!config.isBoxesEnabled());
    }

    // Menu HUD rendering management
    public boolean isMenuHUDEnabled() {
        return config.isMenuHUDVisible();
    }
    public void setMenuHUDEnabled(boolean value) {
        config.setMenuHUDVisible(value);
    }
    public void toggleMenuHUD() {
        config.setMenuHUDVisible(!config.isMenuHUDVisible());
    }

    // Interaction mode (cursor unlock)
    public boolean isInteractionMode() {
        return config.isInteractionMode();
    }

    public void setInteractionMode(Boolean value) {
        config.setInteractionMode(value);

        MinecraftClient client = MinecraftClient.getInstance();
        if (value) {
            client.mouse.unlockCursor();
        } else {
            client.mouse.lockCursor();
        }
    }

    public void toggleInteractionMode() {
        config.setInteractionMode(config.isInteractionMode());

        MinecraftClient client = MinecraftClient.getInstance();
        if (config.isInteractionMode()) {
            client.mouse.unlockCursor();
        } else {
            client.mouse.lockCursor();
        }
    }

    /**
     * Dynamically render a MenuHUD by name.
     * @param drawContext The DrawContext of the game.
     * @param client The MinecraftClient instance.
     * @param menuName The name of the MenuHUD to render.
     */
    public void renderMenuWithName(DrawContext drawContext, MinecraftClient client, String menuName) {
        if (!config.isMenuHUDVisible()) {
            return; // Do not process if the menu HUD is disabled
        }

        if (menuName == null || menuName.isEmpty()) {
            LOGGER.warn("Menu name is null or empty.");
            return;
        }

        MenuHUDManager.renderAll(drawContext, client);
    }

    /**
     * Render the HUD with entity information
     */
    public void renderEntityHud(DrawContext drawContext, MinecraftClient client, List<Entity> entities) {
        if (config.isHudEnabled() && !entities.isEmpty()) {
            hudRenderer.renderDetectedEntitiesOnHud(drawContext, client, entities, entities.size());
        }
    }

    /**
     * Render boxes around entities in the world
     */
    public void renderEntityBoxes(WorldRenderContext context, List<Entity> entities) {
        if (!config.isBoxesEnabled() || entities.isEmpty()) return;

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
                scanAndStore(nbt);
            }
        }

        // Render stored nodes from JSON
        String currentDimension = ResourceNodeManager.getCurrentDimension();
        ResourceNodeManager.getTrackedResources().forEach(resource -> ResourceNodeManager.getNodes(resource).stream()
                .filter(node -> node.dimension.equals(currentDimension))
                .forEach(node -> {
                    Vec3d pos = new Vec3d(node.x, node.y, node.z);
                    boxMarkerRenderer.renderMarker(
                            pos,
                            0xFF00FF00,
                            context.camera(),
                            context.matrixStack(),
                            context.consumers()
                    );
                }));
    }
}