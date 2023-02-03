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

package org.geysermc.geyser.translator.protocol.bedrock;

import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundCustomPayloadPacket;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.nukkitx.protocol.bedrock.packet.NeteaseCustomPacket;
import com.nukkitx.protocol.bedrock.packet.NeteaseMarketReceivePacket;
import org.geysermc.floodgate.pluginmessage.PluginMessageChannels;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.msgpack.MessagePack;

import java.io.IOException;

@Translator(packet = NeteaseMarketReceivePacket.class)
public class BedrockNeteaseMarketReceiveTranslator extends PacketTranslator<NeteaseMarketReceivePacket> {

    @Override
    public void translate(GeyserSession session, NeteaseMarketReceivePacket packet){
        // Stop the player sending animations before they have fully spawned into the server


        if(!session.isSpawned()){
            return;
        }
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeInt(202);
        out.writeUTF("NeteaseMarketReceivePacket");
        session.sendDownstreamPacket(new ServerboundCustomPayloadPacket(PluginMessageChannels.CUSTOM, out.toByteArray()));
    }

}