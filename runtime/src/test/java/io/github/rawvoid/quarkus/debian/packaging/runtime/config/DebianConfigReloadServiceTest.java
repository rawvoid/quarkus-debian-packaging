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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.runtime.configuration.ConfigUtils;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

class DebianConfigReloadServiceTest {

    @TempDir
    Path tempDir;

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

        var configSource = new DebianExternalConfigSource(configFile);
        var reloadService = new DebianConfigReloadService();

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
        var configSource = new DebianExternalConfigSource(configFile);

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
}
