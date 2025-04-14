package net.natga999.wynn_ai;

import net.natga999.wynn_ai.detector.EntityDetector;
import net.natga999.wynn_ai.render.BoxMarkerRenderer;
import net.natga999.wynn_ai.render.ItemMarkerRenderer;
import net.natga999.wynn_ai.render.MarkerRenderer;
import net.natga999.wynn_ai.render.RenderHUD;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.Entity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collections;
import java.util.List;

public class TestRender implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestRender.class);

    private static final int detectionRadius = 256; // Radius to detect entities
    private final EntityDetector entityDetector = new EntityDetector(detectionRadius);
    private final MarkerRenderer markerRenderer = new BoxMarkerRenderer();
    private final ItemMarkerRenderer itemMarkerRenderer = new ItemMarkerRenderer();
    private final RenderHUD renderHUD = new RenderHUD();

    // Cache to store nearby entities
    private List<Entity> cachedNearbyEntities = Collections.emptyList();

    // Flag to toggle HUD rendering
    private boolean renderHud = false;

    // KeyBinding for toggling HUD
    private KeyBinding toggleHudKey;


    @Override
    public void onInitializeClient() {
        LOGGER.info("Initialized Client");
        // Initialize the keybinding
        registerKeyBind();

        // Register rendering event callback
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            // Main world rendering logic
            MinecraftClient client = MinecraftClient.getInstance();
            updateCachedNearbyEntities(client); // Update the cache once
            renderDetectedNearbyEntitiesBox(context, client);
        });

        // Register a HUD callback to display detected entities
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (renderHud) {
                renderDetectedEntitiesOnHud(drawContext);
            }
        });

        // Register a tick event for listening to key presses
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Check if the toggle key was pressed
            if (toggleHudKey.wasPressed()) {
                renderHud = !renderHud; // Toggle the HUD state
            }
        });
    }

    private void registerKeyBind() {
        toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wynn_ai.toggle_hud", // Name/Identifier for the key
                InputUtil.Type.KEYSYM,    // Type of key input
                GLFW.GLFW_KEY_H,          // Default key (e.g., "H")
                "category.wynn_ai"        // Keybinding category (e.g., mod-specific category)
        ));
    }

    private void updateCachedNearbyEntities(MinecraftClient client) {
        assert client.player != null;
        Vec3d playerPos = client.player.getPos();
        // Detect nearby entities and cache the result
        cachedNearbyEntities = entityDetector.detectNearbyEntities(playerPos, client);
    }

    private void renderDetectedEntitiesOnHud(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        assert client.player != null;

        // Delegate HUD rendering logic to RenderHUD
        renderHUD.renderDetectedEntitiesOnHud(drawContext, client, cachedNearbyEntities, cachedNearbyEntities.size());
    }

    private void renderDetectedNearbyEntitiesBox(WorldRenderContext context, MinecraftClient client) {
        // Render markers for nearby entities
        if (client.world == null || client.player == null) return;

        Camera camera = client.gameRenderer.getCamera();
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider.Immediate vertices = client.getBufferBuilders().getEntityVertexConsumers();

        for (Entity entity : cachedNearbyEntities) {
            if (entity instanceof DisplayEntity.TextDisplayEntity displayEntity) {
                NbtCompound nbt = displayEntity.writeNbt(new NbtCompound());
                markerRenderer.renderMarker(nbt, camera, matrices, vertices);
            }
            if (entity instanceof ItemEntity itemEntity) {
                itemMarkerRenderer.renderMarkerForItem(itemEntity, camera, matrices, vertices);
            }
        }
    }
}