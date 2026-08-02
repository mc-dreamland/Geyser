/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.translator.protocol.java.entity;

import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.LivingEntity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.MathUtils;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundMoveVehiclePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;


@Translator(packet = ClientboundTeleportEntityPacket.class)
public class JavaTeleportEntityTranslator extends PacketTranslator<ClientboundTeleportEntityPacket> {

    @Override
    public void translate(GeyserSession session, ClientboundTeleportEntityPacket packet) {
        Entity entity = session.getEntityCache().getEntityByJavaId(packet.getId());

        boolean previouslyRemovedVehicle = false;
        Integer removedVehicleId = session.getPlayerEntity().getRemovedPlayerVehicleId();
        if (entity == null && removedVehicleId != null && removedVehicleId == packet.getId()) {
            entity = session.getPlayerEntity();
            previouslyRemovedVehicle = true;
        }
        if (entity == null) {
            return;
        }

        Vector3f currentBedrockPosition = entity.getPosition();
        Vector3d currentJavaPosition = Vector3d.from(
            currentBedrockPosition.getX(),
            currentBedrockPosition.getY() - entity.getDefinition().offset(),
            currentBedrockPosition.getZ()
        );
        Vector3d position = packet.getPosition().add(
            packet.getRelatives().contains(PositionElement.X) ? currentJavaPosition.getX() : 0,
            packet.getRelatives().contains(PositionElement.Y) ? currentJavaPosition.getY() : 0,
            packet.getRelatives().contains(PositionElement.Z) ? currentJavaPosition.getZ() : 0
        );

        boolean hasRelativePosition = packet.getRelatives().contains(PositionElement.X)
            || packet.getRelatives().contains(PositionElement.Y)
            || packet.getRelatives().contains(PositionElement.Z);
        boolean interpolate = (entity instanceof LivingEntity || hasRelativePosition)
            && currentJavaPosition.distance(position) < 4096.0;

        float newPitch = MathUtils.clamp(packet.getXRot()
            + (packet.getRelatives().contains(PositionElement.X_ROT) ? entity.getPitch() : 0), -90, 90);
        float newYaw = packet.getYRot()
            + (packet.getRelatives().contains(PositionElement.Y_ROT) ? entity.getYaw() : 0);
        float lastPitch = entity.getPitch();
        float lastYaw = entity.getYaw();

        if (interpolate) {
            entity.moveRelative(
                position.getX() - currentJavaPosition.getX(),
                position.getY() - currentJavaPosition.getY(),
                position.getZ() - currentJavaPosition.getZ(),
                newYaw, newPitch, newYaw, packet.isOnGround()
            );
        } else {
            entity.teleport(position.toFloat(), newYaw, newPitch, packet.isOnGround());
        }

        Vector3f deltaMovement = packet.getDeltaMovement().toFloat().add(
            packet.getRelatives().contains(PositionElement.DELTA_X) ? entity.getMotion().getX() : 0,
            packet.getRelatives().contains(PositionElement.DELTA_Y) ? entity.getMotion().getY() : 0,
            packet.getRelatives().contains(PositionElement.DELTA_Z) ? entity.getMotion().getZ() : 0
        );
        if (packet.getRelatives().contains(PositionElement.ROTATE_DELTA)) {
            deltaMovement = MathUtils.xYRot(deltaMovement,
                (float) Math.toRadians(lastPitch - newPitch),
                (float) Math.toRadians(lastYaw - newYaw));
        }

        entity.setMotion(deltaMovement);
        if (deltaMovement.distanceSquared(Vector3f.ZERO) > 1.0E-8F) {
            SetEntityMotionPacket motionPacket = new SetEntityMotionPacket();
            motionPacket.setRuntimeEntityId(entity.getGeyserId());
            motionPacket.setMotion(deltaMovement);
            session.sendUpstreamPacket(motionPacket);
        }

        if (!interpolate && !entity.getPassengers().isEmpty()
            && entity.getPassengers().get(0) == session.getPlayerEntity() && !previouslyRemovedVehicle) {
            session.sendDownstreamGamePacket(new ServerboundMoveVehiclePacket(position, newYaw, newPitch, entity.isOnGround()));
        }

        if (previouslyRemovedVehicle) {
            session.sendDownstreamGamePacket(new ServerboundMovePlayerPosRotPacket(
                false, false, position.getX(), position.getY(), position.getZ(), newYaw, newPitch));
        }
    }
}
