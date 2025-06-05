package net.natga999.wynn_ai.ai;

import net.natga999.wynn_ai.managers.combat.CombatManager;
import net.natga999.wynn_ai.path.network.RoadNode;
import net.natga999.wynn_ai.strategies.*;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.block.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

//ts pmo, start full vibe-coding tbh, didn't even read
public class BasicPathAI {
    private static final Logger LOGGER = LoggerFactory.getLogger(BasicPathAI.class);

    private static final BasicPathAI INSTANCE = new BasicPathAI();
    public static BasicPathAI getInstance() { return INSTANCE; }

    // private static Vec3d target = null; // This static target seems less used; movement relies on getCurrentWaypoint()
    private final double reachThresholdXZ = 1.0;
    private final double reachThresholdY = 1.3;
    private final double lookAheadSwitchDistance = 3.0; // When closer than this to current waypoint, camera looks at next. (Adjust as needed)

    private final double defaultReachThresholdXZ = 1.0; // Your current reachThresholdXZ
    private final double preciseReachThresholdXZ = 0.4; // For precise movement (tune this)
    private final double hazardCheckRadius = 2.0;     // How far to the sides/front to check for hazards
    private final int cliffDownScanDepth = 4;         // How many blocks to scan down to detect a cliff

    private int jumpCooldown = 0;
    private int lastJump= 0;
    private static final int JUMP_COOLDOWN_TICKS = 8;
    private static final double JUMP_CHECK_DISTANCE = 1.0;

    // Added path tracking variables
    private List<Vec3d> path = new ArrayList<>();
    private int currentPathIndex = 0;
    private boolean followingPath = false;
    private MovementStrategy strategy;

    public void tick() {
        if (jumpCooldown > 0) jumpCooldown--;
        lastJump++;

        // If we have no strategy or path, do nothing
        if (!followingPath || strategy == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        // Let the strategy handle camera rotation
        if (client != null && client.player != null) {
            // The strategy will call ai.rotateCameraToward(client, isFinal)
            strategy.handleCameraRotation(this, client);
        }

        if (CombatManager.getInstance().isInAttackRange()) {
            return;
        }

        LOGGER.debug("Ticking movement strategy");
        // Let the strategy handle movement logic
        strategy.tick(this);

        // Check if the strategy considers the movement complete
        if (strategy != null && strategy.isComplete(this)) {
            stop();
        }
    }

    /**
     * Determines the appropriate target for the camera to look at,
     * implementing look-ahead logic.
     * @return The Vec3d target for the camera.
     */
    private Vec3d getCameraLookAtTarget() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (path.isEmpty() || currentPathIndex >= path.size() || client == null || client.player == null) {
            return getCurrentWaypoint(); // Fallback or if no path/player
        }

        Vec3d currentWaypoint = path.get(currentPathIndex);
        if (currentWaypoint == null) return null;

        // If this is the last waypoint in the current path segment, always look at it.
        if (currentPathIndex == path.size() - 1) {
            return currentWaypoint;
        }

        Vec3d playerPos = client.player.getPos();
        double distanceToCurrentXZ = Math.sqrt(
                Math.pow(playerPos.x - currentWaypoint.x, 2) +
                        Math.pow(playerPos.z - currentWaypoint.z, 2)
        );

        if (distanceToCurrentXZ < this.lookAheadSwitchDistance) {
            // Close to the current waypoint, try to look at the next one.
            if (currentPathIndex + 1 < path.size()) {
                Vec3d nextWaypoint = path.get(currentPathIndex + 1);
                if (nextWaypoint != null) {
                    LOGGER.trace("Camera looking ahead to waypoint {} (current index {})", currentPathIndex + 1, currentPathIndex);
                    return nextWaypoint;
                }
            }
        }
        LOGGER.trace("Camera looking at current waypoint {} (index {})", currentPathIndex, currentPathIndex);
        // Default: look at the current waypoint.
        return currentWaypoint;
    }


