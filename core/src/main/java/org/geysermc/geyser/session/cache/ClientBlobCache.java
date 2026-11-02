/*
 * Copyright (c) 2019-2026 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.session.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheBlobStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheMissResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.geysermc.geyser.session.GeyserSession;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Implements the Bedrock client blob cache transaction state for one session.
 * Blob contents are content-addressed and shared globally, while pending ACK/NAK
 * state remains session-local.
 */
public final class ClientBlobCache {
    private static volatile boolean globallyEnabled = true;
    private static final long GLOBAL_CACHE_MAX_BYTES =
            Math.max(1L, Long.getLong("Geyser.NeteaseClientBlobCacheMaxBytes", 256L * 1024L * 1024L));
    private static final long GLOBAL_CACHE_EXPIRE_AFTER_ACCESS_MINUTES =
            Math.max(1L, Long.getLong("Geyser.NeteaseClientBlobCacheExpireAfterAccessMinutes", 10L));
    private static final int MAX_PENDING_BLOBS =
            Math.max(1, Integer.getInteger("Geyser.NeteaseClientBlobCacheMaxPendingBlobs", 8192));
    private static final int MAX_BLOBS_PER_CHUNK = 65;
    private static final long SUMMARY_INTERVAL_SECONDS =
            Math.max(1L, Long.getLong("Geyser.ClientBlobCacheSummaryIntervalSeconds", 10L));
    private static final long SUMMARY_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(SUMMARY_INTERVAL_SECONDS);

    private static final Cache<Long, Blob> GLOBAL_BLOBS = CacheBuilder.<Long, Blob>newBuilder()
            .maximumWeight(GLOBAL_CACHE_MAX_BYTES)
            .weigher((Long hash, Blob blob) -> blob.length)
            .expireAfterAccess(GLOBAL_CACHE_EXPIRE_AFTER_ACCESS_MINUTES, TimeUnit.MINUTES)
            .build();
    private static final LongAdder GLOBAL_BLOB_STORE_HITS = new LongAdder();
    private static final LongAdder GLOBAL_BLOB_STORE_MISSES = new LongAdder();
    private static final LongAdder GLOBAL_CLIENT_BLOB_HITS = new LongAdder();
    private static final LongAdder GLOBAL_CLIENT_BLOB_MISSES = new LongAdder();
    private static final LongAdder GLOBAL_BLOB_FAILURES = new LongAdder();
    private static final LongAdder SUMMARY_PREPARED_CHUNKS = new LongAdder();
    private static final LongAdder SUMMARY_STORE_HITS = new LongAdder();
    private static final LongAdder SUMMARY_STORE_MISSES = new LongAdder();
    private static final LongAdder SUMMARY_CLIENT_HITS = new LongAdder();
    private static final LongAdder SUMMARY_CLIENT_MISSES = new LongAdder();
    private static final LongAdder SUMMARY_RESPONSES = new LongAdder();
    private static final LongAdder SUMMARY_FAILURES = new LongAdder();
    private static final AtomicLong SUMMARY_LAST_LOG_NANOS = new AtomicLong(System.nanoTime());
    private static final TrafficCounters GLOBAL_TRAFFIC = new TrafficCounters();
    private static final TrafficCounters SUMMARY_TRAFFIC = new TrafficCounters();

    private static final long PRIME64_1 = -7046029288634856825L;
    private static final long PRIME64_2 = -4417276706812531889L;
    private static final long PRIME64_3 = 1609587929392839161L;
    private static final long PRIME64_4 = -8796714831421723037L;
    private static final long PRIME64_5 = 2870177450012600261L;

    private final GeyserSession session;
    private final ConcurrentMap<Long, Blob> pendingBlobs = new ConcurrentHashMap<>();
    private volatile boolean statusReceived;
    private volatile boolean clientSupported;

    public ClientBlobCache(GeyserSession session) {
        this.session = session;
    }

    public void setClientSupported(boolean supported) {
        if (this.statusReceived) {
            this.session.getGeyser().getLogger().debug("Ignoring duplicate ClientCacheStatusPacket from "
                    + this.session.bedrockUsername());
            return;
        }

        this.clientSupported = supported;
        this.statusReceived = true;
        if (!supported) {
            this.pendingBlobs.clear();
        }
        this.session.getGeyser().getLogger().info("[ClientBlobCache] player=" + this.session.bedrockUsername()
                + " supported=" + supported + " enabled=" + isEnabled());
    }

