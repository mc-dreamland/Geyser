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

package org.geysermc.geyser.translator.protocol.java;

import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import com.nukkitx.protocol.bedrock.packet.TextPacket;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.translator.text.MessageTranslator;

@Translator(packet = ClientboundSystemChatPacket.class)
public class JavaSystemChatTranslator extends PacketTranslator<ClientboundSystemChatPacket> {

    @Override
    public void translate(GeyserSession session, ClientboundSystemChatPacket packet) {
        TextPacket textPacket = new TextPacket();
        textPacket.setPlatformChatId("");
        textPacket.setSourceName("");
        textPacket.setXuid(session.getAuthData().xuid());
        textPacket.setType(packet.isOverlay() ? TextPacket.Type.TIP : TextPacket.Type.SYSTEM);

        textPacket.setNeedsTranslation(false);
        String message = MessageTranslator.convertMessage(packet.getContent(), session.locale());
        int length = message.length();

        if (length >= 512) {
            int i = length / (512);
            if ( (i * (512)) < length) {
                i++;
            }
            for (int i1 = 0; i1 < i; i1++) {
                TextPacket textPacket2 = new TextPacket();
                textPacket2.setPlatformChatId("");
                textPacket2.setSourceName("");
                textPacket2.setXuid(session.getAuthData().xuid());
                textPacket2.setType(packet.isOverlay() ? TextPacket.Type.TIP : TextPacket.Type.SYSTEM);

                textPacket2.setNeedsTranslation(false);
                String msg;
                if (512 * i1 <= length) {
                    int l2 = 512 * (i1 + 1);
                    if (l2 > length) {
                        l2 = length;
                    }
                    msg = (message.substring(512 * i1, l2));
                    textPacket2.setMessage(msg);
                    if (session.isSentSpawnPacket()) {
                        session.sendUpstreamPacket(textPacket2);
                    } else {
                        session.getUpstream().queuePostStartGamePacket(textPacket2);
                    }
                }
            }
        } else {
            textPacket.setMessage(message);
            if (session.isSentSpawnPacket()) {
                session.sendUpstreamPacket(textPacket);
            } else {
                session.getUpstream().queuePostStartGamePacket(textPacket);
            }
        }
    }
}
