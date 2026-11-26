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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.Builder;
import net.kyori.adventure.text.serializer.json.JSONOptions;
import net.kyori.adventure.text.serializer.json.LegacyHoverEventSerializer;
import net.kyori.option.OptionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Works around Adventure treating every {@code click_event.payload} as a string.
 * Newer Java components may contain an object payload for click actions such as
 * {@code show_dialog}. Adventure cannot represent those actions yet, but the
 * unsupported event should not prevent the rest of the component from loading.
 */
final class LenientGsonComponentSerializer implements InvocationHandler {

    private static final String CLICK_EVENT_SNAKE = "click_event";
    private static final String CLICK_EVENT_CAMEL = "clickEvent";
    private static final String PAYLOAD = "payload";

    private final GsonComponentSerializer delegate;

    private LenientGsonComponentSerializer(GsonComponentSerializer delegate) {
        this.delegate = delegate;
    }

    static GsonComponentSerializer wrap(GsonComponentSerializer delegate) {
        // Have the JVM implement the runtime interface so the Adventure 4 bridge to
        // Buildable.Builder is only present when that interface actually exists.
        return (GsonComponentSerializer) Proxy.newProxyInstance(GsonComponentSerializer.class.getClassLoader(),
                new Class<?>[]{GsonComponentSerializer.class}, new LenientGsonComponentSerializer(delegate));
    }

    @Override
    public Object invoke(Object proxy, Method method, @Nullable Object[] args) throws Throwable {
        return switch (method.getName()) {
            case "deserialize" -> deserialize((String) args[0]);
            case "deserializeOr" -> args[0] == null ? args[1] : deserialize((String) args[0]);
            case "deserializeFromTree" -> deserializeFromTree((JsonElement) args[0]);
            case "serialize" -> delegate.serialize((Component) args[0]);
            case "serializeToTree" -> delegate.serializeToTree((Component) args[0]);
            case "serializer" -> delegate.serializer();
            case "populator" -> delegate.populator();
            case "toBuilder" -> new LenientBuilder(AdventureBuilder.copy(delegate, GsonComponentSerializer.class, Builder.class));
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            default -> invokeDelegate(method, args);
        };
    }

    private Object invokeDelegate(Method method, @Nullable Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private Component deserialize(String input) {
        try {
            return delegate.deserialize(input);
        } catch (JsonParseException exception) {
            JsonElement tree = delegate.serializer().fromJson(input, JsonElement.class);
            return deserializeWithoutObjectClickEventPayloads(tree, exception);
        }
    }

    private Component deserializeFromTree(JsonElement input) {
        try {
            return delegate.deserializeFromTree(input);
        } catch (JsonParseException exception) {
            return deserializeWithoutObjectClickEventPayloads(input, exception);
        }
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
            return wrap(delegate.build());
        }
    }
}