    public boolean isEnabled() {
        return globallyEnabled && this.statusReceived && this.clientSupported;
    }

    public static boolean isGloballyEnabled() {
        return globallyEnabled;
    }

    public static void setGloballyEnabled(boolean enabled) {
        globallyEnabled = enabled;
        if (!enabled) {
            GLOBAL_BLOBS.invalidateAll();
            GLOBAL_BLOBS.cleanUp();
        }
    }

    public static long globalBlobCount() {
        return GLOBAL_BLOBS.size();
    }

    /**
     * Configures a LevelChunkPacket for blob caching when the client negotiated support.
     * If registration cannot be completed safely, the packet transparently falls back to
     * the original complete payload.
     */
    public boolean prepareChunkPacket(LevelChunkPacket packet, byte[] payload, @Nullable byte[][] blobs,
                                      int cachePayloadOffset) {
        packet.getBlobIds().clear();
        if (cachePayloadOffset < 0 || cachePayloadOffset > payload.length) {
            recordFailure("invalidPayloadOffset");
            packet.setCachingEnabled(false);
            packet.setData(Unpooled.wrappedBuffer(payload));
            return false;
        }

        LongList blobIds = registerBlobs(blobs);
        if (blobIds == null) {
            packet.setCachingEnabled(false);
            packet.setData(Unpooled.wrappedBuffer(payload));
            return false;
        }

        packet.setCachingEnabled(true);
        packet.getBlobIds().addAll(blobIds);
        packet.setData(Unpooled.wrappedBuffer(payload, cachePayloadOffset, payload.length - cachePayloadOffset));
        if (!this.session.getUpstream().isClosed()) {
            // Compare the ordinary network payload, not the larger persistent-NBT blob representation.
            long baselineBytes = (long) payload.length + unsignedVarIntSize(payload.length);
            int tailBytes = payload.length - cachePayloadOffset;
            long cacheChunkBytes = (long) tailBytes + unsignedVarIntSize(tailBytes)
                    + (long) blobIds.size() * Long.BYTES + unsignedVarIntSize(blobIds.size());
            GLOBAL_TRAFFIC.addChunk(baselineBytes, cacheChunkBytes);
            SUMMARY_TRAFFIC.addChunk(baselineBytes, cacheChunkBytes);
        }
        SUMMARY_PREPARED_CHUNKS.increment();
        logSummary();
        return true;
    }

    public void handleBlobStatus(ClientCacheBlobStatusPacket packet) {
        // Continue completing transactions that were sent before the global feature was disabled.
        if (!this.statusReceived || !this.clientSupported) {
            return;
        }

        ClientCacheMissResponsePacket response = new ClientCacheMissResponsePacket();
        int unknownNaks = 0;
        long responseBytes = 0;
        for (long blobId : packet.getNaks()) {
            Blob blob = this.pendingBlobs.get(blobId);
            if (blob == null) {
                unknownNaks++;
                continue;
            }
            if (!response.getBlobs().containsKey(blobId)) {
                response.getBlobs().put(blobId, blob.buffer());
                responseBytes += (long) blob.length + unsignedVarIntSize(blob.length) + Long.BYTES;
            }
        }

        for (long blobId : packet.getAcks()) {
            this.pendingBlobs.remove(blobId);
        }

        int clientHits = packet.getAcks().size();
        int clientMisses = packet.getNaks().size();
        GLOBAL_CLIENT_BLOB_HITS.add(clientHits);
        GLOBAL_CLIENT_BLOB_MISSES.add(clientMisses);
        SUMMARY_CLIENT_HITS.add(clientHits);
        SUMMARY_CLIENT_MISSES.add(clientMisses);
        long statusBytes = ((long) clientHits + clientMisses) * Long.BYTES
                + unsignedVarIntSize(clientHits) + unsignedVarIntSize(clientMisses);
        GLOBAL_TRAFFIC.statusBytes.add(statusBytes);
        SUMMARY_TRAFFIC.statusBytes.add(statusBytes);

        int responseCount = response.getBlobs().size();
        int failures = unknownNaks;
        if (responseCount == 0) {
            response.release();
        } else if (this.session.getUpstream().isClosed()) {
            failures += responseCount;
            response.release();
        } else {
            // Cache misses block chunk construction client-side, so bypass the normal send queue.
            this.session.sendUpstreamPacketImmediately(response);
            responseBytes += unsignedVarIntSize(responseCount);
            GLOBAL_TRAFFIC.missResponseBytes.add(responseBytes);
            SUMMARY_TRAFFIC.missResponseBytes.add(responseBytes);
        }
        GLOBAL_BLOB_FAILURES.add(failures);
        SUMMARY_RESPONSES.add(responseCount);
        SUMMARY_FAILURES.add(failures);

        if (unknownNaks != 0) {
            this.session.getGeyser().getLogger().debug("[ClientBlobCache] player=" + this.session.bedrockUsername()
                    + " ignoredUnknownNaks=" + unknownNaks + " pending=" + this.pendingBlobs.size());
        }
        logSummary();
    }

