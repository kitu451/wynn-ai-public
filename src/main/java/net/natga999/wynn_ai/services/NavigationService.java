package net.natga999.wynn_ai.services;

import net.natga999.wynn_ai.path.LongDistancePathPlanner;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NavigationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NavigationService.class);
    private static final NavigationService INSTANCE = new NavigationService();

    private final LongDistancePathPlanner longDistancePathPlanner;
    // Future: private final BoatRouteManager boatManager;
    // Future: private final TeleportManager teleportManager;
    // Future: private final YourOrchestratorClass navigationOrchestrator;

    private NavigationService() {
        this.longDistancePathPlanner = LongDistancePathPlanner.getInstance();
        // Initialize other managers here if they were part of this service
        // this.navigationOrchestrator = new YourOrchestratorClass(BasicPathAI.getInstance(), this);
    }

    public static NavigationService getInstance() {
        return INSTANCE;
    }

    /**
     * Represents a complete travel plan, which might involve multiple segments and modes.
     * For now, it will be simplified.
     */
    public static class TravelPlan {
        // For now, just a list of Vec3d waypoints.
        // Later, this will be a list of segments (local walk, highway spline, use tunnel, take boat etc.)
        public final List<Vec3d> waypoints; // The direct output of LongDistancePathPlanner for now
        public final boolean planSuccess;

        public TravelPlan(List<Vec3d> waypoints, boolean success) {
            this.waypoints = waypoints;
            this.planSuccess = success;
        }
    }

    /**
     * Calculates a travel plan from the current player position to a goal position.
     * For now, this directly uses LongDistancePathPlanner.
     * In the future, this will incorporate multi-modal pathfinding (tunnels, boats, teleports).
     *
     * @param goalPosition The final destination.
     * @return A TravelPlan object. Check planSuccess before using waypoints.
     */
    public TravelPlan planJourneyTo(Vec3d goalPosition) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            LOGGER.warn("NavigationService: Cannot plan journey, player or world is null.");
            return new TravelPlan(null, false);
        }

        Vec3d startPosition = client.player.getPos();
        ClientWorld world = client.world;

        LOGGER.info("NavigationService: Planning journey from {} to {}.", startPosition, goalPosition);

        // --- Future Multi-Modal Logic Would Go Here ---
        // 1. Check for direct teleports (if available and efficient).
        // 2. Evaluate paths involving boats.
        // 3. Evaluate paths involving tunnels (via enhanced LongDistancePathPlanner or here).
        // 4. Evaluate walking/running paths (current LongDistancePathPlanner).
        // 5. Compare options and choose the "best" one.
        // --- For Now: Direct passthrough to LongDistancePathPlanner ---

        List<Vec3d> waypoints = longDistancePathPlanner.planPathToGoal(startPosition, goalPosition, world);

        if (waypoints != null && !waypoints.isEmpty()) {
            LOGGER.info("NavigationService: Journey planned successfully via LongDistancePathPlanner with {} waypoints.", waypoints.size());
            return new TravelPlan(waypoints, true);
        } else {
            LOGGER.warn("NavigationService: LongDistancePathPlanner failed to create a path to {}.", goalPosition);
            return new TravelPlan(null, false);
        }
    }


    /*
     * More advanced planning method that might return structured path segments.
     * Placeholder for when your orchestrator needs more than just Vec3d list.


    public StructuredTravelPlan planStructuredJourneyTo(Vec3d goalPosition) {
        // 1. Path locally to first highway node (List<Vec3d>)
        // 2. Get highway road nodes (List<RoadNode>) - this part now includes tunnels
        // 3. Path locally from last highway node to goal (List<Vec3d>)
        // This method would call your modified LongDistancePathPlanner that returns these 3 components.
        // Then, an orchestrator would use BasicPathAI.startGeneralPath and .startHighwaySplinePath.
        return null; // Placeholder
    }


    // If you create an Orchestrator class for executing multi-segment paths:
    // public void startManagedJourney(Vec3d goalPosition) {
    //    TravelPlan plan = planJourneyTo(goalPosition); // or planStructuredJourneyTo
    //    if (plan.planSuccess) {
    //        navigationOrchestrator.executePlan(plan);
    //    } else {
    //        MinecraftClient.getInstance().player.sendMessage(Text.literal("NavigationService: Could not find a way to " + goalPosition), false);
    //    }
    // }
    //
    // public boolean isJourneyComplete() {
    //    return navigationOrchestrator.isComplete();
    // }
    //
    // public void cancelJourney() {
    //    navigationOrchestrator.stop();
    // }
    **/
}