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

package io.github.rawvoid.quarkus.debian.packaging.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.rawvoid.quarkus.debian.packaging.DebianPackagingConfig;
import io.github.rawvoid.quarkus.debian.packaging.runtime.DebianConfigRecorder;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;
import io.quarkus.deployment.builditem.ConfigMappingBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

class DebianConfigProcessorTest {

    @ConfigMapping(prefix = "app.business")
    interface BusinessConfig {
        String name();
    }

    @ConfigMapping(prefix = "quarkus.framework")
    @ConfigRoot(phase = ConfigPhase.RUN_TIME)
    interface FrameworkConfigWithRoot {
        String port();
    }

    @ConfigMapping(prefix = "quarkus.subsystem")
    interface FrameworkConfigWithoutRoot {
        String host();
    }

    @ConfigMapping(prefix = "quarkus")
    interface FrameworkRootPrefixConfig {
        String mode();
    }

    @ConfigMapping(prefix = "quarkus-app")
    interface AppWithQuarkusDashConfig {
        String custom();
    }

    @Test
    void testSetupConfigReloadExcludesFrameworkMappings() {
        var recordedMappings = new ArrayList<String>();
        var recorder = new DebianConfigRecorder() {
            @Override
            public void registerMapping(String className, String prefix) {
                recordedMappings.add(className);
            }

            @Override
            public void startControlServer(ShutdownContext shutdownContext, boolean enabled, String socketPath) {
                // no-op in unit test
            }
        };

        var shutdownContext = new ShutdownContextBuildItem();
        DebianPackagingConfig config = createTestConfig();
        var appInfo = new ApplicationInfoBuildItem(Optional.of("test-app"), Optional.of("1.0.0"));

        var businessMapping = new ConfigMappingBuildItem(BusinessConfig.class, "app.business");
        var appWithDashMapping = new ConfigMappingBuildItem(AppWithQuarkusDashConfig.class, "quarkus-app");
        var frameworkWithRootMapping = new ConfigMappingBuildItem(FrameworkConfigWithRoot.class, "quarkus.framework");
        var frameworkWithoutRootMapping = new ConfigMappingBuildItem(FrameworkConfigWithoutRoot.class, "quarkus.subsystem");
        var frameworkRootPrefixMapping = new ConfigMappingBuildItem(FrameworkRootPrefixConfig.class, "quarkus");
        var quarkusPackageMapping = new ConfigMappingBuildItem(io.quarkus.runtime.ConfigConfig.class, "quarkus");

        var processor = new DebianConfigProcessor();
        processor.setupConfigReload(recorder, shutdownContext, config, appInfo, List.of(
                businessMapping,
                appWithDashMapping,
                frameworkWithRootMapping,
                frameworkWithoutRootMapping,
                frameworkRootPrefixMapping,
                quarkusPackageMapping
        ));

        assertTrue(recordedMappings.contains(BusinessConfig.class.getName()),
                "Application @ConfigMapping should be registered for reload");
        assertTrue(recordedMappings.contains(AppWithQuarkusDashConfig.class.getName()),
                "Application config with prefix 'quarkus-app' (not quarkus. or quarkus) should be registered");

        assertFalse(recordedMappings.contains(FrameworkConfigWithRoot.class.getName()),
                "Framework @ConfigRoot mapping should be excluded from reload");
        assertFalse(recordedMappings.contains(FrameworkConfigWithoutRoot.class.getName()),
                "Framework mapping with 'quarkus.' prefix should be excluded from reload");
        assertFalse(recordedMappings.contains(FrameworkRootPrefixConfig.class.getName()),
                "Framework mapping with exact 'quarkus' prefix should be excluded from reload");
        assertFalse(recordedMappings.contains(io.quarkus.runtime.ConfigConfig.class.getName()),
                "io.quarkus.* package mapping should be excluded from reload");

        assertEquals(2, recordedMappings.size());
    }

    private static DebianPackagingConfig createTestConfig() {
        return new DebianPackagingConfig() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public Optional<String> name() {
                return Optional.of("test-app");
            }

            @Override
            public Optional<String> version() {
                return Optional.empty();
            }

            @Override
            public Optional<String> description() {
                return Optional.empty();
            }

            @Override
            public String maintainer() {
                return "Test <test@example.com>";
            }

            @Override
            public String section() {
                return "web";
            }

            @Override
            public String priority() {
                return "optional";
            }

            @Override
            public Optional<String> architecture() {
                return Optional.of("all");
            }

            @Override
            public String depends() {
                return "systemd";
            }

            @Override
            public Optional<String> installDir() {
                return Optional.empty();
            }

            @Override
            public Optional<String> configDir() {
                return Optional.empty();
            }

            @Override
            public Optional<String> dataDir() {
                return Optional.empty();
            }

            @Override
            public Optional<String> logDir() {
                return Optional.empty();
            }

            @Override
            public Optional<String> binPath() {
                return Optional.empty();
            }

            @Override
            public Optional<String> defaultsPath() {
                return Optional.empty();
            }

            @Override
            public Optional<String> systemdUnitPath() {
                return Optional.empty();
            }

            @Override
            public Optional<String> serviceUser() {
                return Optional.empty();
            }

            @Override
            public Optional<String> serviceGroup() {
                return Optional.empty();
            }

            @Override
            public Optional<String> outputName() {
                return Optional.empty();
            }

            @Override
            public DebianConfigSection config() {
                return new DebianConfigSection() {
                    @Override
                    public boolean autoBridge() {
                        return true;
                    }

                    @Override
                    public Optional<String> filePath() {
                        return Optional.empty();
                    }

                    @Override
                    public ReloadConfig reload() {
                        return new ReloadConfig() {
                            @Override
                            public boolean enabled() {
                                return true;
                            }

                            @Override
                            public Optional<String> socketPath() {
                                return Optional.empty();
                            }
                        };
                    }
                };
            }
        };
    }
}
