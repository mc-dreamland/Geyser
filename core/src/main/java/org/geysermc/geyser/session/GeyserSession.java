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

package org.geysermc.geyser.session;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.service.MsaAuthenticationService;
import com.github.steveice10.mc.protocol.MinecraftConstants;
import com.github.steveice10.mc.protocol.MinecraftProtocol;
import com.github.steveice10.mc.protocol.data.ProtocolState;
import com.github.steveice10.mc.protocol.data.UnexpectedEncryptionException;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.Pose;
import com.github.steveice10.mc.protocol.data.game.entity.object.Direction;
import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import com.github.steveice10.mc.protocol.data.game.entity.player.HandPreference;
import com.github.steveice10.mc.protocol.data.game.entity.player.PlayerAction;
import com.github.steveice10.mc.protocol.data.game.setting.ChatVisibility;
import com.github.steveice10.mc.protocol.data.game.setting.SkinPart;
import com.github.steveice10.mc.protocol.data.game.statistic.CustomStatistic;
import com.github.steveice10.mc.protocol.data.game.statistic.Statistic;
import com.github.steveice10.mc.protocol.packet.common.serverbound.ServerboundClientInformationPacket;
import com.github.steveice10.mc.protocol.packet.handshake.serverbound.ClientIntentionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundPlayerAbilitiesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import com.github.steveice10.mc.protocol.packet.login.serverbound.ServerboundCustomQueryAnswerPacket;
import com.github.steveice10.packetlib.BuiltinFlags;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.ConnectedEvent;
import com.github.steveice10.packetlib.event.session.DisconnectedEvent;
import com.github.steveice10.packetlib.event.session.PacketErrorEvent;
import com.github.steveice10.packetlib.event.session.PacketSendingEvent;
import com.github.steveice10.packetlib.event.session.SessionAdapter;
import com.github.steveice10.packetlib.packet.Packet;
import com.github.steveice10.packetlib.tcp.TcpClientSession;
import com.github.steveice10.packetlib.tcp.TcpSession;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.common.value.qual.IntRange;
import org.cloudburstmc.math.vector.*;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.BedrockDisconnectReasons;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketDefinition;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketSerializer;
import org.cloudburstmc.protocol.bedrock.codec.v589.serializer.StartGameSerializer_v589;
import org.cloudburstmc.protocol.bedrock.codec.v630.Bedrock_v630;
import org.cloudburstmc.protocol.bedrock.data.*;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission;
import org.cloudburstmc.protocol.bedrock.data.command.SoftEnumUpdateType;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;
import org.cloudburstmc.protocol.common.util.VarInts;
import org.geysermc.api.util.BedrockPlatform;
import org.geysermc.api.util.InputMode;
import org.geysermc.api.util.UiProfile;
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.util.FormBuilder;
import org.geysermc.floodgate.crypto.FloodgateCipher;
import org.geysermc.floodgate.util.BedrockData;
import org.geysermc.geyser.Constants;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.bedrock.camera.CameraShake;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.entity.type.GeyserEntity;
import org.geysermc.geyser.api.entity.type.player.GeyserPlayerEntity;
import org.geysermc.geyser.api.event.bedrock.SessionDisconnectEvent;
import org.geysermc.geyser.api.event.bedrock.SessionLoginEvent;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.api.network.RemoteServer;
import org.geysermc.geyser.api.util.PlatformType;
import org.geysermc.geyser.command.GeyserCommandSource;
import org.geysermc.geyser.configuration.EmoteOffhandWorkaroundOption;
import org.geysermc.geyser.configuration.GeyserConfiguration;
import org.geysermc.geyser.entity.EntityDefinitions;
import org.geysermc.geyser.entity.attribute.GeyserAttributeType;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.ItemFrameEntity;
import org.geysermc.geyser.entity.type.Tickable;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import org.geysermc.geyser.erosion.AbstractGeyserboundPacketHandler;
import org.geysermc.geyser.erosion.GeyserboundHandshakePacketHandler;
import org.geysermc.geyser.inventory.Inventory;
import org.geysermc.geyser.inventory.PlayerInventory;
import org.geysermc.geyser.inventory.recipe.GeyserRecipe;
import org.geysermc.geyser.inventory.recipe.GeyserStonecutterData;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.level.JavaDimension;
import org.geysermc.geyser.level.WorldManager;
import org.geysermc.geyser.level.physics.CollisionManager;
import org.geysermc.geyser.network.netty.GeyserServer;
import org.geysermc.geyser.network.netty.LocalSession;
import org.geysermc.geyser.network.netty.handler.RakConnectionRequestHandler;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.type.BlockMappings;
import org.geysermc.geyser.registry.type.ItemMappings;
import org.geysermc.geyser.session.auth.AuthData;
import org.geysermc.geyser.session.auth.BedrockClientData;
import org.geysermc.geyser.session.auth.NeteaseAuthData;
import org.geysermc.geyser.session.cache.*;
import org.geysermc.geyser.skin.FloodgateSkinUploader;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.text.MinecraftLocale;
import org.geysermc.geyser.text.TextDecoration;
import org.geysermc.geyser.translator.inventory.InventoryTranslator;
import org.geysermc.geyser.translator.text.MessageTranslator;
import org.geysermc.geyser.util.*;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class GeyserSession implements GeyserConnection, GeyserCommandSource {

    private final GeyserImpl geyser;
    private final UpstreamSession upstream;
    private DownstreamSession downstream;
    /**
     * The loop where all packets and ticking is processed to prevent concurrency issues.
     * If this is manually called, ensure that any exceptions are properly handled.
     */
    private final EventLoop eventLoop;
    @Setter
    private AuthData authData;
    @Setter
    private NeteaseAuthData neteaseData;
    @Setter
    private BedrockClientData clientData;
    /**
     * Used for Floodgate skin uploading
     */
    @Setter
    private List<String> certChainData;

    @NonNull
    @Setter
    private AbstractGeyserboundPacketHandler erosionHandler;

    @Accessors(fluent = true)
    @Setter
    private RemoteServer remoteServer;

    private final SessionPlayerEntity playerEntity;

    private final AdvancementsCache advancementsCache;
    private final BookEditCache bookEditCache;
    private final ChunkCache chunkCache;
    private final EntityCache entityCache;
    private final EntityEffectCache effectCache;
    private final FormCache formCache;
    private final LodestoneCache lodestoneCache;
    private final PistonCache pistonCache;
    private final PreferencesCache preferencesCache;
    private final SkullCache skullCache;
    private final TagCache tagCache;
    private final WorldCache worldCache;

    @Setter
    private TeleportCache unconfirmedTeleport;

    private final WorldBorder worldBorder;
    /**
     * Whether simulated fog has been sent to the client or not.
     */
    private boolean isInWorldBorderWarningArea = false;

    private final PlayerInventory playerInventory;
    @Setter
    private Inventory openInventory;
    @Setter
    private boolean closingInventory;

    @Setter
    private InventoryTranslator inventoryTranslator = InventoryTranslator.PLAYER_INVENTORY_TRANSLATOR;

    /**
     * Use {@link #getNextItemNetId()} instead for consistency
     */
    @Getter(AccessLevel.NONE)
    private final AtomicInteger itemNetId = new AtomicInteger(2);

    @Setter
    private ScheduledFuture<?> craftingGridFuture;

    /**
     * Stores session collision
     */
    private final CollisionManager collisionManager;

    /**
     * Stores the block mappings for this specific version.
     */
    @Setter
    private BlockMappings blockMappings;

    /**
     * Stores the item translations for this specific version.
     */
    @Setter
    private ItemMappings itemMappings;

    private final Long2ObjectMap<ClientboundMapItemDataPacket> storedMaps = new Long2ObjectOpenHashMap<>();

    /**
     * Required to decode biomes correctly.
     */
    @Setter
    private int biomeGlobalPalette;
    /**
     * Stores the map between Java and Bedrock biome network IDs.
     */
    @Setter
    private int[] biomeTranslations = null;

    /**
     * A map of Vector3i positions to Java entities.
     * Used for translating Bedrock block actions to Java entity actions.
     */
    private final Map<Vector3i, ItemFrameEntity> itemFrameCache = new Object2ObjectOpenHashMap<>();

    /**
     * Stores a list of all lectern locations and their block entity tags.
     * See {@link WorldManager#sendLecternData(GeyserSession, int, int, int)}
     * for more information.
     */
    private final Set<Vector3i> lecternCache;

    /**
     * A list of all players that have a player head on with a custom texture.
     * Our workaround for these players is to give them a custom skin and geometry to emulate wearing a custom skull.
     */
    private final Set<UUID> playerWithCustomHeads = new ObjectOpenHashSet<>();

    @Setter
    private boolean droppingLecternBook;

    @Setter
    private Vector2i lastChunkPosition = null;
    @Setter
    private int clientRenderDistance = -1;
    private int serverRenderDistance = -1;

    // Exposed for GeyserConnect usage
    protected boolean sentSpawnPacket;

    private boolean loggedIn;
    private boolean loggingIn;

    @Setter
    private boolean spawned;
    /**
     * Accessed on the initial Java and Bedrock packet processing threads
     */
    private volatile boolean closed;

    @Setter
    private GameMode gameMode = GameMode.SURVIVAL;

    /**
     * Keeps track of the world name for respawning.
     */
    @Setter
    private String worldName = null;
    /**
     * As of Java 1.19.3, the client only uses these for commands.
     */
    @Setter
    private String[] levels;

    private boolean sneaking;

    /**
     * Stores the Java pose that the server and/or Geyser believes the player currently has.
     */
    @Setter
    private Pose pose = Pose.STANDING;

    @Setter
    private boolean sprinting;

    /**
     * Whether the player is swimming in water.
     * Used to update speed when crawling.
     */
    @Setter
    private boolean swimmingInWater;

    /**
     * Tracks the original speed attribute.
     * <p>
     * We need to do this in order to emulate speeds when sneaking under 1.5-blocks-tall areas if the player isn't sneaking,
     * and when crawling.
     */
    @Setter
    private float originalSpeedAttribute;

    /**
     * The dimension of the player.
     * As all entities are in the same world, this can be safely applied to all other entities.
     */
    @Setter
    private String dimension = DimensionUtils.OVERWORLD;
    @MonotonicNonNull
    @Setter
    private JavaDimension dimensionType = null;
    /**
     * All dimensions that the client could possibly connect to.
     */
    private final Map<String, JavaDimension> dimensions = new Object2ObjectOpenHashMap<>(3);

    private final Int2ObjectMap<TextDecoration> chatTypes = new Int2ObjectOpenHashMap<>(7);

    @Setter
    private int breakingBlock;

    @Setter
    private Vector3i lastBlockPlacePosition;

    @Setter
    private String lastBlockPlacedId;

    @Setter
    private boolean interacting;

    /**
     * Stores the last position of the block the player interacted with. This can either be a block that the client
     * placed or an existing block the player interacted with (for example, a chest). <br>
     * Initialized as (0, 0, 0) so it is always not-null.
     */
    @Setter
    private Vector3i lastInteractionBlockPosition = Vector3i.ZERO;

    /**
     * Stores the position of the player the last time they interacted.
     * Used to verify that the player did not move since their last interaction. <br>
     * Initialized as (0, 0, 0) so it is always not-null.
     */
    @Setter
    private Vector3f lastInteractionPlayerPosition = Vector3f.ZERO;

    /**
     * The entity that the client is currently looking at.
     */
    @Setter
    private Entity mouseoverEntity;

    /**
     * Stores all Java recipes by recipe identifier, and matches them to all possible Bedrock recipe identifiers.
     * They are not 1:1, since Bedrock can have multiple recipes for the same Java recipe.
     */
    @Setter
    private Map<String, List<String>> javaToBedrockRecipeIds;

    @Setter
    private Int2ObjectMap<GeyserRecipe> craftingRecipes;
    private final AtomicInteger lastRecipeNetId;

    /**
     * Saves a list of all stonecutter recipes, for use in a stonecutter inventory.
     * The key is the Java ID of the item; the values are all the possible outputs' Java IDs sorted by their string identifier
     */
    @Setter
    private Int2ObjectMap<GeyserStonecutterData> stonecutterRecipes;

    /**
     * Whether to work around 1.13's different behavior in villager trading menus.
     */
    @Setter
    private boolean emulatePost1_13Logic = true;
    /**
     * Starting in 1.17, Java servers expect the <code>carriedItem</code> parameter of the serverbound click container
     * packet to be the current contents of the mouse after the transaction has been done. 1.16 expects the clicked slot
     * contents before any transaction is done. With the current ViaVersion structure, if we do not send what 1.16 expects
     * and send multiple click container packets, then successive transactions will be rejected.
     */
    @Setter
    private boolean emulatePost1_16Logic = true;
    @Setter
    private boolean emulatePost1_18Logic = true;

    /**
     * Whether to emulate pre-1.20 smithing table behavior.
     * Adapts ViaVersion's furnace UI to one Bedrock can use.
     * See {@link org.geysermc.geyser.translator.inventory.OldSmithingTableTranslator}.
     */
    @Setter
    private boolean oldSmithingTable = false;

    /**
     * The current attack speed of the player. Used for sending proper cooldown timings.
     * Setting a default fixes cooldowns not showing up on a fresh world.
     */
    @Setter
    private double attackSpeed = 4.0d;
    /**
     * The time of the last hit. Used to gauge how long the cooldown is taking.
     * This is a session variable in order to prevent more scheduled threads than necessary.
     */
    @Setter
    private long lastHitTime;

    /**
     * Saves if the client is steering left on a boat.
     */
    @Setter
    private boolean steeringLeft;
    /**
     * Saves if the client is steering right on a boat.
     */
    @Setter
    private boolean steeringRight;

    /**
     * Store the last time the player interacted. Used to fix a right-click spam bug.
     * See <a href="https://github.com/GeyserMC/Geyser/issues/503">this</a> for context.
     */
    @Setter
    private long lastInteractionTime;

    /**
     * Stores when the player started to break a block. Used to allow correct break time for custom blocks.
     */
    @Setter
    private long blockBreakStartTime;

    /**
     * Stores whether the player intended to place a bucket.
     */
    @Setter
    private boolean placedBucket;

    /**
     * Used to send a movement packet every three seconds if the player hasn't moved. Prevents timeouts when AFK in certain instances.
     */
    @Setter
    private long lastMovementTimestamp = System.currentTimeMillis();

    /**
     * Used to send a ServerboundMoveVehiclePacket for every PlayerInputPacket after idling on a boat/horse for more than 100ms
     */
    @Setter
    private long lastVehicleMoveTimestamp = System.currentTimeMillis();

    /**
     * Counts how many ticks have occurred since an arm animation started.
     * -1 means there is no active arm swing; -2 means an arm swing will start in a tick.
     */
    private int armAnimationTicks = -1;

    /**
     * Controls whether the daylight cycle gamerule has been sent to the client, so the sun/moon remain motionless.
     */
    private boolean daylightCycle = true;

    private boolean reducedDebugInfo = false;

    /**
     * The op permission level set by the server
     */
    @Setter
    private int opPermissionLevel = 0;

    /**
     * If the current player can fly
     */
    @Setter
    private boolean canFly = false;

    /**
     * If the current player is flying
     */
    private boolean flying = false;

    @Setter
    private boolean instabuild = false;

    @Setter
    private float flySpeed;
    @Setter
    private float walkSpeed;

    /**
     * Caches current rain status.
     */
    @Setter
    private boolean raining = false;

    /**
     * Caches current thunder status.
     */
    @Setter
    private boolean thunder = false;

    /**
     * Stores a map of all statistics sent from the server.
     * The server only sends new statistics back to us, so in order to show all statistics we need to cache existing ones.
     */
    private final Object2IntMap<Statistic> statistics = new Object2IntOpenHashMap<>(0);

    /**
     * Whether we're expecting statistics to be sent back to us.
     */
    @Setter
    private boolean waitingForStatistics = false;

    /**
     * All fog effects that are currently applied to the client.
     */
    private final Set<String> appliedFog = new HashSet<>();

    private final Set<UUID> emotes;

    /**
     * Whether advanced tooltips will be added to the player's items.
     */
    @Setter
    private boolean advancedTooltips = false;

    /**
     * The thread that will run every 50 milliseconds - one Minecraft tick.
     */
    private ScheduledFuture<?> tickThread = null;

    /**
     * Used to return the player to their original rotation after using an item in BedrockInventoryTransactionTranslator
     */
    @Setter
    private ScheduledFuture<?> lookBackScheduledFuture = null;

    /**
     * Used to return players back to their vehicles if the server doesn't want them unmounting.
     */
    @Setter
    private ScheduledFuture<?> mountVehicleScheduledFuture = null;

    /**
     * A cache of IDs from ClientboundKeepAlivePackets that have been sent to the Bedrock client, but haven't been returned to the server.
     * Only used if {@link GeyserConfiguration#isForwardPlayerPing()} is enabled.
     */
    private final Queue<Long> keepAliveCache = new ConcurrentLinkedQueue<>();

    private MinecraftProtocol protocol;

    private final HashMap<UUID, String> cachedPlayerList;
    @Setter
    @Getter
    private boolean sendSelf = false;
    @Setter
    private List<String> optionPacksUuid;
    @Setter
    private boolean noUnloadChunk = true;
    @Setter
    private boolean quickSwitchDimension = true;
    @Setter
    private int lastInteractionFace;
    @Setter
    private long lastAttackTime = 0;
    @Setter
    private int qucikAttackTimes = 0;

    @Setter
    private double breakingBlockTime;

    public GeyserSession(GeyserImpl geyser, BedrockServerSession bedrockServerSession, EventLoop eventLoop) {
        this.geyser = geyser;
        this.upstream = new UpstreamSession(bedrockServerSession);
        this.eventLoop = eventLoop;

        this.erosionHandler = new GeyserboundHandshakePacketHandler(this);

        this.advancementsCache = new AdvancementsCache(this);
        this.bookEditCache = new BookEditCache(this);
        this.chunkCache = new ChunkCache(this);
        this.entityCache = new EntityCache(this);
        this.effectCache = new EntityEffectCache();
        this.formCache = new FormCache(this);
        this.lodestoneCache = new LodestoneCache();
        this.pistonCache = new PistonCache(this);
        this.preferencesCache = new PreferencesCache(this);
        this.skullCache = new SkullCache(this);
        this.tagCache = new TagCache();
        this.worldCache = new WorldCache(this);

        this.worldBorder = new WorldBorder(this);

        this.collisionManager = new CollisionManager(this);

        this.playerEntity = new SessionPlayerEntity(this);
        collisionManager.updatePlayerBoundingBox(this.playerEntity.getPosition());

        this.playerInventory = new PlayerInventory();
        this.openInventory = null;
        this.craftingRecipes = new Int2ObjectOpenHashMap<>();
        this.javaToBedrockRecipeIds = new Object2ObjectOpenHashMap<>();
        this.lastRecipeNetId = new AtomicInteger(1);

        this.spawned = false;
        this.loggedIn = false;

        if (geyser.getWorldManager().shouldExpectLecternHandled(this)) {
            // Unneeded on these platforms
            this.lecternCache = null;
        } else {
            this.lecternCache = new ObjectOpenHashSet<>();
        }

        if (geyser.getConfig().getEmoteOffhandWorkaround() != EmoteOffhandWorkaroundOption.NO_EMOTES) {
            this.emotes = new HashSet<>();
            geyser.getSessionManager().getSessions().values().forEach(player -> this.emotes.addAll(player.getEmotes()));
        } else {
            this.emotes = null;
        }

        this.remoteServer = geyser.defaultRemoteServer();
        this.cachedPlayerList = new HashMap<>();
        this.optionPacksUuid = new ArrayList<>();
    }

    /**
     * Send all necessary packets to load Bedrock into the server
     */
    public void connect() {
        startGame();
        sentSpawnPacket = true;

        // Set the hardcoded shield ID to the ID we just defined in StartGamePacket
        // upstream.getSession().getHardcodedBlockingId().set(this.itemMappings.getStoredItems().shield().getBedrockId());

        if (GeyserImpl.getInstance().getConfig().isAddNonBedrockItems()) {
            ItemComponentPacket componentPacket = new ItemComponentPacket();
            componentPacket.getItems().addAll(itemMappings.getComponentItemData());
            upstream.sendPacket(componentPacket);
        }

        ChunkUtils.sendEmptyChunks(this, playerEntity.getPosition().toInt(), 0, false);

        BiomeDefinitionListPacket biomeDefinitionListPacket = new BiomeDefinitionListPacket();
        biomeDefinitionListPacket.setDefinitions(Registries.BIOMES_NBT.get());
        upstream.sendPacket(biomeDefinitionListPacket);

        AvailableEntityIdentifiersPacket entityPacket = new AvailableEntityIdentifiersPacket();
        entityPacket.setIdentifiers(Registries.BEDROCK_ENTITY_IDENTIFIERS.get());
        upstream.sendPacket(entityPacket);

        CreativeContentPacket creativePacket = new CreativeContentPacket();
        creativePacket.setContents(this.itemMappings.getCreativeItems());
        upstream.sendPacket(creativePacket);

        // Potion mixes are registered by default, as they are needed to be able to put ingredients into the brewing stand.
        CraftingDataPacket craftingDataPacket = new CraftingDataPacket();
        craftingDataPacket.setCleanRecipes(true);
        craftingDataPacket.getPotionMixData().addAll(Registries.POTION_MIXES.forVersion(this.upstream.getProtocolVersion()));
        upstream.sendPacket(craftingDataPacket);

        PlayStatusPacket playStatusPacket = new PlayStatusPacket();
        playStatusPacket.setStatus(PlayStatusPacket.Status.PLAYER_SPAWN);
        upstream.sendPacket(playStatusPacket);

        UpdateAttributesPacket attributesPacket = new UpdateAttributesPacket();
        attributesPacket.setRuntimeEntityId(getPlayerEntity().getGeyserId());
        // Default move speed
        // Bedrock clients move very fast by default until they get an attribute packet correcting the speed
        attributesPacket.setAttributes(Collections.singletonList(
                new AttributeData("minecraft:movement", 0.0f, 1024f, 0.1f, 0.1f)));
        upstream.sendPacket(attributesPacket);

        GameRulesChangedPacket gamerulePacket = new GameRulesChangedPacket();
        // Only allow the server to send health information
        // Setting this to false allows natural regeneration to work false but doesn't break it being true
        gamerulePacket.getGameRules().add(new GameRuleData<>("naturalregeneration", false));
        // Don't let the client modify the inventory on death
        // Setting this to true allows keep inventory to work if enabled but doesn't break functionality being false
        gamerulePacket.getGameRules().add(new GameRuleData<>("keepinventory", true));
        // Ensure client doesn't try and do anything funky; the server handles this for us
        gamerulePacket.getGameRules().add(new GameRuleData<>("spawnradius", 0));
        // Recipe unlocking
        gamerulePacket.getGameRules().add(new GameRuleData<>("recipesunlock", true));
        upstream.sendPacket(gamerulePacket);
    }

    public void authenticate(String username) {
        if (loggedIn) {
            geyser.getLogger().severe(GeyserLocale.getLocaleStringLog("geyser.auth.already_loggedin", username));
            return;
        }

        loggingIn = true;
        // Always replace spaces with underscores to avoid illegal nicknames, e.g. with GeyserConnect
        protocol = new MinecraftProtocol(username.replace(' ', '_'));

        try {
            connectDownstream();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void authenticateWithRefreshToken(String refreshToken) {
        if (loggedIn) {
            geyser.getLogger().severe(GeyserLocale.getLocaleStringLog("geyser.auth.already_loggedin", getAuthData().name()));
            return;
        }

        loggingIn = true;

        CompletableFuture.supplyAsync(() -> {
            MsaAuthenticationService service = new MsaAuthenticationService(GeyserImpl.OAUTH_CLIENT_ID);
            service.setRefreshToken(refreshToken);
            try {
                service.login();
            } catch (RequestException e) {
                geyser.getLogger().error("Error while attempting to use refresh token for " + bedrockUsername() + "!", e);
                return Boolean.FALSE;
            }

            GameProfile profile = service.getSelectedProfile();
            if (profile == null) {
                // Java account is offline
                disconnect(GeyserLocale.getPlayerLocaleString("geyser.network.remote.invalid_account", clientData.getLanguageCode()));
                return null;
            }

            protocol = new MinecraftProtocol(profile, service.getAccessToken());
            geyser.saveRefreshToken(bedrockUsername(), service.getRefreshToken());
            return Boolean.TRUE;
        }).whenComplete((successful, ex) -> {
            if (this.closed) {
                return;
            }
            if (successful == Boolean.FALSE) {
                // The player is waiting for a spawn packet, so let's spawn them in now to show them forms
                connect();
                // Will be cached for after login
                LoginEncryptionUtils.buildAndShowTokenExpiredWindow(this);
                return;
            }

            try {
                connectDownstream();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    public void authenticateWithMicrosoftCode() {
        authenticateWithMicrosoftCode(false);
    }

    /**
     * Present a form window to the user asking to log in with another web browser
     */
    public void authenticateWithMicrosoftCode(boolean offlineAccess) {
        if (loggedIn) {
            geyser.getLogger().severe(GeyserLocale.getLocaleStringLog("geyser.auth.already_loggedin", getAuthData().name()));
            return;
        }

        loggingIn = true;

        // This just looks cool
        SetTimePacket packet = new SetTimePacket();
        packet.setTime(16000);
        sendUpstreamPacket(packet);

        final PendingMicrosoftAuthentication.AuthenticationTask task = geyser.getPendingMicrosoftAuthentication().getOrCreateTask(
                getAuthData().xuid()
        );
        task.setOnline(true);
        task.resetTimer();

        if (task.getAuthentication().isDone()) {
            onMicrosoftLoginComplete(task);
        } else {
            task.getCode(offlineAccess).whenComplete((response, ex) -> {
                boolean connected = !closed;
                if (ex != null) {
                    if (connected) {
                        geyser.getLogger().error("Failed to get Microsoft auth code", ex);
                        disconnect(ex.toString());
                    }
                    task.cleanup(); // error getting auth code -> clean up immediately
                } else if (connected) {
                    LoginEncryptionUtils.buildAndShowMicrosoftCodeWindow(this, response);
                    task.getAuthentication().whenComplete((r, $) -> onMicrosoftLoginComplete(task));
                }
            });
        }
    }

    /**
     * If successful, also begins connecting to the Java server.
     */
    public boolean onMicrosoftLoginComplete(PendingMicrosoftAuthentication.AuthenticationTask task) {
        if (closed) {
            return false;
        }
        task.cleanup(); // player is online -> remove pending authentication immediately
        Throwable ex = task.getLoginException();
        if (ex != null) {
            geyser.getLogger().error("Failed to log in with Microsoft code!", ex);
            disconnect(ex.toString());
        } else {
            MsaAuthenticationService service = task.getMsaAuthenticationService();
            GameProfile selectedProfile = service.getSelectedProfile();
            if (selectedProfile == null) {
                disconnect(GeyserLocale.getPlayerLocaleString(
                        "geyser.network.remote.invalid_account",
                        clientData.getLanguageCode()
                ));
            } else {
                this.protocol = new MinecraftProtocol(
                        selectedProfile,
                        service.getAccessToken()
                );
                try {
                    connectDownstream();
                } catch (Throwable t) {
                    t.printStackTrace();
                    return false;
                }

                // Save our refresh token for later use
                geyser.saveRefreshToken(bedrockUsername(), service.getRefreshToken());
                return true;
            }
        }
        return false;
    }

    /**
     * After getting whatever credentials needed, we attempt to join the Java server.
     */
    private void connectDownstream() {
        SessionLoginEvent loginEvent = new SessionLoginEvent(this, remoteServer);
        GeyserImpl.getInstance().eventBus().fire(loginEvent);
        if (loginEvent.isCancelled()) {
            String disconnectReason = loginEvent.disconnectReason() == null ?
                    BedrockDisconnectReasons.DISCONNECTED : loginEvent.disconnectReason();
            disconnect(disconnectReason);
            return;
        }

        this.remoteServer = loginEvent.remoteServer();
        boolean floodgate = this.remoteServer.authType() == AuthType.FLOODGATE;

        // Start ticking
        tickThread = eventLoop.scheduleAtFixedRate(this::tick, 50, 50, TimeUnit.MILLISECONDS);

        TcpSession downstream;
        if (geyser.getBootstrap().getSocketAddress() != null) {
            // We're going to connect through the JVM and not through TCP
            downstream = new LocalSession(this.remoteServer.address(), this.remoteServer.port(),
                    geyser.getBootstrap().getSocketAddress(), upstream.getAddress().getAddress().getHostAddress(),
                    this.protocol, this.protocol.createHelper());
            this.downstream = new DownstreamSession(downstream);
        } else {
            downstream = new TcpClientSession(this.remoteServer.address(), this.remoteServer.port(), this.protocol);
            this.downstream = new DownstreamSession(downstream);

            boolean resolveSrv = false;
            try {
                resolveSrv = this.remoteServer.resolveSrv();
            } catch (AbstractMethodError | NoSuchMethodError ignored) {
                // Ignore if the method doesn't exist
                // This will happen with extensions using old APIs
            }
            this.downstream.getSession().setFlag(BuiltinFlags.ATTEMPT_SRV_RESOLVE, resolveSrv);
        }

        if (geyser.getConfig().getRemote().isUseProxyProtocol()) {
            downstream.setFlag(BuiltinFlags.ENABLE_CLIENT_PROXY_PROTOCOL, true);
            downstream.setFlag(BuiltinFlags.CLIENT_PROXIED_ADDRESS, upstream.getAddress());
        }
        if (geyser.getConfig().isForwardPlayerPing()) {
            // Let Geyser handle sending the keep alive
            downstream.setFlag(MinecraftConstants.AUTOMATIC_KEEP_ALIVE_MANAGEMENT, false);
        }
        downstream.addListener(new SessionAdapter() {
            @Override
            public void packetSending(PacketSendingEvent event) {
                //todo move this somewhere else
                if (event.getPacket() instanceof ClientIntentionPacket clientIntentionPacket) {
                    String addressSuffix;
                    if (floodgate) {
                        byte[] encryptedData;

                        try {
                            FloodgateSkinUploader skinUploader = geyser.getSkinUploader();
                            FloodgateCipher cipher = geyser.getCipher();

                            String bedrockAddress = upstream.getAddress().getAddress().getHostAddress();
                            // both BungeeCord and Velocity remove the IPv6 scope (if there is one) for Spigot
                            int ipv6ScopeIndex = bedrockAddress.indexOf('%');
                            if (ipv6ScopeIndex != -1) {
                                bedrockAddress = bedrockAddress.substring(0, ipv6ScopeIndex);
                            }

                            // 在此处处理网易proxy 代理，获取玩家真实IP

                            int port = upstream.getAddress().getPort();
                            String s = UdpRealIp.IP_PORT_WITH_RealIP.get(bedrockAddress + ":" + port);

                            encryptedData = cipher.encryptFromString(BedrockData.of(
                                    clientData.getGameVersion(),
                                    authData.name(),
                                    authData.uuid().toString(),
                                    clientData.getDeviceOs().ordinal(),
                                    clientData.getLanguageCode(),
                                    clientData.getUiProfile().ordinal(),
                                    clientData.getCurrentInputMode().ordinal(),
                                    s == null ? bedrockAddress : s,
                                    skinUploader.getId(),
                                    skinUploader.getVerifyCode()
                            ).toString());
                        } catch (Exception e) {
                            geyser.getLogger().error(GeyserLocale.getLocaleStringLog("geyser.auth.floodgate.encrypt_fail"), e);
                            disconnect(GeyserLocale.getPlayerLocaleString("geyser.auth.floodgate.encryption_fail", getClientData().getLanguageCode()));
                            return;
                        }

                        addressSuffix = '\0' + new String(encryptedData, StandardCharsets.UTF_8);
                    } else {
                        addressSuffix = "";
                    }

                    ClientIntentionPacket intentionPacket = event.getPacket();

                    String address;
                    if (geyser.getConfig().getRemote().isForwardHost()) {
                        address = clientData.getServerAddress().split(":")[0];
                    } else {
                        address = intentionPacket.getHostname();
                    }

                    event.setPacket(intentionPacket.withHostname(address + addressSuffix));
                }
            }

            @Override
            public void connected(ConnectedEvent event) {
                loggingIn = false;
                loggedIn = true;

                if (downstream instanceof LocalSession) {
                    // Connected directly to the server
                    geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.network.remote.connect_internal",
                            authData.name(), protocol.getProfile().getName()));
                } else {
                    // Connected to an IP address
                    geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.network.remote.connect",
                            authData.name(), protocol.getProfile().getName(), remoteServer.address()));
                }

                UUID uuid = protocol.getProfile().getId();
                if (uuid == null) {
                    // Set what our UUID *probably* is going to be
                    if (remoteServer.authType() == AuthType.FLOODGATE) {
                        if (authData.xuid().length() != 8) {
                            uuid = authData.uuid();
                        } else {
                            uuid = UUID.fromString("00000000-0000-4000-8000-0000" + authData.xuid());
                        }

                        if (uuid.toString().equals("00000000-0000-4000-8000-000000000000")) {
                            geyser.getLogger().warning("玩家: " + protocol.getProfile().getName() + " 登录异常，UUID 为空。authData.uuid: " + authData.uuid() + " XUid: " + authData.xuid());
                        }
//                        uuid = new UUID(0, Long.parseLong(authData.xuid()));
                    } else {
                        uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + protocol.getProfile().getName()).getBytes(StandardCharsets.UTF_8));
                    }
                }
                playerEntity.setUuid(uuid);
                playerEntity.setUsername(protocol.getProfile().getName());

                String locale = clientData.getLanguageCode();

                // Let the user know there locale may take some time to download
                // as it has to be extracted from a JAR
                if (locale.equalsIgnoreCase("en_us") && !MinecraftLocale.LOCALE_MAPPINGS.containsKey("en_us")) {
                    // This should probably be left hardcoded as it will only show for en_us clients
                    sendMessage("Loading your locale (en_us); if this isn't already downloaded, this may take some time");
                }

                // Download and load the language for the player
                MinecraftLocale.downloadAndLoadLocale(locale);
            }

            @Override
            public void disconnected(DisconnectedEvent event) {
                loggingIn = false;
                loggedIn = false;

                String disconnectMessage;
                Throwable cause = event.getCause();
                if (cause instanceof UnexpectedEncryptionException) {
                    if (remoteServer.authType() != AuthType.FLOODGATE) {
                        // Server expects online mode
                        disconnectMessage = GeyserLocale.getPlayerLocaleString("geyser.network.remote.authentication_type_mismatch", locale());
                        // Explain that they may be looking for Floodgate.
                        geyser.getLogger().warning(GeyserLocale.getLocaleStringLog(
                                geyser.getPlatformType() == PlatformType.STANDALONE ?
                                        "geyser.network.remote.floodgate_explanation_standalone"
                                        : "geyser.network.remote.floodgate_explanation_plugin",
                                Constants.FLOODGATE_DOWNLOAD_LOCATION
                        ));
                    } else {
                        // Likely that Floodgate is not configured correctly.
                        disconnectMessage = GeyserLocale.getPlayerLocaleString("geyser.network.remote.floodgate_login_error", locale());
                        if (geyser.getPlatformType() == PlatformType.STANDALONE) {
                            geyser.getLogger().warning(GeyserLocale.getLocaleStringLog("geyser.network.remote.floodgate_login_error_standalone"));
                        }
                    }
                } else if (cause instanceof ConnectException) {
                    // Server is offline, probably
                    disconnectMessage = GeyserLocale.getPlayerLocaleString("geyser.network.remote.server_offline", locale());
                } else {
                    disconnectMessage = MessageTranslator.convertMessage(event.getReason());
                }

                if (downstream instanceof LocalSession) {
                    geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.network.remote.disconnect_internal", authData.name(), disconnectMessage));
                } else {
                    geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.network.remote.disconnect", authData.name(), remoteServer.address(), disconnectMessage));
                }
                if (cause != null) {
                    if (cause.getMessage() != null) {
                        GeyserImpl.getInstance().getLogger().error(cause.getMessage());
                    } else {
                        GeyserImpl.getInstance().getLogger().error("An exception occurred: ", cause);
                    }
                    // GeyserSession is disconnected via session.disconnect() called indirectly be the server
                    // This only needs to be "initiated" here when there is an exception, hence the cause clause
                    GeyserSession.this.disconnect(disconnectMessage);
                    if (geyser.getConfig().isDebugMode()) {
                        cause.printStackTrace();
                    }
                }
            }

            @Override
            public void packetReceived(Session session, Packet packet) {
                Registries.JAVA_PACKET_TRANSLATORS.translate(packet.getClass(), packet, GeyserSession.this);
            }

            @Override
            public void packetError(PacketErrorEvent event) {
                geyser.getLogger().warning(GeyserLocale.getLocaleStringLog("geyser.network.downstream_error", event.getCause().getMessage()));
                if (geyser.getConfig().isDebugMode())
                    event.getCause().printStackTrace();
                event.setSuppress(true);
            }
        });

        if (!daylightCycle) {
            setDaylightCycle(true);
        }

        downstream.connect(false);
    }

    public void disconnect(String reason) {
        if (!closed) {
            loggedIn = false;

            // Fire SessionDisconnectEvent
            SessionDisconnectEvent disconnectEvent = new SessionDisconnectEvent(this, reason);
            geyser.getEventBus().fire(disconnectEvent);

            // Disconnect downstream if necessary
            if (downstream != null) {
                // No need to disconnect if already closed
                if (!downstream.isClosed()) {
                    downstream.disconnect(reason);
                }
            } else {
                // Downstream's disconnect will fire an event that prints a log message
                // Otherwise, we print a message here
                String address = geyser.getConfig().isLogPlayerIpAddresses() ? upstream.getAddress().getAddress().toString() : "<IP address withheld>";
                geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.network.disconnect", address, reason));
            }

            // Disconnect upstream if necessary
            if (!upstream.isClosed()) {
                upstream.disconnect(disconnectEvent.disconnectReason());
            }

            // Remove from session manager
            geyser.getSessionManager().removeSession(this);
            if (authData != null) {
                PendingMicrosoftAuthentication.AuthenticationTask task = geyser.getPendingMicrosoftAuthentication().getTask(authData.xuid());
                if (task != null) {
                    task.setOnline(false);
                }
            }
        }

        if (tickThread != null) {
            tickThread.cancel(false);
        }

        erosionHandler.close();

        closed = true;
    }

    /**
     * Moves task to the session event loop if already not in it. Otherwise, the task is automatically ran.
     */
    public void ensureInEventLoop(Runnable runnable) {
        if (eventLoop.inEventLoop()) {
            runnable.run();
            return;
        }
        executeInEventLoop(runnable);
    }

    /**
     * Executes a task and prints a stack trace if an error occurs.
     */
    public void executeInEventLoop(Runnable runnable) {
        eventLoop.execute(() -> {
            try {
                runnable.run();
            } catch (Throwable e) {
                geyser.getLogger().error("Error thrown in " + this.bedrockUsername() + "'s event loop!", e);
            }
        });
    }

    /**
     * Schedules a task and prints a stack trace if an error occurs.
     */
    public ScheduledFuture<?> scheduleInEventLoop(Runnable runnable, long duration, TimeUnit timeUnit) {
        return eventLoop.schedule(() -> {
            try {
                runnable.run();
            } catch (Throwable e) {
                geyser.getLogger().error("Error thrown in " + this.bedrockUsername() + "'s event loop!", e);
            }
        }, duration, timeUnit);
    }

    /**
     * Called every 50 milliseconds - one Minecraft tick.
     */
    protected void tick() {
        try {
            pistonCache.tick();
            // Check to see if the player's position needs updating - a position update should be sent once every 3 seconds
            if (spawned && (System.currentTimeMillis() - lastMovementTimestamp) > 3000) {
                // Recalculate in case something else changed position
                Vector3d position = collisionManager.adjustBedrockPosition(playerEntity.getPosition(), playerEntity.isOnGround(), false);
                // A null return value cancels the packet
                if (position != null) {
                    ServerboundMovePlayerPosPacket packet = new ServerboundMovePlayerPosPacket(playerEntity.isOnGround(),
                            position.getX(), position.getY(), position.getZ());
                    sendDownstreamGamePacket(packet);
                }
                lastMovementTimestamp = System.currentTimeMillis();
            }

            if (worldBorder.isResizing()) {
                worldBorder.resize();
            }

            boolean shouldShowFog = !worldBorder.isWithinWarningBoundaries();
            if (shouldShowFog || worldBorder.isCloseToBorderBoundaries()) {
                // Show particles representing where the world border is
                worldBorder.drawWall();
                // Set the mood
                if (shouldShowFog && !isInWorldBorderWarningArea) {
                    isInWorldBorderWarningArea = true;
                    sendFog("minecraft:fog_crimson_forest");
                }
            }
            if (!shouldShowFog && isInWorldBorderWarningArea) {
                // Clear fog as we are outside the world border now
                removeFog("minecraft:fog_crimson_forest");
                isInWorldBorderWarningArea = false;
            }


            for (Tickable entity : entityCache.getTickableEntities()) {
                entity.tick();
            }

            if (armAnimationTicks >= 0) {
                // As of 1.18.2 Java Edition, it appears that the swing time is dynamically updated depending on the
                // player's effect status, but the animation can cut short if the duration suddenly decreases
                // (from suddenly no longer having mining fatigue, for example)
                // This math is referenced from Java Edition 1.18.2
                int swingTotalDuration;
                int hasteLevel = Math.max(effectCache.getHaste(), effectCache.getConduitPower());
                if (hasteLevel > 0) {
                    swingTotalDuration = 6 - hasteLevel;
                } else {
                    int miningFatigueLevel = effectCache.getMiningFatigue();
                    if (miningFatigueLevel > 0) {
                        swingTotalDuration = 6 + miningFatigueLevel * 2;
                    } else {
                        swingTotalDuration = 6;
                    }
                }
                if (++armAnimationTicks >= swingTotalDuration) {
                    if (sneaking) {
                        // Attempt to re-activate blocking as our swing animation is up
                        if (attemptToBlock()) {
                            playerEntity.updateBedrockMetadata();
                        }
                    }
                    armAnimationTicks = -1;
                }
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    public void setAuthenticationData(AuthData authData) {
        this.authData = authData;
    }

    public void startSneaking() {
        // Toggle the shield, if there is no ongoing arm animation
        // This matches Bedrock Edition behavior as of 1.18.12
        if (armAnimationTicks < 0) {
            attemptToBlock();
        }

        setSneaking(true);
    }

    public void stopSneaking() {
        disableBlocking();

        setSneaking(false);
    }

    private void setSneaking(boolean sneaking) {
        this.sneaking = sneaking;

        // Update pose and bounding box on our end
        AttributeData speedAttribute;
        if (!sneaking && (speedAttribute = adjustSpeed()) != null) {
            // Update attributes since we're still "sneaking" under a 1.5-block-tall area
            UpdateAttributesPacket attributesPacket = new UpdateAttributesPacket();
            attributesPacket.setRuntimeEntityId(playerEntity.getGeyserId());
            attributesPacket.setAttributes(Collections.singletonList(speedAttribute));
            sendUpstreamPacket(attributesPacket);
            // the server *should* update our pose once it has returned to normal
        } else {
            if (!flying) {
                // The pose and bounding box should not be updated if the player is flying
                setSneakingPose(sneaking);
            }
            collisionManager.updateScaffoldingFlags(false);
        }

        playerEntity.updateBedrockMetadata();

        if (mouseoverEntity != null) {
            // Horses, etc can change their property depending on if you're sneaking
            mouseoverEntity.updateInteractiveTag();
        }
    }

    private void setSneakingPose(boolean sneaking) {
        if (this.pose == Pose.SNEAKING && !sneaking) {
            this.pose = Pose.STANDING;
            playerEntity.setBoundingBoxHeight(playerEntity.getDefinition().height());
        } else if (sneaking) {
            this.pose = Pose.SNEAKING;
            playerEntity.setBoundingBoxHeight(1.5f);
        }
        playerEntity.setFlag(EntityFlag.SNEAKING, sneaking);
    }

    public void setSwimming(boolean swimming) {
        if (swimming) {
            this.pose = Pose.SWIMMING;
            playerEntity.setBoundingBoxHeight(0.6f);
        } else {
            this.pose = Pose.STANDING;
            playerEntity.setBoundingBoxHeight(playerEntity.getDefinition().height());
        }
        playerEntity.setFlag(EntityFlag.SWIMMING, swimming);
        playerEntity.updateBedrockMetadata();
    }

    public void setFlying(boolean flying) {
        this.flying = flying;

        if (sneaking) {
            // update bounding box as it is not reduced when flying
            setSneakingPose(!flying);
            playerEntity.updateBedrockMetadata();
        }
    }

    /**
     * Adjusts speed if the player is crawling.
     *
     * @return not null if attributes should be updated.
     */
    public @Nullable AttributeData adjustSpeed() {
        AttributeData currentPlayerSpeed = playerEntity.getAttributes().get(GeyserAttributeType.MOVEMENT_SPEED);
        if (currentPlayerSpeed != null) {
            if ((pose.equals(Pose.SNEAKING) && !sneaking && collisionManager.mustPlayerSneakHere()) ||
                    (!swimmingInWater && playerEntity.getFlag(EntityFlag.SWIMMING) && !collisionManager.isPlayerInWater())) {
                // Either of those conditions means that Bedrock goes zoom when they shouldn't be
                AttributeData speedAttribute = GeyserAttributeType.MOVEMENT_SPEED.getAttribute(originalSpeedAttribute / 3.32f);
                playerEntity.getAttributes().put(GeyserAttributeType.MOVEMENT_SPEED, speedAttribute);
                return speedAttribute;
            } else if (originalSpeedAttribute != currentPlayerSpeed.getValue()) {
                // Speed has reset to normal
                AttributeData speedAttribute = GeyserAttributeType.MOVEMENT_SPEED.getAttribute(originalSpeedAttribute);
                playerEntity.getAttributes().put(GeyserAttributeType.MOVEMENT_SPEED, speedAttribute);
                return speedAttribute;
            }
        }
        return null;
    }

    /**
     * Checks to see if a shield is in either hand to activate blocking. If so, it sets the Bedrock client to display
     * blocking and sends a packet to the Java server.
     */
    private boolean attemptToBlock() {
        ServerboundUseItemPacket useItemPacket;
        if (playerInventory.getItemInHand().asItem() == Items.SHIELD) {
            useItemPacket = new ServerboundUseItemPacket(Hand.MAIN_HAND, worldCache.nextPredictionSequence());
        } else if (playerInventory.getOffhand().asItem() == Items.SHIELD) {
            useItemPacket = new ServerboundUseItemPacket(Hand.OFF_HAND, worldCache.nextPredictionSequence());
        } else {
            // No blocking
            return false;
        }

        sendDownstreamGamePacket(useItemPacket);
        playerEntity.setFlag(EntityFlag.BLOCKING, true);
        // Metadata should be updated later
        return true;
    }

    /**
     * Starts ticking the amount of time that the Bedrock client has been swinging their arm, and disables blocking if
     * blocking.
     */
    public void activateArmAnimationTicking() {
        armAnimationTicks = 0;
        if (disableBlocking()) {
            playerEntity.updateBedrockMetadata();
        }
    }

    /**
     * For <a href="https://github.com/GeyserMC/Geyser/issues/2113">issue 2113</a> and combating arm ticking activating being delayed in
     * BedrockAnimateTranslator.
     */
    public void armSwingPending() {
        if (armAnimationTicks == -1) {
            armAnimationTicks = -2;
        }
    }

    /**
     * Indicates to the client to stop blocking and tells the Java server the same.
     */
    private boolean disableBlocking() {
        if (playerEntity.getFlag(EntityFlag.BLOCKING)) {
            ServerboundPlayerActionPacket releaseItemPacket = new ServerboundPlayerActionPacket(PlayerAction.RELEASE_USE_ITEM,
                    Vector3i.ZERO, Direction.DOWN, 0);
            sendDownstreamGamePacket(releaseItemPacket);
            playerEntity.setFlag(EntityFlag.BLOCKING, false);
            return true;
        }
        return false;
    }

    public void requestOffhandSwap() {
        ServerboundPlayerActionPacket swapHandsPacket = new ServerboundPlayerActionPacket(PlayerAction.SWAP_HANDS, Vector3i.ZERO,
                Direction.DOWN, 0);
        sendDownstreamGamePacket(swapHandsPacket);
    }

    @Override
    public String name() {
        return clientData.getUsername();
    }

    @Override
    public void sendMessage(@NonNull String message) {
        TextPacket textPacket = new TextPacket();
        textPacket.setPlatformChatId("");
        textPacket.setSourceName("");
        textPacket.setXuid("");
        textPacket.setType(TextPacket.Type.CHAT);
        textPacket.setNeedsTranslation(false);
        textPacket.setMessage(message);

        upstream.sendPacket(textPacket);
    }

    @Override
    public boolean isConsole() {
        return false;
    }

    @Override
    public String locale() {
        return clientData.getLanguageCode();
    }

    /**
     * Sends a chat message to the Java server.
     */
    public void sendChat(String message) {
        sendDownstreamGamePacket(new ServerboundChatPacket(message, Instant.now().toEpochMilli(), 0L, null, 0, new BitSet()));
    }

    /**
     * Sends a command to the Java server.
     */
    public void sendCommand(String command) {
        sendDownstreamGamePacket(new ServerboundChatCommandPacket(command, Instant.now().toEpochMilli(), 0L, Collections.emptyList(), 0, new BitSet()));
    }

    public void setServerRenderDistance(int renderDistance) {
        this.serverRenderDistance = renderDistance;

        ChunkRadiusUpdatedPacket chunkRadiusUpdatedPacket = new ChunkRadiusUpdatedPacket();
        chunkRadiusUpdatedPacket.setRadius(renderDistance);
        upstream.sendPacket(chunkRadiusUpdatedPacket);
    }

    public InetSocketAddress getSocketAddress() {
        return this.upstream.getAddress();
    }

    @Override
    public boolean sendForm(@NonNull Form form) {
        formCache.showForm(form);
        return true;
    }

    @Override
    public boolean sendForm(@NonNull FormBuilder<?, ?, ?> formBuilder) {
        formCache.showForm(formBuilder.build());
        return true;
    }

    /**
     * @deprecated since Cumulus version 1.1, and will be removed when Cumulus 2.0 releases. Please use the new forms instead.
     */
    @Deprecated
    public void sendForm(org.geysermc.cumulus.Form<?> form) {
        sendForm(form.newForm());
    }

    /**
     * @deprecated since Cumulus version 1.1, and will be removed when Cumulus 2.0 releases. Please use the new forms instead.
     */
    @Deprecated
    public void sendForm(org.geysermc.cumulus.util.FormBuilder<?, ?> formBuilder) {
        sendForm(formBuilder.build());
    }

    private void startGame() {
        this.upstream.getCodecHelper().setItemDefinitions(this.itemMappings);
        this.upstream.getCodecHelper().setBlockDefinitions((DefinitionRegistry) this.blockMappings); //FIXME

        StartGamePacket startGamePacket = new StartGamePacket();
        startGamePacket.setUniqueEntityId(playerEntity.getGeyserId());
        startGamePacket.setRuntimeEntityId(playerEntity.getGeyserId());
        startGamePacket.setPlayerGameType(EntityUtils.toBedrockGamemode(gameMode));
        startGamePacket.setPlayerPosition(Vector3f.from(0, 69, 0));
        startGamePacket.setRotation(Vector2f.from(1, 1));

        startGamePacket.setSeed(-1L);
        startGamePacket.setDimensionId(DimensionUtils.javaToBedrock(chunkCache.getBedrockDimension()));
        startGamePacket.setGeneratorId(1);
        startGamePacket.setLevelGameType(GameType.SURVIVAL);
        startGamePacket.setDifficulty(1);
        startGamePacket.setDefaultSpawn(Vector3i.ZERO);
        startGamePacket.setAchievementsDisabled(!geyser.getConfig().isXboxAchievementsEnabled());
        startGamePacket.setCurrentTick(-1);
        startGamePacket.setEduEditionOffers(0);
        startGamePacket.setEduFeaturesEnabled(false);
        startGamePacket.setRainLevel(0);
        startGamePacket.setLightningLevel(0);
        startGamePacket.setMultiplayerGame(true);
        startGamePacket.setBroadcastingToLan(true);
        startGamePacket.setPlatformBroadcastMode(GamePublishSetting.PUBLIC);
        startGamePacket.setXblBroadcastMode(GamePublishSetting.PUBLIC);
        startGamePacket.setCommandsEnabled(!geyser.getConfig().isXboxAchievementsEnabled());
        startGamePacket.setTexturePacksRequired(false);
        startGamePacket.setBonusChestEnabled(false);
        startGamePacket.setStartingWithMap(false);
        startGamePacket.setTrustingPlayers(true);
        startGamePacket.setDefaultPlayerPermission(PlayerPermission.MEMBER);
        startGamePacket.setServerChunkTickRange(4);
        startGamePacket.setBehaviorPackLocked(false);
        startGamePacket.setResourcePackLocked(false);
        startGamePacket.setFromLockedWorldTemplate(false);
        startGamePacket.setUsingMsaGamertagsOnly(false);
        startGamePacket.setFromWorldTemplate(false);
        startGamePacket.setWorldTemplateOptionLocked(false);
        startGamePacket.setSpawnBiomeType(SpawnBiomeType.DEFAULT);
        startGamePacket.setCustomBiomeName("");
        startGamePacket.setEducationProductionId("");
        startGamePacket.setForceExperimentalGameplay(OptionalBoolean.empty());

        String serverName = geyser.getConfig().getBedrock().serverName();
        startGamePacket.setLevelId(serverName);
        startGamePacket.setLevelName(serverName);

        startGamePacket.setPremiumWorldTemplateId("00000000-0000-0000-0000-000000000000");
        // startGamePacket.setCurrentTick(0);
        startGamePacket.setEnchantmentSeed(0);
        startGamePacket.setMultiplayerCorrelationId("");

        startGamePacket.setItemDefinitions(this.itemMappings.getItemDefinitions().values().stream().toList()); // TODO
        // startGamePacket.setBlockPalette(this.blockMappings.getBedrockBlockPalette());

        // Needed for custom block mappings and custom skulls system
        startGamePacket.getBlockProperties().addAll(this.blockMappings.getBlockProperties());

        // See https://learn.microsoft.com/en-us/minecraft/creator/documents/experimentalfeaturestoggle for info on each experiment
        // data_driven_items (Holiday Creator Features) is needed for blocks and items
        startGamePacket.getExperiments().add(new ExperimentData("data_driven_items", true));
        // Needed for block properties for states
        startGamePacket.getExperiments().add(new ExperimentData("upcoming_creator_features", true));
        // Needed for certain molang queries used in blocks and items
        startGamePacket.getExperiments().add(new ExperimentData("experimental_molang_features", true));
        // Required for experimental 1.21 features
        startGamePacket.getExperiments().add(new ExperimentData("updateAnnouncedLive2023", true));

        startGamePacket.setVanillaVersion("*");
        startGamePacket.setInventoriesServerAuthoritative(true);
        startGamePacket.setServerEngine(""); // Do we want to fill this in?

        startGamePacket.setPlayerPropertyData(NbtMap.EMPTY);
        startGamePacket.setWorldTemplateId(UUID.randomUUID());

        startGamePacket.setChatRestrictionLevel(ChatRestrictionLevel.NONE);

        startGamePacket.setAuthoritativeMovementMode(AuthoritativeMovementMode.CLIENT);
        startGamePacket.setRewindHistorySize(0);
        startGamePacket.setServerAuthoritativeBlockBreaking(false);

        upstream.sendPacket(startGamePacket);

//        for (ItemDefinition itemDefinition : startGamePacket.getItemDefinitions()) {
//            System.out.println(itemDefinition);
//        }
//
//        for (BlockPropertyData blockProperty : startGamePacket.getBlockProperties()) {
//            System.out.println(blockProperty);
//        }

//        try {
//            testBytes(this);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }

    }


    public void testBytes(GeyserSession session) {
        String str1 = "fe0bedffffff1f090a0000d4419f01004700008c410000000000000000dc94bddc958a0f3b000006706c61696e730002000434ffff012201000000d6020000000000000000000000000101000400002312636f6d6d616e64626c6f636b6f75747075740101010f646f6461796c696768746379636c650101010d646f656e7469747964726f70730101010a646f666972657469636b0101010d72656369706573756e6c6f636b01010111646f6c696d697465646372616674696e6701010009646f6d6f626c6f6f740101010d646f6d6f62737061776e696e670101010b646f74696c6564726f70730101010e646f776561746865726379636c650101010e64726f776e696e6764616d6167650101010a66616c6c64616d6167650101010a6669726564616d6167650101010d6b656570696e76656e746f72790101000b6d6f626772696566696e67010101037076700101010f73686f77636f6f7264696e61746573010100136e61747572616c726567656e65726174696f6e0101010b746e746578706c6f6465730101011373656e64636f6d6d616e64666565646261636b010101156d6178636f6d6d616e64636861696e6c656e6774680102feff070a646f696e736f6d6e696101010114636f6d6d616e64626c6f636b73656e61626c65640101010f72616e646f6d7469636b737065656401020212646f696d6d6564696174657265737061776e0101001173686f7764656174686d657373616765730101011466756e6374696f6e636f6d6d616e646c696d69740102a09c010b737061776e7261646975730102140873686f77746167730101010c667265657a6564616d616765010101147265737061776e626c6f636b736578706c6f64650101011073686f77626f726465726566666563740101011273686f777265636970656d6573736167657301010119706c6179657273736c656570696e6770657263656e746167650102c8011970726f6a656374696c657363616e627265616b626c6f636b73010101100000000f746573745f6578706572696d656e74010b6e6578745f75706461746501116578706572696d656e74616c5f746578740111616c6c6f775f736565645f6368616e6765011c76616e696c6c615f6578706572696d656e74735f696e7465726e616c0111646174615f64726976656e5f6974656d730112646174615f64726976656e5f62696f6d657301197570636f6d696e675f63726561746f725f6665617475726573010867616d6574657374011c6578706572696d656e74616c5f6d6f6c616e675f6665617475726573010763616d6572617301126d696e6563726166745f6578706c6f726572011a64656665727265645f746563686e6963616c5f70726576696577011976696c6c616765725f7472616465735f726562616c616e63650117757064617465416e6e6f756e6365644c697665323032330124646174615f64726976656e5f76616e696c6c615f626c6f636b735f616e645f6974656d7301010000020400000000000000000000000000012a10000000100000000000000000000c6f4142635a3163794e77413d0831313334313132312430303030303030302d303030302d303030302d303030302d30303030303030303030303000025000ab00000000000000a49daec50900bc0b236d696e6563726166743a77617865645f6578706f7365645f636f707065725f62756c62f3fc00166d696e6563726166743a7075727075725f626c6f636bc90000146d696e6563726166743a636f6f6b65645f636f640d01000d6d696e6563726166743a626f772f0100146d696e6563726166743a656e645f627269636b73ce0000196d696e6563726166743a6d757369635f646973635f776172642902011c6d696e6563726166743a656e6465726d616e5f737061776e5f656767be01000d6d696e6563726166743a61697262ff00146d696e6563726166743a656c656d656e745f393497ff001d6d696e6563726166743a736b756c6c5f706f74746572795f7368657264b00200106d696e6563726166743a726162626974210100156d696e6563726166743a7365615f6c616e7465726ea90000206d696e6563726166743a637265657065725f62616e6e65725f7061747465726e500200146d696e6563726166743a656c656d656e745f3235dcff00196d696e6563726166743a6d616e67726f76655f6c656176657328fe00286d696e6563726166743a706f6c69736865645f626c61636b73746f6e655f627269636b5f736c6162e4fe00176d696e6563726166743a6d757368726f6f6d5f737465770501001b6d696e6563726166743a726176616765725f737061776e5f656767f10100196d696e6563726166743a636f6f6b65645f706f726b63686f700801001a6d696e6563726166743a73747269707065645f6f616b5f6c6f67f6ff00146d696e6563726166743a656c656d656e745f3530c3ff00156d696e6563726166743a62616d626f6f5f7369676e9a02000f6d696e6563726166743a6170706c650101011e6d696e6563726166743a616e676c65725f706f74746572795f73686572649e0200166d696e6563726166743a676f6c64656e5f6170706c65030100136d696e6563726166743a626f6f6b7368656c662f00001c6d696e6563726166743a676f6c64656e5f686f7273655f61726d6f721e02001e6d696e6563726166743a736d6f6f74685f71756172747a5f73746169727347ff00106d696e6563726166743a706f7461746f1901001c6d696e6563726166743a6d6167656e74615f7465727261636f7474612bfd00156d696e6563726166743a6e65746865725f73746172100200206d696e6563726166743a656e6368616e7465645f676f6c64656e5f6170706c65040100146d696e6563726166743a656c656d656e745f3135e6ff00156d696e6563726166743a626c75655f636172706574a1fd00226d696e6563726166743a79656c6c6f775f676c617a65645f7465727261636f747461e000001c6d696e6563726166743a73746f6e655f627269636b5f7374616972736d0000106d696e6563726166743a706f7274616c5a0000146d696e6563726166743a676f6c645f696e676f74350100186d696e6563726166743a6f616b5f63686573745f626f61748c02001f6d696e6563726166743a62726f776e5f636f6e63726574655f706f7764657230fd00146d696e6563726166743a69726f6e5f696e676f74340100196d696e6563726166743a736c696d655f737061776e5f656767c101000f6d696e6563726166743a7363757465460200156d696e6563726166743a70696e6b5f636172706574a6fd00126d696e6563726166743a706f726b63686f70070100106d696e6563726166743a636f6f6b69651001002f6d696e6563726166743a706f6c69736865645f626c61636b73746f6e655f627269636b5f646f75626c655f736c6162e3fe001b6d696e6563726166743a6368657272795f63686573745f626f6174980200136d696e6563726166743a656c656d656e745f37eeff00176d696e6563726166743a6469616d6f6e645f626c6f636b3900000f6d696e6563726166743a62726561640601001a6d696e6563726166743a6d757369635f646973635f6368697270230201176d696e6563726166743a636f6f6b65645f726162626974220100226d696e6563726166743a707269736d6172696e655f627269636b735f737461697273fcff001e6d696e6563726166743a676c6f775f73717569645f737061776e5f656767fc0100186d696e6563726166743a6974656d2e69726f6e5f646f6f724700000d6d696e6563726166743a636f640901001c6d696e6563726166743a70696c6c616765725f737061776e5f656767ef0100166d696e6563726166743a69726f6e5f7069636b6178652c01002b6d696e6563726166743a77696c645f61726d6f725f7472696d5f736d697468696e675f74656d706c617465b70200146d696e6563726166743a656c656d656e745f3237daff000e6d696e6563726166743a62656566120100216d696e6563726166743a677261795f737461696e65645f676c6173735f70616e6577fd00196d696e6563726166743a626c617a655f737061776e5f656767cc01001a6d696e6563726166743a64656570736c6174655f627269636b7379fe00106d696e6563726166743a73616c6d6f6e0a0100156d696e6563726166743a636f636f615f6265616e73a00100176d696e6563726166743a74726f706963616c5f666973680b0100226d696e6563726166743a73696c7665725f676c617a65645f7465727261636f747461e40000156d696e6563726166743a776f6f64656e5f736c61629e0000136d696e6563726166743a73746f6e655f6178653e01001c6d696e6563726166743a737461696e65645f676c6173735f70616e65d00200106d696e6563726166743a6275636b65746c0100186d696e6563726166743a616e6369656e745f646562726973f1fe00176d696e6563726166743a747261707065645f6368657374920000146d696e6563726166743a707566666572666973680c0100126d696e6563726166743a737061726b6c6572650200156d696e6563726166743a7761727065645f646f6f72760200146d696e6563726166743a656c656d656e745f3631b8ff00176d696e6563726166743a636f6f6b65645f73616c6d6f6e0e0100146d696e6563726166743a64726965645f6b656c700f0100176d696e6563726166743a62656574726f6f745f736f75701f0100256d696e6563726166743a77617865645f7765617468657265645f636f707065725f646f6f72eafc00186d696e6563726166743a776f6f64656e5f7069636b6178653901001e6d696e6563726166743a6d616e67726f76655f646f75626c655f736c61620dfe001d6d696e6563726166743a6d6167656e74615f63616e646c655f63616b6550fe00176d696e6563726166743a6974656d2e63616d70666972652fff00156d696e6563726166743a6d656c6f6e5f736c696365110100186d696e6563726166743a6e617574696c75735f7368656c6c440200176d696e6563726166743a776f6f64656e5f73686f76656c380100136d696e6563726166743a6861795f626c6f636baa0000136d696e6563726166743a656c656d656e745f31f4ff001b6d696e6563726166743a73746f6e656375747465725f626c6f636b3bff00156d696e6563726166743a636f6f6b65645f62656566130100186d696e6563726166743a6578706f7365645f636f70706572abfe00146d696e6563726166743a636f6d70617261746f721402012a6d696e6563726166743a7665785f61726d6f725f7472696d5f736d697468696e675f74656d706c617465ba0200166d696e6563726166743a626c61636b5f6361727065749dfd00186d696e6563726166743a7261775f676f6c645f626c6f636b3bfe00106d696e6563726166743a636172726f741801001b6d696e6563726166743a737472696465725f737061776e5f656767f40100176d696e6563726166743a636f6d6d616e645f626c6f636b890000106d696e6563726166743a706f74696f6eae0100116d696e6563726166743a636869636b656e140100166d696e6563726166743a726f7474656e5f666c657368160100146d696e6563726166743a656c656d656e745f3632b7ff000e6d696e6563726166743a64697274030000196d696e6563726166743a77697463685f737061776e5f656767c80100126d696e6563726166743a63616d70666972655702011a6d696e6563726166743a6c696e676572696e675f706f74696f6e3c0200156d696e6563726166743a7261626269745f666f6f741a0200106d696e6563726166743a736d6f6b65723aff00176d696e6563726166743a6d616e67726f76655f776f6f640ffe00186d696e6563726166743a636f6f6b65645f636869636b656e150100266d696e65";
        String str2 = "63726166743a6c696768745f626c75655f676c617a65645f7465727261636f747461df0000156d696e6563726166743a73746f6e655f73776f72643b0100146d696e6563726166743a7370696465725f657965170100196d696e6563726166743a686f7273655f737061776e5f656767ce0100166d696e6563726166743a62616b65645f706f7461746f1a01001d6d696e6563726166743a64656570736c6174655f74696c655f77616c6c7afe00176d696e6563726166743a676f6c64656e5f636172726f741c01001a6d696e6563726166743a706f69736f6e6f75735f706f7461746f1b0100146d696e6563726166743a656c656d656e745f3133e8ff00176d696e6563726166743a7370727563655f7374616972738600001a6d696e6563726166743a706f6c69736865645f6772616e697465b1fd00196d696e6563726166743a63686973656c65645f636f7070657208fd001d6d696e6563726166743a7072697a655f706f74746572795f7368657264ad0200156d696e6563726166743a70756d706b696e5f7069651d0100126d696e6563726166743a6f6273696469616e310000116d696e6563726166743a6c616e7465726e30ff001b6d696e6563726166743a746f726368666c6f7765725f7365656473290100146d696e6563726166743a69726f6e5f73776f7264360100166d696e6563726166743a736d6f6f74685f73746f6e6549ff00126d696e6563726166743a62656574726f6f741e0100146d696e6563726166743a656c656d656e745f3433caff001a6d696e6563726166743a6d757369635f646973635f7374726164280201176d696e6563726166743a73776565745f62657272696573200100166d696e6563726166743a77686974655f63616e646c6563fe00156d696e6563726166743a7261626269745f73746577230100196d696e6563726166743a79656c6c6f775f636f6e637265746589fd00156d696e6563726166743a77686561745f7365656473240100206d696e6563726166743a636f6d6d616e645f626c6f636b5f6d696e65636172743d02000f6d696e6563726166743a6368657374360000176d696e6563726166743a70756d706b696e5f7365656473250100136d696e6563726166743a656c656d656e745f32f3ff00156d696e6563726166743a6d656c6f6e5f7365656473260100136d696e6563726166743a737061776e5f656767d50200126d696e6563726166743a7261775f69726f6e030200126d696e6563726166743a69726f6e5f6178652d0100156d696e6563726166743a6e65746865725f776172742701001c6d696e6563726166743a62616d626f6f5f646f75626c655f736c6162f7fd00146d696e6563726166743a656c656d656e745f3335d2ff00186d696e6563726166743a62656574726f6f745f7365656473280100156d696e6563726166743a6c696d655f636172706574a7fd00156d696e6563726166743a706f776465725f736e6f77cefe00136d696e6563726166743a69726f6e5f626172736500001e6d696e6563726166743a706f6c61725f626561725f737061776e5f656767dc0100156d696e6563726166743a706974636865725f706f642a0100146d696e6563726166743a656c656d656e745f3830a5ff00156d696e6563726166743a69726f6e5f73686f76656c2b0100156d696e6563726166743a656c656d656e745f3130348dff001a6d696e6563726166743a7a6f676c696e5f737061776e5f656767f70100196d696e6563726166743a666c696e745f616e645f737465656c2e0100186d696e6563726166743a6772616e6974655f73746169727357ff001f6d696e6563726166743a6d6f75726e65725f706f74746572795f7368657264ab0200166d696e6563726166743a73746f6e655f73686f76656c3c0100276d696e6563726166743a6c696768745f677261795f737461696e65645f676c6173735f70616e6576fd000f6d696e6563726166743a6172726f77300101156d696e6563726166743a6d656c6f6e5f626c6f636b6700000e6d696e6563726166743a636f616c310101126d696e6563726166743a63686172636f616c320101196d696e6563726166743a73747261795f737061776e5f656767d20100116d696e6563726166743a6469616d6f6e64330100166d696e6563726166743a776f6f64656e5f73776f7264370100246d696e6563726166743a6f786964697a65645f6375745f636f707065725f7374616972739bfe00196d696e6563726166743a6e65746865726974655f626f6f7473710200196d696e6563726166743a6d757369635f646973635f6d616c6c250201126d696e6563726166743a6661726d6c616e643c0000146d696e6563726166743a776f6f64656e5f6178653a0100216d696e6563726166743a7a6f6d6269655f7069676d616e5f737061776e5f656767c401001a6d696e6563726166743a6372696d736f6e5f74726170646f6f720aff00126d696e6563726166743a7261775f676f6c64040200176d696e6563726166743a73616c6d6f6e5f6275636b6574710100106d696e6563726166743a706c616e6b73ca0200176d696e6563726166743a73746f6e655f7069636b6178653d0100216d696e6563726166743a6578706f7365645f636f707065725f74726170646f6f72e7fc001a6d696e6563726166743a636861696e6d61696c5f68656c6d6574560100186d696e6563726166743a6469616d6f6e645f73686f76656c400100226d696e6563726166743a6f786964697a65645f636f707065725f74726170646f6f72e5fc00186d696e6563726166743a736d697468696e675f7461626c6536ff00176d696e6563726166743a6469616d6f6e645f73776f72643f0100196d696e6563726166743a6469616d6f6e645f7069636b617865410100156d696e6563726166743a6469616d6f6e645f6178654201002d6d696e6563726166743a7261697365725f61726d6f725f7472696d5f736d697468696e675f74656d706c617465c10200156d696e6563726166743a64656275675f737469636b5902000f6d696e6563726166743a737469636b430100176d696e6563726166743a666c6f77696e675f77617465720800000e6d696e6563726166743a626f776c4401001f6d696e6563726166743a61726d735f75705f706f74746572795f7368657264a002001b6d696e6563726166743a7761727065645f776172745f626c6f636b1dff00166d696e6563726166743a7370727563655f66656e6365bdfd000e6d696e6563726166743a76696e656a00001f6d696e6563726166743a6d616e67726f76655f68616e67696e675f7369676e04fe00166d696e6563726166743a676f6c64656e5f73776f7264450100156d696e6563726166743a686f6e65795f626c6f636b24ff00246d696e6563726166743a6c69745f64656570736c6174655f72656473746f6e655f6f72656cfe00176d696e6563726166743a676f6c64656e5f73686f76656c460100106d696e6563726166743a656c797472613e0200146d696e6563726166743a677265656e5f776f6f6cd0fd001b6d696e6563726166743a6c69745f72656473746f6e655f6c616d707c0000186d696e6563726166743a676f6c64656e5f7069636b617865470100146d696e6563726166743a676f6c64656e5f617865480100146d696e6563726166743a656c656d656e745f3532c1ff00106d696e6563726166743a737472696e67490101116d696e6563726166743a666561746865724a0100136d696e6563726166743a67756e706f776465724b01001e6d696e6563726166743a736b756c6c5f62616e6e65725f7061747465726e510200176d696e6563726166743a6163616369615f737461697273a30000146d696e6563726166743a776f6f64656e5f686f654c01001e6d696e6563726166743a736e6f775f676f6c656d5f737061776e5f656767fe0100136d696e6563726166743a73746f6e655f686f654d0100196d696e6563726166743a70616e64615f737061776e5f656767ed01001a6d696e6563726166743a62726f776e5f7465727261636f74746121fd00126d696e6563726166743a69726f6e5f686f654e01001e6d696e6563726166743a73747269707065645f6368657272795f776f6f64dffd00156d696e6563726166743a6368657272795f7369676e990200146d696e6563726166743a656c656d656e745f38369fff00156d696e6563726166743a6469616d6f6e645f686f654f0100146d696e6563726166743a676f6c64656e5f686f65500100156d696e6563726166743a6d6167656e74615f6479659c01000f6d696e6563726166743a77686561745101002e6d696e6563726166743a73696c656e63655f61726d6f725f7472696d5f736d697468696e675f74656d706c617465bf0200146d696e6563726166743a676c6f775f6672616d657c0201186d696e6563726166743a6c6561746865725f68656c6d65745201001c6d696e6563726166743a6c6561746865725f6368657374706c6174655301001a6d696e6563726166743a6c6561746865725f6c656767696e6773540100206d696e6563726166743a676c6973746572696e675f6d656c6f6e5f736c696365b60100136d696e6563726166743a6c6f646573746f6e6522ff00176d696e6563726166743a6c6561746865725f626f6f74735501001e6d696e6563726166743a636861696e6d61696c5f6368657374706c617465570100156d696e6563726166743a656e645f67617465776179d10000156d696e6563726166743a656c656d656e745f31303190ff00176d696e6563726166743a6974656d2e62656574726f6f74f400001c6d696e6563726166743a636861696e6d61696c5f6c656767696e6773580100196d696e6563726166743a636861696e6d61696c5f626f6f7473590100136d696e6563726166743a736f756c5f73616e64580000146d696e6563726166743a656c656d656e745f3439c4ff00126d696e6563726166743a736e6f7762616c6c7a0101156d696e6563726166743a69726f6e5f68656c6d65745a01001a6d696e6563726166743a7261775f636f707065725f626c6f636b3cfe00106d696e6563726166743a62617272656c35ff00196d696e6563726166743a69726f6e5f6368657374706c6174655b0100176d696e6563726166743a69726f6e5f6c656767696e67735c0100146d696e6563726166743a69726f6e5f626f6f74735d01001c6d696e6563726166743a6275726e5f706f74746572795f7368657264a30200136d696e6563726166743a656e6465725f657965b501001c6d696e6563726166743a6d757369635f646973635f70696773746570790201176d696e6563726166743a69726f6e5f74726170646f6f72a70000186d696e6563726166743a6469616d6f6e645f68656c6d65745e01001e6d696e6563726166743a73746f6e655f70726573737572655f706c6174654600001c6d696e6563726166743a6469616d6f6e645f6368657374706c6174655f01000e6d696e6563726166743a73616e640c0000196d696e6563726166743a6375745f636f707065725f736c616297fe001b6d696e6563726166743a61786f6c6f746c5f737061776e5f656767fa01001a6d696e6563726166743a6469616d6f6e645f6c656767696e6773600100176d696e6563726166743a6469616d6f6e645f626f6f7473610100136d696e6563726166743a6d6f645f61726d6f726201000e6d696e6563726166743a6b656c70820101176d696e6563726166743a676f6c64656e5f68656c6d65746301001d6d696e6563726166743a677265656e5f737461696e65645f676c61737353fd00146d696e6563726166743a656c656d656e745f3531c2ff001b6d696e6563726166743a676f6c64656e5f6368657374706c617465640100136d696e6563726166743a676c6f7773746f6e65590000196d696e6563726166743a67";
        String str3 = "6f6c64656e5f6c656767696e6773650100166d696e6563726166743a676f6c64656e5f626f6f7473660100236d696e6563726166743a706f6c69736865645f64656570736c6174655f7374616972737ffe00106d696e6563726166743a736869656c64670100196d696e6563726166743a6167656e745f737061776e5f656767eb0100106d696e6563726166743a636172706574c702000f6d696e6563726166743a666c696e74680100156d696e6563726166743a6379616e5f636172706574a3fd001a6d696e6563726166743a68656172745f6f665f7468655f736561450200126d696e6563726166743a7061696e74696e67690100126d696e6563726166743a6f616b5f7369676e6a0100146d696e6563726166743a656c656d656e745f3535beff00196d696e6563726166743a6d757369635f646973635f776169742b0201156d696e6563726166743a776f6f64656e5f646f6f726b0100156d696e6563726166743a6d696c6b5f6275636b65746d0100116d696e6563726166743a7265645f647965900100186d696e6563726166743a746164706f6c655f6275636b65748402000e6d696e6563726166743a626f6e65a301001a6d696e6563726166743a6368657272795f77616c6c5f7369676ee0fd00166d696e6563726166743a77617465725f6275636b65746e0100146d696e6563726166743a656c656d656e745f3734abff001b6d696e6563726166743a7368756c6b65725f737061776e5f656767d901001d6d696e6563726166743a6d616e67726f76655f66656e63655f6761746514fe00156d696e6563726166743a6c6176615f6275636b65746f0100236d696e6563726166743a6d6167656e74615f676c617a65645f7465727261636f747461de00001e6d696e6563726166743a76696e64696361746f725f737061776e5f656767de0100146d696e6563726166743a636f645f6275636b6574700100206d696e6563726166743a6372696d736f6e5f70726573737572655f706c617465fafe001b6d696e6563726166743a7370727563655f66656e63655f67617465b70000216d696e6563726166743a6578706f7365645f6375745f636f707065725f736c616296fe001e6d696e6563726166743a74726f706963616c5f666973685f6275636b65747201001b6d696e6563726166743a707566666572666973685f6275636b6574730100176d696e6563726166743a6d757369635f646973635f31312a02011a6d696e6563726166743a65766f6b65725f737061776e5f656767df01001a6d696e6563726166743a6974656d2e6e65746865725f776172747300001c6d696e6563726166743a706f776465725f736e6f775f6275636b6574740100186d696e6563726166743a61786f6c6f746c5f6275636b65747501001a6d696e6563726166743a706172726f745f737061776e5f656767e20100186d696e6563726166743a776f6c665f737061776e5f656767bb0100126d696e6563726166743a6d696e65636172747601001e6d696e6563726166743a6379616e5f636f6e63726574655f706f7764657233fd00196d696e6563726166743a677261795f7465727261636f74746126fd00106d696e6563726166743a736164646c65770100136d696e6563726166743a69726f6e5f646f6f72780100126d696e6563726166743a72656473746f6e65790100226d696e6563726166743a656c6465725f677561726469616e5f737061776e5f656767db0100126d696e6563726166743a63726f7373626f77490200126d696e6563726166743a6f616b5f626f61747b0100186d696e6563726166743a616374697661746f725f7261696c7e00001b6d696e6563726166743a77686974655f7368756c6b65725f626f78da0000156d696e6563726166743a746f726368666c6f776572c8fd00166d696e6563726166743a636f707065725f677261746500fd00146d696e6563726166743a62697263685f626f61747c0100146d696e6563726166743a656c656d656e745f393794ff00216d696e6563726166743a706f6c69736865645f6772616e6974655f73746169727354ff00156d696e6563726166743a6a756e676c655f626f61747d0100216d696e6563726166743a6c696d655f737461696e65645f676c6173735f70616e6579fd001f6d696e6563726166743a6c696768745f626c75655f7465727261636f7474612afd00156d696e6563726166743a7370727563655f626f61747e0100176d696e6563726166743a6465636f72617465645f706f74d9fd00196d696e6563726166743a6368656d69737472795f7461626c65ee0000156d696e6563726166743a636f707065725f62756c62f8fc001e6d696e6563726166743a73696c766572666973685f737061776e5f656767bf0100156d696e6563726166743a6163616369615f626f61747f0100176d696e6563726166743a6461726b5f6f616b5f626f6174800100126d696e6563726166743a69726f6e5f6f72650f00001d6d696e6563726166743a736e6f72745f706f74746572795f7368657264b10200166d696e6563726166743a7772697474656e5f626f6f6b090200156d696e6563726166743a677261795f63616e646c655cfe00116d696e6563726166743a6c656174686572810100156d696e6563726166743a676f6c645f6e7567676574ad01001e6d696e6563726166743a676c6f62655f62616e6e65725f7061747465726e5602000f6d696e6563726166743a627269636b830100136d696e6563726166743a636c61795f62616c6c840100196d696e6563726166743a6e65746865726974655f696e676f746d02002d6d696e6563726166743a6e65746865726974655f757067726164655f736d697468696e675f74656d706c617465b30200146d696e6563726166743a73756761725f63616e65850101156d696e6563726166743a6c69745f70756d706b696e5b00001b6d696e6563726166743a79656c6c6f775f7465727261636f74746129fd00226d696e6563726166743a6865617274627265616b5f706f74746572795f7368657264a80200146d696e6563726166743a656c656d656e745f3233deff00116d696e6563726166743a6772616e697465b2fd000f6d696e6563726166743a70617065728601001d6d696e6563726166743a6d6167656e74615f7368756c6b65725f626f789afd001b6d696e6563726166743a746164706f6c655f737061776e5f6567678302000f6d696e6563726166743a636f72616ccb02000e6d696e6563726166743a626f6f6b8701001e6d696e6563726166743a677261795f636f6e63726574655f706f7764657235fd000d6d696e6563726166743a6d6f645c0200196d696e6563726166743a646561645f686f726e5f636f72616cb3fd00146d696e6563726166743a736c696d655f62616c6c880100116d696e6563726166743a74726964656e742c0200186d696e6563726166743a63686573745f6d696e6563617274890100176d696e6563726166743a636f775f737061776e5f656767b801000d6d696e6563726166743a6567678a01002c6d696e6563726166743a636f6173745f61726d6f725f7472696d5f736d697468696e675f74656d706c617465b60200116d696e6563726166743a636f6d706173738b0100196d696e6563726166743a6e65746865726974655f73776f7264680200196d696e6563726166743a6d757369635f646973635f7374616c270201156d696e6563726166743a66697368696e675f726f648c01010f6d696e6563726166743a636c6f636b8d0100196d696e6563726166743a616e6465736974655f73746169727355ff00136d696e6563726166743a726573657276656436ff00001a6d696e6563726166743a6f63656c6f745f737061776e5f656767c70100186d696e6563726166743a676c6f7773746f6e655f647573748e0100136d696e6563726166743a626c61636b5f6479658f01001d6d696e6563726166743a6d696e65725f706f74746572795f7368657264aa0200136d696e6563726166743a677265656e5f647965910100156d696e6563726166743a7368756c6b65725f626f78d102000e6d696e6563726166743a64656e79d30000176d696e6563726166743a6265655f737061776e5f656767f301001a6d696e6563726166743a6379616e5f7368756c6b65725f626f7893fd001e6d696e6563726166743a64656570736c6174655f627269636b5f77616c6c76fe00186d696e6563726166743a6d75645f627269636b5f736c616222fe00136d696e6563726166743a62726f776e5f6479659201000f6d696e6563726166743a6672616d650b0201126d696e6563726166743a626c75655f6479659301001a6d696e6563726166743a706f6c69736865645f64696f72697465affd00136d696e6563726166743a6974656d2e63616b655c00002c6d696e6563726166743a736e6f75745f61726d6f725f7472696d5f736d697468696e675f74656d706c617465bc0200146d696e6563726166743a707572706c655f6479659401000d6d696e6563726166743a647965d30200176d696e6563726166743a6d757369635f646973635f3133200201126d696e6563726166743a6379616e5f647965950100156d696e6563726166743a6f72616e67655f776f6f6cd3fd00136d696e6563726166743a626c617a655f726f64ab0100146d696e6563726166743a6f616b5f706c616e6b73050000256d696e6563726166743a737469636b795f706973746f6e5f61726d5f636f6c6c6973696f6e27ff00186d696e6563726166743a6c696768745f677261795f647965960100206d696e6563726166743a7069676c696e5f62727574655f737061776e5f656767f80100146d696e6563726166743a656c656d656e745f3431ccff00126d696e6563726166743a677261795f6479659701001a6d696e6563726166743a7261626269745f737061776e5f656767cf0100126d696e6563726166743a70696e6b5f647965980100126d696e6563726166743a6c696d655f647965990100176d696e6563726166743a626c6173745f6675726e6163653cff00146d696e6563726166743a79656c6c6f775f6479659a0100186d696e6563726166743a6c696768745f626c75655f6479659b0100146d696e6563726166743a747572746c655f65676761ff00196d696e6563726166743a646972745f776974685f726f6f7473c2fe00176d696e6563726166743a6d616e67726f76655f646f6f72870200176d696e6563726166743a737461696e65645f676c617373cf0200216d696e6563726166743a626c75655f737461696e65645f676c6173735f70616e6573fd000d6d696e6563726166743a626564a60100146d696e6563726166743a6f72616e67655f6479659d01001c6d696e6563726166743a6379616e5f737461696e65645f676c61737357fd001b6d696e6563726166743a77617865645f636f707065725f62756c62f4fc00196d696e6563726166743a63616d656c5f737061776e5f6567679d0200136d696e6563726166743a77686974655f6479659e0100176d696e6563726166743a79656c6c6f775f63616e646c655ffe002b6d696e6563726166743a64756e655f61726d6f725f7472696d5f736d697468696e675f74656d706c617465b50200136d696e6563726166743a626f6e655f6d65616c9f0100276d696e6563726166743a77617865645f6578706f7365645f63686973656c65645f636f7070657203fd00196d696e6563726166743a6974656d2e666c6f7765725f706f748c0000176d696e6563726166743a747572746c655f68656c6d6574470200116d696e6563726166743a696e6b5f736163a101001d6d696e6563726166743a6d656469756d5f616d6574687973745f627564b5fe00166d696e6563726166743a6c617069735f6c617a756c69a201001f6d696e6563726166743a73747269707065645f6372696d736f6e5f";
        String str4 = "7374656d10ff00106d696e6563726166743a63616d6572615e0200166d696e6563726166743a63686f7275735f66727569743802001c6d696e6563726166743a77617865645f636f707065725f6772617465fcfc00196d696e6563726166743a737573706963696f75735f737465775802000f6d696e6563726166743a7375676172a401001e6d696e6563726166743a707572706c655f737461696e65645f676c61737356fd00126d696e6563726166743a6e616d655f7461672e02001b6d696e6563726166743a637265657065725f737061776e5f656767bd01000e6d696e6563726166743a63616b65a501011e6d696e6563726166743a6e65746865726974655f6368657374706c6174656f0200206d696e6563726166743a6f72616e67655f636f6e63726574655f706f776465723bfd00156d696e6563726166743a626c75655f63616e646c6558fe00126d696e6563726166743a7265706561746572a70101106d696e6563726166743a626561636f6e8a0000226d696e6563726166743a706f6c69736865645f616e6465736974655f73746169727352ff00146d696e6563726166743a66696c6c65645f6d6170a801001b6d696e6563726166743a64726f776e65645f737061776e5f656767e70100106d696e6563726166743a736865617273a90100166d696e6563726166743a77686974655f636172706574ab0000156d696e6563726166743a656e6465725f706561726caa0100146d696e6563726166743a67686173745f74656172ac0100166d696e6563726166743a676c6173735f626f74746c65af0100146d696e6563726166743a656c656d656e745f3434c9ff00176d696e6563726166743a636f6f6b65645f6d7574746f6e3102001e6d696e6563726166743a6665726d656e7465645f7370696465725f657965b00100166d696e6563726166743a626c617a655f706f77646572b101001f6d696e6563726166743a6c696768745f677261795f7465727261636f74746125fd00156d696e6563726166743a6d61676d615f637265616db20100106d696e6563726166743a6a69677361772dff00176d696e6563726166743a62726577696e675f7374616e64b30101156d696e6563726166743a656c656d656e745f31313186ff00126d696e6563726166743a6361756c64726f6eb401011b6d696e6563726166743a636869636b656e5f737061776e5f656767b70100176d696e6563726166743a7069675f737061776e5f656767b90100196d696e6563726166743a73686565705f737061776e5f656767ba01001d6d696e6563726166743a6d6f6f7368726f6f6d5f737061776e5f656767bc0100226d696e6563726166743a62726f776e5f737461696e65645f676c6173735f70616e6572fd00126d696e6563726166743a616e646573697465aefd001d6d696e6563726166743a62616d626f6f5f68616e67696e675f7369676ef6fd002a6d696e6563726166743a7269625f61726d6f725f7472696d5f736d697468696e675f74656d706c617465bd02001c6d696e6563726166743a736b656c65746f6e5f737061776e5f656767c00100146d696e6563726166743a66656e63655f676174656b0000106d696e6563726166743a626c656163686102001a6d696e6563726166743a7370696465725f737061776e5f656767c201001a6d696e6563726166743a636f6c6f7265645f746f7263685f7267ca0000146d696e6563726166743a656c656d656e745f3231e0ff001a6d696e6563726166743a7a6f6d6269655f737061776e5f656767c301001c6d696e6563726166743a76696c6c616765725f737061776e5f656767c50100196d696e6563726166743a73717569645f737061776e5f656767c60100176d696e6563726166743a707572706c655f636172706574a2fd00136d696e6563726166743a636f6d706f737465722bff001c6d696e6563726166743a706f77657265645f636f6d70617261746f72960000176d696e6563726166743a6261745f737061776e5f656767c90100136d696e6563726166743a656c656d656e745f30240000196d696e6563726166743a67686173745f737061776e5f656767ca0100156d696e6563726166743a7761727065645f7369676e740200156d696e6563726166743a6d6f625f737061776e6572340000206d696e6563726166743a63686973656c65645f6e65746865725f627269636b73d2fe000f6d696e6563726166743a636861696e7802011e6d696e6563726166743a6d61676d615f637562655f737061776e5f656767cb0100226d696e6563726166743a7761727065645f66756e6775735f6f6e5f615f737469636b770201176d696e6563726166743a736f756c5f63616d70666972657b02011f6d696e6563726166743a636176655f7370696465725f737061776e5f656767cd01001c6d696e6563726166743a6974656d2e6d616e67726f76655f646f6f7213fe001d6d696e6563726166743a656e6465726d6974655f737061776e5f656767d001001c6d696e6563726166743a677561726469616e5f737061776e5f656767d10100186d696e6563726166743a6875736b5f737061776e5f656767d301001e6d696e6563726166743a6c696d655f636f6e63726574655f706f7764657237fd001a6d696e6563726166743a7069676c696e5f737061776e5f656767f60100186d696e6563726166743a77686974655f636f6e6372657465ec00001d6d696e6563726166743a6578706f7365645f636f707065725f646f6f72effc00176d696e6563726166743a77656570696e675f76696e657319ff001a6d696e6563726166743a70696e6b5f63616e646c655f63616b654cfe00236d696e6563726166743a7769746865725f736b656c65746f6e5f737061776e5f656767d401001e6d696e6563726166743a6272657765725f706f74746572795f7368657264a20200156d696e6563726166743a7370727563655f7369676e4a02001a6d696e6563726166743a646f6e6b65795f737061776e5f656767d50100166d696e6563726166743a646f75626c655f706c616e74af0000186d696e6563726166743a6d756c655f737061776e5f656767d60100226d696e6563726166743a736b656c65746f6e5f686f7273655f737061776e5f656767d70100156d696e6563726166743a656c656d656e745f31303988ff001a6d696e6563726166743a666c6f776572696e675f617a616c6561aefe00196d696e6563726166743a70696e6b5f7465727261636f74746127fd001b6d696e6563726166743a6e65746865726974655f7069636b6178656a0200116d696e6563726166743a6a756b65626f78540000206d696e6563726166743a7a6f6d6269655f686f7273655f737061776e5f656767d80100156d696e6563726166743a62616d626f6f5f646f6f72fbfd00156d696e6563726166743a62616d626f6f5f736c6162fffd00176d696e6563726166743a6e70635f737061776e5f656767da0100136d696e6563726166743a656e645f73746f6e65790000196d696e6563726166743a6c6c616d615f737061776e5f656767dd01001a6d696e6563726166743a7370727563655f77616c6c5f7369676e4aff00176d696e6563726166743a7665785f737061776e5f656767e00100186d696e6563726166743a7477697374696e675f76696e6573e1fe00196d696e6563726166743a7761727065645f74726170646f6f7209ff00246d696e6563726166743a6461796c696768745f6465746563746f725f696e766572746564b200001a6d696e6563726166743a77617264656e5f737061776e5f6567678602001a6d696e6563726166743a6d6167656e74615f636f6e63726574658bfd00236d696e6563726166743a7a6f6d6269655f76696c6c616765725f737061776e5f656767e10100256d696e6563726166743a7765617468657265645f6375745f636f707065725f7374616972739cfe001a6d696e6563726166743a72617069645f66657274696c697a65726202000e6d696e6563726166743a636c6179520000216d696e6563726166743a74726f706963616c5f666973685f737061776e5f656767e30100176d696e6563726166743a636f645f737061776e5f656767e40100196d696e6563726166743a6f72616e67655f636f6e63726574658cfd00176d696e6563726166743a7374616e64696e675f7369676e3f00002b6d696e6563726166743a686f73745f61726d6f725f7472696d5f736d697468696e675f74656d706c617465c302001e6d696e6563726166743a707566666572666973685f737061776e5f656767e501001c6d696e6563726166743a7265645f6d757368726f6f6d5f626c6f636b640000186d696e6563726166743a6372696d736f6e5f66756e6775731cff00146d696e6563726166743a6974656d2e6672616d65c70000106d696e6563726166743a62756e646c657d02011a6d696e6563726166743a73616c6d6f6e5f737061776e5f656767e60100156d696e6563726166743a7761727065645f736c6162f7fe001b6d696e6563726166743a646f6c7068696e5f737061776e5f656767e80100176d696e6563726166743a79656c6c6f775f636172706574a8fd001a6d696e6563726166743a747572746c655f737061776e5f656767e90100146d696e6563726166743a656c656d656e745f3238d9ff00206d696e6563726166743a646f75626c655f6375745f636f707065725f736c616290fe001b6d696e6563726166743a7068616e746f6d5f737061776e5f656767ea01001a6d696e6563726166743a6974656d2e6163616369615f646f6f72c40000226d696e6563726166743a6f786964697a65645f6375745f636f707065725f736c616294fe00176d696e6563726166743a6361745f737061776e5f656767ec0100196d696e6563726166743a6c696768745f677261795f776f6f6cd8fd00166d696e6563726166743a6368657272795f66656e6365ecfd00106d696e6563726166743a71756172747a160200176d696e6563726166743a666f785f737061776e5f656767ee0100146d696e6563726166743a7370727563655f6c6f67c7fd001a6d696e6563726166743a636f62626c6573746f6e655f77616c6c8b00001b6d696e6563726166743a636172726f745f6f6e5f615f737469636b0f0200246d696e6563726166743a77616e646572696e675f7472616465725f737061776e5f656767f001001d6d696e6563726166743a7370727563655f68616e67696e675f7369676e0bfe00126d696e6563726166743a74726170646f6f726000001a6d696e6563726166743a686f676c696e5f737061776e5f656767f50100216d696e6563726166743a6d6167656e74615f636f6e63726574655f706f776465723afd001b6d696e6563726166743a7761727065645f66656e63655f67617465fdfe001e6d696e6563726166743a706c656e74795f706f74746572795f7368657264ac0200256d696e6563726166743a77617865645f7765617468657265645f636f707065725f62756c62f2fc001b6d696e6563726166743a736e69666665725f737061776e5f656767f901001d6d696e6563726166743a6f786964697a65645f6375745f636f70706572a2fe00186d696e6563726166743a676f61745f737061776e5f656767fb0100206d696e6563726166743a6c696d655f676c617a65645f7465727261636f747461e100001e6d696e6563726166743a69726f6e5f676f6c656d5f737061776e5f656767fd0100206d696e6563726166743a656e6465725f647261676f6e5f737061776e5f656767ff01001a6d696e6563726166743a7769746865725f737061776e5f6567670002001c6d696e6563726166743a626c75655f737461696e65645f676c61737355fd00166d696e6563726166743a676c6f775f696e6b5f736163010200166d696e6563726166743a636f707065725f696e676f740202001c6d69";
        String str5 = "6e6563726166743a6f72616e67655f63616e646c655f63616b6551fe00166d696e6563726166743a636f707065725f626c6f636bacfe001d6d696e6563726166743a73747269707065645f6a756e676c655f6c6f67f9ff00146d696e6563726166743a6375745f636f70706572a5fe00216d696e6563726166743a6461726b5f6f616b5f70726573737572655f706c61746568ff00126d696e6563726166743a73656167726173737eff001a6d696e6563726166743a707269736d6172696e655f73686172643f02001b6d696e6563726166743a6375745f636f707065725f7374616972739efe00236d696e6563726166743a7765617468657265645f6375745f636f707065725f736c616295fe00226d696e6563726166743a646f75626c655f73746f6e655f626c6f636b5f736c61623359ff00106d696e6563726166743a6d7574746f6e3002001b6d696e6563726166743a7265645f737461696e65645f676c61737352fd00146d696e6563726166743a7363756c6b5f7665696e35fe001f6d696e6563726166743a77617865645f6375745f636f707065725f736c616293fe00276d696e6563726166743a77617865645f6578706f7365645f6375745f636f707065725f736c616292fe00196d696e6563726166743a6a756e676c655f74726170646f6f726cff00156d696e6563726166743a666972655f636861726765070200296d696e6563726166743a77617865645f7765617468657265645f6375745f636f707065725f736c616291fe00146d696e6563726166743a656c656d656e745f3437c6ff00286d696e6563726166743a77617865645f6f786964697a65645f6375745f636f707065725f736c61623ffe00146d696e6563726166743a7261775f636f707065720502001b6d696e6563726166743a706f6c69736865645f616e646573697465adfd001b6d696e6563726166743a657870657269656e63655f626f74746c65060200176d696e6563726166743a7772697461626c655f626f6f6b080200146d696e6563726166743a656c656d656e745f3639b0ff001b6d696e6563726166743a62616d626f6f5f66656e63655f67617465fcfd00116d696e6563726166743a656d6572616c640a0200256d696e6563726166743a64656570736c6174655f627269636b5f646f75626c655f736c616271fe00146d696e6563726166743a666c6f7765725f706f740c0201136d696e6563726166743a656d7074795f6d61700d02000f6d696e6563726166743a736b756c6c0e0200186d696e6563726166743a6372696d736f6e5f6e796c69756d18ff00196d696e6563726166743a66697265776f726b5f726f636b6574110200176d696e6563726166743a66697265776f726b5f737461721202001a6d696e6563726166743a636f6c6f7265645f746f7263685f6270cc0000156d696e6563726166743a656c656d656e745f3130328fff00186d696e6563726166743a656e6368616e7465645f626f6f6b1302001a6d696e6563726166743a746f74656d5f6f665f756e6479696e67420200156d696e6563726166743a6e6574686572627269636b150200146d696e6563726166743a656c656d656e745f3633b6ff00166d696e6563726166743a746e745f6d696e6563617274170200196d696e6563726166743a686f707065725f6d696e6563617274180200176d696e6563726166743a647261676f6e5f6272656174683a0200156d696e6563726166743a636f62626c6573746f6e65040000106d696e6563726166743a686f70706572190201156d696e6563726166743a7261626269745f686964651b02001d6d696e6563726166743a6c6561746865725f686f7273655f61726d6f721c02001f6d696e6563726166743a7765617468657265645f636f707065725f646f6f72eefc001a6d696e6563726166743a69726f6e5f686f7273655f61726d6f721d02001d6d696e6563726166743a6469616d6f6e645f686f7273655f61726d6f721f02001d6d696e6563726166743a64656570736c6174655f6c617069735f6f726570fe00156d696e6563726166743a6d6963726f5f626c6f636bf9d900156d696e6563726166743a6a756e676c655f646f6f72350200186d696e6563726166743a6d757369635f646973635f6361742102011b6d696e6563726166743a6d757369635f646973635f626c6f636b73220201186d696e6563726166743a6d757369635f646973635f6661722402011c6d696e6563726166743a6d757369635f646973635f6d656c6c6f6869260201156d696e6563726166743a6163616369615f7369676e4d0200196d696e6563726166743a626c75655f7465727261636f74746122fd00166d696e6563726166743a696e666f5f75706461746532f900001d6d696e6563726166743a707269736d6172696e655f6372797374616c732f02000e6d696e6563726166743a6c6561642d02000f6d696e6563726166743a6272757368b20200146d696e6563726166743a656c656d656e745f3332d5ff00156d696e6563726166743a61726d6f725f7374616e643202001a6d696e6563726166743a7068616e746f6d5f6d656d6272616e65480200156d696e6563726166743a7370727563655f646f6f723302001c6d696e6563726166743a62697263685f68616e67696e675f7369676e0afe00146d696e6563726166743a62697263685f646f6f72340200156d696e6563726166743a6163616369615f646f6f72360200146d696e6563726166743a656c656d656e745f3432cbff001c6d696e6563726166743a6e65746865726974655f6c656767696e6773700200176d696e6563726166743a6461726b5f6f616b5f646f6f72370200236d696e6563726166743a79656c6c6f775f737461696e65645f676c6173735f70616e657afd001d6d696e6563726166743a706f707065645f63686f7275735f6672756974390200146d696e6563726166743a656c656d656e745f3733acff00176d696e6563726166743a73706c6173685f706f74696f6e3b0200176d696e6563726166743a7368756c6b65725f7368656c6c4002001d6d696e6563726166743a6c696768745f626c75655f636f6e63726574658afd00186d696e6563726166743a72656473746f6e655f626c6f636b980000156d696e6563726166743a627261696e5f636f72616cbbfd00106d696e6563726166743a62616e6e6572410200176d696e6563726166743a62616d626f6f5f627574746f6e01fe002d6d696e6563726166743a7368617065725f61726d6f725f7472696d5f736d697468696e675f74656d706c617465c20200156d696e6563726166743a656c656d656e745f3131387fff00156d696e6563726166743a69726f6e5f6e7567676574430200196d696e6563726166743a6d616e67726f76655f706c616e6b731afe00196d696e6563726166743a636f72616c5f66616e5f68616e673278ff00146d696e6563726166743a62697263685f7369676e4b0200186d696e6563726166743a636f72616c5f66616e5f646561647aff00116d696e6563726166743a62616c6c6f6f6e630200156d696e6563726166743a6a756e676c655f7369676e4c0200136d696e6563726166743a6f616b5f66656e6365550000176d696e6563726166743a6461726b5f6f616b5f7369676e4e02001f6d696e6563726166743a666c6f7765725f62616e6e65725f7061747465726e4f0200186d696e6563726166743a6d6167656e74615f636172706574aafd00186d696e6563726166743a66726f675f737061776e5f656767820200216d696e6563726166743a706f6c69736865645f64696f726974655f73746169727353ff001f6d696e6563726166743a6d6f6a616e675f62616e6e65725f7061747465726e520200156d696e6563726166743a6d6f6e737465725f656767610000176d696e6563726166743a6368657272795f627574746f6eeefd00266d696e6563726166743a6669656c645f6d61736f6e65645f62616e6e65725f7061747465726e530200296d696e6563726166743a626f72647572655f696e64656e7465645f62616e6e65725f7061747465726e5402001f6d696e6563726166743a7069676c696e5f62616e6e65725f7061747465726e550200126d696e6563726166743a706f7461746f65738e00001c6d696e6563726166743a707572706c655f63616e646c655f63616b6548fe001c6d696e6563726166743a6f72616e67655f7368756c6b65725f626f789bfd00146d696e6563726166743a656c656d656e745f3738a7ff00136d696e6563726166743a686f6e6579636f6d625a0200156d696e6563726166743a6d6f73735f636172706574b1fe00166d696e6563726166743a686f6e65795f626f74746c655b02001a6d696e6563726166743a7265645f6e65746865725f627269636bd70000106d696e6563726166743a6d6f645f65785d0200126d696e6563726166743a636f6d706f756e645f0200126d696e6563726166743a6963655f626f6d62600200126d696e6563726166743a6d65646963696e65640200146d696e6563726166743a676c6f775f737469636b6602001b6d696e6563726166743a6c6f646573746f6e655f636f6d70617373670200146d696e6563726166743a656c656d656e745f3833a2ff00146d696e6563726166743a71756172747a5f6f72659900001a6d696e6563726166743a6e65746865726974655f73686f76656c6902002a6d696e6563726166743a6579655f61726d6f725f7472696d5f736d697468696e675f74656d706c617465b90200286d696e6563726166743a706f6c69736865645f64656570736c6174655f646f75626c655f736c616273fe001d6d696e6563726166743a636861696e5f636f6d6d616e645f626c6f636bbd0000136d696e6563726166743a677261795f776f6f6cd7fd00226d696e6563726166743a6c696768745f626c75655f737461696e65645f676c6173735dfd000e6d696e6563726166743a6c6f6f6d34ff001a6d696e6563726166743a6974656d2e7761727065645f646f6f720bff00176d696e6563726166743a6e65746865726974655f6178656b0200176d696e6563726166743a6e65746865726974655f686f656c02001b6d696e6563726166743a626c61636b5f63616e646c655f63616b6543fe001b6d696e6563726166743a6c696768745f677261795f63616e646c655bfe000d6d696e6563726166743a6d756427fe001a6d696e6563726166743a6e65746865726974655f68656c6d65746e0200196d696e6563726166743a6e65746865726974655f7363726170720200166d696e6563726166743a6372696d736f6e5f7369676e730200126d696e6563726166743a636f6e6372657465cd0200106d696e6563726166743a73706f6e6765130000166d696e6563726166743a6372696d736f6e5f646f6f72750200146d696e6563726166743a686f726e5f636f72616cb8fd001b6d696e6563726166743a636172746f6772617068795f7461626c6538ff00186d696e6563726166743a6e65746865725f7370726f7574737a0201196d696e6563726166743a626c61636b73746f6e655f736c6162e6fe00226d696e6563726166743a706f6c69736865645f626c61636b73746f6e655f736c6162dbfe00206d696e6563726166743a636f62626c65645f64656570736c6174655f736c616284fe00216d696e6563726166743a706f6c69736865645f64656570736c6174655f736c616280fe001d6d696e6563726166743a64656570736c6174655f74696c655f736c61627cfe00176d696e6563726166743a6d616e67726f76655f626f6174890200146d696e6563726166743a686172645f676c617373fd0000126d696e6563726166743a737079676c6173737f02001e6d696e6563726166743a64656570736c6174655f627269636b5f736c616278fe00206d696e6563726166743a636f62626c65645f64656570736c";
        String str6 = "6174655f77616c6c82fe00146d696e6563726166743a656c656d656e745f3736a9ff00186d696e6563726166743a616d6574687973745f73686172647e02001c6d696e6563726166743a686f776c5f706f74746572795f7368657264a902001a6d696e6563726166743a62697263685f63686573745f626f61748d0200146d696e6563726166743a656c656d656e745f3333d4ff001e6d696e6563726166743a6d757369635f646973635f6f7468657273696465800201256d696e6563726166743a736d6f6f74685f7265645f73616e6473746f6e655f73746169727350ff00136d696e6563726166743a676f61745f686f726e810200186d696e6563726166743a6d6167656e74615f63616e646c6561fe00146d696e6563726166743a66726f675f737061776e2cfe00306d696e6563726166743a77617865645f7765617468657265645f646f75626c655f6375745f636f707065725f736c61628afe00196d696e6563726166743a616c6c61795f737061776e5f656767850200206d696e6563726166743a73747269707065645f7761727065645f687970686165d3fe00176d696e6563726166743a6d616e67726f76655f7369676e880200176d696e6563726166743a6d616e67726f76655f736c616217fe001c6d696e6563726166743a6d616e67726f76655f70726f706167756c6526fe00186d696e6563726166743a6d616e67726f76655f726f6f74731efe001e6d696e6563726166743a6d756464795f6d616e67726f76655f726f6f74731dfe00216d696e6563726166743a677265656e5f676c617a65645f7465727261636f747461e90000166d696e6563726166743a6d757369635f646973635f358a02011e6d696e6563726166743a6a756e676c655f7374616e64696e675f7369676e44ff00196d696e6563726166743a646973635f667261676d656e745f358b0200156d696e6563726166743a63616e646c655f63616b6553fe00206d696e6563726166743a70696e6b5f676c617a65645f7465727261636f747461e20000176d696e6563726166743a626c75655f636f6e637265746582fd001b6d696e6563726166743a6a756e676c655f63686573745f626f61748e02001b6d696e6563726166743a7370727563655f63686573745f626f61748f02001f6d696e6563726166743a77686974655f636f6e63726574655f706f77646572ed00002c6d696e6563726166743a73706972655f61726d6f725f7472696d5f736d697468696e675f74656d706c617465be02001b6d696e6563726166743a6163616369615f63686573745f626f61749002001d6d696e6563726166743a6461726b5f6f616b5f63686573745f626f61749102001d6d696e6563726166743a6d616e67726f76655f63686573745f626f6174920200156d696e6563726166743a696e666f5f757064617465f800001a6d696e6563726166743a7265636f766572795f636f6d70617373940200186d696e6563726166743a7363756c6b5f73687269656b657233fe00176d696e6563726166743a74726970776972655f686f6f6b830000146d696e6563726166743a63686573745f626f6174930200146d696e6563726166743a6563686f5f7368617264950200186d696e6563726166743a62697263685f74726170646f6f726eff00206d696e6563726166743a7472616465725f6c6c616d615f737061776e5f6567679602000f6d696e6563726166743a7363756c6b36fe00156d696e6563726166743a6368657272795f626f6174970200156d696e6563726166743a6c69745f6675726e6163653e0000156d696e6563726166743a6368657272795f646f6f72edfd00186d696e6563726166743a6368657272795f7361706c696e67ddfd001d6d696e6563726166743a6368657272795f68616e67696e675f7369676eeafd00156d696e6563726166743a6368657272795f736c6162e5fd001c6d696e6563726166743a62616d626f6f5f6d6f736169635f736c6162f4fd00176d696e6563726166743a79656c6c6f775f666c6f776572250000156d696e6563726166743a62616d626f6f5f726166749b02001b6d696e6563726166743a62616d626f6f5f63686573745f726166749c02001a6d696e6563726166743a6f616b5f68616e67696e675f7369676e0cfe00146d696e6563726166743a656c656d656e745f38399cff00176d696e6563726166743a71756172747a5f7374616972739c0000206d696e6563726166743a6578706c6f7265725f706f74746572795f7368657264a502001d6d696e6563726166743a6a756e676c655f68616e67696e675f7369676e09fe00146d696e6563726166743a656c656d656e745f39319aff001d6d696e6563726166743a6163616369615f68616e67696e675f7369676e08fe00146d696e6563726166743a656c656d656e745f3832a3ff001f6d696e6563726166743a6461726b5f6f616b5f68616e67696e675f7369676e07fe00176d696e6563726166743a706f6c69736865645f7475666614fd001e6d696e6563726166743a6372696d736f6e5f68616e67696e675f7369676e06fe001d6d696e6563726166743a7761727065645f68616e67696e675f7369676e05fe00166d696e6563726166743a62726f776e5f63616e646c6557fe001f6d696e6563726166743a77617865645f6f786964697a65645f636f7070657242fe00216d696e6563726166743a646f75626c655f73746f6e655f626c6f636b5f736c61622b00001e6d696e6563726166743a6172636865725f706f74746572795f73686572649f0200106d696e6563726166743a636163747573510000146d696e6563726166743a656c656d656e745f393992ff001d6d696e6563726166743a626c6164655f706f74746572795f7368657264a102001a6d696e6563726166743a677261795f63616e646c655f63616b654bfe001e6d696e6563726166743a64616e6765725f706f74746572795f7368657264a402001e6d696e6563726166743a667269656e645f706f74746572795f7368657264a602001a6d696e6563726166743a70696e6b5f7368756c6b65725f626f7896fd001d6d696e6563726166743a68656172745f706f74746572795f7368657264a702001d6d696e6563726166743a73686561665f706f74746572795f7368657264ae0200106d696e6563726166743a6c61646465724100001e6d696e6563726166743a756e6c69745f72656473746f6e655f746f7263684b00001f6d696e6563726166743a7368656c7465725f706f74746572795f7368657264af02002d6d696e6563726166743a73656e7472795f61726d6f725f7472696d5f736d697468696e675f74656d706c617465b402002b6d696e6563726166743a776172645f61726d6f725f7472696d5f736d697468696e675f74656d706c617465b802002b6d696e6563726166743a746964655f61726d6f725f7472696d5f736d697468696e675f74656d706c617465bb0200166d696e6563726166743a62616d626f6f5f626c6f636bf1fd00206d696e6563726166743a7265645f737461696e65645f676c6173735f70616e6570fd00306d696e6563726166743a77617966696e6465725f61726d6f725f7472696d5f736d697468696e675f74656d706c617465c00200146d696e6563726166743a7265645f6361727065749efd001a6d696e6563726166743a6d757369635f646973635f72656c6963c402010e6d696e6563726166743a776f6f6cc602000f6d696e6563726166743a73746f6e65010000226d696e6563726166743a677265656e5f737461696e65645f676c6173735f70616e6571fd00116d696e6563726166743a64696f72697465b0fd00166d696e6563726166743a62697263685f737461697273870000156d696e6563726166743a656c656d656e745f31313483ff00216d696e6563726166743a637261636b65645f64656570736c6174655f74696c657367fe001a6d696e6563726166743a77686974655f7465727261636f7474619f00002c6d696e6563726166743a637261636b65645f706f6c69736865645f626c61636b73746f6e655f627269636b73e8fe001b6d696e6563726166743a6f72616e67655f7465727261636f7474612cfd00196d696e6563726166743a6c696d655f7465727261636f74746128fd00196d696e6563726166743a6379616e5f7465727261636f74746124fd001b6d696e6563726166743a707572706c655f7465727261636f74746123fd001a6d696e6563726166743a677265656e5f7465727261636f74746120fd00186d696e6563726166743a7265645f7465727261636f7474611ffd001a6d696e6563726166743a626c61636b5f7465727261636f7474611efd00196d696e6563726166743a666c65746368696e675f7461626c6537ff00166d696e6563726166743a62726f776e5f636172706574a0fd001f6d696e6563726166743a737461696e65645f68617264656e65645f636c6179c502001c6d696e6563726166743a64656570736c6174655f636f616c5f6f72656afe00136d696e6563726166743a747566665f736c616218fd00136d696e6563726166743a656c656d656e745f39ecff001c6d696e6563726166743a706f6c69736865645f747566665f736c616213fd00196d696e6563726166743a6c696768745f626c75655f776f6f6ccefd00236d696e6563726166743a77617865645f6f786964697a65645f6375745f636f7070657241fe00196d696e6563726166743a747566665f627269636b5f736c61620dfd00136d696e6563726166743a656c656d656e745f33f2ff00156d696e6563726166743a636f707065725f646f6f72f0fc001e6d696e6563726166743a6f786964697a65645f636f707065725f646f6f72edfc00176d696e6563726166743a7761727065645f6e796c69756d17ff001b6d696e6563726166743a77617865645f636f707065725f646f6f72ecfc001b6d696e6563726166743a73746f6e655f626c6f636b5f736c6162345aff00136d696e6563726166743a77617465726c696c796f0000236d696e6563726166743a77617865645f6578706f7365645f636f707065725f646f6f72ebfc00246d696e6563726166743a77617865645f6f786964697a65645f636f707065725f646f6f72e9fc00146d696e6563726166743a6163616369615f6c6f67a20000146d696e6563726166743a77686974655f776f6f6c2300001d6d696e6563726166743a6e65746865725f627269636b5f737461697273720000166d696e6563726166743a6d6167656e74615f776f6f6ccbfd00156d696e6563726166743a79656c6c6f775f776f6f6cd2fd001c6d696e6563726166743a63686973656c65645f626f6f6b7368656c66f2fd00136d696e6563726166743a6c696d655f776f6f6cd1fd00136d696e6563726166743a70696e6b5f776f6f6ccafd00136d696e6563726166743a6379616e5f776f6f6ccffd000e6d696e6563726166743a626f6174d20200156d696e6563726166743a707572706c655f776f6f6cccfd00136d696e6563726166743a626c75655f776f6f6ccdfd00146d696e6563726166743a656c656d656e745f3138e3ff00146d696e6563726166743a62726f776e5f776f6f6cd5fd00146d696e6563726166743a656c656d656e745f3239d8ff001c6d696e6563726166743a6368657272795f646f75626c655f736c6162e4fd001d6d696e6563726166743a62726f776e5f737461696e65645f676c61737354fd00126d696e6563726166743a7265645f776f6f6cd4fd001e6d696e6563726166743a7370727563655f7374616e64696e675f7369676e4bff00176d696e6563726166743a737469636b795f706973746f6e1d0000146d696e6563726166743a626c61636b5f776f6f6cd6fd001b6d696e6563726166743a62726f776e5f7368756c6b65725f626f7890fd00206d696e6563726166743a6461726b5f707269736d617269";
        String str7 = "6e655f737461697273fdff00206d696e6563726166743a64656570736c6174655f627269636b5f73746169727377fe00176d696e6563726166743a6f72616e67655f636172706574abfd001f6d696e6563726166743a62616d626f6f5f70726573737572655f706c617465fefd001b6d696e6563726166743a6c696768745f626c75655f636172706574a9fd00156d696e6563726166743a677261795f636172706574a5fd000f6d696e6563726166743a736c696d65a50000116d696e6563726166743a70756d706b696e5600001b6d696e6563726166743a6c696768745f677261795f636172706574a4fd00166d696e6563726166743a677265656e5f6361727065749ffd00116d696e6563726166743a6f616b5f6c6f67110000136d696e6563726166743a62697263685f6c6f67c6fd00146d696e6563726166743a6a756e676c655f6c6f67c5fd000d6d696e6563726166743a6c6f67c80200156d696e6563726166743a62697263685f66656e6365c0fd00196d696e6563726166743a7374727563747572655f626c6f636bfc0000166d696e6563726166743a6a756e676c655f66656e6365befd00166d696e6563726166743a6163616369615f66656e6365c1fd00186d696e6563726166743a6461726b5f6f616b5f66656e6365bffd000f6d696e6563726166743a66656e6365c90200146d696e6563726166743a656c656d656e745f3533c0ff00176d696e6563726166743a70696e6b5f636f6e637265746587fd00146d696e6563726166743a73746f6e65627269636b6200001b6d696e6563726166743a6c69745f626c6173745f6675726e6163652aff00156d696e6563726166743a636f72616c5f626c6f636b7cff00176d696e6563726166743a62616d626f6f5f73746169727300fe001a6d696e6563726166743a73746f6e655f626c6f636b5f736c61622c0000106d696e6563726166743a6c65617665731200001b6d696e6563726166743a73746f6e655f626c6f636b5f736c616232b60000116d696e6563726166743a6c656176657332a100001d6d696e6563726166743a62697263685f7374616e64696e675f7369676e46ff00266d696e6563726166743a63686973656c65645f706f6c69736865645f626c61636b73746f6e65e9fe001b6d696e6563726166743a73746f6e655f626c6f636b5f736c6162335eff00226d696e6563726166743a646f75626c655f73746f6e655f626c6f636b5f736c616232b500001d6d696e6563726166743a6e6f726d616c5f73746f6e655f7374616972734cff00256d696e6563726166743a77617865645f6f786964697a65645f636f707065725f6772617465f9fc000d6d696e6563726166743a746e742e0000226d696e6563726166743a646f75626c655f73746f6e655f626c6f636b5f736c61623458ff00166d696e6563726166743a74696e7465645f676c617373b2fe001c6d696e6563726166743a6c617267655f616d6574687973745f627564b6fe00136d696e6563726166743a636f72616c5f66616e7bff00246d696e6563726166743a706f6c69736865645f626c61636b73746f6e655f627574746f6ed8fe00146d696e6563726166743a7365615f7069636b6c6564ff00296d696e6563726166743a706f6c69736865645f626c61636b73746f6e655f646f75626c655f736c6162dafe00116d696e6563726166743a7361706c696e67060000106d696e6563726166743a74617267657411ff00206d696e6563726166743a626c61636b73746f6e655f646f75626c655f736c6162e5fe00176d696e6563726166743a617a616c65615f6c6561766573bcfe00206d696e6563726166743a617a616c65615f6c65617665735f666c6f7765726564bbfe00136d696e6563726166743a736f756c5f6669726513ff00136d696e6563726166743a73616e6473746f6e65180000176d696e6563726166743a7370727563655f627574746f6e70ff00176d696e6563726166743a7265645f73616e6473746f6e65b300001b6d696e6563726166743a6e65746865725f776172745f626c6f636bd60000156d696e6563726166743a656c656d656e745f31313681ff00176d696e6563726166743a6372696d736f6e5f726f6f747321ff00166d696e6563726166743a7761727065645f726f6f747320ff00146d696e6563726166743a7265645f666c6f7765722600001b6d696e6563726166743a6461796c696768745f6465746563746f72970000146d696e6563726166743a736e6f775f6c617965724e00001a6d696e6563726166743a626c75655f63616e646c655f63616b6547fe000f6d696e6563726166743a6d61676d61d50000146d696e6563726166743a656c656d656e745f3232dfff00166d696e6563726166743a71756172747a5f626c6f636b9b0000146d696e6563726166743a747562655f636f72616c7dff00166d696e6563726166743a627562626c655f636f72616cbafd00146d696e6563726166743a666972655f636f72616cb9fd00156d696e6563726166743a6469616d6f6e645f6f7265380000196d696e6563726166743a646561645f747562655f636f72616cb7fd00116d696e6563726166743a6c65637465726e3eff001a6d696e6563726166743a646561645f627261696e5f636f72616cb6fd001b6d696e6563726166743a646561645f627562626c655f636f72616cb5fd001c6d696e6563726166743a736d616c6c5f616d6574687973745f627564b4fe00196d696e6563726166743a646561645f666972655f636f72616cb4fd00236d696e6563726166743a707572706c655f737461696e65645f676c6173735f70616e6574fd00136d696e6563726166743a736f756c5f736f696c14ff001f6d696e6563726166743a6163616369615f70726573737572655f706c6174656aff00136d696e6563726166743a74616c6c67726173731f0000206d696e6563726166743a77617865645f7765617468657265645f636f70706572a6fe001e6d696e6563726166743a62726f776e5f6d757368726f6f6d5f626c6f636b630000156d696e6563726166743a656c656d656e745f3130338eff00166d696e6563726166743a6461726b5f6f616b5f6c6f67c4fd000e6d696e6563726166743a6c6f6732cc02001a6d696e6563726166743a656e645f706f7274616c5f6672616d65780000176d696e6563726166743a7761727065645f66756e6775731bff00146d696e6563726166743a656c656d656e745f393299ff000f6d696e6563726166743a616e76696c910000116d696e6563726166743a636f6e6475697463ff00146d696e6563726166743a707269736d6172696e65a80000176d696e6563726166743a6c696d655f636f6e637265746588fd00176d696e6563726166743a677261795f636f6e637265746586fd001d6d696e6563726166743a6c696768745f677261795f636f6e637265746585fd00176d696e6563726166743a6379616e5f636f6e637265746584fd00196d696e6563726166743a707572706c655f636f6e637265746583fd00186d696e6563726166743a62726f776e5f636f6e637265746581fd00146d696e6563726166743a656c656d656e745f3536bdff00186d696e6563726166743a677265656e5f636f6e637265746580fd00156d696e6563726166743a6c696d655f63616e646c655efe00166d696e6563726166743a7265645f636f6e63726574657ffd00186d696e6563726166743a626c61636b5f636f6e63726574657efd00176d696e6563726166743a6a756e676c655f706c616e6b731bfd00216d696e6563726166743a7265645f6e65746865725f627269636b5f73746169727348ff001c6d696e6563726166743a677261795f737461696e65645f676c61737359fd00206d696e6563726166743a6c696768745f677261795f7368756c6b65725f626f7894fd00246d696e6563726166743a6c696768745f626c75655f636f6e63726574655f706f7764657239fd000f6d696e6563726166743a6772617373020000206d696e6563726166743a79656c6c6f775f636f6e63726574655f706f7764657238fd001e6d696e6563726166743a70696e6b5f636f6e63726574655f706f7764657236fd00196d696e6563726166743a7370727563655f74726170646f6f726bff00246d696e6563726166743a6c696768745f677261795f636f6e63726574655f706f7764657234fd00206d696e6563726166743a707572706c655f636f6e63726574655f706f7764657232fd001e6d696e6563726166743a626c75655f636f6e63726574655f706f7764657231fd001f6d696e6563726166743a677265656e5f636f6e63726574655f706f776465722ffd001d6d696e6563726166743a7265645f636f6e63726574655f706f776465722efd001f6d696e6563726166743a626c61636b5f636f6e63726574655f706f776465722dfd00196d696e6563726166743a636f6e63726574655f706f77646572ce02001d6d696e6563726166743a77686974655f737461696e65645f676c617373f10000146d696e6563726166743a656c656d656e745f3735aaff001e6d696e6563726166743a6f72616e67655f737461696e65645f676c6173735ffd001f6d696e6563726166743a6d6167656e74615f737461696e65645f676c6173735efd00146d696e6563726166743a656c656d656e745f3634b5ff001e6d696e6563726166743a79656c6c6f775f737461696e65645f676c6173735cfd001c6d696e6563726166743a6c696d655f737461696e65645f676c6173735bfd001f6d696e6563726166743a6d75645f627269636b5f646f75626c655f736c616221fe00156d696e6563726166743a6974656d2e686f707065729a00000e6d696e6563726166743a776f6f642cff001d6d696e6563726166743a6372696d736f6e5f646f75626c655f736c6162f6fe001c6d696e6563726166743a70696e6b5f737461696e65645f676c6173735afd00226d696e6563726166743a6c696768745f677261795f737461696e65645f676c61737358fd00166d696e6563726166743a62697263685f627574746f6e73ff00206d696e6563726166743a64656570736c6174655f72656473746f6e655f6f72656dfe001e6d696e6563726166743a7265696e666f726365645f64656570736c6174652efe001d6d696e6563726166743a626c61636b5f737461696e65645f676c61737351fd00226d696e6563726166743a77686974655f737461696e65645f676c6173735f70616e65a00000236d696e6563726166743a6f72616e67655f737461696e65645f676c6173735f70616e657dfd001c6d696e6563726166743a6974656d2e6461726b5f6f616b5f646f6f72c50000246d696e6563726166743a6d6167656e74615f737461696e65645f676c6173735f70616e657cfd00156d696e6563726166743a6c696768745f626c6f636b29ff00276d696e6563726166743a6c696768745f626c75655f737461696e65645f676c6173735f70616e657bfd00156d696e6563726166743a656e645f6372797374616cd60200216d696e6563726166743a70696e6b5f737461696e65645f676c6173735f70616e6578fd00216d696e6563726166743a6379616e5f737461696e65645f676c6173735f70616e6575fd00146d696e6563726166743a656c656d656e745f3136e5ff00226d696e6563726166743a626c61636b5f737461696e65645f676c6173735f70616e656ffd001c6d696e6563726166743a756e647965645f7368756c6b65725f626f78cd0000206d696e6563726166743a6c696768745f626c75655f7368756c6b65725f626f7899fd001c6d696e6563726166743a79656c6c6f775f7368756c6b65725f626f7898fd001a6d696e6563726166743a6c696d655f7368756c6b65725f626f7897fd001a6d696e6563726166743a677261795f7368756c6b65725f626f7895fd001f6d696e6563726166743a646565";
        String str8 = "70736c6174655f6469616d6f6e645f6f72656bfe001c6d696e6563726166743a707572706c655f7368756c6b65725f626f7892fd001a6d696e6563726166743a626c75655f7368756c6b65725f626f7891fd001b6d696e6563726166743a677265656e5f7368756c6b65725f626f788ffd00196d696e6563726166743a7265645f7368756c6b65725f626f788efd001e6d696e6563726166743a7265645f73616e6473746f6e655f737461697273b400001b6d696e6563726166743a626c61636b5f7368756c6b65725f626f788dfd00186d696e6563726166743a6361727665645f70756d706b696e65ff00106d696e6563726166743a706973746f6e2100001a6d696e6563726166743a746f726368666c6f7765725f63726f70c9fd00106d696e6563726166743a62616d626f6f5dff00156d696e6563726166743a73636166666f6c64696e675bff00126d696e6563726166743a6f62736572766572fb00001a6d696e6563726166743a6379616e5f63616e646c655f63616b6549fe00146d696e6563726166743a6772696e6473746f6e653dff000e6d696e6563726166743a62656c6c32ff001c6d696e6563726166743a686172645f737461696e65645f676c617373fe0000146d696e6563726166743a656c656d656e745f3834a1ff00216d696e6563726166743a686172645f737461696e65645f676c6173735f70616e65bf00001f6d696e6563726166743a776f6f64656e5f70726573737572655f706c617465480000116d696e6563726166743a6d6f645f6f7265e60000166d696e6563726166743a6372696d736f6e5f736c6162f8fe00106d696e6563726166743a63616e646c6564fe00176d696e6563726166743a6f72616e67655f63616e646c6562fe001b6d696e6563726166743a6c696768745f626c75655f63616e646c6560fe000e6d696e6563726166743a66697265330000156d696e6563726166743a70696e6b5f63616e646c655dfe00156d696e6563726166743a6379616e5f63616e646c655afe00176d696e6563726166743a707572706c655f63616e646c6559fe00166d696e6563726166743a677265656e5f63616e646c6556fe00146d696e6563726166743a7265645f63616e646c6555fe00216d696e6563726166743a706f6c69736865645f64656570736c6174655f77616c6c7efe00166d696e6563726166743a626c61636b5f63616e646c6554fe00136d696e6563726166743a656c656d656e745f34f1ff00136d696e6563726166743a656c656d656e745f35f0ff00216d696e6563726166743a77617865645f6375745f636f707065725f7374616972739afe00136d696e6563726166743a656c656d656e745f36efff00136d696e6563726166743a656c656d656e745f38edff00146d696e6563726166743a656c656d656e745f3130ebff00146d696e6563726166743a656c656d656e745f3131eaff00146d696e6563726166743a656c656d656e745f3132e9ff00146d696e6563726166743a656c656d656e745f3134e7ff002a6d696e6563726166743a636c69656e745f726571756573745f706c616365686f6c6465725f626c6f636b2ffe00146d696e6563726166743a656c656d656e745f3137e4ff00146d696e6563726166743a656c656d656e745f3139e2ff001f6d696e6563726166743a706561726c657363656e745f66726f676c696768742bfe00146d696e6563726166743a656c656d656e745f3230e1ff00146d696e6563726166743a656c656d656e745f3234ddff00146d696e6563726166743a656c656d656e745f3236dbff00146d696e6563726166743a656c656d656e745f3330d7ff00146d696e6563726166743a656c656d656e745f3331d6ff00146d696e6563726166743a656c656d656e745f3334d3ff00146d696e6563726166743a656c656d656e745f3336d1ff00146d696e6563726166743a656c656d656e745f3337d0ff00146d696e6563726166743a656c656d656e745f3338cfff00146d696e6563726166743a656c656d656e745f3339ceff00146d696e6563726166743a656c656d656e745f3430cdff00146d696e6563726166743a656c656d656e745f3435c8ff00146d696e6563726166743a656c656d656e745f3436c7ff00146d696e6563726166743a656c656d656e745f3438c5ff00146d696e6563726166743a656c656d656e745f3534bfff00186d696e6563726166743a6372616674696e675f7461626c653a0000146d696e6563726166743a656c656d656e745f3537bcff00146d696e6563726166743a656c656d656e745f3538bbff00146d696e6563726166743a656c656d656e745f3539baff00146d696e6563726166743a656c656d656e745f3630b9ff00146d696e6563726166743a656c656d656e745f3635b4ff00146d696e6563726166743a656c656d656e745f3636b3ff00146d696e6563726166743a656c656d656e745f3637b2ff00146d696e6563726166743a656c656d656e745f3638b1ff00146d696e6563726166743a656c656d656e745f3730afff00146d696e6563726166743a656c656d656e745f3731aeff00146d696e6563726166743a656c656d656e745f3732adff00196d696e6563726166743a6461726b5f6f616b5f627574746f6e72ff00146d696e6563726166743a656c656d656e745f3737a8ff00116d696e6563726166743a63726166746572c7fe00146d696e6563726166743a656c656d656e745f3739a6ff00186d696e6563726166743a72656473746f6e655f746f7263684c0000146d696e6563726166743a656c656d656e745f3831a4ff00156d696e6563726166743a747566665f627269636b730efd00146d696e6563726166743a656c656d656e745f3835a0ff00146d696e6563726166743a656c656d656e745f38379eff00146d696e6563726166743a656c656d656e745f38389dff00146d696e6563726166743a656c656d656e745f39309bff00146d696e6563726166743a656c656d656e745f393398ff00146d696e6563726166743a656c656d656e745f393596ff00296d696e6563726166743a77617865645f6578706f7365645f6375745f636f707065725f73746169727399fe00146d696e6563726166743a656c656d656e745f393695ff00146d696e6563726166743a656c656d656e745f393893ff00246d696e6563726166743a706f6c69736865645f626c61636b73746f6e655f627269636b73eefe00156d696e6563726166743a656c656d656e745f31303091ff00156d696e6563726166743a656c656d656e745f3130358cff00156d696e6563726166743a656c656d656e745f3130368bff00156d696e6563726166743a656c656d656e745f3130378aff00156d696e6563726166743a656c656d656e745f31303889ff00156d696e6563726166743a656c656d656e745f31313087ff00156d696e6563726166743a656c656d656e745f31313285ff00176d696e6563726166743a7761727065645f627574746f6efbfe00156d696e6563726166743a656c656d656e745f31313384ff00156d696e6563726166743a656c656d656e745f31313582ff00156d696e6563726166743a656c656d656e745f31313780ff00196d696e6563726166743a6e65746865726974655f626c6f636bf2fe001b6d696e6563726166743a77686974655f63616e646c655f63616b6552fe00186d696e6563726166743a7265737061776e5f616e63686f72f0fe00196d696e6563726166743a637279696e675f6f6273696469616edffe00186d696e6563726166743a62616e6e65725f7061747465726ed40200176d696e6563726166743a736d6f6f74685f626173616c7487fe00166d696e6563726166743a676c6f775f62657272696573d70200196d696e6563726166743a6974656d2e676c6f775f6672616d65adfe00196d696e6563726166743a706f6c69736865645f626173616c7415ff00196d696e6563726166743a6e65746865725f676f6c645f6f7265e0fe001b6d696e6563726166743a6d616e67726f76655f74726170646f6f7210fe001e6d696e6563726166743a706973746f6e5f61726d5f636f6c6c6973696f6e220000286d696e6563726166743a77617865645f6f786964697a65645f63686973656c65645f636f7070657202fd001e6d696e6563726166743a64656570736c6174655f636f707065725f6f726568fe00196d696e6563726166743a747566665f627269636b5f77616c6c0afd00176d696e6563726166743a6a756e676c655f627574746f6e71ff001f6d696e6563726166743a6368657272795f70726573737572655f706c617465e6fd001b6d696e6563726166743a6372696d736f6e5f77616c6c5f7369676e04ff00226d696e6563726166743a636f62626c65645f64656570736c6174655f73746169727383fe00176d696e6563726166743a68616e67696e675f726f6f7473c1fe00116d696e6563726166743a63616c63697465bafe001f6d696e6563726166743a73747269707065645f6461726b5f6f616b5f6c6f67f7ff00176d696e6563726166743a72656473746f6e655f6c616d707b0000176d696e6563726166743a62616d626f6f5f706c616e6b7302fe001b6d696e6563726166743a6d6f7373795f636f62626c6573746f6e65300000136d696e6563726166743a64656570736c61746586fe001e6d696e6563726166743a7761727065645f7374616e64696e675f7369676e05ff00166d696e6563726166743a706974636865725f63726f70c2fd00286d696e6563726166743a706f6c69736865645f626c61636b73746f6e655f627269636b5f77616c6ceafe001f6d696e6563726166743a7761727065645f70726573737572655f706c617465f9fe001a6d696e6563726166743a7761727065645f77616c6c5f7369676e03ff00146d696e6563726166743a6f616b5f737461697273350000146d696e6563726166743a7061636b65645f696365ae0000216d696e6563726166743a736d6f6f74685f73616e6473746f6e655f7374616972734fff00146d696e6563726166743a7061636b65645f6d756423fe00206d696e6563726166743a6c696768745f626c75655f63616e646c655f63616b654ffe00186d696e6563726166743a616d6574687973745f626c6f636bb9fe00146d696e6563726166743a676f6c645f626c6f636b290000236d696e6563726166743a7765617468657265645f63686973656c65645f636f7070657206fd00296d696e6563726166743a77617865645f7765617468657265645f63686973656c65645f636f7070657201fd00136d696e6563726166743a6e6f7465626c6f636b190000236d696e6563726166743a706f6c69736865645f747566665f646f75626c655f736c616212fd000e6d696e6563726166743a74756666b3fe00166d696e6563726166743a6d616e67726f76655f6c6f671cfe00246d696e6563726166743a64656570736c6174655f74696c655f646f75626c655f736c616272fe00246d696e6563726166743a77617865645f6578706f7365645f636f707065725f6772617465fbfc00296d696e6563726166743a6f786964697a65645f646f75626c655f6375745f636f707065725f736c61628dfe00166d696e6563726166743a73746f6e655f627574746f6e4d0000286d696e6563726166743a6578706f7365645f646f75626c655f6375745f636f707065725f736c61628ffe00206d696e6563726166743a626c75655f676c617a65645f7465727261636f747461e70000166d696e6563726166743a62616d626f6f5f66656e6365fdfd00176d696e6563726166743a68617264656e65645f636c6179ac0000196d696e6563726166743a686172645f676c6173735f70616e65be00001e6d696e6563726166743a77617865645f6578706f7365645f636f70706572a7fe001c6d696e6563726166743a706f6c69736865645f747566665f77616c6c10fd002a6d69";
        String str9 = "6e6563726166743a77617865645f6f786964697a65645f6375745f636f707065725f73746169727340fe000f6d696e6563726166743a746f726368320000186d696e6563726166743a6d75645f627269636b5f77616c6c1ffe00196d696e6563726166743a6472697073746f6e655f626c6f636bc3fe00196d696e6563726166743a6368657272795f74726170646f6f72e1fd00126d696e6563726166743a676f6c645f6f72650e0000156d696e6563726166743a73746f6e65637574746572f50000176d696e6563726166743a7761727065645f706c616e6b730dff00156d696e6563726166743a676f6c64656e5f7261696c1b00001b6d696e6563726166743a696e76697369626c655f626564726f636b5f00001e6d696e6563726166743a6f786964697a65645f636f707065725f62756c62f5fc00226d696e6563726166743a6f72616e67655f676c617a65645f7465727261636f747461dd0000176d696e6563726166743a656d6572616c645f626c6f636b850000196d696e6563726166743a737573706963696f75735f73616e64effd00276d696e6563726166743a68656176795f77656967687465645f70726573737572655f706c617465940000146d696e6563726166743a6d6f73735f626c6f636bc0fe00176d696e6563726166743a6c696768746e696e675f726f64c8fe001a6d696e6563726166743a756e64657277617465725f746f726368ef0000196d696e6563726166743a6f636872655f66726f676c6967687429fe00176d696e6563726166743a6368657272795f737461697273e3fd00136d696e6563726166743a747566665f77616c6c15fd00196d696e6563726166743a676c6f77696e676f6273696469616ef60000186d696e6563726166743a62726f776e5f6d757368726f6f6d270000216d696e6563726166743a62726f776e5f676c617a65645f7465727261636f747461e800001f6d696e6563726166743a77617865645f636f707065725f74726170646f6f72e4fc00166d696e6563726166743a6d6f76696e675f626c6f636bfa0000196d696e6563726166743a6f786964697a65645f636f70706572a9fe00146d696e6563726166743a636f707065725f6f7265c9fe00196d696e6563726166743a6461726b5f6f616b5f706c616e6b7319fd001e6d696e6563726166743a62697263685f70726573737572655f706c61746569ff00266d696e6563726166743a636176655f76696e65735f626f64795f776974685f6265727269657389fe001a6d696e6563726166743a73616e6473746f6e655f7374616972738000002a6d696e6563726166743a706f6c69736865645f626c61636b73746f6e655f627269636b5f737461697273edfe001f6d696e6563726166743a6a756e676c655f70726573737572655f706c61746567ff001b6d696e6563726166743a677265656e5f63616e646c655f63616b6545fe001f6d696e6563726166743a73747269707065645f62616d626f6f5f626c6f636bf0fd00186d696e6563726166743a7363756c6b5f636174616c79737434fe001b6d696e6563726166743a62726f776e5f63616e646c655f63616b6546fe00176d696e6563726166743a7075727075725f737461697273cb00001a6d696e6563726166743a6163616369615f77616c6c5f7369676e41ff00276d696e6563726166743a6c696768745f77656967687465645f70726573737572655f706c6174659300001d6d696e6563726166743a706f6c69736865645f626c61636b73746f6e65ddfe00126d696e6563726166743a6d7963656c69756d6e0000166d696e6563726166743a73746f6e655f737461697273430000176d696e6563726166743a7761727065645f73746169727301ff00206d696e6563726166743a7765617468657265645f636f707065725f6772617465fefc001a6d696e6563726166743a6974656d2e776f6f64656e5f646f6f72400000176d696e6563726166743a776f6f64656e5f627574746f6e8f0000176d696e6563726166743a706974636865725f706c616e749cfd001f6d696e6563726166743a7370727563655f70726573737572655f706c61746566ff001a6d696e6563726166743a62697263685f66656e63655f67617465b80000176d696e6563726166743a72656473746f6e655f77697265370000226d696e6563726166743a77617865645f6578706f7365645f6375745f636f70706572a0fe000e6d696e6563726166743a6c6176610b00001b6d696e6563726166743a6974656d2e6372696d736f6e5f646f6f720cff00216d696e6563726166743a6d616e67726f76655f70726573737572655f706c61746516fe000e6d696e6563726166743a7261696c420000196d696e6563726166743a626c61636b73746f6e655f77616c6cebfe00226d696e6563726166743a6d6f7373795f636f62626c6573746f6e655f7374616972734dff00196d696e6563726166743a636f72616c5f66616e5f68616e673377ff00176d696e6563726166743a6465746563746f725f7261696c1c00001f6d696e6563726166743a7265645f676c617a65645f7465727261636f747461ea00001d6d696e6563726166743a6461726b5f6f616b5f66656e63655f67617465ba0000236d696e6563726166743a62616d626f6f5f6d6f736169635f646f75626c655f736c6162f3fd00186d696e6563726166743a7374727563747572655f766f6964d90000176d696e6563726166743a6163616369615f627574746f6e74ff000e6d696e6563726166743a736e6f77500000206d696e6563726166743a6d616e67726f76655f7374616e64696e675f7369676e12fe00216d696e6563726166743a626c61636b5f676c617a65645f7465727261636f747461eb00001a6d696e6563726166743a6c69745f72656473746f6e655f6f72654a0000206d696e6563726166743a73747269707065645f6d616e67726f76655f776f6f640efe00146d696e6563726166743a626f6e655f626c6f636bd80000156d696e6563726166743a6c617069735f626c6f636b160000126d696e6563726166743a636f616c5f6f7265100000166d696e6563726166743a72656473746f6e655f6f72654900001c6d696e6563726166743a6e65746865725f627269636b5f66656e6365710000186d696e6563726166743a6372696d736f6e5f687970686165d5fe00246d696e6563726166743a706f6c69736865645f626c61636b73746f6e655f737461697273dcfe001e6d696e6563726166743a736d616c6c5f647269706c6561665f626c6f636bb0fe00266d696e6563726166743a77617865645f7765617468657265645f636f707065725f6772617465fafc00106d696e6563726166743a626173616c7416ff00156d696e6563726166743a656e6465725f63686573748200001c6d696e6563726166743a7761727065645f646f75626c655f736c6162f5fe001a6d696e6563726166743a6a756e676c655f77616c6c5f7369676e43ff00166d696e6563726166743a7363756c6b5f73656e736f72cdfe00186d696e6563726166743a64696f726974655f73746169727356ff001d6d696e6563726166743a73747269707065645f6368657272795f6c6f67e9fd00186d696e6563726166743a6372696d736f6e5f627574746f6efcfe00176d696e6563726166743a6163616369615f706c616e6b731afd00196d696e6563726166743a686f6e6579636f6d625f626c6f636b23ff00146d696e6563726166743a626c61636b73746f6e65effe00176d696e6563726166743a63686f7275735f666c6f776572c80000186d696e6563726166743a636f72616c5f66616e5f68616e6779ff001f6d696e6563726166743a637261636b65645f6e65746865725f627269636b73d1fe001a6d696e6563726166743a706f77657265645f72657065617465725e0000196d696e6563726166743a64656570736c6174655f74696c65737dfe00166d696e6563726166743a7265645f6d757368726f6f6d2800001b6d696e6563726166743a67696c6465645f626c61636b73746f6e65e7fe00236d696e6563726166743a6578706f7365645f6375745f636f707065725f7374616972739dfe00196d696e6563726166743a6d616e67726f76655f73746169727318fe00116d696e6563726166743a756e6b6e6f776ecffe001c6d696e6563726166743a79656c6c6f775f63616e646c655f63616b654efe00116d696e6563726166743a64726f707065727d0000176d696e6563726166743a6a756e676c655f737461697273880000156d696e6563726166743a70696e6b5f706574616c73dbfd001c6d696e6563726166743a696e6665737465645f64656570736c6174653afe00146d696e6563726166743a736f756c5f746f726368f4fe00106d696e6563726166743a706f647a6f6cf300001f6d696e6563726166743a64656570736c6174655f74696c655f7374616972737bfe001c6d696e6563726166743a6372696d736f6e5f66656e63655f67617465fefe00126d696e6563726166743a6465616462757368200000146d696e6563726166743a6974656d2e77686561743b00001a6d696e6563726166743a6974656d2e7370727563655f646f6f72c10000116d696e6563726166743a626172726965725fff00156d696e6563726166743a66726f737465645f696365cf00001b6d696e6563726166743a737573706963696f75735f67726176656cc3fd00156d696e6563726166743a627269636b5f626c6f636b2d0000296d696e6563726166743a77617865645f7765617468657265645f636f707065725f74726170646f6f72e2fc001c6d696e6563726166743a73747269707065645f62697263685f6c6f67faff00146d696e6563726166743a636176655f76696e6573befe00146d696e6563726166743a6d656c6f6e5f7374656d690000186d696e6563726166743a6372696d736f6e5f706c616e6b730eff00246d696e6563726166743a77617865645f7765617468657265645f6375745f636f707065729ffe00286d696e6563726166743a77617865645f6f786964697a65645f636f707065725f74726170646f6f72e1fc00166d696e6563726166743a627269636b5f7374616972736c0000136d696e6563726166743a77616c6c5f7369676e440000166d696e6563726166743a626f726465725f626c6f636bd40000156d696e6563726166743a7368726f6f6d6c696768741aff00196d696e6563726166743a6461726b5f6f616b5f737461697273a40000146d696e6563726166743a676c6173735f70616e656600001c6d696e6563726166743a63686973656c65645f64656570736c61746575fe00196d696e6563726166743a62616d626f6f5f74726170646f6f72f8fd00166d696e6563726166743a62697263685f706c616e6b731cfd00226d696e6563726166743a6f786964697a65645f63686973656c65645f636f7070657205fd001c6d696e6563726166743a6d616e67726f76655f77616c6c5f7369676e11fe00146d696e6563726166743a6974656d2e736b756c6c900000206d696e6563726166743a6c696768745f677261795f63616e646c655f63616b654afe00176d696e6563726166743a6368657272795f6c6561766573dcfd001b6d696e6563726166743a6461726b6f616b5f77616c6c5f7369676e3fff00206d696e6563726166743a6379616e5f676c617a65645f7465727261636f747461e500001e6d696e6563726166743a63686973656c65645f747566665f627269636b7309fd00226d696e6563726166743a637261636b65645f64656570736c6174655f627269636b7366fe00166d696e6563726166743a666c6f77696e675f6c6176610a00001b6d696e6563726166743a6a756e676c655f66656e63655f67617465b900001e6d696e6563726166743a6578706f7365645f636f707065725f6772617465fffc00146d696e6563726166743a636f616c5f";
        String str10 = "626c6f636bad0000266d696e6563726166743a77617865645f646f75626c655f6375745f636f707065725f736c61628cfe00136d696e6563726166743a6974656d2e6b656c7076ff00166d696e6563726166743a63686f7275735f706c616e74f000000f6d696e6563726166743a7761746572090000176d696e6563726166743a6368656d6963616c5f68656174c000001a6d696e6563726166743a6d75645f627269636b5f73746169727320fe001c6d696e6563726166743a756e706f77657265645f72657065617465725d0000216d696e6563726166743a77686974655f676c617a65645f7465727261636f747461dc0000196d696e6563726166743a6163616369615f74726170646f6f726fff00156d696e6563726166743a676c6f775f6c696368656e65fe00156d696e6563726166743a77616c6c5f62616e6e6572b10000166d696e6563726166743a736f756c5f6c616e7465726ef3fe00126d696e6563726166743a6265655f6e65737426ff00176d696e6563726166743a627562626c655f636f6c756d6e60ff00216d696e6563726166743a63616c696272617465645f7363756c6b5f73656e736f72bcfd00196d696e6563726166743a636f707065725f74726170646f6f72e8fc001d6d696e6563726166743a73747269707065645f6163616369615f6c6f67f8ff00276d696e6563726166743a636f62626c65645f64656570736c6174655f646f75626c655f736c616274fe00166d696e6563726166743a7761727065645f66656e6365fffe001e6d696e6563726166743a6368657272795f7374616e64696e675f7369676ee2fd001c6d696e6563726166743a646f75626c655f776f6f64656e5f736c61629d0000146d696e6563726166743a6974656d2e636861696ee2fe00276d696e6563726166743a77617865645f6578706f7365645f636f707065725f74726170646f6f72e3fc00226d696e6563726166743a6d6f7373795f73746f6e655f627269636b5f73746169727351ff00166d696e6563726166743a77617865645f636f70706572a8fe00116d696e6563726166743a656e645f726f64d00000166d696e6563726166743a6372696d736f6e5f7374656d1fff00206d696e6563726166743a747566665f627269636b5f646f75626c655f736c61620cfd00176d696e6563726166743a7761727065645f687970686165d6fe00116d696e6563726166743a636172726f74738d0000156d696e6563726166743a747566665f73746169727316fd00166d696e6563726166743a6269675f647269706c656166bdfe001a6d696e6563726166743a73776565745f62657272795f6275736831ff00146d696e6563726166743a6974656d2e72656564735300001c6d696e6563726166743a64656570736c6174655f676f6c645f6f72656efe00116d696e6563726166743a6265656869766525ff001a6d696e6563726166743a6974656d2e6a756e676c655f646f6f72c300000f6d696e6563726166743a676c617373140000156d696e6563726166743a7769746865725f726f736528ff001c6d696e6563726166743a6578706f7365645f6375745f636f70706572a4fe002b6d696e6563726166743a77617865645f7765617468657265645f6375745f636f707065725f73746169727398fe001e6d696e6563726166743a62616d626f6f5f6d6f736169635f737461697273f5fd002c6d696e6563726166743a706f6c69736865645f626c61636b73746f6e655f70726573737572655f706c617465d9fe001e6d696e6563726166743a6163616369615f7374616e64696e675f7369676e42ff001b6d696e6563726166743a747566665f627269636b5f7374616972730bfd001d6d696e6563726166743a6578706f7365645f636f707065725f62756c62f7fc00186d696e6563726166743a6372696d736f6e5f73746169727302ff001d6d696e6563726166743a73747269707065645f7370727563655f6c6f67fbff00246d696e6563726166743a77617865645f6f786964697a65645f636f707065725f62756c62f1fc00166d696e6563726166743a70756d706b696e5f7374656d6800001f6d696e6563726166743a64656570736c6174655f656d6572616c645f6f726569fe00176d696e6563726166743a71756172747a5f627269636b73d0fe001e6d696e6563726166743a756e706f77657265645f636f6d70617261746f729500001a6d696e6563726166743a656e645f627269636b5f7374616972734eff001d6d696e6563726166743a6974656d2e6e65746865725f7370726f75747312ff001b6d696e6563726166743a76657264616e745f66726f676c696768742afe00156d696e6563726166743a7761727065645f7374656d1eff00216d696e6563726166743a73747269707065645f6372696d736f6e5f687970686165d4fe000f6d696e6563726166743a636f636f617f00000f6d696e6563726166743a6c65766572450000236d696e6563726166743a7765617468657265645f636f707065725f74726170646f6f72e6fc00216d696e6563726166743a6578706f7365645f63686973656c65645f636f7070657207fd00186d696e6563726166743a6d616e67726f76655f66656e636515fe001f6d696e6563726166743a6f786964697a65645f636f707065725f6772617465fdfc001b6d696e6563726166743a636f62626c65645f64656570736c61746585fe000d6d696e6563726166743a6963654f0000126d696e6563726166743a6974656d2e6265641a00000d6d696e6563726166743a7765621e00001a6d696e6563726166743a656e6368616e74696e675f7461626c65740000226d696e6563726166743a706f6c69736865645f626c61636b73746f6e655f77616c6cd7fe002e6d696e6563726166743a77617865645f6578706f7365645f646f75626c655f6375745f636f707065725f736c61628bfe00106d696e6563726166743a617a616c6561affe00146d696e6563726166743a6d75645f627269636b7325fe00196d696e6563726166743a62697263685f77616c6c5f7369676e45ff001a6d696e6563726166743a62616d626f6f5f77616c6c5f7369676ef9fd00186d696e6563726166743a62616d626f6f5f7361706c696e675cff00196d696e6563726166743a7374616e64696e675f62616e6e6572b000001a6d696e6563726166743a62756464696e675f616d657468797374b8fe00156d696e6563726166743a736e69666665725f656767acfd00226d696e6563726166743a707572706c655f676c617a65645f7465727261636f747461db00001e6d696e6563726166743a7765617468657265645f6375745f636f70706572a3fe00116d696e6563726166743a626564726f636b0700001a6d696e6563726166743a64726965645f6b656c705f626c6f636b75ff001b6d696e6563726166743a626c61636b73746f6e655f737461697273ecfe00126d696e6563726166743a626c75655f696365f5ff002a6d696e6563726166743a7765617468657265645f646f75626c655f6375745f636f707065725f736c61628efe00146d696e6563726166743a6e65746865727261636b570000196d696e6563726166743a6d616e67726f76655f627574746f6e19fe000f6d696e6563726166743a616c6c6f77d20000196d696e6563726166743a6974656d2e62697263685f646f6f72c20000156d696e6563726166743a6368657272795f776f6f64defd001e6d696e6563726166743a62616d626f6f5f7374616e64696e675f7369676efafd00146d696e6563726166743a6368657272795f6c6f67e8fd001b6d696e6563726166743a707269736d6172696e655f737461697273feff001e6d696e6563726166743a706f6c69736865645f747566665f73746169727311fd00146d696e6563726166743a647261676f6e5f6567677a0000166d696e6563726166743a6e65746865725f627269636b7000001c6d696e6563726166743a64656570736c6174655f69726f6e5f6f72656ffe00156d696e6563726166743a6974656d2e63616d657261f200001a6d696e6563726166743a77617865645f6375745f636f70706572a1fe00176d696e6563726166743a73706f72655f626c6f73736f6dbffe001f6d696e6563726166743a6372696d736f6e5f7374616e64696e675f7369676e06ff001f6d696e6563726166743a6461726b6f616b5f7374616e64696e675f7369676e40ff00156d696e6563726166743a656d6572616c645f6f72658100001e6d696e6563726166743a73747269707065645f7761727065645f7374656d0fff001b6d696e6563726166743a706f696e7465645f6472697073746f6e65ccfe00176d696e6563726166743a6e657468657272656163746f72f70000136d696e6563726166743a747269705f77697265840000176d696e6563726166743a6974656d2e6361756c64726f6e760000266d696e6563726166743a636176655f76696e65735f686561645f776974685f6265727269657388fe001b6d696e6563726166743a6461726b5f6f616b5f74726170646f6f726dff001c6d696e6563726166743a6974656d2e62726577696e675f7374616e647500001f6d696e6563726166743a7765617468657265645f636f707065725f62756c62f6fc00146d696e6563726166743a656e645f706f7274616c7700001b6d696e6563726166743a6163616369615f66656e63655f67617465bb0000146d696e6563726166743a6c69745f736d6f6b657239ff00136d696e6563726166743a6c617069735f6f7265150000196d696e6563726166743a7265645f63616e646c655f63616b6544fe00216d696e6563726166743a726570656174696e675f636f6d6d616e645f626c6f636bbc00002f6d696e6563726166743a77617865645f6f786964697a65645f646f75626c655f6375745f636f707065725f736c61623efe00176d696e6563726166743a6368657272795f706c616e6b73e7fd001c6d696e6563726166743a706f6c69736865645f64656570736c61746581fe001a6d696e6563726166743a747566665f646f75626c655f736c616217fd00176d696e6563726166743a7370727563655f706c616e6b731dfd00116d696e6563726166743a6675726e6163653d00001a6d696e6563726166743a616d6574687973745f636c7573746572b7fe001f6d696e6563726166743a77617865645f63686973656c65645f636f7070657204fd00176d696e6563726166743a6372696d736f6e5f66656e636500ff00176d696e6563726166743a63686973656c65645f747566660ffd00136d696e6563726166743a64697370656e7365721700001c6d696e6563726166743a6974656d2e736f756c5f63616d7066697265defe001a6d696e6563726166743a6c696d655f63616e646c655f63616b654dfe00146d696e6563726166743a67726173735f70617468c600001a6d696e6563726166743a7765617468657265645f636f70706572aafe001b6d696e6563726166743a6368657272795f66656e63655f67617465ebfd00206d696e6563726166743a677261795f676c617a65645f7465727261636f747461e30000176d696e6563726166743a62616d626f6f5f6d6f7361696303fe00186d696e6563726166743a7261775f69726f6e5f626c6f636b3dfe00146d696e6563726166743a69726f6e5f626c6f636b2a00001f6d696e6563726166743a73747269707065645f6d616e67726f76655f6c6f671bfe00106d696e6563726166743a67726176656c0d00001b3c72616b6e65743e383161302d313334312d326462342d303333310107312e32302e35300a0000ebdcf7ef48d95b4a000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002430303030303030302d303030302d303030302d303030302d3030303030303030303030300101";

        StringBuffer sb = new StringBuffer();
        String pack = sb.append(str1).append(str2).append(str3).append(str4).append(str5).append(str6).append(str7).append(str8).append(str9).append(str10).toString();

        BedrockCodecHelper helper = session.getUpstream().getCodecHelper();
        byte[] a = str2bytes(pack);

        ByteBuf buffer = Unpooled.buffer();
        buffer.writeBytes(a);
//
//        StartGameSerializer_v504.INSTANCE.deserialize(buffer, helper, packet);
        System.out.println(buffer.readByte());
        int x = VarInts.readUnsignedInt(buffer);
        System.out.println(x);
        BedrockPacketDefinition<? extends BedrockPacket> definition = Bedrock_v630.CODEC.getPacketDefinition(x);

        BedrockPacket packet = definition.getFactory().get();
        BedrockPacketSerializer<BedrockPacket> serializer = (BedrockPacketSerializer) definition.getSerializer();

        serializer.deserialize(buffer, helper, packet);
        System.out.println("packet.getPacketType() -> " + packet.getPacketType());
        System.out.println(packet);


        if (packet.getPacketType().equals(BedrockPacketType.START_GAME)) {
            System.out.println("######1");
            StartGamePacket startGamePacket = (StartGamePacket) packet;
            for (ItemDefinition itemDefinition : startGamePacket.getItemDefinitions()) {
                System.out.println(itemDefinition);
            }
            for (BlockPropertyData blockProperty : startGamePacket.getBlockProperties()) {
                System.out.println(blockProperty);
            }
        }
    }

    protected NetworkPermissions readNetworkPermissions(ByteBuf buffer, BedrockCodecHelper helper) {
        boolean serverAuthSound = buffer.readBoolean();
        return new NetworkPermissions(serverAuthSound);
    }

    protected void readSyncedPlayerMovementSettings(ByteBuf buffer, StartGamePacket packet) {
        packet.setAuthoritativeMovementMode(MOVEMENT_MODES[VarInts.readInt(buffer)]);
        packet.setRewindHistorySize(VarInts.readInt(buffer));
        packet.setServerAuthoritativeBlockBreaking(buffer.readBoolean());
    }
    protected static final AuthoritativeMovementMode[] MOVEMENT_MODES = AuthoritativeMovementMode.values();

    protected static final PlayerPermission[] PLAYER_PERMISSIONS = PlayerPermission.values();
    protected long readSeed(ByteBuf buf) {
        return buf.readLongLE();
    }
    protected void readLevelSettings(ByteBuf buffer, BedrockCodecHelper helper, StartGamePacket packet) {
        packet.setSeed(readSeed(buffer));
        packet.setSpawnBiomeType(SpawnBiomeType.byId(buffer.readShortLE()));
        packet.setCustomBiomeName(helper.readString(buffer));
        packet.setDimensionId(VarInts.readInt(buffer));
        packet.setGeneratorId(VarInts.readInt(buffer));
        packet.setLevelGameType(GameType.from(VarInts.readInt(buffer)));
        packet.setDifficulty(VarInts.readInt(buffer));
        packet.setDefaultSpawn(helper.readBlockPosition(buffer));
        packet.setAchievementsDisabled(buffer.readBoolean());
        packet.setWorldEditor(buffer.readBoolean());
        packet.setCreatedInEditor(buffer.readBoolean());
        packet.setExportedFromEditor(buffer.readBoolean());
        packet.setDayCycleStopTime(VarInts.readInt(buffer));
        packet.setEduEditionOffers(VarInts.readInt(buffer));
        packet.setEduFeaturesEnabled(buffer.readBoolean());
        packet.setEducationProductionId(helper.readString(buffer));
        packet.setRainLevel(buffer.readFloatLE());
        packet.setLightningLevel(buffer.readFloatLE());
        packet.setPlatformLockedContentConfirmed(buffer.readBoolean());
        packet.setMultiplayerGame(buffer.readBoolean());
        packet.setBroadcastingToLan(buffer.readBoolean());
        packet.setXblBroadcastMode(GamePublishSetting.byId(VarInts.readInt(buffer)));
        packet.setPlatformBroadcastMode(GamePublishSetting.byId(VarInts.readInt(buffer)));
        packet.setCommandsEnabled(buffer.readBoolean());
        packet.setTexturePacksRequired(buffer.readBoolean());
        helper.readArray(buffer, packet.getGamerules(), helper::readGameRule);
        helper.readExperiments(buffer, packet.getExperiments());
        packet.setExperimentsPreviouslyToggled(buffer.readBoolean());
        packet.setBonusChestEnabled(buffer.readBoolean());
        packet.setStartingWithMap(buffer.readBoolean());
        packet.setDefaultPlayerPermission(PLAYER_PERMISSIONS[VarInts.readInt(buffer)]);
        packet.setServerChunkTickRange(buffer.readIntLE());
        packet.setBehaviorPackLocked(buffer.readBoolean());
        packet.setResourcePackLocked(buffer.readBoolean());
        packet.setFromLockedWorldTemplate(buffer.readBoolean());
        packet.setUsingMsaGamertagsOnly(buffer.readBoolean());
        packet.setFromWorldTemplate(buffer.readBoolean());
        packet.setWorldTemplateOptionLocked(buffer.readBoolean());
        packet.setOnlySpawningV1Villagers(buffer.readBoolean());
        packet.setDisablingPersonas(buffer.readBoolean());
        packet.setDisablingCustomSkins(buffer.readBoolean());
        packet.setEmoteChatMuted(buffer.readBoolean());
        packet.setVanillaVersion(helper.readString(buffer));
        packet.setLimitedWorldWidth(buffer.readIntLE());
        packet.setLimitedWorldHeight(buffer.readIntLE());
        packet.setNetherType(buffer.readBoolean());
        packet.setEduSharedUriResource(new EduSharedUriResource(helper.readString(buffer), helper.readString(buffer)));
        packet.setForceExperimentalGameplay(helper.readOptional(buffer, OptionalBoolean.empty(), buf -> OptionalBoolean.of(buf.readBoolean())));
        packet.setChatRestrictionLevel(ChatRestrictionLevel.values()[buffer.readByte()]);
        packet.setDisablingPlayerInteractions(buffer.readBoolean());
    }

    public static byte[] str2bytes(String src) {
        if (src == null || src.length() == 0 || src.length() % 2 != 0) {
            return null;
        }
        int nSrcLen = src.length();
        byte byteArrayResult[] = new byte[nSrcLen / 2];
        StringBuffer strBufTemp = new StringBuffer(src);
        String strTemp;
        int i = 0;
        while (i < strBufTemp.length() - 1) {
            strTemp = src.substring(i, i + 2);
            byteArrayResult[i / 2] = (byte) Integer.parseInt(strTemp, 16);
            i += 2;
        }
        return byteArrayResult;
    }

    /**
     * @return the next Bedrock item network ID to use for a new item
     */
    public int getNextItemNetId() {
        return itemNetId.getAndIncrement();
    }

    public void confirmTeleport(Vector3d position) {
        if (unconfirmedTeleport == null) {
            return;
        }

        if (unconfirmedTeleport.canConfirm(position)) {
            unconfirmedTeleport = null;
            return;
        }

        // Resend the teleport every few packets until Bedrock responds
        unconfirmedTeleport.incrementUnconfirmedFor();
        if (unconfirmedTeleport.shouldResend()) {
            unconfirmedTeleport.resetUnconfirmedFor();
            geyser.getLogger().debug("Resending teleport " + unconfirmedTeleport.getTeleportConfirmId());
            getPlayerEntity().moveAbsolute(Vector3f.from(unconfirmedTeleport.getX(), unconfirmedTeleport.getY(), unconfirmedTeleport.getZ()),
                    unconfirmedTeleport.getYaw(), unconfirmedTeleport.getPitch(), playerEntity.isOnGround(), true);
        }
    }

    /**
     * Queue a packet to be sent to player.
     *
     * @param packet the bedrock packet from the NukkitX protocol lib
     */
    public void sendUpstreamPacket(BedrockPacket packet) {
        upstream.sendPacket(packet);
    }

    /**
     * Send a packet immediately to the player.
     *
     * @param packet the bedrock packet from the NukkitX protocol lib
     */
    public void sendUpstreamPacketImmediately(BedrockPacket packet) {
        upstream.sendPacketImmediately(packet);
    }

    /**
     * Send a packet to the remote server if in the game state.
     *
     * @param packet the java edition packet from MCProtocolLib
     */
    public void sendDownstreamGamePacket(Packet packet) {
        sendDownstreamPacket(packet, ProtocolState.GAME);
    }

    /**
     * Send a packet to the remote server if in the login state.
     *
     * @param packet the java edition packet from MCProtocolLib
     */
    public void sendDownstreamLoginPacket(Packet packet) {
        sendDownstreamPacket(packet, ProtocolState.LOGIN);
    }

    /**
     * Send a packet to the remote server if in the specified state.
     *
     * @param packet the java edition packet from MCProtocolLib
     * @param intendedState the state the client should be in
     */
    public void sendDownstreamPacket(Packet packet, ProtocolState intendedState) {
        // protocol can be null when we're not yet logged in (online auth)
        if (protocol == null) {
            if (geyser.getConfig().isDebugMode()) {
                geyser.getLogger().debug("Tried to send downstream packet with no downstream session!");
                Thread.dumpStack();
            }
            return;
        }

        if (protocol.getState() != intendedState) {
            geyser.getLogger().debug("Tried to send " + packet.getClass().getSimpleName() + " packet while not in " + intendedState.name() + " state");
            return;
        }

        sendDownstreamPacket(packet);
    }

    /**
     * Send a packet to the remote server.
     *
     * @param packet the java edition packet from MCProtocolLib
     */
    public void sendDownstreamPacket(Packet packet) {
        if (!closed && this.downstream != null) {
            Channel channel = this.downstream.getSession().getChannel();
            if (channel == null) {
                // Channel is only null before the connection has initialized
                geyser.getLogger().warning("Tried to send a packet to the Java server too early!");
                if (geyser.getConfig().isDebugMode()) {
                    Thread.dumpStack();
                }
                return;
            }

            EventLoop eventLoop = channel.eventLoop();
            if (eventLoop.inEventLoop()) {
                sendDownstreamPacket0(packet);
            } else {
                eventLoop.execute(() -> sendDownstreamPacket0(packet));
            }
        }
    }

    private void sendDownstreamPacket0(Packet packet) {
        ProtocolState state = protocol.getState();
        if (state == ProtocolState.GAME || state == ProtocolState.CONFIGURATION || packet.getClass() == ServerboundCustomQueryAnswerPacket.class) {
            downstream.sendPacket(packet);
        } else {
            geyser.getLogger().debug("Tried to send downstream packet " + packet.getClass().getSimpleName() + " before connected to the server");
        }
    }

    /**
     * Update the cached value for the reduced debug info gamerule.
     * If enabled, also hides the player's coordinates.
     *
     * @param value The new value for reducedDebugInfo
     */
    public void setReducedDebugInfo(boolean value) {
        reducedDebugInfo = value;
        // Set the showCoordinates data. This is done because updateShowCoordinates() uses this gamerule as a variable.
        preferencesCache.updateShowCoordinates();
    }

    /**
     * Changes the daylight cycle gamerule on the client
     * This is used in the login screen along-side normal usage
     *
     * @param doCycle If the cycle should continue
     */
    public void setDaylightCycle(boolean doCycle) {
        sendGameRule("dodaylightcycle", doCycle);
        // Save the value so we don't have to constantly send a daylight cycle gamerule update
        this.daylightCycle = doCycle;
    }

    /**
     * Send a gamerule value to the client
     *
     * @param gameRule The gamerule to send
     * @param value    The value of the gamerule
     */
    public void sendGameRule(String gameRule, Object value) {
        GameRulesChangedPacket gameRulesChangedPacket = new GameRulesChangedPacket();
        gameRulesChangedPacket.getGameRules().add(new GameRuleData<>(gameRule, value));
        upstream.sendPacket(gameRulesChangedPacket);
    }

    /**
     * Checks if the given session's player has a permission
     *
     * @param permission The permission node to check
     * @return true if the player has the requested permission, false if not
     */
    @Override
    public boolean hasPermission(String permission) {
        return geyser.getWorldManager().hasPermission(this, permission);
    }

    private static final Ability[] USED_ABILITIES = Ability.values();

    /**
     * Send an AdventureSettingsPacket to the client with the latest flags
     */
    public void sendAdventureSettings() {
        long bedrockId = playerEntity.getGeyserId();
        // Set command permission if OP permission level is high enough
        // This allows mobile players access to a GUI for doing commands. The commands there do not change above OPERATOR
        // and all commands there are accessible with OP permission level 2
        CommandPermission commandPermission = opPermissionLevel >= 2 ? CommandPermission.GAME_DIRECTORS : CommandPermission.ANY;
        // Required to make command blocks destroyable
        PlayerPermission playerPermission = opPermissionLevel >= 2 ? PlayerPermission.OPERATOR : PlayerPermission.MEMBER;

        // Update the noClip and worldImmutable values based on the current gamemode
        boolean spectator = gameMode == GameMode.SPECTATOR;
        boolean worldImmutable = gameMode == GameMode.ADVENTURE || spectator;

        UpdateAdventureSettingsPacket adventureSettingsPacket = new UpdateAdventureSettingsPacket();
        adventureSettingsPacket.setNoMvP(false);
        adventureSettingsPacket.setNoPvM(false);
        adventureSettingsPacket.setImmutableWorld(worldImmutable);
        adventureSettingsPacket.setShowNameTags(false);
        adventureSettingsPacket.setAutoJump(true);
        sendUpstreamPacket(adventureSettingsPacket);

        UpdateAbilitiesPacket updateAbilitiesPacket = new UpdateAbilitiesPacket();
        updateAbilitiesPacket.setUniqueEntityId(bedrockId);
        updateAbilitiesPacket.setCommandPermission(commandPermission);
        updateAbilitiesPacket.setPlayerPermission(playerPermission);

        AbilityLayer abilityLayer = new AbilityLayer();
        Set<Ability> abilities = abilityLayer.getAbilityValues();
        if (canFly) {
            abilities.add(Ability.MAY_FLY);
        }

        // Default stuff we have to fill in
        abilities.add(Ability.BUILD);
        abilities.add(Ability.MINE);
        // Needed so you can drop items
        abilities.add(Ability.DOORS_AND_SWITCHES);
        // Required for lecterns to work (likely started around 1.19.10; confirmed on 1.19.70)
        abilities.add(Ability.OPEN_CONTAINERS);
        if (gameMode == GameMode.CREATIVE) {
            // Needed so the client doesn't attempt to take away items
            abilities.add(Ability.INSTABUILD);
        }

        if (commandPermission == CommandPermission.GAME_DIRECTORS) {
            // Fixes a bug? since 1.19.11 where the player can change their gamemode in Bedrock settings and
            // a packet is not sent to the server.
            // https://github.com/GeyserMC/Geyser/issues/3191
            abilities.add(Ability.OPERATOR_COMMANDS);
        }

        if (flying || spectator) {
            if (spectator && !flying) {
                // We're "flying locked" in this gamemode
                flying = true;
                ServerboundPlayerAbilitiesPacket abilitiesPacket = new ServerboundPlayerAbilitiesPacket(true);
                sendDownstreamGamePacket(abilitiesPacket);
            }
            abilities.add(Ability.FLYING);
        }

        if (spectator) {
            AbilityLayer spectatorLayer = new AbilityLayer();
            spectatorLayer.setLayerType(AbilityLayer.Type.SPECTATOR);
            // Setting all abilitySet causes the zoom issue... BDS only sends these, so ig we will too
            Set<Ability> abilitySet = spectatorLayer.getAbilitiesSet();
            abilitySet.add(Ability.BUILD);
            abilitySet.add(Ability.MINE);
            abilitySet.add(Ability.DOORS_AND_SWITCHES);
            abilitySet.add(Ability.OPEN_CONTAINERS);
            abilitySet.add(Ability.ATTACK_PLAYERS);
            abilitySet.add(Ability.ATTACK_MOBS);
            abilitySet.add(Ability.INVULNERABLE);
            abilitySet.add(Ability.FLYING);
            abilitySet.add(Ability.MAY_FLY);
            abilitySet.add(Ability.INSTABUILD);
            abilitySet.add(Ability.NO_CLIP);

            Set<Ability> abilityValues = spectatorLayer.getAbilityValues();
            abilityValues.add(Ability.INVULNERABLE);
            abilityValues.add(Ability.FLYING);
            abilityValues.add(Ability.NO_CLIP);

            updateAbilitiesPacket.getAbilityLayers().add(spectatorLayer);
        }

        abilityLayer.setLayerType(AbilityLayer.Type.BASE);
        abilityLayer.setFlySpeed(flySpeed);
        // https://github.com/GeyserMC/Geyser/issues/3139 as of 1.19.10
        abilityLayer.setWalkSpeed(walkSpeed == 0f ? 0.01f : walkSpeed);
        Collections.addAll(abilityLayer.getAbilitiesSet(), USED_ABILITIES);

        updateAbilitiesPacket.getAbilityLayers().add(abilityLayer);
        sendUpstreamPacket(updateAbilitiesPacket);
    }

    private int getRenderDistance() {
        if (clientRenderDistance != -1) {
            // The client has sent a render distance
            return clientRenderDistance;
        } else if (serverRenderDistance != -1) {
            // only known once ClientboundLoginPacket is received
            return serverRenderDistance;
        }
        return 2; // unfortunate default until we got more info
    }

    // We need to send our skin parts to the server otherwise java sees us with no hat, jacket etc
    private static final List<SkinPart> SKIN_PARTS = Arrays.asList(SkinPart.values());

    /**
     * Send a packet to the server to indicate client render distance, locale, skin parts, and hand preference.
     */
    public void sendJavaClientSettings() {
        ServerboundClientInformationPacket clientSettingsPacket = new ServerboundClientInformationPacket(locale(),
                getRenderDistance(), ChatVisibility.FULL, true, SKIN_PARTS,
                HandPreference.RIGHT_HAND, false, true);
        sendDownstreamPacket(clientSettingsPacket);
    }

    /**
     * Used for updating statistic values since we only get changes from the server
     *
     * @param statistics Updated statistics values
     */
    public void updateStatistics(@NonNull Object2IntMap<Statistic> statistics) {
        if (this.statistics.isEmpty()) {
            // Initialize custom statistics to 0, so that they appear in the form
            for (CustomStatistic customStatistic : CustomStatistic.values()) {
                this.statistics.put(customStatistic, 0);
            }
        }
        this.statistics.putAll(statistics);
    }

    public void refreshEmotes(List<UUID> emotes) {
        this.emotes.addAll(emotes);
        for (GeyserSession player : geyser.getSessionManager().getSessions().values()) {
            List<UUID> pieces = new ArrayList<>();
            for (UUID piece : emotes) {
                if (!player.getEmotes().contains(piece)) {
                    pieces.add(piece);
                }
                player.getEmotes().add(piece);
            }
            EmoteListPacket emoteList = new EmoteListPacket();
            emoteList.setRuntimeEntityId(player.getPlayerEntity().getGeyserId());
            emoteList.getPieceIds().addAll(pieces);
            player.sendUpstreamPacket(emoteList);
        }
    }

    public boolean canUseCommandBlocks() {
        return instabuild && opPermissionLevel >= 2;
    }

    public void playSoundEvent(SoundEvent sound, Vector3f position) {
        LevelSoundEvent2Packet packet = new LevelSoundEvent2Packet();
        packet.setPosition(position);
        packet.setSound(sound);
        packet.setIdentifier(":");
        packet.setExtraData(-1);
        sendUpstreamPacket(packet);
    }

    public float getEyeHeight() {
        return switch (pose) {
            case SNEAKING -> 1.27f;
            case SWIMMING,
                    FALL_FLYING, // Elytra
                    SPIN_ATTACK -> 0.4f; // Trident spin attack
            case SLEEPING -> 0.2f;
            default -> EntityDefinitions.PLAYER.offset();
        };
    }

    @Override
    public @NonNull String bedrockUsername() {
        return authData.name();
    }

    @Override
    public @MonotonicNonNull String javaUsername() {
        return playerEntity.getUsername();
    }

    @Override
    public UUID javaUuid() {
        return playerEntity.getUuid();
    }

    @Override
    public @NonNull String xuid() {
        return authData.xuid();
    }

    @Override
    public @NonNull String version() {
        return clientData.getGameVersion();
    }

    @Override
    public @NonNull BedrockPlatform platform() {
        return BedrockPlatform.values()[clientData.getDeviceOs().ordinal()]; //todo
    }

    @Override
    public @NonNull String languageCode() {
        return locale();
    }

    @Override
    public @NonNull UiProfile uiProfile() {
        return UiProfile.values()[clientData.getUiProfile().ordinal()]; //todo
    }

    @Override
    public @NonNull InputMode inputMode() {
        return InputMode.values()[clientData.getCurrentInputMode().ordinal()]; //todo
    }

    @Override
    public boolean isLinked() {
        return false; //todo
    }

    @SuppressWarnings("ConstantConditions") // Need to enforce the parameter annotations
    @Override
    public boolean transfer(@NonNull String address, @IntRange(from = 0, to = 65535) int port) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Server address cannot be null or blank");
        } else if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Server port must be between 0 and 65535, was " + port);
        }
        TransferPacket transferPacket = new TransferPacket();
        transferPacket.setAddress(address);
        transferPacket.setPort(port);
        sendUpstreamPacket(transferPacket);
        return true;
    }

    @Override
    public @NonNull CompletableFuture<@Nullable GeyserEntity> entityByJavaId(@NonNegative int javaId) {
        CompletableFuture<GeyserEntity> future = new CompletableFuture<>();
        ensureInEventLoop(() -> future.complete(this.entityCache.getEntityByJavaId(javaId)));
        return future;
    }

    @Override
    public void showEmote(@NonNull GeyserPlayerEntity emoter, @NonNull String emoteId) {
        Entity entity = (Entity) emoter;
        if (entity.getSession() != this) {
            throw new IllegalStateException("Given entity must be from this session!");
        }

        EmotePacket packet = new EmotePacket();
        packet.setRuntimeEntityId(entity.getGeyserId());
        packet.setXuid("");
        packet.setPlatformId(""); // BDS sends empty
        packet.setEmoteId(emoteId);
        sendUpstreamPacket(packet);
    }

    @Override
    public void shakeCamera(float intensity, float duration, @NonNull CameraShake type) {
        CameraShakePacket packet = new CameraShakePacket();
        packet.setIntensity(intensity);
        packet.setDuration(duration);
        packet.setShakeType(type == CameraShake.POSITIONAL ? CameraShakeType.POSITIONAL : CameraShakeType.ROTATIONAL);
        packet.setShakeAction(CameraShakeAction.ADD);
        sendUpstreamPacket(packet);
    }

    @Override
    public void stopCameraShake() {
        CameraShakePacket packet = new CameraShakePacket();
        // CameraShakeAction.STOP removes all types regardless of the given type, but regardless it can't be null
        packet.setShakeType(CameraShakeType.POSITIONAL);
        packet.setShakeAction(CameraShakeAction.STOP);
        sendUpstreamPacket(packet);
    }

    @Override
    public void sendFog(String... fogNameSpaces) {
        Collections.addAll(this.appliedFog, fogNameSpaces);

        PlayerFogPacket packet = new PlayerFogPacket();
        packet.getFogStack().addAll(this.appliedFog);
        sendUpstreamPacket(packet);
    }

    @Override
    public void removeFog(String... fogNameSpaces) {
        if (fogNameSpaces.length == 0) {
            this.appliedFog.clear();
        } else {
            for (String id : fogNameSpaces) {
                this.appliedFog.remove(id);
            }
        }
        PlayerFogPacket packet = new PlayerFogPacket();
        packet.getFogStack().addAll(this.appliedFog);
        sendUpstreamPacket(packet);
    }

    @Override
    public @NonNull Set<String> fogEffects() {
        // Use a copy so that sendFog/removeFog can be called while iterating the returned set (avoid CME)
        return Set.copyOf(this.appliedFog);
    }

    public void addCommandEnum(String name, String enums) {
        softEnumPacket(name, SoftEnumUpdateType.ADD, enums);
    }

    public void removeCommandEnum(String name, String enums) {
        softEnumPacket(name, SoftEnumUpdateType.REMOVE, enums);
    }

    private void softEnumPacket(String name, SoftEnumUpdateType type, String enums) {
        // There is no need to send command enums if command suggestions are disabled
        if (!this.geyser.getConfig().isCommandSuggestions()) {
            return;
        }
//        UpdateSoftEnumPacket packet = new UpdateSoftEnumPacket();
//        packet.setType(type);
//        packet.setSoftEnum(new CommandEnumData(name, Collections.singletonMap(enums, Collections.emptySet()), true));
//        sendUpstreamPacket(packet);
    }
}
