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
import com.nukkitx.protocol.bedrock.BedrockPacketHelper;
import com.nukkitx.protocol.bedrock.BedrockPacketSerializer;
import com.nukkitx.protocol.bedrock.packet.NeteaseMarketReceivePacket;
import io.netty.buffer.ByteBuf;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import java.util.HashMap;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NeteaseMarketReceiveSerializer_v475 implements BedrockPacketSerializer<NeteaseMarketReceivePacket> {
    public static final NeteaseMarketReceiveSerializer_v475 INSTANCE = new NeteaseMarketReceiveSerializer_v475();
    private static final Gson gson = new Gson();

    @SneakyThrows
    @Override
    public void serialize(ByteBuf buffer, BedrockPacketHelper helper, NeteaseMarketReceivePacket packet) {

    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockPacketHelper helper, NeteaseMarketReceivePacket packet) {
        //TODO 还有1byte的数据未读
    }


}