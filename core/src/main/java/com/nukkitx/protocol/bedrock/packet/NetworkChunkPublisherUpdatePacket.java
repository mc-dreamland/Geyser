package com.nukkitx.protocol.bedrock.packet;

import com.nukkitx.math.vector.Vector2i;
import com.nukkitx.math.vector.Vector3i;
import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.BedrockPacketType;
import com.nukkitx.protocol.bedrock.handler.BedrockPacketHandler;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(doNotUseGetters = true, callSuper = false)
public class NetworkChunkPublisherUpdatePacket extends BedrockPacket {
    private Vector3i position;
    private int radius;
    private final List<Vector2i> savedChunks = new ObjectArrayList<>();

    @Override
    public boolean handle(BedrockPacketHandler handler) {
        return handler.handle(this);
    }

    public BedrockPacketType getPacketType() {
        return BedrockPacketType.NETWORK_CHUNK_PUBLISHER_UPDATE;
    }
}
