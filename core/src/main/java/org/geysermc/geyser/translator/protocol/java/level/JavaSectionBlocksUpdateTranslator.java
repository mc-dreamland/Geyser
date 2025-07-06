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

package org.geysermc.geyser.translator.protocol.java.level;

import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateSubChunkBlocksPacket;
import org.geysermc.erosion.util.BlockPositionIterator;
import org.geysermc.geyser.entity.type.ItemFrameEntity;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.level.block.type.Block;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.block.type.SkullBlock;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockChangeEntry;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSectionBlocksUpdatePacket;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;

import java.util.BitSet;

@Translator(packet = ClientboundSectionBlocksUpdatePacket.class)
public class JavaSectionBlocksUpdateTranslator extends PacketTranslator<ClientboundSectionBlocksUpdatePacket> {

    private static final int NEIGHBORS_NETWORK_FLAG = (1 << UpdateBlockPacket.Flag.NEIGHBORS.ordinal()) | (1 << UpdateBlockPacket.Flag.NETWORK.ordinal());
    private static final int NETWORK_FLAG = (1 << UpdateBlockPacket.Flag.NETWORK.ordinal());

    @Override
    public void translate(GeyserSession session, ClientboundSectionBlocksUpdatePacket packet) {
        // Send normal block updates if not many changes
        if (packet.getEntries().length < 32) {
            for (BlockChangeEntry entry : packet.getEntries()) {
                session.getWorldCache().updateServerCorrectBlockState(entry.getPosition(), entry.getBlock());
            }
            return;
        }
        UpdateSubChunkBlocksPacket subChunkBlocksPacket = new UpdateSubChunkBlocksPacket();
        subChunkBlocksPacket.setChunkX(packet.getChunkX());
        subChunkBlocksPacket.setChunkY(packet.getChunkY());
        subChunkBlocksPacket.setChunkZ(packet.getChunkZ());

        // If the entire section is updated, this might be a legacy non-full chunk update
        // which can contain thousands of unchanged blocks
        if (packet.getEntries().length == 4096 && !session.getGeyser().getWorldManager().hasOwnChunkCache()) {
            // hack - bedrock might ignore the block updates if the chunk was still loading.
            // sending an UpdateBlockPacket seems to force it
            BlockChangeEntry firstEntry = packet.getEntries()[0];
            UpdateBlockPacket blockPacket = new UpdateBlockPacket();
            blockPacket.setBlockPosition(firstEntry.getPosition());
            blockPacket.setDefinition(session.getBlockMappings().getBedrockBlock(firstEntry.getBlock()));
            blockPacket.setDataLayer(0);
            session.sendUpstreamPacket(blockPacket);

            // Filter out unchanged blocks
            Vector3i offset = Vector3i.from(packet.getChunkX() << 4, packet.getChunkY() << 4, packet.getChunkZ() << 4);
            BlockPositionIterator blockIter = BlockPositionIterator.fromMinMax(
                offset.getX(), offset.getY(), offset.getZ(),
                offset.getX() + 15, offset.getY() + 15, offset.getZ() + 15
            );

            int[] sectionBlocks = session.getGeyser().getWorldManager().getBlocksAt(session, blockIter);
            BitSet waterlogged = BlockRegistries.WATERLOGGED.get();
            for (BlockChangeEntry entry : packet.getEntries()) {
                Vector3i pos = entry.getPosition().sub(offset);
                int index = pos.getZ() + pos.getX() * 16 + pos.getY() * 256;
                int oldBlockState = sectionBlocks[index];
                if (oldBlockState != entry.getBlock()) {
                    // Avoid sending unnecessary waterlogged updates
                    boolean updateWaterlogged = waterlogged.get(oldBlockState) != waterlogged.get(entry.getBlock());
                    applyEntry(session, entry, subChunkBlocksPacket, updateWaterlogged);
                }
            }
        } else {
            for (BlockChangeEntry entry : packet.getEntries()) {
                applyEntry(session, entry, subChunkBlocksPacket, true);
            }
        }

        session.sendUpstreamPacket(subChunkBlocksPacket);
    }

    // Modified version of ChunkUtils#updateBlockClientSide
    private void applyEntry(GeyserSession session, BlockChangeEntry entry, UpdateSubChunkBlocksPacket subChunkBlocksPacket, boolean updateWaterlogged) {
        Vector3i position = entry.getPosition();
        int blockStateId = entry.getBlock();
        BlockState blockState = BlockState.of(blockStateId);

        session.getChunkCache().updateBlock(position.getX(), position.getY(), position.getZ(), blockStateId);

        // Checks for item frames so they aren't tripped up and removed
        ItemFrameEntity itemFrameEntity = ItemFrameEntity.getItemFrameEntity(session, position);
        if (itemFrameEntity != null) {
            if (blockState.is(Blocks.AIR)) { // Item frame is still present and no block overrides that; refresh it
                itemFrameEntity.updateBlock(true);
                // Still update the chunk cache with the new block if updateBlock is called
                return;
            }
            // Otherwise, let's still store our reference to the item frame, but let the new block take precedence for now
        }

        updateBlock(session, blockState, position, subChunkBlocksPacket, updateWaterlogged);
    }


