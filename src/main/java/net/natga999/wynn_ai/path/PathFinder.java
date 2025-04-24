package net.natga999.wynn_ai.path;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;

public class PathFinder {
    private final ClientWorld world;
    private final ChunkCache cache;

    public PathFinder(ClientWorld world, int cacheRadius, BlockPos start) {
        this.world = world;
        this.cache = new ChunkCache(world, start, cacheRadius);
    }

    public List<BlockPos> findPath(BlockPos start, BlockPos goal) {
        // Nodes to explore and the ones already explored
        PriorityQueue<Node> openSet = new PriorityQueue<>((a, b) -> Double.compare(a.getF(), b.getF()));
        HashSet<BlockPos> closedSet = new HashSet<>();

        // Start node
        Node startNode = new Node(start, 0, estimateDistance(start, goal));
        openSet.add(startNode);

        // Path finding loop
        while (!openSet.isEmpty()) {
            Node current = openSet.poll();

            // Check if goal is found
            if (current.getPos().equals(goal)) {
                return reconstructPath(current);
            }

            closedSet.add(current.getPos());

            // Explore neighbors
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = current.getPos().offset(dir);
                if (closedSet.contains(neighborPos)) continue;

                // Check if the neighbor is within the cache bounds
                if (!cache.isWithinCacheBounds(neighborPos)) continue;

                // Check if the block is walkable
                if (!isBlockNavigable(neighborPos)) continue;

                double tentativeG = current.getG() + 1; // Assume movement cost is 1 (adjust for diagonals if needed)
                Node neighbor = new Node(neighborPos, tentativeG, estimateDistance(neighborPos, goal), current);

                openSet.add(neighbor);
            }
        }

        return null; // No path found
    }

    private boolean isBlockNavigable(BlockPos pos) {
        // 1. Check if the block at the position is clear/walkable
        BlockState blockAtPos = cache.getBlockState(pos);
        if (blockAtPos == null || !blockAtPos.isAir()) return false;

        // 2. Check if the block above has clearance
        BlockState blockAbovePos = cache.getBlockState(pos.up());
        if (blockAbovePos == null || !blockAbovePos.isAir()) return false;

        // 3. Check if there's solid ground below
        BlockState blockBelowPos = cache.getBlockState(pos.down());
        if (blockBelowPos == null) return false;

        // Only allow positions where the block below is solid
        return blockBelowPos.isSideSolidFullSquare(world, pos.down(), Direction.UP);
    }

    private double estimateDistance(BlockPos start, BlockPos end) {
        return start.getSquaredDistance(end); // Heuristic (squared distance for efficiency)
    }

    private List<BlockPos> reconstructPath(Node goalNode) {
        List<BlockPos> path = new ArrayList<>();
        Node current = goalNode;
        while (current != null) {
            path.add(0, current.getPos());
            current = current.getParent();
        }
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