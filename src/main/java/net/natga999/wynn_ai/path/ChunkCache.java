package net.natga999.wynn_ai.path;

import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.Map;

public class ChunkCache {
    private final int cacheRadius; // Radius in chunks
    private final ClientWorld world;
    private final BlockPos center;
    private final Map<ChunkPos, WorldChunk> cachedChunks; // Use a map for O(1) lookup

    public ChunkCache(ClientWorld world, BlockPos center, int cacheRadius) {
        this.world = world;
        this.center = center;
        this.cacheRadius = cacheRadius;
        this.cachedChunks = new HashMap<>(); // HashMap instead of ArrayList

        loadChunks();
    }

    private void loadChunks() {
        // Get the chunk position of the center
        ChunkPos centerChunkPos = new ChunkPos(center);

        // Load chunks within the radius (direct chunk coordinates)
        for (int x = -cacheRadius; x <= cacheRadius; x++) {
            for (int z = -cacheRadius; z <= cacheRadius; z++) {
                // Calculate the chunk position
                ChunkPos targetChunkPos = new ChunkPos(centerChunkPos.x + x, centerChunkPos.z + z);

                // Get the chunk
                WorldChunk chunk = world.getChunk(targetChunkPos.x, targetChunkPos.z);

                if (chunk != null) {
                    // Store with the ChunkPos as the key for fast lookup
                    cachedChunks.put(targetChunkPos, chunk);
                }
            }
        }
    }

    public boolean isWithinCacheBounds(BlockPos pos) {
        ChunkPos blockChunkPos = new ChunkPos(pos);
        ChunkPos centerChunkPos = new ChunkPos(center);

        return Math.abs(blockChunkPos.x - centerChunkPos.x) <= cacheRadius &&
                Math.abs(blockChunkPos.z - centerChunkPos.z) <= cacheRadius;
    }

    public BlockState getBlockState(BlockPos pos) {
        // Calculate the chunk position for this block
        ChunkPos blockChunkPos = new ChunkPos(pos);

        // Direct O(1) lookup from the map
        WorldChunk chunk = cachedChunks.get(blockChunkPos);

        if (chunk != null) {
            return chunk.getBlockState(pos);
        }

        return null; // Chunk not in cache
    }
}