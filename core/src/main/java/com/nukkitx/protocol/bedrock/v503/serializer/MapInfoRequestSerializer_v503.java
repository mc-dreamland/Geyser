package com.nukkitx.protocol.bedrock.v503.serializer;

import com.nukkitx.protocol.bedrock.BedrockPacketHelper;
import com.nukkitx.protocol.bedrock.data.map.MapPixel;
import com.nukkitx.protocol.bedrock.packet.MapInfoRequestPacket;
import com.nukkitx.protocol.bedrock.util.TriConsumer;
import com.nukkitx.protocol.bedrock.v291.serializer.MapInfoRequestSerializer_v291;
import io.netty.buffer.ByteBuf;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.ObjIntConsumer;
import java.util.function.ToLongFunction;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MapInfoRequestSerializer_v503 extends MapInfoRequestSerializer_v291 {
    public static final MapInfoRequestSerializer_v503 INSTANCE = new MapInfoRequestSerializer_v503();

    @Override
    public void serialize(ByteBuf buffer, BedrockPacketHelper helper, MapInfoRequestPacket packet) {
        super.serialize(buffer, helper, packet);

        helper.writeArray(buffer, packet.getPixels(), ByteBuf::writeIntLE, (buf, aHelper, pixel) -> {
            buf.writeIntLE(pixel.getPixel());
            buf.writeShortLE(pixel.getIndex());
        });
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockPacketHelper helper, MapInfoRequestPacket packet) {
        super.deserialize(buffer, helper, packet);

        helper.readArray(buffer, packet.getPixels(), ByteBuf::readUnsignedIntLE, (buf, aHelper) -> {
            int pixel = buf.readIntLE();
            int index = buf.readUnsignedShortLE();
            return new MapPixel(pixel, index);
        });
    }
}
