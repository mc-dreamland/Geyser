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
import org.cloudburstmc.protocol.common.util.VarInts;
import org.geysermc.geyser.network.netease.packets.SetDimensionLocalTimePacket;

public final class SetDimensionLocalTimeSerializer implements BedrockPacketSerializer<SetDimensionLocalTimePacket> {

    public static final SetDimensionLocalTimeSerializer INSTANCE = new SetDimensionLocalTimeSerializer();

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, SetDimensionLocalTimePacket packet) {
        if (!packet.isLocalTimeEnabled()) {
            return;
        }

        VarInts.writeInt(buffer, packet.getTime());
        buffer.writeBoolean(packet.isTickDayTime());
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, SetDimensionLocalTimePacket packet) {
        if (!buffer.isReadable()) {
            packet.setLocalTimeEnabled(false);
            packet.setTime(0);
            packet.setTickDayTime(true);
            return;
        }

        packet.setLocalTimeEnabled(true);
        packet.setTime(VarInts.readInt(buffer));
        packet.setTickDayTime(buffer.readBoolean());
    }

    private SetDimensionLocalTimeSerializer() {
    }
}
