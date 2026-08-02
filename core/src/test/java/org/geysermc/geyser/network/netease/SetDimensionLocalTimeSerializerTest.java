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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketDefinition;
import org.cloudburstmc.protocol.bedrock.data.PacketRecipient;
import org.cloudburstmc.protocol.common.util.VarInts;
import org.geysermc.geyser.network.GameProtocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetDimensionLocalTimeSerializerTest {

    @Test
    void isRegisteredAsClientboundPacket208() {
        for (int protocolVersion : GameProtocol.SUPPORTED_BEDROCK_PROTOCOLS) {
            BedrockCodec codec = GameProtocol.getBedrockCodec(protocolVersion);
            assertNotNull(codec);

            BedrockPacketDefinition<SetDimensionLocalTimePacket> definition =
                    codec.getPacketDefinition(SetDimensionLocalTimePacket.class);
            assertNotNull(definition);
            assertEquals(208, definition.getId());
            assertEquals(PacketRecipient.CLIENT, definition.getRecipient());
            assertSame(definition, codec.getPacketDefinition(208));
        }
    }

    @Test
    void writesNoPayloadWhenLocalTimeIsDisabled() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            SetDimensionLocalTimePacket packet = new SetDimensionLocalTimePacket(false, 6000, false);
            SetDimensionLocalTimeSerializer.INSTANCE.serialize(buffer, null, packet);

            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void writesTimeAndTickFlagWhenLocalTimeIsEnabled() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            SetDimensionLocalTimePacket packet = new SetDimensionLocalTimePacket(true, 6000, false);
            SetDimensionLocalTimeSerializer.INSTANCE.serialize(buffer, null, packet);

            assertEquals(6000, VarInts.readInt(buffer));
            assertFalse(buffer.readBoolean());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void readsEnabledAndDisabledPayloads() {
        SetDimensionLocalTimePacket disabled = new SetDimensionLocalTimePacket(true, 6000, false);
        SetDimensionLocalTimeSerializer.INSTANCE.deserialize(Unpooled.EMPTY_BUFFER, null, disabled);
        assertFalse(disabled.isLocalTimeEnabled());
        assertEquals(0, disabled.getTime());
        assertTrue(disabled.isTickDayTime());

        ByteBuf buffer = Unpooled.buffer();
        try {
            VarInts.writeInt(buffer, 18000);
            buffer.writeBoolean(true);

            SetDimensionLocalTimePacket enabled = new SetDimensionLocalTimePacket();
            SetDimensionLocalTimeSerializer.INSTANCE.deserialize(buffer, null, enabled);

            assertTrue(enabled.isLocalTimeEnabled());
            assertEquals(18000, enabled.getTime());
            assertTrue(enabled.isTickDayTime());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
