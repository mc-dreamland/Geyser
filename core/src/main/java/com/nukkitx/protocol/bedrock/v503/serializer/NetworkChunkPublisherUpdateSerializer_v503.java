package com.nukkitx.protocol.bedrock.v503.serializer;

import com.nukkitx.math.vector.Vector2i;
import com.nukkitx.network.VarInts;
import com.nukkitx.protocol.bedrock.BedrockPacketHelper;
import com.nukkitx.protocol.bedrock.packet.NetworkChunkPublisherUpdatePacket;
import com.nukkitx.protocol.bedrock.util.TriConsumer;
import com.nukkitx.protocol.bedrock.v313.serializer.NetworkChunkPublisherUpdateSerializer_v313;
import io.netty.buffer.ByteBuf;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.ObjIntConsumer;
import java.util.function.ToLongFunction;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NetworkChunkPublisherUpdateSerializer_v503 extends NetworkChunkPublisherUpdateSerializer_v313 {

    public static final NetworkChunkPublisherUpdateSerializer_v503 INSTANCE = new NetworkChunkPublisherUpdateSerializer_v503();

    @Override
    public void serialize(ByteBuf buffer, BedrockPacketHelper helper, NetworkChunkPublisherUpdatePacket packet) {
        super.serialize(buffer, helper, packet);

        writeArray(buffer, packet.getSavedChunks(), ByteBuf::writeIntLE, this::writeSavedChunk, helper);
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockPacketHelper helper, NetworkChunkPublisherUpdatePacket packet) {
        super.deserialize(buffer, helper, packet);
        readArray(buffer, packet.getSavedChunks(), ByteBuf::readIntLE, this::readSavedChunk, helper);

        buffer.readByte();
    }

    protected void writeSavedChunk(ByteBuf buffer, BedrockPacketHelper helper, Vector2i savedChunk) {
        VarInts.writeInt(buffer, savedChunk.getX());
        VarInts.writeInt(buffer, savedChunk.getY());
    }

    protected Vector2i readSavedChunk(ByteBuf buffer, BedrockPacketHelper helper) {
        return Vector2i.from(VarInts.readInt(buffer), VarInts.readInt(buffer));
    }


    public <T> void writeArray(ByteBuf buffer, Collection<T> array, ObjIntConsumer<ByteBuf> lengthWriter,
                               TriConsumer<ByteBuf, BedrockPacketHelper, T> consumer, BedrockPacketHelper helper) {
        lengthWriter.accept(buffer, array.size());
        for (T val : array) {
            consumer.accept(buffer, helper, val);
        }
    }


    public <T> void readArray(ByteBuf buffer, Collection<T> array, ToLongFunction<ByteBuf> lengthReader,
                              BiFunction<ByteBuf, BedrockPacketHelper, T> function, BedrockPacketHelper helper) {
        long length = lengthReader.applyAsLong(buffer);
        for (int i = 0; i < length; i++) {
            array.add(function.apply(buffer, helper));
        }
    }
}