    /**
     * Common private initialization for all path types.
     * Resets path index and sets the new waypoints.
     */
    private void initPathInternal(List<Vec3d> waypoints) { // Renamed to avoid confusion with a potential public initPath
        this.path = (waypoints != null) ? new ArrayList<>(waypoints) : new ArrayList<>();
        this.currentPathIndex = 0;
        this.followingPath = true; // Assume if a path is set, we should follow. Strategy can stop if needed.

        if (this.path.isEmpty() && !(this.strategy instanceof HighwaySplineStrategy)) {
            LOGGER.warn("Initialized path with empty waypoints for a non-dynamic strategy. AI will stop.");
            this.followingPath = false; // Don't follow if path is truly empty and not dynamic
            // If strategy is null here and path is empty, it should probably stop completely.
            // The stop() method handles clearing strategy.
            if (this.strategy == null) {
                stop();
            }
        } else if (!this.path.isEmpty()){
            LOGGER.info("Path segment initialized with {} waypoints. Current strategy: {}",
                    this.path.size(), strategy != null ? strategy.getClass().getSimpleName() : "None");
        } else if (this.strategy instanceof HighwaySplineStrategy) {
            LOGGER.info("Path initialized for HighwaySplineStrategy (path currently empty, strategy will populate).");
        }
    }

    /**
     * Public method for strategies or external controllers to set the current path segment
     * for the AI to follow. This will clear any existing path segment and start fresh.
     *
     * @param waypoints The new list of waypoints for the current segment.
     */
    public void setPath(List<Vec3d> waypoints) {
        // Call the internal private method to do the actual initialization
        initPathInternal(waypoints);
        // If a strategy is active and sets an empty path, it might intend to stop or re-evaluate.
        // The strategy's isComplete() or tick() logic should handle such cases.
        if (waypoints == null || waypoints.isEmpty()) {
            if (!(strategy instanceof HighwaySplineStrategy)) { // HighwaySplineStrategy might start with empty and fill
                LOGGER.warn("An empty path segment was set. BasicPathAI will likely stop unless strategy intervenes.");
                // It's often better for the strategy to call ai.stop() explicitly if it means to stop.
            }
        }
    }

    /** Called by your higher-level controller when a new harvest path is ready. */
    public void startHarvest(List<Vec3d> waypoints) {
        this.strategy = new HarvestMovementStrategy(); // Set strategy first
        setPath(waypoints); // Then set the path
    }

    /** Called by your higher-level controller when combat-movement should begin. */
    public void startCombatPath(List<Vec3d> waypoints) {
        this.strategy = new CombatMovementStrategy(); // Set strategy first
        setPath(waypoints); // Then set the path
    }

    public void startGeneralPath(List<Vec3d> waypoints) {
        this.strategy = new GeneralPurposeTravelStrategy(); // Set strategy first
        setPath(waypoints); // Then set the path
        // Original log: LOGGER.info("Starting new General Purpose path with {} waypoints", waypoints.size());
        // This log is now handled by initPathInternal or setPath.
    }

    /**
     * Initializes BasicPathAI to follow a path composed of highway RoadNodes,
     * using dynamic Catmull-Rom spline generation.
     * The HighwaySplineStrategy will feed spline segments into this AI's path.
     */
    public void startHighwaySplinePath(List<RoadNode> highwayNodes) {
        if (highwayNodes == null || highwayNodes.size() < 2) {
            LOGGER.warn("Attempted to start highway spline path with insufficient nodes. Aborting.");
            stop();
            return;
        }
        this.strategy = new HighwaySplineStrategy(highwayNodes); // Set strategy first
        setPath(new ArrayList<>()); // Start with an empty path; strategy will populate it.
        this.followingPath = true; // Explicitly ensure we are in following mode for this strategy.
        LOGGER.info("Starting new Highway Spline Path with {} total road nodes.", highwayNodes.size());
    }

