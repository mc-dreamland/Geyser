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
import com.github.steveice10.mc.auth.exception.request.InvalidCredentialsException;
import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.service.AuthenticationService;
import com.github.steveice10.mc.auth.service.MojangAuthenticationService;
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
import com.github.steveice10.mc.protocol.packet.handshake.serverbound.ClientIntentionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundClientInformationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundPlayerAbilitiesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import com.github.steveice10.mc.protocol.packet.login.serverbound.ServerboundCustomQueryPacket;
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
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.data.*;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission;
import org.cloudburstmc.protocol.bedrock.data.command.SoftEnumUpdateType;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;
import org.cloudburstmc.protocol.common.util.VarInts;
import org.geysermc.api.util.BedrockPlatform;
import org.geysermc.api.util.InputMode;
import org.geysermc.api.util.UiProfile;
import org.geysermc.geyser.api.bedrock.camera.CameraShake;
import org.geysermc.geyser.api.util.PlatformType;
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.util.FormBuilder;
import org.geysermc.floodgate.crypto.FloodgateCipher;
import org.geysermc.floodgate.util.BedrockData;
import org.geysermc.geyser.Constants;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.entity.type.GeyserEntity;
import org.geysermc.geyser.api.entity.type.player.GeyserPlayerEntity;
import org.geysermc.geyser.api.event.bedrock.SessionLoginEvent;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.api.network.RemoteServer;
import org.geysermc.geyser.command.GeyserCommandSource;
import org.geysermc.geyser.configuration.EmoteOffhandWorkaroundOption;
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
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.network.netty.LocalSession;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.type.BlockMappings;
import org.geysermc.geyser.registry.type.ItemMappings;
import org.geysermc.geyser.session.auth.AuthData;
import org.geysermc.geyser.session.auth.BedrockClientData;
import org.geysermc.geyser.session.cache.*;
import org.geysermc.geyser.skin.FloodgateSkinUploader;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.text.MinecraftLocale;
import org.geysermc.geyser.text.TextDecoration;
import org.geysermc.geyser.translator.inventory.InventoryTranslator;
import org.geysermc.geyser.translator.text.MessageTranslator;
import org.geysermc.geyser.util.ChunkUtils;
import org.geysermc.geyser.util.DimensionUtils;
import org.geysermc.geyser.util.LoginEncryptionUtils;
import org.jetbrains.annotations.NotNull;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
    private BedrockClientData clientData;
    /**
     * Used for Floodgate skin uploading
     */
    @Setter
    private List<String> certChainData;

    @NotNull
    @Setter
    private AbstractGeyserboundPacketHandler erosionHandler;

    @Accessors(fluent = true)
    @Setter
    private RemoteServer remoteServer;

    @Deprecated
    @Setter
    private boolean microsoftAccount;

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
    private int serverRenderDistance;

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
     *
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
     * See https://github.com/GeyserMC/Geyser/issues/503 for context.
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
        upstream.sendPacket(gamerulePacket);
