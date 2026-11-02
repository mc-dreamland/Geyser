/*
 * Copyright (c) 2026 GeyserMC. http://geysermc.org
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
 */

package org.geysermc.geyser.network.netease;

import lombok.SneakyThrows;
import org.cloudburstmc.protocol.bedrock.packet.NeteasePythonRpcPacket;
import org.msgpack.MessagePack;

public final class NeteaseAttackCooldown {
    private NeteaseAttackCooldown() {
    }

    /**
     * Matches AttackCDShowAPI.sendAttackCooldown; the client animates the supplied duration in seconds.
     */
    @SneakyThrows
    public static NeteasePythonRpcPacket createPacket(double cooldownSeconds) {
        try (var packer = new MessagePack().createBufferPacker()) {
            packer.writeArrayBegin(3);
            packer.write("ModEventS2C");
            packer.writeArrayBegin(4);
            packer.write("AttackCDShow");
            packer.write("AttackCDShowServerSystem");
            packer.write("AttackCooldown");
            packer.writeMapBegin(1);
            packer.write("cooldown");
            packer.write(cooldownSeconds);
            packer.writeMapEnd();
            packer.writeArrayEnd();
            packer.writeNil();
            packer.writeArrayEnd();
            return new NeteasePythonRpcPacket(packer.toByteArray());
        }
    }
}
