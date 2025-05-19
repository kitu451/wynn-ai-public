package net.natga999.wynn_ai.managers.combat;

import net.natga999.wynn_ai.managers.combat.enums.CombatState;
import net.natga999.wynn_ai.ai.BasicPathAI;
import net.natga999.wynn_ai.path.PathFinder;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class CombatManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CombatManager.class);

    private static final double TARGET_DETECTION_RANGE = 30.0; // Blocks
    private static final double ATTACK_RANGE = 5.0;
    private TextDisplayEntity currentTarget;
    private Vec3d targetPos;
    private boolean active = false;
    private boolean isInAttackRange = false;
    private CombatState state = CombatState.SEARCH;
    private List<Vec3d> path = null;

    private static final String TARGET_NAME = "Sheep";

    private static final CombatManager INSTANCE = new CombatManager();
    public static CombatManager getInstance() { return INSTANCE; }

    public void toggleCombat() {
        active = !active;
        assert MinecraftClient.getInstance().player != null;
        MinecraftClient.getInstance().player.sendMessage(Text.literal("Combat: " + (active ? "ON" : "OFF")), false);

        if (!active) {
            targetPos = null;
            BasicPathAI.getInstance().stop();
            //current state update
        }
    }

    public void tick() {
        if (!active) return;

        switch (state) {
            case AWAIT -> idling();
            case SEARCH -> startSearch();
            case APPROACH -> handleApproach();
            case ATTACK -> handleAttack();
            //case EVADE -> handleEvade();
        }
    }

    private void idling() {
        targetPos = null;
        state = CombatState.SEARCH;
    }

    private void startSearch() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        // Find the closest hostile mob
        currentTarget = findClosestEnemy(player);
        if (currentTarget != null) {
            state = CombatState.APPROACH;
        }
    }

    private void handleApproach() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (currentTarget != null && client.player != null && client.world != null) {
            targetPos = currentTarget.getPos().subtract(0, 1, 0);

            // Check if we're already in range to attack
            if (inAttackRange(targetPos) && isAimedAt(targetPos, 10.0f)) {
                // Stop pathing and switch to attack state
                BasicPathAI.getInstance().stop();
                isInAttackRange = true;
                state = CombatState.ATTACK;
                path = null;
                LOGGER.info("Target in attack range - switching to attack state");
                return;
            }

            // If we're not already pathing to the target, find a new path
            if (path == null) {
                isInAttackRange = false;
                LOGGER.info("Finding path to target at {}", targetPos);

                // Convert player position and target position to BlockPos
                BlockPos playerPos = client.player.getBlockPos();
                BlockPos targetBlockPos = new BlockPos((int)targetPos.getX(), (int)targetPos.getY(), (int)targetPos.getZ());

                // Create pathfinder and find path
                PathFinder pathFinder = new PathFinder(client.world, 9, playerPos, targetBlockPos);
                path = pathFinder.findPath(playerPos, targetBlockPos);

                if (path != null && !path.isEmpty()) {
                    // Tell BasicPathAI to follow this path
                    BasicPathAI.getInstance().startCombatPath(path);
                    LOGGER.info("Path found with {} waypoints", path.size());
                } else {
                    LOGGER.warn("No path found to target");
                    // If no path is found, try direct movement (as fallback)
                    //BasicPathAI.getInstance().updateMovementToward(targetPos, client);
                }
            }

            // BasicPathAI will handle movement in its tick method
            // Camera rotation will be handled by HudRenderCallback
        } else {
            // Target lost, go back to search
            BasicPathAI.getInstance().stop();
            state = CombatState.SEARCH;
            LOGGER.info("Target lost - switching to search state");
        }
    }

    private void handleAttack() {
        //validate that mob still alive and near, otherwise target = null and back to idle
        if (!validateTarget()) {
            targetPos = null;
            state = CombatState.SEARCH;
        }
        if (inAttackRange(targetPos)) {
            hitMob(); // Simulate left-click attack
        }
    }

    /**
     * Returns true if currentTarget is non-null, alive, and still within detection range.
     * If it ever returns false, you should clear or re-acquire your target.
     */
    public boolean validateTarget() {

        MinecraftClient client = MinecraftClient.getInstance();

        if (currentTarget == null) return false;

        ClientPlayerEntity player = client.player;
        if (player == null) return false;

        // Still alive?
        if (!currentTarget.isAlive()) return false;

        // Still in range?
        double sqDist = currentTarget.squaredDistanceTo(player);
        if (sqDist > TARGET_DETECTION_RANGE * TARGET_DETECTION_RANGE) return false;

        // Optionally: still within line-of-sight?
        return player.canSee(currentTarget);
    }

    private boolean inAttackRange(Vec3d targetPos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || targetPos == null) return false;

        // use the eye position so you account for vertical aim
        Vec3d eyePos = mc.player.getCameraPosVec(1.0f);
        double distance = eyePos.distanceTo(targetPos);

        return distance <= ATTACK_RANGE;
    }

    private void hitMob() {
        // Simulate left-click attack
        assert MinecraftClient.getInstance().interactionManager != null;
        MinecraftClient.getInstance().interactionManager.attackBlock(BlockPos.ofFloored(targetPos), Direction.DOWN);
        assert MinecraftClient.getInstance().player != null;
        MinecraftClient.getInstance().player.swingHand(Hand.MAIN_HAND);
    }

    private TextDisplayEntity findClosestEnemy(ClientPlayerEntity player) {
        // Get all nearby mobs in 12-block radius
        Box detectionBox = player.getBoundingBox().expand(TARGET_DETECTION_RANGE);
        List<TextDisplayEntity> mobs = player.getWorld().getEntitiesByClass(
                TextDisplayEntity.class,
                detectionBox,
                e -> isHostile(e) && e.isAlive()
        );

        // Return closest mob
        return mobs.stream()
                .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)))
                .orElse(null);
    }

    private boolean isHostile(TextDisplayEntity entity) {
        // 1) Dump its NBT
        NbtCompound nbt = new NbtCompound();
        entity.writeNbt(nbt);

        // 2) Must have a "text" field
        if (!nbt.contains("text")) return false;

        // 3) Parse that JSON
        String jsonText = nbt.getString("text");
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(jsonText);
        } catch (Exception e) {
            return false; // invalid JSON
        }
        if (!parsed.isJsonObject()) return false;
        JsonObject root = parsed.getAsJsonObject();

        // 4) Look in "extra" array for the first text element
        JsonArray extra = root.has("extra") ? root.getAsJsonArray("extra") : null;
        if (extra == null) return false;

        for (JsonElement part : extra) {
            if (!part.isJsonObject()) continue;
            JsonObject obj = part.getAsJsonObject();
            if (!obj.has("text")) continue;

            String txt = obj.get("text").getAsString();
            if (TARGET_NAME.equals(txt)) {
                return true;
            }
            // if it has its own "extra", you could recurse here...
        }

        return false;
    }

    /**
     * Returns true if the player’s view is within ±thresholdDegrees of pointing at targetPos.
     */
    private boolean isAimedAt(Vec3d targetPos, float thresholdDegrees) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return false;

        // 1) compute vector from eye to target
        Vec3d eye = player.getCameraPosVec(1.0f);
        Vec3d delta = targetPos.subtract(eye);

        // 2) compute the “ideal” angles
        double dxz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float idealYaw   = (float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float idealPitch = (float)-Math.toDegrees(Math.atan2(delta.y, dxz));

        // 3) get current angles
        float yaw   = player.getYaw();
        float pitch = player.getPitch();

        // 4) compute the smallest difference across wrap‐around
        float yawDiff   = wrapAngle(idealYaw   - yaw);
        float pitchDiff = wrapAngle(idealPitch - pitch);

        return Math.abs(yawDiff)   <= thresholdDegrees
                && Math.abs(pitchDiff) <= thresholdDegrees;
    }

    public static void rotateCameraToward(Vec3d targetPos, MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        // 1. Calculate directional vectors
        Vec3d eyePos = player.getCameraPosVec(1.0f);
        Vec3d delta = targetPos.subtract(eyePos);
        double distXZ = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

        // 2. Calculate target angles
        float rawYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0f;
        float rawPitch = (float) -Math.toDegrees(Math.atan2(delta.y, distXZ));

        // 3. Get current angles
        float currentYaw = player.getYaw();
        float currentPitch = player.getPitch();

        // 4. Calculate dynamic interpolation speed
        float dynamicT = getDynamicT(rawYaw, currentYaw);

        // 5. Apply smoothed rotation
        float newYaw = lerpAngle(currentYaw, rawYaw, dynamicT);
        float newPitch = lerpAngle(currentPitch, rawPitch, dynamicT * 0.7f); // Slower pitch adjustment

        // 6. Update player view
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

    /** Wraps any angle to the range [–180 … +180] */
    private float wrapAngle(float angle) {
        angle = (angle + 180) % 360;
        if (angle < 0) angle += 360;
        return angle - 180;
    }

    public Vec3d getTargetPos () {
        return targetPos;
    }

    public boolean isInCombat () {
        return active;
    }

    public boolean isInAttackRange () {
        return isInAttackRange;
    }

    public List<Vec3d> getCurrentPath () {
        return path;
    }
}