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

        this.writeArray(helper, buffer, packet.getPixels(), ByteBuf::writeIntLE, (buf, aHelper, pixel) -> {
            buf.writeIntLE(pixel.getPixel());
            buf.writeShortLE(pixel.getIndex());
        });
    }


    public <T> void writeArray(BedrockPacketHelper helper, ByteBuf buffer, Collection<T> array, ObjIntConsumer<ByteBuf> lengthWriter,
                               TriConsumer<ByteBuf, BedrockPacketHelper, T> consumer) {
        lengthWriter.accept(buffer, array.size());
        for (T val : array) {
            consumer.accept(buffer, helper, val);
        }
    }

    public <T> void readArray(BedrockPacketHelper helper, ByteBuf buffer, Collection<T> array, ToLongFunction<ByteBuf> lengthReader,
                              BiFunction<ByteBuf, BedrockPacketHelper, T> function) {
        long length = lengthReader.applyAsLong(buffer);
        for (int i = 0; i < length; i++) {
            array.add(function.apply(buffer, helper));
        }
    }


    @Override
    public void deserialize(ByteBuf buffer, BedrockPacketHelper helper, MapInfoRequestPacket packet) {
        super.deserialize(buffer, helper, packet);

        this.readArray(helper, buffer, packet.getPixels(), ByteBuf::readUnsignedIntLE, (buf, aHelper) -> {
            int pixel = buf.readIntLE();
            int index = buf.readUnsignedShortLE();
            return new MapPixel(pixel, index);
        });
    }
}
