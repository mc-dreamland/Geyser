/*
 * Copyright (c) 2019-2023 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.registry.populator;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.registry.type.GeyserMappingItem;

import java.util.List;
import java.util.Map;

/**
 * Backwards-maps the blocks and items of 1.20.50 (630) to 1.20.40 (622)
 */
class Conversion630_618 {

    private static final List<String> NEW_STONES = List.of("minecraft:stone", "minecraft:granite", "minecraft:polished_granite", "minecraft:diorite", "minecraft:polished_diorite", "minecraft:andesite", "minecraft:polished_andesite");
    private static final List<String> NEW_WOODS = List.of("minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:birch_planks", "minecraft:jungle_planks", "minecraft:acacia_planks", "minecraft:dark_oak_planks");
    private static final List<String> FACING_DIRECTION_OFFSET = List.of("minecraft:ender_chest", "minecraft:chest", "minecraft:stonecutter_block", "minecraft:trapped_chest", "minecraft:blast_furnace", "minecraft:lit_blast_furnace", "minecraft:lit_smoker", "minecraft:smoker");
    private static final List<String> FACING_DIRECTION = List.of("minecraft:amethyst_cluster", "minecraft:medium_amethyst_bud", "minecraft:large_amethyst_bud", "minecraft:small_amethyst_bud");
    private static final List<String> DIRECTION = List.of("minecraft:anvil", "minecraft:big_dripleaf", "minecraft:calibrated_sculk_sensor", "minecraft:campfire", "minecraft:end_portal_frame", "minecraft:lectern", "minecraft:pink_petals", "minecraft:powered_comparator", "minecraft:powered_repeater", "minecraft:small_dripleaf_block", "minecraft:soul_campfire", "minecraft:unpowered_comparator", "minecraft:unpowered_repeater");

    private static final Map<String, String> ITEMS = new Object2ObjectOpenHashMap<>();

