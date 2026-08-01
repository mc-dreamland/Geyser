/*
 * Copyright (c) 2019-2026 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.translator.text;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.json.JSONOptions;
import net.kyori.adventure.text.serializer.json.LegacyHoverEventSerializer;
import net.kyori.option.OptionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Works around Adventure treating every {@code click_event.payload} as a string.
 * Newer Java components may contain an object payload for click actions such as
 * {@code show_dialog}. Adventure cannot represent those actions yet, but the
 * unsupported event should not prevent the rest of the component from loading.
 */
final class LenientGsonComponentSerializer implements GsonComponentSerializer {

    private static final String CLICK_EVENT_SNAKE = "click_event";
    private static final String CLICK_EVENT_CAMEL = "clickEvent";
    private static final String PAYLOAD = "payload";

    private final GsonComponentSerializer delegate;

    LenientGsonComponentSerializer(GsonComponentSerializer delegate) {
        this.delegate = delegate;
    }

    @Override
    public @NotNull Gson serializer() {
        return delegate.serializer();
    }

    @Override
    public @NotNull UnaryOperator<GsonBuilder> populator() {
        return delegate.populator();
    }

    @Override
    public @NotNull Component deserialize(@NotNull String input) {
        try {
            return delegate.deserialize(input);
        } catch (JsonParseException exception) {
            JsonElement tree = delegate.serializer().fromJson(input, JsonElement.class);
            return deserializeWithoutObjectClickEventPayloads(tree, exception);
        }
    }

    @Override
    public @Nullable Component deserializeOr(@Nullable String input, @Nullable Component fallback) {
        return input == null ? fallback : deserialize(input);
    }

    @Override
    public @NotNull Component deserializeFromTree(@NotNull JsonElement input) {
        try {
            return delegate.deserializeFromTree(input);
        } catch (JsonParseException exception) {
            return deserializeWithoutObjectClickEventPayloads(input, exception);
        }
    }

    @Override
    public @NotNull String serialize(@NotNull Component component) {
        return delegate.serialize(component);
    }

    @Override
    public @NotNull JsonElement serializeToTree(@NotNull Component component) {
        return delegate.serializeToTree(component);
    }

    @Override
    public @NotNull Builder toBuilder() {
        return new LenientBuilder(delegate.toBuilder());
    }

    private Component deserializeWithoutObjectClickEventPayloads(JsonElement input, JsonParseException originalException) {
        if (!hasObjectClickEventPayload(input)) {
            throw originalException;
        }
        return delegate.deserializeFromTree(removeObjectClickEventPayloads(input));
    }

    private static boolean hasObjectClickEventPayload(JsonElement element) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (hasObjectClickEventPayload(child)) {
                    return true;
                }
            }
            return false;
        }

        if (!element.isJsonObject()) {
            return false;
        }

        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if ((CLICK_EVENT_SNAKE.equals(entry.getKey()) || CLICK_EVENT_CAMEL.equals(entry.getKey())) && value.isJsonObject()) {
                JsonElement payload = value.getAsJsonObject().get(PAYLOAD);
                if (payload != null && (payload.isJsonObject() || payload.isJsonArray())) {
                    return true;
                }
            }
            if (hasObjectClickEventPayload(value)) {
                return true;
            }
        }
        return false;
    }

    private static JsonElement removeObjectClickEventPayloads(JsonElement element) {
        if (element.isJsonArray()) {
            JsonArray copy = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                copy.add(removeObjectClickEventPayloads(child));
            }
            return copy;
        }

        if (!element.isJsonObject()) {
            return element;
        }

        JsonObject copy = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if ((CLICK_EVENT_SNAKE.equals(key) || CLICK_EVENT_CAMEL.equals(key)) && value.isJsonObject()) {
                copy.add(key, removeObjectPayload(value.getAsJsonObject()));
            } else {
                copy.add(key, removeObjectClickEventPayloads(value));
            }
        }
        return copy;
    }

    private static JsonObject removeObjectPayload(JsonObject clickEvent) {
        JsonObject copy = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : clickEvent.entrySet()) {
            JsonElement value = entry.getValue();
            if (PAYLOAD.equals(entry.getKey()) && (value.isJsonObject() || value.isJsonArray())) {
                continue;
            }
            copy.add(entry.getKey(), removeObjectClickEventPayloads(value));
        }
        return copy;
    }

    private static final class LenientBuilder implements Builder {

        private final Builder delegate;

        private LenientBuilder(Builder delegate) {
            this.delegate = delegate;
        }

        @Override
        public @NotNull Builder options(@NotNull OptionState flags) {
            delegate.options(flags);
            return this;
        }

        @Override
        public @NotNull Builder editOptions(@NotNull Consumer<OptionState.Builder> optionEditor) {
            delegate.editOptions(optionEditor);
            return this;
        }

        @Override
        public @NotNull Builder downsampleColors() {
            delegate.editOptions(options -> options.value(JSONOptions.EMIT_RGB, false));
            return this;
        }

        @Override
        public @NotNull Builder legacyHoverEventSerializer(@Nullable LegacyHoverEventSerializer serializer) {
            delegate.legacyHoverEventSerializer(serializer);
            return this;
        }

        @Override
        public @NotNull Builder emitLegacyHoverEvent() {
            delegate.editOptions(options -> options.value(JSONOptions.EMIT_HOVER_EVENT_TYPE, JSONOptions.HoverEventValueMode.ALL));
            return this;
        }

        @Override
        public @NotNull GsonComponentSerializer build() {
            return new LenientGsonComponentSerializer(delegate.build());
        }
    }
}
