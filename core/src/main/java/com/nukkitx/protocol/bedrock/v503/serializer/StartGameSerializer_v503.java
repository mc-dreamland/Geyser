package com.nukkitx.protocol.bedrock.v503.serializer;

import com.nukkitx.protocol.bedrock.BedrockPacketHelper;
import com.nukkitx.protocol.bedrock.BedrockSession;
import com.nukkitx.protocol.bedrock.packet.StartGamePacket;
import com.nukkitx.protocol.bedrock.v475.serializer.StartGameSerializer_v475;
import io.netty.buffer.ByteBuf;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StartGameSerializer_v503 extends StartGameSerializer_v475 {
    public static final StartGameSerializer_v503 INSTANCE = new StartGameSerializer_v503();


    public void serialize(ByteBuf buffer, BedrockPacketHelper helper, StartGamePacket packet) {
        super.serialize(buffer, helper, packet);


        helper.writeTag(buffer, packet.getPlayerPropertyData());
        buffer.writeLongLE(packet.getBlockRegistryChecksum());
        helper.writeUuid(buffer, packet.getWorldTemplateId());

//        buffer.writeZero(44);
//        helper.writeString(buffer, "00000000-0000-0000-0000-000000000000");
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockPacketHelper helper, StartGamePacket packet, BedrockSession session) {
        super.deserialize(buffer, helper, packet, session);

        packet.setPlayerPropertyData(helper.readTag(buffer));
        packet.setBlockRegistryChecksum(buffer.readLongLE());
        packet.setWorldTemplateId(helper.readUuid(buffer));
    }

    @Override
    protected long readSeed(ByteBuf buf) {
        return buf.readLongLE();
    }

    @Override
    protected void writeSeed(ByteBuf buf, long seed) {
        buf.writeLongLE(seed);
    }
}
