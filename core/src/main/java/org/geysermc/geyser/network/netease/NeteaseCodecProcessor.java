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
import org.cloudburstmc.protocol.bedrock.packet.BiomeDefinitionListPacket;
import org.cloudburstmc.protocol.bedrock.packet.ConfirmSkinPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;

/**
 * Registers NetEase-specific Bedrock packet serializers.
 */
public final class NeteaseCodecProcessor {

    public static void processCodec(BedrockCodec.Builder codecBuilder, int protocolVersion) {

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
