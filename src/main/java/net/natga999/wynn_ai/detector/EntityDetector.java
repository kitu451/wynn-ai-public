package net.natga999.wynn_ai.detector;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class EntityDetector {

    private int detectionRadius;

    public EntityDetector(int detectionRadius) {
        this.detectionRadius = detectionRadius;
    }

    // method to update the detection radius
    public void updateDetectionRadius(int radius) {
        this.detectionRadius = radius;
    }

    public List<Entity> detectNearbyEntities(Vec3d playerPosition, MinecraftClient client) {
        List<Entity> detectedEntities = new ArrayList<>();
        assert client.world != null;
        for (Entity entity : client.world.getEntities()) { // Iterate through all entities
            if (entity.getPos().isInRange(playerPosition, detectionRadius)) { // Check if inside radius
                detectedEntities.add(entity);
            }
        }
        return detectedEntities;
    }
}