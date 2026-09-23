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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.runtime.configuration.ConfigUtils;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

class ConfigReloadServiceTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanup() {
        ExternalConfigGroup.clear();
        ReloadableConfigRegistry.clear();
    }

    @Test
    void testConfigUtilsDiscoversDurationAndCharsetConverters() {
        SmallRyeConfig config = ConfigUtils.emptyConfigBuilder()
                .setAddDefaultSources(false)
                .addDiscoveredCustomizers()
                .build();

        assertEquals(Duration.ofSeconds(10), config.convert("10s", Duration.class));
        assertEquals(Duration.ofMinutes(1), config.convert("1M", Duration.class));
        assertEquals(Duration.ofHours(24), config.convert("24H", Duration.class));
        assertEquals(StandardCharsets.UTF_8, config.convert("UTF-8", Charset.class));
        assertEquals(StandardCharsets.ISO_8859_1, config.convert("ISO-8859-1", Charset.class));
    }

    @Test
    void testReloadLifecycleWithoutRegisteredMappings() throws IOException {
        Path configFile = tempDir.resolve("application.properties");
        Files.writeString(configFile, "scoot.http.connect-timeout=10s\n");

        var configSource = new ExternalConfigSource(configFile);
        ExternalConfigGroup.register(new ExternalConfigGroup(configFile, List.of(configSource)));
        var reloadService = new ConfigReloadService();

        var result = reloadService.reload();
        assertTrue(result.success());
        assertEquals("scoot.http.connect-timeout=10s\n", Files.readString(configFile));
        assertEquals("10s", configSource.getValue("scoot.http.connect-timeout"));

        // Update on disk
        Files.writeString(configFile, "scoot.http.connect-timeout=25s\n");
        var result2 = reloadService.reload();
        assertTrue(result2.success());
        assertEquals("25s", configSource.getValue("scoot.http.connect-timeout"));
    }

    @Test
    void testCandidateConfigValidationWithDurationAndCharset() {
        Path configFile = tempDir.resolve("application.properties");
        var configSource = new ExternalConfigSource(configFile);
        ExternalConfigGroup.register(new ExternalConfigGroup(configFile, List.of(configSource)));

        SmallRyeConfig currentConfig = ConfigUtils.emptyConfigBuilder().build();

        SmallRyeConfigBuilder builder = ConfigUtils.emptyConfigBuilder()
                .setAddDefaultSources(false)
                .addDiscoveredCustomizers()
                .addDiscoveredValidator()
                .withProfiles(currentConfig.getProfiles())
                .withValidateUnknown(false);

        SmallRyeConfig candidateConfig = builder.build();

        assertEquals(Duration.ofSeconds(30), candidateConfig.convert("30s", Duration.class));
        assertEquals(StandardCharsets.UTF_8, candidateConfig.convert("UTF-8", Charset.class));
    }

    @ConfigMapping(prefix = "service")
    public interface SampleServiceConfig {
        String host();
        int port();
    }

    @Test
    void testReloadLifecycleWithRegisteredMapping() throws IOException {
        Path configFile = tempDir.resolve("application.properties");
        Files.writeString(configFile, "service.host=localhost\nservice.port=8080\n");

        var configSource = new ExternalConfigSource(configFile);
        ExternalConfigGroup.register(new ExternalConfigGroup(configFile, List.of(configSource)));
        var reloadService = new ConfigReloadService();
        reloadService.registerMapping(SampleServiceConfig.class, "service");

        var result = reloadService.reload();
        assertTrue(result.success());
        assertEquals(0, result.updatedCount());

        SampleServiceConfig snapshot = ReloadableConfigRegistry.get(SampleServiceConfig.class, "service");
        assertNotNull(snapshot);
        assertEquals("localhost", snapshot.host());
        assertEquals(8080, snapshot.port());

        // Update configuration on disk and trigger reload
        Files.writeString(configFile, "service.host=remote-host\nservice.port=9090\n");
        var result2 = reloadService.reload();
        assertTrue(result2.success());
        assertEquals(2, result2.updatedCount());

        SampleServiceConfig updatedSnapshot = ReloadableConfigRegistry.get(SampleServiceConfig.class, "service");
        assertNotNull(updatedSnapshot);
        assertEquals("remote-host", updatedSnapshot.host());
        assertEquals(9090, updatedSnapshot.port());
    }

    @Test
    void testReloadWithProfileCompanionFilesAndAtomicRollbackOnFailure() throws IOException {
        Path mainFile = tempDir.resolve("application.properties");
        Path prodFile = tempDir.resolve("application-prod.properties");

        Files.writeString(mainFile, "service.host=main-host\nservice.port=8080\n");
        Files.writeString(prodFile, "service.port=8443\n");

        var mainSource = new ExternalConfigSource(mainFile, 275);
        var prodSource = new ExternalConfigSource(prodFile, 276);
        ExternalConfigGroup.register(new ExternalConfigGroup(mainFile, List.of(mainSource, prodSource)));

        var reloadService = new ConfigReloadService();
        reloadService.registerMapping(SampleServiceConfig.class, "service");

        var result = reloadService.reload();
        assertTrue(result.success());

        SampleServiceConfig snapshot = ReloadableConfigRegistry.get(SampleServiceConfig.class, "service");
        assertNotNull(snapshot);
        assertEquals("main-host", snapshot.host());
        assertEquals(8443, snapshot.port()); // Overridden by prod profile

        // Modify files: main file has valid host, but prod file has invalid non-integer port
        Files.writeString(mainFile, "service.host=new-main-host\nservice.port=8080\n");
        Files.writeString(prodFile, "service.port=invalid-port\n");

        var failResult = reloadService.reload();
        assertFalse(failResult.success());
        assertTrue(failResult.message().contains("Validation") || failResult.message().contains("failed"));

        // Atomic rollback invariant: neither source should have committed, registry unchanged
        assertEquals("main-host", mainSource.getValue("service.host"));
        assertEquals("8443", prodSource.getValue("service.port"));
        SampleServiceConfig unchanged = ReloadableConfigRegistry.get(SampleServiceConfig.class, "service");
        assertEquals("main-host", unchanged.host());
        assertEquals(8443, unchanged.port());

        // Fix the prod file with a valid integer port
        Files.writeString(prodFile, "service.port=9443\n");
        var successResult = reloadService.reload();
        assertTrue(successResult.success());

        assertEquals("new-main-host", mainSource.getValue("service.host"));
        assertEquals("9443", prodSource.getValue("service.port"));
        SampleServiceConfig finalSnapshot = ReloadableConfigRegistry.get(SampleServiceConfig.class, "service");
        assertEquals("new-main-host", finalSnapshot.host());
        assertEquals(9443, finalSnapshot.port());
    }
}
