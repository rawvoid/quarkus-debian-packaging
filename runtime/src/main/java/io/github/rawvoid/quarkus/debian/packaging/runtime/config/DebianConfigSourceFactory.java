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
import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory.ConfigurableConfigSourceFactory;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

/**
 * Discovers and registers {@link DebianExternalConfigSource} at application bootstrap
 * using typed {@link DebianPackagingConfig} mapping.
 *
 * @author rawvoid
 */
public class DebianConfigSourceFactory implements ConfigurableConfigSourceFactory<DebianPackagingConfig> {

    @Override
    public OptionalInt getPriority() {
        return OptionalInt.of(300);
    }

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        List<String> profiles = safeGetProfiles(context);
        List<ConfigSource> sources = safeGetConfigSources(context);

        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withProfiles(profiles)
                .withSources(new ConfigSourceContext.ConfigSourceContextConfigSource(context))
                .withSources(sources)
                .withMapping(DebianPackagingConfig.class)
                .build();

        DebianPackagingConfig mapping = config.getConfigMapping(DebianPackagingConfig.class);
        return getConfigSources(context, mapping);
    }

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context, DebianPackagingConfig config) {
        if (!config.enabled() || !config.config().autoBridge()) {
            return Collections.emptyList();
        }

        String packageName = config.name().filter(s -> !s.isBlank())
                .orElseGet(() -> getOptionalValue(context, "quarkus.application.name"));

        Path configFilePath;
        if (config.config().filePath().filter(s -> !s.isBlank()).isPresent()) {
            configFilePath = Path.of(config.config().filePath().get());
        } else {
            if (packageName == null || packageName.isBlank()) {
                return Collections.emptyList();
            }
            if (config.configDir().filter(s -> !s.isBlank()).isPresent()) {
                configFilePath = Path.of(config.configDir().get(), "application.properties");
            } else {
                configFilePath = Path.of("/etc", packageName, "application.properties");
            }
        }

        return Collections.singletonList(new DebianExternalConfigSource(configFilePath));
    }

    private static List<String> safeGetProfiles(ConfigSourceContext context) {
        try {
            List<String> profiles = new ArrayList<>(context.getProfiles());
            Collections.reverse(profiles);
            return profiles;
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

    private static String getOptionalValue(ConfigSourceContext context, String name) {
        ConfigValue val = context.getValue(name);
        return (val != null && val.getValue() != null && !val.getValue().isBlank()) ? val.getValue() : null;
    }
}