    public void updateBlock(GeyserSession session, BlockState state, Vector3i position, UpdateSubChunkBlocksPacket subChunkBlocksPacket, boolean updateWaterlogged) {
        checkForEmptySkull(session, state, position);

        BlockDefinition definition = session.getBlockMappings().getBedrockBlock(state);
        sendBlockUpdatePacket(session, state, definition, position, subChunkBlocksPacket, updateWaterlogged);

        // Extended collision boxes for custom blocks
        if (!session.getBlockMappings().getExtendedCollisionBoxes().isEmpty()) {
            int aboveBlock = session.getGeyser().getWorldManager().getBlockAt(session, position.getX(), position.getY() + 1, position.getZ());
            BlockDefinition aboveBedrockExtendedCollisionDefinition = session.getBlockMappings().getExtendedCollisionBoxes().get(state.javaId());
            int belowBlock = session.getGeyser().getWorldManager().getBlockAt(session, position.getX(), position.getY() - 1, position.getZ());
            BlockDefinition belowBedrockExtendedCollisionDefinition = session.getBlockMappings().getExtendedCollisionBoxes().get(belowBlock);
            if (belowBedrockExtendedCollisionDefinition != null && state.is(Blocks.AIR)) {
                subChunkBlocksPacket.getStandardBlocks().add(new org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry(
                    position,
                    belowBedrockExtendedCollisionDefinition,
                    NETWORK_FLAG,
                    -1,
                    org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry.MessageType.NONE
                ));
            } else if (aboveBedrockExtendedCollisionDefinition != null && aboveBlock == Block.JAVA_AIR_ID) {
                subChunkBlocksPacket.getStandardBlocks().add(new org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry(
                    position.add(0, 1, 0),
                    aboveBedrockExtendedCollisionDefinition,
                    NETWORK_FLAG,
                    -1,
                    org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry.MessageType.NONE
                ));
            } else if (aboveBlock == Block.JAVA_AIR_ID) {
                subChunkBlocksPacket.getStandardBlocks().add(new org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry(
                    position.add(0, 1, 0),
                    session.getBlockMappings().getBedrockAir(),
                    NETWORK_FLAG,
                    -1,
                    org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry.MessageType.NONE
                ));
            }
        }
    }

    protected void sendBlockUpdatePacket(GeyserSession session, BlockState state, BlockDefinition definition, Vector3i position, UpdateSubChunkBlocksPacket subChunkBlocksPacket, boolean updateWaterlogged) {
        UpdateBlockPacket updateBlockPacket = new UpdateBlockPacket();
        updateBlockPacket.setDataLayer(0);
        updateBlockPacket.setBlockPosition(position);
        updateBlockPacket.setDefinition(definition);
        updateBlockPacket.getFlags().add(UpdateBlockPacket.Flag.NEIGHBORS);
        updateBlockPacket.getFlags().add(UpdateBlockPacket.Flag.NETWORK);
        session.sendUpstreamPacket(updateBlockPacket);

        UpdateBlockPacket waterPacket = new UpdateBlockPacket();
        waterPacket.setDataLayer(1);
        waterPacket.setBlockPosition(position);
        if (BlockRegistries.WATERLOGGED.get().get(state.javaId())) {
            waterPacket.setDefinition(session.getBlockMappings().getBedrockWater());
        } else {
            waterPacket.setDefinition(session.getBlockMappings().getBedrockAir());
        }
        session.sendUpstreamPacket(waterPacket);



        subChunkBlocksPacket.getStandardBlocks().add(new org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry(
            position,
            definition,
            NEIGHBORS_NETWORK_FLAG,
            -1,
            org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry.MessageType.NONE
        ));

        if (updateWaterlogged) {
            BlockDefinition waterDefinition;
            if (BlockRegistries.WATERLOGGED.get().get(state.javaId())) {
                waterDefinition = session.getBlockMappings().getBedrockWater();
            } else {
                waterDefinition = session.getBlockMappings().getBedrockAir();
            }

            subChunkBlocksPacket.getExtraBlocks().add(new org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry(
                position,
                waterDefinition,
                0,
                -1,
                org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry.MessageType.NONE
            ));
        }
    }

    protected void checkForEmptySkull(GeyserSession session, BlockState state, Vector3i position) {
        if (!(state.block() instanceof SkullBlock)) {
            // Skull is gone
            session.getSkullCache().removeSkull(position);
        }
    }
}
