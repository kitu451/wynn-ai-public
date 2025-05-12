package net.natga999.wynn_ai.managers;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Direction;
import net.natga999.wynn_ai.ai.BasicPathAI;
import net.natga999.wynn_ai.detector.EntityDetector;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PathingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PathingManager.class);

    private static final PathingManager INSTANCE = new PathingManager();
    public static PathingManager getInstance() { return INSTANCE; }

    private boolean active = false;
    private List<Vec3d> path = null;
    private List<Vec3d> splinePath = null;
    private BlockPos goalPos = null;
    private boolean isFounding = false;
    private boolean pathComplete = false;
    private boolean useRightClickHarvest = false;

    private int dynamicWaitTicks;
    private int verifyStartTick = 0;
    private static final int MAX_VERIFY_ATTEMPTS = 20;

    private ResourceNodeManager.ResourceNode currentTargetNode;

    private int waitTicks = 0;

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
                    BasicPathAI.getInstance().goAlongPathBlockPos(splinePath);
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

                //todo clean uo
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
        EntityDetector entityDetector = new EntityDetector(3);
        List<Entity> entities = entityDetector.detectNearbyEntities(MinecraftClient.getInstance().player.getPos(), MinecraftClient.getInstance());

        for (Entity entity : entities) {
            if (entity instanceof DisplayEntity.TextDisplayEntity textEntity) {
                NbtCompound nbt = new NbtCompound();
                textEntity.writeNbt(nbt);

                String text = nbt.getString("text").replaceAll("§.", "");

                // Check for success indicators
                if (text.contains("Farming XP")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean verifyHarvestIndicator() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;

        // Scan nearby TextDisplayEntity instances
        EntityDetector detector = new EntityDetector(3);
        List<Entity> entities = detector.detectNearbyEntities(mc.player.getPos(), mc);

        for (Entity e : entities) {
            if (e instanceof DisplayEntity.TextDisplayEntity textEntity) {
                NbtCompound nbt = new NbtCompound();
                textEntity.writeNbt(nbt);

                String txt = nbt.getString("text")
                        .replaceAll("§.", "")
                        .trim();

                // must have [ and ] and Farming
                if (txt.contains("[") && txt.contains("]") && txt.contains("Farming")) {
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

    private void findAndStartPath() {
        // Get nearest resource node
        currentTargetNode = ResourceNodeManager.getClosestNode("Malt");
        if (currentTargetNode == null) {
            isFounding = false;
            return;
        }

        // Use the node's actual position
        Vec3d pos = new Vec3d(
                currentTargetNode.x,
                currentTargetNode.y,
                currentTargetNode.z
        );

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;

        if (player == null || world == null) return;

        // Set up the goal position
        goalPos = new BlockPos(
                (int) Math.floor(pos.getX()),
                (int) Math.floor(pos.getY()) - 2,
                (int) Math.floor(pos.getZ())
        );

        // Log current start and end positions for debugging
        MinecraftClient.getInstance().player.sendMessage(
                Text.literal("Finding path from " + player.getBlockPos() + " to " + goalPos),
                false
        );

        // Create a pathfinder with a cache
        PathFinder pathFinder = new PathFinder(world, 8, player.getBlockPos(), goalPos); // Cache radius of 4 chunks
        BlockPos playerPos = player.getBlockPos();
        Block blockBelow = world.getBlockState(playerPos).getBlock();

        // If standing on farmland, raise start position by 1
        if (blockBelow == Blocks.FARMLAND) {
            playerPos = playerPos.up(); // Adds +1 to the Y coordinate
        }

        List<Vec3d> rawPath = pathFinder.findPath(playerPos, goalPos);
        if (rawPath != null && !rawPath.isEmpty()) {
            // Replace the very first waypoint with the player's current exact cords
            Vec3d playerPosVec = player.getPos();

            // Figure out which block‐cell those cords live in
            BlockPos footPos = new BlockPos(
                    (int) playerPosVec.x,
                    (int) (playerPosVec.y - 0.5),   // since Vec3d y is centered at feet+0.5
                    (int) playerPosVec.z
            );

            // Look at the block at that position
            BlockState footState = world.getBlockState(footPos.down());

            // If that block is “empty” (air) or a shallow crop, lower you by one
            if ( footState.isAir()
                    || footState.getBlock() == Blocks.WHEAT
                    || footState.getBlock() == Blocks.POTATOES
                    || footState.getBlock() == Blocks.SHORT_GRASS ) {
                playerPosVec = playerPosVec.subtract(0, 1.0, 0);
            }

            rawPath.set(0, new Vec3d(playerPosVec.x, playerPosVec.y + 0.5, playerPosVec.z));

            this.path = rawPath;

            // NO FUNNELING
            LOGGER.debug("Vec3d path size: {}", path.size());

            int segments = path.size() <= 3 ? 16 : 8;
            this.splinePath = new ArrayList<>(CatmullRomSpline.createSpline(path, segments));

            LOGGER.debug("Spline path size: {}", splinePath.size());
            LOGGER.debug("Path found: {}, Spline path: {}", path, splinePath);

            pathComplete = false;

            currentState = HarvestState.START_PATH;
        } else {
            LOGGER.debug("Pathfinding failed: No path to goal {}", goalPos);
            currentState = HarvestState.WAITING; // delay retry
        }
        isFounding = false;
    }

    public void setPathComplete(Boolean complete) {
        pathComplete = complete;
    }

    public boolean isPathComplete() {
        return pathComplete;
    }

    public boolean isPathing() {
        return active;
    }

    public List<Vec3d> getCurrentPath() {
        return this.path;
    }

    public List<Vec3d> getSplinePath() {
        return this.splinePath;
    }

    public boolean isMovingToNode() {
        return currentState == HarvestState.MOVING_TO_NODE;
    }

    public void setHarvestButton(boolean useRightClick) {
        this.useRightClickHarvest = useRightClick;
    }
}