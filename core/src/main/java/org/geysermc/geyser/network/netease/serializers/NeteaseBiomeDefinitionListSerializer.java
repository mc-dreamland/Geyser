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
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.network.netease.serializers;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketSerializer;
import org.cloudburstmc.protocol.bedrock.codec.v859.serializer.BiomeDefinitionListSerializer_v859;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitionChunkGenData;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitionData;
import org.cloudburstmc.protocol.bedrock.packet.BiomeDefinitionListPacket;
import org.cloudburstmc.protocol.common.util.Preconditions;
import org.cloudburstmc.protocol.common.util.SequencedHashSet;
import org.cloudburstmc.protocol.common.util.VarInts;
import org.cloudburstmc.protocol.common.util.index.Indexed;
import org.cloudburstmc.protocol.common.util.index.IndexedList;

import java.awt.Color;
import java.util.List;

public final class NeteaseBiomeDefinitionListSerializer {

    public static final BedrockPacketSerializer<BiomeDefinitionListPacket> V860 = new BiomeDefinitionListSerializer_v859() {
        @Override
        protected void writeDefinition(ByteBuf buffer, BedrockCodecHelper helper, BiomeDefinitionData definition, SequencedHashSet<String> strings) {
            this.writeDefinitionId(buffer, helper, definition, strings);
            buffer.writeFloatLE(definition.getTemperature());
            buffer.writeFloatLE(definition.getDownfall());
            buffer.writeFloatLE(definition.getFoliageSnow());
            buffer.writeFloatLE(definition.getDepth());
            buffer.writeFloatLE(definition.getScale());
            buffer.writeIntLE(definition.getMapWaterColor().getRGB());
            buffer.writeBoolean(definition.isRain());
            buffer.writeIntLE(definition.getDimension());
            helper.writeString(buffer, definition.getVanilla() == null ? "" : definition.getVanilla());
            helper.writeOptionalNull(buffer, definition.getTags(), (byteBuf, aHelper, tags) -> {
                VarInts.writeUnsignedInt(byteBuf, tags.size());
                for (String tag : tags) {
                    byteBuf.writeShortLE(strings.addAndGetIndex(tag));
                }
            });
            helper.writeOptionalNull(buffer, definition.getChunkGenData(),
                (buf, aHelper, data) -> writeDefinitionChunkGen(buf, aHelper, data, strings));
        }

        @Override
        protected BiomeDefinitionData readDefinition(ByteBuf buffer, BedrockCodecHelper helper, List<String> strings) {
            Indexed<String> id = this.readDefinitionId(buffer, helper, strings);
            float temperature = buffer.readFloatLE();
            float downfall = buffer.readFloatLE();
            float foliageSnow = buffer.readFloatLE();
            float depth = buffer.readFloatLE();
            float scale = buffer.readFloatLE();
            Color mapWaterColor = new Color(buffer.readIntLE(), true);
            boolean rain = buffer.readBoolean();
            int dimension = buffer.readIntLE();
            String vanilla = helper.readString(buffer);
            if (vanilla.isEmpty()) {
                vanilla = null;
            }

            IndexedList<String> tags = helper.readOptional(buffer, null, byteBuf -> {
                int length = VarInts.readUnsignedInt(byteBuf);
                Preconditions.checkArgument(byteBuf.isReadable(length * 2), "Not enough readable bytes for tags");
                int[] array = new int[length];
                for (int i = 0; i < length; i++) {
                    array[i] = byteBuf.readUnsignedShortLE();
                }
                return new IndexedList<>(strings, array);
            });

            BiomeDefinitionChunkGenData chunkGenData = helper.readOptional(buffer, null,
                (buf, aHelper) -> this.readDefinitionChunkGen(buf, aHelper, strings));

            return new BiomeDefinitionData(id, temperature, downfall, foliageSnow, depth, scale, mapWaterColor,
                rain, dimension, vanilla, tags, chunkGenData);
        }
    };

    private NeteaseBiomeDefinitionListSerializer() {
    }
}
