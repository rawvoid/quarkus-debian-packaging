/*
 * Copyright 2026 Rawvoid(https://github.com/rawvoid)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.rawvoid.quarkus.debian.packaging.runtime.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DebianExternalConfigSourceTest {

    @TempDir
    Path tempDir;

    @Test
    void testOrdinalAndDefaults() {
        Path dummyPath = tempDir.resolve("non-existent.properties");
        DebianExternalConfigSource source = new DebianExternalConfigSource(dummyPath);

        assertEquals(275, source.getOrdinal());
        assertTrue(source.getPropertyNames().isEmpty());
        assertNull(source.getValue("any.property"));
        assertEquals(dummyPath, source.getConfigFile());
    }

    @Test
    void testLoadAndCommit() throws IOException {
        Path configFile = tempDir.resolve("application.properties");
        Files.writeString(configFile, "app.timeout=30s\napp.greeting=Hello World\n");

        DebianExternalConfigSource source = new DebianExternalConfigSource(configFile);
        assertEquals("30s", source.getValue("app.timeout"));
        assertEquals("Hello World", source.getValue("app.greeting"));

        // Test commit update
        source.commit(Map.of("app.timeout", "60s", "app.greeting", "Updated"));
        assertEquals("60s", source.getValue("app.timeout"));
        assertEquals("Updated", source.getValue("app.greeting"));
    }
}
