/*
 * Copyright (c) 2026 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.network.netease.serializers;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongList;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v361.serializer.ClientCacheBlobStatusSerializer_v361;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheBlobStatusPacket;
import org.cloudburstmc.protocol.common.util.VarInts;

import static org.cloudburstmc.protocol.common.util.Preconditions.checkArgument;

/**
 * NetEase clients can acknowledge all blobs queued during a lobby load in one packet.
 * That list can legitimately exceed the generic codec list limit, so this serializer
 * applies the protocol-specific blob status limit instead.
 */
public final class NeteaseClientCacheBlobStatusSerializer extends ClientCacheBlobStatusSerializer_v361 {
    public static final NeteaseClientCacheBlobStatusSerializer INSTANCE = new NeteaseClientCacheBlobStatusSerializer();
    public static final int MAX_STATUS_ENTRIES = 4095;

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, ClientCacheBlobStatusPacket packet) {
        int naksLength = VarInts.readUnsignedInt(buffer);
        int acksLength = VarInts.readUnsignedInt(buffer);
        checkArgument(naksLength >= 0 && acksLength >= 0,
                "Blob status list sizes must fit in signed integers");

        long totalEntries = (long) naksLength + acksLength;
        checkArgument(totalEntries <= MAX_STATUS_ENTRIES,
                "Tried to read %s blob status entries but maximum is %s", totalEntries, MAX_STATUS_ENTRIES);

        long requiredBytes = totalEntries * Long.BYTES;
        checkArgument(requiredBytes <= buffer.readableBytes(),
                "Tried to read %s blob status bytes but only has %s readable", requiredBytes, buffer.readableBytes());

        LongList naks = packet.getNaks();
        for (int i = 0; i < naksLength; i++) {
            naks.add(buffer.readLongLE());
        }

        LongList acks = packet.getAcks();
        for (int i = 0; i < acksLength; i++) {
            acks.add(buffer.readLongLE());
        }
    }

    private NeteaseClientCacheBlobStatusSerializer() {
    }
}
