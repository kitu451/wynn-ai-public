package net.natga999.wynn_ai;

import net.natga999.wynn_ai.detector.EntityDetector;
import net.natga999.wynn_ai.keys.KeyInputHandler;
import net.natga999.wynn_ai.render.*;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.Entity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
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
    private final MarkerRenderer markerRenderer = new BoxMarkerRenderer();
    private final ItemMarkerRenderer itemMarkerRenderer = new ItemMarkerRenderer();
    private final RenderHUD renderHUD = new RenderHUD();

    // Cache to store nearby entities
    private List<Entity> cachedNearbyEntities = Collections.emptyList();

    // Flag to toggle HUD rendering
    private static boolean renderHud = false;
    // Flag to toggle box rendering
    private static boolean renderBoxes = true;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initialized Client");
        INSTANCE = this;

        // Initialize key bindings through KeyInputHandler
        KeyInputHandler.register();

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

        entityDetector = new EntityDetector(detectionRadius);

        EntityOutliner.init();
    }

    // Getter for detection radius
    public static int getDetectionRadius() {
        return detectionRadius;
    }

    // Setter for detection radius with validation
    public static void setDetectionRadius(int radius) {
        // Ensure radius is within reasonable bounds (e.g., between 1 and 512)
        detectionRadius = Math.max(1, Math.min(512, radius));

        if (INSTANCE != null && INSTANCE.entityDetector != null) {
            INSTANCE.entityDetector.updateDetectionRadius(detectionRadius);
        }
    }
    public static boolean isHudEnabled() {
        return renderHud;
    }
    public static void setHudEnabled(boolean value) {
        renderHud = value;
    }

    public static boolean isBoxRenderingEnabled() {
        return renderBoxes;
    }
    public static void setBoxRenderingEnabled(boolean value) {
        renderBoxes = value;
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
                if (renderBoxes) {
                    markerRenderer.renderMarker(nbt, camera, matrices, vertices);
                }
            }
            if (entity instanceof DisplayEntity.ItemDisplayEntity displayEntity) {
                displayEntity.setGlowing(true);
            }
            if (entity instanceof ItemEntity itemEntity) {
                itemMarkerRenderer.renderMarkerForItem(itemEntity, camera, matrices, vertices);
            }
        }
    }
}