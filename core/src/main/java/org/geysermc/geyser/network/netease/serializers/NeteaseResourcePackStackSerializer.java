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
 */

package org.geysermc.geyser.network.netease.serializers;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketSerializer;
import org.cloudburstmc.protocol.bedrock.codec.v898.serializer.ResourcePackStackSerializer_v898;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.util.ArrayList;

public final class NeteaseResourcePackStackSerializer {

    public static final BedrockPacketSerializer<ResourcePackStackPacket> V898 = new ResourcePackStackSerializer_v898() {
        @Override
        public void serialize(ByteBuf buffer, BedrockCodecHelper helper, ResourcePackStackPacket packet) {
            // v898 has one ordinary stack: behavior packs first, then resource packs.
            // Merge into a separate list so encoding never changes the original packet.
            var packs = new ArrayList<ResourcePackStackPacket.Entry>(
                    packet.getBehaviorPacks().size() + packet.getResourcePacks().size());
            packs.addAll(packet.getBehaviorPacks());
            packs.addAll(packet.getResourcePacks());
            buffer.writeBoolean(packet.isForcedToAccept());
            helper.writeArray(buffer, packs, this::writeEntry);
            helper.writeString(buffer, packet.getGameVersion());
            helper.writeExperiments(buffer, packet.getExperiments());
            buffer.writeBoolean(packet.isExperimentsPreviouslyToggled());
            buffer.writeBoolean(packet.isHasEditorPacks());
            // Geyser has no NetEase VIP packs. These lists follow hasEditorPacks.
            VarInts.writeUnsignedInt(buffer, 0); // mNeteaseVipResIdsAndVersions
            VarInts.writeUnsignedInt(buffer, 0); // mNeteaseVipBehIdsAndVersions
        }

        @Override
        public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, ResourcePackStackPacket packet) {
            super.deserialize(buffer, helper, packet);
            // NetEase 3.10 checks for remaining bytes separately before each VIP list.
            readVipPacks(buffer, helper);
            readVipPacks(buffer, helper);
        }

        private void readVipPacks(ByteBuf buffer, BedrockCodecHelper helper) {
            if (buffer.isReadable()) {
                // Consume the full entries without mixing VIP packs into the ordinary stack.
                helper.readArray(buffer, new ArrayList<ResourcePackStackPacket.Entry>(), this::readEntry);
            }
        }
    };

    private NeteaseResourcePackStackSerializer() {
    }
}
