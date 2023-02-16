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

package org.geysermc.geyser.skin;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.google.gson.Gson;
import com.nukkitx.protocol.bedrock.data.skin.ImageData;
import com.nukkitx.protocol.bedrock.data.skin.SerializedSkin;
import com.nukkitx.protocol.bedrock.packet.PlayerListPacket;
import com.nukkitx.protocol.bedrock.packet.PlayerSkinPacket;
import lombok.SneakyThrows;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.entity.type.player.PlayerEntity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.auth.BedrockClientData;
import org.geysermc.geyser.text.GeyserLocale;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

public class SkinManager {

    /**
     * Builds a Bedrock player list entry from our existing, cached Bedrock skin information
     */
    public static PlayerListPacket.Entry buildCachedEntry(GeyserSession session, PlayerEntity playerEntity) {
        GameProfileData data = GameProfileData.from(playerEntity);
        SkinProvider.Cape cape = SkinProvider.getCachedCape(data.capeUrl());
        SkinProvider.SkinGeometry geometry = SkinProvider.getCachedGeometry().asMap().getOrDefault(playerEntity.getUuid(),
                SkinProvider.SkinGeometry.getLegacy(data.isAlex()));

        GeyserImpl.getInstance().getLogger().debug("playerEntity buildCache: " + playerEntity.getUsername() + " skinUrl: " + data.skinUrl() + " session: " + session.getAuthData().name());
        SkinProvider.Skin skin = SkinProvider.getCachedSkin(data.skinUrl());
        if (skin == null) {
            skin = SkinProvider.EMPTY_SKIN;
        }

        return buildEntryManually(
                session,
                playerEntity.getUuid(),
                playerEntity.getUsername(),
                playerEntity.getGeyserId(),
                skin.getTextureUrl(),
                skin.getSkinData(),
                cape.getCapeId(),
                cape.getCapeData(),
                geometry
        );
    }

    /**
     * With all the information needed, build a Bedrock player entry with translated skin information.
     */
    public static PlayerListPacket.Entry buildEntryManually(GeyserSession session, UUID uuid, String username, long geyserId,
                                                            String skinId, byte[] skinData,
                                                            String capeId, byte[] capeData,
                                                            SkinProvider.SkinGeometry geometry) {
        SerializedSkin serializedSkin = SerializedSkin.of(
                skinId, "", geometry.getGeometryName(), ImageData.of(skinData), Collections.emptyList(),
                ImageData.of(capeData), geometry.getGeometryData(), "", true, false,
                !capeId.equals(SkinProvider.EMPTY_CAPE.getCapeId()), capeId, skinId
        );
        // This attempts to find the XUID of the player so profile images show up for Xbox accounts
        String xuid = "";
        GeyserSession playerSession = GeyserImpl.getInstance().connectionByUuid(uuid);

        if (playerSession != null) {
            xuid = playerSession.getAuthData().xuid();
        }
//        if (SkinProvider.getCachedSkin(playerEntity.getUuid().toString()) != null){
//            skin = SkinProvider.getCachedSkin(playerEntity.getUuid().toString());
//        }

        PlayerListPacket.Entry entry;

        // If we are building a PlayerListEntry for our own session we use our AuthData UUID instead of the Java UUID
        // as Bedrock expects to get back its own provided UUID
        if (session.getPlayerEntity().getUuid().equals(uuid)) {
            entry = new PlayerListPacket.Entry(session.getAuthData().uuid());
        } else {
            entry = new PlayerListPacket.Entry(uuid);
        }

        entry.setName(username);
        entry.setEntityId(geyserId);
        entry.setSkin(serializedSkin);
        entry.setXuid(xuid);
        entry.setPlatformChatId("");
        entry.setTeacher(false);
        entry.setTrustedSkin(true);
        return entry;
    }

