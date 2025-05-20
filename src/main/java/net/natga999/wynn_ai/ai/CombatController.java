package net.natga999.wynn_ai.ai;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.DisplayEntity;
import net.natga999.wynn_ai.managers.combat.CombatManager;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class CombatController {
    private static final CombatController INSTANCE = new CombatController();
    public static CombatController getInstance() { return INSTANCE; }

    private final CombatManager combatManager = CombatManager.getInstance();
    private BasicPathAI pathAI = BasicPathAI.getInstance();

    // Current target approach style
    private enum ApproachStyle {
        DIRECT,          // Move straight to the target
        CIRCLING,        // Circle around the target at optimal range
        STRAFING,        // Move side to side while attacking
        BACKING_AWAY     // Maintain maximum effective range
    }

    private ApproachStyle currentStyle = ApproachStyle.DIRECT;
    private Vec3d lastTargetPos = null;
    private boolean currentActionComplete = false;
    private int pathUpdateTimer = 0;
    private static final int PATH_UPDATE_FREQUENCY = 10; // Update path every 10 ticks

    // Configuration
    private double optimalAttackRange = 12.0;  // Increased from 3.0 to utilize the 15 block range
    private double maxAttackRange = 14.0;      // Increased to stay within effective range but not too close
    private double circlingRadius = 12.5;      // Increased to keep distance while circling
    private double minAttackRange = 10.0;      // New parameter for minimum attack distance

    // Waypoint reaching thresholds
    private final double reachThresholdXZ = 1.0;
    private final double reachThresholdY = 1.3;

    public void tick(MinecraftClient client, BasicPathAI ai) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        Vec3d targetPos = combatManager.getTargetPos();
        if (targetPos == null) return;

        double distanceToTarget = player.getPos().distanceTo(targetPos);

        // Store the previous style before potentially updating it
        ApproachStyle previousStyle = currentStyle;

        // Update approach style based on distance
        updateApproachStyle(distanceToTarget, player);

        // Only update the path in these cases:
        // 1. We haven't set a target yet (lastTargetPos is null)
        // 2. The approach style has changed
        // 3. The target has moved significantly AND the update timer has reached its threshold
        // 4. The update timer has reached a higher threshold (less frequent updates for same style)

        boolean styleChanged = previousStyle != currentStyle;
        boolean targetMovedSignificantly = lastTargetPos != null &&
                lastTargetPos.squaredDistanceTo(targetPos) > 4.0; // 2 block threshold

        if (lastTargetPos == null ||
                styleChanged ||
                (targetMovedSignificantly && pathUpdateTimer >= PATH_UPDATE_FREQUENCY) ||
                pathUpdateTimer >= PATH_UPDATE_FREQUENCY * 3) { // Less frequent updates if style hasn't changed

            // Generate new path based on the current approach style
            List<Vec3d> newPath = generateApproachPath(player, targetPos);

            // If we had a previous target and it's significantly different, create a transition
            if (lastTargetPos != null && lastTargetPos.squaredDistanceTo(targetPos) > 16.0 && !styleChanged) {
                newPath = createTransitionPath(player, lastTargetPos, targetPos, newPath);
            }

            // Apply the path
            pathAI.startCombatPath(newPath);

            // Save the new target position and reset timer
            lastTargetPos = targetPos;
            pathUpdateTimer = 0;
            currentActionComplete = false;
        } else {
            // Increment the update timer
            pathUpdateTimer++;
        }

        // Check if we've reached within the attack range
        boolean inAttackRange = distanceToTarget <= maxAttackRange &&
                distanceToTarget >= minAttackRange;

        // Update rotation to always face the target
        if (client.player != null) {
            Vec3d lookTarget = targetPos;
            if (inAttackRange) {
                lookTarget = lookTarget.subtract(0, 0.8, 0);
            }

            CombatManager.rotateCameraToward(lookTarget, client);
        }

        // Determine if the current action is complete based on reaching waypoints
        // or completing the attack sequence
        if (ai.getCurrentIndex() >= ai.getPathSize()) {
            currentActionComplete = true;
        }
    }

    /**
     * Creates a smooth transition path when the target changes significantly
     */
    private List<Vec3d> createTransitionPath(ClientPlayerEntity player, Vec3d oldTarget,
                                             Vec3d newTarget, List<Vec3d> destinationPath) {
        List<Vec3d> transitionPath = new ArrayList<>();

        // If targets are far apart, add an intermediate point
        if (oldTarget.distanceTo(newTarget) > 5.0) {
            Vec3d playerPos = player.getPos();
            Vec3d moveDirection = newTarget.subtract(playerPos).normalize();

            // Create an intermediate waypoint in the direction of the new target
            Vec3d intermediatePoint = playerPos.add(moveDirection.multiply(2.0));
            transitionPath.add(intermediatePoint);
        }

        // Add the destination path points
        transitionPath.addAll(destinationPath);

        return transitionPath;
    }

    private void updateApproachStyle(double distanceToTarget, ClientPlayerEntity player) {
        // Keep distance when close to combat
        if (distanceToTarget < minAttackRange) {
            currentStyle = ApproachStyle.BACKING_AWAY;
        }
        // Use circling at optimal range
        else if (distanceToTarget >= minAttackRange && distanceToTarget <= optimalAttackRange) {
            // Alternate between circling and strafing for unpredictability
            if (Math.random() < 0.7) {
                currentStyle = ApproachStyle.CIRCLING;
            } else {
                currentStyle = ApproachStyle.STRAFING;
            }
        }
        // Approach directly when too far
        else if (distanceToTarget > maxAttackRange) {
            currentStyle = ApproachStyle.DIRECT;
        }
        // Stay with current style if within good range
        else if (currentStyle != ApproachStyle.DIRECT) {
            // Keep current style with small chance to switch
            if (Math.random() < 0.1) {
                currentStyle = currentStyle == ApproachStyle.CIRCLING ?
                        ApproachStyle.STRAFING : ApproachStyle.CIRCLING;
            }
        } else {
            // Default to circling if no style is set
            currentStyle = ApproachStyle.CIRCLING;
        }
    }

    private List<Vec3d> generateApproachPath(ClientPlayerEntity player, Vec3d targetPos) {
        List<Vec3d> path = new ArrayList<>();
        double distance = player.getPos().distanceTo(targetPos);

        // Choose approach style based on distance and conditions
        updateApproachStyle(distance, player);

        switch (currentStyle) {
            case DIRECT:
                Vec3d directApproach = calculateDirectApproach(player.getPos(), targetPos);
                path.add(directApproach);
                break;
            case CIRCLING:
                generateCirclingPath(path, player, targetPos);
                break;
            case STRAFING:
                generateStrafingPath(path, player, targetPos);
                break;
            case BACKING_AWAY:
                generateBackAwayPath(path, player, targetPos);
                break;
        }

        return path;
    }

    /**
     * Calculates a position to approach the target directly while
     * considering the optimal attack range
     */
    private Vec3d calculateDirectApproach(Vec3d playerPos, Vec3d targetPos) {
        // Direction vector from target to player
        Vec3d directionVector = playerPos.subtract(targetPos).normalize();

        // Calculate the ideal position at optimal attack range
        Vec3d idealPosition = targetPos.add(directionVector.multiply(optimalAttackRange));

        // If we're too close to the target, add more distance
        double currentDistance = playerPos.distanceTo(targetPos);
        if (currentDistance < minAttackRange) {
            // Move back to maintain minimum distance
            return targetPos.add(directionVector.multiply(optimalAttackRange + 1.0));
        }

        // If we're at good range, maintain position with small adjustments
        if (currentDistance >= minAttackRange && currentDistance <= maxAttackRange) {
            // Small adjustment to prevent perfect stillness
            double adjustmentFactor = 0.5;
            double randomAdjustment = (Math.random() - 0.5) * adjustmentFactor;
            return targetPos.add(directionVector.multiply(currentDistance + randomAdjustment));
        }

        return idealPosition;
    }

    private void generateCirclingPath(List<Vec3d> path, ClientPlayerEntity player, Vec3d targetPos) {
        // Generate points in a circle around the target
        Vec3d playerToTarget = player.getPos().subtract(targetPos);
        double currentAngle = Math.atan2(playerToTarget.z, playerToTarget.x);

        // Add several points along the circle for a quarter turn
        for (int i = 0; i < 4; i++) {
            double angle = currentAngle + (Math.PI / 2 * i / 3); // Divide by 3 for smoother movement
            double x = targetPos.x + Math.cos(angle) * circlingRadius;
            double z = targetPos.z + Math.sin(angle) * circlingRadius;
            path.add(new Vec3d(x, targetPos.y, z));
        }
    }

    private void generateStrafingPath(List<Vec3d> path, ClientPlayerEntity player, Vec3d targetPos) {
        // Generate a strafing pattern perpendicular to the target
        Vec3d playerToTarget = targetPos.subtract(player.getPos()).normalize();
        Vec3d perpendicular = new Vec3d(-playerToTarget.z, 0, playerToTarget.x).normalize();

        // Generate two points to strafe between
        Vec3d optimalPos = targetPos.subtract(playerToTarget.multiply(optimalAttackRange));
        Vec3d strafeLeft = optimalPos.add(perpendicular.multiply(2.0));
        Vec3d strafeRight = optimalPos.subtract(perpendicular.multiply(2.0));

        // Add current strafe direction first based on player position
        if (player.age % 60 < 30) { // Switch direction every ~1.5 seconds
            path.add(strafeLeft);
            path.add(strafeRight);
        } else {
            path.add(strafeRight);
            path.add(strafeLeft);
        }
    }

    private void generateBackAwayPath(List<Vec3d> path, ClientPlayerEntity player, Vec3d targetPos) {
        Vec3d playerPos = player.getPos();

        // Direction away from target
        Vec3d directionAway = playerPos.subtract(targetPos).normalize();

        // Calculate the optimal position to retreat to
        Vec3d retreatPos = targetPos.add(directionAway.multiply(optimalAttackRange));

        // Add slight randomness to the retreat path
        double sidewaysAngle = Math.random() * Math.PI * 0.5 - Math.PI * 0.25; // -45 to +45 degrees
        Vec3d sideways = new Vec3d(
                Math.sin(sidewaysAngle),
                0,
                Math.cos(sidewaysAngle)
        );

        // Adjust retreat position with sideways component
        retreatPos = retreatPos.add(sideways.multiply(2.0));

        // Create a path with a few waypoints
        Vec3d midPoint = playerPos.add(retreatPos.subtract(playerPos).multiply(0.5));
        path.add(midPoint);
        path.add(retreatPos);
    }

    /**
     * Checks if the player has reached a specific waypoint
     */
    public boolean hasReachedWaypoint(ClientPlayerEntity player, Vec3d waypoint) {
        // Calculate horizontal (XZ) and vertical (Y) distances separately
        double distanceXZ = Math.sqrt(
                Math.pow(player.getX() - waypoint.x, 2) +
                        Math.pow(player.getZ() - waypoint.z, 2)
        );
        double distanceY = Math.abs(player.getY() - waypoint.y);

        // Check if we've reached the waypoint
        return distanceXZ < reachThresholdXZ && distanceY < reachThresholdY;
    }

    /**
     * Determines if we should skip the current waypoint
     */
    public boolean shouldSkipWaypoint(ClientPlayerEntity player, BasicPathAI ai) {
        // Leverage the existing logic in BasicPathAI
        return ai.isReachedNext(player);
    }

    /**
     * Indicates if the current combat action is complete
     */
    public boolean isCurrentActionComplete() {
        return currentActionComplete;
    }

    /**
     * Handle cleanup when movement is stopped
     */
    public void onMovementStopped() {
        // Reset internal state
        lastTargetPos = null;
        currentActionComplete = true;
        pathUpdateTimer = 0;
    }

    public void handleCameraRotation(MinecraftClient client, BasicPathAI ai) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        // Get the current target position
        Vec3d target = combatManager.getTargetPos();
        if (target == null) return;

        // Improved check for proximity to target
        boolean isCloseToTarget = isNearTarget(player, target);

        // Get accurate target entity position when available
        DisplayEntity.TextDisplayEntity entity = combatManager.getCurrentTarget();
        Vec3d adjustedTarget;

        if (entity != null && entity.isAlive()) {
            // Always use the entity's exact position, not cached targetPos
            adjustedTarget = entity.getPos();

            // Calculate height adjustment based on distance
            double distanceXZ = Math.sqrt(
                    Math.pow(player.getX() - adjustedTarget.x, 2) +
                            Math.pow(player.getZ() - adjustedTarget.z, 2)
            );

            // When very close, look at the entity's eye level
            if (distanceXZ < 5.0) {
                // Adjust aim point to be at the appropriate vertical position
                // based on entity's hitbox - targeting slightly higher than
                // center for most mobs
                adjustedTarget = new Vec3d(
                        adjustedTarget.x,
                        adjustedTarget.y + 1.0, // Aiming higher on the mob when close
                        adjustedTarget.z
                );
            }
        } else {
            // Fallback to the cached position
            adjustedTarget = target;
        }

        // More direct camera movement when in attack range
        if (combatManager.isInAttackRange()) {
            // Use a more direct (higher) rotation speed when in attack range
            CombatManager.rotateCameraToward(adjustedTarget, client);
        }
        // Otherwise balance between looking at waypoint and target
        else {
            Vec3d waypoint = ai.getCurrentWaypoint();
            if (waypoint != null) {
                // When getting closer to a waypoint, begin transition to looking at target earlier
                double waypointDistance = player.getPos().distanceTo(waypoint);
                if (waypointDistance < 7.0) { // Increased from 5.0 to start transition earlier
                    // Gradually blend from waypoint to target as we get closer
                    double blendFactor = Math.max(0, Math.min(1, (7.0 - waypointDistance) / 5.0));

                    // Keep waypoint at eye level for navigation
                    Vec3d adjustedWaypoint = new Vec3d(waypoint.x, player.getEyeY(), waypoint.z);

                    // Interpolate between looking at waypoint and target
                    if (blendFactor < 1.0) {
                        // This is a conceptual implementation - you'd need to implement a method
                        // to blend between two camera targets
                        Vec3d blendedTarget = lerpVec3d(adjustedWaypoint, adjustedTarget, blendFactor);
                        BasicPathAI.rotateCameraToward(blendedTarget, client, false);
                    } else {
                        CombatManager.rotateCameraToward(adjustedTarget, client);
                    }
                } else {
                    // Keep eyes level for better navigation when far from waypoint
                    Vec3d adjustedWaypoint = new Vec3d(waypoint.x, player.getEyeY(), waypoint.z);
                    BasicPathAI.rotateCameraToward(adjustedWaypoint, client, false);
                }
            } else {
                // If no waypoint, focus on the target
                CombatManager.rotateCameraToward(adjustedTarget, client);
            }
        }
    }

    // Helper method to interpolate between Vec3d points
    private Vec3d lerpVec3d(Vec3d a, Vec3d b, double t) {
        return new Vec3d(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t
        );
    }

    /**
     * Checks if player is relatively near the target
     */
    private boolean isNearTarget(ClientPlayerEntity player, Vec3d target) {
        if (player == null || target == null) return false;

        // Calculate horizontal distance (ignoring Y)
        double horizontalDistance = Math.sqrt(
                Math.pow(player.getX() - target.x, 2) +
                        Math.pow(player.getZ() - target.z, 2)
        );

        // Consider vertical difference separately
        double verticalDifference = Math.abs(player.getY() - target.y);

        // A mob is "near" if horizontal distance is small
        // and vertical difference isn't extreme
        return horizontalDistance < optimalAttackRange && verticalDifference < 3.0;
    }
}