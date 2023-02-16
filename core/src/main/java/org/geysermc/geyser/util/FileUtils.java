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

package org.geysermc.geyser.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.geysermc.geyser.GeyserBootstrap;
import org.geysermc.geyser.GeyserImpl;

import java.io.*;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileUtils {

    /**
     * Load the given YAML file into the given class
     *
     * @param src File to load
     * @param valueType Class to load file into
     * @param <T> the type
     * @return The data as the given class
     * @throws IOException if the config could not be loaded
     */
    public static <T> T loadConfig(File src, Class<T> valueType) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        return objectMapper.readValue(src, valueType);
    }

    public static <T> T loadJson(InputStream src, Class<T> valueType) throws IOException {
        // Read specifically with UTF-8 to allow any non-UTF-encoded JSON to read
        return GeyserImpl.JSON_MAPPER.readValue(new InputStreamReader(src, StandardCharsets.UTF_8), valueType);
    }

    /**
     * Open the specified file or copy if from resources
     *
     * @param file File to open
     * @param name Name of the resource get if needed
     * @param format Formatting callback
     * @return File handle of the specified file
     * @throws IOException if the file failed to copy from resource
     */
    public static File fileOrCopiedFromResource(File file, String name, Function<String, String> format, GeyserBootstrap bootstrap) throws IOException {
        if (!file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.createNewFile();
            try (FileOutputStream fos = new FileOutputStream(file)) {
                try (InputStream input = bootstrap.getResource(name)) {
                    byte[] bytes = new byte[input.available()];

                    //noinspection ResultOfMethodCallIgnored
                    input.read(bytes);

                    for(char c : format.apply(new String(bytes)).toCharArray()) {
                        fos.write(c);
                    }

                    fos.flush();
                }
            }
        }

        return file;
    }

    /**
     * Writes the given data to the specified file on disk
     *
     * @param file File to write to
     * @param data Data to write to the file
     * @throws IOException if the file failed to write
     */
    public static void writeFile(File file, char[] data) throws IOException {
        if (!file.exists()) {
            file.createNewFile();
        }

        try (FileOutputStream fos = new FileOutputStream(file)) {
            for (char c : data) {
                fos.write(c);
            }

            fos.flush();
        }
    }

    /**
     * Writes the given data to the specified file on disk
     *
     * @param name File path to write to
     * @param data Data to write to the file
     * @throws IOException if the file failed to write
     */
    public static void writeFile(String name, char[] data) throws IOException {
        writeFile(new File(name), data);
    }

    /**
     * Calculate the SHA256 hash of a file
     *
     * @param file File to calculate the hash for
     * @return A byte[] representation of the hash
     */
    public static byte[] calculateSHA256(File file) {
        byte[] sha256;

        try {
            sha256 = MessageDigest.getInstance("SHA-256").digest(readAllBytes(file));
        } catch (Exception e) {
            throw new RuntimeException("Could not calculate pack hash", e);
        }

        return sha256;
    }

    /**
     * Calculate the SHA1 hash of a file
     *
     * @param file File to calculate the hash for
     * @return A byte[] representation of the hash
     */
    public static byte[] calculateSHA1(File file) {
        byte[] sha1;

        try {
            sha1 = MessageDigest.getInstance("SHA-1").digest(readAllBytes(file));
        } catch (Exception e) {
            throw new RuntimeException("Could not calculate pack hash", e);
        }

        return sha1;
    }

    /**
     * An android compatible version of {@link Files#readAllBytes}
     *
     * @param file File to read bytes of
     * @return The byte array of the file
     */
    public static byte[] readAllBytes(File file) {
        try (InputStream stream = new FileInputStream(file)) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Cannot read " + file);
        }
    }

    /**
     * @param resource the internal resource to read off from
     * @return the byte array of an InputStream
     */
    public static byte[] readAllBytes(String resource) {
        try (InputStream stream = GeyserImpl.getInstance().getBootstrap().getResource(resource)) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Error while trying to read internal input stream!", e);
        }
    }

    /**
     * Read the lines of a file and return it as a stream
     *
     * @param path File path to read
     * @return The lines as a stream
     */
    public static Stream<String> readAllLines(Path path) {
        try {
            return new BufferedReader(new InputStreamReader(Files.newInputStream(path))).lines();
        } catch (IOException e) {
            throw new RuntimeException("Error while trying to read file!", e);
        }
    }

    /**
     * Returns a set of all the classes that are annotated by a given annotation.
     * Keep in mind that these are from a set of generated annotations generated
     * at compile time by the annotation processor, meaning that arbitrary annotations
     * cannot be passed into this method and expected to have a set of classes
     * returned back.
     *
     * @param annotationClass the annotation class
     * @return a set of all the classes annotated by the given annotation
     */
    public static Set<Class<?>> getGeneratedClassesForAnnotation(Class<? extends Annotation> annotationClass) {
        return getGeneratedClassesForAnnotation(annotationClass.getName());
    }

    /**
     * Returns a set of all the classes that are annotated by a given annotation.
     * Keep in mind that these are from a set of generated annotations generated
     * at compile time by the annotation processor, meaning that arbitrary annotations
     * cannot be passed into this method and expected to have a set of classes
     * returned back.
     *
     * @param input the fully qualified name of the annotation
     * @return a set of all the classes annotated by the given annotation
     */
    public static Set<Class<?>> getGeneratedClassesForAnnotation(String input) {
        try (InputStream annotatedClass = GeyserImpl.getInstance().getBootstrap().getResource(input);
             BufferedReader reader = new BufferedReader(new InputStreamReader(annotatedClass))) {
            return reader.lines().map(className -> {
                try {
                    return Class.forName(className);
                } catch (ClassNotFoundException ex) {
                    GeyserImpl.getInstance().getLogger().error("Failed to find class " + className, ex);
                    throw new RuntimeException(ex);
                }
            }).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String replaceBlank(String source) {
        String ret = "";
        if (source.length() > 0) {
            ret = source
                    .replaceAll("\\s{2,}",  "")
                    .replaceAll("\\t", "");
        }
        return ret;
    }

    public static List<File> loopFiles(String path){
        return loopFiles(new File(GeyserBootstrap.class.getClassLoader().getResource(path).getPath()),1,null);
    }
    public static List<File> loopFiles(String path,FileFilter fileFilter){
        try {
            return loopFiles(new File(GeyserBootstrap.class.getClassLoader().getResource(path).toURI().toString()),1,fileFilter);
        }catch (Exception ignored){
            return loopFiles(new File(path),1,fileFilter);
        }
    }

    public static List<File> loopFiles(File file, int maxDepth, FileFilter fileFilter) {
        return loopFiles(file.toPath(), maxDepth, fileFilter);
    }
    public static List<File> loopFiles(File file, FileFilter fileFilter) {
        return loopFiles(file.toPath(), 1, fileFilter);
    }

    public static boolean isDirectory(Path path) {
        return isDirectory(path, false);
    }

    public static List<File> loopFiles(Path path, int maxDepth, FileFilter fileFilter) {
        final List<File> fileList = new ArrayList<>();

        if (null == path || false == Files.exists(path)) {
            return fileList;
        } else if (false == isDirectory(path)) {
            final File file = path.toFile();
            if (null == fileFilter || fileFilter.accept(file)) {
                fileList.add(file);
            }
            return fileList;
        }

        walkFiles(path, maxDepth, new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                final File file = path.toFile();
                if (null == fileFilter || fileFilter.accept(file)) {
                    fileList.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return fileList;
    }

    public static void walkFiles(Path start, int maxDepth, FileVisitor<? super Path> visitor) {
        if (maxDepth < 0) {
            // < 0 表示遍历到最底层
            maxDepth = Integer.MAX_VALUE;
        }

        try {
            Files.walkFileTree(start, EnumSet.noneOf(FileVisitOption.class), maxDepth, visitor);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean isDirectory(Path path, boolean isFollowLinks) {
        if (null == path) {
            return false;
        }
        final LinkOption[] options = isFollowLinks ? new LinkOption[0] : new LinkOption[]{LinkOption.NOFOLLOW_LINKS};
        return Files.isDirectory(path, options);
    }
}
