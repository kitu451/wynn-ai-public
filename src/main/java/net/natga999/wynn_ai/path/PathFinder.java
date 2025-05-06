package net.natga999.wynn_ai.path;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        LOGGER.debug("PathFinder initialized with cache radius {} and max drop {}", cacheRadius, maxDrop);
    }

    public List<BlockPos> findPath(BlockPos start, BlockPos goal) {
        LOGGER.debug("Finding path from {} to {}", start, goal);

        Map<BlockPos, Double> gScore = new HashMap<>();
        gScore.put(start, 0.0);

        // Check if start and goal are within cache bounds
        if (!cache.isWithinCacheBounds(start)) {
            LOGGER.debug("Start position {} is outside cache bounds", start);
            return null;
        }

        if (!cache.isWithinCacheBounds(goal)) {
            LOGGER.debug("Goal position {} is outside cache bounds", goal);
            return null;
        }

        // Nodes to explore and the ones already explored
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(Node::getF));
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
                LOGGER.debug("Path search aborted - exceeded maximum path length ({} nodes explored)", MAX_PATH_LENGTH);
                return null; // Path too long, abort
            }

            Node current = openSet.poll();
            nodesExpanded++;

            // Check if goal is found
            if (current.getPos().equals(goal)) {
                List<BlockPos> path = reconstructPath(current);
                LOGGER.debug("Path found! Length: {}, Nodes expanded: {}, Iterations: {}", path.size(), nodesExpanded, iterations);
                return path;
            }

            closedSet.add(current.getPos());

            // Explore horizontal neighbors (N, S, E, W)
            int validNeighbors = 0;
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos horiz = current.getPos().offset(dir);

                // Check if within cache bounds
                if (!cache.isWithinCacheBounds(horiz)) {
                    LOGGER.debug("Neighbor {} is outside cache bounds", horiz);
                    continue;
                }

                BlockPos candidate = findGroundBelow(horiz);
                if (candidate == null) {
                    LOGGER.debug("No valid ground below {} (offset from {} in direction {})", horiz, current.getPos(), dir);
                    continue;            // too tall a drop or no ground
                }

                if (closedSet.contains(candidate)) {
                    LOGGER.debug("Candidate {} already explored", candidate);
                    continue;
                }

                if (!isSpaceClear(candidate)) {
                    LOGGER.debug("No clear space at candidate {}", candidate);
                    continue;
                }

                validNeighbors++;
                double tentativeG = current.getG() + movementCost(current.getPos(), candidate);
                if (gScore.containsKey(candidate) && tentativeG >= gScore.get(candidate)) {
                    continue; // We've already found a better or equal path
                }
                gScore.put(candidate, tentativeG);
                openSet.add(new Node(candidate, tentativeG, estimateDistance(candidate, goal), current));
            }

            // Consider jumping up one block if possible
            BlockPos upPos = current.getPos().up();
            if (cache.isWithinCacheBounds(upPos) && !closedSet.contains(upPos)) {
                // Can only jump up from a solid block
                BlockState currentBlock = cache.getBlockState(current.getPos().down());
                if (canJumpFrom(currentBlock)) {
                    // Check if space above is clear for jumping
                    if (isSpaceClear(upPos)) {
                        validNeighbors++;
                        double jumpCost = current.getG() + 1.5; // Jumping costs more
                        openSet.add(new Node(upPos, jumpCost, estimateDistance(upPos, goal), current));
                    } else {
                        LOGGER.debug("Can't jump up at {} - space not clear", current.getPos());
                    }
                } else {
                    LOGGER.debug("Can't jump up at {} - not standing on solid block", current.getPos());
                }
            }

            if (validNeighbors == 0 && iterations % 100 == 0) {
                LOGGER.debug("No valid neighbors for position {} after {} iterations", current.getPos(), iterations);
            }

            if (iterations % 1000 == 0) {
                LOGGER.debug("Pathfinding in progress - {} iterations, {} nodes in closed set, {} nodes in open set",
                        iterations, closedSet.size(), openSet.size());
            }
        }

        LOGGER.error("No path found after {} iterations, {} nodes expanded", iterations, nodesExpanded);
        return null; // No path found
    }

    private boolean canJumpFrom(BlockState state) {
        if (state == null) return false;

        return state.isSideSolidFullSquare(world, BlockPos.ORIGIN, Direction.UP)
                || state.getBlock() instanceof FarmlandBlock;
    }

    /** If horiz has no solid block beneath, scan down up to maxDrop. */
    private BlockPos findGroundBelow(BlockPos horiz) {
        for (int d = 0; d <= maxDrop; d++) {
            BlockPos below = horiz.down(d + 1);
            if (below.getY() < -64) {
                LOGGER.debug("Position {} is below world level", below);
                break;
            }

            // Check if this position is within our cache
            if (!cache.isWithinCacheBounds(below)) {
                LOGGER.debug("Position of ground below {} is outside cache bounds", below);
                return null;
            }

            BlockState bs = cache.getBlockState(below);
            if (bs == null) {
                LOGGER.error("No block state for position {}", below);
                return null;
            }

            // treat either a full‐square solid face or tilled farmland as ground
            boolean isSolidFace = bs.isSideSolidFullSquare(world, below, Direction.UP);
            boolean isFarmland = bs.getBlock() instanceof FarmlandBlock;

            if (isSolidFace || isFarmland) {
                BlockPos result = below.up();
                LOGGER.debug("Found ground at {} ({}), returning position just above: {}", below, bs, result);
                return result;  // return the block just above that ground
            }
        }
        LOGGER.debug("No suitable ground found below {} within max drop {}", horiz, maxDrop);
        return null;  // too far to drop or no ground found
    }

    /** Ensure that the two-block-tall space at pos is free. */
    private boolean isSpaceClear(BlockPos pos) {
        // Check if positions are within cache bounds
        if (!cache.isWithinCacheBounds(pos)) {
            LOGGER.debug("Position (isSpaceClear) {} is outside cache bounds", pos);
            return false;
        }

        if (!cache.isWithinCacheBounds(pos.up())) {
            LOGGER.debug("Position above {} is outside cache bounds", pos);
            return false;
        }

        // Fetch the BlockStates
        BlockState blockAt = cache.getBlockState(pos);
        BlockState blockAbove = cache.getBlockState(pos.up());
        if (blockAt == null || blockAbove == null) {
            LOGGER.debug("Missing block state at {} or {}", pos, pos.up());
            return false;
        }


        // Treat air, wheat or potatoes as “empty” at both positions
        boolean feetClear = blockAt.isAir()
                || blockAt.getBlock() == Blocks.WHEAT
                || blockAt.getBlock() == Blocks.POTATOES;
        boolean headClear = blockAbove.isAir()
                || blockAbove.getBlock() == Blocks.WHEAT
                || blockAbove.getBlock() == Blocks.POTATOES;

        boolean result = feetClear && headClear;
        if (!result) {
            LOGGER.debug("Space not clear at {}: block at position is {}, block above is {}",
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

        LOGGER.debug("Movement cost from {} to {}: {}. Base cost: {}, Y diff: {}",
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
            path.addFirst(current.getPos());
            current = current.getParent();
        }
        LOGGER.debug("Reconstructed path of length {}", path.size());
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