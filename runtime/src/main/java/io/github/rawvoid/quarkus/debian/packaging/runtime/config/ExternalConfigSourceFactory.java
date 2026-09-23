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

import io.github.rawvoid.quarkus.debian.packaging.DebianPackagingConfig;
import io.github.rawvoid.quarkus.debian.packaging.DebianPaths;
import io.quarkus.runtime.ApplicationConfig;
import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory.ConfigurableConfigSourceFactory;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

/**
 * Discovers and registers {@link ExternalConfigSource} and profile-specific companion sources
 * when armed by the Debian package launcher.
 *
 * @author rawvoid
 */
public class ExternalConfigSourceFactory implements ConfigurableConfigSourceFactory<DebianPackagingConfig> {

    public static final String EXTERNAL_CONFIG_ENV = "QUARKUS_DEBIAN_EXTERNAL_CONFIG";
    public static final int BASE_ORDINAL = 275;

    @Override
    public OptionalInt getPriority() {
        return OptionalInt.of(300);
    }

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        if (!isArmed()) {
            return Collections.emptyList();
        }
        return ConfigurableConfigSourceFactory.super.getConfigSources(context);
    }

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context, DebianPackagingConfig config) {
        return getConfigSources(context, config, System.getenv(EXTERNAL_CONFIG_ENV));
    }

    Iterable<ConfigSource> getConfigSources(
            ConfigSourceContext context,
            DebianPackagingConfig config,
            String armedEnv) {
        if (!config.enabled() || !isArmed(armedEnv)) {
            return Collections.emptyList();
        }

        String rawPackageName = config.name().filter(s -> !s.isBlank())
                .orElseGet(() -> resolveApplicationName(context));

        if (rawPackageName == null && config.configFile().filter(s -> !s.isBlank()).isEmpty()) {
            return Collections.emptyList();
        }

        String pathStr = DebianPaths.configFile(
                rawPackageName,
                config.configDir().orElse(null),
                config.configFile().orElse(null));
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

    private static boolean isArmed() {
        return isArmed(System.getenv(EXTERNAL_CONFIG_ENV));
    }

    static Path resolveProfilePath(Path mainConfigFile, String profile) {
        String fileName = mainConfigFile.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String profileFileName = (dot != -1)
                ? fileName.substring(0, dot) + "-" + profile + fileName.substring(dot)
                : fileName + "-" + profile;
        return mainConfigFile.resolveSibling(profileFileName);
    }

    private static String resolveApplicationName(ConfigSourceContext context) {
        try {
            SmallRyeConfig config = new SmallRyeConfigBuilder()
                    .withSources(new ConfigSourceContext.ConfigSourceContextConfigSource(context))
                    .withSources(safeGetConfigSources(context))
                    .withMapping(ApplicationConfig.class)
                    .withValidateUnknown(false)
                    .build();
            return config.getConfigMapping(ApplicationConfig.class).name().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> safeGetProfiles(ConfigSourceContext context) {
        try {
            return context.getProfiles();
        } catch (UnsupportedOperationException e) {
            return Collections.emptyList();
        }
    }

    private static List<ConfigSource> safeGetConfigSources(ConfigSourceContext context) {
        try {
            return context.getConfigSources();
        } catch (UnsupportedOperationException e) {
            return Collections.emptyList();
        }
    }
}
