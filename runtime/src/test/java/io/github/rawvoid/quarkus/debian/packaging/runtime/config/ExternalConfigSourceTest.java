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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

        assertEquals(275, source.getOrdinal());
        assertTrue(source.getOrdinal() > 250, "ExternalConfigSource must override classpath application.properties");
        assertTrue(source.getOrdinal() < 300, "ExternalConfigSource must yield to environment variables");
    }

    @Test
    void testLoadsPropertiesAsImmutableSnapshot() throws IOException {
        Path configFile = tempDir.resolve("application.properties");
        Files.writeString(configFile, "app.timeout=30s\napp.greeting=Hello World\n");

        ExternalConfigSource source = new ExternalConfigSource(configFile);
        assertEquals("30s", source.getValue("app.timeout"));
        assertEquals("Hello World", source.getValue("app.greeting"));
        assertThrows(UnsupportedOperationException.class, () -> source.getProperties().put("k", "v"));
    }

    @Test
    void testLoadFromFileFailsFastWhenUnreadable() throws IOException {
        Path configFile = tempDir.resolve("unreadable.properties");
        Files.writeString(configFile, "app.timeout=30s\n");

        if (configFile.toFile().setReadable(false)) {
            try {
                assertThrows(UncheckedIOException.class, () -> ExternalConfigSource.loadFromFile(configFile));
            } finally {
                configFile.toFile().setReadable(true);
            }
        }
    }

    @Test
    void testFactoryUnarmedReturnsEmptyListInAllModes() {
        var factory = new ExternalConfigSourceFactory();
        var context = createContext(Map.of(
                "quarkus.debian.name", "my-app",
                "quarkus.debian.config-file", "/tmp/dev.properties"));

        assertFalse(factory.getConfigSources(context, null, "/etc/my-app/application.properties").iterator().hasNext());
        assertFalse(factory.getConfigSources(context, "false", "/etc/my-app/application.properties").iterator().hasNext());
        assertFalse(factory.getConfigSources(context, "", "/etc/my-app/application.properties").iterator().hasNext());
        assertFalse(factory.getConfigSources(context).iterator().hasNext());
    }

    @Test
    void testFactoryPrefersConfigFileEnvironmentVariableWhenArmed() {
        var factory = new ExternalConfigSourceFactory();
        var context = createContext(Map.of("quarkus.application.name", "fallback-app"));

        var sources = factory.getConfigSources(context, "true", "/opt/custom/app.properties").iterator();
        assertTrue(sources.hasNext());
        var source = (ExternalConfigSource) sources.next();
        assertEquals(Path.of("/opt/custom/app.properties"), source.getConfigFile());

        assertThrows(IllegalArgumentException.class,
                () -> factory.getConfigSources(context, "true", "/etc/../passwd"));
    }

    @Test
    void testFactoryFallsBackToContextPropertiesWhenConfigFileEnvUnset() {
        var factory = new ExternalConfigSourceFactory();

        var nameContext = createContext(Map.of("quarkus.debian.name", "my-app"));
        var sources = factory.getConfigSources(nameContext, "true", null).iterator();
        assertTrue(sources.hasNext());
        assertEquals(Path.of("/etc/my-app/application.properties"), ((ExternalConfigSource) sources.next()).getConfigFile());

        var fallbackContext = createContext(Map.of("quarkus.application.name", "My_Custom_App"));
        sources = factory.getConfigSources(fallbackContext, "true", null).iterator();
        assertTrue(sources.hasNext());
        assertEquals(Path.of("/etc/my-custom-app/application.properties"), ((ExternalConfigSource) sources.next()).getConfigFile());

        var customFileContext = createContext(Map.of(
                "quarkus.debian.name", "my-app",
                "quarkus.debian.config-file", "/opt/custom/config.properties"));
        sources = factory.getConfigSources(customFileContext, "true", null).iterator();
        assertTrue(sources.hasNext());
        assertEquals(Path.of("/opt/custom/config.properties"), ((ExternalConfigSource) sources.next()).getConfigFile());

        var customDirContext = createContext(Map.of(
                "quarkus.debian.name", "my-app",
                "quarkus.debian.config-dir", "/var/etc/my-app"));
        sources = factory.getConfigSources(customDirContext, "true", null).iterator();
        assertTrue(sources.hasNext());
        assertEquals(Path.of("/var/etc/my-app/application.properties"), ((ExternalConfigSource) sources.next()).getConfigFile());

        var emptyContext = createContext(Map.of());
        assertFalse(factory.getConfigSources(emptyContext, "true", null).iterator().hasNext());
    }

    @Test
    void testProfileCompanionSourcesAndOrdinals() {
        var factory = new ExternalConfigSourceFactory();
        var context = createContextWithProfiles(
                Map.of(),
                List.of("prod", "staging"));

        List<ConfigSource> sources = (List<ConfigSource>) factory.getConfigSources(
                context, "true", "/etc/my-app/application.properties");

        assertEquals(3, sources.size());

        ExternalConfigSource main = (ExternalConfigSource) sources.get(0);
        assertEquals(Path.of("/etc/my-app/application.properties"), main.getConfigFile());
        assertEquals(275, main.getOrdinal());

        // Staging (index 1 in profiles [prod, staging]): 275 + 2 - 1 = 276
        ExternalConfigSource staging = (ExternalConfigSource) sources.get(1);
        assertEquals(Path.of("/etc/my-app/application-staging.properties"), staging.getConfigFile());
        assertEquals(276, staging.getOrdinal());

        // Prod (index 0 in profiles [prod, staging]): 275 + 2 - 0 = 277
        ExternalConfigSource prod = (ExternalConfigSource) sources.get(2);
        assertEquals(Path.of("/etc/my-app/application-prod.properties"), prod.getConfigFile());
        assertEquals(277, prod.getOrdinal());

        assertTrue(prod.getOrdinal() > staging.getOrdinal());
        assertTrue(staging.getOrdinal() > main.getOrdinal());
        assertTrue(prod.getOrdinal() < 300);
    }

    private static ConfigSourceContext createContext(Map<String, String> properties) {
        return createContextWithProfiles(properties, List.of());
    }

    private static ConfigSourceContext createContextWithProfiles(Map<String, String> properties, List<String> profiles) {
        return new ConfigSourceContext() {
            @Override
            public ConfigValue getValue(String name) {
                String val = properties.get(name);
                return val != null ? ConfigValue.builder().withName(name).withValue(val).build() : null;
            }

            @Override
            public Iterator<String> iterateNames() {
                return properties.keySet().iterator();
            }

            @Override
            public List<String> getProfiles() {
                return profiles;
            }
        };
    }
}