    public static void requestAndHandleSkinAndCape(PlayerEntity entity, GeyserSession session,
                                                   Consumer<SkinProvider.SkinData> skinDataConsumer) {
        SkinProvider.requestSkinData(entity).whenCompleteAsync((skinData, throwable) -> {
            if (skinData == null) {
                if (skinDataConsumer != null) {
                    skinDataConsumer.accept(null);
                }

                return;
            }

            if (skinData.geometry() != null) {
                SkinProvider.Skin skin = skinData.skin();
                SkinProvider.Cape cape = skinData.cape();
                SkinProvider.SkinGeometry geometry = skinData.geometry();

                GeyserImpl.getInstance().getLogger().debug(
                        String.format("build list packet session: %s entity: %s",
                                session.name(),
                                entity.getUsername())
                );

                PlayerListPacket.Entry updatedEntry = buildEntryManually(
                        session,
                        entity.getUuid(),
                        entity.getUsername(),
                        entity.getGeyserId(),
                        skin.getTextureUrl(),
                        skin.getSkinData(),
                        cape.getCapeId(),
                        cape.getCapeData(),
                        geometry
                );

                PlayerListPacket playerAddPacket = new PlayerListPacket();
                playerAddPacket.setAction(PlayerListPacket.Action.ADD);
                playerAddPacket.getEntries().add(updatedEntry);
                session.sendUpstreamPacket(playerAddPacket);

                if (!entity.isPlayerList()) {
                    PlayerListPacket playerRemovePacket = new PlayerListPacket();
                    playerRemovePacket.setAction(PlayerListPacket.Action.REMOVE);
                    playerRemovePacket.getEntries().add(updatedEntry);
                    session.sendUpstreamPacket(playerRemovePacket);
                }
            }

            if (skinDataConsumer != null) {
                skinDataConsumer.accept(skinData);
            }
        });
    }

    public static void handleBedrockSkin(PlayerEntity playerEntity, BedrockClientData clientData) {
        GeyserImpl geyser = GeyserImpl.getInstance();
        if (geyser.getConfig().isDebugMode()) {
            geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.skin.bedrock.register", playerEntity.getUsername(), playerEntity.getUuid()));
        }

        try {
            byte[] skinBytes = Base64.getDecoder().decode(clientData.getSkinData().getBytes(StandardCharsets.UTF_8));
            byte[] capeBytes = clientData.getCapeData();

            byte[] geometryNameBytes = Base64.getDecoder().decode(clientData.getGeometryName().getBytes(StandardCharsets.UTF_8));
            byte[] geometryBytes = Base64.getDecoder().decode(clientData.getGeometryData().getBytes(StandardCharsets.UTF_8));
            geyser.getLogger().debug(playerEntity.getUuid() + " "+String.format("length: Skin-%s Cape-%s GeometryName-%s geometry-%s", skinBytes.length, capeBytes.length, geometryNameBytes.length, geometryBytes.length));

            SkinProvider.storeBedrockSkin(playerEntity.getUuid(), clientData.getSkinId(), skinBytes);
            SkinProvider.storeBedrockGeometry(playerEntity.getUuid(), geometryNameBytes, geometryBytes);

            if (Objects.nonNull(clientData.getFashionName()) && SkinProvider.getPermanentSkins().containsKey(clientData.getFashionName())){
                SkinProvider.storeBedrockSkin(playerEntity.getUuid(),clientData.getSkinId(), SkinProvider.getPermanentSkins().get(clientData.getFashionName()));
            }
            if (Objects.nonNull(clientData.getFashionDataName()) && SkinProvider.getPermanentGeometry().containsKey(clientData.getFashionDataName())){
                SkinProvider.storeBedrockGeometry(playerEntity.getUuid(),SkinProvider.getPermanentGeometry().get(clientData.getFashionDataName()));
            }

            geyser.getLogger().debug("storeGeometrys: "+SkinProvider.getPermanentGeometry());

            if (!clientData.getCapeId().equals("")) {
                SkinProvider.storeBedrockCape(playerEntity.getUuid(), capeBytes);
            }
        } catch (Exception e) {
            throw new AssertionError("Failed to cache skin for bedrock user (" + playerEntity.getUsername() + "): ", e);
        }
    }


