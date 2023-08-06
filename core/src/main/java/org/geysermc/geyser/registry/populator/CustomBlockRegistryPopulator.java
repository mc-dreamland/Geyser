package org.geysermc.geyser.registry.populator;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.codec.v582.Bedrock_v582;
import org.cloudburstmc.protocol.bedrock.codec.v594.Bedrock_v594;
import org.cloudburstmc.protocol.bedrock.data.BlockPropertyData;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.CustomBlockPermutation;
import org.geysermc.geyser.api.block.custom.CustomBlockState;
import org.geysermc.geyser.api.block.custom.component.*;
import org.geysermc.geyser.api.block.custom.component.PlacementConditions.Face;
import org.geysermc.geyser.api.block.custom.nonvanilla.JavaBlockItem;
import org.geysermc.geyser.api.block.custom.nonvanilla.JavaBlockState;
import org.geysermc.geyser.api.block.custom.property.CustomBlockProperty;
import org.geysermc.geyser.api.block.custom.property.PropertyType;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;
import org.geysermc.geyser.level.block.GeyserCustomBlockState;
import org.geysermc.geyser.level.block.GeyserCustomBlockComponents.CustomBlockComponentsBuilder;
import org.geysermc.geyser.level.block.GeyserCustomBlockData.CustomBlockDataBuilder;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.mappings.MappingsConfigReader;
import org.geysermc.geyser.registry.type.CustomSkull;
import org.geysermc.geyser.util.MathUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CustomBlockRegistryPopulator {

    /**
     * Registers all custom blocks defined by extensions and user supplied mappings
     */
    public static void populate() {
        if (!GeyserImpl.getInstance().getConfig().isAddNonBedrockItems()) {
            return;
        }
        Set<String> customBlockNames = new ObjectOpenHashSet<>();
        Set<CustomBlockData> customBlocks = new ObjectOpenHashSet<>();
        Int2ObjectMap<CustomBlockState> blockStateOverrides = new Int2ObjectOpenHashMap<>();
        Map<JavaBlockState, CustomBlockState> nonVanillaBlockStateOverrides = new HashMap<>();
        Map<String, CustomBlockData> customBlockItemOverrides = new HashMap<>();
        Map<String, CustomBlockData> customBlockHeadOverrides = new HashMap<>();
        Map<JavaBlockItem, CustomBlockData> nonVanillaCustomItemOverrides = new HashMap<>();

        GeyserImpl.getInstance().getEventBus().fire(new GeyserDefineCustomBlocksEvent() {
            @Override
            public void register(@NonNull CustomBlockData customBlockData) {
                if (customBlockData.name().length() == 0) {
                    throw new IllegalArgumentException("Custom block name must have at least 1 character.");
                }
                if (!customBlockNames.add(customBlockData.name())) {
                    throw new IllegalArgumentException("Another custom block was already registered under the name: " + customBlockData.name());
                }
                if (Character.isDigit(customBlockData.name().charAt(0))) {
                    throw new IllegalArgumentException("Custom block can not start with a digit. Name: " + customBlockData.name());
                }
                customBlocks.add(customBlockData);
            }
    
            @Override
            public void registerOverride(@NonNull String javaIdentifier, @NonNull CustomBlockState customBlockState) {
                int id = BlockRegistries.JAVA_IDENTIFIER_TO_ID.getOrDefault(javaIdentifier, -1);
                if (id == -1) {
                    throw new IllegalArgumentException("Unknown Java block state. Identifier: " + javaIdentifier);
                }
                if (!customBlocks.contains(customBlockState.block())) {
                    throw new IllegalArgumentException("Custom block is unregistered. Name: " + customBlockState.name());
                }
                CustomBlockState oldBlockState = blockStateOverrides.put(id, customBlockState);
                if (oldBlockState != null) {
                    GeyserImpl.getInstance().getLogger().debug("Duplicate block state override for Java Identifier: " +
                            javaIdentifier + " Old override: " + oldBlockState.name() + " New override: " + customBlockState.name());
                }
            }

            @Override
            public void registerOverride(@NonNull JavaBlockState javaBlockState, @NonNull CustomBlockState customBlockState) {
                if (!customBlocks.contains(customBlockState.block())) {
                    throw new IllegalArgumentException("Custom block is unregistered. Name: " + customBlockState.name());
                }
                nonVanillaBlockStateOverrides.put(javaBlockState, customBlockState);
            }

            @Override
            public void registerItemOverride(@NonNull String javaIdentifier, @NonNull CustomBlockData customBlockData) {
                if (!customBlocks.contains(customBlockData)) {
                    throw new IllegalArgumentException("Custom block is unregistered. Name: " + customBlockData.name());
                }
                customBlockItemOverrides.put(javaIdentifier, customBlockData);
            }

            @Override
            public void registerItemOverride(@NonNull JavaBlockItem javaBlockItem, @NonNull CustomBlockData customBlockData) {
                if (!customBlocks.contains(customBlockData)) {
                    throw new IllegalArgumentException("Custom block is unregistered. Name: " + customBlockData.name());
                }
                nonVanillaCustomItemOverrides.put(javaBlockItem, customBlockData);
            }

            @Override
            public void registerBlockHeadOverride(@NonNull String headOwnerName, @NonNull CustomBlockData customBlockData) {
                if (!customBlocks.contains(customBlockData)) {
                    throw new IllegalArgumentException("Custom block is unregistered. Name: " + customBlockData.name());
                }
                customBlockHeadOverrides.put(headOwnerName, customBlockData);
            }
        });
    
        for (CustomSkull customSkull : BlockRegistries.CUSTOM_SKULLS.get().values()) {
            customBlocks.add(customSkull.getCustomBlockData());
        }

        Map<CustomBlockData, Set<Integer>> extendedCollisionBoxes = new HashMap<>();
        Map<BoxComponent, CustomBlockData> extendedCollisionBoxSet = new HashMap<>();
        MappingsConfigReader mappingsConfigReader = new MappingsConfigReader();
        mappingsConfigReader.loadBlockMappingsFromJson((key, block) -> {
            customBlocks.add(block.data());
            if (block.overrideItem() && !block.replaceSkull()) {
                customBlockItemOverrides.put(block.javaIdentifier(), block.data());
            }
            if (block.replaceSkull()) {
                customBlockHeadOverrides.put(block.javaIdentifier(), block.data());
            }
            block.states().forEach((javaIdentifier, customBlockState) -> {
                int id = BlockRegistries.JAVA_IDENTIFIER_TO_ID.getOrDefault(javaIdentifier, -1);
                blockStateOverrides.put(id, customBlockState.state());
                BoxComponent extendedCollisionBox = customBlockState.extendedCollisionBox();
                if (extendedCollisionBox != null) {
                    CustomBlockData extendedCollisionBlock = extendedCollisionBoxSet.computeIfAbsent(extendedCollisionBox, box -> {
                        CustomBlockData collisionBlock = createExtendedCollisionBlock(box, extendedCollisionBoxSet.size());
                        customBlocks.add(collisionBlock);
                        return collisionBlock;
                    });
                    extendedCollisionBoxes.computeIfAbsent(extendedCollisionBlock, k -> new HashSet<>())
                            .add(id);
                }
            });
        });
    
        BlockRegistries.CUSTOM_BLOCKS.set(customBlocks.toArray(new CustomBlockData[0]));
        GeyserImpl.getInstance().getLogger().info("Registered " + customBlocks.size() + " custom blocks.");
    
        BlockRegistries.CUSTOM_BLOCK_STATE_OVERRIDES.set(blockStateOverrides);
        GeyserImpl.getInstance().getLogger().info("Registered " + blockStateOverrides.size() + " custom block overrides.");

        BlockRegistries.NON_VANILLA_BLOCK_STATE_OVERRIDES.set(nonVanillaBlockStateOverrides);
        GeyserImpl.getInstance().getLogger().info("Registered " + nonVanillaBlockStateOverrides.size() + " non-vanilla block overrides.");

        BlockRegistries.CUSTOM_BLOCK_ITEM_OVERRIDES.set(customBlockItemOverrides);
        GeyserImpl.getInstance().getLogger().info("Registered " + customBlockItemOverrides.size() + " custom block item overrides.");

        BlockRegistries.NON_VANILLA_CUSTOM_BLOCK_ITEM_OVERRIDES.set(nonVanillaCustomItemOverrides);
        GeyserImpl.getInstance().getLogger().info("Registered " + nonVanillaCustomItemOverrides.size() + " non-vanilla custom item overrides.");

        BlockRegistries.EXTENDED_COLLISION_BOXES.set(extendedCollisionBoxes);
        GeyserImpl.getInstance().getLogger().info("Registered " + extendedCollisionBoxes.size() + " custom block extended collision boxes.");

        BlockRegistries.CUSTOM_BLOCK_HEAD_OVERRIDES.set(customBlockHeadOverrides);
        GeyserImpl.getInstance().getLogger().info("Registered " + customBlockHeadOverrides.size() + " custom block head overrides.");
    }

    /**
     * Generates and appends all custom block states to the provided list of custom block states
     * Appends the custom block states to the provided list of NBT maps
     * @param customBlock the custom block data to generate states for
     * @param blockStates the list of NBT maps to append the custom block states to
     * @param customExtBlockStates the list of custom block states to append the custom block states to 
     * @param stateVersion the state version to use for the custom block states
     */
    static void generateCustomBlockStates(CustomBlockData customBlock, List<NbtMap> blockStates, List<CustomBlockState> customExtBlockStates, int stateVersion) {
        int totalPermutations = 1;
        for (CustomBlockProperty<?> property : customBlock.properties().values()) {
            totalPermutations *= property.values().size();
        }
    
        for (int i = 0; i < totalPermutations; i++) {
            NbtMapBuilder statesBuilder = NbtMap.builder();
            int permIndex = i;
            for (CustomBlockProperty<?> property : customBlock.properties().values()) {
                statesBuilder.put(property.name(), property.values().get(permIndex % property.values().size()));
                permIndex /= property.values().size();
            }
            NbtMap states = statesBuilder.build();
    
            blockStates.add(NbtMap.builder()
                    .putString("name", customBlock.identifier())
                    .putInt("version", stateVersion)
                    .putCompound("states", states)
                    .build());
            customExtBlockStates.add(new GeyserCustomBlockState(customBlock, states));
        }
    }

    /**
     * Generates and returns the block property data for the provided custom block
     * @param customBlock the custom block to generate block property data for
     * @param protocolVersion the protocol version to use for the block property data
     * @return the block property data for the provided custom block
     */
    @SuppressWarnings("unchecked")
    static BlockPropertyData generateBlockPropertyData(CustomBlockData customBlock, int protocolVersion) {
        List<NbtMap> permutations = new ArrayList<>();
        for (CustomBlockPermutation permutation : customBlock.permutations()) {
            permutations.add(NbtMap.builder()
                    .putCompound("components", CustomBlockRegistryPopulator.convertComponents(permutation.components(), protocolVersion))
                    .putString("condition", permutation.condition())
                    .build());
        }
    
        // The order that properties are defined influences the order that block states are generated
        List<NbtMap> properties = new ArrayList<>();
        for (CustomBlockProperty<?> property : customBlock.properties().values()) {
            NbtMapBuilder propertyBuilder = NbtMap.builder()
                    .putString("name", property.name());
            if (property.type() == PropertyType.booleanProp()) {
                propertyBuilder.putList("enum", NbtType.BYTE, List.of((byte) 0, (byte) 1));
            } else if (property.type() == PropertyType.integerProp()) {
                propertyBuilder.putList("enum", NbtType.INT, (List<Integer>) property.values());
            } else if (property.type() == PropertyType.stringProp()) {
                propertyBuilder.putList("enum", NbtType.STRING, (List<String>) property.values());
            }
            properties.add(propertyBuilder.build());
        }
    
        String creativeCategory = customBlock.creativeCategory() != null ? customBlock.creativeCategory() : "none";
        String creativeGroup = customBlock.creativeGroup() != null ? customBlock.creativeGroup() : "";
        NbtMap propertyTag = NbtMap.builder()
                .putCompound("components", CustomBlockRegistryPopulator.convertComponents(customBlock.components(), protocolVersion))
                // this is required or the client will crash
                // in the future, this can be used to replace items in the creative inventory
                // this would require us to map https://wiki.bedrock.dev/documentation/creative-categories.html#for-blocks programatically
                .putCompound("menu_category", NbtMap.builder()
                    .putString("category", creativeCategory)
                    .putString("group", creativeGroup)
                    .putBoolean("is_hidden_in_commands", false)
                .build())
                // meaning of this version is unknown, but it's required for tags to work and should probably be checked periodically
                .putInt("molangVersion", 1)
                .putList("permutations", NbtType.COMPOUND, permutations)
                .putList("properties", NbtType.COMPOUND, properties)
                .build();
        return new BlockPropertyData(customBlock.identifier(), propertyTag);
    }

    /**
     * Converts the provided custom block components to an {@link NbtMap} to be sent to the client in the StartGame packet
     * @param components the custom block components to convert
     * @param protocolVersion the protocol version to use for the conversion
     * @return the NBT representation of the provided custom block components
     */
    static NbtMap convertComponents(CustomBlockComponents components, int protocolVersion) {
        if (components == null) {
            return NbtMap.EMPTY;
        }
        NbtMapBuilder builder = NbtMap.builder();
        if (components.displayName() != null) {
            builder.putCompound("minecraft:display_name", NbtMap.builder()
                    .putString("value", components.displayName())
                    .build());
        }
        if (components.selectionBox() != null) {
            builder.putCompound("minecraft:selection_box", convertBox(components.selectionBox()));
        }
        if (components.collisionBox() != null) {
            builder.putCompound("minecraft:collision_box", convertBox(components.collisionBox()));
        }
        if (components.geometry() != null) {
            NbtMapBuilder geometryBuilder = NbtMap.builder();
            if (protocolVersion >= Bedrock_v594.CODEC.getProtocolVersion()) {
                geometryBuilder.putString("identifier", components.geometry());
            } else {
                geometryBuilder.putString("value", components.geometry());
            }
            builder.putCompound("minecraft:geometry", geometryBuilder.build());
        }
        if (!components.materialInstances().isEmpty()) {
            NbtMapBuilder materialsBuilder = NbtMap.builder();
            for (Map.Entry<String, MaterialInstance> entry : components.materialInstances().entrySet()) {
                MaterialInstance materialInstance = entry.getValue();
                materialsBuilder.putCompound(entry.getKey(), NbtMap.builder()
                        .putString("texture", materialInstance.texture())
                        .putString("render_method", materialInstance.renderMethod())
                        .putBoolean("face_dimming", materialInstance.faceDimming())
                        .putBoolean("ambient_occlusion", materialInstance.faceDimming())
                        .build());
            }
            builder.putCompound("minecraft:material_instances", NbtMap.builder()
                    // we could read these, but there is no functional reason to use them at the moment
                    // they only allow you to make aliases for material instances
                    // but you could already just define the same instance twice if this was really needed
                    .putCompound("mappings", NbtMap.EMPTY)
                    .putCompound("materials", materialsBuilder.build())
                    .build());
        }
        if (components.placementFilter() != null) {
            builder.putCompound("minecraft:placement_filter", NbtMap.builder()
                    .putList("conditions", NbtType.COMPOUND, convertPlacementFilter(components.placementFilter()))
                    .build());
        }
        if (components.destructibleByMining() != null) {
            builder.putCompound("minecraft:destructible_by_mining", NbtMap.builder()
                    .putFloat("value", components.destructibleByMining())
                    .build());
        }
        if (components.friction() != null) {
            builder.putCompound("minecraft:friction", NbtMap.builder()
                    .putFloat("value", components.friction())
                    .build());
        }
        if (components.lightEmission() != null) {
            builder.putCompound("minecraft:light_emission", NbtMap.builder()
                    .putByte("emission", components.lightEmission().byteValue())
                    .build());
            builder.putCompound("minecraft:block_light_emission", NbtMap.builder()
                    .putFloat("emission", components.lightEmission().floatValue())
                    .build());
        }
        if (components.lightDampening() != null) {
            builder.putCompound("minecraft:light_dampening", NbtMap.builder()
                    .putByte("lightLevel", (components.lightDampening()).byteValue())
                    .build());
            builder.putCompound("minecraft:block_light_filter", NbtMap.builder()
                    .putByte("lightLevel", components.lightDampening().byteValue())
                    .build());
        }
        // TODO: remove this protocol check when 1.19.80 becomes required w/ Java 1.20
        if (components.transformation() != null && protocolVersion >= Bedrock_v582.CODEC.getProtocolVersion()) {
            builder.putCompound("minecraft:transformation", NbtMap.builder()
                    .putInt("RX", MathUtils.unwrapDegreesToInt(components.transformation().rx()) / 90)
                    .putInt("RY", MathUtils.unwrapDegreesToInt(components.transformation().ry()) / 90)
                    .putInt("RZ", MathUtils.unwrapDegreesToInt(components.transformation().rz()) / 90)
                    .putFloat("SX", components.transformation().sx())
                    .putFloat("SY", components.transformation().sy())
                    .putFloat("SZ", components.transformation().sz())
                    .putFloat("TX", components.transformation().tx())
                    .putFloat("TY", components.transformation().ty())
                    .putFloat("TZ", components.transformation().tz())
                    .build());
        }
        if (components.unitCube()) {
            builder.putCompound("minecraft:unit_cube", NbtMap.EMPTY);
        }
        // place_air is not an actual component
        // We just apply a dummy event to prevent the client from trying to place a block
        // This mitigates the issue with the client sometimes double placing blocks
        if (components.placeAir()) {
            builder.putCompound("minecraft:on_player_placing", NbtMap.builder()
                    .putString("triggerType", "geyser:place_event")
                    .build());
        }

        if (components.destoryTime() != null) {
            builder.putCompound("minecraft:destroy_time", NbtMap.builder()
                    .putFloat("value", components.destoryTime())
                    .build());
        }

        if (components.neteaseFaceDirectional() != null) {
            builder.putCompound("netease:face_directional", NbtMap.builder()
                    .putByte("direction", components.neteaseFaceDirectional().byteValue())
                    .build());
        }
        if (components.neteaseAabbCollision() != null || components.neteaseAabbClip() != null) {
            NbtMapBuilder endMap = NbtMap.builder();
            List<NeteaseBoxComponent> collision = components.neteaseAabbCollision();
            List<NeteaseBoxComponent> clip = components.neteaseAabbClip();

            List<NbtMap> collisionNbtList = new ArrayList<>();
            List<NbtMap> clipNbtList = new ArrayList<>();


            for (NeteaseBoxComponent boxComponent : collision) {
                NbtMap build = NbtMap.builder()
                        .putString("enable", boxComponent.molang())
                        .putList("aabb", NbtType.FLOAT, boxComponent.originX(), boxComponent.originY(), boxComponent.originZ(), boxComponent.sizeX(), boxComponent.sizeY(), boxComponent.sizeZ())
                        .build();

                collisionNbtList.add(build);
            }
            for (NeteaseBoxComponent boxComponent : clip) {
                clipNbtList.add(NbtMap.builder()
                        .putString("enable", boxComponent.molang())
                        .putList("aabb", NbtType.FLOAT, boxComponent.originX(), boxComponent.originY(), boxComponent.originZ(), boxComponent.sizeX(), boxComponent.sizeY(), boxComponent.sizeZ())
                        .build());
            }
            endMap.putList("clip", NbtType.COMPOUND, clipNbtList).putList("collision", NbtType.COMPOUND, collisionNbtList);
            builder.putCompound("netease:aabb", endMap.build());
        }

        if (components.neteaseBlockEntity()) {
            builder.putCompound("netease:block_entity", NbtMap.builder()
                    .putBoolean("movable", false)
                    .putBoolean("tick", false)
                    .build()
            );
        }

        builder.putCompound("netease:solid", NbtMap.builder()
                .putBoolean("value", components.neteaseSolid())
                .build()
        );

        if (components.neteaseTier() != null) {
            builder.putCompound("netease:tier", NbtMap.builder()
                    .putString("digger", components.neteaseTier())
                    .build()
            );
        }

        if (components.neteaseRenderLayer() != null) {
            builder.putCompound("netease:render_layer", NbtMap.builder()
                    .putString("value", components.neteaseRenderLayer())
                    .build()
            );
        }
        if (components.neteaseLightEmission() != null) {
            builder.putCompound("minecraft:block_light_emission", NbtMap.builder()
                    .putFloat("emission", components.neteaseLightEmission())
                    .build());
        }
        if (components.neteaseLightDampening() != null) {
            builder.putCompound("minecraft:block_light_filter", NbtMap.builder()
                    .putFloat("lightLevel", components.neteaseLightDampening())
                    .build());
        }

        if (!components.tags().isEmpty()) {
            components.tags().forEach(tag -> builder.putCompound("tag:" + tag, NbtMap.EMPTY));
        }
        return builder.build();
    }

    /**
     * Converts the provided box component to an {@link NbtMap}
     * @param boxComponent the box component to convert
     * @return the NBT representation of the provided box component
     */
    private static NbtMap convertBox(BoxComponent boxComponent) {
        return NbtMap.builder()
                .putBoolean("enabled", !boxComponent.isEmpty())
                .putList("origin", NbtType.FLOAT, boxComponent.originX(), boxComponent.originY(), boxComponent.originZ())
                .putList("size", NbtType.FLOAT, boxComponent.sizeX(), boxComponent.sizeY(), boxComponent.sizeZ())
                .build();
    }

    /**
     * Converts the provided placement filter to a list of {@link NbtMap}
     * @param placementFilter the placement filter to convert
     * @return the NBT representation of the provided placement filter
     */
    private static List<NbtMap> convertPlacementFilter(List<PlacementConditions> placementFilter) {
        List<NbtMap> conditions = new ArrayList<>();
        placementFilter.forEach((condition) -> {
            NbtMapBuilder conditionBuilder = NbtMap.builder();

            // allowed_faces on the network is represented by 6 bits for the 6 possible faces
            // the enum has the proper values for that face only, so we just bitwise OR them together
            byte allowedFaces = 0;
            for (Face face : condition.allowedFaces()) { allowedFaces |= (1 << face.ordinal()); }
            conditionBuilder.putByte("allowed_faces", allowedFaces);

            // block_filters is a list of either blocks or queries for block tags
            // if these match the block the player is trying to place on, the placement is allowed by the client
            List <NbtMap> blockFilters = new ArrayList<>();
            condition.blockFilters().forEach((value, type) -> {
                NbtMapBuilder blockFilterBuilder = NbtMap.builder();
                switch (type) {
                    case BLOCK -> blockFilterBuilder.putString("name", value);
                    // meaning of this version is unknown, but it's required for tags to work and should probably be checked periodically
                    case TAG -> blockFilterBuilder.putString("tags", value).putInt("tags_version", 6);
                }
                blockFilters.add(blockFilterBuilder.build());
            });
            conditionBuilder.putList("block_filters", NbtType.COMPOUND, blockFilters);
            conditions.add(conditionBuilder.build());
        });

        return conditions;
    }

    private static CustomBlockData createExtendedCollisionBlock(BoxComponent boxComponent, int extendedCollisionBlock) {
        CustomBlockData customBlockData = new CustomBlockDataBuilder()
                .name("extended_collision_" + extendedCollisionBlock)
                .components(
                    new CustomBlockComponentsBuilder()
                        .collisionBox(boxComponent)
                        .selectionBox(BoxComponent.emptyBox())
                        .materialInstance("*", new MaterialInstance("glass", "alpha_test", false, false))
                        .lightDampening(0)
                        .geometry("geometry.invisible")
                        .build())
                .build();
        return customBlockData;
    }
}
