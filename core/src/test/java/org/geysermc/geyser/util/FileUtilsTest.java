/*
 * Copyright (c) 2024 GeyserMC. http://geysermc.org
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

import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FileUtilsTest {

    @Test
    public void loadJsonAllowsTrailingComments() {
        String json = "{\n"
            + "  \"number\": 42\n"
            + "}\n"
            + "// trailing comment\n";
        TestJson value = FileUtils.loadJson(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), TestJson.class);

        assertEquals(42, value.number);
    }

    @Test
    public void loadJsonRejectsTrailingTokens() {
        String json = "{\n"
            + "  \"number\": 42\n"
            + "}\n"
            + "trailing\n";
        assertThrows(JsonSyntaxException.class, () -> FileUtils.loadJson(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), TestJson.class));
    }

    private static final class TestJson {
        private int number;
    }
}
