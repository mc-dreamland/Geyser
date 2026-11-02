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
 */

package org.geysermc.geyser.registry.loader;

import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.api.pack.ResourcePackManifest;
import org.geysermc.geyser.pack.GeyserResourcePackManifest;
import org.geysermc.geyser.util.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Splits NetEase distribution archives into ordinary pack ZIPs and merges them with local packs. */
public final class NeteaseEncryptedPackLoader {
    private static final Comparator<ResourcePackManifest.Version> VERSIONS = Comparator
            .comparingInt(ResourcePackManifest.Version::major)
            .thenComparingInt(ResourcePackManifest.Version::minor)
            .thenComparingInt(ResourcePackManifest.Version::patch);
    private static final LocalDateTime ZIP_TIME = LocalDateTime.of(1980, 1, 1, 0, 0);

    private NeteaseEncryptedPackLoader() {
    }

    public enum PackType {
        RESOURCE("resource_packs"),
        BEHAVIOR("behavior_packs");

        private final String directory;

        PackType(String directory) {
            this.directory = directory;
        }
    }

    private record Candidate(String name, ResourcePackManifest.Version version, Path path, boolean encrypted) {
    }

    /**
     * Matching uses the archive stem / pack folder, not header.name (which may be empty or shared).
     * Original files, UUIDs, manifests and encrypted payloads are never modified.
     */
    public static synchronized List<Path> merge(List<Path> localPacks, Path encryptionDirectory, Path cacheDirectory,
                                                PackType type, GeyserLogger logger) {
        Map<String, Candidate> selected = new LinkedHashMap<>();
        for (Path path : localPacks.stream().sorted().toList()) {
            try {
                GeyserResourcePackManifest manifest = readLocalManifest(path);
                String filename = path.getFileName().toString();
                choose(selected, new Candidate(filename.substring(0, filename.lastIndexOf('.')),
                        manifest.header().version(), path, false));
            } catch (Exception e) {
                logger.error("[网易加密包] 无法读取原目录包：" + path, e);
            }
        }

        List<Path> archives;
        try {
            Files.createDirectories(encryptionDirectory);
            try (var files = Files.list(encryptionDirectory)) {
                archives = files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
                        .sorted().toList();
            }
        } catch (IOException e) {
            logger.error("[网易加密包] 无法读取目录，继续使用原目录包：" + encryptionDirectory, e);
            archives = List.of();
        }

        for (Path archive : archives) {
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                Map<String, ZipEntry> entries = entries(zip);
                Map<String, List<ZipEntry>> roots = new TreeMap<>();
                for (var entry : entries.entrySet()) {
                    String[] parts = entry.getKey().split("/");
                    if (!entry.getValue().isDirectory() && parts.length >= 3
                            && parts[parts.length - 3].equals(type.directory) && isManifest(parts[parts.length - 1])) {
                        String root = entry.getKey().substring(0, entry.getKey().lastIndexOf('/') + 1);
                        roots.computeIfAbsent(root, ignored -> new ArrayList<>()).add(entry.getValue());
                    }
                }
                String archiveHash = hash(archive);
                for (var root : roots.entrySet()) {
                    String[] parts = root.getKey().split("/");
                    String name = parts[parts.length - 1];
                    try {
                        GeyserResourcePackManifest manifest = readManifest(zip, root.getValue());
                        Path path = splitPack(zip, entries, root.getKey(), name, archiveHash, cacheDirectory, type);
                        choose(selected, new Candidate(name, manifest.header().version(), path, true));
                    } catch (Exception e) {
                        logger.error("[网易加密包] 跳过无效包：" + archive + "!/" + root.getKey(), e);
                    }
                }
            } catch (Exception e) {
                logger.error("[网易加密包] 跳过无法处理的加密 ZIP：" + archive, e);
            }
        }