    /**
     * 重置时装，显示默认皮肤
     * @param session
     */
    public static void restoreSkin(GeyserSession session){
        session.getClientData().setFashionName(null);
        session.getClientData().setFashionDataName(null);
        session.getClientData().setSkinId(session.getClientData().getOriginSkinId());
        GeyserImpl.getInstance().getLogger().debug("restoreSkin: "+session.getClientData().getSkinId());

        byte[] geometryNameBytes = Base64.getDecoder().decode(session.getClientData().getGeometryName().getBytes(StandardCharsets.UTF_8));
        byte[] geometryBytes = Base64.getDecoder().decode(session.getClientData().getGeometryData().getBytes(StandardCharsets.UTF_8));
        byte[] skinBytes = Base64.getDecoder().decode(session.getClientData().getSkinData().getBytes(StandardCharsets.UTF_8));
        // 重置皮肤、模型数据
        SkinProvider.getCachedGeometry().put(session.uuid(),new SkinProvider.SkinGeometry(new String(geometryNameBytes),new String(geometryBytes).replaceAll("\t",""),false));
        SkinProvider.storeBedrockSkin(session.uuid(),session.getClientData().getSkinId(),skinBytes);
        // 同步后端
        GeyserImpl.getInstance().getSkinUploader().syncFashion(session, session.getClientData(),false);

        requestAndHandleSkinAndCape(session.getPlayerEntity(),session,skinData -> {
            SerializedSkin skin = FakeHeadProvider.getSkin(session.getClientData().getSkinId(), skinData.skin(), skinData.cape(), skinData.geometry());
            PlayerListPacket listPacket = new PlayerListPacket();
            listPacket.setAction(PlayerListPacket.Action.ADD);

            PlayerListPacket.Entry entry = new PlayerListPacket.Entry(session.uuid());
            entry.setSkin(skin);
            entry.setEntityId(session.getPlayerEntity().getEntityId());
            entry.setXuid("");
            entry.setPlatformChatId("");
            entry.setTrustedSkin(true);
            entry.setName(session.name());
            listPacket.getEntries().add(entry);
            session.sendUpstreamPacket(listPacket);

            for (PlayerEntity playerEntity : session.getEntityCache().getAllPlayerEntities()) {
                GeyserSession s = GeyserImpl.getInstance().connectionByUuid(playerEntity.getUuid());
                if (s != null){
                    s.sendUpstreamPacket(listPacket);
                }
            }
        });
    }

    /**
     * 切换时装
     * @param session
     * @param fashion
     * @param fashionData
     */
    public static void switchFashion(GeyserSession session, String fashion, String fashionData) {
        // 只适用于单个 Geyser 不适用于多 Geyser
        // 需保证 skinId 不重复
        session.getClientData().setSkinId(fashion);
        session.getClientData().setFashionName(fashion);
        session.getClientData().setFashionDataName(fashionData);
        GeyserImpl.getInstance().getSkinUploader().syncFashion(session, session.getClientData(),true);

        SkinProvider.storeBedrockSkin(session.uuid(), session.getClientData().getSkinId(),
                SkinProvider.getPermanentSkins().getOrDefault(fashion,
                        SkinProvider.getPermanentSkins().get("alex")));
        SkinProvider.storeBedrockGeometry(session.uuid(),
                SkinProvider.getPermanentGeometry(fashionData));

        SkinManager.requestAndHandleSkinAndCape(session.getPlayerEntity(), session, skinAndCape -> {
//            PlayerListPacket listPacket = buildListPacket(fashion, fashionData, session.getPlayerEntity().getEntityId(),
//                    session.name(), session.uuid(), skinAndCape.skin().getTextureUrl());
//            session.sendUpstreamPacket(listPacket);

            PlayerSkinPacket skinPacket = skinPacket(session, skinAndCape);
            session.sendUpstreamPacket(skinPacket);
//            session.sendUpstreamPacket(skinPacket);
//            for (PlayerEntity playerEntity : session.getEntityCache().getAllPlayerEntities()) {
//                GeyserSession geyserSession = GeyserImpl.getInstance().connectionByUuid(playerEntity.getUuid());
//                if (geyserSession != null){
                    // 发包切换皮肤
//                    geyserSession.sendUpstreamPacket(skinPacket);
//                }
//            }
        });
        GeyserImpl.getInstance().getLogger().debug(String.format("%s switch fasion skin: %s geometry: %s", session.name(), fashion, fashionData));
    }