    static {
        ITEMS.put("minecraft:acacia_planks", "minecraft:planks");
        ITEMS.put("minecraft:birch_planks", "minecraft:planks");
        ITEMS.put("minecraft:dark_oak_planks", "minecraft:planks");
        ITEMS.put("minecraft:jungle_planks", "minecraft:planks");
        ITEMS.put("minecraft:oak_planks", "minecraft:planks");
        ITEMS.put("minecraft:spruce_planks", "minecraft:planks");

        ITEMS.put("minecraft:diorite", "minecraft:stone");
        ITEMS.put("minecraft:andesite", "minecraft:stone");
        ITEMS.put("minecraft:granite", "minecraft:stone");
        ITEMS.put("minecraft:polished_andesite", "minecraft:stone");
        ITEMS.put("minecraft:polished_diorite", "minecraft:stone");
        ITEMS.put("minecraft:polished_granite", "minecraft:stone");

        ITEMS.put("minecraft:chiseled_tuff", "minecraft:chiseled_deepslate");
        ITEMS.put("minecraft:chiseled_tuff_bricks", "minecraft:chiseled_deepslate");
        ITEMS.put("minecraft:polished_tuff", "minecraft:polished_deepslate");
        ITEMS.put("minecraft:polished_tuff_double_slab", "minecraft:polished_deepslate_double_slab");
        ITEMS.put("minecraft:polished_tuff_slab", "minecraft:polished_deepslate_slab");
        ITEMS.put("minecraft:polished_tuff_stairs", "minecraft:polished_deepslate_stairs");
        ITEMS.put("minecraft:polished_tuff_wall", "minecraft:polished_deepslate_wall");
        ITEMS.put("minecraft:tuff_brick_double_slab", "minecraft:deepslate_brick_double_slab");
        ITEMS.put("minecraft:tuff_brick_slab", "minecraft:deepslate_brick_slab");
        ITEMS.put("minecraft:tuff_brick_stairs", "minecraft:deepslate_brick_stairs");
        ITEMS.put("minecraft:tuff_brick_wall", "minecraft:deepslate_brick_wall");
        ITEMS.put("minecraft:tuff_bricks", "minecraft:deepslate_bricks");
        ITEMS.put("minecraft:tuff_double_slab", "minecraft:cobbled_deepslate_double_slab");
        ITEMS.put("minecraft:tuff_slab", "minecraft:cobbled_deepslate_slab");
        ITEMS.put("minecraft:tuff_stairs", "minecraft:cobbled_deepslate_stairs");
        ITEMS.put("minecraft:tuff_wall", "minecraft:cobbled_deepslate_wall");

        ITEMS.put("minecraft:chiseled_copper", "minecraft:copper_block");
        ITEMS.put("minecraft:copper_bulb", "minecraft:copper_block");
        ITEMS.put("minecraft:copper_door", "minecraft:iron_door");
        ITEMS.put("minecraft:copper_grate", "minecraft:raw_iron_block");
        ITEMS.put("minecraft:copper_trapdoor", "minecraft:iron_trapdoor");
        ITEMS.put("minecraft:exposed_chiseled_copper", "minecraft:exposed_copper");
        ITEMS.put("minecraft:exposed_copper_bulb", "minecraft:exposed_copper");
        ITEMS.put("minecraft:exposed_copper_door", "minecraft:iron_door");
        ITEMS.put("minecraft:exposed_copper_grate", "minecraft:raw_iron_block");
        ITEMS.put("minecraft:exposed_copper_trapdoor", "minecraft:iron_trapdoor");
        ITEMS.put("minecraft:oxidized_chiseled_copper", "minecraft:oxidized_copper");
        ITEMS.put("minecraft:oxidized_copper_bulb", "minecraft:oxidized_copper");
        ITEMS.put("minecraft:oxidized_copper_door", "minecraft:iron_door");
        ITEMS.put("minecraft:oxidized_copper_grate", "minecraft:raw_iron_block");
        ITEMS.put("minecraft:oxidized_copper_trapdoor", "minecraft:iron_trapdoor");
        ITEMS.put("minecraft:waxed_chiseled_copper", "minecraft:waxed_copper");
        ITEMS.put("minecraft:waxed_copper_bulb", "minecraft:waxed_copper");
        ITEMS.put("minecraft:waxed_copper_door", "minecraft:iron_door");
        ITEMS.put("minecraft:waxed_copper_grate", "minecraft:raw_iron_block");
        ITEMS.put("minecraft:waxed_copper_trapdoor", "minecraft:iron_trapdoor");
        ITEMS.put("minecraft:waxed_exposed_chiseled_copper", "minecraft:waxed_exposed_copper");
        ITEMS.put("minecraft:waxed_exposed_copper_bulb", "minecraft:waxed_exposed_copper");
        ITEMS.put("minecraft:waxed_exposed_copper_door", "minecraft:iron_door");
        ITEMS.put("minecraft:waxed_exposed_copper_grate", "minecraft:raw_iron_block");
        ITEMS.put("minecraft:waxed_exposed_copper_trapdoor", "minecraft:iron_trapdoor");
        ITEMS.put("minecraft:waxed_oxidized_chiseled_copper", "minecraft:waxed_oxidized_copper");
        ITEMS.put("minecraft:waxed_oxidized_copper_bulb", "minecraft:waxed_oxidized_copper");
        ITEMS.put("minecraft:waxed_oxidized_copper_door", "minecraft:iron_door");
        ITEMS.put("minecraft:waxed_oxidized_copper_grate", "minecraft:raw_iron_block");
        ITEMS.put("minecraft:waxed_oxidized_copper_trapdoor", "minecraft:iron_trapdoor");
        ITEMS.put("minecraft:waxed_weathered_chiseled_copper", "minecraft:waxed_weathered_copper");
        ITEMS.put("minecraft:waxed_weathered_copper_bulb", "minecraft:waxed_weathered_copper");
        ITEMS.put("minecraft:waxed_weathered_copper_door", "minecraft:iron_door");
        ITEMS.put("minecraft:waxed_weathered_copper_grate", "minecraft:raw_iron_block");
        ITEMS.put("minecraft:waxed_weathered_copper_trapdoor", "minecraft:iron_trapdoor");
        ITEMS.put("minecraft:weathered_chiseled_copper", "minecraft:weathered_copper");
        ITEMS.put("minecraft:weathered_copper_bulb", "minecraft:weathered_copper");
        ITEMS.put("minecraft:weathered_copper_door", "minecraft:iron_door");
        ITEMS.put("minecraft:weathered_copper_grate", "minecraft:raw_iron_block");
        ITEMS.put("minecraft:weathered_copper_trapdoor", "minecraft:iron_trapdoor");

        ITEMS.put("minecraft:crafter", "minecraft:crafting_table");

        ITEMS.put("minecraft:white_terracotta", "minecraft:stained_hardened_clay");
        ITEMS.put("minecraft:orange_terracotta", "minecraft:stained_hardened_clay");
        ITEMS.put("minecraft:magenta_terracotta", "minecraft:stained_hardened_clay");
        ITEMS.put("minecraft:light_blue_terracotta", "minecraft:stained_hardened_clay");
        ITEMS.put("minecraft:yellow_terracotta", "minecraft:stained_hardened_clay");
        ITEMS.put("minecraft:lime_terracotta", "minecraft:stained_hardened_clay");
        ITEMS.put("minecraft:pink_terracotta", "minecraft:stained_hardened_clay");
        ITEMS.put("minecraft:gray_terracotta", "minecraft:stained_hardened_clay");
        ITEMS.put("minecraft:light_gray_terracotta", "minecraft:stained_hardened_clay");
        ITEMS.put("minecraft:cyan_terracotta", "minecraft:stained_hardened_clay");
        ITEMS.put("minecraft:purple_terracotta", "minecraft:stained_hardened_clay");
        ITEMS.put("minecraft:blue_terracotta", "minecraft:stained_hardened_clay");
        ITEMS.put("minecraft:brown_terracotta", "minecraft:stained_hardened_clay");
        ITEMS.put("minecraft:green_terracotta", "minecraft:stained_hardened_clay");
        ITEMS.put("minecraft:red_terracotta", "minecraft:stained_hardened_clay");
        ITEMS.put("minecraft:black_terracotta", "minecraft:stained_hardened_clay");

        ITEMS.put("minecraft:white_stained_glass_pane", "minecraft:stained_glass_pane");
        ITEMS.put("minecraft:orange_stained_glass_pane", "minecraft:stained_glass_pane");
        ITEMS.put("minecraft:magenta_stained_glass_pane", "minecraft:stained_glass_pane");
        ITEMS.put("minecraft:light_blue_stained_glass_pane", "minecraft:stained_glass_pane");
        ITEMS.put("minecraft:yellow_stained_glass_pane", "minecraft:stained_glass_pane");
        ITEMS.put("minecraft:lime_stained_glass_pane", "minecraft:stained_glass_pane");
        ITEMS.put("minecraft:pink_stained_glass_pane", "minecraft:stained_glass_pane");
        ITEMS.put("minecraft:gray_stained_glass_pane", "minecraft:stained_glass_pane");
        ITEMS.put("minecraft:light_gray_stained_glass_pane", "minecraft:stained_glass_pane");
        ITEMS.put("minecraft:purple_stained_glass_pane", "minecraft:stained_glass_pane");
        ITEMS.put("minecraft:blue_stained_glass_pane", "minecraft:stained_glass_pane");
        ITEMS.put("minecraft:brown_stained_glass_pane", "minecraft:stained_glass_pane");
        ITEMS.put("minecraft:green_stained_glass_pane", "minecraft:stained_glass_pane");
        ITEMS.put("minecraft:red_stained_glass_pane", "minecraft:stained_glass_pane");
        ITEMS.put("minecraft:black_stained_glass_pane", "minecraft:stained_glass_pane");
    }

