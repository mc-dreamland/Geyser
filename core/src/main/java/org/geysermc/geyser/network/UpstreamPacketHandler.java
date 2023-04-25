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

package org.geysermc.geyser.network;

import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.BedrockPacketCodec;
import com.nukkitx.protocol.bedrock.data.ExperimentData;
import com.nukkitx.protocol.bedrock.data.PacketCompressionAlgorithm;
import com.nukkitx.protocol.bedrock.data.ResourcePackType;
import com.nukkitx.protocol.bedrock.packet.*;
import com.nukkitx.protocol.bedrock.v567.Bedrock_v567;
import com.nukkitx.protocol.bedrock.v567.Bedrock_v567patch;
import com.zaxxer.hikari.HikariDataSource;
import org.geysermc.geyser.Constants;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.configuration.GeyserConfiguration;
import org.geysermc.geyser.pack.*;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.PendingMicrosoftAuthentication;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.util.LoginEncryptionUtils;
import org.geysermc.geyser.util.MathUtils;
import org.geysermc.geyser.util.VersionCheckUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class UpstreamPacketHandler extends LoggingPacketHandler {

    private Deque<String> packsToSent = new ArrayDeque<>();

    public UpstreamPacketHandler(GeyserImpl geyser, GeyserSession session) {
        super(geyser, session);
    }

    private boolean translateAndDefault(BedrockPacket packet) {
        return Registries.BEDROCK_PACKET_TRANSLATORS.translate(packet.getClass(), packet, session);
    }

    @Override
    boolean defaultHandler(BedrockPacket packet) {
        return translateAndDefault(packet);
    }

    private boolean newProtocol = false; // TEMPORARY

    private boolean setCorrectCodec(int protocolVersion) {
        BedrockPacketCodec packetCodec = GameProtocol.getBedrockCodec(protocolVersion);
        if (packetCodec == null) {
            String supportedVersions = GameProtocol.getAllSupportedBedrockVersions();
            if (protocolVersion > GameProtocol.DEFAULT_BEDROCK_CODEC.getProtocolVersion()) {
                // Too early to determine session locale
                String disconnectMessage = GeyserLocale.getLocaleStringLog("geyser.network.outdated.server", supportedVersions);
                // If the latest release matches this version, then let the user know.
                OptionalInt latestRelease = VersionCheckUtils.getLatestBedrockRelease();
                if (latestRelease.isPresent() && latestRelease.getAsInt() == protocolVersion) {
                    // Random note: don't make the disconnect message too long or Bedrock will cut it off on smaller screens
                    disconnectMessage += "\n" + GeyserLocale.getLocaleStringLog("geyser.version.new.on_disconnect", Constants.GEYSER_DOWNLOAD_LOCATION);
                }
                session.disconnect(disconnectMessage);
                return false;
            } else if (protocolVersion < GameProtocol.DEFAULT_BEDROCK_CODEC.getProtocolVersion()) {
                session.disconnect(GeyserLocale.getLocaleStringLog("geyser.network.outdated.client", supportedVersions));
                return false;
            }
        }

        session.getUpstream().getSession().setPacketCodec(packetCodec);
        return true;
    }

    @Override
    public boolean handle(RequestNetworkSettingsPacket packet) {
        if (setCorrectCodec(packet.getProtocolVersion())) {
            newProtocol = true;
        } else {
            return true;
        }

        // New since 1.19.30 - sent before login packet
        PacketCompressionAlgorithm algorithm = PacketCompressionAlgorithm.ZLIB;

        NetworkSettingsPacket responsePacket = new NetworkSettingsPacket();
        responsePacket.setCompressionAlgorithm(algorithm);
        responsePacket.setCompressionThreshold(512);
        session.sendUpstreamPacketImmediately(responsePacket);

        session.getUpstream().getSession().setCompression(algorithm);
        return true;
    }

    @Override
    public boolean handle(LoginPacket loginPacket) {
        if (geyser.isShuttingDown()) {
            // Don't allow new players in if we're no longer operating
            session.disconnect(GeyserLocale.getLocaleStringLog("geyser.core.shutdown.kick.message"));
            return true;
        }

        if (!newProtocol) {
            if (!setCorrectCodec(loginPacket.getProtocolVersion())) { // REMOVE WHEN ONLY 1.19.30 IS SUPPORTED OR 1.20
                return true;
            }
        }

        // Set the block translation based off of version
        session.setBlockMappings(BlockRegistries.BLOCKS.forVersion(loginPacket.getProtocolVersion()));
        session.setItemMappings(Registries.ITEMS.forVersion(loginPacket.getProtocolVersion()));

        LoginEncryptionUtils.encryptPlayerConnection(session, loginPacket);

        if (session.isClosed()) {
            // Can happen if Xbox validation fails
            return true;
        }

        // Hack for... whatever this is
        if (loginPacket.getProtocolVersion() == Bedrock_v567.V567_CODEC.getProtocolVersion() && !session.getClientData().getGameVersion().equals("1.19.60")) {
            session.getUpstream().getSession().setPacketCodec(Bedrock_v567patch.BEDROCK_V567PATCH);
        }

        PlayStatusPacket playStatus = new PlayStatusPacket();
        playStatus.setStatus(PlayStatusPacket.Status.LOGIN_SUCCESS);
        session.sendUpstreamPacket(playStatus);

        geyser.getSessionManager().addPendingSession(session);

        ResourcePacksInfoPacket resourcePacksInfo = new ResourcePacksInfoPacket();
        for (BehaviorPack behaviorPack : BehaviorPack.PACKS.values()) {
            BehaviorPackManifest.Header header = behaviorPack.getManifest().getHeader();
            resourcePacksInfo.getBehaviorPackInfos().add(new ResourcePacksInfoPacket.Entry(
                    header.getUuid().toString(), header.getVersionString(), behaviorPack.getFile().length(),
                    behaviorPack.getContentKey(), "", header.getUuid().toString(), false, false));
        }
        for (ResourcePack resourcePack : ResourcePack.PACKS.values()) {
            ResourcePackManifest.Header header = resourcePack.getManifest().getHeader();
            resourcePacksInfo.getResourcePackInfos().add(new ResourcePacksInfoPacket.Entry(
                    header.getUuid().toString(), header.getVersionString(), resourcePack.getFile().length(),
                    resourcePack.getContentKey(), "", header.getUuid().toString(), false, false));
        }

        if (geyser.getConfig().getOptionalPacks().isEnableOptionalPacks()) {
            try {
                Connection connection = geyser.getDataSource().getConnection();
                final PreparedStatement sql = connection.prepareStatement("select used_pack from hey_packs_player where player_uuid = ?");
                sql.setString(1, session.getAuthData().uuid().toString());
                final ResultSet set = sql.executeQuery();
                if (set.next()) {
                    String usedPacks = set.getString("used_pack");
                    String[] split = usedPacks.replace(" ", "").split(",");
                    ArrayList<String> packsUUID = new ArrayList<>();
                    for (String splitPackId : split) {
                        for (Map.Entry<String, OptionalResourcePack> packEntry : OptionalResourcePack.PACKS.entrySet()) {
                            OptionalResourcePack optionalResourcePack = packEntry.getValue();

                            String getPackUUID = geyser.getOptionalPacks().getOrDefault(Integer.parseInt(splitPackId), null);
                            if (getPackUUID == null) {
                                continue;
                            }
                            if (getPackUUID.equals( packEntry.getKey())) {
                                packsUUID.add(getPackUUID);

                                ResourcePackManifest.Header header = optionalResourcePack.getManifest().getHeader();
                                resourcePacksInfo.getResourcePackInfos().add(new ResourcePacksInfoPacket.Entry(
                                        header.getUuid().toString(), header.getVersionString(), optionalResourcePack.getFile().length(),
                                        optionalResourcePack.getContentKey(), "", header.getUuid().toString(), false, false));
                            }
                        }
                    }

                    session.setOptionPacksUuid(packsUUID);
                }
                sql.close();
                set.close();
                connection.close();
            } catch (SQLException e) {
                geyser.getLogger().error("§c获取玩家自选材质包列表失败！请检查数据库连接是否正常！");
                e.printStackTrace();
            }
        }

        resourcePacksInfo.setForcedToAccept(GeyserImpl.getInstance().getConfig().isForceResourcePacks());
        session.sendUpstreamPacket(resourcePacksInfo);

        GeyserLocale.loadGeyserLocale(session.locale());
        return true;
    }

    @Override
    public boolean handle(ResourcePackClientResponsePacket packet) {
        switch (packet.getStatus()) {
            case COMPLETED:
                if (geyser.getConfig().getRemote().authType() != AuthType.ONLINE) {
                    session.authenticate(session.getAuthData().name());
                } else if (!couldLoginUserByName(session.getAuthData().name())) {
                    // We must spawn the white world
                    session.connect();
                }
                geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.network.connect", session.getAuthData().name()));
                break;

            case SEND_PACKS:
                packsToSent.addAll(packet.getPackIds());
                sendPackDataInfo(packsToSent.pop());
                break;

            case HAVE_ALL_PACKS:
                ResourcePackStackPacket stackPacket = new ResourcePackStackPacket();
                stackPacket.setExperimentsPreviouslyToggled(false);
                stackPacket.setForcedToAccept(true); // Leaving this as false allows the player to choose to download or not
                stackPacket.setGameVersion(session.getClientData().getGameVersion());

                for (BehaviorPack pack : BehaviorPack.PACKS.values()) {
                    BehaviorPackManifest.Header header = pack.getManifest().getHeader();
                    stackPacket.getBehaviorPacks().add(new ResourcePackStackPacket.Entry(header.getUuid().toString(), header.getVersionString(), ""));
                }
                for (ResourcePack pack : ResourcePack.PACKS.values()) {
                    ResourcePackManifest.Header header = pack.getManifest().getHeader();
                    stackPacket.getResourcePacks().add(new ResourcePackStackPacket.Entry(header.getUuid().toString(), header.getVersionString(), ""));
                }
                if (geyser.getConfig().getOptionalPacks().isEnableOptionalPacks()) {
                    if (session.getOptionPacksUuid() != null) {
                        for (String packUuid : session.getOptionPacksUuid()) {
                            for (OptionalResourcePack pack : OptionalResourcePack.PACKS.values()) {
                                if (packUuid.equals(pack.getManifest().getHeader().getUuid().toString())) {
                                    ResourcePackManifest.Header header = pack.getManifest().getHeader();
                                    stackPacket.getResourcePacks().add(new ResourcePackStackPacket.Entry(header.getUuid().toString(), header.getVersionString(), ""));
                                }
                            }
                        }
                    }
                }

                if (GeyserImpl.getInstance().getConfig().isAddNonBedrockItems()) {
                    // Allow custom items to work
                    stackPacket.getExperiments().add(new ExperimentData("vanilla_experiments", true));
                    stackPacket.getExperiments().add(new ExperimentData("data_driven_items", true));
                    stackPacket.getExperiments().add(new ExperimentData("upcoming_creator_features", true));
                    stackPacket.getExperiments().add(new ExperimentData("experimental_molang_features", true));
                }

                session.sendUpstreamPacket(stackPacket);
                break;

            default:
                session.disconnect("disconnectionScreen.resourcePack");
                break;
        }

        return true;
    }

    @Override
    public boolean handle(ModalFormResponsePacket packet) {
        session.executeInEventLoop(() -> session.getFormCache().handleResponse(packet));
        return true;
    }

    private boolean couldLoginUserByName(String bedrockUsername) {
        if (geyser.getConfig().getSavedUserLogins().contains(bedrockUsername)) {
            String refreshToken = geyser.refreshTokenFor(bedrockUsername);
            if (refreshToken != null) {
                geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.auth.stored_credentials", session.getAuthData().name()));
                session.authenticateWithRefreshToken(refreshToken);
                return true;
            }
        }
        if (geyser.getConfig().getUserAuths() != null) {
            GeyserConfiguration.IUserAuthenticationInfo info = geyser.getConfig().getUserAuths().get(bedrockUsername);

            if (info != null) {
                geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.auth.stored_credentials", session.getAuthData().name()));
                session.setMicrosoftAccount(info.isMicrosoftAccount());
                session.authenticate(info.getEmail(), info.getPassword());
                return true;
            }
        }
        PendingMicrosoftAuthentication.AuthenticationTask task = geyser.getPendingMicrosoftAuthentication().getTask(session.getAuthData().xuid());
        if (task != null) {
            if (task.getAuthentication().isDone() && session.onMicrosoftLoginComplete(task)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean handle(MovePlayerPacket packet) {
        if (session.isLoggingIn()) {
            SetTitlePacket titlePacket = new SetTitlePacket();
            titlePacket.setType(SetTitlePacket.Type.ACTIONBAR);
            titlePacket.setText(GeyserLocale.getPlayerLocaleString("geyser.auth.login.wait", session.locale()));
            titlePacket.setFadeInTime(0);
            titlePacket.setFadeOutTime(1);
            titlePacket.setStayTime(2);
            titlePacket.setXuid("");
            titlePacket.setPlatformOnlineId("");
            session.sendUpstreamPacket(titlePacket);
        }

        return translateAndDefault(packet);
    }

    @Override
    public boolean handle(ResourcePackChunkRequestPacket packet) {
        ResourcePackChunkDataPacket data = new ResourcePackChunkDataPacket();
        ResourcePack resourcePack = ResourcePack.PACKS.get(packet.getPackId().toString());
        BehaviorPack behaviorPack = BehaviorPack.PACKS.get(packet.getPackId().toString());
        OptionalResourcePack optionalResourcePack = OptionalResourcePack.PACKS.get(packet.getPackId().toString());

        data.setChunkIndex(packet.getChunkIndex());
        data.setPackVersion(packet.getPackVersion());
        data.setPackId(packet.getPackId());

        int offset = packet.getChunkIndex() * ResourcePack.CHUNK_SIZE;
        long remainingSize;
        byte[] packData;
        File file;

        if (resourcePack != null) {
            file = resourcePack.getFile();
            data.setProgress(packet.getChunkIndex() * ResourcePack.CHUNK_SIZE);
            remainingSize = file.length() - offset;
            packData = new byte[(int) MathUtils.constrain(remainingSize, 0, ResourcePack.CHUNK_SIZE)];
        } else if (behaviorPack != null) {
            file = behaviorPack.getFile();
            data.setProgress(packet.getChunkIndex() * BehaviorPack.CHUNK_SIZE);
            remainingSize = file.length() - offset;
            packData = new byte[(int) MathUtils.constrain(remainingSize, 0, BehaviorPack.CHUNK_SIZE)];
        } else {
            file = optionalResourcePack.getFile();
            data.setProgress(packet.getChunkIndex() * BehaviorPack.CHUNK_SIZE);
            remainingSize = file.length() - offset;
            packData = new byte[(int) MathUtils.constrain(remainingSize, 0, BehaviorPack.CHUNK_SIZE)];
        }


        try (InputStream inputStream = new FileInputStream(file)) {
            inputStream.skip(offset);
            inputStream.read(packData, 0, packData.length);
        } catch (Exception e) {
            e.printStackTrace();
        }

        data.setData(packData);

        session.sendUpstreamPacket(data);

        // Check if it is the last chunk and send next pack in queue when available.
        if (remainingSize <= ResourcePack.CHUNK_SIZE && !packsToSent.isEmpty()) {
            sendPackDataInfo(packsToSent.pop());
        }

        return true;
    }

    private void sendPackDataInfo(String id) {
        ResourcePackDataInfoPacket data = new ResourcePackDataInfoPacket();
        String[] packID = id.split("_");
        ResourcePack resourcePack = ResourcePack.PACKS.get(packID[0]);
        BehaviorPack behaviorPack = BehaviorPack.PACKS.get(packID[0]);
        OptionalResourcePack optionalResourcePack = OptionalResourcePack.PACKS.get(packID[0]);

        if (resourcePack != null) {
            ResourcePackManifest.Header header = resourcePack.getManifest().getHeader();
            data.setPackId(header.getUuid());
            int chunkCount = (int) Math.ceil((int) resourcePack.getFile().length() / (double) ResourcePack.CHUNK_SIZE);
            data.setChunkCount(chunkCount);
            data.setCompressedPackSize(resourcePack.getFile().length());
            data.setMaxChunkSize(ResourcePack.CHUNK_SIZE);
            data.setHash(resourcePack.getSha256());
            data.setPackVersion(packID[1]);
            data.setPremium(false);
            data.setType(ResourcePackType.RESOURCE);
        } else if (behaviorPack != null) {
            BehaviorPackManifest.Header header = behaviorPack.getManifest().getHeader();
            data.setPackId(header.getUuid());
            int chunkCount = (int) Math.ceil((int) behaviorPack.getFile().length() / (double) BehaviorPack.CHUNK_SIZE);
            data.setChunkCount(chunkCount);
            data.setCompressedPackSize(behaviorPack.getFile().length());
            data.setMaxChunkSize(BehaviorPack.CHUNK_SIZE);
            data.setHash(behaviorPack.getSha256());
            data.setPackVersion(packID[1]);
            data.setPremium(false);
            data.setType(ResourcePackType.BEHAVIOR);
        } else {
            ResourcePackManifest.Header header = optionalResourcePack.getManifest().getHeader();
            data.setPackId(header.getUuid());
            int chunkCount = (int) Math.ceil((int) optionalResourcePack.getFile().length() / (double) OptionalResourcePack.CHUNK_SIZE);
            data.setChunkCount(chunkCount);
            data.setCompressedPackSize(optionalResourcePack.getFile().length());
            data.setMaxChunkSize(OptionalResourcePack.CHUNK_SIZE);
            data.setHash(optionalResourcePack.getSha256());
            data.setPackVersion(packID[1]);
            data.setPremium(false);
            data.setType(ResourcePackType.RESOURCE);
        }

        session.sendUpstreamPacket(data);
    }
}
