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

import io.netty.buffer.Unpooled;
import org.cloudburstmc.protocol.bedrock.BedrockDisconnectReasons;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.compat.BedrockCompat;
import org.cloudburstmc.protocol.bedrock.codec.v622.Bedrock_v622;
import org.cloudburstmc.protocol.bedrock.data.ExperimentData;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.ResourcePackType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackChunkDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackChunkRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackDataInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetTitlePacket;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.geysermc.geyser.Constants;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.pack.ResourcePackManifest;
import org.geysermc.geyser.event.type.SessionLoadBehaviorPacksEventImpl;
import org.geysermc.geyser.event.type.SessionLoadOptionalResourcePacksEventImpl;
import org.geysermc.geyser.event.type.SessionLoadResourcePacksEventImpl;
import org.geysermc.geyser.pack.GeyserResourcePack;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.PendingMicrosoftAuthentication;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.util.LoginEncryptionUtils;
import org.geysermc.geyser.util.MathUtils;
import org.geysermc.geyser.util.VersionCheckUtils;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.*;

public class UpstreamPacketHandler extends LoggingPacketHandler {

    private boolean networkSettingsRequested = false;
    private final Deque<String> packsToSent = new ArrayDeque<>();

    private SessionLoadResourcePacksEventImpl resourcePackLoadEvent;
    private SessionLoadOptionalResourcePacksEventImpl optionalResourcePacksEvent;
    private SessionLoadBehaviorPacksEventImpl behaviorPacksEvent;

    public UpstreamPacketHandler(GeyserImpl geyser, GeyserSession session) {
        super(geyser, session);
    }

    private PacketSignal translateAndDefault(BedrockPacket packet) {
        Registries.BEDROCK_PACKET_TRANSLATORS.translate(packet.getClass(), packet, session);
        return PacketSignal.HANDLED; // PacketSignal.UNHANDLED will log a WARN publicly
    }

    @Override
    PacketSignal defaultHandler(BedrockPacket packet) {
        return translateAndDefault(packet);
    }

