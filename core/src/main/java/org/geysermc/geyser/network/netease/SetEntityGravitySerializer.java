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

import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketSerializer;

final class SetEntityGravitySerializer implements BedrockPacketSerializer<SetEntityGravityPacket> {

    static final SetEntityGravitySerializer INSTANCE = new SetEntityGravitySerializer();

    private static final String EVENT_NAME = "SET_ENTITY_GRAVITY";

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, SetEntityGravityPacket packet) {
        JsonObject payload = new JsonObject();
        payload.addProperty("entityId", packet.getEntityId());
        payload.addProperty("eventName", EVENT_NAME);
        payload.addProperty("gravity", packet.getGravity());
        helper.writeString(buffer, payload.toString());
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, SetEntityGravityPacket packet) {
        // 0xCB is a bidirectional NetEase event channel. Geyser does not currently consume its
        // serverbound events, and decoding them as SET_ENTITY_GRAVITY would reject unrelated
        // client events. Consume the opaque payload so it can be safely ignored by the handler.
        buffer.skipBytes(buffer.readableBytes());
    }

    private SetEntityGravitySerializer() {
    }
}
