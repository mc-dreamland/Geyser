/*
 * Copyright (c) 2025 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.command.defaults;

import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkChunkPublisherUpdatePacket;
import org.cloudburstmc.protocol.bedrock.packet.RespawnPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.util.TriState;
import org.geysermc.geyser.command.GeyserCommand;
import org.geysermc.geyser.command.GeyserCommandSource;
import org.geysermc.geyser.session.GeyserSession;
import org.incendo.cloud.context.CommandContext;

public class TestPacketCommand extends GeyserCommand {

    private final GeyserImpl geyser;
    private static final String ARGUMENTS = "args";

    public TestPacketCommand(GeyserImpl geyser, String name, String description, String permission) {
        super(name, description, permission, TriState.NOT_SET);
        this.geyser = geyser;
    }

    @Override
    public void execute(CommandContext<GeyserCommandSource> context) {

        String targetName = "妖猫";
        GeyserSession session = geyser.onlineConnections().stream()
                .filter(connection -> connection.bedrockUsername().equalsIgnoreCase(targetName)
                        || targetName.equalsIgnoreCase(connection.javaUsername()))
                .findFirst()
                .orElse(null);

        if (session != null) {
            System.out.println("??");
//            EntityEventPacket entityEventPacket = new EntityEventPacket();
//            entityEventPacket.setData(0);
//            entityEventPacket.setType(EntityEventType.DEATH);
//            entityEventPacket.setRuntimeEntityId(-10);
//            session.sendUpstreamPacket(entityEventPacket);

            RespawnPacket respawnPacket = new RespawnPacket();
            respawnPacket.setRuntimeEntityId(0); // Bedrock server behavior
            respawnPacket.setPosition(session.getPlayerEntity().position());
            respawnPacket.setState(RespawnPacket.State.SERVER_READY);
            session.sendUpstreamPacket(respawnPacket);

            NetworkChunkPublisherUpdatePacket networkChunkPublisherUpdatePacket = new NetworkChunkPublisherUpdatePacket();
            networkChunkPublisherUpdatePacket.setPosition(Vector3i.from(388, 99, 263));
            networkChunkPublisherUpdatePacket.setRadius(160);
            session.sendUpstreamPacket(networkChunkPublisherUpdatePacket);
            return;
        }
    }
}
