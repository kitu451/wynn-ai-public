package net.natga999.wynn_ai.strategies;

import net.natga999.wynn_ai.ai.BasicPathAI;
import net.natga999.wynn_ai.managers.combat.CombatManager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public class CombatMovementStrategy implements MovementStrategy {
    private final CombatManager combat = CombatManager.getInstance();
    private final double reachThresholdXZ = 1.0;
    private final double reachThresholdY = 1.3;

    @Override
    public void tick(BasicPathAI ai) {
        if (combat.isInCombat()) {
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayerEntity player = client.player;

            if (player == null) return;

            // Check if we have a valid path to follow
            if (ai.getCurrentIndex() >= ai.getPathSize() || ai.getCurrentWaypoint() == null) {
                return;
            }

            Vec3d currentTarget = ai.getCurrentWaypoint();

            // Use a slightly lower aim point to avoid looking up too much
            Vec3d aimPoint = currentTarget.subtract(0, 1.0, 0);
            ai.updateMovementToward(aimPoint, client);

            // Calculate horizontal (XZ) and vertical (Y) distances separately
            double distanceXZ = Math.sqrt(
                    Math.pow(player.getX() - currentTarget.x, 2) +
                            Math.pow(player.getZ() - currentTarget.z, 2)
            );
            double distanceY = Math.abs(player.getY() - currentTarget.y);

            // Check if we've reached the current waypoint
            boolean reachedCurrent = distanceXZ < reachThresholdXZ && distanceY < reachThresholdY;
            boolean reachedNext = ai.isReachedNext(player);

            // Decide how far to advance
            if (reachedNext) {
                // Skip current and go straight to the one after next
                ai.incrementCurrentPathIndex();
                ai.incrementCurrentPathIndex();
            } else if (reachedCurrent) {
                // Normal single-step advance
                ai.incrementCurrentPathIndex();
            }

            // Check for jumping over obstacles
            //ai.checkAndJump(client);
        }
    }

    @Override
    public boolean isComplete(BasicPathAI ai) {
        // Combat movement is complete when either:
        // 1. We're no longer in combat
        // 2. We've reached the end of the path
        return !combat.isInCombat() || ai.getCurrentIndex() >= ai.getPathSize();
    }

    @Override
    public void onStop(BasicPathAI ai) {
        // Any specific cleanup needed when combat ends
    }

    @Override
    public void handleCameraRotation(BasicPathAI ai, MinecraftClient client) {
        if (client.player == null) return;

        Vec3d target = CombatManager.getInstance().getTargetPos();
        if (target != null) {
            if (CombatManager.getInstance().isInAttackRange()) {
                CombatManager.rotateCameraToward(
                        target.add(0, 1, 0),
                        client
                );
            } else {
                target = ai.getCurrentWaypoint();
                if (target != null) {
                    BasicPathAI.rotateCameraToward(
                            target,
                            client,
                            false
                    );
                }
            }
        }
    }
}