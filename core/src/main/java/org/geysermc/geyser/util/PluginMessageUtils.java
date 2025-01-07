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

package org.geysermc.geyser.util;

import com.github.steveice10.mc.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import com.google.common.base.Charsets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.SneakyThrows;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.session.GeyserSession;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class PluginMessageUtils {
    private static final byte[] GEYSER_BRAND_DATA;

    static {
        byte[] data = GeyserImpl.NAME.getBytes(Charsets.UTF_8);
        GEYSER_BRAND_DATA =
                ByteBuffer.allocate(data.length + getVarIntLength(data.length))
                        .put(writeVarInt(data.length))
                        .put(data)
                        .array();
    }

    /**
     * Get the prebuilt brand as a byte array
     *
     * @return the brand information of the Geyser client
     */
    public static byte[] getGeyserBrandData() {
        return GEYSER_BRAND_DATA;
    }

    public static void sendMessage(GeyserSession session, String channel, byte[] data) {
        session.sendDownstreamPacket(new ServerboundCustomPayloadPacket(channel, data));
    }

    private static byte[] writeVarInt(int value) {
        byte[] data = new byte[getVarIntLength(value)];
        int index = 0;
        do {
            byte temp = (byte) (value & 0b01111111);
            value >>>= 7;
            if (value != 0) {
                temp |= 0b10000000;
            }
            data[index] = temp;
            index++;
        } while (value != 0);
        return data;
    }

    private static int getVarIntLength(int number) {
        if ((number & 0xFFFFFF80) == 0) {
            return 1;
        } else if ((number & 0xFFFFC000) == 0) {
            return 2;
        } else if ((number & 0xFFE00000) == 0) {
            return 3;
        } else if ((number & 0xF0000000) == 0) {
            return 4;
        }
        return 5;
    }

    @SneakyThrows
    public static byte[] syncSkinData(GeyserSession geyserSession){
        Map<String,Object> map = new LinkedHashMap<>(2);
        map.put("pe",true);
        map.put("alex",geyserSession.getClientData().getSkinId().contains("Slim") ?"true":"false");
        map.put("data",GeyserImpl.getInstance().getConfig().getService().getSkinurl()+"/skin/"
                +geyserSession.getAuthData().uuid()+"?"+
                hash(geyserSession.getClientData().getSkinData())+"?pe");
        // 114514 魔法值 无作用
        return (Base64.getEncoder().encodeToString(GeyserImpl.JSON_MAPPER.writeValueAsBytes(map))+ '\0'+"114514").getBytes(StandardCharsets.UTF_8);
    }

    public static String getSkinUrl(String uuid) {
        return GeyserImpl.getInstance().getConfig().getService().getSkinurl() + "/skin/" + uuid + "?pe";
    }

    public static byte[] gZipBytes(byte[] data) {
        byte[] gZipByte = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(bos);
            gzip.write(data);
            gzip.finish();
            gzip.close();
            gZipByte = bos.toByteArray();
            bos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return gZipByte;
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
        } catch (Exception ignored) {
            GeyserImpl.getInstance().getLogger().warning("解包gzip异常，疑似PCskin 压缩问题!");
            ByteBuf byteBuf = Unpooled.wrappedBuffer(data);
            byteBuf.readInt();
            byteBuf.readInt();
            // 获取剩余的 byte[]
            int readableBytes = byteBuf.readableBytes();
            byte[] remainingBytes = new byte[readableBytes];
            byteBuf.readBytes(remainingBytes);
            return decompress(remainingBytes);
        }
        return b;
    }

    public static byte[] decompressB(@Nonnull byte[] compressedData) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(compressedData))) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzis.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
        } catch (IOException e) {
            GeyserImpl.getInstance().getLogger().warning("Error! decompress byte[] failed #1! -> " + e);
        }
        return compressedData;
    }

    public static byte[] decompress(@Nonnull byte[] data) {

        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gzis = new GZIPInputStream(bais)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = gzis.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
        } catch (IOException e) {
            GeyserImpl.getInstance().getLogger().warning("Error! decompress byte[] failed #2! -> " + e);
            return data;
        }
        return baos.toByteArray();
    }

    public static String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            BigInteger number = new BigInteger (1, messageDigest);
            String hashtext = number.toString(16);
            while (hashtext.length()<32) {
                hashtext = "0" + hashtext;
            }
            return hashtext;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
