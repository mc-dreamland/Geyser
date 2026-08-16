/*
 * Copyright (c) 2019-2024 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.network.netease;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketType;
import org.cloudburstmc.protocol.common.PacketSignal;

/**
 * Sends an entity property event to a NetEase client.
 *
 * <p>Packet ID {@code 0xCB} is used by the client as a generic NetEase event channel. The class
 * keeps its original name for source compatibility with the gravity implementation while also
 * carrying the jump-power and max-auto-step events.</p>
 *
 * <p>The codec accepts this packet in both directions. Serverbound payloads are intentionally
 * ignored.</p>
 */
@Data
@EqualsAndHashCode(doNotUseGetters = true)
@ToString(doNotUseGetters = true)
public final class NeteaseJsonPacket implements BedrockPacket {

    public enum Event {
        SET_ENTITY_GRAVITY("SET_ENTITY_GRAVITY", "gravity"),
        SET_JUMP_POWER("SET_JUMP_POWER", "value"),
        SET_MAX_AUTO_STEP("SET_MAX_AUTO_STEP", "value");

        private final String eventName;
        private final String valueName;

        Event(String eventName, String valueName) {
            this.eventName = eventName;
            this.valueName = valueName;
        }

        public String eventName() {
            return eventName;
        }

        public String valueName() {
            return valueName;
        }
    }

    private long entityId;
    private double gravity;
    private Event event = Event.SET_ENTITY_GRAVITY;

    public NeteaseJsonPacket() {
    }

    public NeteaseJsonPacket(long entityId, double gravity) {
        this(entityId, Event.SET_ENTITY_GRAVITY, gravity);
    }

    private NeteaseJsonPacket(long entityId, Event event, double value) {
        this.entityId = entityId;
        this.event = event;
        this.gravity = value;
    }

    public static NeteaseJsonPacket setJumpPower(long entityId, double jumpPower) {
        return new NeteaseJsonPacket(entityId, Event.SET_JUMP_POWER, jumpPower);
    }

    public static NeteaseJsonPacket setMaxAutoStep(long entityId, double maxAutoStep) {
        return new NeteaseJsonPacket(entityId, Event.SET_MAX_AUTO_STEP, maxAutoStep);
    }

    @Override
    public PacketSignal handle(BedrockPacketHandler handler) {
        // This is a NetEase clientbound extension and has no Cloudburst handler overload.
        return PacketSignal.UNHANDLED;
    }

    @Override
    public BedrockPacketType getPacketType() {
        return BedrockPacketType.NETEASE_CUSTOM;
    }

    @Override
    public NeteaseJsonPacket clone() {
        try {
            return (NeteaseJsonPacket) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}
