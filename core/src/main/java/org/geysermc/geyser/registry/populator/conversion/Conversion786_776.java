/*
 * Copyright (c) 2025 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.registry.populator.conversion;

import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.registry.type.GeyserMappingItem;

import static org.geysermc.geyser.registry.populator.conversion.ConversionHelper.withName;
import static org.geysermc.geyser.registry.populator.conversion.ConversionHelper.withoutStates;

public class Conversion786_776 {

    public static NbtMap remapBlock(NbtMap nbtMap) {
        nbtMap = Conversion800_786.remapBlock(nbtMap);

        final String name = nbtMap.getString("name");
        return switch (name) {
            case "minecraft:bush" -> withName(nbtMap, "fern");
            case "minecraft:firefly_bush" -> withName(nbtMap, "deadbush");
            case "minecraft:tall_dry_grass", "minecraft:short_dry_grass" -> withName(nbtMap, "short_grass");
            case "minecraft:cactus_flower" -> withName(nbtMap, "unknown");
            case "minecraft:leaf_litter", "minecraft:wildflowers" -> withoutStates("unknown");
            default -> nbtMap;
        };
    }

    public static GeyserMappingItem remapItem(Item item, GeyserMappingItem mapping) {
        mapping = Conversion800_786.remapItem(item, mapping);

        return switch (item.javaIdentifier()) {
            case "minecraft:bush" -> mapping.withFallbackIdentifier("minecraft:short_grass");
            case "minecraft:cactus_flower" -> mapping.withFallbackIdentifier("minecraft:bubble_coral_fan");
            case "minecraft:firefly_bush" -> mapping.withFallbackIdentifier("minecraft:short_grass");
            case "minecraft:leaf_litter", "minecraft:wildflowers" -> mapping.withFallbackIdentifier("minecraft:pink_petals");
            case "minecraft:short_dry_grass" -> mapping.withFallbackIdentifier("minecraft:dead_bush");
            case "minecraft:tall_dry_grass" -> mapping.withFallbackIdentifier("minecraft:tall_grass");
            case "minecraft:test_block" -> mapping.withFallbackIdentifier("minecraft:structure_block");
            case "minecraft:test_instance_block" -> mapping.withFallbackIdentifier("minecraft:jigsaw");
            case "minecraft:blue_egg", "minecraft:brown_egg" -> mapping.withFallbackIdentifier("minecraft:egg");
            default -> mapping;
        };
    }
}
