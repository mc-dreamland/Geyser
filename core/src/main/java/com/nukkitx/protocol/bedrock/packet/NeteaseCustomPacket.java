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
import org.msgpack.type.Value;

import java.util.HashMap;

@Data
@EqualsAndHashCode(doNotUseGetters = true, callSuper = false)
public class NeteaseCustomPacket extends BedrockPacket {
    private String modName;
    private String system;
    private String eventName;
    private Value json;
    private long unKnowId;
    private byte[] msgPackBytes;
    private HashMap<String, Object> msgPackMap;

    public NeteaseCustomPacket(){
    }

    @SneakyThrows
    public NeteaseCustomPacket(String modName, String system, String eventName, HashMap<String, Object> msgPackMap) {

        this.modName = modName;
        this.system = system;
        this.eventName = eventName;
        this.msgPackMap = msgPackMap;

        MessagePack messagePack = new MessagePack();
        this.msgPackBytes = messagePack.write(initJsonObject());
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
        return (new JSONParser().parse(gson.toJsonTree(new Object[]{"ModEventS2C", new Object[]{modName, system, eventName, new JSONObject(msgPackMap)}, null}).toString()));
    }
}
