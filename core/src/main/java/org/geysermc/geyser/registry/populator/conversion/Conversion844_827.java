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
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.registry.type.GeyserMappingItem;

public class Conversion844_827 {

    public static NbtMap remapBlock(NbtMap nbtMap) {
        final String name = nbtMap.getString("name");
        if (name.equals("minecraft:iron_chain") || name.endsWith("copper_chain")) {
            return ConversionHelper.withName(nbtMap, "chain");
        } else if (name.endsWith("lightning_rod")) {
            NbtMapBuilder statesWithoutPoweredBit = nbtMap.getCompound("states").toBuilder();
            statesWithoutPoweredBit.remove("powered_bit");
            return nbtMap.toBuilder()
                .putString("name", "minecraft:lightning_rod")
                .putCompound("states", statesWithoutPoweredBit.build())
                .build();
        } else if (name.endsWith("_shelf") || name.endsWith("copper_golem_statue")) {
            return ConversionHelper.withoutStates("unknown");
        } else if (name.equals("minecraft:copper_torch")) {
            return ConversionHelper.withName(nbtMap, "torch");
        } else if (name.endsWith("copper_bars")) {
            return ConversionHelper.withName(nbtMap, "iron_bars");
        } else if (name.endsWith("copper_lantern")) {
            return ConversionHelper.withName(nbtMap, "lantern");
        }

        return nbtMap;
    }

    public static GeyserMappingItem remapItem(Item item, GeyserMappingItem mapping) {
        if (item == Items.IRON_CHAIN || item.javaIdentifier().endsWith("copper_chain")) {
            return mapping.withBedrockIdentifier("minecraft:chain");
        }

        return switch (item.javaIdentifier()) {
            case "minecraft:acacia_shelf", "minecraft:bamboo_shelf", "minecraft:birch_shelf", "minecraft:cherry_shelf",
                    "minecraft:crimson_shelf", "minecraft:dark_oak_shelf", "minecraft:jungle_shelf", "minecraft:mangrove_shelf",
                    "minecraft:oak_shelf", "minecraft:pale_oak_shelf", "minecraft:spruce_shelf", "minecraft:warped_shelf" -> mapping.withFallbackIdentifier("minecraft:chiseled_bookshelf");
            case "minecraft:copper_bars", "minecraft:exposed_copper_bars", "minecraft:weathered_copper_bars",
                    "minecraft:oxidized_copper_bars", "minecraft:waxed_copper_bars", "minecraft:waxed_exposed_copper_bars",
                    "minecraft:waxed_weathered_copper_bars", "minecraft:waxed_oxidized_copper_bars" -> mapping.withFallbackIdentifier("minecraft:iron_bars");
            case "minecraft:copper_chest", "minecraft:exposed_copper_chest", "minecraft:weathered_copper_chest",
                    "minecraft:oxidized_copper_chest", "minecraft:waxed_copper_chest", "minecraft:waxed_exposed_copper_chest",
                    "minecraft:waxed_weathered_copper_chest", "minecraft:waxed_oxidized_copper_chest" -> mapping.withFallbackIdentifier("minecraft:chest");
            case "minecraft:copper_golem_statue", "minecraft:exposed_copper_golem_statue", "minecraft:weathered_copper_golem_statue",
                    "minecraft:oxidized_copper_golem_statue", "minecraft:waxed_copper_golem_statue", "minecraft:waxed_exposed_copper_golem_statue",
                    "minecraft:waxed_weathered_copper_golem_statue", "minecraft:waxed_oxidized_copper_golem_statue" -> mapping.withFallbackIdentifier("minecraft:armor_stand");
            case "minecraft:copper_lantern", "minecraft:exposed_copper_lantern", "minecraft:weathered_copper_lantern",
                    "minecraft:oxidized_copper_lantern", "minecraft:waxed_copper_lantern", "minecraft:waxed_exposed_copper_lantern",
                    "minecraft:waxed_weathered_copper_lantern", "minecraft:waxed_oxidized_copper_lantern" -> mapping.withFallbackIdentifier("minecraft:lantern");
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
            case "minecraft:copper_horse_armor" -> mapping.withFallbackIdentifier("minecraft:leather_horse_armor");
            case "minecraft:copper_golem_spawn_egg" -> mapping.withFallbackIdentifier("minecraft:iron_golem_spawn_egg");
            case "minecraft:exposed_lightning_rod", "minecraft:weathered_lightning_rod", "minecraft:oxidized_lightning_rod",
                    "minecraft:waxed_lightning_rod", "minecraft:waxed_exposed_lightning_rod", "minecraft:waxed_weathered_lightning_rod",
                    "minecraft:waxed_oxidized_lightning_rod" -> mapping.withFallbackIdentifier("minecraft:lightning_rod");
            case "minecraft:copper_torch" -> mapping.withFallbackIdentifier("minecraft:torch");
            case "minecraft:wooden_spear" -> mapping.withFallbackIdentifier("minecraft:wooden_sword");
            case "minecraft:stone_spear" -> mapping.withFallbackIdentifier("minecraft:stone_sword");
            case "minecraft:copper_spear" -> mapping.withFallbackIdentifier("minecraft:copper_sword");
            case "minecraft:iron_spear" -> mapping.withFallbackIdentifier("minecraft:iron_sword");
            case "minecraft:golden_spear" -> mapping.withFallbackIdentifier("minecraft:golden_sword");
            case "minecraft:diamond_spear" -> mapping.withFallbackIdentifier("minecraft:diamond_sword");
            case "minecraft:netherite_spear" -> mapping.withFallbackIdentifier("minecraft:netherite_sword");
            case "minecraft:copper_nautilus_armor" -> mapping.withFallbackIdentifier("minecraft:copper_horse_armor");
            case "minecraft:iron_nautilus_armor" -> mapping.withFallbackIdentifier("minecraft:iron_horse_armor");
            case "minecraft:golden_nautilus_armor" -> mapping.withFallbackIdentifier("minecraft:golden_horse_armor");
            case "minecraft:diamond_nautilus_armor", "minecraft:netherite_horse_armor", "minecraft:netherite_nautilus_armor" -> mapping.withFallbackIdentifier("minecraft:diamond_horse_armor");
            case "minecraft:nautilus_spawn_egg", "minecraft:zombie_nautilus_spawn_egg" -> mapping.withFallbackIdentifier("minecraft:pufferfish_spawn_egg");
            case "minecraft:camel_husk_spawn_egg" -> mapping.withFallbackIdentifier("minecraft:camel_spawn_egg");
            case "minecraft:parched_spawn_egg" -> mapping.withFallbackIdentifier("minecraft:skeleton_spawn_egg");
            default -> mapping;
        };
    }
}
