package net.natga999.wynn_ai.strategies;

import net.natga999.wynn_ai.ai.BasicPathAI;
import net.natga999.wynn_ai.path.network.RoadNode;
import net.natga999.wynn_ai.utility.CatmullRomSpline;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class HighwaySplineStrategy implements MovementStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(HighwaySplineStrategy.class);

    private final List<RoadNode> fullHighwayNodePath;
    private int currentHighwayNodeProgressIndex; // Which node in fullHighwayNodePath is the start of the current spline segment's focus

    private static final int SPLINE_CONTROL_POINTS_LOOKAHEAD = 4; // How many RoadNodes to consider for generating one spline segment (e.g., P0, P1, P2, P3 for spline between P1-P2)
    private static final int CATMULL_SEGMENTS_PER_NODE_PAIR = 8; // Detail level for CatmullRomSpline.createSpline

    boolean pathSegmentInProgress;

    private boolean isOverallHighwayPathComplete = false;

    public HighwaySplineStrategy(List<RoadNode> highwayNodes) {
        if (highwayNodes == null || highwayNodes.size() < 2) {
            LOGGER.debug("HighwaySplineStrategy initialized with insufficient highway nodes ({}). Pathing will likely fail.", highwayNodes != null ? highwayNodes.size() : "null");
            this.fullHighwayNodePath = new ArrayList<>(); // Avoid NPEs
            this.isOverallHighwayPathComplete = true; // Mark as complete to prevent issues
        } else {
            this.fullHighwayNodePath = new ArrayList<>(highwayNodes);
        }
        this.currentHighwayNodeProgressIndex = 0;
        LOGGER.info("HighwaySplineStrategy initialized with {} total highway nodes.", this.fullHighwayNodePath.size());
    }

    @Override
    public void tick(BasicPathAI ai) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || isOverallHighwayPathComplete) {
            if (isOverallHighwayPathComplete && ai.getCurrentWaypoint() == null && pathSegmentInProgress) {
                // This case indicates the last segment was set, but AI finished, and overall is complete.
                LOGGER.info("HighwaySplineStrategy: Overall path complete and AI finished its last segment.");
                pathSegmentInProgress = false; // Clear the flag
            } else if (isOverallHighwayPathComplete && ai.getCurrentWaypoint() != null) {
                // Still processing the very last spline segment.
                ai.updateMovementToward(ai.getCurrentWaypoint(), client);
                if (ai.isReachedNext(client.player)) {
                    ai.incrementCurrentPathIndex();
                    if (ai.getCurrentWaypoint() == null) pathSegmentInProgress = false; // Reached end of last segment
                }
            } else if (!isOverallHighwayPathComplete && (client == null || client.player == null)) {
                LOGGER.warn("HighwaySplineStrategy: Exiting tick early due to null client or player.");
            }
            return;
        }

        // If BasicPathAI has finished its current list of spline points (or it's the first tick)
        if (ai.getCurrentWaypoint() == null) {
            // If a segment was just completed, currentHighwayNodeProgressIndex still points to the *start* of that completed segment.
            // We need to advance it *before* generating the next one.
            if (currentHighwayNodeProgressIndex > 0 || (currentHighwayNodeProgressIndex == 0 && ai.getPathSize() > 0 /* hacky: check if a path was actually set and completed for index 0 */)) {
                // This condition is tricky. We need to know if ai.getCurrentWaypoint() is null because
                // a *previous* segment just finished, or if it's the *very first* call.
                // Let's assume if a path was set (ai.getPathSize() > 0) and now currentWaypoint is null,
                // the segment is done.
                // A flag like "isNewStrategyInstance" could help here.
                // For simplicity, if path is not empty, it means a segment was running.
                if (ai.getPathSize() > 0) { // This implies a segment was just completed
                    LOGGER.debug("HighwaySplineStrategy: Segment starting at node index {} completed.", currentHighwayNodeProgressIndex);
                    currentHighwayNodeProgressIndex++; // Advance to the next node, which will be P1 for the next segment
                }
            }
            if (currentHighwayNodeProgressIndex >= fullHighwayNodePath.size()) {
                isOverallHighwayPathComplete = true;
                LOGGER.info("HighwaySplineStrategy: All highway nodes processed. Marking overall path as complete. Index: {}", currentHighwayNodeProgressIndex);
                return; // No more segments to generate
            }
            generateAndSetNextPathSegment(ai);
        }

        // If, after attempting to generate a segment, we still have no waypoint, something is wrong or path is done.
        if (ai.getCurrentWaypoint() == null) {
            if (!isOverallHighwayPathComplete) { // Only log error if we weren't expecting to be done
                LOGGER.warn("HighwaySplineStrategy: No current waypoint in BasicPathAI after attempting segment generation. currentHighwayNodeIndex: {}", currentHighwayNodeProgressIndex);
                isOverallHighwayPathComplete = true; // Prevent infinite loop if generation fails
            }
            return;
        }

        ai.updateMovementToward(ai.getCurrentWaypoint(), client);

        if (ai.isReachedNext(client.player)) {
            ai.incrementCurrentPathIndex();
            // Log progress within the current spline segment if needed
            // LOGGER.debug("HighwaySplineStrategy: Reached spline point {}/{}", ai.getCurrentIndex(), ai.getPathSize());
        }
    }

    private void generateAndSetNextPathSegment(BasicPathAI ai) {
        if (currentHighwayNodeProgressIndex >= fullHighwayNodePath.size()) {
            LOGGER.info("generateAndSetNextPathSegment: All nodes processed or index out of bounds (index {} >= size {}). Marking complete.",
                    currentHighwayNodeProgressIndex, fullHighwayNodePath.size());
            isOverallHighwayPathComplete = true;
            ai.setPath(new ArrayList<>());
            pathSegmentInProgress = false; // Ensure flag is cleared
            return;
        }

        RoadNode p1_startOfCurrentSegment = fullHighwayNodePath.get(currentHighwayNodeProgressIndex);

        if (currentHighwayNodeProgressIndex >= fullHighwayNodePath.size() - 1) {
            LOGGER.info("generateAndSetNextPathSegment: At the last node ({}) of the highway path. No further segments to generate.", p1_startOfCurrentSegment.getId());
            isOverallHighwayPathComplete = true;
            ai.setPath(new ArrayList<>());
            pathSegmentInProgress = false; // Ensure flag is cleared
            return;
        }

        RoadNode p2_endOfCurrentSegment = fullHighwayNodePath.get(currentHighwayNodeProgressIndex + 1);

        // *** TUNNEL LOGIC ***
        if ("TUNNEL_ENTRANCE".equalsIgnoreCase(p1_startOfCurrentSegment.getType()) &&
                p1_startOfCurrentSegment.getTargetTunnelExitNodeId() != null &&
                p1_startOfCurrentSegment.getTargetTunnelExitNodeId().equals(p2_endOfCurrentSegment.getId())) {

            LOGGER.error("HighwaySplineStrategy: Segment is a tunnel from {} to {}.",
                    p1_startOfCurrentSegment.getId(), p2_endOfCurrentSegment.getId());

            if (p1_startOfCurrentSegment.getPosition() != null) {
                List<Vec3d> tunnelEntrancePath = List.of(p1_startOfCurrentSegment.getPosition());
                // *** DETAILED LOGGING FOR TUNNEL PATH ***
                LOGGER.error("TUNNEL PATH SET FOR BasicPathAI: Target Node ID: {}, Target Position: {}",
                        p1_startOfCurrentSegment.getId(), p1_startOfCurrentSegment.getPosition());
                for (int i = 0; i < tunnelEntrancePath.size(); i++) {
                    LOGGER.error("  Tunnel Path Waypoint [{}]: {}", i, tunnelEntrancePath.get(i));
                }
                // *** END DETAILED LOGGING ***
                ai.setPath(tunnelEntrancePath);
                pathSegmentInProgress = true;
            } else {
                LOGGER.error("Tunnel entrance {} has no position! Cannot path to it. Clearing AI path.", p1_startOfCurrentSegment.getId());
                ai.setPath(new ArrayList<>());
                pathSegmentInProgress = false;
                // To effectively skip this broken tunnel segment (entrance and its A*-planned exit):
                // currentHighwayNodeProgressIndex will be advanced by 1 in tick() because this segment "completes" (empty path).
                // We need to advance it one more time to skip P2 (the exit) as well. This is tricky here.
                // A better approach might be to mark this specific strategy instance as failed/error.
                // For now, let the tick() logic advance past P1. If P2 is also problematic, it will be handled.
            }
            // Note: currentHighwayNodeProgressIndex is NOT incremented here.
            // It's incremented in tick() after BasicPathAI completes this short path to the entrance.
            return;
        }

        // *** REGULAR SPLINE LOGIC ***
        if (fullHighwayNodePath.size() >= 2 && currentHighwayNodeProgressIndex == fullHighwayNodePath.size() - 2) {
            LOGGER.info("HighwaySplineStrategy: Processing final spline segment (from node {} to {}).",
                    p1_startOfCurrentSegment.getId(), p2_endOfCurrentSegment.getId());
        }

        List<RoadNode> segmentControlNodes = new ArrayList<>();
        for (int i = 0; i < SPLINE_CONTROL_POINTS_LOOKAHEAD; i++) {
            int nodeIndexInFullPath = currentHighwayNodeProgressIndex - 1 + i;
            if (nodeIndexInFullPath >= 0 && nodeIndexInFullPath < fullHighwayNodePath.size()) {
                segmentControlNodes.add(fullHighwayNodePath.get(nodeIndexInFullPath));
            }
        }

        if (segmentControlNodes.size() < 2) {
            LOGGER.info("generateAndSetNextPathSegment: Not enough actual control nodes ({}) for spline. HighwayNodeIndex: {}. Ending.",
                    segmentControlNodes.size(), currentHighwayNodeProgressIndex);
            isOverallHighwayPathComplete = true;
            ai.setPath(new ArrayList<>());
            pathSegmentInProgress = false;
            return;
        }

        List<Vec3d> controlPointPositions = segmentControlNodes.stream()
                .map(RoadNode::getPosition)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (controlPointPositions.size() < 2) {
            LOGGER.warn("generateAndSetNextPathSegment: Not enough valid Vec3d control points ({}) for spline. HighwayNodeIndex: {}.",
                    controlPointPositions.size(), currentHighwayNodeProgressIndex);
            isOverallHighwayPathComplete = true;
            ai.setPath(new ArrayList<>());
            pathSegmentInProgress = false;
            return;
        }
        LOGGER.info("Generating spline. Highway progress index: {}. Using {} Vec3d control points from nodes: [{}]",
                currentHighwayNodeProgressIndex,
                controlPointPositions.size(),
                segmentControlNodes.stream().map(RoadNode::getId).collect(Collectors.joining(", ")));

        List<Vec3d> splinePoints = CatmullRomSpline.createSpline(controlPointPositions, CATMULL_SEGMENTS_PER_NODE_PAIR);

        if (splinePoints == null || splinePoints.isEmpty()) {
            LOGGER.warn("CatmullRomSpline.createSpline returned null or empty. HighwayNodeIndex {}.", currentHighwayNodeProgressIndex);
            ai.setPath(new ArrayList<>()); // Give AI an empty path
            pathSegmentInProgress = false; // No active segment for AI
            // Do not advance currentHighwayNodeProgressIndex here. Let tick() handle retrying or erroring out.
            // If CatmullRomSpline consistently fails for these points, tick() might get stuck if it doesn't
            // have a timeout or max retry for segment generation.
            // For now, this will cause tick() to call generateAndSetNextPathSegment again for the same index.
            return;
        }

        // *** DETAILED LOGGING FOR SPLINE PATH ***
        LOGGER.error("SPLINE PATH SET FOR BasicPathAI: From Node ID: {} (index {}), Towards Node ID: {}",
                p1_startOfCurrentSegment.getId(), currentHighwayNodeProgressIndex, p2_endOfCurrentSegment.getId());
        for (int i = 0; i < splinePoints.size(); i++) {
            LOGGER.error("  Spline Path Waypoint [{}]: {}", i, splinePoints.get(i));
        }
        // *** END DETAILED LOGGING ***
        ai.setPath(splinePoints);
        pathSegmentInProgress = true; // A new segment is now active in BasicPathAI

        // DO NOT increment currentHighwayNodeProgressIndex here.
        // The calling tick() method will increment it when this spline segment is completed by BasicPathAI.
    }

    @Override
    public void handleCameraRotation(BasicPathAI ai, MinecraftClient client) {
        Vec3d waypoint = ai.getCurrentWaypoint();
        if (waypoint != null && client.player != null) {
            BasicPathAI.rotateCameraToward(waypoint, client, false);
        }
    }

    @Override
    public boolean isComplete(BasicPathAI ai) {
        // The highway path is complete if we've marked it as such AND
        // BasicPathAI has finished processing its current (likely last) segment of spline points.
        boolean currentSegmentDone = ai.getCurrentWaypoint() == null && (ai.getPathSize() == 0 || ai.getCurrentIndex() >= ai.getPathSize());
        if (isOverallHighwayPathComplete && currentSegmentDone) {
            LOGGER.info("HighwaySplineStrategy: Overall highway path and final segment COMPLETED.");
            return true;
        }
        return false;
    }

    @Override
    public void onStop(BasicPathAI ai) {
        LOGGER.info("HighwaySplineStrategy: Path stopped.");
        isOverallHighwayPathComplete = true; // Ensure it's marked complete if stopped externally
    }
}