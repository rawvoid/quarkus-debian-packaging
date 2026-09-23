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

import jakarta.enterprise.inject.spi.CDI;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.logging.Logger;

import io.quarkus.runtime.configuration.ConfigUtils;
import io.smallrye.config.ConfigValidationException;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

/**
 * Coordinates configuration validation, candidate snapshot generation, and atomic snapshot swapping.
 *
 * @author rawvoid
 */
public class ConfigReloadService {

    private static final Logger LOG = Logger.getLogger(ConfigReloadService.class);

    public record ReloadResult(boolean success, String message, int updatedCount) {}

    private final List<ConfigMappingKey> registeredMappings = new CopyOnWriteArrayList<>();

    public void registerMapping(Class<?> mappingClass, String prefix) {
        if (mappingClass != null) {
            registeredMappings.add(new ConfigMappingKey(mappingClass, prefix));
        }
    }

    public List<ConfigMappingKey> getRegisteredMappings() {
        return Collections.unmodifiableList(registeredMappings);
    }

    public synchronized ReloadResult reload() {
        ExternalConfigGroup group = ExternalConfigGroup.getInstance();
        if (group == null || group.getSources().isEmpty()) {
            LOG.warn("Configuration reload aborted: external configuration source is not active.");
            return new ReloadResult(false, "External configuration source is not active.", 0);
        }

        Path mainConfigFile = group.getMainConfigFile();
        if (mainConfigFile == null) {
            LOG.warn("Configuration reload aborted: external configuration file path is not defined.");
            return new ReloadResult(false, "External configuration file path is not defined.", 0);
        }

        // Phase 1: Load raw properties from disk for all sources in group
        Map<ExternalConfigSource, Map<String, String>> newPropsMap = new HashMap<>();
        for (ExternalConfigSource source : group.getSources()) {
            Path file = source.getConfigFile();
            try {
                Map<String, String> loaded = ExternalConfigSource.loadFromFile(file);
                newPropsMap.put(source, loaded);
            } catch (Exception e) {
                LOG.warnf(e, "Configuration reload aborted: syntax or IO error while reading %s", file);
                return new ReloadResult(false, "Syntax or IO error while reading " + file + ": " + e.getMessage(), 0);
            }
        }

        // Phase 2: Validate against SmallRyeConfig and create snapshots for registered mappings
        SmallRyeConfig currentConfig = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        Map<ConfigMappingKey, Object> newSnapshots = new HashMap<>();

        if (!registeredMappings.isEmpty()) {
            List<ConfigSource> testSources = new ArrayList<>();
            for (ConfigSource src : currentConfig.getConfigSources()) {
                if (!(src instanceof ExternalConfigSource)) {
                    testSources.add(src);
                }
            }
            for (ExternalConfigSource source : group.getSources()) {
                testSources.add(new InMemoryConfigSource(source.getName(), source.getOrdinal(), newPropsMap.get(source)));
            }

            SmallRyeConfigBuilder builder = ConfigUtils.emptyConfigBuilder()
                    .setAddDefaultSources(false)
                    .addDiscoveredCustomizers()
                    .addDiscoveredValidator()
                    .withProfiles(currentConfig.getProfiles())
                    .withSources(testSources)
                    .withValidateUnknown(false);

            for (ConfigMappingKey reg : registeredMappings) {
                if (reg.prefix() != null && !reg.prefix().isEmpty()) {
                    builder.withMapping(reg.mappingClass(), reg.prefix());
                } else {
                    builder.withMapping(reg.mappingClass());
                }
            }

            try {
                SmallRyeConfig candidateConfig = builder.build();
                for (ConfigMappingKey reg : registeredMappings) {
                    Object snapshot = (reg.prefix() != null && !reg.prefix().isEmpty())
                            ? candidateConfig.getConfigMapping(reg.mappingClass(), reg.prefix())
                            : candidateConfig.getConfigMapping(reg.mappingClass());
                    newSnapshots.put(reg, snapshot);
                }
            } catch (ConfigValidationException e) {
                String errorMsg = formatValidationErrors(e);
                LOG.warnf("Configuration validation failed while reloading %s:\n%s", mainConfigFile, errorMsg);
                return new ReloadResult(false, "Configuration validation failed:\n" + errorMsg, 0);
            } catch (Exception e) {
                LOG.warnf(e, "Configuration mapping failed while reloading %s", mainConfigFile);
                return new ReloadResult(false, "Configuration mapping failed: " + e.getMessage(), 0);
            }
        }

        // Phase 3: Validation passed - commit properties and swap active snapshots atomically
        Set<String> changedKeys = new HashSet<>();
        Map<String, String> combinedNewProps = new HashMap<>();

        for (ExternalConfigSource source : group.getSources()) {
            Map<String, String> oldProps = source.getProperties();
            Map<String, String> newProps = newPropsMap.get(source);
            changedKeys.addAll(calculateChangedKeys(oldProps, newProps));
            combinedNewProps.putAll(newProps);
            source.commit(newProps);
        }

        for (Map.Entry<ConfigMappingKey, Object> entry : newSnapshots.entrySet()) {
            ConfigMappingKey reg = entry.getKey();
            Object snapshot = entry.getValue();
            ReloadableConfigRegistry.swap(reg.mappingClass(), reg.prefix(), snapshot);
        }

        // Phase 4: Publish CDI event
        fireReloadedEvent(mainConfigFile, combinedNewProps, changedKeys);

        LOG.infof("Configuration reloaded successfully from %s: %d properties updated (%s)",
                mainConfigFile, changedKeys.size(), changedKeys);

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
            CDI<Object> cdi = CDI.current();
            if (cdi != null) {
                var event = new ConfigReloadedEvent(configFile, Instant.now(), props, changedKeys);
                cdi.getBeanManager().getEvent().select(ConfigReloadedEvent.class).fire(event);
                LOG.debugf("Fired ConfigReloadedEvent for %s", configFile);
            }
        } catch (Throwable t) {
            // CDI container not initialized or Arc not present
            LOG.debugf("Could not fire ConfigReloadedEvent: %s", t.getMessage());
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
