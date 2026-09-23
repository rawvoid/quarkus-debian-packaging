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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * MicroProfile ConfigSource that bridges external properties from {@code /etc/${packageName}/application.properties}.
 * <p>
 * Configured with Ordinal 275 (higher than internal {@code application.properties} at 250, but lower than environment
 * variables at 300).
 *
 * @author rawvoid
 */
public class ExternalConfigSource implements ConfigSource {

    public static final int ORDINAL = 275;
    public static final String DEFAULT_CONFIG_DIR = "/etc";
    public static final String DEFAULT_CONFIG_FILENAME = "application.properties";

    private static volatile ExternalConfigSource instance;

    private final Path configFile;
    private final AtomicReference<Map<String, String>> currentProperties;

    public ExternalConfigSource(Path configFile) {
        this.configFile = Objects.requireNonNull(configFile, "configFile");
        Map<String, String> initialProps = loadFromFile(configFile);
        this.currentProperties = new AtomicReference<>(Collections.unmodifiableMap(initialProps));
        instance = this;
    }

    public static ExternalConfigSource getInstance() {
        return instance;
    }

    public Path getConfigFile() {
        return configFile;
    }

    @Override
    public int getOrdinal() {
        return ORDINAL;
    }

    @Override
    public String getName() {
        return "ExternalConfigSource[" + configFile + "]";
    }

    @Override
    public Set<String> getPropertyNames() {
        return currentProperties.get().keySet();
    }

    @Override
    public String getValue(String propertyName) {
        return currentProperties.get().get(propertyName);
    }

    @Override
    public Map<String, String> getProperties() {
        return currentProperties.get();
    }

    /**
     * Atomically updates the active properties snapshot.
     */
    public void commit(Map<String, String> newProps) {
        Objects.requireNonNull(newProps, "newProps");
        currentProperties.set(Collections.unmodifiableMap(new HashMap<>(newProps)));
    }

    /**
     * Loads properties from a file using UTF-8 encoding.
     * Returns an empty map if the file does not exist.
     */
    public static Map<String, String> loadFromFile(Path path) {
        if (path == null || !Files.isRegularFile(path) || !Files.isReadable(path)) {
            return Collections.emptyMap();
        }
        Properties properties = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read external configuration file: " + path, e);
        }
        Map<String, String> map = new HashMap<>();
        for (String name : properties.stringPropertyNames()) {
            map.put(name, properties.getProperty(name));
        }
        return map;
    }
}
