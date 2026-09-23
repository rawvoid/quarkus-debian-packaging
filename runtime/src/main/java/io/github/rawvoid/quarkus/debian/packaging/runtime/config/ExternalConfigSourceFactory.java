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

import io.github.rawvoid.quarkus.debian.packaging.DebianPackageNames;
import io.github.rawvoid.quarkus.debian.packaging.DebianPackagingConfig;
import io.quarkus.runtime.ApplicationConfig;
import io.quarkus.runtime.LaunchMode;
import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory.ConfigurableConfigSourceFactory;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

/**
 * Discovers and registers {@link ExternalConfigSource} at application bootstrap
 * using typed {@link DebianPackagingConfig} and {@link ApplicationConfig} mappings.
 *
 * @author rawvoid
 */
public class ExternalConfigSourceFactory implements ConfigurableConfigSourceFactory<DebianPackagingConfig> {

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
                .withMapping(ApplicationConfig.class)
                .withValidateUnknown(false)
                .build();

        DebianPackagingConfig debianConfig = config.getConfigMapping(DebianPackagingConfig.class);
        ApplicationConfig appConfig = config.getConfigMapping(ApplicationConfig.class);
        return getConfigSources(context, debianConfig, appConfig);
    }

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context, DebianPackagingConfig config) {
        return getConfigSources(context);
    }

    public Iterable<ConfigSource> getConfigSources(
            ConfigSourceContext context,
            DebianPackagingConfig config,
            ApplicationConfig appConfig) {
        if (!config.enabled()) {
            return Collections.emptyList();
        }

        if (LaunchMode.current() == LaunchMode.DEVELOPMENT && config.configFile().filter(s -> !s.isBlank()).isEmpty()) {
            return Collections.emptyList();
        }

        String rawPackageName = config.name().filter(s -> !s.isBlank())
                .or(appConfig::name)
                .filter(s -> !s.isBlank())
                .orElse(null);

        Path configFilePath;
        if (config.configFile().filter(s -> !s.isBlank()).isPresent()) {
            configFilePath = Path.of(config.configFile().get());
        } else {
            if (rawPackageName == null || rawPackageName.isBlank()) {
                return Collections.emptyList();
            }
            String packageName = DebianPackageNames.sanitize(rawPackageName);
            if (config.configDir().filter(s -> !s.isBlank()).isPresent()) {
                configFilePath = Path.of(config.configDir().get(), ExternalConfigSource.DEFAULT_CONFIG_FILENAME);
            } else {
                configFilePath = Path.of(ExternalConfigSource.DEFAULT_CONFIG_DIR, packageName, ExternalConfigSource.DEFAULT_CONFIG_FILENAME);
            }
        }

        return Collections.singletonList(new ExternalConfigSource(configFilePath));
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
}
