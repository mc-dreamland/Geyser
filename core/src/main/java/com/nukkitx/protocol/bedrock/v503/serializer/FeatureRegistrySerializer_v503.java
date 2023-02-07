package com.nukkitx.protocol.bedrock.v503.serializer;

import com.nukkitx.network.VarInts;
import com.nukkitx.protocol.bedrock.BedrockPacketHelper;
import com.nukkitx.protocol.bedrock.BedrockPacketSerializer;
import com.nukkitx.protocol.bedrock.data.FeatureData;
import com.nukkitx.protocol.bedrock.packet.FeatureRegistryPacket;
import com.nukkitx.protocol.bedrock.util.TriConsumer;
import io.netty.buffer.ByteBuf;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.ObjIntConsumer;
import java.util.function.ToLongFunction;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeatureRegistrySerializer_v503 implements BedrockPacketSerializer<FeatureRegistryPacket> {

    public static final FeatureRegistrySerializer_v503 INSTANCE = new FeatureRegistrySerializer_v503();

    @Override
        public void serialize(ByteBuf buffer, BedrockPacketHelper helper, FeatureRegistryPacket packet) {
        this.writeArray(helper, buffer, packet.getFeatures(), (buf, aHelper, data) -> {
            helper.writeString(buf, data.getName());
            helper.writeString(buf, data.getJson());
        });
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockPacketHelper helper, FeatureRegistryPacket packet) {
        this.readArray(helper, buffer, packet.getFeatures(), (buf, aHelper) -> {
            String name = helper.readString(buf);
            String json = helper.readString(buf);
            return new FeatureData(name, json);
        });
    }


    public <T> void readArray(BedrockPacketHelper helper, ByteBuf buffer, Collection<T> array, BiFunction<ByteBuf, BedrockPacketHelper, T> function) {
        readArray(helper, buffer, array, VarInts::readUnsignedInt, function);
    }



    public <T> void writeArray(BedrockPacketHelper helper, ByteBuf buffer, Collection<T> array, TriConsumer<ByteBuf, BedrockPacketHelper, T> consumer) {
        writeArray(helper, buffer, array, VarInts::writeUnsignedInt, consumer);
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
}
