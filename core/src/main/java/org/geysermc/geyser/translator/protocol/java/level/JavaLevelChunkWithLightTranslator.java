/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.translator.protocol.java.level;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.hash.Funnels;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntImmutableList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NBTOutputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.geysermc.geyser.entity.type.ItemFrameEntity;
import org.geysermc.geyser.level.BedrockDimension;
import org.geysermc.geyser.level.block.type.Block;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.chunk.BlockStorage;
import org.geysermc.geyser.level.chunk.GeyserChunkSection;
import org.geysermc.geyser.level.chunk.bitarray.BitArray;
import org.geysermc.geyser.level.chunk.bitarray.BitArrayVersion;
import org.geysermc.geyser.level.chunk.bitarray.SingletonBitArray;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.ClientBlobCache;
import org.geysermc.geyser.session.cache.registry.JavaRegistries;
import org.geysermc.geyser.translator.level.BiomeTranslator;
import org.geysermc.geyser.translator.level.block.entity.BedrockChunkWantsBlockEntityTag;
import org.geysermc.geyser.translator.level.block.entity.BlockEntityTranslator;
import org.geysermc.geyser.translator.level.block.entity.SkullBlockEntityTranslator;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.BlockEntityUtils;
import org.geysermc.geyser.util.ChunkUtils;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftTypes;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.BitStorage;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkSection;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.DataPalette;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.palette.GlobalPalette;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.palette.Palette;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.palette.SingletonPalette;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityInfo;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;

import java.io.IOException;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import static org.geysermc.geyser.util.ChunkUtils.*;

@Translator(packet = ClientboundLevelChunkWithLightPacket.class)
public class JavaLevelChunkWithLightTranslator extends PacketTranslator<ClientboundLevelChunkWithLightPacket> {
    private static final boolean USE_EXPERIMENTAL_CHUNK_TRANSLATION =
            Boolean.parseBoolean(System.getProperty("Geyser.ExperimentalChunkTranslation", "true"));
    private static volatile boolean globalChunkTranslationCacheEnabled =
            Boolean.parseBoolean(System.getProperty("Geyser.GlobalChunkTranslationCache", "true"));
    private static final long GLOBAL_CHUNK_TRANSLATION_CACHE_MAX_BYTES =
            Long.getLong("Geyser.GlobalChunkTranslationCacheMaxBytes", 512L * 1024L * 1024L);
    private static final int GLOBAL_CHUNK_TRANSLATION_CACHE_HOT_THRESHOLD =
            Math.max(1, Integer.getInteger("Geyser.GlobalChunkTranslationCacheHotThreshold", 3));
    private static final long GLOBAL_CHUNK_TRANSLATION_CACHE_FREQUENCY_WINDOW_SECONDS =
            Math.max(1L, Long.getLong("Geyser.GlobalChunkTranslationCacheFrequencyWindowSeconds", 60L));
    private static final long GLOBAL_CHUNK_TRANSLATION_CACHE_FREQUENCY_MAX_ENTRIES =
            Math.max(1L, Long.getLong("Geyser.GlobalChunkTranslationCacheFrequencyMaxEntries", 100_000L));
    private static final long GLOBAL_CHUNK_TRANSLATION_CACHE_EXPIRE_AFTER_ACCESS_MINUTES =
            Math.max(1L, Long.getLong("Geyser.GlobalChunkTranslationCacheExpireAfterAccessMinutes", 10L));
    private static final long CHUNK_TRANSLATION_SUMMARY_INTERVAL_SECONDS =
            Math.max(1L, Long.getLong("Geyser.ChunkTranslationSummaryIntervalSeconds", 60L));
    private static final long CHUNK_TRANSLATION_SUMMARY_INTERVAL_NANOS =
            TimeUnit.SECONDS.toNanos(CHUNK_TRANSLATION_SUMMARY_INTERVAL_SECONDS);
    private static final Cache<HashCode, CachedChunkPayload> GLOBAL_CHUNK_TRANSLATION_CACHE =
            CacheBuilder.<HashCode, CachedChunkPayload>newBuilder()
                    .maximumWeight(GLOBAL_CHUNK_TRANSLATION_CACHE_MAX_BYTES)
                    .weigher((HashCode key, CachedChunkPayload value) -> value.weight())
                    .expireAfterAccess(GLOBAL_CHUNK_TRANSLATION_CACHE_EXPIRE_AFTER_ACCESS_MINUTES, TimeUnit.MINUTES)
                    .build();
    private static final Cache<HashCode, AtomicLong> GLOBAL_CHUNK_TRANSLATION_FREQUENCY =
            CacheBuilder.<HashCode, AtomicLong>newBuilder()
                    .maximumSize(GLOBAL_CHUNK_TRANSLATION_CACHE_FREQUENCY_MAX_ENTRIES)
                    .expireAfterWrite(GLOBAL_CHUNK_TRANSLATION_CACHE_FREQUENCY_WINDOW_SECONDS, TimeUnit.SECONDS)
                    .build();
    private static final LongAdder GLOBAL_CHUNK_TRANSLATION_CACHE_HITS = new LongAdder();
    private static final LongAdder GLOBAL_CHUNK_TRANSLATION_CACHE_MISSES = new LongAdder();
    private static final LongAdder GLOBAL_CHUNK_TRANSLATION_CACHE_FAILURES = new LongAdder();
    private static final LongAdder SUMMARY_CHUNKS = new LongAdder();
    private static final LongAdder SUMMARY_CACHE_HITS = new LongAdder();
    private static final LongAdder SUMMARY_TRANSLATED = new LongAdder();
    private static final LongAdder SUMMARY_HOT = new LongAdder();
    private static final LongAdder SUMMARY_CACHE_STORED = new LongAdder();
    private static final LongAdder SUMMARY_BLOB_SENT = new LongAdder();
    private static final LongAdder SUMMARY_KEY_NANOS = new LongAdder();
    private static final LongAdder SUMMARY_TRANSLATION_NANOS = new LongAdder();
    private static final LongAdder SUMMARY_TOTAL_NANOS = new LongAdder();
    private static final LongAdder SUMMARY_PAYLOAD_BYTES = new LongAdder();
    private static final AtomicLong SUMMARY_MAX_KEY_NANOS = new AtomicLong();
    private static final AtomicLong SUMMARY_MAX_TRANSLATION_NANOS = new AtomicLong();
    private static final AtomicLong SUMMARY_MAX_TOTAL_NANOS = new AtomicLong();
    private static final AtomicLong SUMMARY_LAST_LOG_NANOS = new AtomicLong(System.nanoTime());
    private static final int[] YZX_TO_XZY = createYzxToXzyMap();
    private static final ThreadLocal<ExtendedCollisionsStorage> EXTENDED_COLLISIONS_STORAGE = ThreadLocal.withInitial(ExtendedCollisionsStorage::new);

    private static final class CachedChunkPayload {
        private final byte[] payload;
        private final int sectionCount;
        private final byte[][] blobPayloads;
        private final int cachePayloadOffset;

        private CachedChunkPayload(byte[] payload, int sectionCount, byte[][] blobPayloads, int cachePayloadOffset) {
            this.payload = payload;
            this.sectionCount = sectionCount;
            this.blobPayloads = blobPayloads;
            this.cachePayloadOffset = cachePayloadOffset;
        }

        private int weight() {
            long weight = this.payload.length;
            if (this.blobPayloads != null) {
                for (byte[] blobPayload : this.blobPayloads) {
                    weight += blobPayload.length;
                }
            }
            return (int) Math.min(Integer.MAX_VALUE, weight);
        }
    }

    private static int[] createYzxToXzyMap() {
        int[] indexes = new int[BlockStorage.SIZE];
        for (int yzx = 0; yzx < indexes.length; yzx++) {
            indexes[yzx] = indexYZXtoXZY(yzx);
        }
        return indexes;
    }

    public static boolean isGlobalChunkTranslationCacheEnabled() {
        return globalChunkTranslationCacheEnabled;
    }

    public static void setGlobalChunkTranslationCacheEnabled(boolean enabled) {
        globalChunkTranslationCacheEnabled = enabled;
        if (!enabled) {
            clearGlobalChunkTranslationCache();
        }
    }

    public static void clearGlobalChunkTranslationCache() {
        GLOBAL_CHUNK_TRANSLATION_CACHE.invalidateAll();
        GLOBAL_CHUNK_TRANSLATION_CACHE.cleanUp();
    }

    public static long globalChunkTranslationCacheSize() {
        return GLOBAL_CHUNK_TRANSLATION_CACHE.size();
    }

    @Override
    public void translate(GeyserSession session, ClientboundLevelChunkWithLightPacket packet) {
        // Geyser.ExperimentalChunkTranslation controls the implementation used for chunk translation.
        // true (default) uses the experimental path, which currently:
        // - reuses a precomputed YZX -> XZY index table instead of recalculating it for every block;
        // - emits Bedrock-only block entity tags during the main block conversion loop when possible;
        // - writes simple palette data directly into BitArray words to avoid per-entry BitArray#set overhead;
        // - uses the optimized biome translator that collapses uniform biome sections to singleton storage.
        // false uses the old baseline path so the optimization can be rolled back quickly.
        if (USE_EXPERIMENTAL_CHUNK_TRANSLATION) {
            translateExperimental(session, packet);
        } else {
            translateOld(session, packet);
        }
    }

