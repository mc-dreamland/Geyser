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

package org.geysermc.geyser.network.netease.serializers;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketSerializer;
import org.cloudburstmc.protocol.bedrock.data.skin.ImageData;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.cloudburstmc.protocol.bedrock.packet.ConfirmSkinPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.util.List;

public final class NeteaseConfirmSkinSerializer {
    public static final BedrockPacketSerializer<ConfirmSkinPacket> INSTANCE = new BedrockPacketSerializer<ConfirmSkinPacket>() {
        @Override
        public void serialize(ByteBuf buffer, BedrockCodecHelper helper, ConfirmSkinPacket packet) {
            List<PlayerListPacket.Entry> entries = packet.getEntries();
            VarInts.writeUnsignedInt(buffer, entries.size());

            for (PlayerListPacket.Entry entry : entries) {
                buffer.writeBoolean(true);
                helper.writeUuid(buffer, entry.getUuid());

                SerializedSkin skin = entry.getSkin();
                ImageData skinData = skin == null || skin.getSkinData() == null ? ImageData.EMPTY : skin.getSkinData();
                helper.writeByteArray(buffer, skinData.getImage());
            }

            for (PlayerListPacket.Entry entry : entries) {
                helper.writeString(buffer, String.valueOf(entry.getUid()));
            }

            for (PlayerListPacket.Entry entry : entries) {
                SerializedSkin skin = entry.getSkin();
                String geometryData = skin == null || skin.getGeometryData() == null ? "" : skin.getGeometryData();
                helper.writeString(buffer, geometryData);
            }
        }

        @Override
        public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, ConfirmSkinPacket packet) {
            throw new UnsupportedOperationException("ConfirmSkinPacket is clientbound only");
        }
    };

    private NeteaseConfirmSkinSerializer() {
    }
}
