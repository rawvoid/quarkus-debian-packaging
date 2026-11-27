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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Immutable MicroProfile ConfigSource loading external properties from {@code /etc/${packageName}/application.properties}.
 * <p>
 * Ordinal 275 sits above packaged {@code application.properties} (250) and below environment variables (300).
 *
 * @author rawvoid
 */
public final class ExternalConfigSource implements ConfigSource {

    public static final int ORDINAL = 275;

    private final Path configFile;
    private final int ordinal;
    private final Map<String, String> properties;

    public ExternalConfigSource(Path configFile, int ordinal) {
        this.configFile = Objects.requireNonNull(configFile, "configFile");
        this.ordinal = ordinal;
        this.properties = Map.copyOf(loadFromFile(configFile));
    }

    public ExternalConfigSource(Path configFile) {
        this(configFile, ORDINAL);
    }

    public Path getConfigFile() {
        return configFile;
    }

    @Override
    public int getOrdinal() {
        return ordinal;
    }

    @Override
    public String getName() {
        return "ExternalConfigSource[" + configFile + "]";
    }

    @Override
    public Set<String> getPropertyNames() {
        return properties.keySet();
    }

    @Override
    public String getValue(String propertyName) {
        return properties.get(propertyName);
    }

    @Override
    public Map<String, String> getProperties() {
        return properties;
    }

    /**
     * Loads UTF-8 properties from disk. Missing files produce an empty map so optional profile companion files
     * remain non-fatal, while existing unreadable or non-regular files fail fast.
     */
    public static Map<String, String> loadFromFile(Path path) {
        if (path == null || !Files.exists(path)) {
            return Map.of();
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new UncheckedIOException("External configuration file is not a readable regular file: " + path,
                    new IOException("Cannot read regular file: " + path));
        }
        Properties loaded = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            loaded.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read external configuration file: " + path, e);
        }
        Map<String, String> map = new HashMap<>(loaded.size());
        for (String name : loaded.stringPropertyNames()) {
            map.put(name, loaded.getProperty(name));
        }
        return map;
    }
}
