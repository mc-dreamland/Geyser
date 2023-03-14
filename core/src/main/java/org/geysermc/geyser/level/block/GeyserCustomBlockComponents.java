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

package org.geysermc.geyser.level.block;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.Value;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.api.block.custom.component.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Value
public class GeyserCustomBlockComponents implements CustomBlockComponents {
    BoxComponent selectionBox;
    BoxComponent collisionBox;
    String displayName;
    String geometry;
    Map<String, MaterialInstance> materialInstances;
    List<PlacementConditions> placementFilter;
    Float destructibleByMining;
    Float friction;
    Float lightEmission;
    Float lightDampening;
    RotationComponent rotation;
    boolean unitCube;
    boolean placeAir;
    Float destroy_time;
    Integer netease_face_directional;
    List<NeteaseBoxComponent> netease_aabb_collision;
    List<NeteaseBoxComponent> netease_aabb_clip;
    boolean netease_block_entity;
    Set<String> tags;

    private GeyserCustomBlockComponents(CustomBlockComponentsBuilder builder) {
        this.selectionBox = builder.selectionBox;
        this.collisionBox = builder.collisionBox;
        this.displayName = builder.displayName;
        this.geometry = builder.geometry;
        if (builder.materialInstances.isEmpty()) {
            this.materialInstances = Object2ObjectMaps.emptyMap();
        } else {
            this.materialInstances = Object2ObjectMaps.unmodifiable(new Object2ObjectArrayMap<>(builder.materialInstances));
        }
        this.placementFilter = builder.placementFilter;
        this.destructibleByMining = builder.destructibleByMining;
        this.friction = builder.friction;
        this.lightEmission = builder.lightEmission;
        this.lightDampening = builder.lightDampening;
        this.rotation = builder.rotation;
        this.unitCube = builder.unitCube;
        this.placeAir = builder.placeAir;
        this.destroy_time = builder.destroy_time;
        this.netease_face_directional = builder.netease_face_directional;
        this.netease_aabb_collision = builder.netease_aabb_collision;
        this.netease_aabb_clip = builder.netease_aabb_clip;
        this.netease_block_entity = builder.netease_block_entity;
        if (builder.tags.isEmpty()) {
            this.tags = Set.of();
        } else {
            this.tags = Set.copyOf(builder.tags);
        }
    }

    @Override
    public BoxComponent selectionBox() {
        return selectionBox;
    }

