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
import java.util.stream.Collectors;

public class HighwaySplineStrategy implements MovementStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(HighwaySplineStrategy.class);

    private final List<RoadNode> fullHighwayNodePath;
    private int currentHighwayNodeProgressIndex; // Which node in fullHighwayNodePath is the start of the current spline segment's focus

    private static final int SPLINE_CONTROL_POINTS_LOOKAHEAD = 4; // How many RoadNodes to consider for generating one spline segment (e.g., P0, P1, P2, P3 for spline between P1-P2)
    private static final int CATMULL_SEGMENTS_PER_NODE_PAIR = 8; // Detail level for CatmullRomSpline.createSpline

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
            if (isOverallHighwayPathComplete && ai.getCurrentWaypoint() == null) {
                // If marked complete and AI has no more waypoints, ensure AI stops.
                // The isComplete() method should handle this, but as a safeguard.
                LOGGER.info("HighwaySplineStrategy: Overall path marked complete and AI has no current waypoint. AI should stop via isComplete().");
            } else if (isOverallHighwayPathComplete) {
                // Still processing the last segment of the overall path
                assert client != null;
                ai.updateMovementToward(ai.getCurrentWaypoint(), client);
                if (ai.isReachedNext(client.player)) {
                    ai.incrementCurrentPathIndex();
                }
            }
            return;
        }

        // If BasicPathAI has finished its current list of spline points (or it's the first tick)
        if (ai.getCurrentWaypoint() == null) {
            if (currentHighwayNodeProgressIndex >= fullHighwayNodePath.size() -1 && fullHighwayNodePath.size() >=2) {
                // We've processed up to the segment leading to the last node.
                // Or if path was too short, already marked complete.
                isOverallHighwayPathComplete = true;
                LOGGER.info("HighwaySplineStrategy: All highway nodes processed. Marking overall path as complete.");
                // BasicPathAI will run out the last segment. isComplete() will then trigger.
                return; // Let the current (possibly empty) path in AI finish.
            }
            generateAndSetNextSplineSegment(ai);
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

    private void generateAndSetNextSplineSegment(BasicPathAI ai) {
        if (currentHighwayNodeProgressIndex >= fullHighwayNodePath.size()) {
            LOGGER.info("generateAndSetNextPathSegment: End of processable nodes. Index: {}", currentHighwayNodeProgressIndex);
            isOverallHighwayPathComplete = true;
            ai.setPath(new ArrayList<>());
            return;
        }

        RoadNode p1_currentNode = fullHighwayNodePath.get(currentHighwayNodeProgressIndex);

        // If this is the last node, we can't form a P1-P2 segment from it.
        if (currentHighwayNodeProgressIndex >= fullHighwayNodePath.size() - 1) {
            LOGGER.info("generateAndSetNextPathSegment: At last node ({}) or beyond. No further segments to generate.", p1_currentNode.getId());
            isOverallHighwayPathComplete = true;
            ai.setPath(new ArrayList<>()); // Ensure AI path is cleared
            return;
        }

        RoadNode p2_nextNode = fullHighwayNodePath.get(currentHighwayNodeProgressIndex + 1);

        // *** TUNNEL LOGIC ***
        if ("TUNNEL_ENTRANCE".equalsIgnoreCase(p1_currentNode.getType()) &&
                p1_currentNode.getTargetTunnelExitNodeId() != null &&
                p1_currentNode.getTargetTunnelExitNodeId().equals(p2_nextNode.getId())) {

            LOGGER.info("HighwaySplineStrategy: Detected tunnel from {} (current) to {} (next in A* plan).",
                    p1_currentNode.getId(), p2_nextNode.getId());

            // Path to the tunnel entrance block itself.
            // BasicPathAI will move to this single point.
            // Upon reaching it, the game's instantaneous teleport should occur.
            if (p1_currentNode.getPosition() != null) {
                ai.setPath(List.of(p1_currentNode.getPosition()));
                LOGGER.info("Setting path to tunnel entrance: {}", p1_currentNode.getPosition());
            } else {
                LOGGER.warn("Tunnel entrance {} has no position! Skipping segment.", p1_currentNode.getId());
                ai.setPath(new ArrayList<>()); // Clear path
                // Error: advance past this problematic segment.
                // We effectively skip both the entrance (p1) and the exit (p2) for path generation.
                currentHighwayNodeProgressIndex += 2; // Advance past P1 (entrance) and P2 (exit)
                isOverallHighwayPathComplete = (currentHighwayNodeProgressIndex >= fullHighwayNodePath.size() -1 && fullHighwayNodePath.size() >=2);
                return;
            }

            // After BasicPathAI reaches p1_currentNode.getPosition(), the player is teleported to p2_nextNode.
            // The next time tick() calls this method (because AI's path to p1 is complete),
            // currentHighwayNodeProgressIndex will be incremented to point to p2_nextNode (the tunnel exit).
            // We advance the progress index here to signify that this "tunnel segment" (P1 to P2) has been "processed"
            // by initiating the move to P1. The next segment generation will start FROM P2.
            currentHighwayNodeProgressIndex++; // Advance past P1 (entrance). Next segment will start from P2 (exit).
            return; // Path set for tunnel entrance, next tick will handle from exit.
        }

        // *** REGULAR SPLINE LOGIC ***
        if (fullHighwayNodePath.size() >=2 && currentHighwayNodeProgressIndex == fullHighwayNodePath.size() - 2) {
            LOGGER.info("HighwaySplineStrategy: Processing final spline segment (from node {} to {}).",
                    p1_currentNode.getId(), p2_nextNode.getId());
        }

        List<RoadNode> segmentControlNodes = new ArrayList<>();
        // Gather to SPLINE_CONTROL_POINTS_LOOKAHEAD nodes for Catmull-Rom.
        // The core segment of the spline will be between the second and third node gathered (if available).
        // CatmullRomSpline.createSpline handles virtual points if the list is shorter than 4.
        for (int i = 0; i < SPLINE_CONTROL_POINTS_LOOKAHEAD; i++) {
            // We need an anchor point (P1 in the typical P0,P1,P2,P3 setup for Catmull-Rom).
            // The currentHighwayNodeProgressIndex usually represents P1.
            // So, P0 would be currentHighwayNodeProgressIndex - 1, P2 is +1, P3 is +2.
            // Let's adjust the indexing to be clearer for typical Catmull-Rom P0,P1,P2,P3 setup:
            // P1 is the current node we are "at" or "approaching the end of a segment for".
            // P2 is the next node we are pathing towards.
            int nodeIndexInFullPath = currentHighwayNodeProgressIndex - 1 + i; // P0, P1, P2, P3 relative to currentHighwayNodeProgressIndex as P1

            if (nodeIndexInFullPath >= 0 && nodeIndexInFullPath < fullHighwayNodePath.size()) {
                segmentControlNodes.add(fullHighwayNodePath.get(nodeIndexInFullPath));
            }
            // If nodeIndexInFullPath is out of bounds, CatmullRomSpline.createSpline is expected to handle it
            // by creating virtual points if necessary, or by working with fewer than 4 points.
        }

        // If we didn't even get the current node (P1) and the next node (P2), we can't make a segment.
        // This means currentHighwayNodeProgressIndex is at or past the second to last node.
        // We need at least two *distinct* nodes to define a segment for the spline.
        if (segmentControlNodes.size() < 2) {
            LOGGER.info("generateAndSetNextSplineSegment: Not enough actual control nodes ({}) for spline (currentHighwayNodeIndex: {}, total highway nodes: {}). Ending highway travel.",
                    segmentControlNodes.size(), currentHighwayNodeProgressIndex, fullHighwayNodePath.size());
            isOverallHighwayPathComplete = true;
            ai.setPath(new ArrayList<>());
            return;
        }

        List<Vec3d> controlPointPositions = segmentControlNodes.stream()
                .map(RoadNode::getPosition)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        // After filtering nulls, re-check if we have enough for CatmullRomSpline
        if (controlPointPositions.size() < 2) {
            LOGGER.warn("generateAndSetNextSplineSegment: Not enough valid Vec3d control points ({}) for spline after filtering nulls. (currentHighwayNodeIndex: {}).",
                    controlPointPositions.size(), currentHighwayNodeProgressIndex);
            isOverallHighwayPathComplete = true;
            ai.setPath(new ArrayList<>());
            return;
        }

        LOGGER.info("Generating spline segment. Highway progress index: {}. Using {} Vec3d control points: [{}] from nodes: [{}]",
                currentHighwayNodeProgressIndex,
                controlPointPositions.size(), // Corrected: Use the size of the actual Vec3d list
                controlPointPositions.stream().map(v -> String.format("(%.1f,%.1f,%.1f)", v.x, v.y, v.z)).collect(Collectors.joining(", ")),
                segmentControlNodes.stream().map(RoadNode::getId).collect(Collectors.joining(", ")));


        List<Vec3d> splinePoints = CatmullRomSpline.createSpline(controlPointPositions, CATMULL_SEGMENTS_PER_NODE_PAIR);

        if (splinePoints.isEmpty()) {
            LOGGER.warn("CatmullRomSpline.createSpline returned null or empty for segment. Highway progress index {}.", currentHighwayNodeProgressIndex);
            currentHighwayNodeProgressIndex++; // Try to advance past problematic segment
            isOverallHighwayPathComplete = (currentHighwayNodeProgressIndex >= fullHighwayNodePath.size() -1 && fullHighwayNodePath.size() >=2);
            ai.setPath(new ArrayList<>());
            return;
        }

        LOGGER.debug("Generated {} spline points for current highway segment (nodes {} to {}).",
                splinePoints.size(),
                (currentHighwayNodeProgressIndex < fullHighwayNodePath.size() ? fullHighwayNodePath.get(currentHighwayNodeProgressIndex).getId() : "END"),
                (currentHighwayNodeProgressIndex + 1 < fullHighwayNodePath.size() ? fullHighwayNodePath.get(currentHighwayNodeProgressIndex + 1).getId() : "END")
        );
        ai.setPath(splinePoints);

        // Advance the progress index. After generating a spline using nodes up to, say, index K
        // (as P_last_in_control_set), the bot will follow this spline. Once done, it should have
        // effectively reached the node that was P2 in the P0,P1,P2,P3 setup.
        // So, currentHighwayNodeProgressIndex (which represented P1) should advance by 1.
        if (fullHighwayNodePath.size() > 1 && currentHighwayNodeProgressIndex < fullHighwayNodePath.size() -1) {
            currentHighwayNodeProgressIndex++;
        } else {
            // If we only had 2 nodes, or we've just processed the segment leading to the last node.
            isOverallHighwayPathComplete = true;
            LOGGER.info("Processed the last highway segment or path was too short. Marked overall complete. currentHighwayNodeProgressIndex: {}", currentHighwayNodeProgressIndex);
        }
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