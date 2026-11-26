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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/**
 * Adventure 4 inherits {@code toBuilder()} from {@code Buildable}; Adventure 5
 * declares it directly with a concrete builder return type. Resolve the runtime
 * signature while keeping the installed serializer or flattener's settings.
 */
final class AdventureBuilder {
    private static final ClassValue<MethodHandle> TO_BUILDER = new ClassValue<>() {
        @Override
        protected MethodHandle computeValue(Class<?> type) {
            try {
                return MethodHandles.publicLookup().unreflect(type.getMethod("toBuilder"));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot resolve toBuilder on " + type.getName(), e);
            }
        }
    };

    private AdventureBuilder() {
    }

    static <T, B> B copy(T source, Class<T> type, Class<B> builderType) {
        try {
            return builderType.cast(TO_BUILDER.get(type).invoke(source));
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Cannot copy builder for " + type.getName(), e);
        }
    }
}
