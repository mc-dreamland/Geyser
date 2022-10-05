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

import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundCustomPayloadPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundCustomPayloadPacket;
import com.google.common.base.Charsets;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.nukkitx.protocol.bedrock.packet.NeteaseCustomPacket;
import com.nukkitx.protocol.bedrock.packet.NeteaseMarketOpenPacket;
import com.nukkitx.protocol.bedrock.packet.TransferPacket;
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.util.FormType;
import org.geysermc.floodgate.pluginmessage.PluginMessageChannels;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.session.auth.AuthType;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.cumulus.Forms;
import org.msgpack.MessagePack;
import org.msgpack.type.ArrayValue;
import org.msgpack.type.Value;
import org.msgpack.type.ValueType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.zip.GZIPInputStream;

@Translator(packet = ClientboundCustomPayloadPacket.class)
public class JavaCustomPayloadTranslator extends PacketTranslator<ClientboundCustomPayloadPacket> {
    private final GeyserLogger logger = GeyserImpl.getInstance().getLogger();
    private static final Gson gson = new Gson();

    @Override
    public void translate(GeyserSession session, ClientboundCustomPayloadPacket packet) {
        // The only plugin messages it has to listen for are Floodgate plugin messages
        if (session.getRemoteAuthType() != AuthType.FLOODGATE) {
            return;
        }

        String channel = packet.getChannel();

        if (channel.equals(PluginMessageChannels.FORM)) {
            byte[] data = packet.getData();

            // receive: first byte is form type, second and third are the id, remaining is the form data
            // respond: first and second byte id, remaining is form response data

            FormType type = FormType.fromOrdinal(data[0]);
            if (type == null) {
                throw new NullPointerException(
                        "Got type " + data[0] + " which isn't a valid form type!");
            }

            String dataString = new String(data, 3, data.length - 3, Charsets.UTF_8);

            Form form = Forms.fromJson(dataString, type, (ignored, response) -> {
                byte[] raw = response.getBytes(StandardCharsets.UTF_8);
                byte[] finalData = new byte[raw.length + 2];

                finalData[0] = data[1];
                finalData[1] = data[2];
                System.arraycopy(raw, 0, finalData, 2, raw.length);

                session.sendDownstreamPacket(new ServerboundCustomPayloadPacket(channel, finalData));
            });
            session.sendForm(form);

        } else if (channel.equals(PluginMessageChannels.TRANSFER)) {
            byte[] data = packet.getData();

            // port, 4 bytes. remaining data, address.

            if (data.length < 5) {
                throw new NullPointerException("Transfer data should be at least 5 bytes long");
            }

            int port = data[0] << 24 | (data[1] & 0xFF) << 16 | (data[2] & 0xFF) << 8 | data[3] & 0xFF;
            String address = new String(data, 4, data.length - 4);

            if (logger.isDebug()) {
                logger.info("Transferring client to: " + address + ":" + port);
            }

            TransferPacket transferPacket = new TransferPacket();
            transferPacket.setAddress(address);
            transferPacket.setPort(port);
            session.sendUpstreamPacket(transferPacket);
        } else if (channel.equals(PluginMessageChannels.NeteaseCustom)) {
            byte[] data = packet.getData();
            NeteaseCustomPacket neteaseCustomPacket = new NeteaseCustomPacket();
            MessagePack messagePack = new MessagePack();

            byte[] msgPackData = unGZipBytes(data);
            neteaseCustomPacket.setMsgPackBytes(msgPackData);
            try {
                Value originJson = messagePack.read(msgPackData);
                Value unConvert = messagePack.unconvert("value");
                if (originJson.getType().equals(ValueType.MAP)) {
                    ArrayValue values = originJson.asMapValue().get(unConvert).asArrayValue();
                    if (!values.get(0).toString().contains("ModEventC2S") && !values.get(0).toString().contains("ModEventS2C"))
                        return;
                    ArrayValue packData = values.get(1).asMapValue().get(unConvert).asArrayValue();
                    neteaseCustomPacket.setModName(packData.get(0).toString().replace("\"", ""));
                    neteaseCustomPacket.setSystem(packData.get(1).toString().replace("\"", ""));
                    neteaseCustomPacket.setEventName(packData.get(2).toString().replace("\"", ""));
                    if (packData.get(3).isMapValue()) {
                        neteaseCustomPacket.setData(gson.fromJson(packData.get(3).toString(), (Type) HashMap.class));
                        neteaseCustomPacket.setJson(originJson);
                    }
                } else if (originJson.getType().equals(ValueType.ARRAY)) {
                    ArrayValue values = originJson.asArrayValue();
                    if (!values.get(0).toString().contains("ModEventC2S") && !values.get(0).toString().contains("ModEventS2C"))
                        return;
                    ArrayValue packData = values.get(1).asArrayValue();
                    neteaseCustomPacket.setModName(packData.get(0).toString().replace("\"", ""));
                    neteaseCustomPacket.setSystem(packData.get(1).toString().replace("\"", ""));
                    neteaseCustomPacket.setEventName(packData.get(2).toString().replace("\"", ""));
                    if (packData.get(3).isMapValue()) {
                        neteaseCustomPacket.setData(gson.fromJson(packData.get(3).toString(), (Type) HashMap.class));
                        neteaseCustomPacket.setJson(originJson);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            session.sendUpstreamPacket(neteaseCustomPacket);

        } else if (channel.equals("floodgate:packet")) {
            byte[] data = packet.getData();
            int packetId = 0;
            String check = null;
            ByteArrayDataInput packetBytes = ByteStreams.newDataInput(data);
            try {
                packetBytes.readByte();
                packetId = packetBytes.readInt();
                check = packetBytes.readUTF();
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (packetId != 0 && check != null && check.equals("NeteaseMarketOpen")) {
                if (packetId == 203) {
                    NeteaseMarketOpenPacket neteaseMarketPacket = new NeteaseMarketOpenPacket();
                    neteaseMarketPacket.setCategory(packetBytes.readUTF());
                    neteaseMarketPacket.setEventName(packetBytes.readUTF());
                    session.sendUpstreamPacket(neteaseMarketPacket);
                }
            } else if (packetId != 0 && check != null && check.equals("GeyserSetQuickChange")) {
                if (packetId == 1000) {
                    session.setQuickSwitch(packetBytes.readBoolean());
                }
            }
        }
    }


    public static byte[] unGZipBytes(byte[] data) {
        byte[] b = null;
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            GZIPInputStream gzip = new GZIPInputStream(bis);
            byte[] buf = new byte[1024];
            int num = -1;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while ((num = gzip.read(buf, 0, buf.length)) != -1) {
                baos.write(buf, 0, num);
            }
            b = baos.toByteArray();
            baos.flush();
            baos.close();
            gzip.close();
            bis.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return b;
    }
}
