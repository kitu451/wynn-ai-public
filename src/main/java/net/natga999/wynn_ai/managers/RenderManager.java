package net.natga999.wynn_ai.managers;

import net.natga999.wynn_ai.menus.huds.MenuHUD;
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

    private static boolean menuVisible = false;
    private static boolean menuHUDVisible = false;
    private static boolean interactionMode = false;

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

    // Menu HUD rendering management
    public static boolean isMenuHUDEnabled() {
        return menuHUDVisible;
    }
    public static void setMenuHUDEnabled(boolean value) {
        menuHUDVisible = value;
    }
    public void toggleMenuHUD() {
        menuHUDVisible = !menuHUDVisible;
    }

    // Menu visibility
    public static boolean isMenuVisible() {
        return menuVisible;
    }

    public static void toggleMenuVisible() {
        menuVisible = !menuVisible;
    }

    // Interaction mode (cursor unlock)
    public static boolean isInteractionMode() {
        return interactionMode;
    }

    public static void toggleInteractionMode() {
        interactionMode = !interactionMode;

        MinecraftClient client = MinecraftClient.getInstance();
        if (interactionMode) {
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
        if (!menuHUDVisible) {
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
        if (hudEnabled && !entities.isEmpty()) {
            hudRenderer.renderDetectedEntitiesOnHud(drawContext, client, entities, entities.size());
        }
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