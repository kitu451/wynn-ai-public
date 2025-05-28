package net.natga999.wynn_ai;

import net.natga999.wynn_ai.commands.NbtInfoCommand;
import net.natga999.wynn_ai.commands.RoadNodeCommandRegistry;
import net.natga999.wynn_ai.detector.EntityDetector;
import net.natga999.wynn_ai.input.MouseInputHandler;
import net.natga999.wynn_ai.input.KeyInputHandler;
import net.natga999.wynn_ai.managers.*;
import net.natga999.wynn_ai.ai.BasicPathAI;
import net.natga999.wynn_ai.managers.combat.CombatManager;
import net.natga999.wynn_ai.render.PathRenderer;
import net.natga999.wynn_ai.render.RoadNetworkRenderer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

import net.minecraft.entity.Entity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

public class WynnAIClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(WynnAIClient.class);

    // Static reference to the current instance
    private static WynnAIClient INSTANCE;

    private static int detectionRadius = 16; // Radius to detect entities
    private EntityDetector entityDetector;

    // Cache to store nearby entities
    private List<Entity> cachedNearbyEntities = Collections.emptyList();

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initialized Client");
        INSTANCE = this;

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            RoadNodeCommandRegistry.register(dispatcher); // Single line to register all road node commands
            NbtInfoCommand.register(dispatcher);
        });

        entityDetector = new EntityDetector(detectionRadius);

        // Initialize renderer managers
        RenderManager.init();
        EntityOutlinerManager.init();

        ResourceNodeManager.loadFromFile();

        // Register key bindings
        KeyInputHandler.register();

        //new registerMouseMovement();
        new MouseInputHandler();

        // Register HUD rendering
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            updateCachedNearbyEntities(client);
            RenderManager.getInstance().renderEntityHud(drawContext, client, cachedNearbyEntities);
            RenderManager.getInstance().renderMenuWithName(drawContext, client, "MainMenu");
        });

        // Register rendering event callback
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            // Main world rendering logic
            MinecraftClient client = MinecraftClient.getInstance();
            updateCachedNearbyEntities(client); // Update the cache once
            renderDetectedNearbyEntitiesBox(context);
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            List<Vec3d> path = HarvestPathManager.getInstance().getCurrentPath();
            if (path == null || path.size() < 2) {
                LOGGER.debug("Path is null or too short to render: {}", path);
            } else {
                PathRenderer.renderPath(context.matrixStack(), context.camera().getPos(), path);
            }

            List<Vec3d> combatPath = CombatManager.getInstance().getCurrentPath();
            if (combatPath == null || combatPath.size() < 2) {
                LOGGER.debug("Path is null or too short to render: {}", combatPath);
            } else {
                PathRenderer.renderPath(context.matrixStack(), context.camera().getPos(), combatPath);
            }

            if (MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().world != null) {
                RoadNetworkRenderer.render(context.matrixStack(), context.camera());
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // Run AI movement logic
            RepairStateManager.getInstance().tick(client);
            CombatManager.getInstance().tick();
            HarvestPathManager.getInstance().tick();

            BasicPathAI.getInstance().tick();
        });
    }

    public static List<Entity> getCachedNearbyEntities() {
        return INSTANCE.cachedNearbyEntities;
    }

    // Setter for detection radius with validation
    public static void setDetectionRadius(int radius) {
        LOGGER.info("Updating detection radius to: {}", radius);
        detectionRadius = radius;

        if (INSTANCE != null && INSTANCE.entityDetector != null) {
            INSTANCE.entityDetector.updateDetectionRadius(detectionRadius);
        }
    }

    // Getter for detection radius
    public static int getDetectionRadius() {
        return detectionRadius;
    }

    private void updateCachedNearbyEntities(MinecraftClient client) {
        assert client.player != null;
        Vec3d playerPos = client.player.getPos();
        // Detect nearby entities and cache the result
        cachedNearbyEntities = entityDetector.detectNearbyEntities(playerPos, client);
    }

    private void renderDetectedNearbyEntitiesBox(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        RenderManager.getInstance().renderEntityBoxes(context, cachedNearbyEntities);
    }
}