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

package org.geysermc.geyser.util;

import com.github.steveice10.mc.protocol.data.game.entity.Effect;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.nukkitx.math.vector.Vector2i;
import com.nukkitx.math.vector.Vector3f;
import com.nukkitx.math.vector.Vector3i;
import com.nukkitx.protocol.bedrock.packet.ChangeDimensionPacket;
import com.nukkitx.protocol.bedrock.packet.ChunkRadiusUpdatedPacket;
import com.nukkitx.protocol.bedrock.packet.MobEffectPacket;
import com.nukkitx.protocol.bedrock.packet.StopSoundPacket;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.level.BedrockDimension;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.ChunkCache;

import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class DimensionUtils {

    // Changes if the above-bedrock Nether building workaround is applied
    private static int BEDROCK_NETHER_ID = 1;

    /**
     * String reference to vanilla Java overworld dimension identifier
     */
    public static final String OVERWORLD = "minecraft:overworld";
    /**
     * String reference to vanilla Java nether dimension identifier
     */
    public static final String NETHER = "minecraft:the_nether";
    /**
     * String reference to vanilla Java end dimension identifier
     */
    public static final String THE_END = "minecraft:the_end";

    public static void clearLoadedChunks(GeyserSession session) {
//        session.setStartClearChunkCache(false);
//        HashSet<Vector2i> vector2is = new HashSet<>(session.getOldLoadedChunkCache());
//        for (Vector2i vector2i : vector2is) {
//            ChunkUtils.sendEmptyChunk(session, vector2i.getX(), vector2i.getY(), false);
//            session.getOldLoadedChunkCache().remove(vector2i);
//        }

        try {
            session.getSkullCache().getSkulls().forEach((vector3i, skull) -> {
                session.getSkullCache().removeSkull(vector3i);
            });
        } catch (Exception ignored) {
        }

        session.getSkullCache().getSkulls().forEach((vector3i, skull) -> {
            session.getSkullCache().removeSkull(vector3i);
        });
    }

    public static void switchDimension(GeyserSession session, String javaDimension, boolean changeWorld) {
        int bedrockDimension = javaToBedrock(javaDimension);
        int previousDimension = javaToBedrock(session.getDimension());


        if (session.isNewVersion() || session.isQuickSwitch() || changeWorld) {
            clearLoadedChunks(session);
        }

//        if (session.isNewVersion() || session.isQuickSwitch()) {
//            clearLoadedChunks(session);
//        }
//        if (session.isQuickSwitch() && !session.isStartClearChunkCache()) {
//            GeyserImpl.getInstance().getScheduledThread().schedule(new Runnable() {
//                @Override
//                public void run() {
//                    clearLoadedChunks(session);
//                }
//            }, 1000, TimeUnit.MILLISECONDS);
//            session.setStartClearChunkCache(true);
//        }

        Entity player = session.getPlayerEntity();

        session.getChunkCache().clear();
        session.getEntityCache().removeAllEntities();
        session.getItemFrameCache().clear();
        if (session.getLecternCache() != null) {
            session.getLecternCache().clear();
        }
        session.getLodestoneCache().clear();
        session.getPistonCache().clear();
        session.getSkullCache().clear();

        if (session.getServerRenderDistance() > 47 && !session.isEmulatePost1_13Logic()) {
            // The server-sided view distance wasn't a thing until Minecraft Java 1.14
            // So ViaVersion compensates by sending a "view distance" of 64
            // That's fine, except when the actual view distance sent from the server is five chunks
            // The client locks up when switching dimensions, expecting more chunks than it's getting
            // To solve this, we cap at 32 unless we know that the render distance actually exceeds 32
            // 47 is the Bedrock equivalent of 32
            // Also, as of 1.19: PS4 crashes with a ChunkRadiusUpdatedPacket too large
            session.getGeyser().getLogger().debug("Applying dimension switching workaround for Bedrock render distance of "
                    + session.getServerRenderDistance());
            ChunkRadiusUpdatedPacket chunkRadiusUpdatedPacket = new ChunkRadiusUpdatedPacket();
            chunkRadiusUpdatedPacket.setRadius(47);
            session.sendUpstreamPacket(chunkRadiusUpdatedPacket);
            // Will be re-adjusted on spawn
        }

        Vector3f pos = Vector3f.from(0, 64, 0);

        ChangeDimensionPacket changeDimensionPacket = new ChangeDimensionPacket();
        changeDimensionPacket.setDimension(bedrockDimension);
        changeDimensionPacket.setRespawn(true);
        changeDimensionPacket.setPosition(pos);
//        if (!session.isQuickSwitch() || changeWorld) {
        if (!session.isNewVersion() && !session.isQuickSwitch() || changeWorld) {
//        if (!session.isQuickSwitch()) {
            session.sendUpstreamPacket(changeDimensionPacket);
            session.setDimension(javaDimension);
        }
        setBedrockDimension(session, javaDimension);
        player.setPosition(pos);
        session.setSpawned(false);
        session.setLastChunkPosition(null);

        Set<Effect> entityEffects = session.getEffectCache().getEntityEffects();
        for (Effect effect : entityEffects) {
            MobEffectPacket mobEffectPacket = new MobEffectPacket();
            mobEffectPacket.setEvent(MobEffectPacket.Event.REMOVE);
            mobEffectPacket.setRuntimeEntityId(player.getGeyserId());
            mobEffectPacket.setEffectId(EntityUtils.toBedrockEffectId(effect));
            session.sendUpstreamPacket(mobEffectPacket);
        }
        // Effects are re-sent from server
        entityEffects.clear();

        if (!session.isNewVersion() && !session.isQuickSwitch() || changeWorld) {
//        if (!session.isQuickSwitch()) {
            //let java server handle portal travel sound
            StopSoundPacket stopSoundPacket = new StopSoundPacket();
            stopSoundPacket.setStoppingAllSound(true);
            stopSoundPacket.setSoundName("");
            session.sendUpstreamPacket(stopSoundPacket);
        }

        // TODO - fix this hack of a fix by sending the final dimension switching logic after sections have been sent.
        // The client wants sections sent to it before it can successfully respawn.
//        ChunkUtils.sendEmptyChunks(session, Vector3i.from(0, 64, 0), 3, true);
        if (!session.isNewVersion() && !session.isQuickSwitch() || changeWorld) {
            ChunkUtils.sendEmptyChunks(session, player.getPosition().toInt(), 3, true);
        }
//        if (!session.isQuickSwitch()) ChunkUtils.sendEmptyChunks(session, player.getPosition().toInt(), 3, true);
//        if (!session.isQuickSwitch() && changeWorld) ChunkUtils.sendEmptyChunks(session, player.getPosition().toInt(), 3, true);
//        ChunkUtils.sendEmptyChunks(session, player.getPosition().toInt(), 3, true);

        // If the bedrock nether height workaround is enabled, meaning the client is told it's in the end dimension,
        // we check if the player is entering the nether and apply the nether fog to fake the fact that the client
        // thinks they are in the end dimension.
        if (!session.isNewVersion() && !session.isQuickSwitch() || changeWorld) {
//        if (!session.isQuickSwitch()) {
            if (BEDROCK_NETHER_ID == 2) {
                if (NETHER.equals(javaDimension)) {
                    session.sendFog("minecraft:fog_hell");
                } else if (previousDimension == BEDROCK_NETHER_ID) {
                    session.removeFog("minecraft:fog_hell");
                }
            }
        }
    }

    public static void setBedrockDimension(GeyserSession session, String javaDimension) {
        session.getChunkCache().setBedrockDimension(switch (javaDimension) {
            case DimensionUtils.THE_END -> BedrockDimension.THE_END;
            case DimensionUtils.NETHER -> DimensionUtils.isCustomBedrockNetherId() ? BedrockDimension.THE_END : BedrockDimension.THE_NETHER;
            default -> BedrockDimension.OVERWORLD;
        });
    }

    public static int javaToBedrock(BedrockDimension dimension) {
        if (dimension == BedrockDimension.THE_NETHER) {
            return BEDROCK_NETHER_ID;
        } else if (dimension == BedrockDimension.THE_END) {
            return 2;
        } else {
            return 0;
        }
    }

    /**
     * Map the Java edition dimension IDs to Bedrock edition
     *
     * @param javaDimension Dimension ID to convert
     * @return Converted Bedrock edition dimension ID
     */
    public static int javaToBedrock(String javaDimension) {
        return switch (javaDimension) {
            case NETHER -> BEDROCK_NETHER_ID;
            case THE_END -> 2;
            default -> 0;
        };
    }

    /**
     * The Nether dimension in Bedrock does not permit building above Y128 - the Bedrock above the dimension.
     * This workaround sets the Nether as the End dimension to ignore this limit.
     *
     * @param isAboveNetherBedrockBuilding true if we should apply The End workaround
     */
    public static void changeBedrockNetherId(boolean isAboveNetherBedrockBuilding) {
        // Change dimension ID to the End to allow for building above Bedrock
        BEDROCK_NETHER_ID = isAboveNetherBedrockBuilding ? 2 : 1;
    }

    /**
     * Gets the fake, temporary dimension we send clients to so we aren't switching to the same dimension without an additional
     * dimension switch.
     *
     * @param currentDimension the current dimension of the player
     * @param newDimension the new dimension that the player will be transferred to
     * @return the fake dimension to transfer to
     */
    public static String getTemporaryDimension(String currentDimension, String newDimension) {
        if (BEDROCK_NETHER_ID == 2) {
            // Prevents rare instances of Bedrock locking up
            return javaToBedrock(newDimension) == 2 ? OVERWORLD : NETHER;
        }
        return currentDimension.equals(OVERWORLD) ? NETHER : OVERWORLD;
    }

    public static boolean isCustomBedrockNetherId() {
        return BEDROCK_NETHER_ID == 2;
    }
}