    /**
     * Checks if the player has reached at least the current waypoint,
     * and optionally any subsequent waypoints to allow for skipping in case of overshooting.
     *
     * @param player The client player entity to check position for
     * @return true if the player has reached the current waypoint or any further ones in the path
     */
    public boolean isReachedNext(ClientPlayerEntity player) {
        if (player == null || path.isEmpty() || currentPathIndex >= path.size()) {
            return false;
        }

        Vec3d playerPos = player.getPos();
        Vec3d currentWaypoint = path.get(currentPathIndex);

        // Determine the dynamic reach threshold
        double activeReachThresholdXZ;
        if (isEnvironmentHazardous(player, currentWaypoint)) {
            activeReachThresholdXZ = this.preciseReachThresholdXZ;
            LOGGER.trace("Using PRECISE reach threshold: {}", activeReachThresholdXZ);
        } else {
            activeReachThresholdXZ = this.defaultReachThresholdXZ;
            LOGGER.trace("Using DEFAULT reach threshold: {}", activeReachThresholdXZ);
        }

        // First check if we've reached the current waypoint using the dynamic threshold
        double currentDistanceXZ = Math.sqrt(
                Math.pow(playerPos.x - currentWaypoint.x, 2) +
                        Math.pow(playerPos.z - currentWaypoint.z, 2)
        );
        double currentDistanceY = Math.abs(playerPos.y - currentWaypoint.y);

        boolean reachedCurrent = currentDistanceXZ < activeReachThresholdXZ && currentDistanceY < reachThresholdY;

        if (reachedCurrent) {
            return true;
        }

        // Then check if we've overshot and reached any of the next waypoints in the path
        // For overshooting, we can also use the dynamic threshold, or stick to default.
        // Using dynamic might be better to prevent skipping crucial points in tight areas.
        for (int i = currentPathIndex + 1; i < path.size(); i++) {
            Vec3d nextWaypoint = path.get(i);
            // Recalculate hazard for the 'nextWaypoint' if we want the skip check to be context-aware too
            // For simplicity now, let's use the same activeReachThresholdXZ determined for the current waypoint.
            // A more advanced version could re-evaluate isEnvironmentHazardous for each 'nextWaypoint'.

            double nextDistanceXZ = Math.sqrt(
                    Math.pow(playerPos.x - nextWaypoint.x, 2) +
                            Math.pow(playerPos.z - nextWaypoint.z, 2)
            );
            double nextDistanceY = Math.abs(playerPos.y - nextWaypoint.y);

            if (nextDistanceXZ < activeReachThresholdXZ && nextDistanceY < reachThresholdY) {
                currentPathIndex = i;
                LOGGER.debug("Skipped to waypoint {} as player overshot (using threshold {})", i, activeReachThresholdXZ);
                return true;
            }
        }
        return false;
    }

    public void updateMovementToward(Vec3d targetPos, MinecraftClient client) {
        if (client.player == null || client.options == null || targetPos == null) return; // Added null check for targetPos

        // Reset movement each tick
        client.options.forwardKey.setPressed(false);
        client.options.backKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        client.options.jumpKey.setPressed(false);
        client.options.sprintKey.setPressed(false);

        // World-space difference
        Vec3d playerPos = client.player.getPos();
        double dx = targetPos.x - playerPos.x;
        double dz = targetPos.z - playerPos.z;

        // If very close, do nothing for movement keys (camera might still adjust)
        if (dx * dx + dz * dz < 0.01 * 0.01) { // Adjusted threshold for very close
            // Allow checkAndJump to still operate even if very close to the XZ of the waypoint
            checkAndJump(client);
            return;
        }

        // Convert player yaw to radians, and invert it
        double yawRad = -Math.toRadians(client.player.getYaw());

        // Rotate (dx, dz) by –yaw to get local coordinates
        double localX = dx * Math.cos(yawRad) - dz * Math.sin(yawRad);
        double localZ = dx * Math.sin(yawRad) + dz * Math.cos(yawRad);

        // Decide which key to press
        if (localZ > Math.abs(localX) * 0.5) { // Adjusted sensitivity: prefer forward more
            // Mostly in front
            client.options.forwardKey.setPressed(true);
        } else if (localZ < -Math.abs(localX) * 0.5) {
            // Mostly behind
            client.options.backKey.setPressed(true);
        } else if (localX < 0) { // Player's local X is negative, target is to their right
            // Mostly to the right
            client.options.rightKey.setPressed(true);
        } else { // Player's local X is positive, target is to their left
            // Mostly to the left
            client.options.leftKey.setPressed(true);
        }

        checkAndJump(client);

        if (lastJump > 20) {
            client.options.sprintKey.setPressed(true);
        }

        // Random "bunny hop" (20% chance each tick)
        if (Math.random() < 0.05 && client.player.isOnGround() && client.options.forwardKey.isPressed()) { // Reduced chance, only if moving forward
            client.options.jumpKey.setPressed(true);
        }
    }

