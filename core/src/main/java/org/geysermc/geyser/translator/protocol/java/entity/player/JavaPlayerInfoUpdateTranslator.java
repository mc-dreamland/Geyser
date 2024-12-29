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

package org.geysermc.geyser.translator.protocol.java.entity.player;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.protocol.data.game.PlayerListEntry;
import com.github.steveice10.mc.protocol.data.game.PlayerListEntryAction;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundPlayerInfoUpdatePacket;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.ConfirmSkinPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.entity.type.player.PlayerEntity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.skin.SkinManager;
import org.geysermc.geyser.skin.SkinProvider;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Translator(packet = ClientboundPlayerInfoUpdatePacket.class)
public class JavaPlayerInfoUpdateTranslator extends PacketTranslator<ClientboundPlayerInfoUpdatePacket> {
    @Override
    public void translate(GeyserSession session, ClientboundPlayerInfoUpdatePacket packet) {
        Set<PlayerListEntryAction> actions = packet.getActions();

        if (actions.contains(PlayerListEntryAction.ADD_PLAYER)) {
            for (PlayerListEntry entry : packet.getEntries()) {
                @Nullable GameProfile profile = entry.getProfile();

                UUID id = entry.getProfileId();
                String name = null;
                String texturesProperty = null;

                if (profile != null) {
                    name = profile.getName();

                    GameProfile.Property textures = profile.getProperty("textures");
                    if (textures != null) {
                        texturesProperty = textures.getValue();
                    }
                }

                boolean self = id.equals(session.getPlayerEntity().getUuid());

                PlayerEntity playerEntity;
                if (self) {
                    // Entity is ourself
                    playerEntity = session.getPlayerEntity();
                } else {
                    // It's a new player
                    playerEntity = new PlayerEntity(
                            session,
                            -1,
                            session.getEntityCache().getNextEntityId().incrementAndGet(),
                            id,
                            Vector3f.ZERO,
                            Vector3f.ZERO,
                            0, 0, 0,
                            name,
                            texturesProperty
                    );

                    session.getEntityCache().addPlayerEntity(playerEntity);
                }
                playerEntity.setUsername(name);
                playerEntity.setTexturesProperty(texturesProperty);

                if (self) {
                    SkinManager.requestAndHandleSkinAndCape(playerEntity, session, skinAndCape ->
                            GeyserImpl.getInstance().getLogger().debug("Loaded Local Bedrock Java Skin Data for " + session.getClientData().getUsername()));
                } else {
                    playerEntity.setValid(true);
                }
            }
        }

        if (actions.contains(PlayerListEntryAction.UPDATE_LISTED)) {
            List<PlayerListPacket.Entry> toAdd = new ArrayList<>();
            List<PlayerListPacket.Entry> toRemove = new ArrayList<>();

            for (PlayerListEntry entry : packet.getEntries()) {
                PlayerEntity entity = session.getEntityCache().getPlayerEntity(entry.getProfileId());
                if (entity == null) {
                    session.getGeyser().getLogger().debug("Ignoring player info update for " + entry.getProfileId());
                    continue;
                }

                if (entry.isListed()) {
                    sendAddPlayerList(session, entity);
                } else {
                    toRemove.add(new PlayerListPacket.Entry(entity.getTabListUuid()));
                }
            }

            if (!toAdd.isEmpty()) {
                PlayerListPacket tabListPacket = new PlayerListPacket();
                tabListPacket.setAction(PlayerListPacket.Action.ADD);
                tabListPacket.getEntries().addAll(toAdd);
                session.sendUpstreamPacket(tabListPacket);
            }
            if (!toRemove.isEmpty()) {
                PlayerListPacket tabListPacket = new PlayerListPacket();
                tabListPacket.setAction(PlayerListPacket.Action.REMOVE);
                tabListPacket.getEntries().addAll(toRemove);
                session.sendUpstreamPacket(tabListPacket);
            }
        }
    }

    private void sendAddPlayerList(GeyserSession session, PlayerEntity entity) {
        SkinProvider.requestSkinData(entity).whenCompleteAsync((skinData, throwable) -> {

            PlayerListPacket.Entry updatedEntry = SkinManager.buildEntryManually(
                    session,
                    entity.getUuid(),
                    entity.getUsername(),
                    entity.getGeyserId(),
                    skinData.skin(),
                    skinData.cape(),
                    skinData.geometry()
            );

            if (session.getCachedPlayerList().containsKey(entity.getUuid())) {
                if (session.getCachedPlayerList().get(entity.getUuid()).equals(updatedEntry.getSkin().getFullSkinId())) {
                    return;
                }
            }

            PlayerListPacket playerAddPacket = new PlayerListPacket();
            playerAddPacket.setAction(PlayerListPacket.Action.ADD);
            playerAddPacket.getEntries().add(updatedEntry);
            session.sendUpstreamPacket(playerAddPacket);
            //TODO 后续需修改此判断以正确的判断是否是玩家还是NPC
            session.getCachedPlayerList().put(entity.getUuid(), updatedEntry.getSkin().getFullSkinId());

            ConfirmSkinPacket confirmSkinPacket = new ConfirmSkinPacket();
            confirmSkinPacket.setSkinData(updatedEntry.getSkin().getSkinData().getImage());
            confirmSkinPacket.setGeometry(updatedEntry.getSkin().getGeometryData());
            confirmSkinPacket.setUuid(entity.getUuid());
            long uid = updatedEntry.getUid();
            if (uid == -1) {
                uid = entity.getUuid().toString().replace("-", "").hashCode();
                if (uid < 0) {
                    uid = -uid;
                }
            }
            confirmSkinPacket.setUid(uid);
            session.sendUpstreamPacket(confirmSkinPacket);
        });
    }
}
