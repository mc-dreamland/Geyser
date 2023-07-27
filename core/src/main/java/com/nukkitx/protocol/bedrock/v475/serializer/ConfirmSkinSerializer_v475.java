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

package com.nukkitx.protocol.bedrock.v475.serializer;

import com.nukkitx.network.VarInts;
import com.nukkitx.protocol.bedrock.BedrockPacketHelper;
import com.nukkitx.protocol.bedrock.BedrockPacketSerializer;
import com.nukkitx.protocol.bedrock.packet.ConfirmSkinPacket;
import com.nukkitx.protocol.bedrock.packet.NeteaseCustomPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConfirmSkinSerializer_v475 implements BedrockPacketSerializer<ConfirmSkinPacket>{
    public static final ConfirmSkinSerializer_v475 INSTANCE = new ConfirmSkinSerializer_v475();

    @Override
    public void serialize(ByteBuf buffer, BedrockPacketHelper helper, ConfirmSkinPacket packet) {
        /**
         * VarInts.writeUnsignedInt(buffer, packet.getEntries().size());
         * for(ConfirmSkinEntry entry : packet.getEntries()){
         *     buffer.writeBoolean(entry.valid);
         *     helper.writeUuid(buffer, entry.getUuid());
         *     helper.writeString(buffer, entry.skinImageData);
         * }
         * for(ConfirmSkinEntry entry : packet.getEntries()){
         *     // 通过uuid获取的uid
         *     String uid;
         *     helper.writeString(buffer, uid);
         * }
         * for(ConfirmSkinEntry entry : packet.getEntries()){
         *     // 通过uuid获取的uid
         *     String uid;
         *     helper.writeString(buffer, entry.geoDataStr);
         * }
         */

        VarInts.writeUnsignedInt(buffer, 1);
        buffer.writeBoolean(true);
        helper.writeUuid(buffer, packet.getUuid());
        helper.writeByteArray(buffer, packet.getSkinData());
        helper.writeString(buffer, String.valueOf(packet.getUid()));
        helper.writeString(buffer, packet.getGeometry());

    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockPacketHelper helper, ConfirmSkinPacket packet) {


    }


}
