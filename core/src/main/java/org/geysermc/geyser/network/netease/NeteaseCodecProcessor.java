/*
 * Copyright (c) 2019-2024 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.network.netease;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v361.serializer.ClientCacheStatusSerializer_v361;
import org.cloudburstmc.protocol.bedrock.data.PacketRecipient;
import org.cloudburstmc.protocol.bedrock.packet.BiomeDefinitionListPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheBlobStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;

/**
 * Registers NetEase-specific Bedrock packet serializers.
 */
public final class NeteaseCodecProcessor {

    private static final int NETEASE_JSON_PACKET_ID = 203;
    private static final int SET_DIMENSION_LOCAL_TIME_PACKET_ID = 208;

    public static void processCodec(BedrockCodec.Builder codecBuilder, int protocolVersion) {

        codecBuilder.registerPacket(NeteaseJsonPacket::new, NeteaseJsonSerializer.INSTANCE,
            NETEASE_JSON_PACKET_ID, PacketRecipient.BOTH);
        codecBuilder.registerPacket(SetDimensionLocalTimePacket::new, SetDimensionLocalTimeSerializer.INSTANCE,
                SET_DIMENSION_LOCAL_TIME_PACKET_ID, PacketRecipient.CLIENT);

        // The base Geyser codec rejects/ignores these packets because upstream does not implement
        // the client blob cache. NetEase clients use the vanilla packet layout, so restore the
        // original serializers here alongside the other NetEase-specific codec overrides.
        codecBuilder.updateSerializer(ClientCacheBlobStatusPacket.class, NeteaseClientCacheBlobStatusSerializer.INSTANCE);
        codecBuilder.updateSerializer(ClientCacheStatusPacket.class, ClientCacheStatusSerializer_v361.INSTANCE);

        if (protocolVersion >= 819) {
            codecBuilder.updateSerializer(PlayerAuthInputPacket.class, NeteasePlayerAuthInputSerializer.V819_860);
            codecBuilder.updateSerializer(TextPacket.class, NeteaseTextSerializer.INSTANCE);
        }

        if (protocolVersion == 860) {
            codecBuilder.updateSerializer(BiomeDefinitionListPacket.class, NeteaseBiomeDefinitionListSerializer.V860);
            codecBuilder.updateSerializer(ContainerOpenPacket.class, NeteaseContainerOpenSerializer.V860);
        }
        //TODO 后续尽量将所有网易特性的包都由当前类进行注册
    }

    private NeteaseCodecProcessor() {
    }
}
