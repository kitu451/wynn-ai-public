package net.natga999.wynn_ai.managers;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.natga999.wynn_ai.ai.BasicPathAI;
import net.natga999.wynn_ai.path.PathFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class PathingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PathingManager.class);

    private static final PathingManager INSTANCE = new PathingManager();
    public static PathingManager getInstance() { return INSTANCE; }

    private boolean active = false;

    public void togglePathing() {
        active = !active;
        assert MinecraftClient.getInstance().player != null;
        MinecraftClient.getInstance().player.sendMessage(Text.literal("Pathing: " + (active ? "ON" : "OFF")), false);

        if (active) {
            // Get nearest resource node (e.g., from marker system)
            Vec3d pos = ResourceNodeManager.getClosestNode("Wheat"); // Example
            MinecraftClient.getInstance().player.sendMessage(Text.literal("pos: " + pos), false);

            if (pos != null) {
                ClientWorld world = MinecraftClient.getInstance().world;
                if (world != null) {
                    // Get player's current position
                    ClientPlayerEntity player = MinecraftClient.getInstance().player;
                    if (player != null) {
                        // Create a properly positioned chicken entity at the player's position
                        ChickenEntity ghost = new ChickenEntity(EntityType.CHICKEN, world);
                        ghost.refreshPositionAndAngles(
                                player.getX(),
                                player.getY(),
                                player.getZ(),
                                player.getYaw(),
                                player.getPitch()
                        );

                        // Set the entity position explicitly
                        ghost.setPosition(player.getPos());

                        // Create navigation with the correctly positioned entity
                        MobNavigation nav = new MobNavigation(ghost, world);
                        nav.setCanPathThroughDoors(true);
                        nav.setCanSwim(true);

                        // Set up the goal position
                        BlockPos goalPos = new BlockPos((int) pos.getX(), (int) pos.getY() - 3, (int) pos.getZ());

                        // Log current start and end positions for debugging
                        MinecraftClient.getInstance().player.sendMessage(
                                Text.literal("Finding path from " + player.getBlockPos() + " to " + goalPos),
                                false
                        );

                        // Create a pathfinder with a cache
                        PathFinder pathFinder = new PathFinder(world, 4, player.getBlockPos()); // Cache radius of 4 chunks
                        List<BlockPos> path = pathFinder.findPath(player.getBlockPos(), goalPos);

                        if (path == null) {
                            LOGGER.error("Pathfinding failed: No path to goal {}", goalPos);
                        } else {
                            LOGGER.error("Path found: {}", path);
                            BasicPathAI.getInstance().goAlongPathBlockPos(path.stream().map(BlockPos::toImmutable).collect(Collectors.toList()));
                        }
                    }
                }
            }

        } else {
            BasicPathAI.getInstance().stop();
        }
    }

    public boolean isPathing() {
        return active;
    }
}