    private boolean setCorrectCodec(int protocolVersion) {
        BedrockCodec packetCodec = GameProtocol.getBedrockCodec(protocolVersion);
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
                if (protocolVersion < Bedrock_v622.CODEC.getProtocolVersion()) {
                    // https://github.com/GeyserMC/Geyser/issues/4378
                    session.getUpstream().getSession().setCodec(BedrockCompat.CODEC_LEGACY);
                }
                session.disconnect(GeyserLocale.getLocaleStringLog("geyser.network.outdated.client", supportedVersions));
                return false;
            } else {
                throw new IllegalStateException("Default codec of protocol version " + protocolVersion + " should have been found");
            }
        }

        session.getUpstream().getSession().setCodec(packetCodec);
        return true;
    }

    @Override
    public void onDisconnect(String reason) {
        // Use our own disconnect messages for these reasons
        if (BedrockDisconnectReasons.CLOSED.equals(reason)) {
            this.session.getUpstream().getSession().setDisconnectReason(GeyserLocale.getLocaleStringLog("geyser.network.disconnect.closed_by_remote_peer"));
        } else if (BedrockDisconnectReasons.TIMEOUT.equals(reason)) {
            this.session.getUpstream().getSession().setDisconnectReason(GeyserLocale.getLocaleStringLog("geyser.network.disconnect.timed_out"));
        }
        this.session.disconnect(this.session.getUpstream().getSession().getDisconnectReason());
    }

    @Override
    public PacketSignal handle(RequestNetworkSettingsPacket packet) {
        if (!setCorrectCodec(packet.getProtocolVersion())) {
            return PacketSignal.HANDLED;
        }

        // New since 1.19.30 - sent before login packet
        PacketCompressionAlgorithm algorithm = PacketCompressionAlgorithm.ZLIB;

        NetworkSettingsPacket responsePacket = new NetworkSettingsPacket();
        responsePacket.setCompressionAlgorithm(algorithm);
        responsePacket.setCompressionThreshold(512);
        session.sendUpstreamPacketImmediately(responsePacket);

        session.getUpstream().getSession().setCompression(algorithm);
        session.getUpstream().getSession().setCompressionLevel(this.geyser.getConfig().getBedrock().getCompressionLevel());
        networkSettingsRequested = true;
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(LoginPacket loginPacket) {
        if (geyser.isShuttingDown()) {
            // Don't allow new players in if we're no longer operating
            session.disconnect(GeyserLocale.getLocaleStringLog("geyser.core.shutdown.kick.message"));
            return PacketSignal.HANDLED;
        }

        if (!networkSettingsRequested) {
            session.disconnect(GeyserLocale.getLocaleStringLog("geyser.network.outdated.client", GameProtocol.getAllSupportedBedrockVersions()));
            return PacketSignal.HANDLED;
        }

        // Set the block translation based off of version
        session.setBlockMappings(BlockRegistries.BLOCKS.forVersion(loginPacket.getProtocolVersion()));
        session.setItemMappings(Registries.ITEMS.forVersion(loginPacket.getProtocolVersion()));

        LoginEncryptionUtils.encryptPlayerConnection(session, loginPacket);

        if (session.isClosed()) {
            // Can happen if Xbox validation fails
            return PacketSignal.HANDLED;
        }

        PlayStatusPacket playStatus = new PlayStatusPacket();
        playStatus.setStatus(PlayStatusPacket.Status.LOGIN_SUCCESS);
        session.sendUpstreamPacket(playStatus);

        geyser.getSessionManager().addPendingSession(session);

        this.resourcePackLoadEvent = new SessionLoadResourcePacksEventImpl(session, new HashMap<>(Registries.RESOURCE_PACKS.get()));
        this.geyser.eventBus().fire(this.resourcePackLoadEvent);
        this.behaviorPacksEvent = new SessionLoadBehaviorPacksEventImpl(session, new HashMap<>(Registries.BEHAVIOR_PACKS.get()));
        this.geyser.eventBus().fire(this.behaviorPacksEvent);

        ResourcePacksInfoPacket resourcePacksInfo = new ResourcePacksInfoPacket();
        for (ResourcePack pack : this.resourcePackLoadEvent.resourcePacks()) {
            PackCodec codec = pack.codec();
            ResourcePackManifest.Header header = pack.manifest().header();
            resourcePacksInfo.getResourcePackInfos().add(new ResourcePacksInfoPacket.Entry(
                    header.uuid().toString(), header.version().toString(), codec.size(), pack.contentKey(),
                    "", header.uuid().toString(), false, false));
        }
        for (ResourcePack pack : this.behaviorPacksEvent.resourcePacks()) {
            PackCodec codec = pack.codec();
            ResourcePackManifest.Header header = pack.manifest().header();
            resourcePacksInfo.getBehaviorPackInfos().add(new ResourcePacksInfoPacket.Entry(
                    header.uuid().toString(), header.version().toString(), codec.size(), pack.contentKey(),
                    "", header.uuid().toString(), false, false));
        }
        if (geyser.getConfig().getOptionalPacks().isEnableOptionalPacks()) {
            this.optionalResourcePacksEvent = new SessionLoadOptionalResourcePacksEventImpl(session, new HashMap<>(Registries.OPTIONAL_RESOURCE_PACKS.get()));
            this.geyser.eventBus().fire(this.optionalResourcePacksEvent);

            try (Jedis resource = GeyserImpl.getPool().getResource()){
                String packs = resource.get("HeyCore:Resource:" + session.getAuthData().uuid());

                if (packs != null){
                    List<String> packsUUID = new ArrayList<>();
                    for (String packId : packs.split(",")) {
                        String packUUID = GeyserImpl.getInstance().getOptionalPacks().get(Integer.valueOf(packId));
                        ResourcePack pack = this.optionalResourcePacksEvent.getPacks().get(packUUID);
                        if (pack != null) {
                            PackCodec codec = pack.codec();
                            ResourcePackManifest.Header header = pack.manifest().header();
                            resourcePacksInfo.getResourcePackInfos().add(new ResourcePacksInfoPacket.Entry(
                                    header.uuid().toString(), header.version().toString(), codec.size(), pack.contentKey(),
                                    "", header.uuid().toString(), false, false));
                            packsUUID.add(packUUID);
                        }
                    }
                    session.setOptionPacksUuid(packsUUID);
                }
            }
        }
        resourcePacksInfo.setForcedToAccept(GeyserImpl.getInstance().getConfig().isForceResourcePacks());
        session.sendUpstreamPacket(resourcePacksInfo);

        GeyserLocale.loadGeyserLocale(session.locale());
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(ResourcePackClientResponsePacket packet) {
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

                for (ResourcePack pack : this.behaviorPacksEvent.resourcePacks()) {
                    ResourcePackManifest.Header header = pack.manifest().header();
                    stackPacket.getBehaviorPacks().add(new ResourcePackStackPacket.Entry(header.uuid().toString(), header.version().toString(), ""));
                }

                for (ResourcePack pack : this.resourcePackLoadEvent.resourcePacks()) {
                    ResourcePackManifest.Header header = pack.manifest().header();
                    stackPacket.getResourcePacks().add(new ResourcePackStackPacket.Entry(header.uuid().toString(), header.version().toString(), ""));
                }

                if (geyser.getConfig().getOptionalPacks().isEnableOptionalPacks()) {
                    for (String packUuid : session.getOptionPacksUuid()) {
                        for (ResourcePack pack : this.optionalResourcePacksEvent.resourcePacks()) {
                            ResourcePackManifest.Header header = pack.manifest().header();
                            if (packUuid.equals(header.uuid().toString())) {
                                stackPacket.getResourcePacks().add(new ResourcePackStackPacket.Entry(header.uuid().toString(), header.version().toString(), ""));
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

                // Required for experimental 1.21 features
                stackPacket.getExperiments().add(new ExperimentData("updateAnnouncedLive2023", true));
                stackPacket.getExperiments().add(new ExperimentData("next_major_update", true));
                stackPacket.getExperiments().add(new ExperimentData("sniffer", true));

                session.sendUpstreamPacket(stackPacket);
                break;

            default:
                session.disconnect("disconnectionScreen.resourcePack");
                break;
        }

        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(ModalFormResponsePacket packet) {
        session.executeInEventLoop(() -> session.getFormCache().handleResponse(packet));
        return PacketSignal.HANDLED;
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
        PendingMicrosoftAuthentication.AuthenticationTask task = geyser.getPendingMicrosoftAuthentication().getTask(session.getAuthData().xuid());
        if (task != null) {
            return task.getAuthentication().isDone() && session.onMicrosoftLoginComplete(task);
        }

        return false;
    }

    @Override
    public PacketSignal handle(MovePlayerPacket packet) {
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
    public PacketSignal handle(ResourcePackChunkRequestPacket packet) {
        ResourcePackChunkDataPacket data = new ResourcePackChunkDataPacket();
        ResourcePack pack;
        ResourcePack resourcePack = this.resourcePackLoadEvent.getPacks().get(packet.getPackId().toString());
        ResourcePack behaviorPack = this.behaviorPacksEvent.getPacks().get(packet.getPackId().toString());
        ResourcePack optionalPack = null;

        if (geyser.getConfig().getOptionalPacks().isEnableOptionalPacks()) {
            optionalPack = this.optionalResourcePacksEvent.getPacks().get(packet.getPackId().toString());
        }

        if (optionalPack != null) {
            pack = optionalPack;
        } else if (behaviorPack != null) {
            pack = behaviorPack;
        } else {
            pack = resourcePack;
        }
        PackCodec codec = pack.codec();

        data.setChunkIndex(packet.getChunkIndex());
        data.setProgress((long) packet.getChunkIndex() * GeyserResourcePack.CHUNK_SIZE);
        data.setPackVersion(packet.getPackVersion());
        data.setPackId(packet.getPackId());

        int offset = packet.getChunkIndex() * GeyserResourcePack.CHUNK_SIZE;
        long remainingSize = codec.size() - offset;
        byte[] packData = new byte[(int) MathUtils.constrain(remainingSize, 0, GeyserResourcePack.CHUNK_SIZE)];

        try (SeekableByteChannel channel = codec.serialize(pack)) {
            channel.position(offset);
            channel.read(ByteBuffer.wrap(packData, 0, packData.length));
        } catch (IOException e) {
            e.printStackTrace();
        }

        data.setData(Unpooled.wrappedBuffer(packData));

        session.sendUpstreamPacket(data);

        // Check if it is the last chunk and send next pack in queue when available.
        if (remainingSize <= GeyserResourcePack.CHUNK_SIZE && !packsToSent.isEmpty()) {
            sendPackDataInfo(packsToSent.pop());
        }

        return PacketSignal.HANDLED;
    }

    private void sendPackDataInfo(String id) {
        ResourcePackDataInfoPacket data = new ResourcePackDataInfoPacket();
        String[] packID = id.split("_");
        ResourcePack pack;
        if (this.resourcePackLoadEvent.getPacks().get(packID[0]) != null) {
            pack = this.resourcePackLoadEvent.getPacks().get(packID[0]);
            data.setType(ResourcePackType.RESOURCES);
        } else if (geyser.getConfig().getOptionalPacks().isEnableOptionalPacks() && this.optionalResourcePacksEvent.getPacks().get(packID[0]) != null) {
            pack = this.optionalResourcePacksEvent.getPacks().get(packID[0]);
            data.setType(ResourcePackType.RESOURCES);
        } else {
            pack = this.behaviorPacksEvent.getPacks().get(packID[0]);
            data.setType(ResourcePackType.DATA_ADD_ON);
        }
        PackCodec codec = pack.codec();
        ResourcePackManifest.Header header = pack.manifest().header();

        data.setPackId(header.uuid());
        int chunkCount = (int) Math.ceil(codec.size() / (double) GeyserResourcePack.CHUNK_SIZE);
        data.setChunkCount(chunkCount);
        data.setCompressedPackSize(codec.size());
        data.setMaxChunkSize(GeyserResourcePack.CHUNK_SIZE);
        data.setHash(codec.sha256());
        data.setPackVersion(packID[1]);
        data.setPremium(false);

        session.sendUpstreamPacket(data);
    }
}
