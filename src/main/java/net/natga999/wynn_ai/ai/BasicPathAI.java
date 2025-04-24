package net.natga999.wynn_ai.ai;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import net.natga999.wynn_ai.managers.PathingManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class BasicPathAI {
    private static final Logger LOGGER = LoggerFactory.getLogger(BasicPathAI.class);

    private static final BasicPathAI INSTANCE = new BasicPathAI();
    public static BasicPathAI getInstance() { return INSTANCE; }

    private Vec3d target = null;
    private final double reachThreshold = 1.0;

    // Added path tracking variables
    private List<Vec3d> path = new ArrayList<>();
    private int currentPathIndex = 0;
    private boolean followingPath = false;

    public void tick() {
        if (PathingManager.getInstance().isPathing()) {
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

            double distance = player.getPos().distanceTo(currentTarget);

            if (distance < reachThreshold) {
                currentPathIndex++;

                if (currentPathIndex + 1 < path.size()) {
                    currentPathIndex++;
                }

                // Reached the last point
                if (currentPathIndex >= path.size()) {
                    stop();
                    player.sendMessage(Text.literal("Path complete."), false);
                    return;
                }

                this.target = path.get(currentPathIndex); // Move to next target
            }

            player.sendMessage(Text.literal("Moving to: " + target + " | Distance: " + distance), false);
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
    }

    private void rotateCameraToward(Vec3d targetPos, MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        // Eye position (player’s eye height)
        Vec3d eyePos = player.getCameraPosVec(1.0f);

        // Vector from eye to target
        Vec3d delta = targetPos.subtract(eyePos);

        // Horizontal distance
        double distXZ = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

        // Raw yaw & pitch (in degrees)
        float desiredYaw  = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float desiredPitch= (float) (-Math.toDegrees(Math.atan2(delta.y, distXZ)));

        // Add a tiny random jitter (±1.0° max)
        float yawJitter   = (float) ((Math.random() - 0.5) * 2.0);   // random in [-1,1]
        float pitchJitter = (float) ((Math.random() - 0.5) * 2.0);

        // Smoothly interpolate from current to desired (optional)
        float currentYaw   = player.getYaw();
        float currentPitch = player.getPitch();

        float newYaw   = lerpAngle(currentYaw,  desiredYaw + yawJitter,   0.2f);
        float newPitch = lerpAngle(currentPitch,desiredPitch + pitchJitter, 0.2f);

        // Apply
        player.setYaw(newYaw);
        player.setPitch(newPitch);
    }

    /**
     * Interpolates angles correctly across the ±180° wrap.
     * t in [0..1]
     */
    private float lerpAngle(float a, float b, float t) {
        float delta = ((b - a + 540) % 360) - 180;
        return a + delta * t;
    }

    public void goTo(Vec3d pos) {
        this.target = pos;
    }

    public void goAlongPathBlockPos(List<BlockPos> blockPath) {
        if (blockPath == null || blockPath.isEmpty()) {
            LOGGER.warn("Attempted to follow empty path (BlockPos)");
            stop();
            return;
        }

        // Convert BlockPos to Vec3d using center of block
        List<Vec3d> waypoints = blockPath.stream()
                .map(BlockPos::toCenterPos)
                .toList();

        goAlongPath(waypoints);
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
}