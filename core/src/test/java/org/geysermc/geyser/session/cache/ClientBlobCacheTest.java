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

package org.geysermc.geyser.session.cache;

import org.cloudburstmc.protocol.bedrock.packet.ClientCacheBlobStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.session.GeyserSession;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientBlobCacheTest {

    @Test
    void matchesXxHash64SeedZeroVectors() {
        assertHash("", "ef46db3751d8e999");
        assertHash("a", "d24ec4f1a98c6e5b");
        assertHash("abc", "44bc2cf5ad770999");
        assertHash("123456789", "8cb841db40e6ae83");
        assertHash("abcdefghijklmnopqrstuvwxyz0123456789", "64f23ecf1609b766");
    }

    @Test
    void hashesOnlyTheRequestedSlice() {
        byte[] data = "__abcdefghijklmnopqrstuvwxyz0123456789__".getBytes(StandardCharsets.UTF_8);
        long actual = ClientBlobCache.xxHash64(data, 2, data.length - 4);
        assertEquals(Long.parseUnsignedLong("64f23ecf1609b766", 16), actual);
    }

    @Test
    void doesNotSendGloballyCachedBlobThatWasNotReferencedToSession() {
        boolean originallyEnabled = ClientBlobCache.isGloballyEnabled();
        ClientBlobCache.setGloballyEnabled(true);
        LevelChunkPacket referencedChunk = new LevelChunkPacket();
        try {
            GeyserSession referencedSession = mockSession("referenced");
            ClientBlobCache referencedCache = new ClientBlobCache(referencedSession);
            referencedCache.setClientSupported(true);

            byte[] payload = {0};
            byte[][] blobs = {{1, 2, 3, 4}};
            assertTrue(referencedCache.prepareChunkPacket(referencedChunk, payload, blobs, 0));
            long globallyCachedBlobId = referencedChunk.getBlobIds().getLong(0);

            GeyserSession otherSession = mockSession("other");
            ClientBlobCache otherCache = new ClientBlobCache(otherSession);
            otherCache.setClientSupported(true);
            ClientCacheBlobStatusPacket status = new ClientCacheBlobStatusPacket();
            status.getNaks().add(globallyCachedBlobId);

            otherCache.handleBlobStatus(status);

            verify(otherSession, never()).sendUpstreamPacketImmediately(any());
        } finally {
            referencedChunk.release();
            ClientBlobCache.setGloballyEnabled(originallyEnabled);
        }
    }

    private static void assertHash(String input, String expectedHex) {
        byte[] data = input.getBytes(StandardCharsets.UTF_8);
        long actual = ClientBlobCache.xxHash64(data, 0, data.length);
        assertEquals(Long.parseUnsignedLong(expectedHex, 16), actual);
    }

    private static GeyserSession mockSession(String username) {
        GeyserSession session = mock(GeyserSession.class);
        GeyserImpl geyser = mock(GeyserImpl.class);
        when(session.getGeyser()).thenReturn(geyser);
        when(session.bedrockUsername()).thenReturn(username);
        when(geyser.getLogger()).thenReturn(mock(GeyserLogger.class));
        return session;
    }
}