    private synchronized @Nullable LongList registerBlobs(@Nullable byte[][] blobPayloads) {
        if (!isEnabled() || blobPayloads == null) {
            return null;
        }
        if (blobPayloads.length == 0 || blobPayloads.length > MAX_BLOBS_PER_CHUNK) {
            recordFailure("invalidBlobCount");
            return null;
        }

        LongArrayList blobIds = new LongArrayList(blobPayloads.length);
        Blob[] blobs = new Blob[blobPayloads.length];
        int newPendingBlobs = 0;
        int storeHits = 0;
        int storeMisses = 0;
        for (int i = 0; i < blobPayloads.length; i++) {
            byte[] payload = blobPayloads[i];
            if (payload == null) {
                recordFailure("nullBlobPayload");
                return null;
            }

            long blobId = xxHash64(payload, 0, payload.length);
            Blob candidate = new Blob(payload);
            Blob blob = GLOBAL_BLOBS.getIfPresent(blobId);
            if (blob == null) {
                storeMisses++;
                Blob raced = GLOBAL_BLOBS.asMap().putIfAbsent(blobId, candidate);
                blob = raced == null ? candidate : raced;
            } else {
                storeHits++;
            }
            if (!blob.contentEquals(payload)) {
                recordFailure("xxHash64Collision");
                this.session.getGeyser().getLogger().error("XXHash64 collision in Bedrock client blob cache for id "
                        + Long.toUnsignedString(blobId));
                return null;
            }

            blobIds.add(blobId);
            blobs[i] = blob;
            if (!this.pendingBlobs.containsKey(blobId)) {
                newPendingBlobs++;
            }
        }

        if (this.pendingBlobs.size() + newPendingBlobs > MAX_PENDING_BLOBS) {
            recordFailure("pendingLimit");
            this.session.getGeyser().getLogger().debug("[ClientBlobCache] player=" + this.session.bedrockUsername()
                    + " pending limit reached; sending full chunk payload");
            return null;
        }

        for (int i = 0; i < blobs.length; i++) {
            this.pendingBlobs.put(blobIds.getLong(i), blobs[i]);
        }
        GLOBAL_BLOB_STORE_HITS.add(storeHits);
        GLOBAL_BLOB_STORE_MISSES.add(storeMisses);
        SUMMARY_STORE_HITS.add(storeHits);
        SUMMARY_STORE_MISSES.add(storeMisses);
        return blobIds;
    }

    public void recordFailure(String reason) {
        GLOBAL_BLOB_FAILURES.increment();
        SUMMARY_FAILURES.increment();
        this.session.getGeyser().getLogger().debug("[ClientBlobCacheFailure] player=" + this.session.bedrockUsername()
                + " reason=" + reason + " totalFailures=" + GLOBAL_BLOB_FAILURES.sum());
        logSummary();
    }