    @Override
    public BoxComponent collisionBox() {
        return collisionBox;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public String geometry() {
        return geometry;
    }

    @Override
    public @NonNull Map<String, MaterialInstance> materialInstances() {
        return materialInstances;
    }

    @Override
    public List<PlacementConditions> placementFilter() {
        return placementFilter;
    }

    @Override
    public Float destructibleByMining() {
        return destructibleByMining;
    }

    @Override
    public Float friction() {
        return friction;
    }

    @Override
    public Float lightEmission() {
        return lightEmission;
    }

    @Override
    public Float lightDampening() {
        return lightDampening;
    }

    @Override
    public RotationComponent rotation() {
        return rotation;
    }

    @Override
    public boolean unitCube() {
        return unitCube;
    }

    @Override
    public boolean placeAir() {
        return placeAir;
    }

    @Override
    public Float destory_time() {
        return destroy_time;
    }

    @Override
    public Integer netease_face_directional() {
        return netease_face_directional;
    }

    @Override
    public List<NeteaseBoxComponent> netease_aabb_collision() {
        return netease_aabb_collision;
    }

    @Override
    public List<NeteaseBoxComponent> netease_aabb_clip() {
        return netease_aabb_clip;
    }

    @Override
    public boolean netease_block_entity() {
        return netease_block_entity;
    }

    @Override
    public Set<String> tags() {
        return tags;
    }

    public static class CustomBlockComponentsBuilder implements Builder {
        protected BoxComponent selectionBox;
        protected BoxComponent collisionBox;
        protected String displayName;
        protected String geometry;
        protected final Object2ObjectMap<String, MaterialInstance> materialInstances = new Object2ObjectOpenHashMap<>();
        protected List<PlacementConditions> placementFilter;
        protected Float destructibleByMining;
        protected Float friction;
        protected Float lightEmission;
        protected Float lightDampening;
        protected RotationComponent rotation;
        protected boolean unitCube = false;
        protected boolean placeAir = false;
        protected final Set<String> tags = new HashSet<>();
        protected float destroy_time;
        protected Integer netease_face_directional;
        protected List<NeteaseBoxComponent> netease_aabb_collision;
        protected List<NeteaseBoxComponent> netease_aabb_clip;
        public boolean netease_block_entity;

        private void validateBox(BoxComponent box) {
            if (box == null) {
                return;
            }
            if (box.sizeX() < 0 || box.sizeY() < 0 || box.sizeZ() < 0) {
                throw new IllegalArgumentException("Box size must be non-negative.");
            }
            float minX = box.originX() + 8;
            float minY = box.originY();
            float minZ = box.originZ() + 8;
            float maxX = minX + box.sizeX();
            float maxY = minY + box.sizeY();
            float maxZ = minZ + box.sizeZ();
            if (minX < 0 || minY < 0 || minZ < 0 || maxX > 16 || maxY > 16 || maxZ > 16) {
                throw new IllegalArgumentException("Box bounds must be within (0, 0, 0) and (16, 16, 16)");
            }
        }
        private void validateNetEaseBox(List<NeteaseBoxComponent> boxComponent) {
            if (boxComponent == null) return;
            for (NeteaseBoxComponent box : boxComponent) {
                if (box == null) {
                    continue;
                }
                if (box.originX() > box.sizeX() || box.originY() > box.sizeY() || box.originZ() > box.sizeZ()) {
                    throw new IllegalArgumentException("Box size must be non-negative.");
                }
                if (box.originX() < 0 || box.originY() < 0 || box.originZ() < 0 || box.sizeX() > 2 || box.sizeY() > 2 || box.sizeZ() > 2) {
                    throw new IllegalArgumentException("Box bounds must be within (-1, -1, -1) and (2, 2, 2)");
                }
            }
            return;
        }

        @Override
        public Builder selectionBox(BoxComponent selectionBox) {
            validateBox(selectionBox);
            this.selectionBox = selectionBox;
            return this;
        }

        @Override
        public Builder collisionBox(BoxComponent collisionBox) {
            validateBox(collisionBox);
            this.collisionBox = collisionBox;
            return this;
        }

        @Override
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        @Override
        public Builder geometry(String geometry) {
            this.geometry = geometry;
            return this;
        }

        @Override
        public Builder materialInstance(@NotNull String name, @NotNull MaterialInstance materialInstance) {
            this.materialInstances.put(name, materialInstance);
            return this;
        }

        @Override
        public Builder placementFilter(List<PlacementConditions> placementFilter) {
            this.placementFilter = placementFilter;
            return this;
        }

        @Override
        public Builder destructibleByMining(Float destructibleByMining) {
            if (destructibleByMining != null && destructibleByMining < 0) {
                throw new IllegalArgumentException("Destructible by mining must be non-negative");
            }
            this.destructibleByMining = destructibleByMining;
            return this;
        }

        @Override
        public Builder friction(Float friction) {
            if (friction != null) {
                if (friction < 0 || friction > 1) {
                    throw new IllegalArgumentException("Friction must be in the range 0-1");
                }
            }
            this.friction = friction;
            return this;
        }

        @Override
        public Builder lightEmission(Float lightEmission) {
            if (lightEmission != null) {
                if (lightEmission < 0 || lightEmission > 15) {
                    throw new IllegalArgumentException("Light emission must be in the range 0-15");
                }
            }
            this.lightEmission = lightEmission;
            return this;
        }

        @Override
        public Builder lightDampening(Float lightDampening) {
            if (lightDampening != null) {
                if (lightDampening < 0 || lightDampening > 15) {
                    throw new IllegalArgumentException("Light dampening must be in the range 0-15");
                }
            }
            this.lightDampening = lightDampening;
            return this;
        }

        @Override
        public Builder rotation(RotationComponent rotation) {
            if (rotation.x() % 90 != 0 || rotation.y() % 90 != 0 || rotation.z() % 90 != 0) {
                throw new IllegalArgumentException("Rotation must be a multiple of 90 degrees.");
            }
            this.rotation = rotation;
            return this;
        }

        @Override
        public Builder unitCube(boolean unitCube) {
            this.unitCube = unitCube;
            return this;
        }

        @Override
        public Builder placeAir(boolean placeAir) {
            this.placeAir = placeAir;
            return this;
        }

        @Override
        public Builder tags(Set<String> tags) {
            this.tags.addAll(tags);
            return this;
        }

        @Override
        public Builder destroy_time(float destroy_time) {
            this.destroy_time = destroy_time;
            return this;
        }

        @Override
        public Builder netease_face_directional(int netease_face_directional) {
            this.netease_face_directional = netease_face_directional;
            return this;
        }

        @Override
        public Builder netease_aabb_collision(List<NeteaseBoxComponent> netease_aabb_collision) {
            validateNetEaseBox(netease_aabb_collision);
            this.netease_aabb_collision = netease_aabb_collision;
            return this;
        }

        @Override
        public Builder netease_aabb_clip(List<NeteaseBoxComponent> netease_aabb_clip) {
            validateNetEaseBox(netease_aabb_clip);
            this.netease_aabb_clip = netease_aabb_clip;
            return this;
        }

        @Override
        public Builder netease_block_entity(boolean netease_block_entity) {
            this.netease_block_entity = netease_block_entity;
            return this;
        }

        @Override
        public CustomBlockComponents build() {
            return new GeyserCustomBlockComponents(this);
        }
    }
}
