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

package org.geysermc.geyser.network.netease;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v361.serializer.ClientCacheStatusSerializer_v361;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheBlobStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheStatusPacket;
import org.cloudburstmc.protocol.common.util.VarInts;
import org.geysermc.geyser.network.GameProtocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientBlobCacheCodecTest {

    @Test
    void restoresClientCacheSerializersForNeteaseCodecs() {
        for (int protocolVersion : GameProtocol.SUPPORTED_BEDROCK_PROTOCOLS) {
            BedrockCodec codec = GameProtocol.getBedrockCodec(protocolVersion);
            assertNotNull(codec);
            assertSame(ClientCacheStatusSerializer_v361.INSTANCE,
                    codec.getPacketDefinition(ClientCacheStatusPacket.class).getSerializer());
            assertSame(NeteaseClientCacheBlobStatusSerializer.INSTANCE,
                    codec.getPacketDefinition(ClientCacheBlobStatusPacket.class).getSerializer());
        }
    }

    @Test
    void acceptsLargeNeteaseBlobNakBatch() {
        int naksLength = 1925;
        ByteBuf buffer = Unpooled.buffer();
        try {
            VarInts.writeUnsignedInt(buffer, naksLength);
            VarInts.writeUnsignedInt(buffer, 0);
            for (int i = 0; i < naksLength; i++) {
                buffer.writeLongLE(i);
            }

            ClientCacheBlobStatusPacket packet = new ClientCacheBlobStatusPacket();
            BedrockCodec codec = GameProtocol.getBedrockCodec(GameProtocol.SUPPORTED_BEDROCK_PROTOCOLS.getInt(0));
            NeteaseClientCacheBlobStatusSerializer.INSTANCE.deserialize(buffer, codec.createHelper(), packet);

            assertEquals(naksLength, packet.getNaks().size());
            assertEquals(naksLength - 1L, packet.getNaks().getLong(naksLength - 1));
            assertEquals(0, packet.getAcks().size());
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsTruncatedNeteaseBlobStatus() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            VarInts.writeUnsignedInt(buffer, 1);
            VarInts.writeUnsignedInt(buffer, 0);

            ClientCacheBlobStatusPacket packet = new ClientCacheBlobStatusPacket();
            BedrockCodec codec = GameProtocol.getBedrockCodec(GameProtocol.SUPPORTED_BEDROCK_PROTOCOLS.getInt(0));
            assertThrows(IllegalArgumentException.class, () ->
                    NeteaseClientCacheBlobStatusSerializer.INSTANCE.deserialize(buffer, codec.createHelper(), packet));
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsBlobStatusAboveProtocolLimit() {
        int naksLength = (NeteaseClientCacheBlobStatusSerializer.MAX_STATUS_ENTRIES / 2) + 1;
        int acksLength = NeteaseClientCacheBlobStatusSerializer.MAX_STATUS_ENTRIES - naksLength + 1;
        ByteBuf buffer = Unpooled.buffer();
        try {
            VarInts.writeUnsignedInt(buffer, naksLength);
            VarInts.writeUnsignedInt(buffer, acksLength);

            ClientCacheBlobStatusPacket packet = new ClientCacheBlobStatusPacket();
            BedrockCodec codec = GameProtocol.getBedrockCodec(GameProtocol.SUPPORTED_BEDROCK_PROTOCOLS.getInt(0));
            assertThrows(IllegalArgumentException.class, () ->
                    NeteaseClientCacheBlobStatusSerializer.INSTANCE.deserialize(buffer, codec.createHelper(), packet));
        } finally {
            buffer.release();
        }
    }
}
