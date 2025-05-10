package net.natga999.wynn_ai.path;

import net.minecraft.block.*;
import net.minecraft.util.math.Vec3d;
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
    private static final double CORNER_OFFSET = 0.2; // amount to soften 90° turns

    public PathFinder(ClientWorld world, int cacheRadius, BlockPos start) {
        this(world, cacheRadius, start, 3); // Default max drop of 3 blocks
    }

    public PathFinder(ClientWorld world, int cacheRadius, BlockPos start, int maxDrop) {
        this.world = world;
        this.cache = new ChunkCache(world, start, cacheRadius);
        this.maxDrop = maxDrop;
        LOGGER.debug("PathFinder initialized with cache radius {} and max drop {}", cacheRadius, maxDrop);
    }

    public List<Vec3d> findPath(BlockPos start, BlockPos goal) {
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
                List<BlockPos> rawPath = reconstructPath(current);
                List<Vec3d> simplifiedPath = simplifyPath(rawPath);
                List<Vec3d> curvedPath = postProcessCorners(simplifiedPath);
                LOGGER.debug("Simplified path from {} to {} nodes", rawPath.size(), curvedPath.size());
                return curvedPath;
            }

            closedSet.add(current.getPos());

            // Explore 8 planar neighbors (N, NE, E, SE, S, SW, W, NW)
            int validNeighbors = 0;
            // Offsets for the 8 directions in XZ plane
            int[] dx = {  0,  1,  1,  1,  0, -1, -1, -1 };
            int[] dz = {  1,  1,  0, -1, -1, -1,  0,  1 };

            for (int i = 0; i < 8; i++) {
                BlockPos horiz = current.getPos().add(dx[i], 0, dz[i]);

                // within cache?
                if (!cache.isWithinCacheBounds(horiz)) continue;

                // drop to ground if needed
                BlockPos candidate = findGroundBelow(horiz);
                if (candidate == null) continue;

                // headspace at original horizontal must be clear (for jumps)
                if (!isSpaceClear(horiz.up())) continue;

                // avoid re‐exploring
                if (closedSet.contains(candidate)) continue;

                // ensure target feet & head are clear
                if (!isSpaceClear(candidate)) continue;

                // corner‐cut prevention: when moving diagonally, both axis steps must be clear
                if (Math.abs(dx[i]) == 1 && Math.abs(dz[i]) == 1) {
                    BlockPos sideA = current.getPos().add(dx[i], 0, 0);
                    BlockPos sideB = current.getPos().add(0, 0, dz[i]);
                    if (!isSpaceClear(sideA) || !isSpaceClear(sideB)) continue;
                }

                validNeighbors++;
                double tentativeG = current.getG() + movementCost(current.getPos(), candidate);
                if (gScore.containsKey(candidate) && tentativeG >= gScore.get(candidate)) {
                    continue;
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

    public List<Vec3d> simplifyPath(List<BlockPos> rawPath) {
        // Early exit: if too few waypoints, convert all to Vec3d and return
        if (rawPath.size() <= 2) {
            List<Vec3d> direct = new ArrayList<>(rawPath.size());
            for (BlockPos p : rawPath) direct.add(toVec3(p));
            return direct;
        }

        // We'll build directly a Vec3d list so we can use fractional offsets
        List<Vec3d> simplified = new ArrayList<>();
        // Start with first point
        Vec3d lastVec = toVec3(rawPath.getFirst());
        simplified.add(lastVec);

        int currentIndex = 0;
        while (currentIndex < rawPath.size() - 1) {
            int farthestValid = currentIndex + 1;
            BlockPos lastAddedBlock = rawPath.get(currentIndex);
            lastVec = simplified.getLast();

            // 1) Find the farthest straight-line reach
            for (int i = currentIndex + 1; i < rawPath.size(); i++) {
                BlockPos candidate = rawPath.get(i);
                if (isRaycastWalkable(lastAddedBlock, candidate)) {
                    farthestValid = i;
                } else {
                    break;
                }
            }

            // 2) Next block target
            BlockPos nextBlock = rawPath.get(farthestValid);
            Vec3d nextVec = toVec3(nextBlock);

            // 3) Handle big drops: detect using block Y
            int dy = lastAddedBlock.getY() - nextBlock.getY();
            if (dy >= 2) {
                // Compute a 50% offset in Vec3d space
                Vec3d direction = nextVec.subtract(lastVec);
                Vec3d edgeVec = lastVec.add(direction.multiply(0.5));
                edgeVec = new Vec3d(edgeVec.x, lastVec.y, edgeVec.z);
                simplified.add(edgeVec);

                // Adjust nextVec toward nextNext if exists
                if (farthestValid + 1 < rawPath.size()) {
                    BlockPos nextNextBlock = rawPath.get(farthestValid + 1);
                    Vec3d nextNextVec = toVec3(nextNextBlock);

                    // Compute full vector, then drop the Y component
                    Vec3d toNextNext = nextNextVec.subtract(nextVec);
                    Vec3d toNextNextXZ = new Vec3d(toNextNext.x, 0, toNextNext.z);

                    double horizontalDist = Math.hypot(toNextNextXZ.x, toNextNextXZ.z);
                    if (horizontalDist > 0) {
                        // e.g. move 50% of one block toward nextNext in XZ
                        double fraction = 1.0;
                        Vec3d offset = toNextNextXZ.multiply(fraction / horizontalDist);

                        // Only adjust X and Z—keep Y the same
                        nextVec = new Vec3d(
                                nextVec.x + offset.x,
                                nextVec.y,
                                nextVec.z + offset.z
                        );
                    }
                }
            }

            // 4) Add the actual next
            simplified.add(nextVec);
            currentIndex = farthestValid;
        }

        return simplified;
    }

    /**
     * Convert BlockPos to Vec3d, centering within the block
     */
    private Vec3d toVec3(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    /**
     * Post-process waypoints to soften 90° corners by replacing the corner point
     * with a point offset from the corner in both directions.
     */
    private List<Vec3d> postProcessCorners(List<Vec3d> path) {
        if (path.size() < 3) return path;
        List<Vec3d> result = new ArrayList<>();
        result.add(path.getFirst());

        for (int i = 1; i < path.size() - 1; i++) {
            Vec3d prev = path.get(i - 1);
            Vec3d curr = path.get(i);
            Vec3d next = path.get(i + 1);
            Vec3d dirPrev = curr.subtract(prev).normalize();
            Vec3d dirNext = next.subtract(curr).normalize();

            // Detect orthogonal turn (dot product ~0)
            if (Math.abs(dirPrev.dotProduct(dirNext)) < 0.01) {
                // Replace corner with a single point offset from both directions
                Vec3d offset = curr.subtract(dirPrev.multiply(CORNER_OFFSET))
                        .add(dirNext.multiply(CORNER_OFFSET));
                result.add(offset);
            } else {
                result.add(curr);
            }
        }

        result.add(path.getLast());
        return result;
    }

    private boolean isRaycastWalkable(BlockPos start, BlockPos end) {
        List<BlockPos> line = getBlocksBetween(start, end);
        for (BlockPos pos : line) {
            // Ensure position is within cached chunks
            if (!cache.isWithinCacheBounds(pos)) {
                return false;
            }

            // Check ground stability
            BlockPos groundPos = pos.down();
            if (!isGroundWalkable(groundPos)) {
                return false;
            }

            // Check if the space at this position is clear (feet and head)
            if (!isSpaceClear(pos)) {
                return false;
            }
        }
        return true;
    }

    private boolean isGroundWalkable(BlockPos pos) {
        BlockState state = cache.getBlockState(pos);
        return state != null &&
                (state.isSideSolidFullSquare(world, pos, Direction.UP)
                        || state.getBlock() instanceof StairsBlock
                        || state.getBlock() instanceof SlabBlock
                        || state.getBlock() instanceof FarmlandBlock);
    }

    /**
     * Voxel traversal that never skips orthogonal neighbors on diagonal moves.
     * Based on Amanatides & Woo, but handles ties by inserting both axis‐steps.
     */
    private List<BlockPos> getBlocksBetween(BlockPos start, BlockPos end) {
        List<BlockPos> blocks = new ArrayList<>();
        double x0 = start.getX() + 0.5, y0 = start.getY() + 0.5, z0 = start.getZ() + 0.5;
        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        double dz = end.getZ() - start.getZ();

        int stepX = dx>0?1:-1, stepY = dy>0?1:-1, stepZ = dz>0?1:-1;
        double tDeltaX = Math.abs(1.0 / dx);
        double tDeltaY = Math.abs(1.0 / dy);
        double tDeltaZ = Math.abs(1.0 / dz);

        double tMaxX = (stepX>0
                ? (Math.floor(x0)+1 - x0) * tDeltaX
                : (x0 - Math.floor(x0))   * tDeltaX);
        double tMaxY = (stepY>0
                ? (Math.floor(y0)+1 - y0) * tDeltaY
                : (y0 - Math.floor(y0))   * tDeltaY);
        double tMaxZ = (stepZ>0
                ? (Math.floor(z0)+1 - z0) * tDeltaZ
                : (z0 - Math.floor(z0))   * tDeltaZ);

        int ix = start.getX(), iy = start.getY(), iz = start.getZ();

        while (true) {
            blocks.add(new BlockPos(ix, iy, iz));
            if (ix==end.getX() && iy==end.getY() && iz==end.getZ()) break;

            // Find the smallest tMax
            double min = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));

            boolean stepXNow = tMaxX == min;
            boolean stepYNow = tMaxY == min;
            boolean stepZNow = tMaxZ == min;

            // If two (or three) axes tie, we’ll step each—and insert the orthogonal blocks first.
            // e.g. stepping X and Z diagonally, insert (ix+stepX,iy,iz) and (ix,iy,iz+stepZ)
            if (stepXNow && stepYNow) {
                // insert both axis edges
                blocks.add(new BlockPos(ix + stepX, iy, iz));
                blocks.add(new BlockPos(ix, iy + stepY, iz));
                ix += stepX; iy += stepY;
                tMaxX += tDeltaX; tMaxY += tDeltaY;
            }
            else if (stepXNow && stepZNow) {
                blocks.add(new BlockPos(ix + stepX, iy, iz));
                blocks.add(new BlockPos(ix, iy, iz + stepZ));
                ix += stepX; iz += stepZ;
                tMaxX += tDeltaX; tMaxZ += tDeltaZ;
            }
            else if (stepYNow && stepZNow) {
                blocks.add(new BlockPos(ix, iy + stepY, iz));
                blocks.add(new BlockPos(ix, iy, iz + stepZ));
                iy += stepY; iz += stepZ;
                tMaxY += tDeltaY; tMaxZ += tDeltaZ;
            }
            else if (stepXNow) {
                tMaxX += tDeltaX;
                ix += stepX;
            }
            else if (stepYNow) {
                tMaxY += tDeltaY;
                iy += stepY;
            }
            else { // stepZNow
                tMaxZ += tDeltaZ;
                iz += stepZ;
            }
        }

        return blocks;
    }

    private boolean canJumpFrom(BlockState state) {
        if (state == null) return false;

        return state.isSideSolidFullSquare(world, BlockPos.ORIGIN, Direction.UP)
                || state.getBlock() instanceof StairsBlock
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

            if (hasTopCollision(below)) {
                BlockPos result = below.up();
                LOGGER.debug("Found ground at {}, returning position just above: {}", below, result);
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
                || blockAt.getBlock() == Blocks.POTATOES
                || blockAt.getBlock() == Blocks.SHORT_GRASS;
        boolean headClear = blockAbove.isAir()
                || blockAbove.getBlock() == Blocks.WHEAT
                || blockAbove.getBlock() == Blocks.POTATOES
                || blockAt.getBlock() == Blocks.SHORT_GRASS;

        boolean result = feetClear && headClear;
        if (!result) {
            LOGGER.debug("Space not clear at {}: block at position is {}, block above is {}",
                    pos, blockAt, blockAbove);
        }

        return result;
    }

    /**
     * @return true if there is any collision geometry at this block's top face.
     */
    private boolean hasTopCollision(BlockPos pos) {
        BlockState state = cache.getBlockState(pos);
        if (state == null) return false;

        return state.isSideSolidFullSquare(world, pos, Direction.UP)
                || state.getBlock() instanceof FarmlandBlock
                || state.getBlock() instanceof SlabBlock
                || state.getBlock() instanceof StairsBlock;
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

    private boolean isOnGround(BlockPos pos) {
        BlockPos below = pos.down();
        if (!cache.isWithinCacheBounds(below)) {
            return false;
        }

        BlockState state = cache.getBlockState(below);
        return state.isSideSolidFullSquare(world, below, Direction.UP)
                || state.getBlock() instanceof StairsBlock
                || state.getBlock() instanceof SlabBlock
                || state.getBlock() instanceof FarmlandBlock;
    }

    private List<BlockPos> reconstructPath(Node goalNode) {
        List<BlockPos> rawPath = new ArrayList<>();
        Node current = goalNode;
        while (current != null) {
            rawPath.addFirst(current.getPos());
            current = current.getParent();
        }

        // Filter out mid-air nodes
        List<BlockPos> groundPath = new ArrayList<>();
        for (BlockPos pos : rawPath) {
            if (isOnGround(pos)) {
                groundPath.add(pos);
            }
        }

        LOGGER.debug("Reconstructed path of length {}", groundPath.size());
        return groundPath;
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