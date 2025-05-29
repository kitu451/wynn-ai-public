package net.natga999.wynn_ai.path;

import net.natga999.wynn_ai.path.network.RoadNetworkManager;
import net.natga999.wynn_ai.path.network.RoadNode;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LongDistancePathPlanner {
    private static final Logger LOGGER = LoggerFactory.getLogger(LongDistancePathPlanner.class);
    private static final LongDistancePathPlanner INSTANCE = new LongDistancePathPlanner();

    private final RoadNetworkManager roadNetworkManager;
    private static final double MAX_DIST_TO_ROAD_NODE = 150.0; // Max distance to consider a road node "close"
    private static final int LOCAL_PATHFINDER_RANGE = 100; // Max block range for local PathFinder

    private LongDistancePathPlanner() {
        this.roadNetworkManager = RoadNetworkManager.getInstance();
    }

    public static LongDistancePathPlanner getInstance() {
        return INSTANCE;
    }

    public List<Vec3d> planPathToGoal(Vec3d startPosition, Vec3d goalPosition, ClientWorld world) {
        if (world == null || startPosition == null || goalPosition == null) {
            LOGGER.warn("Cannot plan path: null world, start, or goal position.");
            return null;
        }
        String worldId = world.getRegistryKey().getValue().toString();

        RoadNode startRoadNode = roadNetworkManager.findClosestNode(startPosition, worldId, MAX_DIST_TO_ROAD_NODE);
        RoadNode goalRoadNode = roadNetworkManager.findClosestNode(goalPosition, worldId, MAX_DIST_TO_ROAD_NODE);

        List<Vec3d> finalWaypoints = new ArrayList<>();

        // Case 1: Direct local path (no road nodes involved or accessible)
        if ((startRoadNode == null && goalRoadNode == null) ||
                (startPosition.distanceTo(goalPosition) < LOCAL_PATHFINDER_RANGE * 0.5)) { // Heuristic: if very close, just do local
            LOGGER.info("Planning direct local path.");
            List<Vec3d> localPath = planLocalPath(startPosition, goalPosition, world, LOCAL_PATHFINDER_RANGE * 2);
            if (localPath != null) {
                appendSegmentAvoidingDuplicates(finalWaypoints, localPath);
            }
            return finalWaypoints.isEmpty() ? null : finalWaypoints;
        }

        // --- Attempt to use road network ---
        List<Vec3d> segmentA;
        if (startRoadNode != null) {
            segmentA = planLocalPath(startPosition, startRoadNode.getPosition(), world, LOCAL_PATHFINDER_RANGE);
            if (segmentA == null) {
                LOGGER.warn("Failed to plan local path from player to start road node: {}. Attempting direct path to goal.", startRoadNode.getId());
                // Fallback to direct path if we can't even reach the first road node
                List<Vec3d> directFallback = planLocalPath(startPosition, goalPosition, world, LOCAL_PATHFINDER_RANGE * 3);
                if (directFallback != null) appendSegmentAvoidingDuplicates(finalWaypoints, directFallback);
                return finalWaypoints.isEmpty() ? null : finalWaypoints;
            }
        } else {
            LOGGER.info("Start position is too far from any road node. Attempting direct path to goal.");
            List<Vec3d> directFallback = planLocalPath(startPosition, goalPosition, world, LOCAL_PATHFINDER_RANGE * 3);
            if (directFallback != null) appendSegmentAvoidingDuplicates(finalWaypoints, directFallback);
            return finalWaypoints.isEmpty() ? null : finalWaypoints;
        }

        // At this point, segmentA should exist IF startRoadNode was valid and pathable.
        appendSegmentAvoidingDuplicates(finalWaypoints, segmentA);

        List<Vec3d> networkSegmentWaypoints = new ArrayList<>();
        if (goalRoadNode != null) {
            if (!startRoadNode.getId().equals(goalRoadNode.getId())) {
                List<RoadNode> roadNodeHops = roadNetworkManager.findPathOnRoadNetwork(startRoadNode.getId(), goalRoadNode.getId());
                if (roadNodeHops != null && !roadNodeHops.isEmpty()) {
                    roadNodeHops.forEach(node -> {
                        if (node.getPosition() != null) networkSegmentWaypoints.add(node.getPosition());
                    });
                    LOGGER.info("Using road network path between {} and {}.", startRoadNode.getId(), goalRoadNode.getId());
                } else {
                    LOGGER.warn("Failed to find path on road network. Attempting local path between road nodes as fallback.");
                    List<Vec3d> directBetweenNodes = planLocalPath(startRoadNode.getPosition(), goalRoadNode.getPosition(), world, LOCAL_PATHFINDER_RANGE * 2);
                    if (directBetweenNodes != null) networkSegmentWaypoints.addAll(directBetweenNodes);
                }
            } else {
                LOGGER.info("Start and goal road nodes are the same: {}. No highway segment needed.", startRoadNode.getId());
                // networkSegmentWaypoints remains empty, but we might add goalRoadNode.getPosition()
                // if it wasn't the last point of segmentA.
                // appendSegmentAvoidingDuplicates will handle this if startRoadNode.getPosition() is added.
                // For clarity, explicitly add if segmentA's last point isn't already it.
                if (startRoadNode.getPosition() != null) {
                    networkSegmentWaypoints.add(startRoadNode.getPosition());
                }
            }
        } else { // Start on network, goal off network
            LOGGER.warn("Goal is off-network. Path will end at startRoadNode ({}), then attempt direct to final goal.", startRoadNode.getId());
            // No actual network segment. The next segment (C) will try to go from startRoadNode.getPosition() to goalPosition
        }
        // (Similar handling if startRoadNode is null and goalRoadNode is not, though earlier logic might preempt this)

        appendSegmentAvoidingDuplicates(finalWaypoints, networkSegmentWaypoints);

        List<Vec3d> segmentC = null;
        // If goalRoadNode exists, path from it. Otherwise, path from startRoadNode (if goal is off-network),
        // or from playerStartPos if no network was involved at all (covered by Case 1).
        Vec3d segmentC_startPoint = goalRoadNode != null ? goalRoadNode.getPosition() :
                startRoadNode.getPosition();

        if (segmentC_startPoint != null && segmentC_startPoint.distanceTo(goalPosition) > 1.0) { // Only if not already at goal
            segmentC = planLocalPath(segmentC_startPoint, goalPosition, world, LOCAL_PATHFINDER_RANGE);
            if (segmentC == null) {
                LOGGER.warn("Failed to plan local path from last network point/goal_RN to final goal. Path might be incomplete.");
            }
        }
        appendSegmentAvoidingDuplicates(finalWaypoints, segmentC);

        if (finalWaypoints.isEmpty() || (finalWaypoints.size() == 1 && finalWaypoints.getFirst().equals(startPosition))) {
            LOGGER.warn("Final path is empty or only contains start position. Planning failed.");
            return null;
        }

        LOGGER.info("Successfully planned long-distance path with {} distinct waypoints.", finalWaypoints.size());
        return finalWaypoints;
    }

    private List<Vec3d> planLocalPath(Vec3d localStart, Vec3d localGoal, ClientWorld world, int maxRange) {
        if (localStart.distanceTo(localGoal) < 1.5) { // Already there or very close
            return new ArrayList<>(Collections.singletonList(localGoal)); // Path to just the goal
        }

        // Adjust start/goal for PathFinder (e.g., HarvestPathManager's adjustPlayerStartPosition)
        BlockPos startBlock = BlockPos.ofFloored(localStart); // Simplified, adapt as per your needs
        BlockPos goalBlock = BlockPos.ofFloored(localGoal);

        PathFinder pf = new PathFinder(world, maxRange, startBlock, goalBlock); // Max range for local segments
        List<Vec3d> path = pf.findPath(startBlock, goalBlock);

        // Ensure first point is actual start, last is actual goal if PathFinder uses block centers
        if (path == null || path.isEmpty()) {
            LOGGER.warn("Local PathFinder failed: {} -> {}", localStart, localGoal);
            return null;
        }

        // PathFinder usually uses player's current block pos, so this might be okay.
        // path.set(0, localStart); // Careful with this, might break pathfinder's assumptions
        path.set(path.size() - 1, localGoal); // Ensure exact goal

        return path;
    }

    /**
     * Appends points from segmentPath to mainPath, ensuring the first point of
     * segmentPath is not a duplicate of the last point of mainPath.
     * It also filters consecutive duplicates within segmentPath itself before adding.
     *
     * @param mainPath The primary list of waypoints (will be modified).
     * @param segmentPath The segment of waypoints to append.
     */
    private void appendSegmentAvoidingDuplicates(List<Vec3d> mainPath, List<Vec3d> segmentPath) {
        if (segmentPath == null || segmentPath.isEmpty()) {
            return;
        }

        Vec3d previousPointAddedFromSegment = mainPath.isEmpty() ? null : mainPath.getLast(); // Initialize with the last point of mainPath

        for (Vec3d currentPointInSegment : segmentPath) {
            if (currentPointInSegment == null) continue; // Skip null points in segment

            // Check against the last point actually added (either from mainPath or previous from segmentPath)
            if (previousPointAddedFromSegment == null || !previousPointAddedFromSegment.equals(currentPointInSegment)) {
                mainPath.add(currentPointInSegment);
                previousPointAddedFromSegment = currentPointInSegment; // Update the last successfully added point
            } else {
                LOGGER.debug("Skipping duplicate consecutive waypoint during segment append: {}", currentPointInSegment);
            }
        }
    }
}