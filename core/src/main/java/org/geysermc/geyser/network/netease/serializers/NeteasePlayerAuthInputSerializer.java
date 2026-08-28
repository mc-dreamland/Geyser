/*
 * Copyright (c) 2019-2026 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.network.netease.serializers;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketSerializer;
import org.cloudburstmc.protocol.bedrock.codec.v662.serializer.PlayerAuthInputSerializer_v662;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.LegacySetItemSlotData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.math.BigInteger;
import java.util.Set;

public final class NeteasePlayerAuthInputSerializer {
    private static final int NETEASE_INPUT_BITS = 67; // TODO 每次协议更新记得检查枚举数量是否变化
    private static final PlayerAuthInputData[] NETEASE_WIRE_TO_INPUT = createWireToInputMapping();

    public static final BedrockPacketSerializer<PlayerAuthInputPacket> V819_860 = new PlayerAuthInputSerializer_v662() {
        @Override
        public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, PlayerAuthInputPacket packet) {
            //v388
            float x = buffer.readFloatLE();
            float y = buffer.readFloatLE();
            Vector3f position = helper.readVector3f(buffer);
            packet.setPosition(position);
            packet.setMotion(Vector2f.from(buffer.readFloatLE(), buffer.readFloatLE()));
            float z = buffer.readFloatLE();
            packet.setRotation(Vector3f.from(x, y, z));
            BigInteger flagValue = VarInts.readUnsignedBigVarInt(buffer, NETEASE_INPUT_BITS);

            Set<PlayerAuthInputData> flags = packet.getInputData();
            flags.clear();
            int wireBitsToCheck = Math.min(flagValue.bitLength(), NETEASE_WIRE_TO_INPUT.length);
            for (int wireBit = 0; wireBit < wireBitsToCheck; wireBit++) {
                PlayerAuthInputData input = NETEASE_WIRE_TO_INPUT[wireBit];
                if (input != null && flagValue.testBit(wireBit)) {
                    flags.add(input);
                }
            }
            packet.setInputMode(INPUT_MODES[VarInts.readUnsignedInt(buffer)]);
            packet.setPlayMode(CLIENT_PLAY_MODES[VarInts.readUnsignedInt(buffer)]);
            readInteractionModel(buffer, helper, packet);

            packet.setInteractRotation(helper.readVector2f(buffer));

            //v419
            packet.setTick(VarInts.readUnsignedLong(buffer));
            packet.setDelta(helper.readVector3f(buffer));

            //v428
            //Netease Only Start
            packet.setCameraDeparted(buffer.readBoolean());
            //Netease Only End


            if (packet.getInputData().contains(PlayerAuthInputData.PERFORM_ITEM_INTERACTION)) {
                packet.setItemUseTransaction(this.readItemUseTransaction(buffer, helper));
            }

            if (packet.getInputData().contains(PlayerAuthInputData.PERFORM_ITEM_STACK_REQUEST)) {
                packet.setItemStackRequest(helper.readItemStackRequest(buffer));
            }

            if (packet.getInputData().contains(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS)) {
                helper.readArray(buffer, packet.getPlayerActions(), VarInts::readInt, this::readPlayerBlockActionData, 32); // 32 is more than enough
            }

            //v662
            if (packet.getInputData().contains(PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE)) {
                packet.setVehicleRotation(helper.readVector2f(buffer));
                packet.setPredictedVehicle(VarInts.readLong(buffer));
            }
            packet.setAnalogMoveVector(helper.readVector2f(buffer));
            packet.setCameraOrientation(helper.readVector3f(buffer));
            packet.setRawMoveVector(helper.readVector2f(buffer));

            //Netease Only Start
            packet.setThirdPersonPerspective(buffer.readBoolean());
            packet.setPlayerRotationToCamera(Vector2f.from(buffer.readFloatLE(), buffer.readFloatLE()));
            packet.setReadyPosDetalDirty(buffer.readBoolean());
            packet.setOnGround(buffer.readBoolean());
            packet.setResetPosition(buffer.readByte());

            if (buffer.readableBytes() > 0) {
                System.out.println("异常输入包!");
                System.out.println(buffer.readableBytes());
            }
            //Netease Only End
        }

        @Override
        protected ItemUseTransaction readItemUseTransaction(ByteBuf buffer, BedrockCodecHelper helper) {
            ItemUseTransaction itemTransaction = new ItemUseTransaction();

            int legacyRequestId = VarInts.readInt(buffer);
            itemTransaction.setLegacyRequestId(legacyRequestId);

            if (legacyRequestId < -1 && (legacyRequestId & 1) == 0) {
                helper.readArray(buffer, itemTransaction.getLegacySlots(), (buf, packetHelper) -> {
                    byte containerId = buf.readByte();
                    byte[] slots = packetHelper.readByteArray(buf, 89);
                    return new LegacySetItemSlotData(containerId, slots);
                });
            }

            helper.readInventoryActions(buffer, itemTransaction.getActions());
            itemTransaction.setActionType(VarInts.readUnsignedInt(buffer));
            itemTransaction.setTriggerType(ItemUseTransaction.TriggerType.values()[VarInts.readUnsignedInt(buffer)]);
            itemTransaction.setBlockPosition(helper.readBlockPosition(buffer));
            itemTransaction.setBlockFace(VarInts.readInt(buffer));
            itemTransaction.setHotbarSlot(VarInts.readInt(buffer));
            itemTransaction.setItemInHand(helper.readItem(buffer));
            itemTransaction.setPlayerPosition(helper.readVector3f(buffer));
            itemTransaction.setClickPosition(helper.readVector3f(buffer));
            itemTransaction.setBlockDefinition(helper.getBlockDefinitions().getDefinition(VarInts.readUnsignedInt(buffer)));
            itemTransaction.setClientInteractPrediction(ItemUseTransaction.PredictedResult.values()[VarInts.readUnsignedInt(buffer)]);
            return itemTransaction;
        }
    };

    private static PlayerAuthInputData[] createWireToInputMapping() {
        PlayerAuthInputData[] mapping = new PlayerAuthInputData[NETEASE_INPUT_BITS];
        int stopFlyingOrdinal = PlayerAuthInputData.STOP_FLYING.ordinal();

        // NetEase reserves wire bits 44 and 45, shifting later vanilla flags by two bits.
        for (PlayerAuthInputData input : PlayerAuthInputData.values()) {
            int ordinal = input.ordinal();
            int wireBit = ordinal <= stopFlyingOrdinal ? ordinal : ordinal + 2;
            if (wireBit >= mapping.length) {
                throw new IllegalStateException("Unsupported PlayerAuthInputData ordinal: " + ordinal);
            }
            mapping[wireBit] = input;
        }
        return mapping;
    }

    private NeteasePlayerAuthInputSerializer() {
    }
}
