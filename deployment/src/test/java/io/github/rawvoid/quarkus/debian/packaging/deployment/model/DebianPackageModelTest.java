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
        };
    }
}
