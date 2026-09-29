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
import org.cloudburstmc.protocol.bedrock.codec.v630.serializer.SetPlayerInventoryOptionsSerializer_v630;
import org.cloudburstmc.protocol.bedrock.packet.SetPlayerInventoryOptionsPacket;
import org.cloudburstmc.protocol.common.util.VarInts;

public final class NeteaseSetPlayerInventoryOptionsSerializer {

    public static final BedrockPacketSerializer<SetPlayerInventoryOptionsPacket> V860 = new SetPlayerInventoryOptionsSerializer_v630() {
        @Override
        public void serialize(ByteBuf buffer, BedrockCodecHelper helper, SetPlayerInventoryOptionsPacket packet) {
            super.serialize(buffer, helper, packet);
            // NetEase v860+ requires this unnamed field even when no option is set.
            VarInts.writeUnsignedInt(buffer, 0);
        }

        @Override
        public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, SetPlayerInventoryOptionsPacket packet) {
            super.deserialize(buffer, helper, packet);
            // This client-only option has no Java inventory equivalent.
            if (buffer.isReadable()) {
                VarInts.readUnsignedInt(buffer);
            }
        }
    };

    private NeteaseSetPlayerInventoryOptionsSerializer() {
    }
}
