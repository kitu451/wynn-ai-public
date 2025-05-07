package net.natga999.wynn_ai.ai;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.MathHelper;
import net.natga999.wynn_ai.managers.PathingManager;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BasicPathAI {
    private static final Logger LOGGER = LoggerFactory.getLogger(BasicPathAI.class);

    private static final BasicPathAI INSTANCE = new BasicPathAI();
    public static BasicPathAI getInstance() { return INSTANCE; }

    private Vec3d target = null;
    private final double reachThresholdXY = 0.4;
    private final double reachThresholdZ = 0.7;

    private Double lastTargetY = null;
    private float pitchSmoothing = 0.25f; // Adjust this for responsiveness
    private int jumpCooldown = 0;
    private static final int JUMP_COOLDOWN_TICKS = 8;
    private static final double JUMP_CHECK_DISTANCE = 0.7;

    // Added path tracking variables
    private List<Vec3d> path = new ArrayList<>();
    private int currentPathIndex = 0;
    private boolean followingPath = false;

    public void tick() {
        if (PathingManager.getInstance().isPathing()) {
            if (jumpCooldown > 0) jumpCooldown--;
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayerEntity player = client.player;
            if (player == null) return;

            if (!followingPath || path == null || currentPathIndex >= path.size()) {
                //stop();
                return;
            }

            Vec3d currentTarget = path.get(currentPathIndex);
            this.target = currentTarget;

            Vec3d aimPoint = currentTarget.subtract(0, 1.0, 0);
            rotateCameraToward(aimPoint.add(0, 2, 0), client);
            updateMovementToward(aimPoint, client);

            // Calculate horizontal (XY) and vertical (Z) distances separately
            double distanceXY = Math.sqrt(
                    Math.pow(player.getX() - currentTarget.x, 2) +
                            Math.pow(player.getZ() - currentTarget.z, 2)
            );
            double distanceZ = Math.abs(player.getY() - currentTarget.y);

            // Check if we're close enough using separate thresholds
            if (distanceXY < reachThresholdXY && distanceZ < reachThresholdZ) {

                currentPathIndex++;

//                if (currentPathIndex + 1 < path.size()) {
//                    currentPathIndex++;
//                }

                // Reached the last point
                if (currentPathIndex >= path.size()) {
                    PathingManager.getInstance().setPathComplete(true);
                    stop();
                    player.sendMessage(Text.literal("Path complete."), false);
                    return;
                }

                this.target = path.get(currentPathIndex); // Move to next target
            }

            //player.sendMessage(Text.literal("Moving to: " + target + " | Distance: " + distance), false);
        } else {
            path = null;
        }
    }

    public void updateMovementToward(Vec3d targetPos, MinecraftClient client) {
        if (client.player == null || client.options == null) return;

        // Reset movement each tick
        client.options.forwardKey.setPressed(false);
        client.options.backKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        client.options.jumpKey.setPressed(false);

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

        // Random "bunny hop" (5% chance)
//        if (Math.random() < 0.005 && client.player.isOnGround()) {
//            client.options.jumpKey.setPressed(true);
//        }
    }

    private void rotateCameraToward(Vec3d targetPos, MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        Vec3d eyePos = player.getCameraPosVec(1.0f);
        Vec3d delta = targetPos.subtract(eyePos);
        double distXZ = Math.hypot(delta.x, delta.z);

        // Calculate raw angles
        float desiredYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0f;
        float desiredPitch = (float) -Math.toDegrees(Math.atan2(delta.y, distXZ));

        // Vertical movement analysis
        boolean verticalChange = lastTargetY == null || Math.abs(targetPos.y - lastTargetY) > 0.2;
        lastTargetY = targetPos.y;

        // Horizontal movement stabilization
        if (!verticalChange) {
            // When moving on flat ground, blend pitch toward horizon
            float horizonPitch = 25.0f; // Slightly below horizon for natural look
            desiredPitch = lerpAngle(desiredPitch, horizonPitch, 0.7f);
            pitchSmoothing = 0.35f; // Slower pitch adjustments
        } else {
            pitchSmoothing = 0.25f; // Normal speed for vertical changes
        }

        // Reduced jitter when on flat terrain
        float yawJitter = verticalChange ?
                (float) ((Math.random() - 0.5) * 1.5) :
                (float) ((Math.random() - 0.5) * 0.8);

        float pitchJitter = verticalChange ?
                (float) ((Math.random() - 0.5) * 1.2) :
                (float) ((Math.random() - 0.5) * 0.5);

        // Smooth interpolation
        float currentYaw = player.getYaw();
        float currentPitch = player.getPitch();

        float newYaw = lerpAngle(currentYaw, desiredYaw + yawJitter, 0.25f);
        float newPitch = lerpAngle(currentPitch, desiredPitch + pitchJitter, pitchSmoothing);

        // Horizon preservation
        if (!verticalChange && distXZ < 3.0) {
            // Keep pitch within ±15 degrees when moving horizontally
            newPitch = MathHelper.clamp(newPitch, -15.0f, 15.0f);
        }

        player.setYaw(newYaw);
        player.setPitch(newPitch);
    }

// Keep the existing lerpAngle implementation

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
        BlockPos checkPos = BlockPos.ofFloored(client.player.getPos()
                .add(lookVec.multiply(JUMP_CHECK_DISTANCE).x, 0, lookVec.multiply(JUMP_CHECK_DISTANCE).z));

        // Check if obstacle in movement direction
        assert client.world != null;
        BlockState state = client.world.getBlockState(checkPos);

        // Check if the block is wheat or potatoes, treat them as air
        boolean isCropBlock = state.getBlock() == Blocks.WHEAT || state.getBlock() == Blocks.POTATOES;

        // Only consider it an obstacle if it's not air and not a crop block
        boolean needsJump = !state.isAir() && !isCropBlock &&
                client.world.getBlockState(checkPos.up()).isAir();

        LOGGER.error("Obstacle detected: {}", needsJump);

        // 80% chance to jump if obstacle detected
        if (needsJump && Math.random() < 0.8) {
            client.options.jumpKey.setPressed(true);
            jumpCooldown = JUMP_COOLDOWN_TICKS;
        }
    }

    public void goTo(Vec3d pos) {
        this.target = pos;
    }

    public void goAlongPathBlockPos(List<Vec3d> blockPath) {
        if (blockPath == null || blockPath.isEmpty()) {
            LOGGER.warn("Attempted to follow empty path (BlockPos)");
            stop();
            return;
        }

        // Convert BlockPos to Vec3d using center of block
//        List<Vec3d> waypoints = blockPath.stream()
//                .map(BlockPos::toCenterPos)
//                .toList();


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
        }
    }

    public Vec3d getTarget() {
        return this.target;
    }
}