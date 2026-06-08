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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.util.TriState;
import org.geysermc.geyser.command.GeyserCommand;
import org.geysermc.geyser.command.GeyserCommandSource;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.auth.BedrockClientData;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jose4j.base64url.Base64;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.incendo.cloud.parser.standard.StringParser.stringParser;

public class GetSkinCommand extends GeyserCommand {
    private static final String TARGET = "target";

    private final GeyserImpl geyser;

    public GetSkinCommand(GeyserImpl geyser, String name, String description, String permission) {
        super(name, description, permission, TriState.NOT_SET);
        this.geyser = geyser;
    }

    @Override
    public void register(CommandManager<GeyserCommandSource> manager) {
        manager.command(baseBuilder(manager)
            .required(TARGET, stringParser())
            .handler(this::execute));
    }

    @Override
    public void execute(CommandContext<GeyserCommandSource> context) {
        GeyserCommandSource source = context.sender();
        String target = context.get(TARGET);
        UUID uuid = UUID.fromString(target);
        GeyserSession geyserSession = geyser.getSessionManager().getSessions().get(uuid);
        if (geyserSession == null) {
            source.sendMessage(Component.text("玩家不存在或不在线!", NamedTextColor.RED));
            return;
        }
        BedrockClientData clientData = geyserSession.getClientData();
        source.sendMessage(GeyserImpl.GSON.toJson(Map.of("skinId", clientData.getSkinId(), "skinData", Base64.encode(clientData.getSkinData()),
            "SkinImageHeight", clientData.getSkinImageHeight(), "SkinImageWidth", clientData.getSkinImageWidth(),
            "geometryName", new String(clientData.getGeometryName(), StandardCharsets.UTF_8),
            "geometryData", Base64.encode(clientData.getGeometryData()))));
    }
}
