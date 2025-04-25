package net.natga999.wynn_ai.managers;

import net.minecraft.util.Hand;
import net.minecraft.util.math.Direction;
import net.natga999.wynn_ai.ai.BasicPathAI;
import net.natga999.wynn_ai.path.PathFinder;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class PathingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PathingManager.class);

    private static final PathingManager INSTANCE = new PathingManager();
    public static PathingManager getInstance() { return INSTANCE; }

    private boolean active = false;
    private List<BlockPos> path = null;
    private BlockPos goalPos = null;
    private boolean isFounding = false;
    private boolean pathComplete = false;

    private int waitTicks = 0;
    private static final int MAX_WAIT_TICKS = 100; // 5 seconds if 20 tps

    // Node harvesting states
    private enum HarvestState {
        FINDING_NODE,
        MOVING_TO_NODE,
        HARVESTING,
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
                    LOGGER.error("FOUNDing");
                    isFounding = true;
                    findAndStartPath();
                }
                break;

            case MOVING_TO_NODE: if (path != null) {
                LOGGER.error("Path found: {}", path);
                BasicPathAI.getInstance().goAlongPathBlockPos(path.stream().map(BlockPos::toImmutable).collect(Collectors.toList()));
            }
            currentState = HarvestState.HARVESTING;
            break;

            case HARVESTING:
                if (pathComplete) {
                    // Simulate click or trigger your "use" action here
                    assert MinecraftClient.getInstance().interactionManager != null;
                    MinecraftClient.getInstance().interactionManager.attackBlock(goalPos, Direction.DOWN);
                    MinecraftClient.getInstance().player.swingHand(Hand.MAIN_HAND); // visual arm swing
                    currentState = HarvestState.WAITING;
                    waitTicks = 0;
                    pathComplete = false;
                }
                break;

            case WAITING:
                waitTicks++;
                if (waitTicks >= MAX_WAIT_TICKS) {
                    currentState = HarvestState.FINDING_NODE;
                    ResourceNodeManager.clearNodes();
                }
                break;
        }
    }

    private void findAndStartPath() {
        // Get nearest resource node (e.g., from marker system)
        Vec3d pos = ResourceNodeManager.getClosestNode("Wheat");
        if (pos == null) {
            isFounding = false;
            return;
        }

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
        PathFinder pathFinder = new PathFinder(world, 4, player.getBlockPos()); // Cache radius of 4 chunks
        path = pathFinder.findPath(player.getBlockPos(), goalPos);
        if (path != null) {
            currentState = HarvestState.MOVING_TO_NODE;
        } else {
            LOGGER.error("Pathfinding failed: No path to goal {}", goalPos);
            currentState = HarvestState.WAITING; // delay retry
        }
        isFounding = false;
    }

    public void setPathComplete(Boolean complete) {
        pathComplete = complete;
    }

    public boolean isPathing() {
        return active;
    }
}