    private void logSummary() {
        long now = System.nanoTime();
        long previousLog = SUMMARY_LAST_LOG_NANOS.get();
        if (now - previousLog < SUMMARY_INTERVAL_NANOS
                || !SUMMARY_LAST_LOG_NANOS.compareAndSet(previousLog, now)) {
            return;
        }

        TrafficSnapshot traffic = SUMMARY_TRAFFIC.snapshot(true);
        TrafficSnapshot totalTraffic = GLOBAL_TRAFFIC.snapshot(false);
        long storePayloadBytes = GLOBAL_BLOBS.asMap().values().stream().mapToLong(blob -> blob.length).sum();
        this.session.getGeyser().getLogger().info("[ClientBlobCacheSummary] windowSeconds="
                + ((now - previousLog) / 1_000_000_000.0D)
                + " preparedChunks=" + SUMMARY_PREPARED_CHUNKS.sumThenReset()
                + " storeHits=" + SUMMARY_STORE_HITS.sumThenReset()
                + " storeMisses=" + SUMMARY_STORE_MISSES.sumThenReset()
                + " clientHits=" + SUMMARY_CLIENT_HITS.sumThenReset()
                + " clientMisses=" + SUMMARY_CLIENT_MISSES.sumThenReset()
                + " responses=" + SUMMARY_RESPONSES.sumThenReset()
                + " failures=" + SUMMARY_FAILURES.sumThenReset()
                + " entries=" + GLOBAL_BLOBS.size()
                + " totalStoreHits=" + GLOBAL_BLOB_STORE_HITS.sum()
                + " totalStoreMisses=" + GLOBAL_BLOB_STORE_MISSES.sum()
                + " totalClientHits=" + GLOBAL_CLIENT_BLOB_HITS.sum()
                + " totalClientMisses=" + GLOBAL_CLIENT_BLOB_MISSES.sum()
                + " totalFailures=" + GLOBAL_BLOB_FAILURES.sum()
                + " byteAccounting=uncompressed-estimate"
                + traffic.logFields("")
                + totalTraffic.logFields("total")
                + " totalRawSavedGiBEstimate=" + String.format(Locale.ROOT, "%.3f", totalTraffic.savedBytesEstimate() / 1073741824.0D)
                + " storePayloadBytes=" + storePayloadBytes
                + " storeMaxPayloadBytes=" + GLOBAL_CACHE_MAX_BYTES);
    }

    private static int unsignedVarIntSize(int value) {
        return Math.max(1, (Integer.SIZE - Integer.numberOfLeadingZeros(value) + 6) / 7);
    }

    static TrafficSnapshot totalTraffic() {
        return GLOBAL_TRAFFIC.snapshot(false);
    }

    /**
     * Uncompressed size estimates for successfully prepared cache-mode chunks and submitted miss responses.
     * Counts payload lengths, hash IDs and their VarInt prefixes. Common LevelChunk fields, packet/batch
     * headers, compression, encryption, RakNet overhead and retransmissions are excluded.
     * ACK/NAK bytes are inbound and reported separately; they never count as saved outbound bytes.
     */
    record TrafficSnapshot(long baselineBytes, long cacheChunkBytes, long missResponseBytes, long statusBytes) {
        long savedBytesEstimate() {
            // Do not clamp: misses can arrive in later windows or cost more than the ordinary chunk payload.
            return baselineBytes - cacheChunkBytes - missResponseBytes;
        }

        double savedPercentEstimate() {
            return baselineBytes == 0 ? 0.0D : savedBytesEstimate() * 100.0D / baselineBytes;
        }

        private String logFields(String prefix) {
            String raw = prefix.isEmpty() ? "raw" : prefix + "Raw";
            return " " + raw + "BaselineBytes=" + baselineBytes
                    + " " + raw + "CacheChunkBytes=" + cacheChunkBytes
                    + " " + raw + "MissResponseBytes=" + missResponseBytes
                    + " " + raw + "StatusBytes=" + statusBytes
                    + " " + raw + "SavedBytesEstimate=" + savedBytesEstimate()
                    + " " + raw + "SavedPercentEstimate=" + String.format(Locale.ROOT, "%.2f", savedPercentEstimate());
        }
    }

    private static final class TrafficCounters {
        private final LongAdder baselineBytes = new LongAdder();
        private final LongAdder cacheChunkBytes = new LongAdder();
        private final LongAdder missResponseBytes = new LongAdder();
        private final LongAdder statusBytes = new LongAdder();

