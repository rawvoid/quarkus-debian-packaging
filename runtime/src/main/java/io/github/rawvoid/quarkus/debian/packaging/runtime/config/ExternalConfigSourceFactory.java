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
 * Discovers and registers {@link ExternalConfigSource} and profile-specific companion sources
 * when armed by the Debian package launcher.
 *
 * @author rawvoid
 */
public class ExternalConfigSourceFactory implements ConfigSourceFactory {

    public static final String EXTERNAL_CONFIG_ENV = "QUARKUS_DEBIAN_EXTERNAL_CONFIG";
    public static final int BASE_ORDINAL = 275;

    @Override
    public OptionalInt getPriority() {
        return OptionalInt.of(300);
    }

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        return getConfigSources(context, System.getenv(EXTERNAL_CONFIG_ENV));
    }

    Iterable<ConfigSource> getConfigSources(ConfigSourceContext context, String armedEnv) {
        if (!isArmed(armedEnv) || !isEnabled(context)) {
            return Collections.emptyList();
        }

        String rawPackageName = getOptionalValue(context, "quarkus.debian.name");
        if (rawPackageName == null) {
            rawPackageName = getOptionalValue(context, "quarkus.application.name");
        }

        String configFile = getOptionalValue(context, "quarkus.debian.config-file");
        String configDir = getOptionalValue(context, "quarkus.debian.config-dir");

        if (rawPackageName == null && configFile == null) {
            return Collections.emptyList();
        }

        String pathStr = DebianPaths.configFile(rawPackageName, configDir, configFile);
        Path mainConfigFile = Path.of(pathStr);

        List<String> profiles = safeGetProfiles(context);
        List<ExternalConfigSource> sources = new ArrayList<>();
        sources.add(new ExternalConfigSource(mainConfigFile, BASE_ORDINAL));

        for (int i = profiles.size() - 1; i >= 0; i--) {
            int ordinal = BASE_ORDINAL + profiles.size() - i;
            if (ordinal >= 300) {
                throw new IllegalStateException("Active profile count causes external config ordinal to reach or exceed 300: " + ordinal);
            }
            String profile = profiles.get(i);
            Path profilePath = resolveProfilePath(mainConfigFile, profile);
            sources.add(new ExternalConfigSource(profilePath, ordinal));
        }

        ExternalConfigGroup group = new ExternalConfigGroup(mainConfigFile, sources);
        ExternalConfigGroup.register(group);
        return new ArrayList<>(sources);
    }

    static boolean isArmed(String envVal) {
        return "true".equals(envVal);
    }

    private static boolean isEnabled(ConfigSourceContext context) {
        ConfigValue val = context.getValue("quarkus.debian.enabled");
        if (val == null || val.getValue() == null || val.getValue().isBlank()) {
            return true;
        }
        return !"false".equalsIgnoreCase(val.getValue().trim());
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