        List<Path> result = new ArrayList<>();
        for (Candidate candidate : selected.values()) {
            result.add(candidate.path());
        }
        return result;
    }

    /**
     * Only call after all pack registries are loaded on a cold start, before accepting players.
     * Reloads must keep previous files because existing sessions may still be downloading them.
     */
    public static synchronized void cleanupUnused(Path cacheDirectory, Collection<Path> activePacks,
                                                   GeyserLogger logger) {
        Path cache = cacheDirectory.toAbsolutePath().normalize();
        try {
            if (!isCacheDirectory(cache)) {
                return;
            }
            Set<Path> active = new HashSet<>();
            for (Path path : activePacks) {
                active.add(path.toAbsolutePath().normalize());
                active.add(path.toRealPath());
            }
            for (PackType type : PackType.values()) {
                Path category = cache.resolve(type.directory);
                if (!isCacheDirectory(category)) {
                    continue;
                }
                List<Path> directories;
                try (var paths = Files.list(category)) {
                    directories = paths.toList();
                }
                for (Path directory : directories) {
                    // Restrict deletion to our own layout; leave unknown files and directories alone.
                    if (!directory.getFileName().toString().matches("[0-9a-f]{64}")) {
                        continue;
                    }
                    try {
                        if (!isCacheDirectory(directory)) {
                            continue;
                        }
                        List<Path> files;
                        try (var paths = Files.list(directory)) {
                            files = paths.toList();
                        }
                        for (Path file : files) {
                            String name = file.getFileName().toString();
                            boolean generated = name.endsWith(".zip")
                                    || (name.startsWith("pack-") && name.endsWith(".tmp"));
                            if (generated && !active.contains(file)
                                    && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                                    && file.toRealPath().equals(file)) {
                                Files.deleteIfExists(file);
                            }
                        }
                        try (var paths = Files.list(directory)) {
                            if (paths.findAny().isPresent()) {
                                continue;
                            }
                        }
                        Files.deleteIfExists(directory);
                    } catch (IOException e) {
                        logger.error("[网易加密包] 无法清理旧缓存：" + directory, e);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("[网易加密包] 无法扫描旧缓存：" + cache, e);
        }
    }

    private static boolean isCacheDirectory(Path path) throws IOException {
        // Do not follow symlinks/junctions, including redirected parent directories.
        return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && path.toRealPath().equals(path);
    }

    private static void choose(Map<String, Candidate> selected, Candidate incoming) {
        String key = incoming.name().toLowerCase(Locale.ROOT);
        Candidate previous = selected.get(key);
        if (previous == null) {
            selected.put(key, incoming);
            return;
        }
        int comparison = VERSIONS.compare(incoming.version(), previous.version());
        boolean replace = comparison > 0 || (comparison == 0 && incoming.encrypted() && !previous.encrypted());
        if (replace) {
            selected.put(key, incoming);
        }
    }

    private static GeyserResourcePackManifest readLocalManifest(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            Map<String, List<ZipEntry>> roots = new TreeMap<>();
            int minimumDepth = Integer.MAX_VALUE;
            for (var entry : entries(zip).entrySet()) {
                String[] parts = entry.getKey().split("/");
                if (entry.getValue().isDirectory() || !isManifest(parts[parts.length - 1])) {
                    continue;
                }
                if (parts.length < minimumDepth) {
                    roots.clear();
                    minimumDepth = parts.length;
                }
                if (parts.length == minimumDepth) {
                    String root = entry.getKey().substring(0, entry.getKey().lastIndexOf('/') + 1);
                    roots.computeIfAbsent(root, ignored -> new ArrayList<>()).add(entry.getValue());
                }
            }
            if (roots.size() != 1) {
                throw new IOException("Expected one pack root in " + path);
            }
            return readManifest(zip, roots.values().iterator().next());
        }
    }

    private static GeyserResourcePackManifest readManifest(ZipFile zip, List<ZipEntry> manifests) throws IOException {
        GeyserResourcePackManifest result = null;
        for (ZipEntry entry : manifests) {
            try (InputStream input = zip.getInputStream(entry)) {
                GeyserResourcePackManifest manifest = FileUtils.loadJson(input, GeyserResourcePackManifest.class);
                if (manifest == null || manifest.header() == null || manifest.header().uuid() == null
                        || manifest.header().version() == null || manifest.header().version().major() < 0
                        || manifest.header().version().minor() < 0 || manifest.header().version().patch() < 0) {
                    throw new IOException("Missing or invalid pack UUID/version: " + entry.getName());
                }
                if (result != null && (!result.header().uuid().equals(manifest.header().uuid())
                        || VERSIONS.compare(result.header().version(), manifest.header().version()) != 0)) {
                    throw new IOException("Conflicting manifest.json and pack_manifest.json: " + entry.getName());
                }
                result = manifest;
            }
        }
        return result;
    }

    private static boolean isManifest(String filename) {
        return filename.equals("manifest.json") || filename.equals("pack_manifest.json");
    }

    private static Map<String, ZipEntry> entries(ZipFile zip) throws IOException {
        Map<String, ZipEntry> entries = new TreeMap<>();
        var enumeration = zip.entries();
        while (enumeration.hasMoreElements()) {
            ZipEntry entry = enumeration.nextElement();
            String name = entry.getName().replace('\\', '/');
            for (String part : name.split("/", -1)) {
                if (part.equals("..") || part.equals(".") || part.contains(":") || part.indexOf('\0') >= 0) {
                    throw new IOException("Unsafe ZIP entry: " + name);
                }
            }
            if (name.startsWith("/") || name.isEmpty() || name.contains("//") || entries.putIfAbsent(name, entry) != null) {
                throw new IOException("Invalid or duplicate ZIP entry: " + name);
            }
        }
        return entries;
    }

    private static Path splitPack(ZipFile zip, Map<String, ZipEntry> entries, String root, String name,
                                  String archiveHash, Path cacheDirectory, PackType type) throws IOException {
        // Never overwrite a ZIP still referenced by a player's in-progress download after loadpacks.
        // Include the internal root hash because an archive can contain multiple worlds with the same pack names.
        String cacheHash = HexFormat.of().formatHex(digest().digest((archiveHash + "\n" + root).getBytes(StandardCharsets.UTF_8)));
        Path cache = cacheDirectory.toAbsolutePath().normalize();
        Path output = cache.resolve(type.directory).resolve(cacheHash).resolve(name + ".zip").normalize();
        if (!output.startsWith(cache)) {
            throw new IOException("Pack cache path escapes cache directory: " + name);
        }
        if (Files.isRegularFile(output)) {
            return output;
        }
        Files.createDirectories(output.getParent());
        Path temporary = Files.createTempFile(output.getParent(), "pack-", ".tmp");
        try {
            try (ZipOutputStream target = new ZipOutputStream(Files.newOutputStream(temporary))) {
                for (var entry : entries.entrySet()) {
                    if (!entry.getKey().startsWith(root) || entry.getKey().equals(root)) {
                        continue;
                    }
                    ZipEntry packed = new ZipEntry(entry.getKey().substring(root.length()));
                    packed.setTimeLocal(ZIP_TIME);
                    target.putNextEntry(packed);
                    if (!entry.getValue().isDirectory()) {
                        try (InputStream input = zip.getInputStream(entry.getValue())) {
                            input.transferTo(target);
                        }
                    }
                    target.closeEntry();
                }
            }
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, output);
            }
            return output;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String hash(Path path) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
