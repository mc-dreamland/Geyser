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

package org.geysermc.geyser.translator.level;

import org.geysermc.geyser.session.cache.registry.JavaRegistries;
import org.geysermc.geyser.session.cache.registry.JavaRegistry;
import org.geysermc.geyser.session.cache.registry.RegistryEntryContext;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.BitStorage;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.DataPalette;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.palette.GlobalPalette;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.palette.Palette;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.palette.SingletonPalette;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import org.geysermc.geyser.level.chunk.BlockStorage;
import org.geysermc.geyser.level.chunk.bitarray.BitArray;
import org.geysermc.geyser.level.chunk.bitarray.BitArrayVersion;
import org.geysermc.geyser.level.chunk.bitarray.SingletonBitArray;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;

// Array index formula by https://wiki.vg/Chunk_Format
public class BiomeTranslator {

    public static int loadServerBiome(RegistryEntryContext entry) {
        String javaIdentifier = entry.id().asString();
        return Registries.BIOME_IDENTIFIERS.get().getOrDefault(javaIdentifier, 0);
    }

    public static BlockStorage toNewBedrockBiome(GeyserSession session, DataPalette biomeData) {
        JavaRegistry<Integer> biomeTranslations = session.getRegistryCache().registry(JavaRegistries.BIOME);
        // As of 1.17.10: the client expects the same format as a chunk but filled with biomes
        // As of 1.18 this is the same as Java Edition

        Palette palette = biomeData.getPalette();
        if (palette instanceof SingletonPalette) {
            int biomeId = biomeTranslations.byId(palette.idToState(0));
            return singletonBiome(biomeId);
        } else {
            BlockStorage storage;
            BitStorage bitStorage = biomeData.getStorage();
            if (!(palette instanceof GlobalPalette)) {
                // Prevent resizing by allocating what we can ahead of time
                int size = palette.size();
                IntList bedrockPalette = new IntArrayList(size);
                int[] bedrockPaletteIds = new int[size];

                for (int i = 0; i < size; i++) {
                    int javaId = palette.idToState(i);
                    int biomeId = biomeTranslations.byId(javaId).intValue();
                    bedrockPaletteIds[i] = biomeId;
                    bedrockPalette.add(biomeId);
                }

                int singletonBiomeId = singletonBiomeId(bitStorage, bedrockPaletteIds);
                if (singletonBiomeId != -1) {
                    return singletonBiome(singletonBiomeId);
                }

                BitArray bitArray = BitArrayVersion.forBitsCeil(bitStorage.getBitsPerEntry())
                    .createArray(BlockStorage.SIZE);

                // Each section of biome corresponding to a chunk section contains 4 * 4 * 4 entries
                for (int i = 0; i < 64; i++) {
                    int idx = bitStorage.get(i);
                    int x = i & 3;
                    int y = (i >> 4) & 3;
                    int z = (i >> 2) & 3;
                    // Convert biome coordinates into block coordinates
                    // Bedrock expects a full 4096 blocks
                    multiplyIdToStorage(bitArray, idx, x, y, z);
                }

                storage = new BlockStorage(bitArray, bedrockPalette);
            } else {
                int[] bedrockBiomeIds = new int[64];
                int firstBiomeId = -1;
                boolean singleBiome = true;

                for (int i = 0; i < 64; i++) {
                    int javaId = palette.idToState(bitStorage.get(i));
                    int biomeId = biomeTranslations.byId(javaId);
                    bedrockBiomeIds[i] = biomeId;
                    if (i == 0) {
                        firstBiomeId = biomeId;
                    } else if (biomeId != firstBiomeId) {
                        singleBiome = false;
                    }
                }

                if (singleBiome) {
                    return singletonBiome(firstBiomeId);
                }

                storage = new BlockStorage(0);

                // Each section of biome corresponding to a chunk section contains 4 * 4 * 4 entries
                for (int i = 0; i < 64; i++) {
                    int x = i & 3;
                    int y = (i >> 4) & 3;
                    int z = (i >> 2) & 3;
                    // Get the Bedrock biome ID override
                    int biomeId = bedrockBiomeIds[i];
                    int idx = storage.idFor(biomeId);
                    // Convert biome coordinates into block coordinates
                    // Bedrock expects a full 4096 blocks
                    // Implementation note: storage.getBitArray() must be called and not stored - if the palette
                    // grows, then the instance can change
                    multiplyIdToStorage(storage.getBitArray(), idx, x, y, z);
                }
            }
            return storage;
        }
    }