    public static PlayerSkinPacket skinPacket(GeyserSession session, SkinProvider.SkinData skinAndCape){
        PlayerSkinPacket skinPacket = new PlayerSkinPacket();
        skinPacket.setUuid(session.uuid());
        skinPacket.setOldSkinName("");
        skinPacket.setNewSkinName(session.getClientData().getSkinId());
        skinPacket.setSkin(FakeHeadProvider.getSkin(session.getClientData().getSkinId(),skinAndCape.skin(),skinAndCape.cape(),
                skinAndCape.geometry()));
        skinPacket.setTrustedSkin(true);
        return skinPacket;
    }

    /**
     * 对其他Geyser进行皮肤同步
     */
    public record GameProfileData(String skinUrl, String capeUrl, boolean isAlex) {
        /**
         * Generate the GameProfileData from the given CompoundTag representing a GameProfile
         *
         * @param tag tag to build the GameProfileData from
         * @return The built GameProfileData, or null if this wasn't a valid tag
         */
        public static @Nullable GameProfileData from(CompoundTag tag) {
            if (!(tag.get("Properties") instanceof CompoundTag propertiesTag)) {
                return null;
            }
            if (!(propertiesTag.get("textures") instanceof ListTag texturesTag) || texturesTag.size() == 0) {
                return null;
            }
            if (!(texturesTag.get(0) instanceof CompoundTag texturesData)) {
                return null;
            }
            if (!(texturesData.get("Value") instanceof StringTag skinDataValue)) {
                return null;
            }

            try {
                return loadFromJson(skinDataValue.getValue());
            } catch (IOException e) {
                GeyserImpl.getInstance().getLogger().debug("Something went wrong while processing skin for tag " + tag);
                if (GeyserImpl.getInstance().getConfig().isDebugMode()) {
                    e.printStackTrace();
                }
                return null;
            }
        }

        /**
         * Generate the GameProfileData from the given player entity
         *
         * @param entity entity to build the GameProfileData from
         * @return The built GameProfileData
         */
        public static GameProfileData from(PlayerEntity entity) {
            try {
                String texturesProperty = entity.getTexturesProperty();
//                GeyserImpl.getInstance().getLogger().debug("GameProfile Form: entity: " + entity.getUsername() + " property: " + texturesProperty);
                if (texturesProperty == null) {
                    // Likely offline mode
                    return loadBedrockOrOfflineSkin(entity);
                }
                GameProfileData data = loadFromJson(texturesProperty);
                if (data != null) {
                    return data;
                } else {
                    return loadBedrockOrOfflineSkin(entity);
                }
            } catch (IOException exception) {
                GeyserImpl.getInstance().getLogger().debug("Something went wrong while processing skin for " + entity.getUsername());
                if (GeyserImpl.getInstance().getConfig().isDebugMode()) {
                    exception.printStackTrace();
                }
                return loadBedrockOrOfflineSkin(entity);
            }
        }

