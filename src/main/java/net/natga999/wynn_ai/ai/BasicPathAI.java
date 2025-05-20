package net.natga999.wynn_ai.ai;

import net.natga999.wynn_ai.managers.HarvestPathManager;
import net.natga999.wynn_ai.strategies.CombatMovementStrategy;
import net.natga999.wynn_ai.strategies.HarvestMovementStrategy;
import net.natga999.wynn_ai.strategies.MovementStrategy;

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

public class BasicPathAI {
    private static final Logger LOGGER = LoggerFactory.getLogger(BasicPathAI.class);

    private static final BasicPathAI INSTANCE = new BasicPathAI();
    public static BasicPathAI getInstance() { return INSTANCE; }

    private static Vec3d target = null;
    private final double reachThresholdXZ = 1.0;
    private final double reachThresholdY = 1.3;

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

        // Let the strategy handle movement logic
        strategy.tick(this);

        // Let the strategy handle camera rotation
        MinecraftClient client = MinecraftClient.getInstance();
        if (strategy != null && client != null && client.player != null) {
            strategy.handleCameraRotation(this, client);
        }

        // Check if the strategy considers the movement complete
        if (strategy != null && strategy.isComplete(this)) {
            stop();
        }
    }

    /**
     * Common initialization for all path types
     */
    private void initPath(List<Vec3d> waypoints) {
        if (waypoints == null || waypoints.isEmpty()) {
            LOGGER.warn("Attempted to start path with empty waypoints");
            return;
        }

        this.path = new ArrayList<>(waypoints);
        this.currentPathIndex = 0;
        this.followingPath = true;

        LOGGER.info("Starting new path with {} waypoints", waypoints.size());
    }

    /** Called by your higher-level controller when a new harvest path is ready. */
    public void startHarvest(List<Vec3d> waypoints) {
        initPath(waypoints);
        this.strategy = new HarvestMovementStrategy();
    }

    /** Called by your higher-level controller when combat-movement should begin. */
    public void startCombatPath(List<Vec3d> waypoints) {
        initPath(waypoints);
        this.strategy = new CombatMovementStrategy();
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

        // First check if we've reached the current waypoint
        Vec3d currentWaypoint = path.get(currentPathIndex);
        double currentDistanceXZ = Math.sqrt(
                Math.pow(playerPos.x - currentWaypoint.x, 2) +
                        Math.pow(playerPos.z - currentWaypoint.z, 2)
        );
        double currentDistanceY = Math.abs(playerPos.y - currentWaypoint.y);

        boolean reachedCurrent = currentDistanceXZ < reachThresholdXZ && currentDistanceY < reachThresholdY;

        if (reachedCurrent) {
            return true;
        }

        // Then check if we've overshot and reached any of the next waypoints in the path
        for (int i = currentPathIndex + 1; i < path.size(); i++) {
            Vec3d nextWaypoint = path.get(i);
            double nextDistanceXZ = Math.sqrt(
                    Math.pow(playerPos.x - nextWaypoint.x, 2) +
                            Math.pow(playerPos.z - nextWaypoint.z, 2)
            );
            double nextDistanceY = Math.abs(playerPos.y - nextWaypoint.y);

            if (nextDistanceXZ < reachThresholdXZ && nextDistanceY < reachThresholdY) {
                // We've reached a future waypoint, skip to it
                currentPathIndex = i;
                LOGGER.info("Skipped to waypoint " + i + " as player overshot");
                return true;
            }
        }

        return false;
    }

    public void updateMovementToward(Vec3d targetPos, MinecraftClient client) {
        if (client.player == null || client.options == null) return;

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

        // If very close, do nothing
        if (dx * dx + dz * dz < 0.01) return;

        // Convert player yaw to radians, and invert it
        double yawRad = -Math.toRadians(client.player.getYaw());

        // Rotate (dx, dz) by –yaw to get local coordinates
        double localX = dx * Math.cos(yawRad) - dz * Math.sin(yawRad);
        double localZ = dx * Math.sin(yawRad) + dz * Math.cos(yawRad);

        // Decide which key to press
        if (localZ > Math.abs(localX)) {
            // Mostly in front
            client.options.forwardKey.setPressed(true);
        } else if (localZ < -Math.abs(localX)) {
            // Mostly behind
            client.options.backKey.setPressed(true);
        } else if (localX < 0) {
            // Mostly to the right
            client.options.rightKey.setPressed(true);
        } else {
            // Mostly to the left
            client.options.leftKey.setPressed(true);
        }

        checkAndJump(client);

        if (lastJump > 20) {
            client.options.sprintKey.setPressed(true);
        }

        // Random "bunny hop" (5% chance)
        if (Math.random() < 0.005 && client.player.isOnGround()) {
            //client.options.jumpKey.setPressed(true);
        }
    }

    public static void rotateCameraToward(Vec3d targetPos, MinecraftClient client, boolean isFinal) {
        ClientPlayerEntity player = client.player;
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
        LOGGER.debug("velocity: {}", player.getVelocity());
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
            float desiredPitch = lerpAngle(rawPitch, 25.0f, 0.7f);
            float pitchSpeed = 0.35f;

            // Small jitter for natural look
            float yawJitter   = 0; //(float)((Math.random() - 0.5) * 0.4);
            float pitchJitter = (float)((Math.random() - 0.5) * 0.25);

            // compute raw yaw error in [–180;+180)
            float dynamicT = getDynamicT(rawYaw, currentYaw);

            // finally lerp with the dynamic t
            newYaw = lerpAngle(currentYaw, rawYaw + yawJitter, dynamicT);

            // Interpolate yaw and pitch
            newPitch = lerpAngle(currentPitch, desiredPitch + pitchJitter, pitchSpeed);

            // Keep pitch from tilting too far when very close
            if (distXZ < 3.0) {
                newPitch = MathHelper.clamp(newPitch, -15.0f, 15.0f);
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
        float maxAngleForFullSpeed = 60f;

        // compute a normalized error in [0..1]
        float normalized = Math.min(absDelta / maxAngleForFullSpeed, 1f);

        // apply a non-linear curve: square it (you could also use Math.pow(normalized, 3) for cubic)
        float curve = (float) Math.sin(normalized * (float)Math.PI/2);

        // blend between minT and maxT via the curve

        return minT + (maxT - minT) * curve;
    }

    /**
     * Interpolates angles correctly across the ±180° wrap.
     * t in [0..1]
     */
    private static float lerpAngle(float a, float b, float t) {
        float delta = ((b - a + 540) % 360) - 180;
        float result = a + delta * t;
        return result % 360;
    }

    public void checkAndJump(MinecraftClient client) {
        if (jumpCooldown > 0 || !Objects.requireNonNull(client.player).isOnGround()) return;

        Vec3d lookVec = client.player.getRotationVec(1.0f);
        BlockPos clientPos = client.player.getBlockPos();
        Vec3d clientVecPos = client.player.getPos();
        assert client.world != null;
        if ( client.world.getBlockState(clientPos).getBlock() == Blocks.FARMLAND) {
            clientVecPos = client.player.getPos().add(0,1,0);
        }
        Vec3d forward = lookVec.multiply(JUMP_CHECK_DISTANCE);
        BlockPos checkPos = BlockPos.ofFloored(clientVecPos.add(forward.x, 0, forward.z));

        // Check if obstacle in movement direction
        assert client.world != null;
        BlockState state = client.world.getBlockState(checkPos);
        BlockState stateUp = client.world.getBlockState(checkPos.up());

        // Check if the block is wheat or potatoes, treat them as air
        Block block = state.getBlock();
        Block blockUp = stateUp.getBlock();
        boolean isCropBlock = block == Blocks.WHEAT || block == Blocks.POTATOES || block == Blocks.SHORT_GRASS || block == Blocks.CARROTS || block == Blocks.AZURE_BLUET || block == Blocks.BEETROOTS;
        boolean isCropBlockUp = blockUp == Blocks.WHEAT || blockUp == Blocks.POTATOES || blockUp == Blocks.SHORT_GRASS || blockUp == Blocks.CARROTS || blockUp == Blocks.AZURE_BLUET || blockUp == Blocks.BEETROOTS;
        boolean isLowSnowBlockUp = (blockUp == Blocks.SNOW && stateUp.get(SnowBlock.LAYERS) <= 3);
        boolean isSlab  = block instanceof SlabBlock;
        boolean isStair = block instanceof StairsBlock;
        boolean isCarpet = block instanceof CarpetBlock;
        boolean isCarpetUp = blockUp instanceof CarpetBlock;
        boolean isLowSnowBlock = (block == Blocks.SNOW && state.get(SnowBlock.LAYERS) <= 3);

        // Only consider it an obstacle if it's not air and not a crop block
        boolean needsJump = !state.isAir() && !isCropBlock && !isSlab && !isStair
                && !isLowSnowBlock && !isCarpet && (stateUp.isAir() || isCropBlockUp || isLowSnowBlockUp || isCarpetUp);

        LOGGER.error("Obstacle detected: {} - {} - {} - {}", needsJump, state.getBlock(), checkPos, clientVecPos);

        // 90% chance to jump if obstacle detected
        if (needsJump && Math.random() < 0.9) {
            client.options.jumpKey.setPressed(true);
            client.player.sendMessage(Text.of("JUMP!"), true);
            lastJump = 0;
            jumpCooldown = JUMP_COOLDOWN_TICKS;
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

        // Set the first waypoint as the initial target
        if (!path.isEmpty()) {
            target = path.get(currentPathIndex);

            // Log the path details
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(
                        Text.literal("Following path with " + path.size() + " waypoints"),
                        false
                );
            }

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
        target = null;
        strategy = null;

        // Release keyboard controls
        MinecraftClient client = MinecraftClient.getInstance();
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

        LOGGER.info("Path state cleared");
    }

    /**
     * Enhanced stop method to notify strategy when stopping
     */
    public void stop() {
        if (strategy != null) {
            strategy.onStop(this);
        }

        clearPathState();
    }

    public static Vec3d getTarget() {
        return target;
    }

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
}