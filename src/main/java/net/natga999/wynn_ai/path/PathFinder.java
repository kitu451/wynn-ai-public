package net.natga999.wynn_ai.path;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;

public class PathFinder {
    private static final Logger LOGGER = LoggerFactory.getLogger(PathFinder.class);
    private final ClientWorld world;
    private final ChunkCache cache;
    private final int maxDrop;  // maximum safe drop height
    private static final int MAX_PATH_LENGTH = 1000; // Maximum number of nodes to explore

    public PathFinder(ClientWorld world, int cacheRadius, BlockPos start) {
        this(world, cacheRadius, start, 3); // Default max drop of 3 blocks
    }

    public PathFinder(ClientWorld world, int cacheRadius, BlockPos start, int maxDrop) {
        this.world = world;
        this.cache = new ChunkCache(world, start, cacheRadius);
        this.maxDrop = maxDrop;
        LOGGER.error("PathFinder initialized with cache radius {} and max drop {}", cacheRadius, maxDrop);
    }

    public List<BlockPos> findPath(BlockPos start, BlockPos goal) {
        LOGGER.error("Finding path from {} to {}", start, goal);

        // Check if start and goal are within cache bounds
        if (!cache.isWithinCacheBounds(start)) {
            LOGGER.error("Start position {} is outside cache bounds", start);
            return null;
        }

        if (!cache.isWithinCacheBounds(goal)) {
            LOGGER.error("Goal position {} is outside cache bounds", goal);
            return null;
        }

        // Nodes to explore and the ones already explored
        PriorityQueue<Node> openSet = new PriorityQueue<>((a, b) -> Double.compare(a.getF(), b.getF()));
        HashSet<BlockPos> closedSet = new HashSet<>();

        // Start node
        Node startNode = new Node(start, 0, estimateDistance(start, goal));
        openSet.add(startNode);

        int iterations = 0;
        int nodesExpanded = 0;

        // Path finding loop
        while (!openSet.isEmpty()) {
            iterations++;

            // Check if we've explored too many nodes
            if (closedSet.size() > MAX_PATH_LENGTH) {
                LOGGER.error("Path search aborted - exceeded maximum path length ({} nodes explored)", MAX_PATH_LENGTH);
                return null; // Path too long, abort
            }

            Node current = openSet.poll();
            nodesExpanded++;

            // Check if goal is found
            if (current.getPos().equals(goal)) {
                List<BlockPos> path = reconstructPath(current);
                LOGGER.error("Path found! Length: {}, Nodes expanded: {}, Iterations: {}", path.size(), nodesExpanded, iterations);
                return path;
            }

            closedSet.add(current.getPos());

            // Explore horizontal neighbors (N, S, E, W)
            int validNeighbors = 0;
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos horiz = current.getPos().offset(dir);

                // Check if within cache bounds
                if (!cache.isWithinCacheBounds(horiz)) {
                    LOGGER.error("Neighbor {} is outside cache bounds", horiz);
                    continue;
                }

                BlockPos candidate = findGroundBelow(horiz);
                if (candidate == null) {
                    LOGGER.error("No valid ground below {} (offset from {} in direction {})", horiz, current.getPos(), dir);
                    continue;            // too tall a drop or no ground
                }

                if (closedSet.contains(candidate)) {
                    LOGGER.error("Candidate {} already explored", candidate);
                    continue;
                }

                if (!isSpaceClear(candidate)) {
                    LOGGER.error("No clear space at candidate {}", candidate);
                    continue;
                }

                validNeighbors++;
                double tentativeG = current.getG() + movementCost(current.getPos(), candidate);
                openSet.add(new Node(candidate, tentativeG, estimateDistance(candidate, goal), current));
            }

            // Consider jumping up one block if possible
            BlockPos upPos = current.getPos().up();
            if (cache.isWithinCacheBounds(upPos) && !closedSet.contains(upPos)) {
                // Can only jump up from a solid block
                BlockState currentBlock = cache.getBlockState(current.getPos().down());
                if (currentBlock != null && currentBlock.isSideSolidFullSquare(world, current.getPos().down(), Direction.UP)) {
                    // Check if space above is clear for jumping
                    if (isSpaceClear(upPos)) {
                        validNeighbors++;
                        double jumpCost = current.getG() + 1.5; // Jumping costs more
                        openSet.add(new Node(upPos, jumpCost, estimateDistance(upPos, goal), current));
                    } else {
                        LOGGER.error("Can't jump up at {} - space not clear", current.getPos());
                    }
                } else {
                    LOGGER.error("Can't jump up at {} - not standing on solid block", current.getPos());
                }
            }

            if (validNeighbors == 0 && iterations % 100 == 0) {
                LOGGER.error("No valid neighbors for position {} after {} iterations", current.getPos(), iterations);
            }

            if (iterations % 1000 == 0) {
                LOGGER.error("Pathfinding in progress - {} iterations, {} nodes in closed set, {} nodes in open set",
                        iterations, closedSet.size(), openSet.size());
            }
        }

