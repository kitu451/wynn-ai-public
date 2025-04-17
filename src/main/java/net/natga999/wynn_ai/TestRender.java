package net.natga999.wynn_ai;

import net.natga999.wynn_ai.detector.EntityDetector;
import net.natga999.wynn_ai.keys.KeyInputHandler;
import net.natga999.wynn_ai.managers.EntityOutlinerManager;
import net.natga999.wynn_ai.managers.RenderManager;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.entity.Entity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collections;
import java.util.List;

public class TestRender implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestRender.class);

    // Static reference to the current instance
    private static TestRender INSTANCE;

    private static int detectionRadius = 16; // Radius to detect entities
    private EntityDetector entityDetector;

    // Cache to store nearby entities
    private List<Entity> cachedNearbyEntities = Collections.emptyList();

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initialized Client");
        INSTANCE = this;

        entityDetector = new EntityDetector(detectionRadius);

        // Initialize renderer managers
        RenderManager.init();
        EntityOutlinerManager.init();

        // Register key bindings
        KeyInputHandler.register();

        // Register HUD rendering
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            updateCachedNearbyEntities(client);
            RenderManager.getInstance().renderEntityHud(drawContext, client, cachedNearbyEntities);
        });

        // Register rendering event callback
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            // Main world rendering logic
            MinecraftClient client = MinecraftClient.getInstance();
            updateCachedNearbyEntities(client); // Update the cache once
            renderDetectedNearbyEntitiesBox(context, client);
        });
    }

    // Getter for detection radius
    public static int getDetectionRadius() {
        return detectionRadius;
    }

    public static List<Entity> getCachedNearbyEntities() {
        return INSTANCE.cachedNearbyEntities;
    }

    // Setter for detection radius with validation
    public static void setDetectionRadius(int radius) {
        // Ensure radius is within reasonable bounds (e.g., between 1 and 512)
        detectionRadius = Math.max(1, Math.min(512, radius));

        if (INSTANCE != null && INSTANCE.entityDetector != null) {
            INSTANCE.entityDetector.updateDetectionRadius(detectionRadius);
        }
    }

    private void updateCachedNearbyEntities(MinecraftClient client) {
        assert client.player != null;
        Vec3d playerPos = client.player.getPos();
        // Detect nearby entities and cache the result
        cachedNearbyEntities = entityDetector.detectNearbyEntities(playerPos, client);
    }

    private void renderDetectedNearbyEntitiesBox(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context, MinecraftClient client) {
        RenderManager.getInstance().renderEntityBoxes(context, client, cachedNearbyEntities);
    }
}