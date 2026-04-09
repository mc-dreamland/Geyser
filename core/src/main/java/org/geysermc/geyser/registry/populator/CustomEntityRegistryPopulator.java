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

package org.geysermc.geyser.registry.populator;

import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.geysermc.geyser.entity.EntityDefinition;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.LivingEntity;
import org.geysermc.geyser.entity.type.living.MobEntity;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.mappings.MappingsConfigReader;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;

import java.util.ArrayList;
import java.util.List;

public class CustomEntityRegistryPopulator {
    private static int rid = 10000;
    private static EntityDefinition<LivingEntity> baseEntity;

    static {
        EntityDefinition<Entity> entityBase = EntityDefinition.builder(Entity::new)
            .addTranslator(MetadataTypes.BYTE, Entity::setFlags)
            .addTranslator(MetadataTypes.INT, Entity::setAir) // Air/bubbles
            .addTranslator(MetadataTypes.OPTIONAL_COMPONENT, Entity::setDisplayName)
            .addTranslator(MetadataTypes.BOOLEAN, Entity::setDisplayNameVisible)
            .addTranslator(MetadataTypes.BOOLEAN, Entity::setSilent)
            .addTranslator(MetadataTypes.BOOLEAN, Entity::setGravity)
            .addTranslator(MetadataTypes.POSE, (entity, entityMetadata) -> entity.setPose(entityMetadata.getValue()))
            .addTranslator(MetadataTypes.INT, Entity::setFreezing)
            .build();

        baseEntity = EntityDefinition.inherited(LivingEntity::new, entityBase)
            .addTranslator(MetadataTypes.BYTE, LivingEntity::setLivingEntityFlags)
            .addTranslator(MetadataTypes.FLOAT, LivingEntity::setHealth)
            .addTranslator(MetadataTypes.INT,
                (livingEntity, entityMetadata) -> livingEntity.getDirtyMetadata().put(EntityDataTypes.EFFECT_COLOR, entityMetadata.getValue()))
            .addTranslator(MetadataTypes.BOOLEAN,
                (livingEntity, entityMetadata) -> livingEntity.getDirtyMetadata().put(EntityDataTypes.EFFECT_AMBIENCE, (byte) (((BooleanEntityMetadata) entityMetadata).getPrimitiveValue() ? 1 : 0)))
            .addTranslator(null) // Arrow count
            .addTranslator(null) // Stinger count
            .addTranslator(MetadataTypes.OPTIONAL_BLOCK_POS, LivingEntity::setBedPosition)
            .build();
//        baseEntity = EntityDefinition.inherited(MobEntity::new, livingEntityBase)
//                .addTranslator(MetadataTypes.BYTE, MobEntity::setMobFlags)
//                .build();

    }

    public static void populate() {
        NbtMap nbtMap = Registries.BEDROCK_ENTITY_IDENTIFIERS.get();

        List<NbtMap> idlist = nbtMap.getList("idlist", NbtType.COMPOUND);
        List<NbtMap> nbtMaps = new ArrayList<>(idlist);
        MappingsConfigReader mappingsConfigReader = new MappingsConfigReader();
        // Load custom entities from mappings files
        mappingsConfigReader.loadEntityMappingsFromJson((key, item) -> {
            rid++;
            NbtMap entityNbt = NbtMap.builder().putInt("rid", rid)
                .putString("id", key)
                .putString("bid", "")
                .putBoolean("hasspawnegg", false)
                .putBoolean("summonable", false)
                .putInt("type", 256).build();
            nbtMaps.add(entityNbt);

            int start = key.indexOf(":");

            String substring = key.substring(start + 1);
            EntityDefinition<MobEntity> mobEntityBase = EntityDefinition.inherited(MobEntity::new, baseEntity)
                .addTranslator(MetadataTypes.BYTE, MobEntity::setMobFlags)
                .identifier(item.identifier())
                .height(item.height())
                .width(item.width())
                .build();

            Registries.CUSTOM_ENTITY_DEFINITIONS.put(substring, mobEntityBase);
        });

        NbtMap entityNbtMaps = NbtMap.builder().putList("idlist", NbtType.COMPOUND, nbtMaps).build();
        Registries.BEDROCK_ENTITY_IDENTIFIERS.set(entityNbtMaps);

    }

}
