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
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.command.defaults;

import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.util.TriState;
import org.geysermc.geyser.command.GeyserCommand;
import org.geysermc.geyser.command.GeyserCommandSource;
import org.geysermc.geyser.session.cache.ClientBlobCache;
import org.geysermc.geyser.translator.protocol.java.level.JavaLevelChunkWithLightTranslator;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

import java.util.Locale;

public final class ChunkCacheCommand extends GeyserCommand {
    private final GeyserImpl geyser;

    public ChunkCacheCommand(GeyserImpl geyser, String name, String description, String permission) {
        super(name, description, permission, TriState.NOT_SET);
        this.geyser = geyser;
    }

    @Override
    public void register(CommandManager<GeyserCommandSource> manager) {
        manager.command(baseBuilder(manager).handler(this::execute));
        manager.command(baseBuilder(manager).literal("status", "state").handler(this::execute));

        registerToggle(manager, "blob", null, Target.BLOB, true);
        registerToggle(manager, "blob", null, Target.BLOB, false);
        registerToggle(manager, "cache", "chunk", Target.CACHE, true);
        registerToggle(manager, "cache", "chunk", Target.CACHE, false);
        registerToggle(manager, "all", null, Target.ALL, true);
        registerToggle(manager, "all", null, Target.ALL, false);
    }

    private void registerToggle(CommandManager<GeyserCommandSource> manager, String targetName,
                                String targetAlias, Target target, boolean enabled) {
        var builder = targetAlias == null
                ? baseBuilder(manager).literal(targetName)
                : baseBuilder(manager).literal(targetName, targetAlias);
        if (enabled) {
            manager.command(builder.literal("on", "enable").handler(context -> setEnabled(context, target, true)));
        } else {
            manager.command(builder.literal("off", "disable").handler(context -> setEnabled(context, target, false)));
        }
    }

    @Override
    public void execute(CommandContext<GeyserCommandSource> context) {
        sendStatus(context.sender());
    }

    private void setEnabled(CommandContext<GeyserCommandSource> context, Target target, boolean enabled) {
        boolean cacheChanged = target.controlsCache()
                && JavaLevelChunkWithLightTranslator.isGlobalChunkTranslationCacheEnabled() != enabled;
        boolean blobChanged = target.controlsBlob() && ClientBlobCache.isGloballyEnabled() != enabled;

        if (cacheChanged) {
            JavaLevelChunkWithLightTranslator.setGlobalChunkTranslationCacheEnabled(enabled);
        }
        if (blobChanged) {
            ClientBlobCache.setGloballyEnabled(enabled);
            // Cached entries created while Blob Cache was disabled do not contain persistent blob payloads.
            // Clear both variants so the next hot translation rebuilds entries for the new mode.
            JavaLevelChunkWithLightTranslator.clearGlobalChunkTranslationCache();
        }

        GeyserCommandSource source = context.sender();
        if (!cacheChanged && !blobChanged) {
            source.sendMessage("Chunk 缓存状态没有变化。");
        } else {
            source.sendMessage("已" + (enabled ? "开启" : "关闭") + " " + target.displayName() + "。");
            this.geyser.getLogger().info("[ChunkCacheControl] source=" + source.name()
                    + " target=" + target.name().toLowerCase(Locale.ROOT) + " enabled=" + enabled);
        }
        sendStatus(source);
    }

    private void sendStatus(GeyserCommandSource source) {
        boolean cacheEnabled = JavaLevelChunkWithLightTranslator.isGlobalChunkTranslationCacheEnabled();
        boolean blobEnabled = ClientBlobCache.isGloballyEnabled();
        source.sendMessage("Chunk 转义缓存: " + state(cacheEnabled)
                + "，entries=" + JavaLevelChunkWithLightTranslator.globalChunkTranslationCacheSize());
        source.sendMessage("Blob Cache: " + state(blobEnabled)
                + "，entries=" + ClientBlobCache.globalBlobCount());
        if (!cacheEnabled && blobEnabled) {
            source.sendMessage("Blob Cache 会继续计算热点 Key，但不会复用 Chunk 转义结果。");
        }
    }

    private static String state(boolean enabled) {
        return enabled ? "开启" : "关闭";
    }

    private enum Target {
        BLOB("Blob Cache", false, true),
        CACHE("Chunk 转义缓存", true, false),
        ALL("Chunk 转义缓存和 Blob Cache", true, true);

        private final String displayName;
        private final boolean controlsCache;
        private final boolean controlsBlob;

        Target(String displayName, boolean controlsCache, boolean controlsBlob) {
            this.displayName = displayName;
            this.controlsCache = controlsCache;
            this.controlsBlob = controlsBlob;
        }

        private String displayName() {
            return this.displayName;
        }

        private boolean controlsCache() {
            return this.controlsCache;
        }

        private boolean controlsBlob() {
            return this.controlsBlob;
        }
    }
}
