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

import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.registry.type.GeyserMappingItem;

public class Conversion819_818 {

    public static GeyserMappingItem remapItem(Item item, GeyserMappingItem mapping) {
        mapping = Conversion827_819.remapItem(item, mapping);

        return switch (item.javaIdentifier()) {
            case "minecraft:copper_chest", "minecraft:exposed_copper_chest", "minecraft:weathered_copper_chest",
                    "minecraft:oxidized_copper_chest", "minecraft:waxed_copper_chest", "minecraft:waxed_exposed_copper_chest",
                    "minecraft:waxed_weathered_copper_chest", "minecraft:waxed_oxidized_copper_chest" -> mapping.withFallbackIdentifier("minecraft:chest");
            case "minecraft:music_disc_lava_chicken" -> mapping.withFallbackIdentifier("minecraft:music_disc_chirp");
            case "minecraft:copper_helmet" -> mapping.withFallbackIdentifier("minecraft:leather_helmet");
            case "minecraft:copper_chestplate" -> mapping.withFallbackIdentifier("minecraft:leather_chestplate");
            case "minecraft:copper_leggings" -> mapping.withFallbackIdentifier("minecraft:leather_leggings");
            case "minecraft:copper_boots" -> mapping.withFallbackIdentifier("minecraft:leather_boots");
            case "minecraft:copper_nugget" -> mapping.withFallbackIdentifier("minecraft:iron_nugget");
            case "minecraft:copper_sword" -> mapping.withFallbackIdentifier("minecraft:stone_sword");
            case "minecraft:copper_pickaxe" -> mapping.withFallbackIdentifier("minecraft:stone_pickaxe");
            case "minecraft:copper_shovel" -> mapping.withFallbackIdentifier("minecraft:stone_shovel");
            case "minecraft:copper_axe" -> mapping.withFallbackIdentifier("minecraft:stone_axe");
            case "minecraft:copper_hoe" -> mapping.withFallbackIdentifier("minecraft:stone_hoe");
            case "minecraft:copper_golem_spawn_egg" -> mapping.withFallbackIdentifier("minecraft:iron_golem_spawn_egg");
            default -> mapping;
        };
    }
}
