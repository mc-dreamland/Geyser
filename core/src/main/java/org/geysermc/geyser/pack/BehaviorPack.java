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

package org.geysermc.geyser.pack;

import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.util.FileUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * This represents a resource pack and all the data relevant to it
 */
public class BehaviorPack {
    /**
     * The list of loaded resource packs
     */
    public static final Map<String, BehaviorPack> PACKS = new HashMap<>();

    /**
     * The size of each chunk to use when sending the resource packs to clients in bytes
     */
    public static final int CHUNK_SIZE = 102400;

    private byte[] sha256;
    private File file;
    private BehaviorPackManifest manifest;
    private BehaviorPackManifest.Version version;

    /**
     * Loop through the packs directory and locate valid resource pack files
     */
    public static void loadPacks() {
        File directory = GeyserImpl.getInstance().getBootstrap().getConfigFolder().resolve("packs/BehaviorPack").toFile();

        if (!directory.exists()) {
            directory.mkdir();

            // As we just created the directory it will be empty
            return;
        }

        for (File file : directory.listFiles()) {
            if (file.getName().endsWith(".zip") || file.getName().endsWith(".mcpack")) {
                BehaviorPack behaviorPack = new BehaviorPack();

                behaviorPack.sha256 = FileUtils.calculateSHA256(file);

                Stream<? extends ZipEntry> stream = null;
                try {
                    ZipFile zip = new ZipFile(file);

                    stream = zip.stream();
                    stream.forEach((x) -> {
                        if (x.getName().contains("manifest.json") || x.getName().contains("pack_manifest.json")) {
                            try {
                                BehaviorPackManifest manifest = FileUtils.loadJson(zip.getInputStream(x), BehaviorPackManifest.class);
                                // Sometimes a pack_manifest file is present and not in a valid format,
                                // but a manifest file is, so we null check through that one
                                if (manifest.getHeader().getUuid() != null) {
                                    behaviorPack.file = file;
                                    behaviorPack.manifest = manifest;
                                    behaviorPack.version = BehaviorPackManifest.Version.fromArray(manifest.getHeader().getVersion());

                                    PACKS.put(behaviorPack.getManifest().getHeader().getUuid().toString(), behaviorPack);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                } catch (Exception e) {
                    GeyserImpl.getInstance().getLogger().error(GeyserLocale.getLocaleStringLog("geyser.resource_pack.broken", file.getName()));
                    e.printStackTrace();
                } finally {
                    if (stream != null) {
                        stream.close();
                    }
                }
            }
        }
    }

    public byte[] getSha256() {
        return sha256;
    }

    public File getFile() {
        return file;
    }

    public BehaviorPackManifest getManifest() {
        return manifest;
    }

    public BehaviorPackManifest.Version getVersion() {
        return version;
    }
}
