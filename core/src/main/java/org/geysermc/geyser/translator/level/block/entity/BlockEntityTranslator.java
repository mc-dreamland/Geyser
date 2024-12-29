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

package org.geysermc.geyser.translator.level.block.entity;

import com.github.steveice10.mc.protocol.data.game.level.block.BlockEntityType;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.BlockEntityUtils;

import java.util.Random;

/**
 * The class that all block entities (on both Java and Bedrock) should translate with
 */
public abstract class BlockEntityTranslator {
    protected BlockEntityTranslator() {
    }

    public abstract void translateTag(NbtMapBuilder builder, CompoundTag tag, int blockState);

    public NbtMap getBlockEntityTag(GeyserSession session, BlockEntityType type, int x, int y, int z, CompoundTag tag, int blockState) {
        NbtMapBuilder tagBuilder = getConstantBedrockTag(BlockEntityUtils.getBedrockBlockEntityId(type), x, y, z);
        translateTag(tagBuilder, tag, blockState);
        return tagBuilder.build();
    }

    public static NbtMap getCustomSkullBlockEntityTag(BlockEntityType type, int x, int y, int z, CompoundTag tag, int blockState, String blockName) {
        NbtMapBuilder nbtMapBuilder = NbtMap.builder();
        if (!blockName.startsWith("heypixel") && !blockName.contains(":")) {
            blockName = "heypixel:" + blockName;
        }
        nbtMapBuilder.putString("_blockName", blockName);
//        nbtMapBuilder.putBoolean("_movable", false);
//        nbtMapBuilder.putBoolean("_tick", false);
        Random random = new Random();
        long l = random.nextLong(); //TODO 后续自增长？需缓存起来。用于一些操作。
        nbtMapBuilder.putLong("_uniqueId", l * -1);
        NbtMap nbtMapBuilder1 = NbtMap.builder().putByte("1", (byte) 1).putByte("2", (byte) 2).putString("3", "123").build();
        nbtMapBuilder.putCompound("exData", NbtMap.builder().putCompound("abc", nbtMapBuilder1).build());

        nbtMapBuilder.putString("id", "ModBlock");
        nbtMapBuilder.putBoolean("isMovable", false);
        nbtMapBuilder.putInt("x", x);
        nbtMapBuilder.putInt("y", y);
        nbtMapBuilder.putInt("z", z);

        return nbtMapBuilder.build();

    }


    protected NbtMapBuilder getConstantBedrockTag(String bedrockId, int x, int y, int z) {
        return NbtMap.builder()
                .putInt("x", x)
                .putInt("y", y)
                .putInt("z", z)
                .putString("id", bedrockId);
    }

    @SuppressWarnings("unchecked")
    protected <T> T getOrDefault(Tag tag, T defaultValue) {
        return (tag != null && tag.getValue() != null) ? (T) tag.getValue() : defaultValue;
    }
}
