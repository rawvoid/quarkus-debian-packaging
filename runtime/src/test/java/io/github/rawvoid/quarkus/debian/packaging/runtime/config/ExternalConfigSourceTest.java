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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.runtime.LaunchMode;
import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigValue;

class ExternalConfigSourceTest {

    @TempDir
    Path tempDir;

    @Test
    void testOrdinalAndDefaults() {
        Path dummyPath = tempDir.resolve("non-existent.properties");
        ExternalConfigSource source = new ExternalConfigSource(dummyPath);

        assertEquals(275, source.getOrdinal());
        assertTrue(source.getPropertyNames().isEmpty());
        assertNull(source.getValue("any.property"));
        assertEquals(dummyPath, source.getConfigFile());
    }

    @Test
    void testOrdinalHierarchyAgainstStandardSources() {
        Path configFile = tempDir.resolve("application.properties");
        ExternalConfigSource source = new ExternalConfigSource(configFile);

        // Ordinal 275 sits above internal application.properties (250)
        // and below environment variables (300)
        assertEquals(275, source.getOrdinal());
        assertTrue(source.getOrdinal() > 250, "ExternalConfigSource must override classpath application.properties");
        assertTrue(source.getOrdinal() < 300, "ExternalConfigSource must yield to environment variables");
    }

    @Test
    void testLoadAndCommit() throws IOException {
        Path configFile = tempDir.resolve("application.properties");
        Files.writeString(configFile, "app.timeout=30s\napp.greeting=Hello World\n");

        ExternalConfigSource source = new ExternalConfigSource(configFile);
        assertEquals("30s", source.getValue("app.timeout"));
        assertEquals("Hello World", source.getValue("app.greeting"));

        // Test commit update
        source.commit(Map.of("app.timeout", "60s", "app.greeting", "Updated"));
        assertEquals("60s", source.getValue("app.timeout"));
        assertEquals("Updated", source.getValue("app.greeting"));
    }

    @Test
    void testFactoryRespectsDisabledSwitch() {
        var factory = new ExternalConfigSourceFactory();

        var disabledContext = createContext(Map.of("quarkus.debian.enabled", "false"));
        assertFalse(factory.getConfigSources(disabledContext).iterator().hasNext());
    }

    @Test
    void testFactorySkipsDefaultConfigInDevelopmentMode() {
        LaunchMode prev = LaunchMode.current();
        try {
            LaunchMode.set(LaunchMode.DEVELOPMENT);
            var factory = new ExternalConfigSourceFactory();

            // Default config without explicit configFile is skipped in dev mode
            var context = createContext(Map.of("quarkus.debian.name", "my-app"));
            assertFalse(factory.getConfigSources(context).iterator().hasNext());

            // Explicit configFile is still honored in dev mode
            var explicitContext = createContext(Map.of(
                    "quarkus.debian.name", "my-app",
                    "quarkus.debian.config-file", "/tmp/dev.properties"));
            assertTrue(factory.getConfigSources(explicitContext).iterator().hasNext());
        } finally {
            LaunchMode.set(prev);
        }
    }

    @Test
    void testFactoryResolvesConfigFilePath() {
        var factory = new ExternalConfigSourceFactory();

        // 1. Explicit debian package name
        var nameContext = createContext(Map.of("quarkus.debian.name", "my-app"));
        var sources = factory.getConfigSources(nameContext).iterator();
        assertTrue(sources.hasNext());
        var source = (ExternalConfigSource) sources.next();
        assertEquals(Path.of("/etc/my-app/application.properties"), source.getConfigFile());

        // 2. Fallback to quarkus.application.name
        var fallbackContext = createContext(Map.of("quarkus.application.name", "my-fallback-app"));
        sources = factory.getConfigSources(fallbackContext).iterator();
        assertTrue(sources.hasNext());
        source = (ExternalConfigSource) sources.next();
        assertEquals(Path.of("/etc/my-fallback-app/application.properties"), source.getConfigFile());

        // 2b. Fallback to quarkus.application.name with underscores and uppercase (sanitized)
        var unsanitizedContext = createContext(Map.of("quarkus.application.name", "My_Custom_App"));
        sources = factory.getConfigSources(unsanitizedContext).iterator();
        assertTrue(sources.hasNext());
        source = (ExternalConfigSource) sources.next();
        assertEquals(Path.of("/etc/my-custom-app/application.properties"), source.getConfigFile());

        // 3. Custom config file override
        var customFileContext = createContext(Map.of(
                "quarkus.debian.name", "my-app",
                "quarkus.debian.config-file", "/opt/custom/config.properties"));
        sources = factory.getConfigSources(customFileContext).iterator();
        assertTrue(sources.hasNext());
        source = (ExternalConfigSource) sources.next();
        assertEquals(Path.of("/opt/custom/config.properties"), source.getConfigFile());

        // 4. Custom config directory
        var customDirContext = createContext(Map.of(
                "quarkus.debian.name", "my-app",
                "quarkus.debian.config-dir", "/var/etc/my-app"));
        sources = factory.getConfigSources(customDirContext).iterator();
        assertTrue(sources.hasNext());
        source = (ExternalConfigSource) sources.next();
        assertEquals(Path.of("/var/etc/my-app/application.properties"), source.getConfigFile());

        // 5. Missing package name and app name
        var emptyContext = createContext(Map.of());
        assertFalse(factory.getConfigSources(emptyContext).iterator().hasNext());
    }

    private static ConfigSourceContext createContext(Map<String, String> properties) {
        return new ConfigSourceContext() {
            @Override
            public ConfigValue getValue(String name) {
                String val = properties.get(name);
                return val != null ? ConfigValue.builder().withName(name).withValue(val).build() : null;
            }

            @Override
            public java.util.Iterator<String> iterateNames() {
                return properties.keySet().iterator();
            }
        };
    }
}
