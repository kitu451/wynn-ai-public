package net.natga999.wynn_ai.strategies;

import net.natga999.wynn_ai.ai.BasicPathAI;
import net.natga999.wynn_ai.ai.CombatController;
import net.natga999.wynn_ai.managers.combat.CombatManager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public class CombatMovementStrategy implements MovementStrategy {
    private final CombatManager combat = CombatManager.getInstance();
    private final CombatController combatController = CombatController.getInstance();

    @Override
    public void tick(BasicPathAI ai) {
        if (combat.isInCombat()) {
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayerEntity player = client.player;

            if (player == null) return;

            // Let the combat controller manage the paths and movement plans
            combatController.tick(client, ai);

            // Only handle the actual movement if we have a valid path
            if (ai.getCurrentIndex() < ai.getPathSize() && ai.getCurrentWaypoint() != null) {
                Vec3d currentTarget = ai.getCurrentWaypoint();

                // Use a slightly lower aim point to avoid looking up too much when moving
                Vec3d aimPoint = currentTarget.subtract(0, 0.5, 0);
                ai.updateMovementToward(aimPoint, client);

                // Let the controller determine if we've reached waypoints
                if (combatController.hasReachedWaypoint(player, currentTarget)) {
                    ai.incrementCurrentPathIndex();
                }

                // Check if we're overshooting and should skip waypoints
                if (combatController.shouldSkipWaypoint(player, ai)) {
                    ai.incrementCurrentPathIndex();
                }
            }
        }
    }

    @Override
    public boolean isComplete(BasicPathAI ai) {
        // Combat movement is complete when either:
        // 1. We're no longer in combat
        // 2. We've reached the end of the path
        // 3. The combat controller signals completion
        return !combat.isInCombat() ||
                ai.getCurrentIndex() >= ai.getPathSize() ||
                combatController.isCurrentActionComplete();
    }

    @Override
    public void onStop(BasicPathAI ai) {
        // Notify the combat controller that we're stopping
        combatController.onMovementStopped();
    }

    @Override
    public void handleCameraRotation(BasicPathAI ai, MinecraftClient client) {
        if (client.player == null) return;

        // Delegate camera rotation to the combat controller
        combatController.handleCameraRotation(client, ai);
    }
}