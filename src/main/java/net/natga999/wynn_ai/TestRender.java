package net.natga999.wynn_ai;

import net.natga999.wynn_ai.detector.EntityDetector;
import net.natga999.wynn_ai.render.BoxMarkerRenderer;
import net.natga999.wynn_ai.render.MarkerRenderer;
import net.natga999.wynn_ai.render.RenderHUD;

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

import java.util.Collections;
import java.util.List;

public class TestRender implements ClientModInitializer {

    private static final int detectionRadius = 15; // Radius to detect entities
    private final EntityDetector entityDetector = new EntityDetector(detectionRadius);
    private final MarkerRenderer markerRenderer = new BoxMarkerRenderer();
    private final RenderHUD renderHUD = new RenderHUD();

    // Cache to store nearby entities
    private List<Entity> cachedNearbyEntities = Collections.emptyList();

    @Override
    public void onInitializeClient() {
        // Register rendering event callback
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            // Main world rendering logic
            MinecraftClient client = MinecraftClient.getInstance();
            updateCachedNearbyEntities(client); // Update the cache once
            renderDetectedNearbyEntitiesBox(context, client);
        });
        // Register a HUD callback to display detected entities
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> renderDetectedEntitiesOnHud(drawContext));
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
        for (Entity entity : cachedNearbyEntities) {
                if (entity instanceof DisplayEntity.TextDisplayEntity displayEntity) {
                    NbtCompound nbt = displayEntity.writeNbt(new NbtCompound());
                    markerRenderer.renderMarker(nbt, context.camera(), context.matrixStack(), client.getBufferBuilders().getEntityVertexConsumers());
                }
        }
    }
}