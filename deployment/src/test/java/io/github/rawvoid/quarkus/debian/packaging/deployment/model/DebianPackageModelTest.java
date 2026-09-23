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

package io.github.rawvoid.quarkus.debian.packaging.deployment.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.rawvoid.quarkus.debian.packaging.DebianPackagingConfig;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;

/**
 * Tests resolution and validation of Debian package metadata.
 *
 * @author rawvoid
 */
class DebianPackageModelTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsSingleCharacterPackageName() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> resolve(configWithName("a"), payload()));
        assertTrue(error.getMessage().contains("at least two characters"));
    }

    @Test
    void acceptsTwoCharacterPackageName() {
        DebianPackageModel model = resolve(configWithName("ab"), payload());
        assertEquals("ab", model.packageName());
    }

    @Test
    void normalizesUnderscoresAndSpacesInPackageName() {
        assertEquals("my-app-service", DebianPackageModel.sanitizePackageName("My_App Service"));
        DebianPackageModel model = resolve(configWithName("hello_world"), payload());
        assertEquals("hello-world", model.packageName());
    }

    @Test
    void collapsesRepeatedHyphensWhenNormalizingPackageName() {
        assertEquals("foo-bar", DebianPackageModel.sanitizePackageName("foo__bar"));
        assertEquals("foo-bar", DebianPackageModel.sanitizePackageName("foo--bar"));
    }

    @Test
    void stripsTrailingSlashesFromConfiguredPaths() {
        DebianPackageModel model = resolve(configWithInstallDir("myapp", "/usr/share/myapp/"), payload());
        assertEquals("/usr/share/myapp", model.installDir());
        assertEquals("/usr/share/myapp/app-runner.jar", model.quarkusRunner());
    }

    @Test
    void rejectsRelativeConfiguredPaths() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> resolve(configWithInstallDir("myapp", "usr/share/myapp"), payload()));
        assertTrue(error.getMessage().contains("absolute path"));
    }

    @Test
    void derivesUnixAccountFromPackageNameWithDotsAndPlus() {
        assertEquals("my-app-1", DebianPackageModel.deriveUnixAccountName("my.app+1"));
    }

    @Test
    void prefixesLeadingDigitWhenDerivingUnixAccount() {
        assertEquals("_1demo", DebianPackageModel.deriveUnixAccountName("1demo"));
    }

    @Test
    void truncatesDerivedUnixAccountTo32Characters() {
        String longName = "a".repeat(40);
        String account = DebianPackageModel.deriveUnixAccountName(longName);
        assertEquals(32, account.length());
        assertEquals("a".repeat(32), account);
    }

    @Test
    void defaultsServiceUserAndGroupFromPackageName() {
        DebianPackageModel model = resolve(configWithName("my.app"), payload());
        assertEquals("my-app", model.serviceUser());
        assertEquals("my-app", model.serviceGroup());
    }

    @Test
    void rejectsInvalidExplicitServiceUser() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> resolve(configWithNameAndUser("myapp", "bad.user"), payload()));
        assertTrue(error.getMessage().contains("bad.user"));
    }

    @Test
    void acceptsExplicitServiceUserAndGroup() {
        DebianPackageModel model = resolve(configWithAccounts("my.app", "svc_user", "svc_group"), payload());
        assertEquals("svc_user", model.serviceUser());
        assertEquals("svc_group", model.serviceGroup());
    }

    private DebianPackageModel resolve(DebianPackagingConfig config, PackagePayload payload) {
        return DebianPackageModel.resolve(
                config,
                new ApplicationInfoBuildItem(Optional.of("fallback"), Optional.of("1.0.0")),
                new OutputTargetBuildItem(tempDir, "app", "app", false, new Properties(), Optional.empty()),
                payload);
    }

    private static PackagePayload payload() {
        return PackagePayload.uberJar(Path.of("app-runner.jar"));
    }

    private static DebianPackagingConfig configWithName(String name) {
        return config(name, Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static DebianPackagingConfig configWithInstallDir(String name, String installDir) {
        return config(name, Optional.empty(), Optional.empty(), Optional.of(installDir));
    }

    private static DebianPackagingConfig configWithNameAndUser(String name, String serviceUser) {
        return config(name, Optional.of(serviceUser), Optional.empty(), Optional.empty());
    }

    private static DebianPackagingConfig configWithAccounts(String name, String serviceUser, String serviceGroup) {
        return config(name, Optional.of(serviceUser), Optional.of(serviceGroup), Optional.empty());
    }

    private static DebianPackagingConfig config(
            String name,
            Optional<String> serviceUser,
            Optional<String> serviceGroup,
            Optional<String> installDir) {
        return new DebianPackagingConfig() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public Optional<String> name() {
                return Optional.of(name);
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
                return Optional.empty();
            }

            @Override
            public String depends() {
                return "systemd";
            }

            @Override
            public Optional<String> installDir() {
                return installDir;
            }

            @Override
            public Optional<String> configDir() {
                return Optional.empty();
            }

            @Override
            public Optional<String> configFile() {
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
            public Optional<String> executableFile() {
                return Optional.empty();
            }

            @Override
            public Optional<String> defaultsFile() {
                return Optional.empty();
            }

            @Override
            public Optional<String> systemdUnitFile() {
                return Optional.empty();
            }

            @Override
            public Optional<String> serviceUser() {
                return serviceUser;
            }

            @Override
            public Optional<String> serviceGroup() {
                return serviceGroup;
            }

            @Override
            public Optional<String> outputName() {
                return Optional.empty();
            }
        };
    }
}
