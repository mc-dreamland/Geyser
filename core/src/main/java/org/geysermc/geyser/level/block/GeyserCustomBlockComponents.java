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
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.api.block.custom.component.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

@Value
public class GeyserCustomBlockComponents implements CustomBlockComponents {
    BoxComponent selectionBox;
    BoxComponent collisionBox;
    String displayName;
    GeometryComponent geometry;
    Map<String, MaterialInstance> materialInstances;
    List<PlacementConditions> placementFilter;
    Float destructibleByMining;
    Float friction;
    Integer lightEmission;
    Integer lightDampening;
    TransformationComponent transformation;
    boolean unitCube;
    boolean placeAir;
    Set<String> tags;

    //是否支持转向
    boolean rotatable;

    //Netease 自定义方块属性
    Integer neteaseFaceDirectional;
    List<NeteaseBoxComponent> neteaseAabbCollision;
    List<NeteaseBoxComponent> neteaseAabbClip;
    boolean neteaseBlockEntity;
    String neteaseTier;
    boolean neteaseSolid;
    String neteaseRenderLayer;

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
        this.transformation = builder.transformation;
        this.unitCube = builder.unitCube;
        this.placeAir = builder.placeAir;
        if (builder.tags.isEmpty()) {
            this.tags = Set.of();
        } else {
            this.tags = Set.copyOf(builder.tags);
        }

        this.rotatable = builder.rotatable;

        this.neteaseFaceDirectional = builder.neteaseFaceDirectional;
        this.neteaseAabbCollision = builder.neteaseAabbCollision;
        this.neteaseAabbClip = builder.neteaseAabbClip;
        this.neteaseBlockEntity = builder.neteaseBlockEntity;
        this.neteaseSolid = builder.neteaseSolid;
        this.neteaseTier = builder.neteaseTier;
        this.neteaseRenderLayer = builder.neteaseRenderLayer;
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
    public GeometryComponent geometry() {
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
    public Integer lightEmission() {
        return lightEmission;
    }

    @Override
    public Integer lightDampening() {
        return lightDampening;
    }

    @Override
    public TransformationComponent transformation() {
        return transformation;
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
    public @NonNull Set<String> tags() {
        return tags;
    }

    @Override
    public Integer neteaseFaceDirectional() {
        return neteaseFaceDirectional;
    }
    @Override
    public boolean rotatable() {
        return rotatable;
    }

    @Override
    public List<NeteaseBoxComponent> neteaseAabbCollision() {
        return neteaseAabbCollision;
    }

    @Override
    public List<NeteaseBoxComponent> neteaseAabbClip() {
        return neteaseAabbClip;
    }

    @Override
    public boolean neteaseBlockEntity() {
        return neteaseBlockEntity;
    }

    @Override
    public String neteaseTier() {
        return neteaseTier;
    }

    @Override
    public boolean neteaseSolid() {
        return neteaseSolid;
    }

    @Override
    public String neteaseRenderLayer() {
        return neteaseRenderLayer;
    }

    public static class CustomBlockComponentsBuilder implements Builder {
        protected BoxComponent selectionBox;
        protected BoxComponent collisionBox;
        protected String displayName;
        protected GeometryComponent geometry;
        protected final Object2ObjectMap<String, MaterialInstance> materialInstances = new Object2ObjectOpenHashMap<>();
        protected List<PlacementConditions> placementFilter;
        protected Float destructibleByMining;
        protected Float friction;
        protected Integer lightEmission;
        protected Integer lightDampening;
        protected TransformationComponent transformation;
        protected boolean unitCube = false;
        protected boolean placeAir = false;
        protected Set<String> tags = new HashSet<>();

        protected boolean rotatable = false;

        protected Integer neteaseFaceDirectional;
        protected List<NeteaseBoxComponent> neteaseAabbCollision;
        protected List<NeteaseBoxComponent> neteaseAabbClip;
        public boolean neteaseBlockEntity;
        public boolean neteaseSolid = true;
        public String neteaseTier;
        public String neteaseRenderLayer;

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
                throw new IllegalArgumentException("Box bounds must be within (0, 0, 0) and (16, 16, 16). Recieved: (" + minX + ", " + minY + ", " + minZ + ") to (" + maxX + ", " + maxY + ", " + maxZ + ")");
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
                if (box.originX() < -1 || box.originY() < -1 || box.originZ() < -1 || box.sizeX() > 2 || box.sizeY() > 2 || box.sizeZ() > 2) {
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
        public Builder geometry(GeometryComponent geometry) {
            this.geometry = geometry;
            return this;
        }

        @Override
        public Builder materialInstance(@NonNull String name, @NonNull MaterialInstance materialInstance) {
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
        public Builder lightEmission(Integer lightEmission) {
            if (lightEmission != null) {
                if (lightEmission < 0 || lightEmission > 15) {
                    throw new IllegalArgumentException("Light emission must be in the range 0-15");
                }
            }
            this.lightEmission = lightEmission;
            return this;
        }

        @Override
        public Builder lightDampening(Integer lightDampening) {
            if (lightDampening != null) {
                if (lightDampening < 0 || lightDampening > 15) {
                    throw new IllegalArgumentException("Light dampening must be in the range 0-15");
                }
            }
            this.lightDampening = lightDampening;
            return this;
        }

        @Override
        public Builder transformation(TransformationComponent transformation) {
            if (transformation.rx() % 90 != 0 || transformation.ry() % 90 != 0 || transformation.rz() % 90 != 0) {
                throw new IllegalArgumentException("Rotation of transformation must be a multiple of 90 degrees.");
            }
            this.transformation = transformation;
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
        public Builder tags(@Nullable Set<String> tags) {
            this.tags = Objects.requireNonNullElseGet(tags, Set::of);
            return this;
        }

        @Override
        public Builder rotatable(boolean rotatable) {
            this.rotatable = rotatable;
            return this;
        }

        @Override
        public Builder neteaseFaceDirectional(int netease_face_directional) {
            this.neteaseFaceDirectional = netease_face_directional;
            return this;
        }

        @Override
        public Builder neteaseAabbCollision(List<NeteaseBoxComponent> netease_aabb_collision) {
            validateNetEaseBox(netease_aabb_collision);
            this.neteaseAabbCollision = netease_aabb_collision;
            return this;
        }

        @Override
        public Builder neteaseAabbClip(List<NeteaseBoxComponent> netease_aabb_clip) {
            validateNetEaseBox(netease_aabb_clip);
            this.neteaseAabbClip = netease_aabb_clip;
            return this;
        }

        @Override
        public Builder neteaseBlockEntity(boolean netease_block_entity) {
            this.neteaseBlockEntity = netease_block_entity;
            return this;
        }

        @Override
        public Builder neteaseTier(String netease_tier) {
            this.neteaseTier = netease_tier;
            return this;
        }

        @Override
        public Builder neteaseSolid(boolean netease_solid) {
            this.neteaseSolid = netease_solid;
            return this;
        }

        @Override
        public Builder neteaseRenderLayer(String netease_render_layer) {
            this.neteaseRenderLayer = netease_render_layer;
            return this;
        }

        @Override
        public CustomBlockComponents build() {
            return new GeyserCustomBlockComponents(this);
        }
    }
}
