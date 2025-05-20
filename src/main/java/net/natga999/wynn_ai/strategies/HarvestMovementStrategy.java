package net.natga999.wynn_ai.strategies;

import net.natga999.wynn_ai.ai.BasicPathAI;
import net.natga999.wynn_ai.managers.HarvestPathManager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class HarvestMovementStrategy implements MovementStrategy {
    private final HarvestPathManager harvest = HarvestPathManager.getInstance();
    private final Random random = new Random();

    @Override
    public void tick(BasicPathAI ai) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;

        if (player == null) return;

        // Get the current waypoint
        Vec3d waypoint = ai.getCurrentWaypoint();
        if (waypoint == null) return;

        // Move toward the current waypoint
        ai.updateMovementToward(waypoint, client);

        // Check if we've reached the final destination with random factor
        if (ai.getCurrentIndex() >= ai.getPathSize() - 1 && !harvest.isPathComplete()) {
            Vec3d playerPos = player.getPos();
            double distanceXZ = Math.sqrt(
                    Math.pow(playerPos.x - waypoint.x, 2) +
                            Math.pow(playerPos.z - waypoint.z, 2)
            );
            double distanceY = Math.abs(playerPos.y - waypoint.y);

            // Apply random factor to distance check for more natural behavior
            double randomFactor = 0.9 + random.nextDouble() * (1.6 - 0.9);
            if (distanceXZ < randomFactor && distanceY < 1.0) {
                if (HarvestPathManager
                        .getOriginalGoalPos() != null) {
                    Vec3d goalPos = Vec3d.of(HarvestPathManager
                            .getOriginalGoalPos().add(0, 1, 0));
                    BasicPathAI.rotateCameraToward(goalPos, client, true);
                }
                harvest.setPathComplete(true);
                ai.stop();
                return;
            }
        }

        // Check if we've reached the current waypoint
        if (ai.isReachedNext(player)) {
            ai.incrementCurrentPathIndex();

            // Check if we're at the end of the path
            if (ai.getCurrentIndex() >= ai.getPathSize() && !harvest.isPathComplete()) {
                // Final rotation to face the original goal
                if (HarvestPathManager
                        .getOriginalGoalPos() != null) {
                    Vec3d goalPos = new Vec3d(
                            HarvestPathManager
                                    .getOriginalGoalPos().getX() + 0.5,
                            HarvestPathManager
                                    .getOriginalGoalPos().getY() + 0.5,
                            HarvestPathManager
                                    .getOriginalGoalPos().getZ() + 0.5
                    );
                    BasicPathAI.rotateCameraToward(goalPos, client, true);
                }

                // Mark the path as complete
                harvest.setPathComplete(true);
                ai.stop();
            }
        }
    }

    @Override
    public boolean isComplete(BasicPathAI ai) {
        // mirror your existing conditions that end the harvest path
        return harvest.isPathComplete()
                || ai.getCurrentIndex() >= ai.getPathSize();
    }

    @Override
    public void onStop(BasicPathAI ai) {
        // any special cleanup for harvest
    }

    @Override
    public void handleCameraRotation(BasicPathAI ai, MinecraftClient client) {
        if (client.player == null) return;

        Vec3d target = ai.getCurrentWaypoint();
        if (target != null) {
            BasicPathAI.rotateCameraToward(
                    target.subtract(0, 1, 0),
                    client,
                    false
            );
        }
    }
}