    /**
     * Rotates the camera towards an appropriate target, using look-ahead logic.
     * This is an instance method.
     * @param client The Minecraft client.
     * @param isFinal True if this is the final rotation adjustment (no interpolation).
     */
    public void rotateCameraToward(MinecraftClient client, boolean isFinal) {
        ClientPlayerEntity player = client.player;
        Vec3d targetPos = getCameraLookAtTarget(); // Use the look-ahead target

        if (player == null || targetPos == null) return;

        // 1) Get eye position and full delta
        Vec3d eyePos = player.getCameraPosVec(1.0f);
        Vec3d delta  = targetPos.subtract(eyePos);

        // 2) Extract horizontal (XZ) component and length
        Vec3d deltaXZ = new Vec3d(delta.x, 0, delta.z);
        double distXZ = deltaXZ.length();

        // 3) Compute a “look-ahead” vector one block past the target on the same line
        Vec3d lookVector;
        if (distXZ > 1e-6) {
            Vec3d normXZ     = deltaXZ.multiply(1.0 / distXZ);
            lookVector       = deltaXZ.add(normXZ);  // 1 block further
        } else {
            lookVector = deltaXZ; // basically zero; you’ll fall back to current yaw
        }

        // 4) Compute rawYaw from the extended lookVector
        float rawYaw;
        if (lookVector.length() > 1e-6) {
            rawYaw = (float) Math.toDegrees(Math.atan2(lookVector.z, lookVector.x)) - 90.0f;
        } else {
            rawYaw = player.getYaw();
        }

        float rawPitch = (float) -Math.toDegrees(Math.atan2(delta.y, distXZ));

        if (isFinal) {
            // Directly set the yaw and pitch without interpolation for final rotation
            player.setYaw(rawYaw);
            player.setPitch(rawPitch);
            return;
        }

        // Get current view
        float currentYaw = player.getYaw();
        float currentPitch = player.getPitch();

        // Detect actual falling via vertical velocity
        // LOGGER.debug("velocity: {}", player.getVelocity()); // Can be spammy
        boolean isFalling = player.getVelocity().y < -0.38;

        float newYaw;
        float newPitch;

        if (isFalling) {
            // --- LOCK YAW ---
            newYaw = currentYaw;

            // --- ONLY ADJUST PITCH toward raw target ---
            float targetPitch = lerpAngle(currentPitch, rawPitch, 0.25f);

            // Clamp so you never look more than 50° below the horizon
            // (i.e. max pitch = 28+-2°)
            newPitch = MathHelper.clamp(targetPitch, -90.0f, 28.0f + (float)((Math.random() - 0.5) * 4));
        } else {
            // --- NORMAL BEHAVIOR WHEN NOT FALLING ---
            // Clamp pitch toward horizon if on flat ground
            float desiredPitch = lerpAngle(rawPitch, 25.0f, 0.7f); // Look slightly down towards ground
            float pitchSpeed = 0.35f;

            // Small jitter for natural look
            // float yawJitter   = 0; //(float)((Math.random() - 0.5) * 0.4); // Jitter can be distracting
            float pitchJitter = (float)((Math.random() - 0.5) * 0.25);

            // compute raw yaw error in [–180;+180)
            float dynamicT = getDynamicT(rawYaw, currentYaw);

            // finally lerp with the dynamic t
            newYaw = lerpAngle(currentYaw, rawYaw /* + yawJitter */, dynamicT);

            // Interpolate yaw and pitch
            newPitch = lerpAngle(currentPitch, desiredPitch + pitchJitter, pitchSpeed);

            // Keep pitch from tilting too far when very close
            if (distXZ < 3.0) { // If very close to the look-at target
                newPitch = MathHelper.clamp(newPitch, -20.0f, 35.0f); // Allow looking down a bit more, up a bit more
            } else if (distXZ < 5.0) {
                newPitch = MathHelper.clamp(newPitch, -30.0f, 45.0f);
            } else {
                newPitch = MathHelper.clamp(newPitch, -45.0f, 60.0f); // General pitch limits
            }
        }

        player.setYaw(newYaw);
        player.setPitch(newPitch);
    }

    private static float getDynamicT(float rawYaw, float currentYaw) {
        float rawDelta = ((rawYaw - currentYaw + 540f) % 360f) - 180f;
        float absDelta = Math.abs(rawDelta);

        // parameters
        float minT = 0.05f;   // base speed when almost aligned
        float maxT = 0.20f;   // top speed when way off
        float maxAngleForFullSpeed = 60f; // Angle at which turning speed is maxT

        // compute a normalized error in [0..1]
        float normalized = Math.min(absDelta / maxAngleForFullSpeed, 1f);

        // apply a non-linear curve: e.g. sin easing
        float curve = (float) Math.sin(normalized * (Math.PI / 2.0));


        return minT + (maxT - minT) * curve;
    }

