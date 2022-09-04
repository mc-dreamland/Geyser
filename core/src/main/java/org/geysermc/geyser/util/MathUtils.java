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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class MathUtils {
    public static final double SQRT_OF_TWO = Math.sqrt(2);

    public static float wrapDegrees(float degrees) {
        degrees = degrees % 360.0f;
        if (degrees < -180.0f) {
            degrees += 360.0f;
        } else if (degrees >= 180.0f) {
            degrees -= 360.0f;
        }
        return degrees;
    }

    public static float wrapDegrees(double degrees) {
        return wrapDegrees((float) degrees);
    }

    public static int wrapDegreesToInt(float degrees) {
        return (int) wrapDegrees(degrees);
    }

    /**
     * Round the given float to the next whole number
     *
     * @param floatNumber Float to round
     * @return Rounded number
     */
    public static int ceil(float floatNumber) {
        int truncated = (int) floatNumber;
        return floatNumber > truncated ? truncated + 1 : truncated;
    }

    /**
     * If number is greater than the max, set it to max, and if number is lower than low, set it to low.
     *
     * @param num number to calculate
     * @param min the lowest value the number can be
     * @param max the greatest value the number can be
     * @return - min if num is lower than min <br>
     * - max if num is greater than max <br>
     * - num otherwise
     */
    public static double constrain(double num, double min, double max) {
        if (num > max) {
            num = max;
        }

        if (num < min) {
            num = min;
        }

        return num;
    }

    /**
     * If number is greater than the max, set it to max, and if number is lower than low, set it to low.
     *
     * @param num number to calculate
     * @param min the lowest value the number can be
     * @param max the greatest value the number can be
     * @return - min if num is lower than min <br>
     * - max if num is greater than max <br>
     * - num otherwise
     */
    public static int constrain(int num, int min, int max) {
        if (num > max) {
            num = max;
        }

        if (num < min) {
            num = min;
        }

        return num;
    }

    /**
     * Ensures the resulting object is a byte. Java Edition does not care whether a byte is encoded as an integer or not;
     * it converts it into a byte anyway.
     *
     * @param value The value to convert
     * @return The converted byte
     */
    public static byte getNbtByte(Object value) {
        return ((Number) value).byteValue();
    }

    /**
     * Packs a chunk's X and Z coordinates into a single {@code long}.
     *
     * @param x the X coordinate
     * @param z the Z coordinate
     * @return the packed coordinates
     */
    public static long chunkPositionToLong(int x, int z) {
        return ((x & 0xFFFFFFFFL) << 32L) | (z & 0xFFFFFFFFL);
    }

    /**
     * @return the bits per entry used when this number is the maximum amount of entries.
     */
    public static int getGlobalPaletteForSize(int size) {
        return 32 - Integer.numberOfLeadingZeros(size - 1);
    }

    public static byte[] gZipBytes(byte[] data) {
        byte[] gZipByte = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(bos);
            gzip.write(data);
            gzip.finish();
            gzip.close();
            gZipByte = bos.toByteArray();
            bos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return gZipByte;
    }

    public static byte[] unGZipBytes(byte[] data) {
        byte[] b = null;
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            GZIPInputStream gzip = new GZIPInputStream(bis);
            byte[] buf = new byte[1024];
            int num = -1;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while ((num = gzip.read(buf, 0, buf.length)) != -1) {
                baos.write(buf, 0, num);
            }
            b = baos.toByteArray();
            baos.flush();
            baos.close();
            gzip.close();
            bis.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return b;
    }

    public static String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            BigInteger number = new BigInteger (1, messageDigest);
            String hashtext = number.toString(16);
            while (hashtext.length()<32) {
                hashtext = "0" + hashtext;
            }
            return hashtext;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
