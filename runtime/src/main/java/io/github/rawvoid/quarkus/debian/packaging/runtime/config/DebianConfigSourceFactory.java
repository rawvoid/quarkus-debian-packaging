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
import java.util.Collections;
import java.util.OptionalInt;

import org.eclipse.microprofile.config.spi.ConfigSource;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;

/**
 * Discovers and registers {@link DebianExternalConfigSource} at application bootstrap.
 *
 * @author rawvoid
 */
public class DebianConfigSourceFactory implements ConfigSourceFactory {

    @Override
    public OptionalInt getPriority() {
        return OptionalInt.of(300);
    }

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        ConfigValue autoBridge = context.getValue("quarkus.debian.config.auto-bridge");
        if (autoBridge != null && "false".equalsIgnoreCase(autoBridge.getValue())) {
            return Collections.emptyList();
        }

        String packageName = getOptionalValue(context, "quarkus.debian.name");
        if (packageName == null || packageName.isBlank()) {
            packageName = getOptionalValue(context, "quarkus.application.name");
        }

        // Allow explicit override of the external config file path (e.g. for testing or custom paths)
        String customFilePath = getOptionalValue(context, "quarkus.debian.config.file-path");
        Path configFilePath;
        if (customFilePath != null && !customFilePath.isBlank()) {
            configFilePath = Path.of(customFilePath);
        } else {
            if (packageName == null || packageName.isBlank()) {
                return Collections.emptyList();
            }
            String configDir = getOptionalValue(context, "quarkus.debian.config-dir");
            if (configDir != null && !configDir.isBlank()) {
                configFilePath = Path.of(configDir, "application.properties");
            } else {
                configFilePath = Path.of("/etc", packageName, "application.properties");
            }
        }

        return Collections.singletonList(new DebianExternalConfigSource(configFilePath));
    }

    private static String getOptionalValue(ConfigSourceContext context, String name) {
        ConfigValue val = context.getValue(name);
        return (val != null && val.getValue() != null) ? val.getValue() : null;
    }
}
