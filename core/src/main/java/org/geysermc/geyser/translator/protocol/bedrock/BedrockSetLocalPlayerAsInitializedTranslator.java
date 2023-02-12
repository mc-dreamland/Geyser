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

package org.geysermc.geyser.translator.protocol.bedrock;

import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundCustomPayloadPacket;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.nukkitx.protocol.bedrock.data.entity.EntityDataMap;
import com.nukkitx.protocol.bedrock.data.entity.EntityFlag;
import com.nukkitx.protocol.bedrock.data.entity.EntityFlags;
import com.nukkitx.protocol.bedrock.packet.SetEntityDataPacket;
import com.nukkitx.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.geysermc.geyser.api.network.AuthType;
import lombok.SneakyThrows;
import org.geysermc.floodgate.pluginmessage.PluginMessageChannels;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.InventoryUtils;
import org.geysermc.geyser.util.LoginEncryptionUtils;

import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Translator(packet = SetLocalPlayerAsInitializedPacket.class)
public class BedrockSetLocalPlayerAsInitializedTranslator extends PacketTranslator<SetLocalPlayerAsInitializedPacket> {
    @SneakyThrows
    @Override
    public void translate(GeyserSession session, SetLocalPlayerAsInitializedPacket packet) {
        if (session.getPlayerEntity().getGeyserId() == packet.getRuntimeEntityId()) {
            if (!session.getUpstream().isInitialized()) {
                session.getUpstream().setInitialized(true);

                UUID uuid = session.getPlayerEntity().getUuid();
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeInt(packet.getPacketId());
                out.writeUTF(packet.getPacketType().name());
                out.writeUTF(uuid.toString());

//                SetEntityDataPacket entityDataPacket = session.getEntityDataPacket();

                SetEntityDataPacket entityDataPacket = new SetEntityDataPacket();

                entityDataPacket.setRuntimeEntityId(-10);
                entityDataPacket.setTick(0);

                // 后续排查该问题，目前在客户端加载完成后手动发一次。
                EntityFlags flags = new EntityFlags();
                flags.setFlag(EntityFlag.SNEAKING, true);
                flags.setFlag(EntityFlag.CAN_SHOW_NAME, true);
                flags.setFlag(EntityFlag.CAN_CLIMB, true);
                flags.setFlag(EntityFlag.HAS_COLLISION, true);
                flags.setFlag(EntityFlag.HAS_GRAVITY, true);
                entityDataPacket.getMetadata().putFlags(flags);
                session.sendUpstreamPacket(entityDataPacket);
                ScheduledFuture<?> scheduledFuture = session.scheduleInEventLoop(new Runnable() {
                    @Override
                    public void run() {
                        flags.setFlag(EntityFlag.SNEAKING, false);
                        entityDataPacket.getMetadata().putFlags(flags);
                        session.sendUpstreamPacket(entityDataPacket);

                    }
                }, 2, TimeUnit.SECONDS);

                session.sendDownstreamPacket(new ServerboundCustomPayloadPacket(PluginMessageChannels.CUSTOM, out.toByteArray()));

                if (session.remoteServer().authType() == AuthType.ONLINE) {
                    if (!session.isLoggedIn()) {
                        if (session.getGeyser().getConfig().getSavedUserLogins().contains(session.bedrockUsername())) {
                            if (session.getGeyser().refreshTokenFor(session.bedrockUsername()) == null) {
                                LoginEncryptionUtils.buildAndShowConsentWindow(session);
                            } else {
                                // If the refresh token is not null and we're here, then the refresh token expired
                                // and the expiration form has been cached
                                session.getFormCache().resendAllForms();
                            }
                        } else {
                            LoginEncryptionUtils.buildAndShowLoginWindow(session);
                        }
                    }
                    // else we were able to log the user in
                }
                if (session.isLoggedIn()) {
                    // Sigh - as of Bedrock 1.18
                    session.getEntityCache().updateBossBars();

                    // Double sigh - https://github.com/GeyserMC/Geyser/issues/2677 - as of Bedrock 1.18
                    if (session.getOpenInventory() != null && session.getOpenInventory().isPending()) {
                        InventoryUtils.openInventory(session, session.getOpenInventory());
                    }

                    // What am I to expect - as of Bedrock 1.18
                    session.getFormCache().resendAllForms();
                }
            }
        }
    }
}
