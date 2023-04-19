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

package org.geysermc.geyser.api.block.custom.component;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class is used to store components for a custom block or custom block permutation.
 */
public interface CustomBlockComponents {

    /**
     * Gets the selection box component
     * Equivalent to "minecraft:selection_box"
     *
     * @return The selection box.
     */
    BoxComponent selectionBox();

    /**
     * Gets the collision box component
     * Equivalent to "minecraft:collision_box"
     * @return The collision box.
     */
    BoxComponent collisionBox();

    /**
     * Gets the display name component
     * Equivalent to "minecraft:display_name"
     *
     * @return The display name.
     */
    String displayName();

    /**
     * Gets the geometry component
     * Equivalent to "minecraft:geometry"
     *
     * @return The geometry.
     */
    String geometry();

    /**
     * Gets the material instances component
     * Equivalent to "minecraft:material_instances"
     *
     * @return The material instances.
     */
    @NonNull Map<String, MaterialInstance> materialInstances();


    /**
     * Gets the placement filter component
     * Equivalent to "minecraft:placement_filter"
     *
     * @return The placement filter.
     */
    List<PlacementConditions> placementFilter();

    /**
     * Gets the destructible by mining component
     * Equivalent to "minecraft:destructible_by_mining"
     *
     * @return The destructible by mining value.
     */
    Float destructibleByMining();

    /**
     * Gets the friction component
     * Equivalent to "minecraft:friction"
     *
     * @return The friction value.
     */
    Float friction();

    /**
     * Gets the light emission component
     * Equivalent to "minecraft:light_emission"
     *
     * @return The light emission value.
     */
    Float lightEmission();

    /**
     * Gets the light dampening component
     * Equivalent to "minecraft:light_dampening"
     *
     * @return The light dampening value.
     */
    Float lightDampening();

    /**
     * Gets the rotation component
     * Equivalent to "minecraft:rotation"
     *
     * @return The rotation.
     */
    RotationComponent rotation();

    /**
     * Gets the unit cube component
     * Equivalent to "minecraft:unit_cube"
     *
     * @return The rotation.
     */
    boolean unitCube();

    /**
     * Gets if the block should place only air
     * Equivalent to setting a dummy event to run on "minecraft:on_player_placing"
     * 
     * @return If the block should place only air.
     */
    boolean placeAir();

    Float destory_time();

    Integer netease_face_directional();

    List<NeteaseBoxComponent> netease_aabb_collision();

    List<NeteaseBoxComponent> netease_aabb_clip();

    boolean netease_block_entity();

    /**
     * Gets the set of tags
     * Equivalent to "tag:some_tag"
     * 
     * @return The set of tags.
     */
    Set<String> tags();

    interface Builder {
        Builder selectionBox(BoxComponent selectionBox);

        Builder collisionBox(BoxComponent collisionBox);

        Builder displayName(String displayName);

        Builder geometry(String geometry);

        Builder materialInstance(@NonNull String name, @NonNull MaterialInstance materialInstance);

        Builder placementFilter(List<PlacementConditions> placementConditions);

        Builder destructibleByMining(Float destructibleByMining);

        Builder friction(Float friction);

        Builder lightEmission(Float lightEmission);

        Builder lightDampening(Float lightDampening);

        Builder rotation(RotationComponent rotation);

        Builder unitCube(boolean unitCube);

        Builder placeAir(boolean placeAir);

        Builder tags(Set<String> tags);

        Builder destroy_time(float destroy_time);
        Builder netease_face_directional(int netease_face_directional);
        Builder netease_aabb_collision(List<NeteaseBoxComponent> netease_aabb_collision);

        Builder netease_aabb_clip(List<NeteaseBoxComponent> netease_aabb_clip);

        Builder netease_block_entity(boolean netease_block_entity);

        CustomBlockComponents build();
    }
}