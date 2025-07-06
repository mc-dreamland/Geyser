package org.geysermc.geyser.session.cache;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import lombok.Setter;
import org.geysermc.geyser.level.block.type.Block;
import org.geysermc.geyser.level.chunk.GeyserChunk;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.ChunkUtils;
import org.geysermc.geyser.util.MathUtils;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.DataPalette;

public class ChunkCache {
    private final boolean cache;
    private final Long2ObjectMap<GeyserChunk> chunks;
    /** Chunks waiting to be deleted in a throttled manner. */
    private final Long2ObjectMap<GeyserChunk> waitDeleteChunks;

    @Setter
    private int minY;
    @Setter
    private int heightY;

    @Setter
    private long lastChangeWorldTime = -1L;

    /**
     * Number of chunks purged from {@link #waitDeleteChunks} per cleanup run.
     * Adjust according to your performance requirements.
     */
    private static final int CHUNKS_TO_REMOVE_PER_CLEANUP = 6;

    public ChunkCache(GeyserSession session) {
        this.cache = !session.getGeyser().getWorldManager().hasOwnChunkCache();
        this.chunks = cache ? new Long2ObjectOpenHashMap<>() : null;
        this.waitDeleteChunks = cache ? new Long2ObjectOpenHashMap<>() : null;
    }

    public void addToCache(int x, int z, DataPalette[] chunks) {
        if (!cache) {
            return;
        }

        long chunkPosition = MathUtils.chunkPositionToLong(x, z);
        // If this chunk was previously scheduled for deletion, cancel that.
        waitDeleteChunks.remove(chunkPosition);

        GeyserChunk geyserChunk = GeyserChunk.from(chunks);
        this.chunks.put(chunkPosition, geyserChunk);
    }

    /**
     * Doesn't check for cache enabled, so don't use this without checking that first!
     */
    private GeyserChunk getChunk(int chunkX, int chunkZ) {
        long chunkPosition = MathUtils.chunkPositionToLong(chunkX, chunkZ);
        return chunks.getOrDefault(chunkPosition, null);
    }

    public void updateBlock(int x, int y, int z, int block) {
        if (!cache) {
            return;
        }

        GeyserChunk chunk = this.getChunk(x >> 4, z >> 4);
        if (chunk == null) {
            return;
        }

        if (y < minY || ((y - minY) >> 4) > chunk.sections().length - 1) {
            // Y likely goes above or below the height limit of this world
            return;
        }

        DataPalette palette = chunk.sections()[(y - minY) >> 4];
        if (palette == null) {
            if (block != Block.JAVA_AIR_ID) {
                // A previously empty chunk, which is no longer empty as a block has been added to it
                palette = DataPalette.createForChunk();
                // Fixes the chunk assuming that all blocks is the `block` variable we are updating. /shrug
                palette.getPalette().stateToId(Block.JAVA_AIR_ID);
                chunk.sections()[(y - minY) >> 4] = palette;
            } else {
                // Nothing to update
                return;
            }
        }

        palette.set(x & 0xF, y & 0xF, z & 0xF, block);
    }

    public int getBlockAt(int x, int y, int z) {
        if (!cache) {
            return Block.JAVA_AIR_ID;
        }

        GeyserChunk column = this.getChunk(x >> 4, z >> 4);
        if (column == null) {
            return Block.JAVA_AIR_ID;
        }

        if (y < minY || ((y - minY) >> 4) > column.sections().length - 1) {
            // Y likely goes above or below the height limit of this world
            return Block.JAVA_AIR_ID;
        }

        DataPalette chunk = column.sections()[(y - minY) >> 4];
        if (chunk != null) {
            return chunk.get(x & 0xF, y & 0xF, z & 0xF);
        }

        return Block.JAVA_AIR_ID;
    }

    public void removeChunk(int chunkX, int chunkZ) {
        if (!cache) {
            return;
        }

        long chunkPosition = MathUtils.chunkPositionToLong(chunkX, chunkZ);
        chunks.remove(chunkPosition);
        waitDeleteChunks.remove(chunkPosition);
    }

    /**
     * Manually clears all entries in the chunk cache.
     * The server is responsible for clearing chunk entries if out of render distance (for example) or switching dimensions,
     * but it is the client that must clear sections in the event of proxy switches.
     */
    public void clear() {
        if (!cache) {
            return;
        }

        if (!chunks.isEmpty()) {
            waitDeleteChunks.putAll(chunks);
            chunks.clear();
        }
    }
    public void tickCleanup(GeyserSession session) {
        if (!cache || waitDeleteChunks.isEmpty() || System.currentTimeMillis() - lastChangeWorldTime < 1000) {
            return;
        }

        int removed = 0;
        LongIterator iterator = waitDeleteChunks.keySet().iterator();
        while (iterator.hasNext() && removed < CHUNKS_TO_REMOVE_PER_CLEANUP) {
            long l = iterator.nextLong();
            ChunkUtils.sendEmptyChunk(session, MathUtils.getChunkX(l), MathUtils.getChunkZ(l), false);
            iterator.remove();
            removed++;
        }
    }

    public int getChunkMinY() {
        return minY >> 4;
    }

    public int getChunkHeightY() {
        return heightY >> 4;
    }
}
