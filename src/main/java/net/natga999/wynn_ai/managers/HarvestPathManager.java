package net.natga999.wynn_ai.managers;

import net.natga999.wynn_ai.ai.BasicPathAI;
import net.natga999.wynn_ai.path.PathFinder;
import net.natga999.wynn_ai.utility.CatmullRomSpline;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class HarvestPathManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(HarvestPathManager.class);

    private static final HarvestPathManager INSTANCE = new HarvestPathManager();
    public static HarvestPathManager getInstance() { return INSTANCE; }

    private boolean active = false;
    private List<Vec3d> path = null;
    private List<Vec3d> splinePath = null;
    private BlockPos goalPos = null;
    private boolean isFounding = false;
    private boolean pathComplete = false;
    private boolean useRightClickHarvest = false;
    private static BlockPos originalGoalPos;

    private int dynamicWaitTicks;
    private int verifyStartTick = 0;
    private static final int MAX_VERIFY_ATTEMPTS = 20;

    private ResourceNodeManager.ResourceNode currentTargetNode;

    private int waitTicks = 0;

    public boolean isActive() {
        return active;
    }

    // Node harvesting states
    private enum HarvestState {
        FINDING_NODE,
        START_PATH,
        MOVING_TO_NODE,
        HARVESTING,
        VERIFYING,
        WAITING
    }

    private HarvestState currentState = HarvestState.FINDING_NODE;

    public void togglePathing() {
        active = !active;
        assert MinecraftClient.getInstance().player != null;
        MinecraftClient.getInstance().player.sendMessage(Text.literal("Pathing: " + (active ? "ON" : "OFF")), false);

        if (!active) {
            BasicPathAI.getInstance().stop();
            currentState = HarvestState.FINDING_NODE;
            waitTicks = 0;
        }
    }

    public void tick() {
        if (!active) return;

        switch (currentState) {
            case FINDING_NODE:
                if (!isFounding) {
                    LOGGER.debug("Founding node");
                    isFounding = true;
                    findAndStartPath();
                }
                break;

            case START_PATH:
                LOGGER.debug("START PATH");
                if (splinePath != null) {
                    pathComplete = false;
                    BasicPathAI.getInstance().startHarvest(splinePath);
                    currentState = HarvestState.MOVING_TO_NODE;
                }
                break;

            case MOVING_TO_NODE:
                LOGGER.debug("MOVING TO NODE");
                if (pathComplete) {
                    path = null;
                    splinePath = null;
                    currentState = HarvestState.HARVESTING;
                }
                break;

            //todo add state to aim to goal before harvest
            case HARVESTING:
                LOGGER.debug("HARVESTING");
                performHarvestAction();
                currentState = HarvestState.VERIFYING;
                waitTicks = 0;
                verifyStartTick = 0;
                break;

            case VERIFYING:
                LOGGER.debug("VERIFYING HARVEST");

                // Check nearby text entities for success indicators
                boolean success = verifyHarvestSuccess();
                boolean progress = verifyHarvestIndicator();

                //todo clean up
                if (!progress && !success) {
                    verifyStartTick++;
                } else {
                    verifyStartTick = 0;
                }
                if (verifyStartTick >= MAX_VERIFY_ATTEMPTS) {
                    dynamicWaitTicks = new Random().nextInt(10);
                    currentState = HarvestState.WAITING;
                }
                if (success) {
                    // Mark node as harvested
                    if (currentTargetNode != null) {
                        ResourceNodeManager.markHarvested(currentTargetNode);
                    }
                    dynamicWaitTicks = 2 + new Random().nextInt(10);
                    currentState = HarvestState.WAITING;
                }
                break;

            case WAITING:
                LOGGER.debug("WAITING");
                waitTicks++;
                if (waitTicks >= dynamicWaitTicks) {
                    currentState = HarvestState.FINDING_NODE;
                }
                break;
        }
    }

    private boolean verifyHarvestSuccess() {
        // Get nearby text display entities
        assert MinecraftClient.getInstance().player != null;

        List<Entity> entities = new ArrayList<>();

        BlockPos originalGoal = getOriginalGoalPos();
        Vec3d vec = new Vec3d(
                originalGoal.getX(),
                originalGoal.getY() + 2,
                originalGoal.getZ()
        );

        assert MinecraftClient.getInstance().world != null;
        for (Entity entity : MinecraftClient.getInstance().world.getEntities()) { // Iterate through all entities
            if (entity.getPos().isInRange(vec, 2)) { // Check if inside radius
                entities.add(entity);
            }
        }
        for (Entity entity : entities) {
            if (entity instanceof DisplayEntity.TextDisplayEntity textEntity) {
                NbtCompound nbt = new NbtCompound();
                textEntity.writeNbt(nbt);

                String text = nbt.getString("text").replaceAll("§.", "");

                // Check for success indicators
                if (text.contains("Farming XP") || text.contains("Mining XP")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean verifyHarvestIndicator() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;

        List<Entity> entities = new ArrayList<>();

        BlockPos originalGoal = getOriginalGoalPos();
        Vec3d vec = new Vec3d(
                originalGoal.getX(),
                originalGoal.getY() + 2,
                originalGoal.getZ()
        );

        assert MinecraftClient.getInstance().world != null;
        for (Entity entity : MinecraftClient.getInstance().world.getEntities()) { // Iterate through all entities
            if (entity.getPos().isInRange(vec, 2)) { // Check if inside radius
                entities.add(entity);
            }
        }

        for (Entity e : entities) {
            if (e instanceof DisplayEntity.TextDisplayEntity textEntity) {
                NbtCompound nbt = new NbtCompound();
                textEntity.writeNbt(nbt);

                String txt = nbt.getString("text")
                        .replaceAll("§.", "")
                        .trim();

                // must have [ and ] and Farming
                if (txt.contains("[") && txt.contains("]") && (txt.contains("Farming") || txt.contains("Mining"))) {
                    return true;
                }
            }
        }

        return false;
    }

    private void performHarvestAction() {
        MinecraftClient mc = MinecraftClient.getInstance();
        assert mc.interactionManager != null;
        assert mc.player            != null;
        assert mc.world             != null;

        if (useRightClickHarvest) {
            //right click
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        } else {
            //left click
            mc.interactionManager.attackBlock(goalPos, Direction.DOWN);
        }
        mc.player.swingHand(Hand.MAIN_HAND); // visual arm swing
    }

    private static final List<Vec3i> NEIGHBOUR_OFFSETS = List.of(
            new Vec3i( 0, 0,  0),  // original
            new Vec3i( 1, 0,  0),
            new Vec3i(-1, 0,  0),
            new Vec3i( 0, 0,  1),
            new Vec3i( 0, 0, -1),
            new Vec3i( 1, 0,  1),
            new Vec3i( 1, 0, -1),
            new Vec3i(-1, 0,  1),
            new Vec3i(-1, 0, -1),
            new Vec3i( 1, 1,  0),
            new Vec3i(-1, 1,  0),
            new Vec3i( 0, 1,  1),
            new Vec3i( 0, 1, -1),
            new Vec3i( 1, 1,  1),
            new Vec3i( 1, 1, -1),
            new Vec3i(-1, 1,  1),
            new Vec3i(-1, 1, -1)
    );

    private void findAndStartPath() {
        // Get nearest resource node
        currentTargetNode = ResourceNodeManager.getClosestNode(HarvestingManager.getActiveResource());
        if (currentTargetNode == null) {
            isFounding = false;
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) return;

        // Get original target position
        Vec3d targetPos = new Vec3d(
                currentTargetNode.x,
                currentTargetNode.y,
                currentTargetNode.z
        );

        // Create base goal position
        BlockPos baseGoal = new BlockPos(
                (int) Math.floor(targetPos.x),
                (int) Math.floor(targetPos.y) - 2,
                (int) Math.floor(targetPos.z)
        );

        originalGoalPos = baseGoal;

        //TODO fix stopping earlier before reaching goalPos
        //really dont want to stop early when near and because of that stack in barrier upon harvesting
        // 2) Check if baseGoal is “free” and try it first
//        if (isPositionFree(world, baseGoal)) {
//            List<Vec3d> path = tryPath(player, world, baseGoal);
//            if (path != null) {
//                startWithPath(path);
//                return;
//            }
//        }

        // 3) If we get here, baseGoal was blocked or path failed → fallback
        List<BlockPos> candidates = NEIGHBOUR_OFFSETS.stream()
                .map(off -> baseGoal.add(off.getX(), off.getY(), off.getZ()))
                .filter(p -> isPositionFree(world, p))
                .toList();

        List<List<Vec3d>> validPaths = new ArrayList<>();

        for (BlockPos cand : candidates) {
            List<Vec3d> p = tryPath(player, world, cand);
            if (p != null) validPaths.add(p);
        }

        if (!validPaths.isEmpty()) {
            List<Vec3d> best = validPaths.stream()
                    .min(Comparator.comparingDouble(this::calculatePathLength))
                    .get();
            startWithPath(best);
        } else {
            LOGGER.debug("Pathfinding failed: no path to {}", baseGoal);
            currentState = HarvestState.WAITING;
        }
        isFounding = false;
    }

    // Helper: check if a blockpos is not solid/barrier
    private boolean isPositionFree(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        // If there's no collision shape, it's essentially air/fluids/etc.
        return state.getCollisionShape(world, pos).isEmpty();
    }


    // Helper: try a single path, returning null if no path
    private List<Vec3d> tryPath(ClientPlayerEntity player, ClientWorld world, BlockPos goal) {
        BlockPos start = adjustPlayerStartPosition(player, world);
        PathFinder pf = new PathFinder(world, 8, start, goal);
        return pf.findPath(start, goal);
    }

    // Helper: kick off your path and spline
    private void startWithPath(List<Vec3d> path) {
        assert MinecraftClient.getInstance().player != null;
        assert MinecraftClient.getInstance().world != null;
        Vec3d exact = getAdjustedPlayerPosition(MinecraftClient.getInstance().player, MinecraftClient.getInstance().world);
        path.set(0, exact.add(0, 0.5, 0));
        this.path       = path;
        this.splinePath = CatmullRomSpline.createSpline(path, calculateSegmentCount());
        this.goalPos    = BlockPos.ofFloored(splinePath.getLast());
        pathComplete    = false;
        currentState    = HarvestState.START_PATH;
        isFounding      = false;
    }

    private BlockPos adjustPlayerStartPosition(ClientPlayerEntity player, ClientWorld world) {
        BlockPos pos = player.getBlockPos();
        if (world.getBlockState(pos.down()).getBlock() == Blocks.FARMLAND) {
            return pos.up();
        }
        return pos;
    }

    private double calculatePathLength(List<Vec3d> path) {
        double length = 0;
        for (int i = 1; i < path.size(); i++) {
            length += path.get(i).distanceTo(path.get(i - 1));
        }
        return length;
    }

    private int calculateSegmentCount() {
        return path.size() <= 3 ? 16 : 8;
    }

    private Vec3d getAdjustedPlayerPosition(ClientPlayerEntity player, ClientWorld world) {
        Vec3d pos = player.getPos();
        BlockPos footPos = new BlockPos(
                (int) pos.x,
                (int) (pos.y - 0.5),
                (int) pos.z
        );

        BlockState groundBlock = world.getBlockState(footPos.down());
        if (groundBlock.isAir() || isShallowCrop(groundBlock.getBlock())) {
            return pos.subtract(0, 1.0, 0);
        }
        return pos;
    }

    private boolean isShallowCrop(Block block) {
        return block == Blocks.WHEAT ||
                block == Blocks.POTATOES ||
                block == Blocks.SHORT_GRASS;
    }

    public void setPathComplete(Boolean complete) {
        pathComplete = complete;
    }

    public boolean isPathComplete() {
        return pathComplete;
    }

    public List<Vec3d> getCurrentPath() {
        return this.path;
    }

    public List<Vec3d> getSplinePath() {
        return this.splinePath;
    }

    public void setHarvestButton(boolean useRightClick) {
        this.useRightClickHarvest = useRightClick;
    }

    public static BlockPos getOriginalGoalPos() {
        return originalGoalPos;
    }

    public void setActive(boolean active) {
        if (this.active == active) return; // No change

        this.active = active;
        assert MinecraftClient.getInstance().player != null;
        MinecraftClient.getInstance().player.sendMessage(Text.literal("Harvest Pathing: " + (this.active ? "ON" : "OFF")), false);

        if (!this.active) {
            BasicPathAI.getInstance().stop(); // Stop current BasicPathAI movement
            currentState = HarvestState.FINDING_NODE; // Reset state
            waitTicks = 0;
            // any other cleanup needed when disabling
        } else {
            // any setup needed when enabling (though FINDING_NODE should kick it off)
            isFounding = false; // Ensure it tries to find a node on next tick if activated
        }
    }
}