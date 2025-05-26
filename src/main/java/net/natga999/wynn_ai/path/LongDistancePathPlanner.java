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

        // Case 1: No road nodes involved (either too far, or direct path is short enough)
        if (startRoadNode == null && goalRoadNode == null) {
            LOGGER.info("Planning direct local path (no road nodes accessible/needed).");
            return planLocalPath(startPosition, goalPosition, world, LOCAL_PATHFINDER_RANGE * 2); // Allow longer range for full direct
        }

        // Case 2: Only start is near a road node
        if (startRoadNode != null && goalRoadNode == null) {
            LOGGER.info("Planning path: Start near road node ({}), goal is off-network.", startRoadNode.getId());
            List<Vec3d> pathToNode = planLocalPath(startPosition, startRoadNode.getPosition(), world, LOCAL_PATHFINDER_RANGE);
            if (pathToNode == null) return null; // Cannot reach start node
            finalWaypoints.addAll(pathToNode);
            // No direct way to connect to goal from here without a road network path or a very long local path
            LOGGER.warn("Goal is too far from road network and start node cannot reach it via network.");
            return null; // Or attempt a very long direct path as a last resort if desired
        }

        // Case 3: Only goal is near a road node
        if (startRoadNode == null && goalRoadNode != null) {
            LOGGER.info("Planning path: Start is off-network, goal near road node ({}).", goalRoadNode.getId());
            // Similar logic, may not be useful if start cannot connect to any road node.
            // For simplicity, might require both to be connectable to network if using network.
            LOGGER.warn("Start is too far from road network.");
            return null;
        }

        // Case 4: Both start and goal are near road nodes (startRoadNode != null && goalRoadNode != null)
        LOGGER.info("Planning path via road network: {} -> {}", startRoadNode.getId(), goalRoadNode.getId());

        // Segment A: Player to Start Road Node
        List<Vec3d> segmentA = planLocalPath(startPosition, startRoadNode.getPosition(), world, LOCAL_PATHFINDER_RANGE);
        if (segmentA == null) {
            LOGGER.warn("Failed to plan local path from player to start road node: {}", startRoadNode.getId());
            return null;
        }
        finalWaypoints.addAll(segmentA);

        // Segment B: Road Network Path
        if (!startRoadNode.getId().equals(goalRoadNode.getId())) {
            List<RoadNode> roadNodeHops = roadNetworkManager.findPathOnRoadNetwork(startRoadNode.getId(), goalRoadNode.getId());
            if (roadNodeHops == null || roadNodeHops.isEmpty()) {
                LOGGER.warn("Failed to find path on road network between {} and {}", startRoadNode.getId(), goalRoadNode.getId());
                // Fallback: try direct local path between the two road nodes if they are somewhat close
                List<Vec3d> directBetweenNodes = planLocalPath(startRoadNode.getPosition(), goalRoadNode.getPosition(), world, LOCAL_PATHFINDER_RANGE * 2);
                if (directBetweenNodes != null) {
                    LOGGER.info("Using direct local path as fallback between road nodes.");
                    ensureNoDuplicateConsecutive(finalWaypoints, directBetweenNodes.getFirst());
                    finalWaypoints.addAll(directBetweenNodes);
                } else {
                    return null; // True failure
                }
            } else {
                // Add positions from roadNodeHops, skipping the first if it's same as startRoadNode
                List<Vec3d> networkSegment = roadNodeHops.stream()
                        .map(RoadNode::getPosition)
                        .toList();

                if (!networkSegment.isEmpty()) {
                    ensureNoDuplicateConsecutive(finalWaypoints, networkSegment.getFirst());
                    finalWaypoints.addAll(networkSegment);
                }
            }
        } else {
            LOGGER.info("Start and goal road nodes are the same: {}", startRoadNode.getId());
        }


        // Segment C: Goal Road Node to Final Goal
        List<Vec3d> segmentC = planLocalPath(goalRoadNode.getPosition(), goalPosition, world, LOCAL_PATHFINDER_RANGE);
        if (segmentC == null) {
            LOGGER.warn("Failed to plan local path from goal road node {} to final goal.", goalRoadNode.getId());
            return null; // Or maybe return path to goalRoadNode if that's acceptable
        }
        if (!segmentC.isEmpty()) {
            ensureNoDuplicateConsecutive(finalWaypoints, segmentC.getFirst());
            finalWaypoints.addAll(segmentC);
        }


        if (finalWaypoints.isEmpty()) return null;

        // Optional: Apply spline to the whole path or segments
        // For now, returning raw waypoints. Consider spline application for smoother movement.
        // Example: return CatmullRomSpline.createSpline(finalWaypoints, calculateSegmentCount(finalWaypoints));

        LOGGER.info("Successfully planned long-distance path with {} waypoints.", finalWaypoints.size());
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

        if (path == null || path.isEmpty()) {
            LOGGER.warn("Local PathFinder failed: {} -> {}", localStart, localGoal);
            return null;
        }

        // Ensure first point is actual start, last is actual goal if PathFinder uses block centers
        if (!path.isEmpty()) {
            // PathFinder usually uses player's current block pos, so this might be okay.
            // path.set(0, localStart); // Careful with this, might break pathfinder's assumptions
            path.set(path.size() - 1, localGoal); // Ensure exact goal
        }
        return path;
    }

    private void ensureNoDuplicateConsecutive(List<Vec3d> mainPath, Vec3d pointToAdd) {
        if (mainPath.isEmpty() || !mainPath.getLast().equals(pointToAdd)) {
            // Okay to add
        } else {
            LOGGER.trace("Skipping duplicate consecutive waypoint: {}", pointToAdd);
            // If we don't add it, and the next list starts with it, we need to skip that too.
            // This logic is tricky. Simpler might be to allow duplicates and have BasicPathAI handle them,
            // or to build segments and then merge, removing duplicates at merge points.
            // For now, this simple check might lead to missing the first point of a subsequent segment
            // if it was identical to the last point of the previous one.
            // A robust solution would be to assemble all segments, then filter.
        }
    }


    // Example: From HarvestPathManager, adapt as needed
    private int calculateSegmentCount(List<Vec3d> path) {
        return path.size() <= 3 ? 16 : (path.size() * 2); // More segments for longer paths
    }
}