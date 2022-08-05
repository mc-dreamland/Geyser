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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.nimbusds.jose.shaded.json.JSONObject;
import com.nukkitx.protocol.bedrock.BedrockPacketHelper;
import com.nukkitx.protocol.bedrock.BedrockPacketSerializer;
import com.nukkitx.protocol.bedrock.packet.NeteaseCustomPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.msgpack.MessagePack;
import org.msgpack.type.ArrayValue;
import org.msgpack.type.Value;

import java.lang.reflect.Type;
import java.util.HashMap;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NeteaseCustomSerializer_v475 implements BedrockPacketSerializer<NeteaseCustomPacket>{
    public static final NeteaseCustomSerializer_v475 INSTANCE = new NeteaseCustomSerializer_v475();
    private static final Gson gson = new Gson();

    @SneakyThrows
    @Override
    public void serialize(ByteBuf buffer, BedrockPacketHelper helper, NeteaseCustomPacket packet) {
        MessagePack messagePack = new MessagePack();
        Object[] objects = new Object[]{
                "ModEventS2C",
                new Object[]{
                        packet.getModName(),
                        packet.getSystem(),
                        packet.getEventName(),
                        new JSONObject(packet.getData())
                },
                null
        };
        JsonElement v = JsonParser.parseString(gson.toJsonTree(objects).toString());
        byte[] write = messagePack.write(v);
        helper.writeByteArray(buffer, write);
        buffer.writeBytes(Unpooled.wrappedBuffer(new byte[]{8, -44, -108, 0}));
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockPacketHelper helper, NeteaseCustomPacket packet) {
        try {
            MessagePack messagePack = new MessagePack();
            byte[] bytes = helper.readByteArray(buffer);
            Value originJson = messagePack.read(bytes);
            Value unConvert = messagePack.unconvert("value");
            ArrayValue values = originJson.asMapValue().get(unConvert).asArrayValue();
            if (!values.get(0).toString().equals("\"ModEventC2S\"") && !values.get(0).toString().equals("\"ModEventS2C\"") ) return;
            ArrayValue packData = values.get(1).asMapValue().get(unConvert).asArrayValue();

            packet.setModName(packData.get(0).toString().replace("\"", ""));
            packet.setSystem(packData.get(1).toString().replace("\"", ""));
            packet.setEventName(packData.get(2).toString().replace("\"", ""));
            if (packData.get(3).isMapValue()) {
                packet.setData(gson.fromJson(packData.get(3).toString(), (Type) HashMap.class));
            }
            packet.setJson(originJson);
            packet.setUnKnowId(buffer.readUnsignedInt());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


}
