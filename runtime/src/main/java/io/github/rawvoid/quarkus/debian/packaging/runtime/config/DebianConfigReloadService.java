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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.logging.Logger;

import io.smallrye.config.ConfigValidationException;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

/**
 * Coordinates configuration validation, snapshot generation, and in-place hot patching.
 *
 * @author rawvoid
 */
public class DebianConfigReloadService {

    private static final Logger LOG = Logger.getLogger(DebianConfigReloadService.class);

    public record ReloadResult(boolean success, String message, int updatedCount) {}

    public record RegisteredMapping(Class<?> mappingClass, String prefix) {}

    private final List<RegisteredMapping> registeredMappings = new CopyOnWriteArrayList<>();

    public void registerMapping(Class<?> mappingClass, String prefix) {
        if (mappingClass != null) {
            registeredMappings.add(new RegisteredMapping(mappingClass, prefix));
        }
    }

    public List<RegisteredMapping> getRegisteredMappings() {
        return Collections.unmodifiableList(registeredMappings);
    }

    public synchronized ReloadResult reload() {
        DebianExternalConfigSource configSource = DebianExternalConfigSource.getInstance();
        if (configSource == null) {
            LOG.warn("Debian configuration reload aborted: external configuration source is not active.");
            return new ReloadResult(false, "External Debian configuration source is not active.", 0);
        }

        Path configFile = configSource.getConfigFile();
        if (configFile == null) {
            LOG.warn("Debian configuration reload aborted: external configuration file path is not defined.");
            return new ReloadResult(false, "External configuration file path is not defined.", 0);
        }

        if (!Files.isRegularFile(configFile) || !Files.isReadable(configFile)) {
            LOG.warnf("Debian configuration reload aborted: configuration file is not readable or does not exist: %s", configFile);
            return new ReloadResult(false, "Configuration file is not readable or does not exist: " + configFile, 0);
        }

        // Phase 1: Load raw properties from disk
        Map<String, String> newProps;
        try {
            newProps = DebianExternalConfigSource.loadFromFile(configFile);
        } catch (Exception e) {
            LOG.warnf(e, "Debian configuration reload aborted: syntax or IO error while reading %s", configFile);
            return new ReloadResult(false, "Syntax or IO error while reading " + configFile + ": " + e.getMessage(), 0);
        }

        // Phase 2: Validate against SmallRyeConfig and create snapshots for registered mappings
        SmallRyeConfig currentConfig = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        Map<RegisteredMapping, Object> newSnapshots = new HashMap<>();

        if (!registeredMappings.isEmpty()) {
            List<ConfigSource> testSources = new ArrayList<>();
            for (ConfigSource src : currentConfig.getConfigSources()) {
                if (!(src instanceof DebianExternalConfigSource)) {
                    testSources.add(src);
                }
            }
            testSources.add(new InMemoryConfigSource(configSource.getName(), configSource.getOrdinal(), newProps));

            SmallRyeConfigBuilder builder = new SmallRyeConfigBuilder()
                    .withSources(testSources)
                    .withValidateUnknown(false);

            for (RegisteredMapping reg : registeredMappings) {
                if (reg.prefix() != null && !reg.prefix().isEmpty()) {
                    builder.withMapping(reg.mappingClass(), reg.prefix());
                } else {
                    builder.withMapping(reg.mappingClass());
                }
            }

            try {
                SmallRyeConfig candidateConfig = builder.build();
                for (RegisteredMapping reg : registeredMappings) {
                    Object snapshot = (reg.prefix() != null && !reg.prefix().isEmpty())
                            ? candidateConfig.getConfigMapping(reg.mappingClass(), reg.prefix())
                            : candidateConfig.getConfigMapping(reg.mappingClass());
                    newSnapshots.put(reg, snapshot);
                }
            } catch (ConfigValidationException e) {
                String errorMsg = formatValidationErrors(e);
                LOG.warnf("Debian configuration validation failed while reloading %s:\n%s", configFile, errorMsg);
                return new ReloadResult(false, "Configuration validation failed:\n" + errorMsg, 0);
            } catch (Exception e) {
                LOG.warnf(e, "Debian configuration mapping failed while reloading %s", configFile);
                return new ReloadResult(false, "Configuration mapping failed: " + e.getMessage(), 0);
            }
        }

        // Phase 3: Validation passed - commit properties and patch live instances
        Map<String, String> oldProps = configSource.getProperties();
        configSource.commit(newProps);

        for (Map.Entry<RegisteredMapping, Object> entry : newSnapshots.entrySet()) {
            RegisteredMapping reg = entry.getKey();
            Object snapshot = entry.getValue();
            try {
                Object currentLive = (reg.prefix() != null && !reg.prefix().isEmpty())
                        ? currentConfig.getConfigMapping(reg.mappingClass(), reg.prefix())
                        : currentConfig.getConfigMapping(reg.mappingClass());
                ConfigMappingInPlacePatcher.patch(currentLive, snapshot);
            } catch (Exception e) {
                LOG.warnf(e, "Failed to patch live config mapping for class %s", reg.mappingClass().getName());
            }
        }

        // Phase 4: Publish CDI event
        Set<String> changedKeys = calculateChangedKeys(oldProps, newProps);
        fireReloadedEvent(configFile, newProps, changedKeys);

        LOG.infof("Configuration reloaded successfully from %s: %d properties updated (%s)",
                configFile, changedKeys.size(), changedKeys);

        return new ReloadResult(true, "Configuration reloaded successfully. " + changedKeys.size() + " properties updated.", changedKeys.size());
    }

    private static String formatValidationErrors(ConfigValidationException e) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < e.getProblemCount(); i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append("  - ").append(e.getProblem(i).getMessage());
        }
        return sb.toString();
    }

    private static Set<String> calculateChangedKeys(Map<String, String> oldProps, Map<String, String> newProps) {
        Set<String> changed = new HashSet<>();
        for (Map.Entry<String, String> entry : newProps.entrySet()) {
            if (!Objects.equals(entry.getValue(), oldProps.get(entry.getKey()))) {
                changed.add(entry.getKey());
            }
        }
        for (String oldKey : oldProps.keySet()) {
            if (!newProps.containsKey(oldKey)) {
                changed.add(oldKey);
            }
        }
        return changed;
    }

    private static void fireReloadedEvent(Path configFile, Map<String, String> props, Set<String> changedKeys) {
        try {
            jakarta.enterprise.inject.spi.CDI<Object> cdi = jakarta.enterprise.inject.spi.CDI.current();
            if (cdi != null) {
                var event = new DebianConfigReloadedEvent(configFile, Instant.now(), props, changedKeys);
                cdi.getBeanManager().getEvent().select(DebianConfigReloadedEvent.class).fire(event);
                LOG.debugf("Fired DebianConfigReloadedEvent for %s", configFile);
            }
        } catch (Throwable t) {
            // CDI container not initialized or Arc not present
            LOG.debugf("Could not fire DebianConfigReloadedEvent: %s", t.getMessage());
        }
    }

    private static class InMemoryConfigSource implements ConfigSource {
        private final String name;
        private final int ordinal;
        private final Map<String, String> props;

        InMemoryConfigSource(String name, int ordinal, Map<String, String> props) {
            this.name = name;
            this.ordinal = ordinal;
            this.props = Map.copyOf(props);
        }

        @Override
        public Set<String> getPropertyNames() {
            return props.keySet();
        }

        @Override
        public String getValue(String propertyName) {
            return props.get(propertyName);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getOrdinal() {
            return ordinal;
        }
    }
}
