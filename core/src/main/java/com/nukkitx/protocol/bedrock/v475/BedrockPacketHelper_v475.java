package com.nukkitx.protocol.bedrock.v475;

import com.nukkitx.protocol.bedrock.data.LevelEventType;
import com.nukkitx.protocol.bedrock.data.SoundEvent;
import com.nukkitx.protocol.bedrock.data.skin.*;
import com.nukkitx.protocol.bedrock.v471.BedrockPacketHelper_v471;
import io.netty.buffer.ByteBuf;

import java.util.List;

import static java.util.Objects.requireNonNull;

public class BedrockPacketHelper_v475 extends BedrockPacketHelper_v471 {
    public static final BedrockPacketHelper_v475 INSTANCE = new BedrockPacketHelper_v475();

    @Override
    protected void registerLevelEvents() {
        super.registerLevelEvents();
        this.addLevelEvent(9801, LevelEventType.SLEEPING_PLAYERS);
    }

    @Override
    protected void registerSoundEvents() {
        super.registerSoundEvents();
        this.addSoundEvent(371, SoundEvent.RECORD_OTHERSIDE);
        this.addSoundEvent(372, SoundEvent.UNDEFINED);
    }


    @SuppressWarnings("DuplicatedCode")
    @Override
    public void writeSkin(ByteBuf buffer, SerializedSkin skin) {
        requireNonNull(skin, "Skin is null");

        this.writeString(buffer, skin.getSkinId());
        this.writeString(buffer, skin.getPlayFabId()); // new for v428
        this.writeString(buffer, skin.getSkinResourcePatch());
        this.writeImage(buffer, skin.getSkinData());

        List<AnimationData> animations = skin.getAnimations();
        buffer.writeIntLE(animations.size());
        for (AnimationData animation : animations) {
            this.writeAnimationData(buffer, animation);
        }

        this.writeImage(buffer, skin.getCapeData());
        this.writeString(buffer, skin.getGeometryData());
        this.writeString(buffer, skin.getGeometryDataEngineVersion());
        this.writeString(buffer, skin.getAnimationData());
        this.writeString(buffer, skin.getCapeId());
        this.writeString(buffer, skin.getFullSkinId());
        this.writeString(buffer, skin.getArmSize());
        this.writeString(buffer, skin.getSkinColor());
        List<PersonaPieceData> pieces = skin.getPersonaPieces();
        buffer.writeIntLE(pieces.size());
        for (PersonaPieceData piece : pieces) {
            this.writeString(buffer, piece.getId());
            this.writeString(buffer, piece.getType());
            this.writeString(buffer, piece.getPackId());
            buffer.writeBoolean(piece.isDefault());
            this.writeString(buffer, piece.getProductId());
        }

        List<PersonaPieceTintData> tints = skin.getTintColors();
        buffer.writeIntLE(tints.size());
        for (PersonaPieceTintData tint : tints) {
            this.writeString(buffer, tint.getType());
            List<String> colors = tint.getColors();
            buffer.writeIntLE(colors.size());
            for (String color : colors) {
                this.writeString(buffer, color);
            }
        }

        // TODO 不知道为什么，这个地方需要这样写，皮肤才能正常显示。。。待后续处理。
//        System.out.println(skin.isPremium());
//        System.out.println(skin.isPersona());
//        buffer.writeBoolean(skin.isPremium());
//        buffer.writeBoolean(skin.isPersona());
        buffer.writeBoolean(false);
        buffer.writeBoolean(true);
        buffer.writeBoolean(skin.isCapeOnClassic());
        buffer.writeBoolean(skin.isPrimaryUser());
    }


    @Override
    public void writeImage(ByteBuf buffer, ImageData image) {
        requireNonNull(buffer, "buffer is null");
        requireNonNull(image, "image is null");

        buffer.writeIntLE(image.getWidth());
        buffer.writeIntLE(image.getHeight());
        writeByteArray(buffer, image.getImage());
    }
}
