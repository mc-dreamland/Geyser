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
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.registry.type.GeyserMappingItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.geysermc.geyser.registry.populator.conversion.ConversionHelper.withName;
import static org.geysermc.geyser.registry.populator.conversion.ConversionHelper.withoutStates;

public class Conversion766_748 {
    static List<String> PALE_WOODEN_BLOCKS = new ArrayList<>();
    static List<String> OTHER_NEW_BLOCKS = new ArrayList<>();

    static {
        Set.of(
            Blocks.PALE_OAK_WOOD,
            Blocks.PALE_OAK_PLANKS,
            Blocks.PALE_OAK_SAPLING,
            Blocks.PALE_OAK_LOG,
            Blocks.STRIPPED_PALE_OAK_LOG,
            Blocks.STRIPPED_PALE_OAK_WOOD,
            Blocks.PALE_OAK_LEAVES,
            Blocks.PALE_OAK_HANGING_SIGN,
            Blocks.PALE_OAK_PRESSURE_PLATE,
            Blocks.PALE_OAK_TRAPDOOR,
            Blocks.PALE_OAK_BUTTON,
            Blocks.PALE_OAK_STAIRS,
            Blocks.PALE_OAK_SLAB,
            Blocks.PALE_OAK_FENCE_GATE,
            Blocks.PALE_OAK_FENCE,
            Blocks.PALE_OAK_DOOR
        ).forEach(block -> PALE_WOODEN_BLOCKS.add(block.javaIdentifier().value()));

        // Some things are of course stupid
        PALE_WOODEN_BLOCKS.add("pale_oak_standing_sign");
        PALE_WOODEN_BLOCKS.add("pale_oak_wall_sign");
        PALE_WOODEN_BLOCKS.add("pale_oak_double_slab");

        Set.of(
            Blocks.PALE_MOSS_BLOCK,
            Blocks.PALE_MOSS_CARPET,
            Blocks.PALE_HANGING_MOSS,

            Blocks.OPEN_EYEBLOSSOM,
            Blocks.CLOSED_EYEBLOSSOM,

            Blocks.RESIN_CLUMP,
            Blocks.RESIN_BLOCK,
            Blocks.RESIN_BRICKS,
            Blocks.RESIN_BRICK_STAIRS,
            Blocks.RESIN_BRICK_SLAB,
            Blocks.RESIN_BRICK_WALL,
            Blocks.CHISELED_RESIN_BRICKS,

            Blocks.CREAKING_HEART
        ).forEach(block -> OTHER_NEW_BLOCKS.add(block.javaIdentifier().value()));

        OTHER_NEW_BLOCKS.add("resin_brick_double_slab");
        OTHER_NEW_BLOCKS.add("resin_brick");

    }

    public static NbtMap remapBlock(NbtMap tag) {

        // First: Downgrade from 1.21.60 -> 1.21.50
        tag = Conversion776_766.remapBlock(tag);

        String name = tag.getString("name").replace("minecraft:", "");
        if (PALE_WOODEN_BLOCKS.contains(name)) {
            return withName(tag, name.replace("pale_oak", "birch"));
        }

        if (OTHER_NEW_BLOCKS.contains(name)) {
            return switch (name) {
                case "resin_brick_double_slab" -> withName(tag,"red_sandstone_double_slab");
                case "pale_moss_block" -> withName(tag, "moss_block");
                case "pale_moss_carpet" -> withoutStates("moss_carpet");
                case "pale_hanging_moss" -> withoutStates("hanging_roots");
                case "open_eyeblossom" -> withoutStates("oxeye_daisy");
                case "closed_eyeblossom" -> withoutStates("white_tulip");
                case "resin_clump" -> withoutStates("unknown");
                case "resin_block" -> withoutStates("red_sandstone");
                case "resin_bricks" -> withoutStates("cut_red_sandstone");
                case "resin_brick_stairs" -> withName(tag, "red_sandstone_stairs");
                case "resin_brick_slab" -> withName(tag, "red_sandstone_slab");
                case "resin_brick_wall" -> withName(tag, "red_sandstone_wall");
                case "chiseled_resin_bricks" -> withName(tag, "chiseled_red_sandstone");
                case "creaking_heart" -> withoutStates("chiseled_polished_blackstone");
                default -> throw new IllegalStateException("missing replacement for new block! " + name);
            };
        }

        return tag;
    }

    public static GeyserMappingItem remapItem(Item item, GeyserMappingItem mapping) {

        String identifier = mapping.getBedrockIdentifier();
        String newName;

        String name = identifier.replace("minecraft:", "");
        if (PALE_WOODEN_BLOCKS.contains(name)) {
            return mapping.withBedrockIdentifier(identifier.replace("pale_oak", "birch"));
        }

        if (name.contains("pale_oak")) {
            return mapping.withBedrockIdentifier(identifier.replace("pale_oak", "birch"));
        }

        if (name.equals("blue_egg")) {
            return mapping.withBedrockIdentifier("minecraft:egg");
        }

        if (name.equals("brown_egg")) {
            return mapping.withBedrockIdentifier("minecraft:egg");
        }

        if (name.contains("bundle")) {
            return mapping.withBedrockIdentifier("minecraft:unknown");
        }

        if (name.contains("creaking_spawn_egg")) {
            return mapping.withBedrockIdentifier("minecraft:zombie_spawn_egg");
        }

        if (identifier.equals("minecraft:bush")) {
            return mapping.withBedrockIdentifier("minecraft:fern");
        }
        if (identifier.equals("minecraft:firefly_bush")) {
            return mapping.withBedrockIdentifier("minecraft:deadbush");
        }
        if (identifier.equals("minecraft:open_eyeblossom")) {
            return mapping.withBedrockIdentifier("minecraft:oxeye_daisy");
        }
        if (identifier.equals("minecraft:tall_dry_grass") || identifier.equals("minecraft:short_dry_grass")) {
            return mapping.withBedrockIdentifier("minecraft:short_grass");
        }
        if (OTHER_NEW_BLOCKS.contains(name)) {
            newName =  switch (name) {
                case "resin_brick_double_slab" -> "red_sandstone_double_slab";
                case "pale_moss_block" ->  "moss_block";
                case "pale_moss_carpet" -> "moss_carpet";
                case "pale_hanging_moss" -> "hanging_roots";
                case "open_eyeblossom" -> "oxeye_daisy";
                case "closed_eyeblossom" -> "white_tulip";
                case "resin_clump" -> "unknown";
                case "resin_block" -> "red_sandstone";
                case "resin_brick" -> "brick";
                case "resin_bricks" -> "cut_red_sandstone";
                case "resin_brick_stairs" ->  "red_sandstone_stairs";
                case "resin_brick_slab" ->  "red_sandstone_slab";
                case "resin_brick_wall" ->  "red_sandstone_wall";
                case "chiseled_resin_bricks" ->  "chiseled_red_sandstone";
                case "creaking_heart" -> "chiseled_polished_blackstone";
                default -> throw new IllegalStateException("missing replacement for new block! " + name);
            };

            return mapping.withBedrockIdentifier("minecraft:" + newName);
        }

        if (name.equals("cactus_flower")) {
            return mapping.withBedrockIdentifier("minecraft:" + "unknown");
        }

        if (name.equals("leaf_litter") || name.equals("wildflowers")) {
            return mapping.withBedrockIdentifier("minecraft:" + "unknown");
        }

        return mapping;
    }
}