        private void addChunk(long baseline, long cached) {
            baselineBytes.add(baseline);
            cacheChunkBytes.add(cached);
        }

        private TrafficSnapshot snapshot(boolean reset) {
            return new TrafficSnapshot(reset ? baselineBytes.sumThenReset() : baselineBytes.sum(),
                    reset ? cacheChunkBytes.sumThenReset() : cacheChunkBytes.sum(),
                    reset ? missResponseBytes.sumThenReset() : missResponseBytes.sum(),
                    reset ? statusBytes.sumThenReset() : statusBytes.sum());
        }
    }

    static long xxHash64(byte[] data, int offset, int length) {
        int end = offset + length;
        int index = offset;
        long hash;

        if (length >= 32) {
            long v1 = PRIME64_1 + PRIME64_2;
            long v2 = PRIME64_2;
            long v3 = 0;
            long v4 = -PRIME64_1;
            int limit = end - 32;
            do {
                v1 = round(v1, readLongLE(data, index));
                index += 8;
                v2 = round(v2, readLongLE(data, index));
                index += 8;
                v3 = round(v3, readLongLE(data, index));
                index += 8;
                v4 = round(v4, readLongLE(data, index));
                index += 8;
            } while (index <= limit);

            hash = Long.rotateLeft(v1, 1) + Long.rotateLeft(v2, 7)
                    + Long.rotateLeft(v3, 12) + Long.rotateLeft(v4, 18);
            hash = mergeRound(hash, v1);
            hash = mergeRound(hash, v2);
            hash = mergeRound(hash, v3);
            hash = mergeRound(hash, v4);
        } else {
            hash = PRIME64_5;
        }

        hash += length;
        while (index <= end - 8) {
            long value = round(0, readLongLE(data, index));
            hash ^= value;
            hash = Long.rotateLeft(hash, 27) * PRIME64_1 + PRIME64_4;
            index += 8;
        }
        if (index <= end - 4) {
            hash ^= Integer.toUnsignedLong(readIntLE(data, index)) * PRIME64_1;
            hash = Long.rotateLeft(hash, 23) * PRIME64_2 + PRIME64_3;
            index += 4;
        }
        while (index < end) {
            hash ^= (data[index] & 0xffL) * PRIME64_5;
            hash = Long.rotateLeft(hash, 11) * PRIME64_1;
            index++;
        }

        hash ^= hash >>> 33;
        hash *= PRIME64_2;
        hash ^= hash >>> 29;
        hash *= PRIME64_3;
        hash ^= hash >>> 32;
        return hash;
    }

    private static long round(long accumulator, long input) {
        accumulator += input * PRIME64_2;
        accumulator = Long.rotateLeft(accumulator, 31);
        return accumulator * PRIME64_1;
    }

    private static long mergeRound(long accumulator, long value) {
        accumulator ^= round(0, value);
        return accumulator * PRIME64_1 + PRIME64_4;
    }

    private static long readLongLE(byte[] data, int offset) {
        return (data[offset] & 0xffL)
                | ((data[offset + 1] & 0xffL) << 8)
                | ((data[offset + 2] & 0xffL) << 16)
                | ((data[offset + 3] & 0xffL) << 24)
                | ((data[offset + 4] & 0xffL) << 32)
                | ((data[offset + 5] & 0xffL) << 40)
                | ((data[offset + 6] & 0xffL) << 48)
                | ((data[offset + 7] & 0xffL) << 56);
    }

    private static int readIntLE(byte[] data, int offset) {
        return (data[offset] & 0xff)
                | ((data[offset + 1] & 0xff) << 8)
                | ((data[offset + 2] & 0xff) << 16)
                | ((data[offset + 3] & 0xff) << 24);
    }

    private static final class Blob {
        private final byte[] data;
        private final int length;

        private Blob(byte[] data) {
            this.data = data;
            this.length = data.length;
        }

        private ByteBuf buffer() {
            return Unpooled.wrappedBuffer(this.data);
        }

        private boolean contentEquals(byte[] other) {
            if (this.length != other.length) {
                return false;
            }
            for (int i = 0; i < this.length; i++) {
                if (this.data[i] != other[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}