    /**
     * Interpolates angles correctly across the ±180° wrap.
     * t in [0..1]
     */
    private static float lerpAngle(float a, float b, float t) {
        float delta = ((b - a + 540) % 360) - 180; // Shortest angle difference
        float result = a + delta * t;
        return result; // No need for final modulo if a and delta*t are within reasonable bounds, but can keep if issues arise
    }

    public void checkAndJump(MinecraftClient client) {
        if (jumpCooldown > 0 || !Objects.requireNonNull(client.player).isOnGround()) return;

        Vec3d lookVec = client.player.getRotationVec(1.0f); // Based on current camera
        BlockPos clientFeetPos = client.player.getBlockPos(); // Position of player's feet block
        Vec3d clientExactPos = client.player.getPos();


        // Determine the block directly in front of the player's feet at their Y level
        // This is a simplified check. A more robust check might involve player's bounding box.
        Vec3d checkOffset = lookVec.normalize().multiply(JUMP_CHECK_DISTANCE); // Normalize lookVec before multiplying
        BlockPos checkPos = BlockPos.ofFloored(clientExactPos.x + checkOffset.x, clientExactPos.y, clientExactPos.z + checkOffset.z);


        // If standing on farmland, the effective "ground" for jumping might be slightly higher relative to checkPos
        // However, checkPos is already at player's Y, so this should be fine.

        assert client.world != null;
        BlockState stateInFront = client.world.getBlockState(checkPos);
        BlockState stateInFrontAbove = client.world.getBlockState(checkPos.up());


        // More robust check for what constitutes an obstacle that needs a jump
        boolean isObstacle = false;
        if (!stateInFront.getCollisionShape(client.world, checkPos).isEmpty() && // Block in front has collision
                stateInFront.getBlock() != Blocks.WATER && // Not water
                !(stateInFront.getBlock() instanceof FluidBlock) && // Not any fluid
                !(stateInFront.getBlock() instanceof SignBlock) && // Not a sign
                !(stateInFront.getBlock() instanceof WallSignBlock) &&
                !(stateInFront.getBlock() instanceof AbstractBannerBlock) && // Not a banner
                !(stateInFront.getBlock() instanceof FlowerPotBlock) && // Not a flower pot
                !(stateInFront.getBlock() instanceof TripwireHookBlock) &&
                !(stateInFront.getBlock() instanceof TripwireBlock) &&
                !(stateInFront.getBlock() instanceof LeverBlock) &&
                !(stateInFront.getBlock() instanceof ButtonBlock) &&
                !(stateInFront.getBlock() instanceof TorchBlock) &&
                !(stateInFront.getBlock() instanceof WallTorchBlock) &&
                !(stateInFront.getBlock() instanceof RedstoneTorchBlock) &&
                //!(stateInFront.getBlock() instanceof RedstoneWallTorchBlock) &&
                !(stateInFront.getBlock() instanceof PressurePlateBlock) &&
                !(stateInFront.getBlock() instanceof CropBlock) && // Path through crops
                !(stateInFront.getBlock() instanceof FlowerBlock) && // Path through flowers
                !(stateInFront.getBlock() == Blocks.SHORT_GRASS) &&
                !(stateInFront.getBlock() == Blocks.TALL_GRASS) &&
                !(stateInFront.getBlock() == Blocks.FERN) &&
                !(stateInFront.getBlock() == Blocks.LARGE_FERN) &&
                !(stateInFront.getBlock() == Blocks.DEAD_BUSH) &&
                !(stateInFront.getBlock() instanceof CarpetBlock) &&
                !(stateInFront.getBlock() == Blocks.SNOW && stateInFront.get(SnowBlock.LAYERS) < SnowBlock.MAX_LAYERS) && // Path over thin snow
                !(stateInFront.getBlock() instanceof LadderBlock) && // Don't jump at ladders
                !(stateInFront.getBlock() instanceof VineBlock) // Don't jump at vines
        ) {
            // It's a collidable block that isn't one of the explicitly allowed "pass-through" or "climbable" types.
            // Now check if the space above it is clear for jumping into.
            if (stateInFrontAbove.getCollisionShape(client.world, checkPos.up()).isEmpty() ||
                    (stateInFrontAbove.getBlock() instanceof CarpetBlock) || // Can jump into space with carpet
                    (stateInFrontAbove.getBlock() == Blocks.SNOW && stateInFrontAbove.get(SnowBlock.LAYERS) < SnowBlock.MAX_LAYERS) // or thin snow
            ) {
                isObstacle = true;
            }
        }


        LOGGER.trace("Jump check: Obstacle in front at {}: {} (Block: {}). Space above clear: {}. Needs Jump: {}",
                checkPos, !stateInFront.getCollisionShape(client.world, checkPos).isEmpty(), stateInFront.getBlock(),
                stateInFrontAbove.getCollisionShape(client.world, checkPos.up()).isEmpty(), isObstacle);


        // 90% chance to jump if obstacle detected
        if (isObstacle && Math.random() < 0.9) {
            client.options.jumpKey.setPressed(true);
            lastJump = 0;
            jumpCooldown = JUMP_COOLDOWN_TICKS;
            LOGGER.debug("Attempting jump over obstacle at {}", checkPos);
        }
    }

