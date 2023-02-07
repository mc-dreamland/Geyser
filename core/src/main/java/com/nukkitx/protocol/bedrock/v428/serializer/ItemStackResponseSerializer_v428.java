package com.nukkitx.protocol.bedrock.v428.serializer;

import com.nukkitx.network.VarInts;
import com.nukkitx.protocol.bedrock.BedrockPacketHelper;
import com.nukkitx.protocol.bedrock.packet.ItemStackResponsePacket;
import com.nukkitx.protocol.bedrock.v422.serializer.ItemStackResponseSerializer_v422;
import io.netty.buffer.ByteBuf;
import org.w3c.dom.html.HTMLBRElement;

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
    }

    @Override
    protected void writeItemEntry(ByteBuf buffer, BedrockPacketHelper helper, ItemStackResponsePacket.ItemEntry itemEntry) {
        buffer.writeByte(itemEntry.getSlot());
        buffer.writeByte(itemEntry.getHotbarSlot());
        buffer.writeByte(itemEntry.getCount());
        VarInts.writeInt(buffer, itemEntry.getStackNetworkId());
        VarInts.writeInt(buffer, itemEntry.getDurabilityCorrection());
        helper.writeString(buffer, itemEntry.getCustomName());
    }
}