    public static BlockStorage toNewBedrockBiomeOld(GeyserSession session, DataPalette biomeData) {
        JavaRegistry<Integer> biomeTranslations = session.getRegistryCache().registry(JavaRegistries.BIOME);
        // As of 1.17.10: the client expects the same format as a chunk but filled with biomes
        // As of 1.18 this is the same as Java Edition

        Palette palette = biomeData.getPalette();
        if (palette instanceof SingletonPalette) {
            int biomeId = biomeTranslations.byId(palette.idToState(0));
            return new BlockStorage(SingletonBitArray.INSTANCE, IntLists.singleton(biomeId));
        } else {
            BlockStorage storage;
            BitStorage bitStorage = biomeData.getStorage();
            if (!(palette instanceof GlobalPalette)) {
                // Prevent resizing by allocating what we can ahead of time
                int size = palette.size();
                BitArray bitArray = BitArrayVersion.forBitsCeil(bitStorage.getBitsPerEntry()).createArray(BlockStorage.SIZE);
                IntList bedrockPalette = new IntArrayList(size);

                for (int i = 0; i < size; i++) {
                    int javaId = palette.idToState(i);
                    bedrockPalette.add(biomeTranslations.byId(javaId).intValue());
                }

                // Each section of biome corresponding to a chunk section contains 4 * 4 * 4 entries
                for (int i = 0; i < 64; i++) {
                    int idx = bitStorage.get(i);
                    int x = i & 3;
                    int y = (i >> 4) & 3;
                    int z = (i >> 2) & 3;
                    // Convert biome coordinates into block coordinates
                    // Bedrock expects a full 4096 blocks
                    multiplyIdToStorageOld(bitArray, idx, x, y, z);
                }

                storage = new BlockStorage(bitArray, bedrockPalette);
            } else {
                storage = new BlockStorage(0);

                // Each section of biome corresponding to a chunk section contains 4 * 4 * 4 entries
                for (int i = 0; i < 64; i++) {
                    int javaId = palette.idToState(bitStorage.get(i));
                    int x = i & 3;
                    int y = (i >> 4) & 3;
                    int z = (i >> 2) & 3;
                    // Get the Bedrock biome ID override
                    int biomeId = biomeTranslations.byId(javaId);
                    int idx = storage.idFor(biomeId);
                    // Convert biome coordinates into block coordinates
                    // Bedrock expects a full 4096 blocks
                    // Implementation note: storage.getBitArray() must be called and not stored - if the palette
                    // grows, then the instance can change
                    multiplyIdToStorageOld(storage.getBitArray(), idx, x, y, z);
                }
            }
            return storage;
        }
    }

    private static BlockStorage singletonBiome(int biomeId) {
        return new BlockStorage(SingletonBitArray.INSTANCE, IntLists.singleton(biomeId));
    }

    private static int singletonBiomeId(BitStorage bitStorage, int[] bedrockPaletteIds) {
        int firstBiomeId = bedrockPaletteIds[bitStorage.get(0)];
        for (int i = 1; i < 64; i++) {
            if (bedrockPaletteIds[bitStorage.get(i)] != firstBiomeId) {
                return -1;
            }
        }
        return firstBiomeId;
    }

    private static void multiplyIdToStorage(final BitArray bitArray, final int idx, final int x, final int y, final int z) {
        if (multiplyIdToStorageFast(bitArray, idx, x, y, z)) {
            return;
        }

        for (int blockX = x << 2; blockX < (x << 2) + 4; blockX++) {
            for (int blockZ = z << 2; blockZ < (z << 2) + 4; blockZ++) {
                for (int blockY = y << 2; blockY < (y << 2) + 4; blockY++) {
                    bitArray.set((blockX << 8) | (blockZ << 4) | blockY, idx);
                }
            }
        }
    }

    private static void multiplyIdToStorageOld(final BitArray bitArray, final int idx, final int x, final int y, final int z) {
        for (int blockX = x << 2; blockX < (x << 2) + 4; blockX++) {
            for (int blockZ = z << 2; blockZ < (z << 2) + 4; blockZ++) {
                for (int blockY = y << 2; blockY < (y << 2) + 4; blockY++) {
                    bitArray.set((blockX << 8) | (blockZ << 4) | blockY, idx);
                }
            }
        }
    }

    private static boolean multiplyIdToStorageFast(final BitArray bitArray, final int idx, final int x, final int y, final int z) {
        BitArrayVersion version = bitArray.getVersion();
        int bits = version.getId();
        if (bits != 1 && bits != 2 && bits != 4 && bits != 8 && bits != 16) {
            return false;
        }
        if (idx < 0 || idx > version.getMaxEntryValue()) {
            return false;
        }

        int[] words = bitArray.getWords();
        int blockY = y << 2;
        for (int blockX = x << 2; blockX < (x << 2) + 4; blockX++) {
            int xOffset = blockX << 8;
            for (int blockZ = z << 2; blockZ < (z << 2) + 4; blockZ++) {
                setFourBiomeEntries(words, (xOffset | (blockZ << 4) | blockY), idx, bits);
            }
        }
        return true;
    }

    private static void setFourBiomeEntries(int[] words, int baseIndex, int value, int bits) {
        switch (bits) {
            case 1 -> {
                int wordIndex = baseIndex >>> 5;
                int offset = baseIndex & 31;
                int mask = 0xF << offset;
                words[wordIndex] = (words[wordIndex] & ~mask) | ((value == 0 ? 0 : 0xF) << offset);
            }
            case 2 -> {
                int wordIndex = baseIndex >>> 4;
                int offset = (baseIndex & 15) << 1;
                int packed = value | (value << 2) | (value << 4) | (value << 6);
                int mask = 0xFF << offset;
                words[wordIndex] = (words[wordIndex] & ~mask) | (packed << offset);
            }
            case 4 -> {
                int wordIndex = baseIndex >>> 3;
                int offset = (baseIndex & 7) << 2;
                int packed = value | (value << 4) | (value << 8) | (value << 12);
                int mask = 0xFFFF << offset;
                words[wordIndex] = (words[wordIndex] & ~mask) | (packed << offset);
            }
            case 8 -> {
                int packed = value | (value << 8) | (value << 16) | (value << 24);
                words[baseIndex >>> 2] = packed;
            }
            case 16 -> {
                int packed = value | (value << 16);
                int wordIndex = baseIndex >>> 1;
                words[wordIndex] = packed;
                words[wordIndex + 1] = packed;
            }
            default -> throw new IllegalArgumentException("Unsupported direct biome bit width: " + bits);
        }
    }
}