    public void goAlongPath(List<Vec3d> waypoints) {
        if (waypoints == null || waypoints.isEmpty()) {
            LOGGER.warn("Attempted to follow empty path");
            stop();
            return;
        }

        // Store the new path
        this.path = new ArrayList<>(waypoints);
        this.currentPathIndex = 0;
        this.followingPath = true;

        // Set the first waypoint as the initial target for movement (camera will use getCameraLookAtTarget)
        // The static 'target' field is not actively used by core path following logic anymore.
        // if (!path.isEmpty()) {
        //     target = path.get(currentPathIndex); // Avoid using static target
        // }


        // Log the path details
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && !path.isEmpty()) { // Check if path is not empty before logging
            client.player.sendMessage(
                    Text.literal("Following path with " + path.size() + " waypoints"),
                    false
            );
        }
        if (!path.isEmpty()){
            LOGGER.info("Starting path with {} waypoints", path.size());
        }
    }

    /**
     * Completely clears all path data and stops any movement.
     * This should be called when toggling off automated features.
     */
    public void clearPathState() {
        // Clear movement state
        followingPath = false;
        path.clear();
        currentPathIndex = 0;
        // target = null; // Static field, clear if necessary, but prefer instance fields
        if (strategy != null) { // Clear strategy only when explicitly stopping all AI
            // strategy = null; // Let stop() handle strategy nullification if needed
        }


        // Release keyboard controls
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.options != null) { // Add null check for client and options
            if (client.options.forwardKey.isPressed()) {
                client.options.forwardKey.setPressed(false);
            }
            if (client.options.backKey.isPressed()) {
                client.options.backKey.setPressed(false);
            }
            if (client.options.leftKey.isPressed()) {
                client.options.leftKey.setPressed(false);
            }
            if (client.options.rightKey.isPressed()) {
                client.options.rightKey.setPressed(false);
            }
            if (client.options.jumpKey.isPressed()) {
                client.options.jumpKey.setPressed(false);
            }
            if (client.options.sprintKey.isPressed()) { // Also release sprint key
                client.options.sprintKey.setPressed(false);
            }
        }

        LOGGER.info("Path state cleared");
    }

    /**
     * Enhanced stop method to notify strategy when stopping
     */
    public void stop() {
        if (strategy != null) {
            strategy.onStop(this);
        }
        strategy = null; // Nullify strategy on stop
        clearPathState(); // clearPathState will clear path, index, followingPath
    }

    /**
     * Checks if the player is near a cliff or in a tight space requiring precise movement.
     * @param player The player entity.
     * @param currentWaypoint The waypoint the player is currently moving towards.
     * @return True if hazards are detected, false otherwise.
     */
    private boolean isEnvironmentHazardous(ClientPlayerEntity player, Vec3d currentWaypoint) {
        if (player.getWorld() == null) return false;

        BlockPos playerBlockPos = player.getBlockPos();
        Vec3d playerPos = player.getPos();

        // Determine general direction towards waypoint for side checks
        Vec3d directionToWaypoint = currentWaypoint.subtract(playerPos).normalize();
        if (directionToWaypoint.lengthSquared() < 0.01) { // If very close, use player's look vector
            directionToWaypoint = player.getRotationVec(1.0f);
        }

        // Calculate perpendicular vector for "sides"
        Vec3d sideVector = new Vec3d(-directionToWaypoint.z, 0, directionToWaypoint.x).normalize(); // Perpendicular in XZ plane

        // Check points to the left, right, and slightly in front
        Vec3d[] checkDirections = {
                sideVector,                     // Right
                sideVector.negate(),            // Left
                directionToWaypoint,            // Front (for tight corridors ahead)
                directionToWaypoint.add(sideVector).normalize(), // Front-Right
                directionToWaypoint.add(sideVector.negate()).normalize() // Front-Left
        };

        for (Vec3d checkDir : checkDirections) {
            for (double offset = 0.5; offset <= hazardCheckRadius; offset += 0.5) { // Check at different distances
                BlockPos checkPosBase = BlockPos.ofFloored(
                        playerPos.x + checkDir.x * offset,
                        playerPos.y, // Check at player's feet level first
                        playerPos.z + checkDir.z * offset
                );

                // --- Cliff Check ---
                // Check one block out from player's feet level
                BlockPos cliffScanStart = checkPosBase;
                boolean potentialCliff = true;
                for (int yOffset = 1; yOffset <= cliffDownScanDepth; yOffset++) {
                    BlockState state = player.getWorld().getBlockState(cliffScanStart.down(yOffset));
                    if (!state.isAir() && !(state.getBlock() instanceof FluidBlock)) { // Consider fluids as non-solid for cliff check
                        potentialCliff = false;
                        break;
                    }
                }
                if (potentialCliff) {
                    // To confirm it's a cliff, ensure the block at cliffScanStart itself is also air/fluid
                    // or that the player isn't standing right on the edge of a 1-block drop they can step down.
                    // A simple check: if the block directly at cliffScanStart is air, and we found a drop.
                    if (player.getWorld().getBlockState(cliffScanStart).isAir()) {
                        LOGGER.trace("Hazard detected: Cliff near {}", cliffScanStart);
                        return true; // Found a cliff
                    }
                }

                // --- Tight Wall Check ---
                // Check at feet level and head level
                for (int yLevel = 0; yLevel <= 1; yLevel++) { // 0 for feet, 1 for head
                    BlockPos wallCheckPos = checkPosBase.up(yLevel);
                    BlockState wallState = player.getWorld().getBlockState(wallCheckPos);
                    if (wallState.isSolid() && !isPassableBlockForTightCheck(wallState.getBlock())) {
                        LOGGER.trace("Hazard detected: Wall at {}", wallCheckPos);
                        return true; // Found a tight wall
                    }
                }
            }
        }
        return false; // No immediate hazards found
    }

    /**
     * Helper to determine if a block is considered "passable" when checking for tight spaces.
     * We don't want to consider things like grass or flowers as "walls".
     */
    private boolean isPassableBlockForTightCheck(Block block) {
        return block instanceof AirBlock ||
                block instanceof FluidBlock ||
                block instanceof PlantBlock || // Covers most crops, flowers, grass, ferns
                block instanceof SugarCaneBlock ||
                block instanceof VineBlock ||
                block instanceof CarpetBlock ||
                block instanceof SnowBlock || // Thin snow is passable
                block instanceof LadderBlock ||
                block instanceof SignBlock ||
                block instanceof AbstractBannerBlock ||
                block instanceof TorchBlock ||
                block instanceof ButtonBlock ||
                block instanceof LeverBlock ||
                block instanceof PressurePlateBlock ||
                block instanceof TripwireBlock ||
                block instanceof TripwireHookBlock;
    }

    // public static Vec3d getTarget() { // Static target is problematic
    //     return target;
    // }

    /**
     * Returns the current waypoint in the path based on the current index.
     * @return The current waypoint as a Vec3d, or null if the path is empty or index is out of bounds
     */
    public Vec3d getCurrentWaypoint() {
        if (path.isEmpty() || currentPathIndex >= path.size()) {
            return null;
        }
        return path.get(currentPathIndex);
    }

    /**
     * Increments the current path index to advance to the next waypoint.
     */
    public void incrementCurrentPathIndex() {
        currentPathIndex++;
        LOGGER.debug("Advanced to waypoint {} of {}", currentPathIndex, path.size());
    }

    /**
     * Gets the current index in the path.
     * @return The current index
     */
    public int getCurrentIndex() {
        return currentPathIndex;
    }

    /**
     * Gets the total size of the current path.
     * @return The number of waypoints in the path
     */
    public int getPathSize() {
        return path.size();
    }

    public MovementStrategy getStrategy() {
        return strategy;
    }
}