    private void translateExperimental(GeyserSession session, ClientboundLevelChunkWithLightPacket packet) {
        final long startNanos = System.nanoTime();
        final boolean useExtendedCollisions = !session.getBlockMappings().getExtendedCollisionBoxes().isEmpty();
        final int chunkX = packet.getX();
        final int chunkZ = packet.getZ();

        if (session.isSpawned()) {
            ChunkUtils.updateChunkPosition(session, session.getPlayerEntity().getPosition().toInt());
        }

        // Ensure that, if the player is using lower world heights, the position is not offset
        int yOffset = session.getChunkCache().getChunkMinY();
        int chunkSize = session.getChunkCache().getChunkHeightY();

        DataPalette[] javaChunks = new DataPalette[chunkSize];
        DataPalette[] javaBiomes = new DataPalette[chunkSize];

        final BlockEntityInfo[] blockEntities = packet.getBlockEntities();
        final List<NbtMap> bedrockBlockEntities = new ObjectArrayList<>(blockEntities.length);

        BitSet waterloggedPaletteIds = new BitSet();
        BitSet bedrockOnlyBlockEntityIds = new BitSet();

        BedrockDimension bedrockDimension = session.getBedrockDimension();
        int maxBedrockSectionY = (bedrockDimension.height() >> 4) - 1;
        var biomeTranslations = session.getRegistryCache().registry(JavaRegistries.BIOME);
        int dimension = bedrockDimension.bedrockId();
        int lastNormalDimId = session.getLastNormalDimId();
        if (dimension != lastNormalDimId && (dimension == 0 || dimension == 3)) {
            dimension = lastNormalDimId;
        }

        long cacheKeyStartNanos = System.nanoTime();
        HashCode cacheKey = null;
        CachedChunkPayload cachedPayload = null;
        boolean translationCacheEnabled = globalChunkTranslationCacheEnabled;
        boolean blobCacheEnabled = ClientBlobCache.isGloballyEnabled();
        if (translationCacheEnabled || blobCacheEnabled) {
            try {
                Hasher hasher = Hashing.sha256().newHasher();
                hasher.putInt(2); // Cache key format version
                hasher.putInt(session.protocolVersion());
                hasher.putInt(System.identityHashCode(session.getBlockMappings()));
                hasher.putInt(bedrockDimension.bedrockId());
                hasher.putInt(dimension);
                hasher.putInt(bedrockDimension.minY());
                hasher.putInt(bedrockDimension.height());
                hasher.putInt(yOffset);
                hasher.putInt(chunkSize);
                hasher.putInt(chunkX);
                hasher.putInt(chunkZ);
                hasher.putBoolean(useExtendedCollisions);
                hasher.putBoolean(session.getPreferencesCache().showCustomSkulls());
                hasher.putInt(biomeTranslations.size());
                for (var biome : biomeTranslations.entries()) {
                    hasher.putInt(biome.id());
                    hasher.putInt(biome.data() == null ? -1 : biome.data());
                }

                byte[] chunkData = packet.getChunkData();
                hasher.putInt(chunkData.length);
                hasher.putBytes(chunkData);
                hasher.putInt(blockEntities.length);

                try (NBTOutputStream cacheKeyNbtStream = NbtUtils.createNetworkWriter(Funnels.asOutputStream(hasher))) {
                    for (BlockEntityInfo blockEntity : blockEntities) {
                        BlockEntityType type = blockEntity.getType();
                        String typeName = type == null ? "<null>" : type.toString();
                        hasher.putInt(typeName.length());
                        hasher.putUnencodedChars(typeName);
                        hasher.putInt(blockEntity.getX());
                        hasher.putInt(blockEntity.getY());
                        hasher.putInt(blockEntity.getZ());

                        NbtMap tag = blockEntity.getNbt();
                        if (tag == null) {
                            hasher.putBoolean(false);
                        } else {
                            hasher.putBoolean(true);
                            cacheKeyNbtStream.writeTag(tag);
                        }
                    }
                }

                cacheKey = hasher.hash();
                if (translationCacheEnabled) {
                    cachedPayload = GLOBAL_CHUNK_TRANSLATION_CACHE.getIfPresent(cacheKey);
                    if (cachedPayload == null) {
                        GLOBAL_CHUNK_TRANSLATION_CACHE_MISSES.increment();
                    } else {
                        GLOBAL_CHUNK_TRANSLATION_CACHE_HITS.increment();
                    }
                }
            } catch (IOException e) {
                GLOBAL_CHUNK_TRANSLATION_CACHE_FAILURES.increment();
                session.getGeyser().getLogger().error("Failed to calculate global chunk cache key", e);
            }
        }
        long cacheKeyElapsedNanos = System.nanoTime() - cacheKeyStartNanos;
        long requestCount = 0L;
        boolean hot = false;
        if (cacheKey != null) {
            AtomicLong frequency = GLOBAL_CHUNK_TRANSLATION_FREQUENCY.asMap()
                    .computeIfAbsent(cacheKey, ignored -> new AtomicLong());
            requestCount = frequency.incrementAndGet();
            hot = cachedPayload != null || requestCount >= GLOBAL_CHUNK_TRANSLATION_CACHE_HOT_THRESHOLD;
        }

        if (cachedPayload != null) {
            ByteBuf cachedInput = Unpooled.wrappedBuffer(packet.getChunkData());
            try {
                for (int sectionY = 0; sectionY < chunkSize; sectionY++) {
                    ChunkSection javaSection = MinecraftTypes.readChunkSection(cachedInput, BlockRegistries.BLOCK_STATES.get().size(),
                            biomeTranslations.size());
                    javaChunks[sectionY] = javaSection.getBlockData();
                }
            } catch (RuntimeException e) {
                GLOBAL_CHUNK_TRANSLATION_CACHE_FAILURES.increment();
                session.getGeyser().getLogger().error("Error while restoring a globally cached chunk", e);
                cachedPayload = null;
            } finally {
                cachedInput.release();
            }
        }

        if (cachedPayload != null) {
            Map<Vector3i, BlockDefinition> customSkullUpdates = null;
            if (!session.getErosionHandler().isActive()) {
                session.getChunkCache().addToCache(chunkX, chunkZ, javaChunks);
            }

            if (session.getPreferencesCache().showCustomSkulls()) {
                final int chunkBlockX = chunkX << 4;
                final int chunkBlockZ = chunkZ << 4;
                for (BlockEntityInfo blockEntity : blockEntities) {
                    NbtMap tag = blockEntity.getNbt();
                    if (blockEntity.getType() != BlockEntityType.SKULL || tag == null || !tag.containsKey("profile")) {
                        continue;
                    }

                    int x = blockEntity.getX();
                    int y = blockEntity.getY();
                    int z = blockEntity.getZ();
                    DataPalette section = javaChunks[(y >> 4) - yOffset];
                    BlockState blockState = BlockState.of(section.get(x, y & 0xF, z));
                    if (blockEntity.getType() == blockState.block().blockEntityType()) {
                        Vector3i position = Vector3i.from(x + chunkBlockX, y, z + chunkBlockZ);
                        BlockDefinition blockDefinition = SkullBlockEntityTranslator.translateSkull(session, tag, position, blockState);
                        if (blockDefinition != null) {
                            if (customSkullUpdates == null) {
                                customSkullUpdates = new HashMap<>();
                            }
                            customSkullUpdates.put(position, blockDefinition);
                        }
                    }
                }
            }

            LevelChunkPacket cachedLevelChunkPacket = new LevelChunkPacket();
            cachedLevelChunkPacket.setSubChunksLength(cachedPayload.sectionCount);
            cachedLevelChunkPacket.setChunkX(chunkX);
            cachedLevelChunkPacket.setChunkZ(chunkZ);
            cachedLevelChunkPacket.setDimension(dimension);
            boolean blobSent = session.getClientBlobCache().prepareChunkPacket(cachedLevelChunkPacket, cachedPayload.payload,
                    cachedPayload.blobPayloads, cachedPayload.cachePayloadOffset);
            session.sendUpstreamPacket(cachedLevelChunkPacket);

            if (customSkullUpdates != null) {
                for (Map.Entry<Vector3i, BlockDefinition> entry : customSkullUpdates.entrySet()) {
                    UpdateBlockPacket updateBlockPacket = new UpdateBlockPacket();
                    updateBlockPacket.setDataLayer(0);
                    updateBlockPacket.setBlockPosition(entry.getKey());
                    updateBlockPacket.setDefinition(entry.getValue());
                    updateBlockPacket.getFlags().add(UpdateBlockPacket.Flag.NEIGHBORS);
                    updateBlockPacket.getFlags().add(UpdateBlockPacket.Flag.NETWORK);
                    session.sendUpstreamPacket(updateBlockPacket);
                }
            }

            for (Map.Entry<Vector3i, ItemFrameEntity> entry : session.getItemFrameCache().entrySet()) {
                Vector3i position = entry.getKey();
                if ((position.getX() >> 4) == chunkX && (position.getZ() >> 4) == chunkZ) {
                    entry.getValue().updateBlock(true);
                }
            }

            recordChunkTranslationMetrics(session, true, true, false, blobSent,
                    cacheKeyElapsedNanos, 0L, System.nanoTime() - startNanos, cachedPayload.payload.length);
            return;
        }

        long translationStartNanos = System.nanoTime();
        int sectionCount;
        byte[] payload;
        int biomePayloadOffset = 0;
        int cachePayloadOffset = 0;
        ByteBuf byteBuf = null;

        // calculate the difference between the java dimension minY and the bedrock dimension minY as
        // the java chunk sections may need to be placed higher up in the bedrock chunk section array
        int sectionCountDiff = yOffset - (bedrockDimension.minY() >> 4);
        GeyserChunkSection[] sections = new GeyserChunkSection[chunkSize + sectionCountDiff];

        try {
            ByteBuf in = Unpooled.wrappedBuffer(packet.getChunkData());
            boolean extendedCollisionNextSection = false;
            for (int sectionY = 0; sectionY < chunkSize; sectionY++) {
                ChunkSection javaSection = MinecraftTypes.readChunkSection(in, BlockRegistries.BLOCK_STATES.get().size(),
                    session.getRegistryCache().registry(JavaRegistries.BIOME).size());
                javaChunks[sectionY] = javaSection.getBlockData();
                javaBiomes[sectionY] = javaSection.getBiomeData();
                boolean extendedCollision = extendedCollisionNextSection;
                boolean thisExtendedCollisionNextSection = false;

                int bedrockSectionY = sectionY + sectionCountDiff;
                int subChunkIndex = sectionY + yOffset;
                if (bedrockSectionY < 0 || maxBedrockSectionY < bedrockSectionY) {
                    // Ignore this chunk section since it goes outside the bounds accepted by the Bedrock client
                    if (useExtendedCollisions) {
                        EXTENDED_COLLISIONS_STORAGE.get().clear();
                    }
                    extendedCollisionNextSection = false;
                    continue;
                }

                // No need to encode an empty section...
                if (javaSection.isBlockCountEmpty()) {
                    // Unless we need to send extended collisions
                    if (useExtendedCollisions) {
                        if (extendedCollision) {
                            int blocks = EXTENDED_COLLISIONS_STORAGE.get().bottomLayerCollisions() + 1;
                            BitArray bedrockData = BitArrayVersion.forBitsCeil(Integer.SIZE - Integer.numberOfLeadingZeros(blocks)).createArray(BlockStorage.SIZE);
                            BlockStorage layer0 = new BlockStorage(bedrockData, new IntArrayList(blocks));

                            layer0.idFor(session.getBlockMappings().getBedrockAir().getRuntimeId());
                            for (int yzx = 0; yzx < BlockStorage.SIZE / 16; yzx++) {
                                if (EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY) != 0) {
                                    bedrockData.set(YZX_TO_XZY[yzx], layer0.idFor(EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY)));
                                    EXTENDED_COLLISIONS_STORAGE.get().set(yzx, 0, sectionY);
                                }
                            }

                            BlockStorage[] layers = new BlockStorage[]{ layer0 };
                            sections[bedrockSectionY] = new GeyserChunkSection(layers, subChunkIndex);
                        }
                        EXTENDED_COLLISIONS_STORAGE.get().clear();
                        extendedCollisionNextSection = false;
                    }
                    continue;
                }

                Palette javaPalette = javaSection.getBlockData().getPalette();
                BitStorage javaData = javaSection.getBlockData().getStorage();

                if (javaPalette instanceof GlobalPalette) {
                    // As this is the global palette, simply iterate through the whole chunk section once
                    GeyserChunkSection section = new GeyserChunkSection(session.getBlockMappings().getBedrockAir().getRuntimeId(), subChunkIndex);
                    for (int yzx = 0; yzx < BlockStorage.SIZE; yzx++) {
                        int javaId = javaData.get(yzx);
                        BlockState state = BlockState.of(javaId);
                        int bedrockId = session.getBlockMappings().getBedrockBlockId(javaId);
                        int xzy = YZX_TO_XZY[yzx];
                        section.getBlockStorageArray()[0].setFullBlock(xzy, bedrockId);

                        if (BlockRegistries.WATERLOGGED.get().get(javaId)) {
                            section.getBlockStorageArray()[1].setFullBlock(xzy, session.getBlockMappings().getBedrockWater().getRuntimeId());
                        }

                        // Extended collision blocks
                        if (useExtendedCollisions) {
                            if (EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY) != 0) {
                                if (javaId == Block.JAVA_AIR_ID) {
                                    section.getBlockStorageArray()[0].setFullBlock(xzy, EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY));
                                }
                                EXTENDED_COLLISIONS_STORAGE.get().set(yzx, 0, sectionY);
                                continue;
                            }
                            BlockDefinition aboveBedrockExtendedCollisionDefinition = session.getBlockMappings().getExtendedCollisionBoxes().get(javaId);
                            if (aboveBedrockExtendedCollisionDefinition != null) {
                                EXTENDED_COLLISIONS_STORAGE.get().set((yzx + 0x100) & 0xFFF, aboveBedrockExtendedCollisionDefinition.getRuntimeId(), sectionY);
                                if ((xzy & 0xF) == 15) {
                                    thisExtendedCollisionNextSection = true;
                                }
                            }
                        }

                        // Check if block is piston or flower to see if we'll need to create additional block entities, as they're only block entities in Bedrock
                        if (state.block() instanceof BedrockChunkWantsBlockEntityTag) {
                            addBedrockOnlyBlockEntity(session, bedrockBlockEntities, chunkX, chunkZ, sectionY, yOffset, yzx, state);
                        }
                    }
                    sections[bedrockSectionY] = section;
                    extendedCollisionNextSection = thisExtendedCollisionNextSection;
                    continue;
                }

                if (javaPalette instanceof SingletonPalette) {
                    // There's only one block here. Very easy!
                    int javaId = javaPalette.idToState(0);
                    int bedrockId = session.getBlockMappings().getBedrockBlockId(javaId);
                    BlockStorage blockStorage = new BlockStorage(SingletonBitArray.INSTANCE, IntLists.singleton(bedrockId));

                    if (BlockRegistries.WATERLOGGED.get().get(javaId)) {
                        BlockStorage waterlogged = new BlockStorage(SingletonBitArray.INSTANCE, IntLists.singleton(session.getBlockMappings().getBedrockWater().getRuntimeId()));
                        sections[bedrockSectionY] = new GeyserChunkSection(new BlockStorage[] {blockStorage, waterlogged}, subChunkIndex);
                    } else {
                        sections[bedrockSectionY] = new GeyserChunkSection(new BlockStorage[] {blockStorage}, subChunkIndex);
                    }
                    if (useExtendedCollisions) {
                        EXTENDED_COLLISIONS_STORAGE.get().clear();
                        extendedCollisionNextSection = false;
                    }
                    // If a chunk contains all of the same piston or flower pot then god help us
                    continue;
                }

                IntList bedrockPalette = new IntArrayList(javaPalette.size());
                int airPaletteId = -1;
                waterloggedPaletteIds.clear();
                bedrockOnlyBlockEntityIds.clear();
                BlockState[] bedrockOnlyBlockEntityStates = null;

                // Iterate through palette and convert state IDs to Bedrock, doing some additional checks as we go
                int extendedCollisionsInPalette = 0;
                for (int i = 0; i < javaPalette.size(); i++) {
                    int javaId = javaPalette.idToState(i);
                    bedrockPalette.add(session.getBlockMappings().getBedrockBlockId(javaId));

                    if (BlockRegistries.WATERLOGGED.get().get(javaId)) {
                        waterloggedPaletteIds.set(i);
                    }

                    if (javaId == Block.JAVA_AIR_ID) {
                        airPaletteId = i;
                    }

                    if (useExtendedCollisions) {
                        if (session.getBlockMappings().getExtendedCollisionBoxes().get(javaId) != null) {
                            extendedCollision = true;
                            extendedCollisionsInPalette++;
                        }
                    }

                    // Track blocks that need Bedrock-only block entity tags so the main conversion loop can emit them without a second scan.
                    BlockState state = BlockState.of(javaId);
                    if (state.block() instanceof BedrockChunkWantsBlockEntityTag) {
                        if (bedrockOnlyBlockEntityStates == null) {
                            bedrockOnlyBlockEntityStates = new BlockState[javaPalette.size()];
                        }
                        bedrockOnlyBlockEntityStates[i] = state;
                        bedrockOnlyBlockEntityIds.set(i);
                    }
                }
                boolean hasBedrockOnlyBlockEntities = !bedrockOnlyBlockEntityIds.isEmpty();

                // We need to ensure we use enough bits to represent extended collision blocks in the chunk section
                int sectionCollisionBlocks = 0;
                if (useExtendedCollisions) {
                    int bottomLayerCollisions = extendedCollision ? EXTENDED_COLLISIONS_STORAGE.get().bottomLayerCollisions() : 0;
                    sectionCollisionBlocks = bottomLayerCollisions + extendedCollisionsInPalette;
                }
                int bedrockDataBits = Integer.SIZE - Integer.numberOfLeadingZeros(javaPalette.size() + sectionCollisionBlocks);
                BitArray bedrockData = BitArrayVersion.forBitsCeil(bedrockDataBits).createArray(BlockStorage.SIZE);
                BlockStorage layer0 = new BlockStorage(bedrockData, bedrockPalette);
                BlockStorage[] layers;

                // Convert data array from YZX to XZY coordinate order
                if (waterloggedPaletteIds.isEmpty() && !extendedCollision) {
                    // No blocks are waterlogged, simply convert coordinate order.
                    // If no Bedrock-only block entity tags are needed, skip BitArray#set overhead on non-padded palettes.
                    if (hasBedrockOnlyBlockEntities || !copyBlockPaletteDataFast(bedrockData, javaData)) {
                        for (int yzx = 0; yzx < BlockStorage.SIZE; yzx++) {
                            int paletteId = javaData.get(yzx);
                            int xzy = YZX_TO_XZY[yzx];
                            bedrockData.set(xzy, paletteId);
                            if (hasBedrockOnlyBlockEntities && bedrockOnlyBlockEntityIds.get(paletteId)) {
                                addBedrockOnlyBlockEntity(session, bedrockBlockEntities, chunkX, chunkZ, sectionY, yOffset, yzx,
                                        bedrockOnlyBlockEntityStates[paletteId]);
                            }
                        }
                    }

                    layers = new BlockStorage[]{ layer0 };
                } else if (!waterloggedPaletteIds.isEmpty() && !extendedCollision) {
                    // The section contains waterlogged blocks, we need to convert coordinate order AND generate a V1 block storage for
                    // layer 1 with palette ID 1 indicating water
                    int[] layer1Data = new int[BlockStorage.SIZE >> 5];
                    for (int yzx = 0; yzx < BlockStorage.SIZE; yzx++) {
                        int paletteId = javaData.get(yzx);
                        int xzy = YZX_TO_XZY[yzx];
                        bedrockData.set(xzy, paletteId);
                        if (hasBedrockOnlyBlockEntities && bedrockOnlyBlockEntityIds.get(paletteId)) {
                            addBedrockOnlyBlockEntity(session, bedrockBlockEntities, chunkX, chunkZ, sectionY, yOffset, yzx,
                                    bedrockOnlyBlockEntityStates[paletteId]);
                        }

                        if (waterloggedPaletteIds.get(paletteId)) {
                            layer1Data[xzy >> 5] |= 1 << (xzy & 0x1F);
                        }
                    }

                    // V1 palette
                    IntList layer1Palette = IntList.of(
                            session.getBlockMappings().getBedrockAir().getRuntimeId(), // Air - see BlockStorage's constructor for more information
                            session.getBlockMappings().getBedrockWater().getRuntimeId());

                    layers = new BlockStorage[]{ layer0, new BlockStorage(BitArrayVersion.V1.createArray(BlockStorage.SIZE, layer1Data), layer1Palette) };
                } else if (waterloggedPaletteIds.isEmpty()) {
                    for (int yzx = 0; yzx < BlockStorage.SIZE; yzx++) {
                        int paletteId = javaData.get(yzx);
                        int xzy = YZX_TO_XZY[yzx];
                        bedrockData.set(xzy, paletteId);
                        if (hasBedrockOnlyBlockEntities && bedrockOnlyBlockEntityIds.get(paletteId)) {
                            addBedrockOnlyBlockEntity(session, bedrockBlockEntities, chunkX, chunkZ, sectionY, yOffset, yzx,
                                    bedrockOnlyBlockEntityStates[paletteId]);
                        }

                        if (EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY) != 0) {
                            if (paletteId == airPaletteId) {
                                bedrockData.set(xzy, layer0.idFor(EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY)));
                            }
                            EXTENDED_COLLISIONS_STORAGE.get().set(yzx, 0, sectionY);
                            continue;
                        }
                        BlockDefinition aboveBedrockExtendedCollisionDefinition = session.getBlockMappings()
                                .getExtendedCollisionBoxes().get(javaPalette.idToState(paletteId));
                        if (aboveBedrockExtendedCollisionDefinition != null) {
                            EXTENDED_COLLISIONS_STORAGE.get().set((yzx + 0x100) & 0xFFF, aboveBedrockExtendedCollisionDefinition.getRuntimeId(), sectionY);
                            if ((xzy & 0xF) == 15) {
                                thisExtendedCollisionNextSection = true;
                            }
                        }
                    }

                    layers = new BlockStorage[]{ layer0 };
                } else {
                    int[] layer1Data = new int[BlockStorage.SIZE >> 5];
                    for (int yzx = 0; yzx < BlockStorage.SIZE; yzx++) {
                        int paletteId = javaData.get(yzx);
                        int xzy = YZX_TO_XZY[yzx];
                        bedrockData.set(xzy, paletteId);
                        if (hasBedrockOnlyBlockEntities && bedrockOnlyBlockEntityIds.get(paletteId)) {
                            addBedrockOnlyBlockEntity(session, bedrockBlockEntities, chunkX, chunkZ, sectionY, yOffset, yzx,
                                    bedrockOnlyBlockEntityStates[paletteId]);
                        }

                        if (waterloggedPaletteIds.get(paletteId)) {
                            layer1Data[xzy >> 5] |= 1 << (xzy & 0x1F);
                        }

                        if (EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY) != 0) {
                            if (paletteId == airPaletteId) {
                                bedrockData.set(xzy, layer0.idFor(EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY)));
                            }
                            EXTENDED_COLLISIONS_STORAGE.get().set(yzx, 0, sectionY);
                            continue;
                        }
                        BlockDefinition aboveBedrockExtendedCollisionDefinition = session.getBlockMappings().getExtendedCollisionBoxes()
                                .get(javaPalette.idToState(paletteId));
                        if (aboveBedrockExtendedCollisionDefinition != null) {
                            EXTENDED_COLLISIONS_STORAGE.get().set((yzx + 0x100) & 0xFFF, aboveBedrockExtendedCollisionDefinition.getRuntimeId(), sectionY);
                            if ((xzy & 0xF) == 15) {
                                thisExtendedCollisionNextSection = true;
                            }
                        }
                    }

                    // V1 palette
                    IntList layer1Palette = IntList.of(
                            session.getBlockMappings().getBedrockAir().getRuntimeId(), // Air - see BlockStorage's constructor for more information
                            session.getBlockMappings().getBedrockWater().getRuntimeId());

                    layers = new BlockStorage[]{ layer0, new BlockStorage(BitArrayVersion.V1.createArray(BlockStorage.SIZE, layer1Data), layer1Palette) };
                }

                sections[bedrockSectionY] = new GeyserChunkSection(layers, subChunkIndex);
                extendedCollisionNextSection = thisExtendedCollisionNextSection;
            }

            if (!session.getErosionHandler().isActive()) {
                session.getChunkCache().addToCache(chunkX, chunkZ, javaChunks);
            }

            final int chunkBlockX = chunkX << 4;
            final int chunkBlockZ = chunkZ << 4;
            for (BlockEntityInfo blockEntity : blockEntities) {
                BlockEntityType type = blockEntity.getType();
                NbtMap tag = blockEntity.getNbt();
                if (type == null) {
                    // As an example: ViaVersion will send -1 if it cannot find the block entity type
                    // Vanilla Minecraft gracefully handles this
                    continue;
                }
                int x = blockEntity.getX(); // Relative to chunk
                int y = blockEntity.getY();
                int z = blockEntity.getZ(); // Relative to chunk

                // Get the Java block state ID from block entity position
                DataPalette section = javaChunks[(y >> 4) - yOffset];
                BlockState blockState = BlockState.of(section.get(x, y & 0xF, z));

                // Note that, since 1.20.5, tags can be null, but Bedrock still needs a default tag to render the item
                // Also, some properties - like banner base colors - are part of the tag and is processed here.
                BlockEntityTranslator blockEntityTranslator = BlockEntityUtils.getBlockEntityTranslator(type);

                // The Java server can send block entity data for blocks that aren't actually those blocks.
                // A Java client ignores these
                if (type == blockState.block().blockEntityType()) {
                    bedrockBlockEntities.add(blockEntityTranslator.getBlockEntityTag(session, type, x + chunkBlockX, y, z + chunkBlockZ, tag, blockState));

                    // Check for custom skulls
                    if (session.getPreferencesCache().showCustomSkulls() && type == BlockEntityType.SKULL && tag != null && tag.containsKey("profile")) {
                        BlockDefinition blockDefinition = SkullBlockEntityTranslator.translateSkull(session, tag, Vector3i.from(x + chunkBlockX, y, z + chunkBlockZ), blockState);
                        if (blockDefinition != null) {
                            int bedrockSectionY = (y >> 4) - (bedrockDimension.minY() >> 4);
                            int subChunkIndex = (y >> 4) + (bedrockDimension.minY() >> 4);
                            if (0 <= bedrockSectionY && bedrockSectionY < maxBedrockSectionY) {
                                // Custom skull is in a section accepted by Bedrock
                                GeyserChunkSection bedrockSection = sections[bedrockSectionY];
                                IntList palette = bedrockSection.getBlockStorageArray()[0].getPalette();
                                if (palette instanceof IntImmutableList || palette instanceof IntLists.Singleton) {
                                    // TODO there has to be a better way to expand the palette .-.
                                    bedrockSection = bedrockSection.copy(subChunkIndex);
                                    sections[bedrockSectionY] = bedrockSection;
                                }
                                bedrockSection.setFullBlock(x, y & 0xF, z, 0, blockDefinition.getRuntimeId());
                            }
                        }
                    }
                }
            }

            // Find highest section
            sectionCount = sections.length - 1;
            while (sectionCount >= 0 && sections[sectionCount] == null) {
                sectionCount--;
            }
            sectionCount++;

            // As of 1.18.30, the amount of biomes read is dependent on how high Bedrock thinks the dimension is
            int biomeCount = bedrockDimension.height() >> 4;

            // Estimate chunk size
            int size = 0;
            for (int i = 0; i < sectionCount; i++) {
                GeyserChunkSection section = sections[i];
                if (section != null) {
                    size += section.estimateNetworkSize();
                } else {
                    size += EMPTY_CHUNK_SECTION_SIZE;
                }
            }
            size += ChunkUtils.EMPTY_BIOME_DATA.length * biomeCount;
            size += 1; // Border blocks
            size += bedrockBlockEntities.size() * 64; // Conservative estimate of 64 bytes per tile entity

            // Allocate output buffer
            byteBuf = ByteBufAllocator.DEFAULT.ioBuffer(size);
            for (int i = 0; i < sectionCount; i++) {
                GeyserChunkSection section = sections[i];
                if (section != null) {
                    section.writeToNetwork(byteBuf);
                } else {
                    int subChunkIndex = (i + (bedrockDimension.minY() >> 4));
                    new GeyserChunkSection(EMPTY_BLOCK_STORAGE, subChunkIndex).writeToNetwork(byteBuf);
                }
            }

            biomePayloadOffset = byteBuf.writerIndex();
            int dimensionOffset = bedrockDimension.minY() >> 4;
            for (int i = 0; i < biomeCount; i++) {
                int biomeYOffset = dimensionOffset + i;
                if (biomeYOffset < yOffset) {
                    // Ignore this biome section since it goes below the height of the Java world
                    byteBuf.writeBytes(ChunkUtils.EMPTY_BIOME_DATA);
                    continue;
                }
                if (biomeYOffset >= (chunkSize + yOffset)) {
                    // This biome section goes above the height of the Java world
                    // The byte written here is a header that says to carry on the biome data from the previous chunk
                    byteBuf.writeByte((127 << 1) | 1);
                    continue;
                }

                DataPalette biomeData = javaBiomes[i + (dimensionOffset - yOffset)];
                BlockStorage biomeStorage = BiomeTranslator.toNewBedrockBiome(session, biomeData);
                biomeStorage.writeToNetwork(byteBuf);
            }

            cachePayloadOffset = byteBuf.writerIndex();
            byteBuf.writeByte(0); // Border blocks - Edu edition only

            // Encode tile entities into buffer
            NBTOutputStream nbtStream = NbtUtils.createNetworkWriter(new ByteBufOutputStream(byteBuf));
            for (NbtMap blockEntity : bedrockBlockEntities) {
                nbtStream.writeTag(blockEntity);
            }
            payload = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(payload);
        } catch (IOException e) {
            session.getGeyser().getLogger().error("IO error while encoding chunk", e);
            return;
        } finally {
            if (byteBuf != null) {
                byteBuf.release(); // Release buffer to allow buffer pooling to be useful
            }
        }

        byte[][] blobPayloads = null;
        if (hot && ClientBlobCache.isGloballyEnabled()) {
            try {
                blobPayloads = createBlobPayloads(session, sections, sectionCount, payload,
                        biomePayloadOffset, cachePayloadOffset, bedrockDimension.minY() >> 4);
            } catch (IOException e) {
                session.getClientBlobCache().recordFailure("encodeBlobPayload");
                session.getGeyser().getLogger().error("IO error while encoding chunk blob cache payload", e);
            }
        }

        LevelChunkPacket levelChunkPacket = new LevelChunkPacket();
        levelChunkPacket.setSubChunksLength(sectionCount);
        levelChunkPacket.setChunkX(chunkX);
        levelChunkPacket.setChunkZ(chunkZ);
        levelChunkPacket.setDimension(dimension);
        boolean blobSent = session.getClientBlobCache().prepareChunkPacket(levelChunkPacket, payload, blobPayloads,
                cachePayloadOffset);

        boolean cacheStored = false;
        if (globalChunkTranslationCacheEnabled && cacheKey != null && hot) {
            CachedChunkPayload newCachedPayload = new CachedChunkPayload(payload, sectionCount, blobPayloads, cachePayloadOffset);
            CachedChunkPayload existingCachedPayload = GLOBAL_CHUNK_TRANSLATION_CACHE.asMap().putIfAbsent(cacheKey, newCachedPayload);
            if (existingCachedPayload == null) {
                cacheStored = true;
            }
        }

        session.sendUpstreamPacket(levelChunkPacket);

        for (Map.Entry<Vector3i, ItemFrameEntity> entry : session.getItemFrameCache().entrySet()) {
            Vector3i position = entry.getKey();
            if ((position.getX() >> 4) == chunkX && (position.getZ() >> 4) == chunkZ) {
                // Update this item frame so it doesn't get lost in the abyss
                //TODO optimize
                entry.getValue().updateBlock(true);
            }
        }

        recordChunkTranslationMetrics(session, false, hot, cacheStored, blobSent, cacheKeyElapsedNanos,
                System.nanoTime() - translationStartNanos, System.nanoTime() - startNanos, payload.length);
    }

    private static void recordChunkTranslationMetrics(GeyserSession session, boolean cacheHit, boolean hot,
                                                      boolean cacheStored, boolean blobSent, long keyNanos,
                                                      long translationNanos, long totalNanos, int payloadBytes) {
        SUMMARY_CHUNKS.increment();
        SUMMARY_KEY_NANOS.add(keyNanos);
        SUMMARY_TOTAL_NANOS.add(totalNanos);
        SUMMARY_PAYLOAD_BYTES.add(payloadBytes);
        SUMMARY_MAX_KEY_NANOS.accumulateAndGet(keyNanos, Math::max);
        SUMMARY_MAX_TOTAL_NANOS.accumulateAndGet(totalNanos, Math::max);
        if (cacheHit) {
            SUMMARY_CACHE_HITS.increment();
        } else {
            SUMMARY_TRANSLATED.increment();
            SUMMARY_TRANSLATION_NANOS.add(translationNanos);
            SUMMARY_MAX_TRANSLATION_NANOS.accumulateAndGet(translationNanos, Math::max);
        }
        if (hot) {
            SUMMARY_HOT.increment();
        }
        if (cacheStored) {
            SUMMARY_CACHE_STORED.increment();
        }
        if (blobSent) {
            SUMMARY_BLOB_SENT.increment();
        }

        long now = System.nanoTime();
        long previousLog = SUMMARY_LAST_LOG_NANOS.get();
        if (now - previousLog < CHUNK_TRANSLATION_SUMMARY_INTERVAL_NANOS
                || !SUMMARY_LAST_LOG_NANOS.compareAndSet(previousLog, now)) {
            return;
        }

        long chunks = SUMMARY_CHUNKS.sumThenReset();
        long cacheHits = SUMMARY_CACHE_HITS.sumThenReset();
        long translated = SUMMARY_TRANSLATED.sumThenReset();
        long hotChunks = SUMMARY_HOT.sumThenReset();
        long cacheStores = SUMMARY_CACHE_STORED.sumThenReset();
        long blobSends = SUMMARY_BLOB_SENT.sumThenReset();
        long keyTotalNanos = SUMMARY_KEY_NANOS.sumThenReset();
        long translationTotalNanos = SUMMARY_TRANSLATION_NANOS.sumThenReset();
        long totalNanosSum = SUMMARY_TOTAL_NANOS.sumThenReset();
        long payloadBytesSum = SUMMARY_PAYLOAD_BYTES.sumThenReset();
        long maxKeyNanos = SUMMARY_MAX_KEY_NANOS.getAndSet(0L);
        long maxTranslationNanos = SUMMARY_MAX_TRANSLATION_NANOS.getAndSet(0L);
        long maxTotalNanos = SUMMARY_MAX_TOTAL_NANOS.getAndSet(0L);

        session.getGeyser().getLogger().info("[ChunkTranslationSummary] windowSeconds="
                + ((now - previousLog) / 1_000_000_000.0D)
                + " chunks=" + chunks
                + " cacheHits=" + cacheHits
                + " cacheMisses=" + (chunks - cacheHits)
                + " translated=" + translated
                + " hot=" + hotChunks
                + " cacheStored=" + cacheStores
                + " blobSent=" + blobSends
                + " keyAvgMs=" + averageMillis(keyTotalNanos, chunks)
                + " keyMaxMs=" + (maxKeyNanos / 1_000_000.0D)
                + " translationAvgMs=" + averageMillis(translationTotalNanos, translated)
                + " translationMaxMs=" + (maxTranslationNanos / 1_000_000.0D)
                + " totalAvgMs=" + averageMillis(totalNanosSum, chunks)
                + " totalMaxMs=" + (maxTotalNanos / 1_000_000.0D)
                + " payloadBytes=" + payloadBytesSum
                + " cacheEntries=" + GLOBAL_CHUNK_TRANSLATION_CACHE.size()
                + " frequencyEntries=" + GLOBAL_CHUNK_TRANSLATION_FREQUENCY.size()
                + " totalLookupHits=" + GLOBAL_CHUNK_TRANSLATION_CACHE_HITS.sum()
                + " totalLookupMisses=" + GLOBAL_CHUNK_TRANSLATION_CACHE_MISSES.sum()
                + " totalCacheFailures=" + GLOBAL_CHUNK_TRANSLATION_CACHE_FAILURES.sum());
    }

    private static double averageMillis(long nanos, long count) {
        return count == 0L ? 0.0D : (nanos / 1_000_000.0D) / count;
    }

    private void translateOld(GeyserSession session, ClientboundLevelChunkWithLightPacket packet) {
        final boolean useExtendedCollisions = !session.getBlockMappings().getExtendedCollisionBoxes().isEmpty();
        final int chunkX = packet.getX();
        final int chunkZ = packet.getZ();

        if (session.isSpawned()) {
            ChunkUtils.updateChunkPosition(session, session.getPlayerEntity().getPosition().toInt());
        }

        // Ensure that, if the player is using lower world heights, the position is not offset
        int yOffset = session.getChunkCache().getChunkMinY();
        int chunkSize = session.getChunkCache().getChunkHeightY();

        DataPalette[] javaChunks = new DataPalette[chunkSize];
        DataPalette[] javaBiomes = new DataPalette[chunkSize];

        final BlockEntityInfo[] blockEntities = packet.getBlockEntities();
        final List<NbtMap> bedrockBlockEntities = new ObjectArrayList<>(blockEntities.length);

        BitSet waterloggedPaletteIds = new BitSet();
        BitSet bedrockOnlyBlockEntityIds = new BitSet();

        BedrockDimension bedrockDimension = session.getBedrockDimension();
        int maxBedrockSectionY = (bedrockDimension.height() >> 4) - 1;

        int sectionCount;
        byte[] payload;
        ByteBuf byteBuf = null;

        // calculate the difference between the java dimension minY and the bedrock dimension minY as
        // the java chunk sections may need to be placed higher up in the bedrock chunk section array
        int sectionCountDiff = yOffset - (bedrockDimension.minY() >> 4);
        GeyserChunkSection[] sections = new GeyserChunkSection[chunkSize + sectionCountDiff];

        try {
            ByteBuf in = Unpooled.wrappedBuffer(packet.getChunkData());
            boolean extendedCollisionNextSection = false;
            for (int sectionY = 0; sectionY < chunkSize; sectionY++) {
                ChunkSection javaSection = MinecraftTypes.readChunkSection(in, BlockRegistries.BLOCK_STATES.get().size(),
                    session.getRegistryCache().registry(JavaRegistries.BIOME).size());
                javaChunks[sectionY] = javaSection.getBlockData();
                javaBiomes[sectionY] = javaSection.getBiomeData();
                boolean extendedCollision = extendedCollisionNextSection;
                boolean thisExtendedCollisionNextSection = false;

                int bedrockSectionY = sectionY + sectionCountDiff;
                int subChunkIndex = sectionY + yOffset;
                if (bedrockSectionY < 0 || maxBedrockSectionY < bedrockSectionY) {
                    // Ignore this chunk section since it goes outside the bounds accepted by the Bedrock client
                    if (useExtendedCollisions) {
                        EXTENDED_COLLISIONS_STORAGE.get().clear();
                    }
                    extendedCollisionNextSection = false;
                    continue;
                }

                // No need to encode an empty section...
                if (javaSection.isBlockCountEmpty()) {
                    // Unless we need to send extended collisions
                    if (useExtendedCollisions) {
                        if (extendedCollision) {
                            int blocks = EXTENDED_COLLISIONS_STORAGE.get().bottomLayerCollisions() + 1;
                            BitArray bedrockData = BitArrayVersion.forBitsCeil(Integer.SIZE - Integer.numberOfLeadingZeros(blocks)).createArray(BlockStorage.SIZE);
                            BlockStorage layer0 = new BlockStorage(bedrockData, new IntArrayList(blocks));
    
                            layer0.idFor(session.getBlockMappings().getBedrockAir().getRuntimeId());
                            for (int yzx = 0; yzx < BlockStorage.SIZE / 16; yzx++) {
                                if (EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY) != 0) {
                                    bedrockData.set(indexYZXtoXZY(yzx), layer0.idFor(EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY)));
                                    EXTENDED_COLLISIONS_STORAGE.get().set(yzx, 0, sectionY);
                                }
                            }
    
                            BlockStorage[] layers = new BlockStorage[]{ layer0 };
                            sections[bedrockSectionY] = new GeyserChunkSection(layers, subChunkIndex);
                        }
                        EXTENDED_COLLISIONS_STORAGE.get().clear();
                        extendedCollisionNextSection = false;
                    }
                    continue;
                }

                Palette javaPalette = javaSection.getBlockData().getPalette();
                BitStorage javaData = javaSection.getBlockData().getStorage();

                if (javaPalette instanceof GlobalPalette) {
                    // As this is the global palette, simply iterate through the whole chunk section once
                    GeyserChunkSection section = new GeyserChunkSection(session.getBlockMappings().getBedrockAir().getRuntimeId(), subChunkIndex);
                    for (int yzx = 0; yzx < BlockStorage.SIZE; yzx++) {
                        int javaId = javaData.get(yzx);
                        BlockState state = BlockState.of(javaId);
                        int bedrockId = session.getBlockMappings().getBedrockBlockId(javaId);
                        int xzy = indexYZXtoXZY(yzx);
                        section.getBlockStorageArray()[0].setFullBlock(xzy, bedrockId);

                        if (BlockRegistries.WATERLOGGED.get().get(javaId)) {
                            section.getBlockStorageArray()[1].setFullBlock(xzy, session.getBlockMappings().getBedrockWater().getRuntimeId());
                        }

                        // Extended collision blocks
                        if (useExtendedCollisions) {
                            if (EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY) != 0) {
                                if (javaId == Block.JAVA_AIR_ID) {
                                    section.getBlockStorageArray()[0].setFullBlock(xzy, EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY));
                                }
                                EXTENDED_COLLISIONS_STORAGE.get().set(yzx, 0, sectionY);
                                continue;
                            }
                            BlockDefinition aboveBedrockExtendedCollisionDefinition = session.getBlockMappings().getExtendedCollisionBoxes().get(javaId);
                            if (aboveBedrockExtendedCollisionDefinition != null) {
                                EXTENDED_COLLISIONS_STORAGE.get().set((yzx + 0x100) & 0xFFF, aboveBedrockExtendedCollisionDefinition.getRuntimeId(), sectionY);
                                if ((xzy & 0xF) == 15) {
                                    thisExtendedCollisionNextSection = true;
                                }
                            }
                        }

                        // Check if block is piston or flower to see if we'll need to create additional block entities, as they're only block entities in Bedrock
                        if (state.block() instanceof BedrockChunkWantsBlockEntityTag) {
                            addBedrockOnlyBlockEntity(session, bedrockBlockEntities, chunkX, chunkZ, sectionY, yOffset, yzx, state);
                        }
                    }
                    sections[bedrockSectionY] = section;
                    extendedCollisionNextSection = thisExtendedCollisionNextSection;
                    continue;
                }

                if (javaPalette instanceof SingletonPalette) {
                    // There's only one block here. Very easy!
                    int javaId = javaPalette.idToState(0);
                    int bedrockId = session.getBlockMappings().getBedrockBlockId(javaId);
                    BlockStorage blockStorage = new BlockStorage(SingletonBitArray.INSTANCE, IntLists.singleton(bedrockId));

                    if (BlockRegistries.WATERLOGGED.get().get(javaId)) {
                        BlockStorage waterlogged = new BlockStorage(SingletonBitArray.INSTANCE, IntLists.singleton(session.getBlockMappings().getBedrockWater().getRuntimeId()));
                        sections[bedrockSectionY] = new GeyserChunkSection(new BlockStorage[] {blockStorage, waterlogged}, subChunkIndex);
                    } else {
                        sections[bedrockSectionY] = new GeyserChunkSection(new BlockStorage[] {blockStorage}, subChunkIndex);
                    }
                    if (useExtendedCollisions) {
                        EXTENDED_COLLISIONS_STORAGE.get().clear();
                        extendedCollisionNextSection = false;
                    }
                    // If a chunk contains all of the same piston or flower pot then god help us
                    continue;
                }

                IntList bedrockPalette = new IntArrayList(javaPalette.size());
                int airPaletteId = -1;
                waterloggedPaletteIds.clear();
                bedrockOnlyBlockEntityIds.clear();

                // Iterate through palette and convert state IDs to Bedrock, doing some additional checks as we go
                int extendedCollisionsInPalette = 0;
                for (int i = 0; i < javaPalette.size(); i++) {
                    int javaId = javaPalette.idToState(i);
                    bedrockPalette.add(session.getBlockMappings().getBedrockBlockId(javaId));

                    if (BlockRegistries.WATERLOGGED.get().get(javaId)) {
                        waterloggedPaletteIds.set(i);
                    }

                    if (javaId == Block.JAVA_AIR_ID) {
                        airPaletteId = i;
                    }

                    if (useExtendedCollisions) {
                        if (session.getBlockMappings().getExtendedCollisionBoxes().get(javaId) != null) {
                            extendedCollision = true;
                            extendedCollisionsInPalette++;
                        }
                    }

                    // Check if block is piston, flower or cauldron to see if we'll need to create additional block entities, as they're only block entities in Bedrock
                    // TODO this needs a performance check when my head is clearer
                    BlockState state = BlockState.of(javaId);
                    if (state.block() instanceof BedrockChunkWantsBlockEntityTag) {
                        bedrockOnlyBlockEntityIds.set(i);
                    }
                }

                // Add Bedrock-exclusive block entities
                // We only if the palette contained any blocks that are Bedrock-exclusive block entities to avoid iterating through the whole block data
                // for no reason, as most sections will not contain any pistons or flower pots
                if (!bedrockOnlyBlockEntityIds.isEmpty()) {
                    for (int yzx = 0; yzx < BlockStorage.SIZE; yzx++) {
                        int paletteId = javaData.get(yzx);
                        if (bedrockOnlyBlockEntityIds.get(paletteId)) {
                            BlockState state = BlockState.of(javaPalette.idToState(paletteId));
                            addBedrockOnlyBlockEntity(session, bedrockBlockEntities, chunkX, chunkZ, sectionY, yOffset, yzx, state);
                        }
                    }
                }

                // We need to ensure we use enough bits to represent extended collision blocks in the chunk section
                int sectionCollisionBlocks = 0;
                if (useExtendedCollisions) {
                    int bottomLayerCollisions = extendedCollision ? EXTENDED_COLLISIONS_STORAGE.get().bottomLayerCollisions() : 0;
                    sectionCollisionBlocks = bottomLayerCollisions + extendedCollisionsInPalette;
                }
                int bedrockDataBits = Integer.SIZE - Integer.numberOfLeadingZeros(javaPalette.size() + sectionCollisionBlocks);
                BitArray bedrockData = BitArrayVersion.forBitsCeil(bedrockDataBits).createArray(BlockStorage.SIZE);
                BlockStorage layer0 = new BlockStorage(bedrockData, bedrockPalette);
                BlockStorage[] layers;

                // Convert data array from YZX to XZY coordinate order
                if (waterloggedPaletteIds.isEmpty() && !extendedCollision) {
                    // No blocks are waterlogged, simply convert coordinate order
                    // This could probably be optimized further...
                    for (int yzx = 0; yzx < BlockStorage.SIZE; yzx++) {
                        int paletteId = javaData.get(yzx);
                        int xzy = indexYZXtoXZY(yzx);
                        bedrockData.set(xzy, paletteId);
                    }

                    layers = new BlockStorage[]{ layer0 };
                } else if (!waterloggedPaletteIds.isEmpty() && !extendedCollision) {
                    // The section contains waterlogged blocks, we need to convert coordinate order AND generate a V1 block storage for
                    // layer 1 with palette ID 1 indicating water
                    int[] layer1Data = new int[BlockStorage.SIZE >> 5];
                    for (int yzx = 0; yzx < BlockStorage.SIZE; yzx++) {
                        int paletteId = javaData.get(yzx);
                        int xzy = indexYZXtoXZY(yzx);
                        bedrockData.set(xzy, paletteId);

                        if (waterloggedPaletteIds.get(paletteId)) {
                            layer1Data[xzy >> 5] |= 1 << (xzy & 0x1F);
                        }
                    }
                    
                    // V1 palette
                    IntList layer1Palette = IntList.of(
                            session.getBlockMappings().getBedrockAir().getRuntimeId(), // Air - see BlockStorage's constructor for more information
                            session.getBlockMappings().getBedrockWater().getRuntimeId());

                    layers = new BlockStorage[]{ layer0, new BlockStorage(BitArrayVersion.V1.createArray(BlockStorage.SIZE, layer1Data), layer1Palette) };
                } else if (waterloggedPaletteIds.isEmpty()) {
                    for (int yzx = 0; yzx < BlockStorage.SIZE; yzx++) {
                        int paletteId = javaData.get(yzx);
                        int xzy = indexYZXtoXZY(yzx);
                        bedrockData.set(xzy, paletteId);

                        if (EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY) != 0) {
                            if (paletteId == airPaletteId) {
                                bedrockData.set(xzy, layer0.idFor(EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY)));
                            }
                            EXTENDED_COLLISIONS_STORAGE.get().set(yzx, 0, sectionY);
                            continue;
                        }
                        BlockDefinition aboveBedrockExtendedCollisionDefinition = session.getBlockMappings()
                                .getExtendedCollisionBoxes().get(javaPalette.idToState(paletteId));
                        if (aboveBedrockExtendedCollisionDefinition != null) {
                            EXTENDED_COLLISIONS_STORAGE.get().set((yzx + 0x100) & 0xFFF, aboveBedrockExtendedCollisionDefinition.getRuntimeId(), sectionY);
                            if ((xzy & 0xF) == 15) {
                                thisExtendedCollisionNextSection = true;
                            }
                        }
                    }

                    layers = new BlockStorage[]{ layer0 };
                } else {
                    int[] layer1Data = new int[BlockStorage.SIZE >> 5];
                    for (int yzx = 0; yzx < BlockStorage.SIZE; yzx++) {
                        int paletteId = javaData.get(yzx);
                        int xzy = indexYZXtoXZY(yzx);
                        bedrockData.set(xzy, paletteId);

                        if (waterloggedPaletteIds.get(paletteId)) {
                            layer1Data[xzy >> 5] |= 1 << (xzy & 0x1F);
                        }

                        if (EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY) != 0) {
                            if (paletteId == airPaletteId) {
                                bedrockData.set(xzy, layer0.idFor(EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY)));
                            }
                            EXTENDED_COLLISIONS_STORAGE.get().set(yzx, 0, sectionY);
                            continue;
                        }
                        BlockDefinition aboveBedrockExtendedCollisionDefinition = session.getBlockMappings().getExtendedCollisionBoxes()
                                .get(javaPalette.idToState(paletteId));
                        if (aboveBedrockExtendedCollisionDefinition != null) {
                            EXTENDED_COLLISIONS_STORAGE.get().set((yzx + 0x100) & 0xFFF, aboveBedrockExtendedCollisionDefinition.getRuntimeId(), sectionY);
                            if ((xzy & 0xF) == 15) {
                                thisExtendedCollisionNextSection = true;
                            }
                        }
                    }

                    // V1 palette
                    IntList layer1Palette = IntList.of(
                            session.getBlockMappings().getBedrockAir().getRuntimeId(), // Air - see BlockStorage's constructor for more information
                            session.getBlockMappings().getBedrockWater().getRuntimeId());

                    layers = new BlockStorage[]{ layer0, new BlockStorage(BitArrayVersion.V1.createArray(BlockStorage.SIZE, layer1Data), layer1Palette) };
                }

                sections[bedrockSectionY] = new GeyserChunkSection(layers, subChunkIndex);
                extendedCollisionNextSection = thisExtendedCollisionNextSection;
            }

            if (!session.getErosionHandler().isActive()) {
                session.getChunkCache().addToCache(chunkX, chunkZ, javaChunks);
            }

            final int chunkBlockX = chunkX << 4;
            final int chunkBlockZ = chunkZ << 4;
            for (BlockEntityInfo blockEntity : blockEntities) {
                BlockEntityType type = blockEntity.getType();
                NbtMap tag = blockEntity.getNbt();
                if (type == null) {
                    // As an example: ViaVersion will send -1 if it cannot find the block entity type
                    // Vanilla Minecraft gracefully handles this
                    continue;
                }
                int x = blockEntity.getX(); // Relative to chunk
                int y = blockEntity.getY();
                int z = blockEntity.getZ(); // Relative to chunk

                // Get the Java block state ID from block entity position
                DataPalette section = javaChunks[(y >> 4) - yOffset];
                BlockState blockState = BlockState.of(section.get(x, y & 0xF, z));

                // Note that, since 1.20.5, tags can be null, but Bedrock still needs a default tag to render the item
                // Also, some properties - like banner base colors - are part of the tag and is processed here.
                BlockEntityTranslator blockEntityTranslator = BlockEntityUtils.getBlockEntityTranslator(type);

                // The Java server can send block entity data for blocks that aren't actually those blocks.
                // A Java client ignores these
                if (type == blockState.block().blockEntityType()) {
                    bedrockBlockEntities.add(blockEntityTranslator.getBlockEntityTag(session, type, x + chunkBlockX, y, z + chunkBlockZ, tag, blockState));

                    // Check for custom skulls
                    if (session.getPreferencesCache().showCustomSkulls() && type == BlockEntityType.SKULL && tag != null && tag.containsKey("profile")) {
                        BlockDefinition blockDefinition = SkullBlockEntityTranslator.translateSkull(session, tag, Vector3i.from(x + chunkBlockX, y, z + chunkBlockZ), blockState);
                        if (blockDefinition != null) {
                            int bedrockSectionY = (y >> 4) - (bedrockDimension.minY() >> 4);
                            int subChunkIndex = (y >> 4) + (bedrockDimension.minY() >> 4);
                            if (0 <= bedrockSectionY && bedrockSectionY < maxBedrockSectionY) {
                                // Custom skull is in a section accepted by Bedrock
                                GeyserChunkSection bedrockSection = sections[bedrockSectionY];
                                IntList palette = bedrockSection.getBlockStorageArray()[0].getPalette();
                                if (palette instanceof IntImmutableList || palette instanceof IntLists.Singleton) {
                                    // TODO there has to be a better way to expand the palette .-.
                                    bedrockSection = bedrockSection.copy(subChunkIndex);
                                    sections[bedrockSectionY] = bedrockSection;
                                }
                                bedrockSection.setFullBlock(x, y & 0xF, z, 0, blockDefinition.getRuntimeId());
                            }
                        }
                    }
                }
            }

            // Find highest section
            sectionCount = sections.length - 1;
            while (sectionCount >= 0 && sections[sectionCount] == null) {
                sectionCount--;
            }
            sectionCount++;

            // As of 1.18.30, the amount of biomes read is dependent on how high Bedrock thinks the dimension is
            int biomeCount = bedrockDimension.height() >> 4;

            // Estimate chunk size
            int size = 0;
            for (int i = 0; i < sectionCount; i++) {
                GeyserChunkSection section = sections[i];
                if (section != null) {
                    size += section.estimateNetworkSize();
                } else {
                    size += EMPTY_CHUNK_SECTION_SIZE;
                }
            }
            size += ChunkUtils.EMPTY_BIOME_DATA.length * biomeCount;
            size += 1; // Border blocks
            size += bedrockBlockEntities.size() * 64; // Conservative estimate of 64 bytes per tile entity

            // Allocate output buffer
            byteBuf = ByteBufAllocator.DEFAULT.ioBuffer(size);
            for (int i = 0; i < sectionCount; i++) {
                GeyserChunkSection section = sections[i];
                if (section != null) {
                    section.writeToNetwork(byteBuf);
                } else {
                    int subChunkIndex = (i + (bedrockDimension.minY() >> 4));
                    new GeyserChunkSection(EMPTY_BLOCK_STORAGE, subChunkIndex).writeToNetwork(byteBuf);
                }
            }

            int dimensionOffset = bedrockDimension.minY() >> 4;
            for (int i = 0; i < biomeCount; i++) {
                int biomeYOffset = dimensionOffset + i;
                if (biomeYOffset < yOffset) {
                    // Ignore this biome section since it goes below the height of the Java world
                    byteBuf.writeBytes(ChunkUtils.EMPTY_BIOME_DATA);
                    continue;
                }
                if (biomeYOffset >= (chunkSize + yOffset)) {
                    // This biome section goes above the height of the Java world
                    // The byte written here is a header that says to carry on the biome data from the previous chunk
                    byteBuf.writeByte((127 << 1) | 1);
                    continue;
                }

                DataPalette biomeData = javaBiomes[i + (dimensionOffset - yOffset)];
                BlockStorage biomeStorage = BiomeTranslator.toNewBedrockBiome(session, biomeData);
                biomeStorage.writeToNetwork(byteBuf);
            }

            byteBuf.writeByte(0); // Border blocks - Edu edition only

            // Encode tile entities into buffer
            NBTOutputStream nbtStream = NbtUtils.createNetworkWriter(new ByteBufOutputStream(byteBuf));
            for (NbtMap blockEntity : bedrockBlockEntities) {
                nbtStream.writeTag(blockEntity);
            }
            payload = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(payload);
        } catch (IOException e) {
            session.getGeyser().getLogger().error("IO error while encoding chunk", e);
            return;
        } finally {
            if (byteBuf != null) {
                byteBuf.release(); // Release buffer to allow buffer pooling to be useful
            }
        }

        int lastNormalDimId = session.getLastNormalDimId();
        int dimension = session.getBedrockDimension().bedrockId();
        if (dimension != lastNormalDimId) {
            if (dimension == 0 || dimension == 3) {
                dimension = lastNormalDimId;
            }
        }
        LevelChunkPacket levelChunkPacket = new LevelChunkPacket();
        levelChunkPacket.setSubChunksLength(sectionCount);
        levelChunkPacket.setChunkX(chunkX);
        levelChunkPacket.setChunkZ(chunkZ);
        levelChunkPacket.setDimension(dimension);
        levelChunkPacket.setCachingEnabled(false);
        levelChunkPacket.setData(Unpooled.wrappedBuffer(payload));
        session.sendUpstreamPacket(levelChunkPacket);

        for (Map.Entry<Vector3i, ItemFrameEntity> entry : session.getItemFrameCache().entrySet()) {
            Vector3i position = entry.getKey();
            if ((position.getX() >> 4) == chunkX && (position.getZ() >> 4) == chunkZ) {
                // Update this item frame so it doesn't get lost in the abyss
                //TODO optimize
                entry.getValue().updateBlock(true);
            }
        }
    }

    private static byte[][] createBlobPayloads(GeyserSession session, GeyserChunkSection[] sections,
                                                int sectionCount, byte[] payload, int biomePayloadOffset,
                                                int cachePayloadOffset, int dimensionOffset) throws IOException {
        byte[][] blobs = new byte[sectionCount + 1][];
        ByteBuf blobBuffer = ByteBufAllocator.DEFAULT.ioBuffer();
        try {
            for (int i = 0; i < sectionCount; i++) {
                blobBuffer.clear();
                GeyserChunkSection section = sections[i];
                if (section == null) {
                    section = new GeyserChunkSection(session.getBlockMappings().getBedrockAir().getRuntimeId(),
                            i + dimensionOffset);
                }
                section.writeToCache(blobBuffer, session.getBlockMappings());

                byte[] blob = new byte[blobBuffer.readableBytes()];
                blobBuffer.getBytes(blobBuffer.readerIndex(), blob);
                blobs[i] = blob;
            }
        } finally {
            blobBuffer.release();
        }

        int biomeLength = cachePayloadOffset - biomePayloadOffset;
        byte[] biomeBlob = new byte[biomeLength];
        System.arraycopy(payload, biomePayloadOffset, biomeBlob, 0, biomeLength);
        blobs[sectionCount] = biomeBlob;
        return blobs;
    }

    private static void addBedrockOnlyBlockEntity(GeyserSession session, List<NbtMap> bedrockBlockEntities,
                                                  int chunkX, int chunkZ, int sectionY, int yOffset, int yzx,
                                                  BlockState state) {
        bedrockBlockEntities.add(((BedrockChunkWantsBlockEntityTag) state.block()).createTag(session,
                Vector3i.from(
                        (chunkX << 4) + (yzx & 0xF),
                        ((sectionY + yOffset) << 4) + ((yzx >> 8) & 0xF),
                        (chunkZ << 4) + ((yzx >> 4) & 0xF)
                ),
                state
        ));
    }

    private static boolean copyBlockPaletteDataFast(BitArray bedrockData, BitStorage javaData) {
        BitArrayVersion version = bedrockData.getVersion();
        int bits = version.getId();
        if (bits != 1 && bits != 2 && bits != 4 && bits != 8 && bits != 16) {
            return false;
        }

        int maxEntryValue = version.getMaxEntryValue();
        int[] words = bedrockData.getWords();
        switch (bits) {
            case 1 -> {
                for (int yzx = 0; yzx < BlockStorage.SIZE; yzx++) {
                    int value = javaData.get(yzx);
                    if (value < 0 || value > maxEntryValue) {
                        return false;
                    }
                    int xzy = YZX_TO_XZY[yzx];
                    int mask = 1 << (xzy & 31);
                    int wordIndex = xzy >>> 5;
                    words[wordIndex] = (words[wordIndex] & ~mask) | (value << (xzy & 31));
                }
            }
            case 2 -> {
                for (int yzx = 0; yzx < BlockStorage.SIZE; yzx++) {
                    int value = javaData.get(yzx);
                    if (value < 0 || value > maxEntryValue) {
                        return false;
                    }
                    int xzy = YZX_TO_XZY[yzx];
                    int offset = (xzy & 15) << 1;
                    int mask = maxEntryValue << offset;
                    int wordIndex = xzy >>> 4;
                    words[wordIndex] = (words[wordIndex] & ~mask) | (value << offset);
                }
            }
            case 4 -> {
                for (int yzx = 0; yzx < BlockStorage.SIZE; yzx++) {
                    int value = javaData.get(yzx);
                    if (value < 0 || value > maxEntryValue) {
                        return false;
                    }
                    int xzy = YZX_TO_XZY[yzx];
                    int offset = (xzy & 7) << 2;
                    int mask = maxEntryValue << offset;
                    int wordIndex = xzy >>> 3;
                    words[wordIndex] = (words[wordIndex] & ~mask) | (value << offset);
                }
            }
            case 8 -> {
                for (int yzx = 0; yzx < BlockStorage.SIZE; yzx++) {
                    int value = javaData.get(yzx);
                    if (value < 0 || value > maxEntryValue) {
                        return false;
                    }
                    int xzy = YZX_TO_XZY[yzx];
                    int offset = (xzy & 3) << 3;
                    int mask = maxEntryValue << offset;
                    int wordIndex = xzy >>> 2;
                    words[wordIndex] = (words[wordIndex] & ~mask) | (value << offset);
                }
            }
            case 16 -> {
                for (int yzx = 0; yzx < BlockStorage.SIZE; yzx++) {
                    int value = javaData.get(yzx);
                    if (value < 0 || value > maxEntryValue) {
                        return false;
                    }
                    int xzy = YZX_TO_XZY[yzx];
                    int offset = (xzy & 1) << 4;
                    int mask = maxEntryValue << offset;
                    int wordIndex = xzy >>> 1;
                    words[wordIndex] = (words[wordIndex] & ~mask) | (value << offset);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }


    static final class ExtendedCollisionsStorage {
        private int[] data;
        private int sectionY;
    
        int get(int index, int sY) {
            if (data == null) {
                return 0;
            }
            if (!(sY ==  sectionY || sY == sectionY + 1)) {
                data = null;
                return 0;
            }
            return data[index];
        }
    
        void set(int index, int value, int sY) {
            ensureDataExists();
            data[index] = value;
            sectionY = sY;
        }

        void clear() {
            data = null;
        }
    
        int bottomLayerCollisions() {
            if (data == null) {
                return 0;
            }
    
            IntSet uniqueNonZeroSet = new IntOpenHashSet();
            for (int i = 0; i < BlockStorage.SIZE / 16; i++) {
                if (data[i] != 0) {
                    uniqueNonZeroSet.add(data[i]);
                }
            }
            return uniqueNonZeroSet.size();
        }
    
        private void ensureDataExists() {
            if (data == null) {
                data = new int[BlockStorage.SIZE];
            }
        }
    }
}
