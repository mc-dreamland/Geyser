package com.nukkitx.protocol.bedrock.v428.serializer;

import com.nukkitx.network.VarInts;
import com.nukkitx.protocol.bedrock.BedrockPacketHelper;
import com.nukkitx.protocol.bedrock.data.inventory.ContainerSlotType;
import com.nukkitx.protocol.bedrock.packet.ItemStackResponsePacket;
import com.nukkitx.protocol.bedrock.v422.serializer.ItemStackResponseSerializer_v422;
import io.netty.buffer.ByteBuf;
import org.w3c.dom.html.HTMLBRElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ItemStackResponseSerializer_v428 extends ItemStackResponseSerializer_v422 {

    public static final ItemStackResponseSerializer_v428 INSTANCE = new ItemStackResponseSerializer_v428();

    @Override
    protected ItemStackResponsePacket.ItemEntry readItemEntry(ByteBuf buffer, BedrockPacketHelper helper) {
        byte slot = buffer.readByte();
        byte hotbarSlot = buffer.readByte();
        byte count = buffer.readByte();
        int stackNetworkId = VarInts.readInt(buffer);
        int durabilityCorrection = VarInts.readInt(buffer);
        String customName = helper.readString(buffer);
        return new ItemStackResponsePacket.ItemEntry(
                slot,
                hotbarSlot,
                count,
                stackNetworkId,
                customName,
                durabilityCorrection);

//        return new ItemStackResponsePacket.ItemEntry(
//                buffer.readByte(),
//                buffer.readByte(),
//                buffer.readByte(),
//                VarInts.readInt(buffer),
//                helper.readString(buffer),
//                0);
    }

    @Override
    protected void writeItemEntry(ByteBuf buffer, BedrockPacketHelper helper, ItemStackResponsePacket.ItemEntry itemEntry) {
//        super.writeItemEntry(buffer, helper, itemEntry);
//        VarInts.writeInt(buffer, itemEntry.getDurabilityCorrection());
        buffer.writeByte(itemEntry.getSlot());
        buffer.writeByte(itemEntry.getHotbarSlot());
        buffer.writeByte(itemEntry.getCount());
        VarInts.writeInt(buffer, itemEntry.getStackNetworkId());
        VarInts.writeInt(buffer, itemEntry.getDurabilityCorrection());
        helper.writeString(buffer, itemEntry.getCustomName());
    }
}
