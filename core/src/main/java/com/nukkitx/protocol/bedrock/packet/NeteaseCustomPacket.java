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

package com.nukkitx.protocol.bedrock.packet;

import com.google.gson.Gson;
import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.BedrockPacketType;
import com.nukkitx.protocol.bedrock.handler.BedrockPacketHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.msgpack.MessagePack;
import org.msgpack.type.ArrayValue;
import org.msgpack.type.Value;
import org.msgpack.type.ValueType;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(doNotUseGetters = true, callSuper = false)
public class NeteaseCustomPacket extends BedrockPacket {
    private String eventType;
    private String modName;
    private String system;
    private String eventName;
    private Value json;
    private long unKnowId;
    private byte[] msgPackBytes;
    private Map<String, Object> msgPackMap;

    public NeteaseCustomPacket(){
    }

    @SneakyThrows
    public NeteaseCustomPacket(String modName, String system, String eventName, Map<String, Object> msgPackMap) {

        this.modName = modName;
        this.system = system;
        this.eventName = eventName;
        this.msgPackMap = msgPackMap;

        MessagePack messagePack = new MessagePack();
        this.msgPackBytes = messagePack.write(initJsonObject());
    }

    @SneakyThrows
    public NeteaseCustomPacket(byte[] msgPackData) {
        this.init(msgPackData);
    }

    @SneakyThrows
    public void init(byte[] msgPackData) {

        MessagePack messagePack = new MessagePack();
        this.setMsgPackBytes(msgPackData);

        Value originJson = messagePack.read(msgPackData);
        Value unConvert = messagePack.unconvert("value");
        ArrayValue packValue;
        if (originJson.getType().equals(ValueType.MAP)) {
            packValue = originJson.asMapValue().get(unConvert).asArrayValue();
        } else {
            packValue = originJson.asArrayValue();
        }

        String eventType = packValue.get(0).toString().replace("\"", "");
        this.setEventType(eventType);

        ArrayValue packData;
        if (packValue.get(1).getType().equals(ValueType.MAP)) {
            packData = packValue.get(1).asMapValue().get(unConvert).asArrayValue();
        } else {
            packData = packValue.get(1).asArrayValue();
        }

        Gson gson = new Gson();
        ArrayList<Object> t = gson.fromJson(packData.toString(), (Type) ArrayList.class);

        if (t.size() > 0) {
            this.setModName(String.valueOf(t.get(0)));
            this.setSystem(String.valueOf(t.size() > 1 ? t.get(1) : null));
            this.setEventName(String.valueOf(t.size() > 2 ? t.get(2) : null));
            if (t.size() > 3) {
                if (t.get(3) instanceof Map) {
                    this.setMsgPackMap((Map<String, Object>) t.get(3));
                } else {
                    Map<String, Object> map = new HashMap<>();
                    map.put("data", t.get(3));
                    this.setMsgPackMap(map);
                }
            } else {
                this.setMsgPackMap(null);
            }
        } else {
            this.setModName(null);
            this.setSystem(null);
            this.setEventName(null);
            this.setMsgPackMap(null);
        }

        this.setJson(originJson);
    }

    @Override
    public boolean handle(BedrockPacketHandler handler) {
        return handler.handle(this);
    }

    @Override
    public BedrockPacketType getPacketType() {
        return BedrockPacketType.NETEASE_CUSTOM;
    }

    @SneakyThrows
    private Object initJsonObject() {
        Gson gson = new Gson();
        return (new JSONParser().parse(gson.toJsonTree(new Object[]{eventType, new Object[]{modName, system, eventName, new JSONObject(msgPackMap)}, null}).toString()));
    }
}