//        testBytes(this);
    }

    public void testBytes(GeyserSession session) {
        String str1 = "fe0bfdffffff1f010a0000003f9f0100470000003f0000000000000000e49d397ae9b94369000006706c61696e730004020400ffff010001420000000000000000000000000101000601001e12636f6d6d616e64626c6f636b6f75747075740101010f646f6461796c696768746379636c650101010d646f656e7469747964726f70730101010a646f666972657469636b01010109646f6d6f626c6f6f740101010d646f6d6f62737061776e696e670101010b646f74696c6564726f70730101010e646f776561746865726379636c650101010e64726f776e696e6764616d6167650101010a66616c6c64616d6167650101010a6669726564616d6167650101010d6b656570696e76656e746f72790101010b6d6f626772696566696e67010101037076700101010f73686f77636f6f7264696e61746573010100136e61747572616c726567656e65726174696f6e0101010b746e746578706c6f6465730101011373656e64636f6d6d616e64666565646261636b010101156d6178636f6d6d616e64636861696e6c656e6774680102feff070a646f696e736f6d6e696101010114636f6d6d616e64626c6f636b73656e61626c65640101010f72616e646f6d7469636b737065656401020212646f696d6d6564696174657265737061776e0101001173686f7764656174686d657373616765730101011466756e6374696f6e636f6d6d616e646c696d69740102a09c010b737061776e72616469757301020a0873686f77746167730101010c667265657a6564616d616765010101147265737061776e626c6f636b736578706c6f64650101011073686f77626f7264657265666665637401010100000000000000020400000000000000000000012a1000000010000000000000002463336431613533622d383366642d346465622d393132662d34316138303766653837353015427265616b44697274e5bc80e58f91e6b58be8af952430303030303030302d303030302d303030302d303030302d30303030303030303030303000022800210000000000000090c98e94030113686579706978656c3a627265616b5f646972740a00080a626173655f626c6f636b000a0a636f6d706f6e656e74730a166d696e6563726166743a64657374726f795f74696d65050576616c7565003c1c460000080a6d6963726f5f73697a6500030d6d6f6c616e6756657273696f6e0008106e6574656173655f63617465676f72790000d908146d696e6563726166743a636f6f6b65645f636f640c0100166d696e6563726166743a7075727075725f626c6f636bc90000246d696e6563726166743a77617865645f7765617468657265645f6375745f636f707065729ffe000d6d696e6563726166743a626f772c0100146d696e6563726166743a656e645f627269636b73ce0000196d696e6563726166743a6d757369635f646973635f776172642102001c6d696e6563726166743a656e6465726d616e5f737061776e5f656767bb01000d6d696e6563726166743a61697262ff00156d696e6563726166743a7365615f6c616e7465726ea90000106d696e6563726166743a726162626974200100206d696e6563726166743a637265657065725f62616e6e65725f7061747465726e480200146d696e6563726166743a656c656d656e745f3235dcff00196d696e6563726166743a6d616e67726f76655f6c656176657328fe00286d696e6563726166743a706f6c69736865645f626c61636b73746f6e655f627269636b5f736c6162e4fe00176d696e6563726166743a6d757368726f6f6d5f737465770401001b6d696e6563726166743a726176616765725f737061776e5f656767ee0100196d696e6563726166743a636f6f6b65645f706f726b63686f700701001a6d696e6563726166743a73747269707065645f6f616b5f6c6f67f6ff00146d696e6563726166743a656c656d656e745f3530c3ff000f6d696e6563726166743a6170706c65010100166d696e6563726166743a676f6c64656e5f6170706c650201001c6d696e6563726166743a676f6c64656e5f686f7273655f61726d6f72160200136d696e6563726166743a626f6f6b7368656c662f00001e6d696e6563726166743a736d6f6f74685f71756172747a5f73746169727347ff00106d696e6563726166743a706f7461746f180100156d696e6563726166743a6e65746865725f73746172080200206d696e6563726166743a656e6368616e7465645f676f6c64656e5f6170706c65030100146d696e6563726166743a656c656d656e745f3135e6ff00156d696e6563726166743a6c696768745f626c6f636b29ff001c6d696e6563726166743a6974656d2e6461726b5f6f616b5f646f6f72c50000226d696e6563726166743a79656c6c6f775f676c617a65645f7465727261636f747461e000001c6d696e6563726166743a73746f6e655f627269636b5f7374616972736d0000106d696e6563726166743a706f7274616c5a0000146d696e6563726166743a676f6c645f696e676f74320100186d696e6563726166743a6f616b5f63686573745f626f61747e0200146d696e6563726166743a69726f6e5f696e676f74310100196d696e6563726166743a736c696d655f737061776e5f656767be01000f6d696e6563726166743a73637574653e0200126d696e6563726166743a706f726b63686f70060100106d696e6563726166743a636f6f6b69650f0100136d696e6563726166743a656c656d656e745f37eeff00176d696e6563726166743a6469616d6f6e645f626c6f636b3900000f6d696e6563726166743a62726561640501001a6d696e6563726166743a6d757369635f646973635f63686972701b0200176d696e6563726166743a636f6f6b65645f726162626974210100226d696e6563726166743a707269736d6172696e655f627269636b735f737461697273fcff001e6d696e6563726166743a676c6f775f73717569645f737061776e5f656767f80100176d696e6563726166743a71756172747a5f627269636b73d0fe000d6d696e6563726166743a636f64080100186d696e6563726166743a6974656d2e69726f6e5f646f6f724700001c6d696e6563726166743a70696c6c616765725f737061776e5f656767ec0100166d696e6563726166743a69726f6e5f7069636b6178652901000e6d696e6563726166743a62656566110100196d696e6563726166743a626c617a655f737061776e5f656767c90100106d696e6563726166743a73616c6d6f6e0901001a6d696e6563726166743a64656570736c6174655f627269636b7379fe00156d696e6563726166743a636f636f615f6265616e739d0100176d696e6563726166743a74726f706963616c5f666973680a0100226d696e6563726166743a73696c7665725f676c617a65645f7465727261636f747461e40000156d696e6563726166743a776f6f64656e5f736c61629e00001c6d696e6563726166743a737461696e65645f676c6173735f70616e65a00000136d696e6563726166743a73746f6e655f6178653b01000f6d696e6563726166743a616e76696c910000146d696e6563726166743a707566666572666973680b0100176d696e6563726166743a747261707065645f6368657374920000106d696e6563726166743a6275636b6574690100186d696e6563726166743a616e6369656e745f646562726973f1fe00126d696e6563726166743a737061726b6c65725d0200156d696e6563726166743a7761727065645f646f6f726e0200146d696e6563726166743a656c656d656e745f3631b8ff00176d696e6563726166743a636f6f6b65645f73616c6d6f6e0d0100146d696e6563726166743a64726965645f6b656c700e0100176d696e6563726166743a62656574726f6f745f736f75701e0100166d696e6563726166743a7265645f6d757368726f6f6d280000186d696e6563726166743a776f6f64656e5f7069636b617865360100156d696e6563726166743a6d656c6f6e5f736c696365100100176d696e6563726166743a6974656d2e63616d70666972652fff001d6d696e6563726166743a6d6167656e74615f63616e646c655f63616b6550fe00186d696e6563726166743a6e617574696c75735f7368656c6c3c0200176d696e6563726166743a776f6f64656e5f73686f76656c350100136d696e6563726166743a6861795f626c6f636baa0000136d696e6563726166743a656c656d656e745f31f4ff00156d696e6563726166743a70696e6b5f63616e646c655dfe000e6d696e6563726166743a666972653300001b6d696e6563726166743a73746f6e656375747465725f626c6f636b3bff00156d696e6563726166743a636f6f6b65645f62656566120100186d696e6563726166743a6578706f7365645f636f70706572abfe00146d696e6563726166743a636f6d70617261746f720c0200186d696e6563726166743a7261775f676f6c645f626c6f636b3bfe00106d696e6563726166743a636172726f741701001b6d696e6563726166743a737472696465725f737061776e5f656767f00100176d696e6563726166743a636f6d6d616e645f626c6f636b890000106d696e6563726166743a706f74696f6eab0100116d696e6563726166743a636869636b656e130100146d696e6563726166743a736e6f775f6c617965724e00001b6d696e6563726166743a6461796c696768745f6465746563746f72970000166d696e6563726166743a726f7474656e5f666c657368150100146d696e6563726166743a656c656d656e745f3632b7ff000e6d696e6563726166743a64697274030000196d696e6563726166743a77697463685f737061776e5f656767c50100106d696e6563726166743a736d6f6b65723aff00126d696e6563726166743a63616d70666972654f02001a6d696e6563726166743a6c696e676572696e675f706f74696f6e340200156d696e6563726166743a7261626269745f666f6f74120200166d696e6563726166743a7761727065645f66656e6365fffe00186d696e6563726166743a636f6f6b65645f636869636b656e140100156d696e6563726166743a73746f6e655f73776f7264380100266d696e6563726166743a6c696768745f626c75655f676c617a65645f7465727261636f747461df0000146d696e6563726166743a7370696465725f657965160100196d696e6563726166743a686f7273655f737061776e5f656767cb0100166d696e6563726166743a62616b65645f706f7461746f190100176d696e6563726166743a676f6c64656e5f636172726f741b01001d6d696e6563726166743a64656570736c6174655f74696c655f77616c6c7afe001a6d696e6563726166743a706f69736f6e6f75735f706f7461746f1a0100146d696e6563726166743a656c656d656e745f3133e8ff00176d696e6563726166743a7370727563655f737461697273860000156d696e6563726166743a70756d706b696e5f7069651c0100126d696e6563726166743a6f6273696469616e31000013686579706978656c3a627265616b5f6469727417fd00196d696e6563726166743a6469616d6f6e645f7069636b6178653e0100116d696e6563726166743a6c616e7465726e30ff00146d696e6563726166743a69726f6e5f73776f7264330100126d696e6563726166743a62656574726f6f741d0100146d696e6563726166743a656c656d656e745f3433caff00166d696e6563726166743a736d6f6f74685f73746f6e6549ff001a6d696e6563726166743a6d757369635f646973635f7374726164200200166d696e6563726166743a77686974655f63616e646c6563fe00176d696e6563726166743a73776565745f626572726965731f0100156d696e6563726166743a7261626269745f73746577220100156d696e6563726166743a77686561745f7365656473230100206d696e6563726166743a636f6d6d616e645f626c6f636b5f6d696e65636172743502001b6d696e6563726166743a6974656d2e6372696d736f6e5f646f6f720cff000f6d696e6563726166743a6368657374360000176d696e6563726166743a70756d706b696e5f7365656473240100136d696e6563726166743a656c656d656e745f32f3ff00156d696e6563726166743a6d656c6f6e5f7365656473250100136d696e6563726166743a737061776e5f656767880200126d696e6563726166743a7261775f69726f6efb0100126d696e6563726166743a69726f6e5f6178652a0100156d696e6563726166743a6e65746865725f77617274260100146d696e6563726166743a656c656d656e745f3335d2ff00186d696e6563726166743a62656574726f6f745f7365656473270100156d696e6563726166743a69726f6e5f73686f76656c280100156d696e6563726166743a656c656d656e745f3130348dff00186d696e6563726166743a6772616e6974655f73746169727357ff001a6d696e6563726166743a7a6f676c696e5f737061776e5f656767f40100196d696e6563726166743a666c696e745f616e645f737465656c2b0100166d696e6563726166743a73746f6e655f73686f76656c3901000f6d696e6563726166743a6172726f772d0100156d696e6563726166743a6d656c6f6e5f626c6f636b6700000e6d696e6563726166743a636f616c2e0100216d696e6563726166743a7265616c5f646f75626c655f73746f6e655f736c616232b50000126d696e6563726166743a63686172636f616c2f0100196d696e6563726166743a73747261795f737061776e5f656767cf0100116d696e6563726166743a636172726f74738d0000116d696e6563726166743a6469616d6f6e64300100166d696e6563726166743a776f6f64656e5f73776f7264340100246d696e6563726166743a6f786964697a65645f6375745f636f707065725f7374616972739bfe00196d696e6563726166743a6e65746865726974655f626f6f7473690200196d696e6563726166743a6d757369635f646973635f6d616c6c1d0200196d696e6563726166743a6461726b5f6f616b5f737461697273a40000126d696e6563726166743a6661726d6c616e643c0000146d696e6563726166743a776f6f64656e5f617865370100126d696e6563726166743a7261775f676f6c64fc01001a6d696e6563726166743a6372696d736f6e5f74726170646f6f720aff00216d696e6563726166743a7a6f6d6269655f7069676d616e5f737061776e5f656767c10100176d696e6563726166743a73746f6e655f7069636b6178653a0100106d696e6563726166743a706c616e6b73050000176d696e6563726166743a73616c6d6f6e5f6275636b65746e01001a6d696e6563726166743a636861696e6d61696c5f68656c6d6574530100186d696e6563726166743a6469616d6f6e645f73686f76656c3d0100176d696e6563726166743a6469616d6f6e645f73776f72643c0100186d696e6563726166743a736d697468696e675f7461626c6536ff00156d696e6563726166743a6469616d6f6e645f6178653f0100156d696e6563726166743a64656275675f737469636b5102000f6d696e6563726166743a737469636b4001000e6d696e6563726166743a626f776c410100176d696e6563726166743a666c6f77696e675f7761746572080000166d696e6563726166743a676f6c64656e5f73776f7264420100156d696e6563726166743a686f6e65795f626c6f636b24ff00246d696e6563726166743a6c69745f64656570736c6174655f72656473746f6e655f6f72656cfe00176d696e6563726166743a676f6c64656e5f73686f76656c430100106d696e6563726166743a656c79747261360200186d696e6563726166743a676f6c64656e5f7069636b6178654401001b6d696e6563726166743a6c69745f72656473746f6e655f6c616d707c0000146d696e6563726166743a676f6c64656e5f617865450100146d696e6563726166743a656c656d656e745f3532c1ff00216d696e6563726166743a7265616c5f646f75626c655f73746f6e655f736c61623458ff00106d696e6563726166743a737472696e67460100116d696e6563726166743a666561746865724701001f6d696e6563726166743a64656570736c6174655f656d6572616c645f6f726569fe00136d696e6563726166743a67756e706f776465724801001e6d696e6563726166743a736b756c6c5f62616e6e65725f7061747465726e490200146d696e6563726166743a776f6f64656e5f686f65490100176d696e6563726166743a6163616369615f737461697273a30000196d696e6563726166743a70616e64615f737061776e5f656767ea0100136d696e6563726166743a73746f6e655f686f654a0100126d696e6563726166743a69726f6e5f686f654b0100146d696e6563726166743a656c656d656e745f38369fff00156d696e6563726166743a6469616d6f6e645f686f654c0100146d696e6563726166743a676f6c64656e5f686f654d0100156d696e6563726166743a6d6167656e74615f6479659901000f6d696e6563726166743a77686561744e0100146d696e6563726166743a676c6f775f6672616d65740200186d696e6563726166743a6c6561746865725f68656c6d65744f01001c6d696e6563726166743a6c6561746865725f6368657374706c6174655001001a6d696e6563726166743a6c6561746865725f6c656767696e6773510100206d696e6563726166743a676c6973746572696e675f6d656c6f6e5f736c696365b30100186d696e6563726166743a62726f776e5f6d757368726f6f6d270000136d696e6563726166743a6c6f646573746f6e6522ff00176d696e6563726166743a6c6561746865725f626f6f74735201001e6d696e6563726166743a636861696e6d61696c5f6368657374706c617465540100156d696e6563726166743a656e645f67617465776179d100001c6d696e6563726166743a636861696e6d61696c5f6c656767696e6773550100176d696e6563726166743a6974656d2e62656574726f6f74f40000156d696e6563726166743a656c656d656e745f31303190ff00196d696e6563726166743a636861696e6d61696c5f626f6f7473560100146d696e6563726166743a656c656d656e745f3439c4ff00136d696e6563726166743a736f756c5f73616e64580000126d696e6563726166743a736e6f7762616c6c770100156d696e6563726166743a69726f6e5f68656c6d6574570100106d696e6563726166743a62617272656c35ff001a6d696e6563726166743a7261775f636f707065725f626c6f636b3cfe00196d696e6563726166743a69726f6e5f6368657374706c617465580100176d696e6563726166743a69726f6e5f6c656767696e6773590100146d696e6563726166743a69726f6e5f626f6f74735a0100216d696e6563726166743a7265616c5f646f75626c655f73746f6e655f736c61623359ff00136d696e6563726166743a656e6465725f657965b201001c6d696e6563726166743a6d757369635f646973635f70696773746570710200176d696e6563726166743a69726f6e5f74726170646f6f72a70000186d696e6563726166743a6469616d6f6e645f68656c6d65745b01001c6d696e6563726166743a6469616d6f6e645f6368657374706c6174655c01001e6d696e6563726166743a73746f6e655f70726573737572655f706c6174654600000e6d696e6563726166743a73616e640c0000276d696e6563726166743a6c696768745f77656967687465645f70726573737572655f706c617465930000106d696e6563726166743a706973746f6e210000196d696e6563726166743a6375745f636f707065725f736c616297fe001b6d696e6563726166743a61786f6c6f746c5f737061776e5f656767f601001a6d696e6563726166743a6469616d6f6e645f6c656767696e67735d0100176d696e6563726166743a6469616d6f6e645f626f6f74735e0100136d696e6563726166743a6d6f645f61726d6f725f01000e6d696e6563726166743a6b656c707f0100176d696e6563726166743a676f6c64656e5f68656c6d6574600100146d696e6563726166743a656c656d656e745f3531c2ff001c6d696e6563726166743a646f75626c655f776f6f64656e5f736c61629d00001c6d696e6563726166743a686172645f737461696e65645f676c617373fe0000146d696e6563726166743a656c656d656e745f3834a1ff001b6d696e6563726166743a676f6c64656e5f6368657374706c617465610100196d696e6563726166743a676f6c64656e5f6c656767696e6773620100136d696e6563726166743a676c6f7773746f6e65590000166d696e6563726166743a676f6c64656e5f626f6f7473630100236d696e6563726166743a706f6c69736865645f64656570736c6174655f7374616972737ffe00106d696e6563726166743a736869656c64640100166d696e6563726166743a666c6f77696e675f6c6176610a00001b6d696e6563726166743a6a756e676c655f66656e63655f67617465b90000106d696e6563726166743a636172706574ab0000196d696e6563726166743a6167656e745f737061776e5f656767e801000f6d696e6563726166743a666c696e746501001a6d696e6563726166743a68656172745f6f665f7468655f7365613d0200126d696e6563726166743a7061696e74696e67660100226d696e6563726166743a6d6f7373795f636f62626c6573746f6e655f7374616972734dff00126d696e6563726166743a6f616b5f7369676e670100146d696e6563726166743a656c656d656e745f3535beff00196d696e6563726166743a6d757369635f646973635f77616974230200156d696e6563726166743a776f6f64656e5f646f6f72680100156d696e6563726166743a6d696c6b5f6275636b65746a0100116d696e6563726166743a7265645f6479658d0100186d696e6563726166743a746164706f6c655f6275636b65747a02000e6d696e6563726166743a626f6e65a00100166d696e6563726166743a77617465725f6275636b65746b0100146d696e6563726166743a656c656d656e745f3734abff001b6d696e6563726166743a7368756c6b65725f737061776e5f656767d60100156d696e6563726166743a6c6176615f6275636b65746c0100236d696e6563726166743a6d6167656e74615f676c617a65645f7465727261636f747461de00001e6d696e6563726166743a76696e64696361746f725f737061776e5f656767db0100146d696e6563726166743a636f645f6275636b65746d0100206d696e6563726166743a6372696d736f6e5f70726573737572655f706c617465fafe00216d696e6563726166743a6578706f7365645f6375745f636f707065725f736c616296fe001b6d696e6563726166743a7370727563655f66656e63655f67617465b700001e6d696e6563726166743a74726f706963616c5f666973685f6275636b65746f01001b6d696e6563726166743a707566666572666973685f6275636b6574700100196d696e6563726166743a6974656d2e62697263685f646f6f72c20000176d696e6563726166743a6d757369635f646973635f31312202001a6d696e6563726166743a65766f6b65725f737061776e5f656767dc01001a6d696e6563726166743a6974656d2e6e65746865725f776172747300001c6d696e6563726166743a706f776465725f736e6f775f6275636b6574710100186d696e6563726166743a61786f6c6f746c5f6275636b65747201001a6d696e6563726166743a706172726f745f737061776e5f656767df0100186d696e6563726166743a776f6c665f737061776e5f656767b80100196d696e6563726166743a636f6e63726574655f706f77646572ed0000126d696e6563726166743a6d696e6563617274730100106d696e6563726166743a736164646c65740100156d696e6563726166743a656c656d656e745f31313681ff001b6d696e6563726166743a6e65746865725f776172745f626c6f636bd60000176d696e6563726166743a6372696d736f6e5f726f6f747321ff00136d696e6563726166743a69726f6e5f646f6f72750100126d696e6563726166743a72656473746f6e65760100226d696e6563726166743a656c6465725f677561726469616e5f737061776e5f656767d80100126d696e6563726166743a63726f7373626f77410200126d696e6563726166743a6f616b5f626f6174780100186d696e6563726166743a616374697661746f725f7261696c7e0000146d696e6563726166743a62697263685f626f6174790100146d696e6563726166743a656c656d656e745f393794ff00146d696e6563726166743a707269736d6172696e65a80000156d696e6563726166743a6a756e676c655f626f61747a0100216d696e6563726166743a706f6c69736865645f6772616e6974655f73746169727354ff001e6d696e6563726166743a73696c766572666973685f737061776e5f656767bc0100196d696e6563726166743a6368656d69737472795f7461626c65ee0000156d696e6563726166743a7370727563655f626f61747b0100156d696e6563726166743a6163616369615f626f61747c0100176d696e6563726166743a6461726b5f6f616b5f626f61747d0100116d696e6563726166743a6c6561746865727e0100156d696e6563726166743a677261795f63616e646c655cfe00166d696e6563726166743a7772697474656e5f626f6f6b010200126d696e6563726166743a69726f6e5f6f72650f0000156d696e6563726166743a676f6c645f6e7567676574aa01001e6d696e6563726166743a676c6f62655f62616e6e65725f7061747465726e4e02000f6d696e6563726166743a627269636b800100136d696e6563726166743a636c61795f62616c6c810100156d696e6563726166743a6c69745f70756d706b696e5b0000146d696e6563726166743a73756761725f63616e65820100196d696e6563726166743a6e65746865726974655f696e676f74600200146d696e6563726166743a656c656d656e745f3233deff000f6d696e6563726166743a70617065728301000f6d696e6563726166743a636f72616c7dff001b6d696e6563726166743a746164706f6c655f737061776e5f6567677902000e6d696e6563726166743a626f6f6b840100146d696e6563726166743a736c696d655f62616c6c8501000d6d696e6563726166743a6d6f64540200116d696e6563726166743a74726964656e74240200186d696e6563726166743a63686573745f6d696e6563617274860100146d696e6563726166743a6d75645f627269636b7325fe00176d696e6563726166743a636f775f737061776e5f656767b501000d6d696e6563726166743a656767870100196d696e6563726166743a6e65746865726974655f73776f7264610200196d696e6563726166743a6d757369635f646973635f7374616c1f0200146d696e6563726166743a6974656d2e7265656473530000116d696e6563726166743a636f6d70617373880100186d696e6563726166743a6372696d736f6e5f73746169727302ff00156d696e6563726166743a66697368696e675f726f648901001a6d696e6563726166743a6f63656c6f745f737061776e5f656767c40100136d696e6563726166743a726573657276656436ff0000196d696e6563726166743a616e6465736974655f73746169727355ff000f6d696e6563726166743a636c6f636b8a0100176d696e6563726166743a7265645f73616e6473746f6e65b30000176d696e6563726166743a7370727563655f627574746f6e70ff00186d696e6563726166743a676c6f7773746f6e655f647573748b0100136d696e6563726166743a626c61636b5f6479658c0100156d696e6563726166743a7368756c6b65725f626f78da0000136d696e6563726166743a677265656e5f6479658e0100176d696e6563726166743a6265655f737061776e5f656767ef01001e6d696e6563726166743a64656570736c6174655f627269636b5f77616c6c76fe000e6d696e6563726166743a64656e79d30000186d696e6563726166743a6d75645f627269636b5f736c616222fe00136d696e6563726166743a62726f776e5f6479658f01000f6d696e6563726166743a6672616d65030200126d696e6563726166743a626c75655f647965900100136d696e6563726166743a6974656d2e63616b655c0000146d696e6563726166743a707572706c655f6479659101000d6d696e6563726166743a647965860200176d696e6563726166743a6d757369635f646973635f3133180200126d696e6563726166743a6379616e5f647965920100136d696e6563726166743a626c617a655f726f64a80100186d696e6563726166743a6c696768745f677261795f647965930100256d696e6563726166743a737469636b795f706973746f6e5f61726d5f636f6c6c6973696f6e27ff00206d696e6563726166743a7069676c696e5f62727574655f737061776e5f656767f50100146d696e6563726166743a656c656d656e745f3431ccff00126d696e6563726166743a677261795f6479659401001a6d696e6563726166743a7261626269745f737061776e5f656767cc0100126d696e6563726166743a70696e6b5f647965950100126d696e6563726166743a6c696d655f647965960100146d696e6563726166743a79656c6c6f775f647965970100176d696e6563726166743a626c6173745f6675726e6163653cff00196d696e6563726166743a646972745f776974685f726f6f7473c2fe00146d696e6563726166743a747572746c655f65676761ff00186d696e6563726166743a6c696768745f626c75655f6479659801000d6d696e6563726166743a626564a30100176d696e6563726166743a737461696e65645f676c617373f10000146d69";
        String str2 = "6e6563726166743a6f72616e67655f6479659a0100176d696e6563726166743a79656c6c6f775f63616e646c655ffe00136d696e6563726166743a77686974655f6479659b0100136d696e6563726166743a626f6e655f6d65616c9c0100196d696e6563726166743a6974656d2e666c6f7765725f706f748c0000176d696e6563726166743a747572746c655f68656c6d65743f0200116d696e6563726166743a756e6b6e6f776ecffe00116d696e6563726166743a696e6b5f7361639e0100106d696e6563726166743a63616d657261560200166d696e6563726166743a63686f7275735f66727569743002001f6d696e6563726166743a73747269707065645f6372696d736f6e5f7374656d10ff00166d696e6563726166743a6c617069735f6c617a756c699f01001d6d696e6563726166743a6d656469756d5f616d6574687973745f627564b5fe00196d696e6563726166743a737573706963696f75735f737465775002000f6d696e6563726166743a7375676172a10100126d696e6563726166743a6e616d655f7461672602001b6d696e6563726166743a637265657065725f737061776e5f656767ba01000e6d696e6563726166743a63616b65a20100156d696e6563726166743a626c75655f63616e646c6558fe00106d696e6563726166743a626561636f6e8a0000126d696e6563726166743a7265706561746572a401001e6d696e6563726166743a6e65746865726974655f6368657374706c617465670200146d696e6563726166743a66696c6c65645f6d6170a50100226d696e6563726166743a706f6c69736865645f616e6465736974655f73746169727352ff001b6d696e6563726166743a64726f776e65645f737061776e5f656767e401001e6d696e6563726166743a756e706f77657265645f636f6d70617261746f72950000106d696e6563726166743a736865617273a60100156d696e6563726166743a656e6465725f706561726ca70100186d696e6563726166743a6361727665645f70756d706b696e65ff001e6d696e6563726166743a7265645f73616e6473746f6e655f737461697273b40000146d696e6563726166743a67686173745f74656172a90100166d696e6563726166743a676c6173735f626f74746c65ac0100146d696e6563726166743a656c656d656e745f3434c9ff00176d696e6563726166743a636f6f6b65645f6d7574746f6e2902001b6d696e6563726166743a677265656e5f63616e646c655f63616b6545fe001f6d696e6563726166743a6a756e676c655f70726573737572655f706c61746567ff002a6d696e6563726166743a706f6c69736865645f626c61636b73746f6e655f627269636b5f737461697273edfe001e6d696e6563726166743a6665726d656e7465645f7370696465725f657965ad0100196d696e6563726166743a686f6e6579636f6d625f626c6f636b23ff00166d696e6563726166743a626c617a655f706f77646572ae0100156d696e6563726166743a6d61676d615f637265616daf0100176d696e6563726166743a62726577696e675f7374616e64b00100106d696e6563726166743a6a69677361772dff00156d696e6563726166743a656c656d656e745f31313186ff00126d696e6563726166743a6361756c64726f6eb101001b6d696e6563726166743a636869636b656e5f737061776e5f656767b40100176d696e6563726166743a7069675f737061776e5f656767b60100196d696e6563726166743a73686565705f737061776e5f656767b701001b6d696e6563726166743a66697265666c795f737061776e5f6567677d0200116d696e6563726166743a63616c63697465bafe001d6d696e6563726166743a6d6f6f7368726f6f6d5f737061776e5f656767b901001c6d696e6563726166743a646f75626c655f73746f6e655f736c6162335eff001c6d696e6563726166743a736b656c65746f6e5f737061776e5f656767bd0100146d696e6563726166743a66656e63655f676174656b0000156d696e6563726166743a627269636b5f626c6f636b2d00001a6d696e6563726166743a636f6c6f7265645f746f7263685f7267ca0000106d696e6563726166743a626c656163685902001a6d696e6563726166743a7370696465725f737061776e5f656767bf0100146d696e6563726166743a656c656d656e745f3231e0ff001a6d696e6563726166743a7a6f6d6269655f737061776e5f656767c001001c6d696e6563726166743a76696c6c616765725f737061776e5f656767c20100196d696e6563726166743a73717569645f737061776e5f656767c30100136d696e6563726166743a636f6d706f737465722bff001c6d696e6563726166743a706f77657265645f636f6d70617261746f72960000176d696e6563726166743a6261745f737061776e5f656767c60100136d696e6563726166743a656c656d656e745f30240000196d696e6563726166743a67686173745f737061776e5f656767c70100156d696e6563726166743a7761727065645f7369676e6c0200156d696e6563726166743a6d6f625f737061776e6572340000206d696e6563726166743a63686973656c65645f6e65746865725f627269636b73d2fe000f6d696e6563726166743a636861696e7002001e6d696e6563726166743a6d61676d615f637562655f737061776e5f656767c80100226d696e6563726166743a7761727065645f66756e6775735f6f6e5f615f737469636b6f0200176d696e6563726166743a736f756c5f63616d70666972657302001f6d696e6563726166743a636176655f7370696465725f737061776e5f656767ca01001d6d696e6563726166743a656e6465726d6974655f737061776e5f656767cd01001c6d696e6563726166743a677561726469616e5f737061776e5f656767ce0100186d696e6563726166743a6875736b5f737061776e5f656767d001001a6d696e6563726166743a7069676c696e5f737061776e5f656767f30100236d696e6563726166743a7769746865725f736b656c65746f6e5f737061776e5f656767d101001a6d696e6563726166743a70696e6b5f63616e646c655f63616b654cfe00176d696e6563726166743a77656570696e675f76696e657319ff00196d696e6563726166743a676c6f77696e676f6273696469616ef60000116d696e6563726166743a6c656176657332a10000156d696e6563726166743a7370727563655f7369676e4202001a6d696e6563726166743a646f6e6b65795f737061776e5f656767d20100186d696e6563726166743a6d756c655f737061776e5f656767d30100166d696e6563726166743a646f75626c655f706c616e74af0000226d696e6563726166743a736b656c65746f6e5f686f7273655f737061776e5f656767d40100156d696e6563726166743a656c656d656e745f31303988ff001b6d696e6563726166743a6e65746865726974655f7069636b6178656302001a6d696e6563726166743a666c6f776572696e675f617a616c6561aefe00206d696e6563726166743a7a6f6d6269655f686f7273655f737061776e5f656767d50100116d696e6563726166743a6a756b65626f78540000176d696e6563726166743a6e70635f737061776e5f656767d701001e6d696e6563726166743a706f6c61725f626561725f737061776e5f656767d90100146d696e6563726166743a656c656d656e745f3830a5ff00136d696e6563726166743a69726f6e5f62617273650000156d696e6563726166743a706f776465725f736e6f77cefe00196d696e6563726166743a6c6c616d615f737061776e5f656767da0100136d696e6563726166743a656e645f73746f6e657900001a6d696e6563726166743a7370727563655f77616c6c5f7369676e4aff00176d696e6563726166743a7665785f737061776e5f656767dd01001a6d696e6563726166743a77617264656e5f737061776e5f6567677c0200246d696e6563726166743a6461796c696768745f6465746563746f725f696e766572746564b20000196d696e6563726166743a7761727065645f74726170646f6f7209ff00186d696e6563726166743a7477697374696e675f76696e6573e1fe00236d696e6563726166743a7a6f6d6269655f76696c6c616765725f737061776e5f656767de01001a6d696e6563726166743a72617069645f66657274696c697a65725a0200256d696e6563726166743a7765617468657265645f6375745f636f707065725f7374616972739cfe000e6d696e6563726166743a636c6179520000216d696e6563726166743a74726f706963616c5f666973685f737061776e5f656767e00100176d696e6563726166743a7374616e64696e675f7369676e3f0000176d696e6563726166743a636f645f737061776e5f656767e10100186d696e6563726166743a6372696d736f6e5f66756e6775731cff00146d696e6563726166743a6974656d2e6672616d65c700001c6d696e6563726166743a7265645f6d757368726f6f6d5f626c6f636b6400001e6d696e6563726166743a707566666572666973685f737061776e5f656767e20100156d696e6563726166743a7761727065645f736c6162f7fe001a6d696e6563726166743a73616c6d6f6e5f737061776e5f656767e301001c6d696e6563726166743a646f75626c655f73746f6e655f736c616232b600001b6d696e6563726166743a646f6c7068696e5f737061776e5f656767e501001a6d696e6563726166743a747572746c655f737061776e5f656767e601001b6d696e6563726166743a7068616e746f6d5f737061776e5f656767e70100206d696e6563726166743a646f75626c655f6375745f636f707065725f736c616290fe00146d696e6563726166743a656c656d656e745f3238d9ff00226d696e6563726166743a6f786964697a65645f6375745f636f707065725f736c616294fe00176d696e6563726166743a6361745f737061776e5f656767e901001a6d696e6563726166743a6974656d2e6163616369615f646f6f72c400001a6d696e6563726166743a636f62626c6573746f6e655f77616c6c8b0000176d696e6563726166743a666f785f737061776e5f656767eb0100106d696e6563726166743a71756172747a0e02001b6d696e6563726166743a636172726f745f6f6e5f615f737469636b070200246d696e6563726166743a77616e646572696e675f7472616465725f737061776e5f656767ed0100126d696e6563726166743a74726170646f6f726000001a6d696e6563726166743a686f676c696e5f737061776e5f656767f10100186d696e6563726166743a676f61745f737061776e5f656767f701001d6d696e6563726166743a6f786964697a65645f6375745f636f70706572a2fe00166d696e6563726166743a676c6f775f696e6b5f736163f90100166d696e6563726166743a636f707065725f696e676f74fa01001c6d696e6563726166743a6f72616e67655f63616e646c655f63616b6551fe00166d696e6563726166743a636f707065725f626c6f636bacfe001d6d696e6563726166743a73747269707065645f6a756e676c655f6c6f67f9ff00146d696e6563726166743a6375745f636f70706572a5fe00126d696e6563726166743a73656167726173737eff00216d696e6563726166743a6461726b5f6f616b5f70726573737572655f706c61746568ff001a6d696e6563726166743a707269736d6172696e655f73686172643702001b6d696e6563726166743a6375745f636f707065725f7374616972739efe00136d696e6563726166743a77617465726c696c796f0000236d696e6563726166743a7765617468657265645f6375745f636f707065725f736c616295fe00146d696e6563726166743a7363756c6b5f7665696e35fe001f6d696e6563726166743a77617865645f6375745f636f707065725f736c616293fe00106d696e6563726166743a6d7574746f6e280200276d696e6563726166743a77617865645f6578706f7365645f6375745f636f707065725f736c616292fe00156d696e6563726166743a666972655f636861726765ff0100296d696e6563726166743a77617865645f7765617468657265645f6375745f636f707065725f736c616291fe00196d696e6563726166743a6a756e676c655f74726170646f6f726cff00146d696e6563726166743a656c656d656e745f3437c6ff00286d696e6563726166743a77617865645f6f786964697a65645f6375745f636f707065725f736c61623ffe00146d696e6563726166743a7261775f636f70706572fd01001b6d696e6563726166743a657870657269656e63655f626f74746c65fe0100176d696e6563726166743a7772697461626c655f626f6f6b000200146d696e6563726166743a656c656d656e745f3639b0ff00116d696e6563726166743a656d6572616c64020200256d696e6563726166743a64656570736c6174655f627269636b5f646f75626c655f736c616271fe00146d696e6563726166743a666c6f7765725f706f74040200106d696e6563726166743a6c6561766573120000136d696e6563726166743a656d7074795f6d61700502000f6d696e6563726166743a736b756c6c060200196d696e6563726166743a66697265776f726b5f726f636b6574090200186d696e6563726166743a6372696d736f6e5f6e796c69756d18ff001a6d696e6563726166743a636f6c6f7265645f746f7263685f6270cc0000176d696e6563726166743a66697265776f726b5f737461720a0200156d696e6563726166743a656c656d656e745f3130328fff00186d696e6563726166743a656e6368616e7465645f626f6f6b0b02001a6d696e6563726166743a746f74656d5f6f665f756e6479696e673a0200156d696e6563726166743a6e6574686572627269636b0d0200146d696e6563726166743a656c656d656e745f3633b6ff00166d696e6563726166743a746e745f6d696e65636172740f0200196d696e6563726166743a686f707065725f6d696e6563617274100200176d696e6563726166743a647261676f6e5f627265617468320200106d696e6563726166743a686f70706572110200156d696e6563726166743a636f62626c6573746f6e65040000156d696e6563726166743a7261626269745f686964651302001d6d696e6563726166743a6c6561746865725f686f7273655f61726d6f721402001a6d696e6563726166743a69726f6e5f686f7273655f61726d6f721502001d6d696e6563726166743a6469616d6f6e645f686f7273655f61726d6f72170200156d696e6563726166743a6a756e676c655f646f6f722d0200186d696e6563726166743a6d757369635f646973635f6361741902001d6d696e6563726166743a64656570736c6174655f6c617069735f6f726570fe00156d696e6563726166743a6d6963726f5f626c6f636bf9d9001b6d696e6563726166743a6d757369635f646973635f626c6f636b731a0200136d696e6563726166743a73616e6473746f6e65180000176d696e6563726166743a776f6f64656e5f627574746f6e8f0000186d696e6563726166743a6d757369635f646973635f6661721c02001c6d696e6563726166743a6d757369635f646973635f6d656c6c6f68691e0200166d696e6563726166743a696e666f5f75706461746532f900001d6d696e6563726166743a707269736d6172696e655f6372797374616c732702000e6d696e6563726166743a6c656164250200156d696e6563726166743a6163616369615f7369676e450200126d696e6563726166743a636f616c5f6f7265100000156d696e6563726166743a61726d6f725f7374616e642a02001a6d696e6563726166743a7068616e746f6d5f6d656d6272616e65400200156d696e6563726166743a7370727563655f646f6f722b0200146d696e6563726166743a62697263685f646f6f722c0200156d696e6563726166743a6163616369615f646f6f722e0200146d696e6563726166743a656c656d656e745f3432cbff001c6d696e6563726166743a6e65746865726974655f6c656767696e6773680200176d696e6563726166743a6461726b5f6f616b5f646f6f722f02001d6d696e6563726166743a706f707065645f63686f7275735f6672756974310200146d696e6563726166743a656c656d656e745f3733acff00176d696e6563726166743a73706c6173685f706f74696f6e330200176d696e6563726166743a7368756c6b65725f7368656c6c380200186d696e6563726166743a72656473746f6e655f626c6f636b980000106d696e6563726166743a62616e6e6572390200156d696e6563726166743a69726f6e5f6e75676765743b0200226d696e6563726166743a637261636b65645f64656570736c6174655f627269636b7366fe00186d696e6563726166743a636f72616c5f66616e5f646561647aff00146d696e6563726166743a62697263685f7369676e430200196d696e6563726166743a636f72616c5f66616e5f68616e673278ff00116d696e6563726166743a62616c6c6f6f6e5b0200156d696e6563726166743a6a756e676c655f7369676e440200176d696e6563726166743a6461726b5f6f616b5f7369676e4602001f6d696e6563726166743a666c6f7765725f62616e6e65725f7061747465726e470200156d696e6563726166743a6d6f6e737465725f6567676100001f6d696e6563726166743a6d6f6a616e675f62616e6e65725f7061747465726e4a0200216d696e6563726166743a706f6c69736865645f64696f726974655f73746169727353ff00186d696e6563726166743a66726f675f737061776e5f656767780200266d696e6563726166743a6669656c645f6d61736f6e65645f62616e6e65725f7061747465726e4b02000e6d696e6563726166743a62656c6c32ff001e6d696e6563726166743a736d616c6c5f647269706c6561665f626c6f636bb0fe00296d696e6563726166743a626f72647572655f696e64656e7465645f62616e6e65725f7061747465726e4c02001c6d696e6563726166743a707572706c655f63616e646c655f63616b6548fe001f6d696e6563726166743a7069676c696e5f62616e6e65725f7061747465726e4d0200126d696e6563726166743a706f7461746f65738e0000146d696e6563726166743a656c656d656e745f3738a7ff00136d696e6563726166743a686f6e6579636f6d62520200156d696e6563726166743a6d6f73735f636172706574b1fe00166d696e6563726166743a686f6e65795f626f74746c655302001a6d696e6563726166743a7265645f6e65746865725f627269636bd70000106d696e6563726166743a6d6f645f6578550200126d696e6563726166743a636f6d706f756e64570200126d696e6563726166743a6963655f626f6d62580200126d696e6563726166743a6d65646963696e655c02001a6d696e6563726166743a656e645f706f7274616c5f6672616d65780000176d696e6563726166743a7761727065645f66756e6775731bff00146d696e6563726166743a656c656d656e745f393299ff00146d696e6563726166743a676c6f775f737469636b5e0200126d696e6563726166743a626c75655f696365f5ff001b6d696e6563726166743a6c6f646573746f6e655f636f6d706173735f0200146d696e6563726166743a656c656d656e745f3833a2ff001a6d696e6563726166743a6e65746865726974655f73686f76656c620200146d696e6563726166743a71756172747a5f6f72659900000e6d696e6563726166743a6c6f6f6d34ff001d6d696e6563726166743a636861696e5f636f6d6d616e645f626c6f636bbd0000286d696e6563726166743a706f6c69736865645f64656570736c6174655f646f75626c655f736c616273fe001a6d696e6563726166743a6974656d2e7761727065645f646f6f720bff00176d696e6563726166743a6e65746865726974655f617865640200176d696e6563726166743a6e65746865726974655f686f656502001a6d696e6563726166743a6e65746865726974655f68656c6d65746602000d6d696e6563726166743a6d756427fe001b6d696e6563726166743a6c696768745f677261795f63616e646c655bfe001b6d696e6563726166743a626c61636b5f63616e646c655f63616b6543fe00196d696e6563726166743a6e65746865726974655f73637261706a0200126d696e6563726166743a636f6e6372657465ec0000166d696e6563726166743a6372696d736f6e5f7369676e6b0200166d696e6563726166743a6372696d736f6e5f646f6f726d0200106d696e6563726166743a73706f6e6765130000186d696e6563726166743a6e65746865725f7370726f7574737202001b6d696e6563726166743a636172746f6772617068795f7461626c6538ff00196d696e6563726166743a626c61636b73746f6e655f736c6162e6fe00226d696e6563726166743a706f6c69736865645f626c61636b73746f6e655f736c6162dbfe00206d696e6563726166743a636f62626c65645f64656570736c6174655f736c616284fe00216d696e6563726166743a706f6c69736865645f64656570736c6174655f736c616280fe001d6d696e6563726166743a64656570736c6174655f74696c655f736c61627cfe00126d696e6563726166743a737079676c6173737602001e6d696e6563726166743a64656570736c6174655f627269636b5f736c616278fe00146d696e6563726166743a686172645f676c617373fd0000206d696e6563726166743a636f62626c65645f64656570736c6174655f77616c6c82fe00146d696e6563726166743a656c656d656e745f3736a9ff00186d696e6563726166743a616d6574687973745f73686172647502001a6d696e6563726166743a62697263685f63686573745f626f61747f0200146d696e6563726166743a656c656d656e745f3333d4ff001e6d696e6563726166743a6d757369635f646973635f6f7468657273696465770200146d696e6563726166743a66726f675f737061776e2cfe00196d696e6563726166743a616c6c61795f737061776e5f6567677b0200306d696e6563726166743a77617865645f7765617468657265645f646f75626c655f6375745f636f707065725f736c61628afe001a6d696e6563726166743a64726965645f6b656c705f626c6f636b75ff001b6d696e6563726166743a626c61636b73746f6e655f737461697273ecfe001c6d696e6563726166743a6d616e67726f76655f70726f706167756c6526fe00156d696e6563726166743a73636166666f6c64696e675bff00126d696e6563726166743a6f62736572766572fb00001a6d696e6563726166743a6379616e5f63616e646c655f63616b6549fe00156d696e6563726166743a63616e646c655f63616b6553fe00206d696e6563726166743a70696e6b5f676c617a65645f7465727261636f747461e200001b6d696e6563726166743a6a756e676c655f63686573745f626f61748002001b6d696e6563726166743a7370727563655f63686573745f626f61748102001b6d696e6563726166743a6163616369615f63686573745f626f61748202001d6d696e6563726166743a6461726b5f6f616b5f63686573745f626f6174830200146d696e6563726166743a63686573745f626f6174840200176d696e6563726166743a74726970776972655f686f6f6b8300000f6d696e6563726166743a73746f6e650100000e6d696e6563726166743a776f6f6c230000176d696e6563726166743a79656c6c6f775f666c6f7765722500001f6d696e6563726166743a737461696e65645f68617264656e65645f636c61799f00000d6d696e6563726166743a6c6f671100000f6d696e6563726166743a66656e6365550000146d696e6563726166743a656c656d656e745f3533c0ff00146d696e6563726166743a73746f6e65627269636b620000156d696e6563726166743a636f72616c5f626c6f636b7cff001b6d696e6563726166743a6c69745f626c6173745f6675726e6163652aff001b6d696e6563726166743a646f75626c655f73746f6e655f736c61622c0000156d696e6563726166743a656c656d656e745f31303091ff00246d696e6563726166743a706f6c69736865645f626c61636b73746f6e655f627269636b73eefe001c6d696e6563726166743a646f75626c655f73746f6e655f736c6162345aff000e6d696e6563726166743a7261696c420000206d696e6563726166743a7265616c5f646f75626c655f73746f6e655f736c61622b00001d6d696e6563726166743a73747269707065645f6163616369615f6c6f67f8ff001c6d696e6563726166743a736d616c6c5f616d6574687973745f627564b4fe00136d696e6563726166743a636f72616c5f66616e7bff001c6d696e6563726166743a6c617267655f616d6574687973745f627564b6fe00166d696e6563726166743a74696e7465645f676c617373b2fe00246d696e6563726166743a706f6c69736865645f626c61636b73746f6e655f627574746f6ed8fe00146d696e6563726166743a7365615f7069636b6c6564ff00296d696e6563726166743a706f6c69736865645f626c61636b73746f6e655f646f75626c655f736c6162dafe00116d696e6563726166743a7361706c696e67060000206d696e6563726166743a626c61636b73746f6e655f646f75626c655f736c6162e5fe00106d696e6563726166743a74617267657411ff00176d696e6563726166743a617a616c65615f6c6561766573bcfe00206d696e6563726166743a617a616c65615f6c65617665735f666c6f7765726564bbfe00136d696e6563726166743a736f756c5f6669726513ff00166d696e6563726166743a7761727065645f726f6f747320ff00146d696e6563726166743a7265645f666c6f776572260000166d696e6563726166743a71756172747a5f626c6f636b9b0000136d696e6563726166743a74616c6c67726173731f00001f6d696e6563726166743a6163616369615f70726573737572655f706c6174656aff00136d696e6563726166743a736f756c5f736f696c14ff00206d696e6563726166743a77617865645f7765617468657265645f636f70706572a6fe001e6d696e6563726166743a62726f776e5f6d757368726f6f6d5f626c6f636b630000156d696e6563726166743a656c656d656e745f3130338eff000e6d696e6563726166743a6c6f6732a20000116d696e6563726166743a636f6e6475697463ff000f6d696e6563726166743a6d61676d61d50000146d696e6563726166743a656c656d656e745f3232dfff001a6d696e6563726166743a626c75655f63616e646c655f63616b6547fe001c6d696e6563726166743a756e647965645f7368756c6b65725f626f78cd0000176d696e6563726166743a737469636b795f706973746f6e1d00001e6d696e6563726166743a7370727563655f7374616e64696e675f7369676e4bff00106d696e6563726166743a62616d626f6f5dff00146d696e6563726166743a6772696e6473746f6e653dff00166d696e6563726166743a77617865645f636f70706572a8fe00116d696e6563726166743a656e645f726f64d00000196d696e6563726166743a666c65746368696e675f7461626c6537ff00186d696e6563726166743a6d6167656e74615f63616e646c6561fe00256d696e6563726166743a736d6f6f74685f7265645f73616e6473746f6e655f73746169727350ff000e6d696e6563726166743a776f6f642cff00156d696e6563726166743a6974656d2e686f707065729a00001f6d696e6563726166743a6d75645f627269636b5f646f75626c655f736c616221fe000d6d696e6563726166743a746e742e0000216d696e6563726166743a686172645f737461696e65645f676c6173735f70616e65bf0000156d696e6563726166743a7769746865725f726f736528ff001f6d696e6563726166743a776f6f64656e5f70726573737572655f706c617465480000116d696e6563726166743a6d6f645f6f7265e60000116d696e6563726166743a70756d706b696e5600000f6d696e6563726166743a736c696d65a50000166d696e6563726166743a6372696d736f6e5f736c6162f8fe00106d696e6563726166743a63616e646c6564fe00176d696e6563726166743a6f72616e67655f63616e646c6562fe001b6d696e6563726166743a6c696768745f626c75655f63616e646c6560fe00156d696e6563726166743a6c696d655f63616e646c655efe00156d696e6563726166743a6379616e5f63616e646c655afe00176d696e6563726166743a707572706c655f63616e646c6559fe00166d696e6563726166743a62726f776e5f63616e646c6557fe00166d696e6563726166743a677265656e5f63616e646c6556fe00146d696e6563726166743a7265645f63616e646c6555fe00196d696e6563726166743a62697263685f77616c6c5f7369676e45ff00216d696e6563726166743a706f6c69736865645f64656570736c6174655f77616c6c7efe00166d696e6563726166743a626c61636b5f63616e646c6554fe00136d696e6563726166743a656c656d656e745f33f2ff00136d696e6563726166743a656c656d656e745f34f1ff00156d696e6563726166743a656e6465725f6368657374820000136d696e6563726166743a656c656d656e745f35f0ff00216d696e6563726166743a77617865645f6375745f636f707065725f7374616972739afe00136d696e6563726166743a656c656d656e745f36efff00136d696e6563726166743a656c656d656e745f38edff001c6d696e6563726166743a64656570736c6174655f636f616c5f6f72656afe00136d696e6563726166743a656c656d656e745f39ecff00146d696e6563726166743a656c656d656e745f3130ebff00146d696e6563726166743a656c656d656e745f3131eaff00146d696e6563726166743a656c656d656e745f3132e9ff00146d696e6563726166743a656c656d656e745f3134e7ff00146d696e6563726166743a656c656d656e745f3136e5ff00146d696e656372616";
        String str3 = "6743a656c656d656e745f3137e4ff002a6d696e6563726166743a636c69656e745f726571756573745f706c616365686f6c6465725f626c6f636b2ffe00146d696e6563726166743a656c656d656e745f3138e3ff00146d696e6563726166743a656c656d656e745f3139e2ff00146d696e6563726166743a656c656d656e745f3230e1ff001f6d696e6563726166743a706561726c657363656e745f66726f676c696768742bfe00146d696e6563726166743a656c656d656e745f3234ddff00146d696e6563726166743a656c656d656e745f3236dbff00146d696e6563726166743a656c656d656e745f3237daff00146d696e6563726166743a656c656d656e745f3239d8ff00146d696e6563726166743a656c656d656e745f3330d7ff00146d696e6563726166743a656c656d656e745f3331d6ff00146d696e6563726166743a656c656d656e745f3332d5ff00146d696e6563726166743a656c656d656e745f3334d3ff00146d696e6563726166743a656c656d656e745f3336d1ff000d6d696e6563726166743a6963654f0000146d696e6563726166743a656c656d656e745f3337d0ff00146d696e6563726166743a656c656d656e745f3338cfff00146d696e6563726166743a656c656d656e745f3339ceff00146d696e6563726166743a656c656d656e745f3430cdff00146d696e6563726166743a656c656d656e745f3435c8ff00146d696e6563726166743a656c656d656e745f3436c7ff00146d696e6563726166743a656c656d656e745f3438c5ff00146d696e6563726166743a656c656d656e745f3534bfff00146d696e6563726166743a656c656d656e745f3536bdff00146d696e6563726166743a656c656d656e745f3537bcff00186d696e6563726166743a6372616674696e675f7461626c653a00001a6d696e6563726166743a6c69745f72656473746f6e655f6f72654a0000216d696e6563726166743a626c61636b5f676c617a65645f7465727261636f747461eb0000146d696e6563726166743a656c656d656e745f3538bbff00146d696e6563726166743a656c656d656e745f3539baff00146d696e6563726166743a656c656d656e745f3630b9ff00206d696e6563726166743a6c696768745f677261795f63616e646c655f63616b654afe00146d696e6563726166743a656c656d656e745f3634b5ff00146d696e6563726166743a656c656d656e745f3635b4ff00146d696e6563726166743a656c656d656e745f3636b3ff00146d696e6563726166743a656c656d656e745f3637b2ff00146d696e6563726166743a656c656d656e745f3638b1ff00146d696e6563726166743a656c656d656e745f3730afff00146d696e6563726166743a656c656d656e745f3731aeff001c6d696e6563726166743a696e6665737465645f64656570736c6174653afe00146d696e6563726166743a656c656d656e745f3732adff00146d696e6563726166743a656c656d656e745f3735aaff00196d696e6563726166743a6461726b5f6f616b5f627574746f6e72ff00146d696e6563726166743a656c656d656e745f3737a8ff00186d696e6563726166743a64696f726974655f73746169727356ff00146d696e6563726166743a656c656d656e745f3739a6ff00186d696e6563726166743a72656473746f6e655f746f7263684c0000146d696e6563726166743a656c656d656e745f3831a4ff00146d696e6563726166743a656c656d656e745f3832a3ff00226d696e6563726166743a706f6c69736865645f626c61636b73746f6e655f77616c6cd7fe00146d696e6563726166743a656c656d656e745f3835a0ff00146d696e6563726166743a656c656d656e745f38379eff00146d696e6563726166743a656c656d656e745f38389dff00146d696e6563726166743a656c656d656e745f38399cff00146d696e6563726166743a656c656d656e745f39309bff00146d696e6563726166743a656c656d656e745f39319aff00146d696e6563726166743a656c656d656e745f393398ff00146d696e6563726166743a656c656d656e745f393497ff00146d696e6563726166743a656c656d656e745f393596ff00146d696e6563726166743a656c656d656e745f393695ff00296d696e6563726166743a77617865645f6578706f7365645f6375745f636f707065725f73746169727399fe00146d696e6563726166743a656c656d656e745f393893ff00146d696e6563726166743a656c656d656e745f393992ff00106d696e6563726166743a636163747573510000156d696e6563726166743a656c656d656e745f3130358cff00156d696e6563726166743a656c656d656e745f3130368bff00206d696e6563726166743a6379616e5f676c617a65645f7465727261636f747461e50000156d696e6563726166743a656c656d656e745f3130378aff00156d696e6563726166743a656c656d656e745f31303889ff00156d696e6563726166743a656c656d656e745f31313087ff00156d696e6563726166743a656c656d656e745f31313285ff00176d696e6563726166743a7761727065645f627574746f6efbfe00156d696e6563726166743a656c656d656e745f31313384ff00156d696e6563726166743a656c656d656e745f31313483ff00166d696e6563726166743a62697263685f737461697273870000216d696e6563726166743a637261636b65645f64656570736c6174655f74696c657367fe001d6d696e6563726166743a6461726b5f6f616b5f66656e63655f67617465ba0000156d696e6563726166743a656c656d656e745f31313582ff00156d696e6563726166743a656c656d656e745f31313780ff00156d696e6563726166743a656c656d656e745f3131387fff00196d696e6563726166743a6e65746865726974655f626c6f636bf2fe001b6d696e6563726166743a77686974655f63616e646c655f63616b6552fe00186d696e6563726166743a7265737061776e5f616e63686f72f0fe00196d696e6563726166743a637279696e675f6f6273696469616edffe000e6d696e6563726166743a626f6174850200186d696e6563726166743a62616e6e65725f7061747465726e870200156d696e6563726166743a656e645f6372797374616c890200176d696e6563726166743a736d6f6f74685f626173616c7487fe00166d696e6563726166743a676c6f775f626572726965738a0200266d696e6563726166743a63686973656c65645f706f6c69736865645f626c61636b73746f6e65e9fe001d6d696e6563726166743a62697263685f7374616e64696e675f7369676e46ff00196d696e6563726166743a6974656d2e676c6f775f6672616d65adfe00196d696e6563726166743a706f6c69736865645f626173616c7415ff00196d696e6563726166743a6e65746865725f676f6c645f6f7265e0fe001e6d696e6563726166743a706973746f6e5f61726d5f636f6c6c6973696f6e2200001e6d696e6563726166743a64656570736c6174655f636f707065725f6f726568fe001d6d696e6563726166743a6e65746865725f627269636b5f737461697273720000176d696e6563726166743a6a756e676c655f627574746f6e71ff001a6d696e6563726166743a677261795f63616e646c655f63616b654bfe001b6d696e6563726166743a6372696d736f6e5f77616c6c5f7369676e04ff00226d696e6563726166743a636f62626c65645f64656570736c6174655f73746169727383fe00176d696e6563726166743a68616e67696e675f726f6f7473c1fe00236d696e6563726166743a77617865645f6f786964697a65645f6375745f636f7070657241fe00226d696e6563726166743a77617865645f6578706f7365645f6375745f636f70706572a0fe001f6d696e6563726166743a73747269707065645f6461726b5f6f616b5f6c6f67f7ff00156d696e6563726166743a696e666f5f757064617465f80000186d696e6563726166743a7363756c6b5f73687269656b657233fe00206d696e6563726166743a6c696d655f676c617a65645f7465727261636f747461e10000176d696e6563726166743a72656473746f6e655f6c616d707b00000f6d696e6563726166743a6c657665724500001b6d696e6563726166743a6d6f7373795f636f62626c6573746f6e65300000136d696e6563726166743a64656570736c61746586fe001f6d696e6563726166743a7761727065645f70726573737572655f706c617465f9fe001a6d696e6563726166743a7761727065645f77616c6c5f7369676e03ff00146d696e6563726166743a6f616b5f737461697273350000186d696e6563726166743a636f72616c5f66616e5f68616e6779ff00146d696e6563726166743a7061636b65645f696365ae0000146d696e6563726166743a7061636b65645f6d756423fe00216d696e6563726166743a736d6f6f74685f73616e6473746f6e655f7374616972734fff00206d696e6563726166743a6c696768745f626c75655f63616e646c655f63616b654ffe00186d696e6563726166743a616d6574687973745f626c6f636bb9fe00166d696e6563726166743a63686f7275735f706c616e74f00000146d696e6563726166743a676f6c645f626c6f636b290000136d696e6563726166743a6e6f7465626c6f636b1900000e6d696e6563726166743a74756666b3fe00246d696e6563726166743a64656570736c6174655f74696c655f646f75626c655f736c616272fe00296d696e6563726166743a6f786964697a65645f646f75626c655f6375745f636f707065725f736c61628dfe00166d696e6563726166743a73746f6e655f627574746f6e4d0000146d696e6563726166743a647261676f6e5f6567677a0000176d696e6563726166743a6c6176615f6361756c64726f6e2eff00286d696e6563726166743a6578706f7365645f646f75626c655f6375745f636f707065725f736c61628ffe001d6d696e6563726166743a6e6f726d616c5f73746f6e655f7374616972734cff00176d696e6563726166743a68617264656e65645f636c6179ac0000196d696e6563726166743a686172645f676c6173735f70616e65be00002f6d696e6563726166743a706f6c69736865645f626c61636b73746f6e655f627269636b5f646f75626c655f736c6162e3fe001d6d696e6563726166743a6372696d736f6e5f646f75626c655f736c6162f6fe001e6d696e6563726166743a77617865645f6578706f7365645f636f70706572a7fe000f6d696e6563726166743a746f7263683200002a6d696e6563726166743a77617865645f6f786964697a65645f6375745f636f707065725f73746169727340fe00186d696e6563726166743a6d75645f627269636b5f77616c6c1ffe00196d696e6563726166743a6472697073746f6e655f626c6f636bc3fe000e6d696e6563726166743a76696e656a00001b6d696e6563726166743a7761727065645f776172745f626c6f636b1dff00126d696e6563726166743a676f6c645f6f72650e0000156d696e6563726166743a73746f6e65637574746572f50000176d696e6563726166743a7761727065645f706c616e6b730dff001b6d696e6563726166743a696e76697369626c655f626564726f636b5f0000156d696e6563726166743a676f6c64656e5f7261696c1b00001e6d696e6563726166743a7761727065645f7374616e64696e675f7369676e05ff00286d696e6563726166743a706f6c69736865645f626c61636b73746f6e655f627269636b5f77616c6ceafe00136d696e6563726166743a77616c6c5f7369676e440000166d696e6563726166743a627269636b5f7374616972736c0000226d696e6563726166743a6f72616e67655f676c617a65645f7465727261636f747461dd0000176d696e6563726166743a656d6572616c645f626c6f636b850000276d696e6563726166743a68656176795f77656967687465645f70726573737572655f706c617465940000176d696e6563726166743a6c696768746e696e675f726f64c8fe00146d696e6563726166743a6d6f73735f626c6f636bc0fe001a6d696e6563726166743a756e64657277617465725f746f726368ef0000196d696e6563726166743a6f636872655f66726f676c6967687429fe00216d696e6563726166743a62726f776e5f676c617a65645f7465727261636f747461e80000196d696e6563726166743a6f786964697a65645f636f70706572a9fe00166d696e6563726166743a6d6f76696e675f626c6f636bfa0000146d696e6563726166743a636f707065725f6f7265c9fe001e6d696e6563726166743a62697263685f70726573737572655f706c61746569ff001a6d696e6563726166743a73616e6473746f6e655f737461697273800000266d696e6563726166743a636176655f76696e65735f626f64795f776974685f6265727269657389fe00186d696e6563726166743a7363756c6b5f636174616c79737434fe001b6d696e6563726166743a62726f776e5f63616e646c655f63616b6546fe00176d696e6563726166743a7075727075725f737461697273cb00001a6d696e6563726166743a6163616369615f77616c6c5f7369676e41ff001d6d696e6563726166743a706f6c69736865645f626c61636b73746f6e65ddfe00126d696e6563726166743a6d7963656c69756d6e0000166d696e6563726166743a73746f6e655f737461697273430000176d696e6563726166743a7761727065645f73746169727301ff001a6d696e6563726166743a6974656d2e776f6f64656e5f646f6f724000001f6d696e6563726166743a7370727563655f70726573737572655f706c61746566ff001a6d696e6563726166743a62697263685f66656e63655f67617465b80000176d696e6563726166743a72656473746f6e655f776972653700000e6d696e6563726166743a6c6176610b0000196d696e6563726166743a626c61636b73746f6e655f77616c6cebfe00196d696e6563726166743a636f72616c5f66616e5f68616e673377ff00176d696e6563726166743a6163616369615f627574746f6e74ff000e6d696e6563726166743a736e6f77500000176d696e6563726166743a6465746563746f725f7261696c1c00001f6d696e6563726166743a7265645f676c617a65645f7465727261636f747461ea0000186d696e6563726166743a7374727563747572655f766f6964d90000146d696e6563726166743a626f6e655f626c6f636bd80000156d696e6563726166743a6c617069735f626c6f636b160000166d696e6563726166743a72656473746f6e655f6f72654900001c6d696e6563726166743a6e65746865725f627269636b5f66656e6365710000186d696e6563726166743a6372696d736f6e5f687970686165d5fe00246d696e6563726166743a706f6c69736865645f626c61636b73746f6e655f737461697273dcfe00106d696e6563726166743a626173616c7416ff00156d696e6563726166743a6469616d6f6e645f6f72653800001c6d696e6563726166743a7761727065645f646f75626c655f736c6162f5fe001a6d696e6563726166743a6a756e676c655f77616c6c5f7369676e43ff00166d696e6563726166743a7363756c6b5f73656e736f72cdfe00186d696e6563726166743a6372696d736f6e5f627574746f6efcfe00146d696e6563726166743a626c61636b73746f6e65effe00176d696e6563726166743a63686f7275735f666c6f776572c800001f6d696e6563726166743a637261636b65645f6e65746865725f627269636b73d1fe001a6d696e6563726166743a706f77657265645f72657065617465725e0000196d696e6563726166743a64656570736c6174655f74696c65737dfe001b6d696e6563726166743a67696c6465645f626c61636b73746f6e65e7fe00236d696e6563726166743a6578706f7365645f6375745f636f707065725f7374616972739dfe00246d696e6563726166743a6d616e67726f76655f70726f706167756c655f68616e67696e6724fe001c6d696e6563726166743a79656c6c6f775f63616e646c655f63616b654efe00146d696e6563726166743a736f756c5f746f726368f4fe00106d696e6563726166743a706f647a6f6cf300001f6d696e6563726166743a64656570736c6174655f74696c655f7374616972737bfe001c6d696e6563726166743a6372696d736f6e5f66656e63655f67617465fefe00126d696e6563726166743a6465616462757368200000146d696e6563726166743a6974656d2e77686561743b00001a6d696e6563726166743a6974656d2e7370727563655f646f6f72c10000156d696e6563726166743a66726f737465645f696365cf0000116d696e6563726166743a626172726965725fff00176d696e6563726166743a71756172747a5f7374616972739c0000146d696e6563726166743a636176655f76696e6573befe001c6d696e6563726166743a73747269707065645f62697263685f6c6f67faff00146d696e6563726166743a6d656c6f6e5f7374656d690000186d696e6563726166743a6372696d736f6e5f706c616e6b730eff00166d696e6563726166743a626f726465725f626c6f636bd40000156d696e6563726166743a7368726f6f6d6c696768741aff00146d696e6563726166743a676c6173735f70616e656600001c6d696e6563726166743a63686973656c65645f64656570736c61746575fe00146d696e6563726166743a6974656d2e736b756c6c9000001b6d696e6563726166743a6461726b6f616b5f77616c6c5f7369676e3fff00146d696e6563726166743a636f616c5f626c6f636bad00001b6d696e6563726166743a7761727065645f66656e63655f67617465fdfe00266d696e6563726166743a77617865645f646f75626c655f6375745f636f707065725f736c61628cfe001b6d696e6563726166743a636f62626c65645f64656570736c61746585fe00136d696e6563726166743a6974656d2e6b656c7076ff00206d696e6563726166743a626c75655f676c617a65645f7465727261636f747461e700000f6d696e6563726166743a7761746572090000176d696e6563726166743a6368656d6963616c5f68656174c000001a6d696e6563726166743a6d75645f627269636b5f73746169727320fe001c6d696e6563726166743a756e706f77657265645f72657065617465725d0000216d696e6563726166743a77686974655f676c617a65645f7465727261636f747461dc0000206d696e6563726166743a73747269707065645f7761727065645f687970686165d3fe00196d696e6563726166743a6163616369615f74726170646f6f726fff00156d696e6563726166743a676c6f775f6c696368656e65fe00156d696e6563726166743a77616c6c5f62616e6e6572b10000166d696e6563726166743a736f756c5f6c616e7465726ef3fe00126d696e6563726166743a6265655f6e65737426ff00176d696e6563726166743a627562626c655f636f6c756d6e60ff00166d696e6563726166743a62697263685f627574746f6e73ff001e6d696e6563726166743a7265696e666f726365645f64656570736c6174652efe00206d696e6563726166743a64656570736c6174655f72656473746f6e655f6f72656dfe00276d696e6563726166743a636f62626c65645f64656570736c6174655f646f75626c655f736c616274fe001d6d696e6563726166743a6974656d2e6e65746865725f7370726f75747312ff00226d696e6563726166743a6d6f7373795f73746f6e655f627269636b5f73746169727351ff00166d696e6563726166743a6372696d736f6e5f7374656d1fff00176d696e6563726166743a7761727065645f687970686165d6fe00166d696e6563726166743a6269675f647269706c656166bdfe001a6d696e6563726166743a73776565745f62657272795f6275736831ff001e6d696e6563726166743a6a756e676c655f7374616e64696e675f7369676e44ff001c6d696e6563726166743a64656570736c6174655f676f6c645f6f72656efe00116d696e6563726166743a6265656869766525ff001a6d696e6563726166743a6974656d2e6a756e676c655f646f6f72c300000f6d696e6563726166743a676c6173731400001c6d696e6563726166743a6578706f7365645f6375745f636f70706572a4fe002b6d696e6563726166743a77617865645f7765617468657265645f6375745f636f707065725f73746169727398fe002c6d696e6563726166743a706f6c69736865645f626c61636b73746f6e655f70726573737572655f706c617465d9fe001e6d696e6563726166743a6163616369615f7374616e64696e675f7369676e42ff001d6d696e6563726166743a73747269707065645f7370727563655f6c6f67fbff00166d696e6563726166743a70756d706b696e5f7374656d680000176d696e6563726166743a7761727065645f6e796c69756d17ff00196d696e6563726166743a7374727563747572655f626c6f636bfc00001a6d696e6563726166743a656e645f627269636b5f7374616972734eff001b6d696e6563726166743a76657264616e745f66726f676c696768742afe00156d696e6563726166743a7761727065645f7374656d1eff00216d696e6563726166743a73747269707065645f6372696d736f6e5f687970686165d4fe000f6d696e6563726166743a636f636f617f0000196d696e6563726166743a7370727563655f74726170646f6f726bff00216d696e6563726166743a7265645f6e65746865725f627269636b5f73746169727348ff00216d696e6563726166743a677265656e5f676c617a65645f7465727261636f747461e90000186d696e6563726166743a62697263685f74726170646f6f726eff00126d696e6563726166743a6974656d2e6265641a00000d6d696e6563726166743a7765621e00001a6d696e6563726166743a656e6368616e74696e675f7461626c657400002e6d696e6563726166743a77617865645f6578706f7365645f646f75626c655f6375745f636f707065725f736c61628bfe00106d696e6563726166743a617a616c6561affe00116d696e6563726166743a6c65637465726e3eff00186d696e6563726166743a62616d626f6f5f7361706c696e675cff00196d696e6563726166743a7374616e64696e675f62616e6e6572b00000116d696e6563726166743a64726f707065727d0000176d696e6563726166743a6a756e676c655f7374616972738800000f6d696e6563726166743a67726173730200001a6d696e6563726166743a62756464696e675f616d657468797374b8fe00226d696e6563726166743a707572706c655f676c617a65645f7465727261636f747461db00001e6d696e6563726166743a7765617468657265645f6375745f636f70706572a3fe00116d696e6563726166743a626564726f636b0700000f6d696e6563726166743a7363756c6b36fe002a6d696e6563726166743a7765617468657265645f646f75626c655f6375745f636f707065725f736c61628efe00146d696e6563726166743a6e65746865727261636b5700000f6d696e6563726166743a616c6c6f77d20000146d696e6563726166743a6974656d2e636861696ee2fe002c6d696e6563726166743a637261636b65645f706f6c69736865645f626c61636b73746f6e655f627269636b73e8fe00156d696e6563726166743a6c69745f6675726e6163653e00001b6d696e6563726166743a707269736d6172696e655f737461697273feff00166d696e6563726166743a6e65746865725f627269636b7000001c6d696e6563726166743a64656570736c6174655f69726f6e5f6f72656ffe00156d696e6563726166743a6974656d2e63616d657261f200001a6d696e6563726166743a77617865645f6375745f636f70706572a1fe00176d696e6563726166743a73706f72655f626c6f73736f6dbffe001f6d696e6563726166743a6372696d736f6e5f7374616e64696e675f7369676e06ff001f6d696e6563726166743a6461726b6f616b5f7374616e64696e675f7369676e40ff00156d696e6563726166743a656d6572616c645f6f72658100001e6d696e6563726166743a73747269707065645f7761727065645f7374656d0fff001b6d696e6563726166743a706f696e7465645f6472697073746f6e65ccfe00176d696e6563726166743a6e657468657272656163746f72f70000206d696e6563726166743a6461726b5f707269736d6172696e655f737461697273fdff00206d696e6563726166743a64656570736c6174655f627269636b5f73746169727377fe00136d696e6563726166743a747269705f77697265840000176d696e6563726166743a6974656d2e6361756c64726f6e760000266d696e6563726166743a636176655f76696e65735f686561645f776974685f6265727269657388fe001b6d696e6563726166743a6461726b5f6f616b5f74726170646f6f726dff001f6d696e6563726166743a77617865645f6f786964697a65645f636f7070657242fe001c6d696e6563726166743a6974656d2e62726577696e675f7374616e64750000146d696e6563726166743a656e645f706f7274616c7700001b6d696e6563726166743a6163616369615f66656e63655f67617465bb0000146d696e6563726166743a6c69745f736d6f6b657239ff00136d696e6563726166743a6c617069735f6f7265150000196d696e6563726166743a7265645f63616e646c655f63616b6544fe00216d696e6563726166743a726570656174696e675f636f6d6d616e645f626c6f636bbc00002f6d696e6563726166743a77617865645f6f786964697a65645f646f75626c655f6375745f636f707065725f736c61623efe001c6d696e6563726166743a706f6c69736865645f64656570736c61746581fe00116d696e6563726166743a6675726e6163653d00001a6d696e6563726166743a616d6574687973745f636c7573746572b7fe00176d696e6563726166743a6372696d736f6e5f66656e636500ff00136d696e6563726166743a64697370656e7365721700001f6d696e6563726166743a64656570736c6174655f6469616d6f6e645f6f72656bfe001c6d696e6563726166743a6974656d2e736f756c5f63616d7066697265defe001a6d696e6563726166743a6c696d655f63616e646c655f63616b654dfe00146d696e6563726166743a67726173735f70617468c600001a6d696e6563726166743a7765617468657265645f636f70706572aafe00106d696e6563726166743a6c61646465724100001e6d696e6563726166743a756e6c69745f72656473746f6e655f746f7263684b0000206d696e6563726166743a677261795f676c617a65645f7465727261636f747461e30000186d696e6563726166743a7261775f69726f6e5f626c6f636b3dfe00146d696e6563726166743a69726f6e5f626c6f636b2a0000106d696e6563726166743a67726176656c0d00002464666636626565622d626666652d343665332d396436662d3738336662623837393538650107312e31382e3332a54da5d538186e3800000000000000000000000000000000000000000000000000000000000000000000000000000000000000002430303030303030302d303030302d303030302d303030302d30303030303030303030303001";
        String str4 = "";
        String str5 = "";
        String str6 = "";
        String str7 = "";
        String str8 = "";
        String str9 = "";

        StringBuffer sb = new StringBuffer();
        String pack = sb.append(str1).append(str2).append(str3).append(str4).append(str5).append(str6).append(str7).append(str8).append(str9).toString();

        BedrockCodecHelper helper = session.getUpstream().getCodecHelper();
        byte[] a = str2bytes(pack);

        ByteBuf buffer = Unpooled.buffer();
        buffer.writeBytes(a);
        StartGamePacket packet = new StartGamePacket();
//
//        StartGameSerializer_v504.INSTANCE.deserialize(buffer, helper, packet);
        System.out.println(buffer.readByte());
        System.out.println(VarInts.readUnsignedInt(buffer));
        this.deserialize(buffer, helper, packet);
        System.out.println(packet);
//        System.out.println(Arrays.toString(packet.getContents()));

    }

    protected void readLevelSettings(ByteBuf buffer, BedrockCodecHelper helper, StartGamePacket packet) {
        packet.setSeed(buffer.readLongLE());
        packet.setSpawnBiomeType(SpawnBiomeType.byId(buffer.readShortLE()));
        packet.setCustomBiomeName(helper.readString(buffer));
        packet.setDimensionId(VarInts.readInt(buffer));
        packet.setGeneratorId(VarInts.readInt(buffer));
        packet.setLevelGameType(GameType.from(VarInts.readInt(buffer)));
        packet.setDifficulty(VarInts.readInt(buffer));
        packet.setDefaultSpawn(helper.readBlockPosition(buffer));
        packet.setAchievementsDisabled(buffer.readBoolean());
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
        VarInts.readInt(buffer);
//        packet.setDefaultPlayerPermission(PLAYER_PERMISSIONS[VarInts.readInt(buffer)]);
        packet.setServerChunkTickRange(buffer.readIntLE());
        packet.setBehaviorPackLocked(buffer.readBoolean());
        packet.setResourcePackLocked(buffer.readBoolean());
        packet.setFromLockedWorldTemplate(buffer.readBoolean());
        packet.setUsingMsaGamertagsOnly(buffer.readBoolean());
        packet.setFromWorldTemplate(buffer.readBoolean());
        packet.setWorldTemplateOptionLocked(buffer.readBoolean());
        packet.setOnlySpawningV1Villagers(buffer.readBoolean());
        packet.setVanillaVersion(helper.readString(buffer));
        packet.setLimitedWorldWidth(buffer.readIntLE());
        packet.setLimitedWorldHeight(buffer.readIntLE());
        packet.setNetherType(buffer.readBoolean());
        packet.setEduSharedUriResource(new EduSharedUriResource(helper.readString(buffer), helper.readString(buffer)));
        packet.setForceExperimentalGameplay((OptionalBoolean)helper.readOptional(buffer, OptionalBoolean.empty(), (buf) -> {
            return OptionalBoolean.of(buf.readBoolean());
        }));
    }
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, StartGamePacket packet) {
        packet.setUniqueEntityId(VarInts.readLong(buffer));
        packet.setRuntimeEntityId(VarInts.readUnsignedLong(buffer));
        packet.setPlayerGameType(GameType.from(VarInts.readInt(buffer)));
        packet.setPlayerPosition(helper.readVector3f(buffer));
        packet.setRotation(helper.readVector2f(buffer));
        this.readLevelSettings(buffer, helper, packet);
        packet.setLevelId(helper.readString(buffer));
        packet.setLevelName(helper.readString(buffer));
        packet.setPremiumWorldTemplateId(helper.readString(buffer));
        packet.setTrial(buffer.readBoolean());
        this.readSyncedPlayerMovementSettings(buffer, packet);
        packet.setCurrentTick(buffer.readLongLE());
        packet.setEnchantmentSeed(VarInts.readInt(buffer));
        helper.readArray(buffer, packet.getBlockProperties(), (buf, packetHelper) -> {
            String name = packetHelper.readString(buf);
            NbtMap properties = (NbtMap)packetHelper.readTag(buf, NbtMap.class);
            return new BlockPropertyData(name, properties);
        });
        helper.readArray(buffer, packet.getItemDefinitions(), (buf, packetHelper) -> {
            String identifier = packetHelper.readString(buf);
            short id = buf.readShortLE();
            boolean componentBased = buf.readBoolean();
            return new SimpleItemDefinition(identifier, id, componentBased);
        });
        packet.setMultiplayerCorrelationId(helper.readString(buffer));
        packet.setInventoriesServerAuthoritative(buffer.readBoolean());
        packet.setServerEngine(helper.readString(buffer));

        packet.setBlockRegistryChecksum(buffer.readLongLE());

//        packet.setPlayerPropertyData(helper.readTag(buffer, NbtMap.class));
//        packet.setBlockRegistryChecksum(buffer.readLongLE());
//        packet.setWorldTemplateId(helper.readUuid(buffer));
    }
    protected void readSyncedPlayerMovementSettings(ByteBuf buffer, StartGamePacket packet) {
        VarInts.readInt(buffer);
        packet.setRewindHistorySize(VarInts.readInt(buffer));
        packet.setServerAuthoritativeBlockBreaking(buffer.readBoolean());
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

    public void authenticate(String username) {
        authenticate(username, "");
    }

    public void authenticate(String username, String password) {
        if (loggedIn) {
            geyser.getLogger().severe(GeyserLocale.getLocaleStringLog("geyser.auth.already_loggedin", username));
            return;
        }

        loggingIn = true;

        // Use a future to prevent timeouts as all the authentication is handled sync
        CompletableFuture.supplyAsync(() -> {
            try {
                if (password != null && !password.isEmpty()) {
                    AuthenticationService authenticationService;
                    if (microsoftAccount) {
                        authenticationService = new MsaAuthenticationService(GeyserImpl.OAUTH_CLIENT_ID);
                    } else {
                        authenticationService = new MojangAuthenticationService();
                    }
                    authenticationService.setUsername(username);
                    authenticationService.setPassword(password);
                    authenticationService.login();

                    GameProfile profile = authenticationService.getSelectedProfile();
                    if (profile == null) {
                        // Java account is offline
                        disconnect(GeyserLocale.getPlayerLocaleString("geyser.network.remote.invalid_account", clientData.getLanguageCode()));
                        return null;
                    }

                    protocol = new MinecraftProtocol(profile, authenticationService.getAccessToken());
                } else {
                    // always replace spaces when using Floodgate,
                    // as usernames with spaces cause issues with Bungeecord's login cycle.
                    // However, this doesn't affect the final username as Floodgate is still in charge of that.
                    // So if you have (for example) replace spaces enabled on Floodgate the spaces will re-appear.
                    String validUsername = username;
                    if (this.remoteServer.authType() == AuthType.FLOODGATE) {
                        validUsername = username.replace(' ', '_');
                    }

                    protocol = new MinecraftProtocol(validUsername);
                }
            } catch (InvalidCredentialsException | IllegalArgumentException e) {
                geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.auth.login.invalid", username));
                disconnect(GeyserLocale.getPlayerLocaleString("geyser.auth.login.invalid.kick", getClientData().getLanguageCode()));
            } catch (RequestException ex) {
                disconnect(ex.getMessage());
            }
            return null;
        }).whenComplete((aVoid, ex) -> {
            if (ex != null) {
                disconnect(ex.toString());
            }
            if (this.closed) {
                if (ex != null) {
                    geyser.getLogger().error("", ex);
                }
                // Client disconnected during the authentication attempt
                return;
            }

            try {
                connectDownstream();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
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
            disableSrvResolving();
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
                if (event.getPacket() instanceof ClientIntentionPacket) {
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

                            encryptedData = cipher.encryptFromString(BedrockData.of(
                                    clientData.getGameVersion(),
                                    authData.name(),
                                    authData.uuid().toString(),
                                    clientData.getDeviceOs().ordinal(),
                                    clientData.getLanguageCode(),
                                    clientData.getUiProfile().ordinal(),
                                    clientData.getCurrentInputMode().ordinal(),
                                    bedrockAddress,
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
                    cause.printStackTrace();
                }

                upstream.disconnect(disconnectMessage);
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
            if (downstream != null) {
                downstream.disconnect(reason);
            } else {
                // Downstream's disconnect will fire an event that prints a log message
                // Otherwise, we print a message here
                String address = geyser.getConfig().isLogPlayerIpAddresses() ? upstream.getAddress().getAddress().toString() : "<IP address withheld>";
                geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.network.disconnect", address, reason));
            }
            if (!upstream.isClosed()) {
                upstream.disconnect(reason);
            }
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
                    sendDownstreamPacket(packet);
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
    public AttributeData adjustSpeed() {
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

        sendDownstreamPacket(useItemPacket);
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
     * For https://github.com/GeyserMC/Geyser/issues/2113 and combating arm ticking activating being delayed in
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
            sendDownstreamPacket(releaseItemPacket);
            playerEntity.setFlag(EntityFlag.BLOCKING, false);
            return true;
        }
        return false;
    }

    public void requestOffhandSwap() {
        ServerboundPlayerActionPacket swapHandsPacket = new ServerboundPlayerActionPacket(PlayerAction.SWAP_HANDS, Vector3i.ZERO,
                Direction.DOWN, 0);
        sendDownstreamPacket(swapHandsPacket);
    }

    /**
     * Will be overwritten for GeyserConnect.
     */
    protected void disableSrvResolving() {
        this.downstream.getSession().setFlag(BuiltinFlags.ATTEMPT_SRV_RESOLVE, false);
    }

    @Override
    public String name() {
        return clientData.getUsername();
    }

    @Override
    public void sendMessage(String message) {
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
        sendDownstreamPacket(new ServerboundChatPacket(message, Instant.now().toEpochMilli(), 0L, null, 0, new BitSet()));
    }

    /**
     * Sends a command to the Java server.
     */
    public void sendCommand(String command) {
        sendDownstreamPacket(new ServerboundChatCommandPacket(command, Instant.now().toEpochMilli(), 0L, Collections.emptyList(), 0, new BitSet()));
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
        startGamePacket.setPlayerGameType(switch (gameMode) {
            case CREATIVE -> GameType.CREATIVE;
            case ADVENTURE -> GameType.ADVENTURE;
            default -> GameType.SURVIVAL;
        });
        startGamePacket.setPlayerPosition(Vector3f.from(0, 69, 0));
        startGamePacket.setRotation(Vector2f.from(0, 0));

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

        startGamePacket.setVanillaVersion("*");
        startGamePacket.setInventoriesServerAuthoritative(true);
        startGamePacket.setServerEngine(""); // Do we want to fill this in?

        startGamePacket.setPlayerPropertyData(NbtMap.EMPTY);
        startGamePacket.setWorldTemplateId(UUID.randomUUID());

        startGamePacket.setChatRestrictionLevel(ChatRestrictionLevel.NONE);

        startGamePacket.setAuthoritativeMovementMode(AuthoritativeMovementMode.CLIENT);
        startGamePacket.setRewindHistorySize(0);
        startGamePacket.setServerAuthoritativeBlockBreaking(false);

        if (GameProtocol.isPre1_20(this)) {
            startGamePacket.getExperiments().add(new ExperimentData("next_major_update", true));
            startGamePacket.getExperiments().add(new ExperimentData("sniffer", true));
        }

        upstream.sendPacket(startGamePacket);
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
        if (protocol.getState().equals(ProtocolState.GAME) || packet.getClass() == ServerboundCustomQueryPacket.class) {
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
        if (GameProtocol.supports1_19_10(this)) {
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
            if (canFly || spectator) {
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
                    sendDownstreamPacket(abilitiesPacket);
                }
                abilities.add(Ability.FLYING);
            }

            if (spectator) {
                abilities.add(Ability.NO_CLIP);
            }

            // https://github.com/GeyserMC/Geyser/issues/3769 Setting Spectator mode ability layer
            if (spectator) {
                abilityLayer.setLayerType(AbilityLayer.Type.SPECTATOR);
            } else {
                abilityLayer.setLayerType(AbilityLayer.Type.BASE);
            }
            abilityLayer.setFlySpeed(flySpeed);
            // https://github.com/GeyserMC/Geyser/issues/3139 as of 1.19.10
            abilityLayer.setWalkSpeed(walkSpeed == 0f ? 0.01f : walkSpeed);
            Collections.addAll(abilityLayer.getAbilitiesSet(), USED_ABILITIES);

            updateAbilitiesPacket.getAbilityLayers().add(abilityLayer);
            sendUpstreamPacket(updateAbilitiesPacket);
            return;
        }

        AdventureSettingsPacket adventureSettingsPacket = new AdventureSettingsPacket();
        adventureSettingsPacket.setUniqueEntityId(bedrockId);
        adventureSettingsPacket.setCommandPermission(commandPermission);
        adventureSettingsPacket.setPlayerPermission(playerPermission);

        Set<AdventureSetting> flags = adventureSettingsPacket.getSettings();
        if (canFly || spectator) {
            flags.add(AdventureSetting.MAY_FLY);
        }

        if (flying || spectator) {
            if (spectator && !flying) {
                // We're "flying locked" in this gamemode
                flying = true;
                ServerboundPlayerAbilitiesPacket abilitiesPacket = new ServerboundPlayerAbilitiesPacket(true);
                sendDownstreamPacket(abilitiesPacket);
            }
            flags.add(AdventureSetting.FLYING);
        }

        if (worldImmutable) {
            flags.add(AdventureSetting.WORLD_IMMUTABLE);
        }

        if (spectator) {
            flags.add(AdventureSetting.NO_CLIP);
        }

        flags.add(AdventureSetting.AUTO_JUMP);

        sendUpstreamPacket(adventureSettingsPacket);
    }

    private int getRenderDistance() {
        if (clientRenderDistance != -1) {
            // The client has sent a render distance
            return clientRenderDistance;
        }
        return serverRenderDistance;
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
    public String bedrockUsername() {
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
    public String xuid() {
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
        if (!this.geyser.getConfig().isCommandSuggestions()) {
            return;
        }
        UpdateSoftEnumPacket packet = new UpdateSoftEnumPacket();
        packet.setType(type);
        packet.setSoftEnum(new CommandEnumData(name, Collections.singletonMap(enums, Collections.emptySet()), true));
//        sendUpstreamPacket(packet); //对于我们来说，目前不需要改包
    }
}