        private static GameProfileData loadFromJson(String encodedJson) throws IOException {
            JsonNode skinObject = GeyserImpl.JSON_MAPPER.readTree(new String(Base64.getDecoder().decode(encodedJson), StandardCharsets.UTF_8));
            JsonNode textures = skinObject.get("textures");


            if (skinObject.hasNonNull("pe")) {
                String skinUrl = skinObject.get("data").asText();
                GeyserImpl.getInstance().getLogger().debug("loadFromJson PE " + skinUrl);
                return new GameProfileData(skinUrl, SkinProvider.EMPTY_CAPE.getTextureUrl(), skinObject.get("alex").asBoolean());
            }

            if (textures == null) {
                return null;
            }

            JsonNode skinTexture = textures.get("SKIN");
            if (skinTexture == null) {
                return null;
            }

            String skinUrl = skinTexture.get("url").asText().replace("http://", "https://");

            boolean isAlex = skinTexture.has("metadata");

            String capeUrl = null;
            JsonNode capeTexture = textures.get("CAPE");
            if (capeTexture != null) {
                capeUrl = capeTexture.get("url").asText().replace("http://", "https://");
            }
            return new GameProfileData(skinUrl, capeUrl, isAlex);
        }

        /**
         * @return default skin with default cape when texture data is invalid, or the Bedrock player's skin if this
         * is a Bedrock player.
         */
        @SneakyThrows
        private static GameProfileData loadBedrockOrOfflineSkin(PlayerEntity entity) {
            // Fallback to the offline mode of working it out
            UUID uuid = entity.getUuid();
            boolean isAlex = (Math.abs(uuid.hashCode() % 2) == 1);

            String skinUrl = isAlex ? SkinProvider.EMPTY_SKIN_ALEX.getTextureUrl() : SkinProvider.EMPTY_SKIN.getTextureUrl();
            String capeUrl = SkinProvider.EMPTY_CAPE.getTextureUrl();
            if (("steve".equals(skinUrl) || "alex".equals(skinUrl)) && GeyserImpl.getInstance().getConfig().getRemote().authType() != AuthType.ONLINE) {
                GeyserSession session = GeyserImpl.getInstance().connectionByUuid(uuid);

                if (session != null) {
                    skinUrl = session.getClientData().getSkinId();
                    capeUrl = session.getClientData().getCapeId();
                }
                if (session == null && GeyserImpl.getInstance().getConfig().getRemote().authType() == AuthType.FLOODGATE) {
                    skinUrl = GeyserImpl.getInstance().getConfig().getService().getSkinurl()+"/skin/" + uuid+"?pe";
                    SkinProvider.SkinGeometry geometry = SkinProvider.getCachedGeometry().getIfPresent(uuid);
                    if (geometry != null) {
                        String geometryName = GeyserImpl.JSON_MAPPER.readTree(geometry.getGeometryName()).get("geometry").get("default").asText()
                                .replace("geometry.","")
                                .replace("humanoid.","");
                        skinUrl += "&"+geometryName;
                    }
                }

                GeyserImpl.getInstance().getLogger().debug("loadOfflineSkin: " + entity.getUsername() + " url: " + skinUrl);
            }
            return new GameProfileData(skinUrl, capeUrl, isAlex);
        }
    }

    @NotNull
    public static PlayerListPacket buildListPacket(String fashion, String geometryName, int entityId, String userName, UUID uuid, String skinUrl) {
        PlayerListPacket listPacket = new PlayerListPacket();
        listPacket.setAction(PlayerListPacket.Action.ADD);

        PlayerListPacket.Entry entry = new PlayerListPacket.Entry(uuid);
        entry.setEntityId(entityId);
        entry.setName(userName);
        SkinProvider.SkinGeometry geometry = SkinProvider.getPermanentGeometry(geometryName);
        SerializedSkin skin = SerializedSkin.of(skinUrl, "", "{\"geometry\" :{\"default\" :\"geometry." + geometryName + "\"}}",
                ImageData.of(SkinProvider.getPermanentSkins().get(fashion).getSkinData()),
                Collections.emptyList(),
                ImageData.of(SkinProvider.EMPTY_CAPE.getCapeData()),
                geometry.getGeometryData(), "", true, false,
                false, SkinProvider.EMPTY_CAPE.getCapeId(), skinUrl);
        entry.setSkin(skin);
        entry.setXuid("");
        entry.setPlatformChatId("");
        entry.setTrustedSkin(true);

        listPacket.getEntries().add(entry);
        return listPacket;
    }
}
