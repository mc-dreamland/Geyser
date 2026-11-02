/*
 * Copyright (c) 2019-2026 GeyserMC. http://geysermc.org
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
 */

package org.geysermc.geyser.network.netease.serializers;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v291.serializer.ResourcePackClientResponseSerializer_v291;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket.Status;

import static org.cloudburstmc.protocol.common.util.Preconditions.checkArgument;

public final class NeteaseResourcePackClientResponseSerializer extends ResourcePackClientResponseSerializer_v291 {
    public static final NeteaseResourcePackClientResponseSerializer INSTANCE = new NeteaseResourcePackClientResponseSerializer();

    private static final int DOWNLOAD_FAILED = 5;
    private static final Status[] STATUSES = Status.values();

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, ResourcePackClientResponsePacket packet) {
        int status = buffer.readUnsignedByte();
        if (status == DOWNLOAD_FAILED) {
            // Cloudburst has no DownloadFailed status. Use the existing resource-pack failure
            // disconnect path in UpstreamPacketHandler instead of advancing the login flow.
            packet.setStatus(Status.REFUSED);
        } else {
            checkArgument(status < STATUSES.length, "Unknown resource pack response status: %s", status);
            packet.setStatus(STATUSES[status]);
        }
        helper.readArray(buffer, packet.getPackIds(), ByteBuf::readUnsignedShortLE, helper::readString);
    }

    private NeteaseResourcePackClientResponseSerializer() {
    }
}
