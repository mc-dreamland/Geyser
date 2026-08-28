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
import org.cloudburstmc.protocol.bedrock.codec.v685.serializer.TextSerializer_v685;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;

public final class NeteaseTextSerializer {

    public static final BedrockPacketSerializer<TextPacket> INSTANCE = new TextSerializer_v685() {
        @Override
        public void serialize(ByteBuf buffer, BedrockCodecHelper helper, TextPacket packet) {
            //V554

            TextPacket.Type type = packet.getType();
            // 网易客户端收到聊天包会掉线，目前将所有聊天都视作系统命令
            if (type.equals(TextPacket.Type.CHAT)) {
                type = TextPacket.Type.SYSTEM;
            }
            buffer.writeByte(type.ordinal());
            buffer.writeBoolean(packet.isNeedsTranslation());

            switch (type) {
                case WHISPER:
                case ANNOUNCEMENT:
                    helper.writeString(buffer, packet.getSourceName());
                case CHAT:
                case RAW:
                case TIP:
                case SYSTEM:
                case JSON:
                case WHISPER_JSON:
                case ANNOUNCEMENT_JSON:
                    helper.writeString(buffer, packet.getMessage());
                    break;
                case TRANSLATION:
                case POPUP:
                case JUKEBOX_POPUP:
                    helper.writeString(buffer, packet.getMessage());
                    helper.writeArray(buffer, packet.getParameters(), helper::writeString);
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported TextType " + type);
            }

            helper.writeString(buffer, packet.getXuid());
            helper.writeString(buffer, packet.getPlatformChatId());

            //V685
            helper.writeString(buffer, packet.getFilteredMessage());
        }

        @Override
        public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, TextPacket packet) {
            //V554
            TextPacket.Type type = TextPacket.Type.values()[buffer.readUnsignedByte()];
            packet.setType(type);
            packet.setNeedsTranslation(buffer.readBoolean());

            switch (type) {
                case CHAT:
                case WHISPER:
                case ANNOUNCEMENT:
                    packet.setSourceName(helper.readString(buffer));
                case RAW:
                case TIP:
                case SYSTEM:
                case JSON:
                case WHISPER_JSON:
                case ANNOUNCEMENT_JSON:
                    packet.setMessage(helper.readString(buffer));
                    break;
                case TRANSLATION:
                case POPUP:
                case JUKEBOX_POPUP:
                    packet.setMessage(helper.readString(buffer));
                    helper.readArray(buffer, packet.getParameters(), helper::readString);
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported TextType " + type);
            }

            packet.setXuid(helper.readString(buffer));
            packet.setPlatformChatId(helper.readString(buffer));

            //V685
            packet.setFilteredMessage(helper.readString(buffer));
        }
    };

    private NeteaseTextSerializer() {
    }
}
