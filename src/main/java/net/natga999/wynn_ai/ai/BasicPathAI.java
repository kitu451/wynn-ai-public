package net.natga999.wynn_ai.ai;

import net.natga999.wynn_ai.managers.PathingManager;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.MathHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class BasicPathAI {
    private static final Logger LOGGER = LoggerFactory.getLogger(BasicPathAI.class);

    private static final BasicPathAI INSTANCE = new BasicPathAI();
    public static BasicPathAI getInstance() { return INSTANCE; }

    private Vec3d target = null;
    private final double reachThresholdXZ = 0.6;
    private final double reachThresholdY = 1.3;

    private int jumpCooldown = 0;
    private int lastJump= 0;
    private static final int JUMP_COOLDOWN_TICKS = 8;
    private static final double JUMP_CHECK_DISTANCE = 0.7;

    // Added path tracking variables
    private List<Vec3d> path = new ArrayList<>();
    private int currentPathIndex = 0;
    private boolean followingPath = false;

    public void tick() {
        if (PathingManager.getInstance().isPathing()) {
            if (jumpCooldown > 0) jumpCooldown--;
            lastJump++;
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayerEntity player = client.player;
            if (player == null) return;

            if (!followingPath || path == null || currentPathIndex >= path.size() || PathingManager.getInstance().isPathComplete()) {
                //stop();
                return;
            }

            Vec3d currentTarget = path.get(currentPathIndex);
            this.target = currentTarget;

            Vec3d aimPoint = currentTarget.subtract(0, 1.0, 0);
            rotateCameraToward(aimPoint.add(0, 2, 0), client);
            updateMovementToward(aimPoint, client);

            // Calculate horizontal (XY) and vertical (Z) distances separately
            double distanceXZ = Math.sqrt(
                    Math.pow(player.getX() - currentTarget.x, 2) +
                            Math.pow(player.getZ() - currentTarget.z, 2)
            );
            double distanceY = Math.abs(player.getY() - currentTarget.y);

            boolean reachedCurrent = distanceXZ < reachThresholdXZ && distanceY < reachThresholdY;
            boolean reachedNext = isReachedNext(player);

            // Decide how far to advance
            if (reachedNext) {
                // Skip current and go straight to the one after next
                currentPathIndex += 2;
            } else if (reachedCurrent) {
                // Normal single-step advance
                currentPathIndex ++;
            }

            // Reached the last point
            if (currentPathIndex >= path.size()) {
                PathingManager.getInstance().setPathComplete(true);
                stop();
                player.sendMessage(Text.literal("Path complete."), false);
                return;
            }

            // Check if we're close enough to the final goal
            Vec3d finalTarget = path.getLast();
            double finalDistanceXZ = Math.sqrt(
                    Math.pow(player.getX() - finalTarget.x, 2) +
                            Math.pow(player.getZ() - finalTarget.z, 2)
            );
            double finalDistanceY = Math.abs(player.getY() - finalTarget.y);

            //to-do implement shorter XZ if trying to reach same goal > 3 in a row
            Random rand = new Random();
            double randomFactor = 0.9 + rand.nextDouble() * (1.6 - 0.9);
            if (finalDistanceXZ < randomFactor && finalDistanceY < 1.0) {
                if (!PathingManager.getInstance().isPathComplete()) {
                    PathingManager.getInstance().setPathComplete(true);
                    stop();
                    player.sendMessage(Text.literal("Path complete (close to goal)."), false);
                    aimPoint = path.getLast().subtract(0, 1.0, 0);
                    rotateCameraToward(aimPoint.add(0, 2, 0), client);
                    return;
                }
            }

            this.target = path.get(currentPathIndex); // Move to next target

            //player.sendMessage(Text.literal("Moving to: " + target + " | Distance: " + distance), false);
        } else {
            path = null;
        }
    }

    private boolean isReachedNext(ClientPlayerEntity player) {
        boolean reachedNext = false;
        if (currentPathIndex + 1 < path.size()) {
            Vec3d nextTarget = path.get(currentPathIndex + 1);
            double nextDistXZ = Math.hypot(
                    player.getX() - nextTarget.x,
                    player.getZ() - nextTarget.z
            );
            double nextDistY = Math.abs(player.getY() - nextTarget.y);
            reachedNext = nextDistXZ < reachThresholdXZ && nextDistY < reachThresholdY;
        }
        return reachedNext;
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

        if (PathingManager.getInstance().isMovingToNode()) {
            checkAndJump(client);
        }

        if (lastJump > 20) {
            client.options.sprintKey.setPressed(true);
        }

        // Random "bunny hop" (5% chance)
        if (Math.random() < 0.005 && client.player.isOnGround()) {
            client.options.jumpKey.setPressed(true);
        }
    }

    private void rotateCameraToward(Vec3d targetPos, MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

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
    private float lerpAngle(float a, float b, float t) {
        float delta = ((b - a + 540) % 360) - 180;
        float result = a + delta * t;
        return result % 360;
    }

    private void checkAndJump(MinecraftClient client) {
        if (jumpCooldown > 0 || !Objects.requireNonNull(client.player).isOnGround()) return;

        Vec3d lookVec = client.player.getRotationVec(1.0f);
        BlockPos clientPos = client.player.getBlockPos();
        Vec3d clientVecPos = client.player.getPos();
        assert client.world != null;
        if ( client.world.getBlockState(clientPos).getBlock() == Blocks.FARMLAND) {
            clientVecPos = client.player.getPos().add(0,1,0);
        }
        BlockPos checkPos = BlockPos.ofFloored(clientVecPos
                .add(lookVec.multiply(JUMP_CHECK_DISTANCE).x, 0, lookVec.multiply(JUMP_CHECK_DISTANCE).z));

        // Check if obstacle in movement direction
        assert client.world != null;
        BlockState state = client.world.getBlockState(checkPos);
        BlockState stateUp = client.world.getBlockState(checkPos.up());

        // Check if the block is wheat or potatoes, treat them as air
        boolean isCropBlock = state.getBlock() == Blocks.WHEAT || state.getBlock() == Blocks.POTATOES || state.getBlock() == Blocks.SHORT_GRASS;
        boolean isCropBlockUp = stateUp.getBlock() == Blocks.WHEAT || stateUp.getBlock() == Blocks.POTATOES || stateUp.getBlock() == Blocks.SHORT_GRASS;

        // Only consider it an obstacle if it's not air and not a crop block
        boolean needsJump = !state.isAir() && !isCropBlock && (stateUp.isAir() || isCropBlockUp);

        //LOGGER.error("NEEDS JUMP: {} - {} - {} - {} - {} ({})", needsJump, !state.isAir(), !isCropBlock, stateUp.isAir(), isCropBlockUp, state.getBlock().getName());
        LOGGER.debug("Obstacle detected: {} - {} - {}", needsJump, state.getBlock(), clientVecPos);

        // 80% chance to jump if obstacle detected
        if (needsJump && Math.random() < 0.8) {
            client.options.jumpKey.setPressed(true);
            lastJump = 0;
            jumpCooldown = JUMP_COOLDOWN_TICKS;
        }
    }

    public void goAlongPathBlockPos(List<Vec3d> blockPath) {
        if (blockPath == null || blockPath.isEmpty()) {
            LOGGER.warn("Attempted to follow empty path (BlockPos)");
            stop();
            return;
        }

        goAlongPath(blockPath);
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
            this.target = path.get(currentPathIndex);

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

    public void stop() {
        this.target = null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null) {
            client.options.forwardKey.setPressed(false);
            client.options.backKey.setPressed(false);
            client.options.leftKey.setPressed(false);
            client.options.rightKey.setPressed(false);
            client.options.jumpKey.setPressed(false);
            client.options.sprintKey.setPressed(false);
        }
    }

    public Vec3d getTarget() {
        return target;
    }
}