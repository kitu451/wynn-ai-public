package net.natga999.wynn_ai.strategies;

import net.natga999.wynn_ai.ai.BasicPathAI;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeneralPurposeTravelStrategy implements MovementStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeneralPurposeTravelStrategy.class);

    @Override
    public void tick(BasicPathAI ai) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            ai.stop();
            return;
        }
        ClientPlayerEntity player = client.player;
        Vec3d currentWaypoint = ai.getCurrentWaypoint();

        if (currentWaypoint == null) {
            // Path might be complete, or an issue occurred.
            // isComplete will handle stopping if truly done.
            LOGGER.debug("GeneralPurposeTravelStrategy: No current waypoint.");
            return;
        }

        ai.updateMovementToward(currentWaypoint, client); // Assumes BasicPathAI handles jump checks

        if (ai.isReachedNext(player)) {
            ai.incrementCurrentPathIndex();
            if (ai.getCurrentWaypoint() == null) {
                LOGGER.info("GeneralPurposeTravelStrategy: Reached end of path.");
                // isComplete will trigger stop
            } else {
                LOGGER.debug("GeneralPurposeTravelStrategy: Advanced to waypoint {}", ai.getCurrentIndex());
            }
        }
    }

    @Override
    public void handleCameraRotation(BasicPathAI ai, MinecraftClient client) {
        Vec3d waypoint = ai.getCurrentWaypoint();
        if (waypoint != null && client.player != null) {
            // Use the final=false version for smooth turning
            BasicPathAI.getInstance().rotateCameraToward(client, false);
        }
    }

    @Override
    public boolean isComplete(BasicPathAI ai) {
        // Complete if there was a path, and we've processed all waypoints
        boolean pathWasSet = ai.getPathSize() > 0;
        boolean allWaypointsReached = ai.getCurrentIndex() >= ai.getPathSize();
        return pathWasSet && allWaypointsReached;
    }

    @Override
    public void onStop(BasicPathAI ai) {
        LOGGER.info("GeneralPurposeTravelStrategy: Path stopped.");
        // Any specific cleanup for this strategy can go here.
    }
}