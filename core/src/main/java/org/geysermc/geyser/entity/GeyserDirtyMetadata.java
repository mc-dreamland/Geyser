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

package org.geysermc.geyser.entity;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;

import java.util.Map;

/**
 * A wrapper for temporarily storing entity metadata that will be sent to Bedrock.
 */
public final class GeyserDirtyMetadata {
    private final Map<EntityDataType<?>, Object> dirtyMetadata = new Object2ObjectLinkedOpenHashMap<>();
    private final Map<EntityDataType<?>, Object> currentMetadata = new Object2ObjectLinkedOpenHashMap<>();

    public <T> void put(EntityDataType<T> entityData, T value) {
        dirtyMetadata.put(entityData, value);
        currentMetadata.put(entityData, value);
    }

    /**
     * Applies the contents of the dirty metadata into the input and clears the contents of our map.
     */
    public void apply(EntityDataMap map) {
        map.putAll(dirtyMetadata);
        dirtyMetadata.clear();
    }

    /**
     * Marks the complete current metadata state for the next packet. This is used when a
     * client-side actor is recreated and therefore no longer has any previously sent state.
     */
    public void markAllDirty() {
        dirtyMetadata.putAll(currentMetadata);
    }

    /**
     * Clears both pending changes and the retained current state before an entity is reset.
     */
    public void clear() {
        dirtyMetadata.clear();
        currentMetadata.clear();
    }

    public boolean hasEntries() {
        return !dirtyMetadata.isEmpty();
    }

    /**
     * Intended for testing purposes only
     */
    public <T> T get(EntityDataType<T> entityData) {
        //noinspection unchecked
        return (T) dirtyMetadata.get(entityData);
    }

    @Override
    public String toString() {
        return dirtyMetadata.toString();
    }
}