    static GeyserMappingItem remapItem(@SuppressWarnings("unused") Item item, GeyserMappingItem mapping) {
        String replacement = ITEMS.get(mapping.getBedrockIdentifier());
        if (replacement == null) {
            return mapping;
        } else {
            return mapping.withBedrockIdentifier(replacement);
        }
    }

    static NbtMap remapBlock(NbtMap tag) {
        final String name = tag.getString("name");

        String replacement;
        if (NEW_STONES.contains(name) || NEW_WOODS.contains(name)) {
            String typeKey;
            String type = name.substring(10);
            if (NEW_STONES.contains(name)) {
                replacement = "minecraft:stone";
                typeKey = "stone_type";
                if (type.startsWith("polished_")) {
                    type = type.substring(9) + "_smooth";
                }
            } else {
                replacement = "minecraft:planks";
                typeKey = "wood_type";
                type = type.substring(0, type.indexOf("_planks"));
            }

            return tag.toBuilder()
                .putString("name", replacement)
                .putCompound("states", NbtMap.builder().putString(typeKey, type).build())
                .build();
        } else if (name.contains("tuff") && !name.equals("minecraft:tuff")) {

            if (name.contains("brick") || name.contains("polished") || name.contains("chiseled")) {
                replacement = name.replace("tuff", "deepslate");

                if (name.contains("chiseled")) {
                    // chiseled deepslate bricks don't exist. just use chiseled deepslate instead
                    replacement = replacement.replace("_bricks", "");
                }
            } else {
                replacement = name.replace("tuff", "cobbled_deepslate");
            }

            NbtMapBuilder builder = tag.toBuilder()
                    .putString("name", replacement);
            if (tag.containsKey("states")) {
                NbtMap b = tag.getCompound("states");

                if (b.containsKey("minecraft:vertical_half")) {
                    NbtMapBuilder states = b.toBuilder();
                    String top_slot_bit = b.getString("minecraft:vertical_half");
                    if (top_slot_bit.equals("top")) {
                        states.put("top_slot_bit", true);
                    } else {
                        states.put("top_slot_bit", false);
                    }
                    states.remove("minecraft:vertical_half");
                    builder.put("states", states.build());
                }
            }



            return builder.build();
        } else if (name.contains("copper")) {


            boolean removeStates = false;
            if (name.contains("chiseled")) {
                replacement = name.replace("_chiseled", ""); // special chiseled
                replacement = replacement.replace("chiseled_", ""); // plain chiseled
            } else if (name.endsWith("bulb")) {
                replacement = name.replace("_bulb", "");
                removeStates = true;
            } else if (name.endsWith("grate")) {
                replacement = "minecraft:raw_iron_block";
            } else if (name.endsWith("door")) {
                if (name.contains("trap")) {
                    replacement = "minecraft:iron_trapdoor";
                } else {
                    replacement = "minecraft:iron_door";
                }
            } else {

                NbtMapBuilder builder = tag.toBuilder();
                if (tag.containsKey("states")) {
                    NbtMap b = tag.getCompound("states");

                    if (b.containsKey("minecraft:vertical_half")) {
                        NbtMapBuilder states = b.toBuilder();
                        String top_slot_bit = b.getString("minecraft:vertical_half");
                        if (top_slot_bit.equals("top")) {
                            states.put("top_slot_bit", true);
                        } else {
                            states.put("top_slot_bit", false);
                        }
                        states.remove("minecraft:vertical_half");
                        builder.put("states", states.build());
                        return builder.build();
                    }
                    return tag;
                } else {
                    return tag;
                }
            }

            if (replacement.endsWith(":copper")) {
                // case for plain chiseled copper and plain bulb
                replacement = replacement + "_block";
            }

            NbtMapBuilder builder = tag.toBuilder();
            builder.putString("name", replacement);
            if (removeStates) {
                builder.putCompound("states", NbtMap.EMPTY);
            }


            return builder.build();
        } else if (name.equals("minecraft:crafter")) {
            NbtMapBuilder builder = tag.toBuilder();
            builder.put("name", "minecraft:crafting_table");
            builder.put("states", NbtMap.EMPTY);
            return builder.build();
        } else if (FACING_DIRECTION_OFFSET.contains(name) || FACING_DIRECTION.contains(name) || DIRECTION.contains(name)) {
            NbtMapBuilder builder = tag.toBuilder();
            NbtMap b = builder.build().getCompound("states");
            if (b == null) {
                return tag;
            }
            NbtMapBuilder states = b.toBuilder();
            builder.put("name", name);
            String cardinal_direction = b.getString("minecraft:cardinal_direction");
            int facing_direction = getFacingDirection(name, cardinal_direction);

            states.remove("minecraft:cardinal_direction");
            states.remove("minecraft:block_face");

            String key;
            if (DIRECTION.contains(name)) {
                key = "direction";
            } else {
                key = "facing_direction";
            }

            states.put(key, facing_direction);
            builder.put("states", states.build());

            return builder.build();
        } else if (
                (name.contains("_stained_glass") || name.contains("_stained_glass_pane")
                        || name.contains("_concrete_powder") || name.contains("_terracotta")
                )
                && !name.contains("glazed_terracotta"))
        {
            String color = name.replace("minecraft:", "")
                    .replace("_stained_glass_pane", "")
                    .replace("_concrete_powder", "")
                    .replace("_terracotta", "")
                    .replace("_stained_glass", "");
            String n = name;
            if (color.equals("light_gray")) {
                n = name.replace("light_gray_", "");
                color = "silver";
            } else {
                n = name.replace(color + "_", "");
            }

            if (name.contains("_terracotta")) {
                n = "minecraft:stained_hardened_clay";
            }

            NbtMapBuilder builder = tag.toBuilder();
            NbtMapBuilder states = NbtMap.EMPTY.toBuilder();
            states.put("color", color);
            builder.putCompound("states", states.build());
            builder.put("name", n);
            NbtMap build = builder.build();
            return build;
        } else if (name.matches("minecraft:.+slab(?:[2-4])?\\b")) {
            NbtMapBuilder builder = tag.toBuilder();
            NbtMap b = builder.build().getCompound("states");
            if (b == null) {
                return tag;
            }
            String top_slot_bit = b.getString("minecraft:vertical_half");
            NbtMapBuilder states = b.toBuilder();
            String n = name;

            builder.put("name", n);
            if (top_slot_bit.equals("top")) {
                states.put("top_slot_bit", true);
            } else {
                states.put("top_slot_bit", false);
            }
            states.remove("minecraft:vertical_half");

            builder.put("states", states.build());

            return builder.build();
        }

        NbtMapBuilder builder = tag.toBuilder();
        NbtMap b = builder.build().getCompound("states");
        if (b == null) {
            return tag;
        }

        return tag;
    }

    private static int getFacingDirection(String name, String cardinal_direction) {
        int facing_direction = 0;
        if (FACING_DIRECTION_OFFSET.contains(name)) {
            facing_direction = switch (cardinal_direction) {
                case "up" -> 1;
                case "north" -> 2;
                case "south" -> 3;
                case "west" -> 4;
                case "east" -> 5;
                default -> 0;
            };
        } else if (FACING_DIRECTION.contains(name) || DIRECTION.contains(name)) {
            facing_direction = switch (cardinal_direction) {
                case "south" -> 1;
                case "east" -> 2;
                case "west" -> 3;
                default -> 0;
            };
        }
        return facing_direction;
    }
}
