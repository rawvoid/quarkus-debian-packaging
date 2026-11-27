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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

import org.eclipse.microprofile.config.spi.ConfigSource;

import io.github.rawvoid.quarkus.debian.packaging.DebianPaths;
import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;

/**
 * Registers {@link ExternalConfigSource} and profile-specific companion sources when armed by the Debian launcher.
 *
 * @author rawvoid
 */
public final class ExternalConfigSourceFactory implements ConfigSourceFactory {

    public static final String EXTERNAL_CONFIG_ENV = "QUARKUS_DEBIAN_EXTERNAL_CONFIG";
    public static final String EXTERNAL_CONFIG_FILE_ENV = "QUARKUS_DEBIAN_CONFIG_FILE";
    public static final int BASE_ORDINAL = ExternalConfigSource.ORDINAL;

    @Override
    public OptionalInt getPriority() {
        return OptionalInt.of(BASE_ORDINAL);
    }

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        return getConfigSources(context, System.getenv(EXTERNAL_CONFIG_ENV), System.getenv(EXTERNAL_CONFIG_FILE_ENV));
    }

    Iterable<ConfigSource> getConfigSources(ConfigSourceContext context, String armedEnv, String configFileEnv) {
        if (!isArmed(armedEnv)) {
            return Collections.emptyList();
        }

        Path mainConfigFile = resolveMainConfigFile(context, configFileEnv);
        if (mainConfigFile == null) {
            return Collections.emptyList();
        }

        List<String> profiles = safeGetProfiles(context);
        List<ConfigSource> sources = new ArrayList<>(1 + profiles.size());
        sources.add(new ExternalConfigSource(mainConfigFile, BASE_ORDINAL));

        // Context profiles are ordered highest-priority first; assign ordinals in ascending priority below env vars (300).
        for (int i = profiles.size() - 1; i >= 0; i--) {
            int ordinal = BASE_ORDINAL + profiles.size() - i;
            if (ordinal >= 300) {
                throw new IllegalStateException(
                        "Active profile count causes external config ordinal to reach or exceed 300: " + ordinal);
            }
            Path profilePath = resolveProfilePath(mainConfigFile, profiles.get(i));
            sources.add(new ExternalConfigSource(profilePath, ordinal));
        }

        return sources;
    }

    static boolean isArmed(String envVal) {
        return "true".equals(envVal);
    }

    private static Path resolveMainConfigFile(ConfigSourceContext context, String configFileEnv) {
        if (configFileEnv != null && !configFileEnv.isBlank()) {
            return Path.of(DebianPaths.normalize(configFileEnv, 2));
        }

        String rawPackageName = getOptionalValue(context, "quarkus.debian.name");
        if (rawPackageName == null) {
            rawPackageName = getOptionalValue(context, "quarkus.application.name");
        }

        String configFile = getOptionalValue(context, "quarkus.debian.config-file");
        String configDir = getOptionalValue(context, "quarkus.debian.config-dir");
        if (rawPackageName == null && configFile == null) {
            return null;
        }

        return Path.of(DebianPaths.configFile(rawPackageName, configDir, configFile));
    }

    private static String getOptionalValue(ConfigSourceContext context, String name) {
        ConfigValue val = context.getValue(name);
        if (val == null || val.getValue() == null || val.getValue().isBlank()) {
            return null;
        }
        return val.getValue().trim();
    }

    static Path resolveProfilePath(Path mainConfigFile, String profile) {
        String fileName = mainConfigFile.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String profileFileName = (dot != -1)
                ? fileName.substring(0, dot) + "-" + profile + fileName.substring(dot)
                : fileName + "-" + profile;
        return mainConfigFile.resolveSibling(profileFileName);
    }

    private static List<String> safeGetProfiles(ConfigSourceContext context) {
        try {
            return context.getProfiles();
        } catch (UnsupportedOperationException e) {
            return Collections.emptyList();
        }
    }
}