        LOGGER.error("No path found after {} iterations, {} nodes expanded", iterations, nodesExpanded);
        return null; // No path found
    }

    /** If horiz has no solid block beneath, scan down up to maxDrop. */
    private BlockPos findGroundBelow(BlockPos horiz) {
        for (int d = 0; d <= maxDrop; d++) {
            BlockPos below = horiz.down(d + 1);
            if (below.getY() < -64) {
                LOGGER.error("Position {} is below world level", below);
                break;
            }

            // Check if this position is within our cache
            if (!cache.isWithinCacheBounds(below)) {
                LOGGER.error("Position {} is outside cache bounds", below);
                return null;
            }

            BlockState bs = cache.getBlockState(below);
            if (bs == null) {
                LOGGER.error("No block state for position {}", below);
                return null;
            }

            if (bs.isSideSolidFullSquare(world, below, Direction.UP)) {
                BlockPos result = below.up();
                LOGGER.error("Found ground at {} ({}), returning position just above: {}", below, bs, result);
                return result;  // return the block just above that ground
            }
        }
        LOGGER.error("No suitable ground found below {} within max drop {}", horiz, maxDrop);
        return null;  // too far to drop or no ground found
    }

    /** Ensure that the two-block-tall space at pos is free. */
    private boolean isSpaceClear(BlockPos pos) {
        // Check if positions are within cache bounds
        if (!cache.isWithinCacheBounds(pos)) {
            LOGGER.error("Position {} is outside cache bounds", pos);
            return false;
        }

        if (!cache.isWithinCacheBounds(pos.up())) {
            LOGGER.error("Position above {} is outside cache bounds", pos);
            return false;
        }

        BlockState blockAt = cache.getBlockState(pos);
        if (blockAt == null) {
            LOGGER.error("No block state for position {}", pos);
            return false;
        }

        BlockState blockAbove = cache.getBlockState(pos.up());
        if (blockAbove == null) {
            LOGGER.error("No block state for position {}", pos.up());
            return false;
        }

        boolean result = blockAt.isAir() && blockAbove.isAir();
        if (!result) {
            LOGGER.error("Space not clear at {}: block at position is {}, block above is {}",
                    pos, blockAt, blockAbove);
        }

        return result;
    }

    /** Cost: use Euclidean or custom if you want to penalize drops/jumps. */
    private double movementCost(BlockPos from, BlockPos to) {
        // Base cost is distance
        double baseCost = Math.sqrt(from.getSquaredDistance(to));

        // Add penalty for vertical movement
        int yDiff = to.getY() - from.getY();
        double finalCost;

        if (yDiff < 0) {
            // Dropping down is cheaper than flat movement
            finalCost = baseCost * 0.8;
        } else if (yDiff > 0) {
            // Going up (jumping) is more expensive
            finalCost = baseCost * 1.5;
        } else {
            finalCost = baseCost;
        }

        LOGGER.error("Movement cost from {} to {}: {}. Base cost: {}, Y diff: {}",
                from, to, finalCost, baseCost, yDiff);
        return finalCost;
    }

    private double estimateDistance(BlockPos start, BlockPos end) {
        // Using Euclidean distance as heuristic
        return Math.sqrt(start.getSquaredDistance(end));
    }

    private List<BlockPos> reconstructPath(Node goalNode) {
        List<BlockPos> path = new ArrayList<>();
        Node current = goalNode;
        while (current != null) {
            path.add(0, current.getPos());
            current = current.getParent();
        }
        LOGGER.error("Reconstructed path of length {}", path.size());
        return path;
    }

    // Node class for A* algorithm
    private static class Node {
        private final BlockPos pos;
        private final double g; // Cost from start
        private final double h; // Estimated cost to goal
        private final Node parent;

        public Node(BlockPos pos, double g, double h) {
            this.pos = pos;
            this.g = g;
            this.h = h;
            this.parent = null;
        }

        public Node(BlockPos pos, double g, double h, Node parent) {
            this.pos = pos;
            this.g = g;
            this.h = h;
            this.parent = parent;
        }

        public BlockPos getPos() {
            return pos;
        }

        public double getG() {
            return g;
        }

        public double getF() {
            return g + h;
        }

        public Node getParent() {
            return parent;
        }
    }
}