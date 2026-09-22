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

package io.github.rawvoid.quarkus.debian.packaging.deployment.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.rawvoid.quarkus.debian.packaging.DebianPackagingConfig;
import io.github.rawvoid.quarkus.debian.packaging.deployment.model.DebianPackageModel;
import io.github.rawvoid.quarkus.debian.packaging.deployment.model.PackagePayload;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;

/**
 * Tests Debian payload packaging layouts.
 *
 * @author rawvoid
 */
class DebPackagerPayloadTest {

    @TempDir
    Path tempDir;

    @Test
    void packagesUberJarPayloadAndJvmLauncher() throws Exception {
        Path runner = tempDir.resolve("demo-runner.jar");
        Files.writeString(runner, "uber-content");

        Path deb = DebPackager.packageDeb(model("uber-demo", PackagePayload.uberJar(runner), Optional.empty()));
        Map<String, TarMember> data = readDataMembers(deb);

        assertTrue(data.containsKey("usr/share/uber-demo/demo-runner.jar"));
        assertEquals(DebEntry.MODE_FILE, data.get("usr/share/uber-demo/demo-runner.jar").mode() & 0777);
        assertTrue(data.containsKey("usr/share/uber-demo/reload"));
        assertEquals(DebEntry.MODE_EXEC, data.get("usr/share/uber-demo/reload").mode() & 0777);
        assertTrue(data.containsKey("usr/share/uber-demo/startup"));
        assertEquals(DebEntry.MODE_EXEC, data.get("usr/share/uber-demo/startup").mode() & 0777);
        assertFalse(data.containsKey("usr/share/uber-demo/environment"));
        assertTrue(data.containsKey("etc/uber-demo/jvm.options"));

        String launcher = new String(data.get("usr/bin/uber-demo").content(), StandardCharsets.UTF_8);
        assertTrue(launcher.contains("exec \"${INSTALL_DIR}/startup\""));

        String startup = new String(data.get("usr/share/uber-demo/startup").content(), StandardCharsets.UTF_8);
        assertTrue(startup.contains("exec \"${JAVA}\" -jar"));
        assertTrue(startup.contains("/usr/share/uber-demo/demo-runner.jar"));
    }

    @Test
    void packagesLegacyJarPayloadWithLibTree() throws Exception {
        Path runner = tempDir.resolve("legacy-runner.jar");
        Path lib = tempDir.resolve("lib");
        Files.createDirectories(lib);
        Files.writeString(runner, "legacy");
        Files.writeString(lib.resolve("dep.jar"), "dep");

        Path deb = DebPackager.packageDeb(model("legacy-demo", PackagePayload.legacyJar(runner, lib), Optional.empty()));
        Map<String, TarMember> data = readDataMembers(deb);

        assertTrue(data.containsKey("usr/share/legacy-demo/legacy-runner.jar"));
        assertTrue(data.containsKey("usr/share/legacy-demo/lib/dep.jar"));
        assertTrue(data.containsKey("usr/share/legacy-demo/startup"));
        assertTrue(data.containsKey("etc/legacy-demo/jvm.options"));
    }

    @Test
    void packagesNativePayloadWithExecutableAndNativeLauncher() throws Exception {
        Path binary = tempDir.resolve("native-demo-runner");
        Files.writeString(binary, "native-bin");

        Path deb = DebPackager.packageDeb(
                model("native-demo", PackagePayload.nativeImage(binary), Optional.of("amd64")));
        Map<String, TarMember> data = readDataMembers(deb);
        Map<String, String> control = readControlStrings(deb);

        assertTrue(data.containsKey("usr/share/native-demo/native-demo-runner"));
        assertEquals(DebEntry.MODE_EXEC, data.get("usr/share/native-demo/native-demo-runner").mode() & 0777);
        assertTrue(data.containsKey("usr/share/native-demo/reload"));
        assertEquals(DebEntry.MODE_EXEC, data.get("usr/share/native-demo/reload").mode() & 0777);
        assertTrue(data.containsKey("usr/share/native-demo/startup"));
        assertEquals(DebEntry.MODE_EXEC, data.get("usr/share/native-demo/startup").mode() & 0777);
        assertFalse(data.containsKey("usr/share/native-demo/environment"));
        assertFalse(data.containsKey("etc/native-demo/jvm.options"));

        String launcher = new String(data.get("usr/bin/native-demo").content(), StandardCharsets.UTF_8);
        assertTrue(launcher.contains("exec \"${INSTALL_DIR}/startup\""));

        String startup = new String(data.get("usr/share/native-demo/startup").content(), StandardCharsets.UTF_8);
        assertTrue(startup.contains("exec \"${MAIN_EXECUTABLE}\"")
                || startup.contains("/usr/share/native-demo/native-demo-runner"));
        assertFalse(startup.contains("java -jar"));

        assertTrue(control.get("control").contains("Architecture: amd64"));
        assertFalse(control.get("conffiles").contains("jvm.options"));
        assertFalse(control.get("prerm").contains("hsperfdata"), "native prerm should not clear JVM hsperfdata");
        assertTrue(control.get("prerm").contains("\n            :\n") || control.get("prerm").contains("            :"));
    }

    @Test
    void jvmPrermClearsHsPerfData() throws Exception {
        Path runner = tempDir.resolve("demo-runner.jar");
        Files.writeString(runner, "uber");
        Path deb = DebPackager.packageDeb(model("jvm-demo", PackagePayload.uberJar(runner), Optional.empty()));
        Map<String, String> control = readControlStrings(deb);
        assertTrue(control.get("prerm").contains("hsperfdata_jvm-demo"));
    }

    private DebianPackageModel model(String name, PackagePayload payload, Optional<String> architecture) {
        DebianPackagingConfig config = new DebianPackagingConfig() {
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
                return Optional.of("1.0.0");
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
                return architecture;
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

        return DebianPackageModel.resolve(
                config,
                new ApplicationInfoBuildItem(Optional.of(name), Optional.of("1.0.0")),
                new OutputTargetBuildItem(tempDir, name, name, false, new Properties(), Optional.empty()),
                payload);
    }

    private static Map<String, TarMember> readDataMembers(Path deb) throws Exception {
        Map<String, byte[]> ar = readAr(deb);
        return readTarGz(ar.get("data.tar.gz"));
    }

    private static Map<String, String> readControlStrings(Path deb) throws Exception {
        Map<String, byte[]> ar = readAr(deb);
        Map<String, TarMember> members = readTarGz(ar.get("control.tar.gz"));
        Map<String, String> strings = new HashMap<>();
        for (Map.Entry<String, TarMember> entry : members.entrySet()) {
            strings.put(entry.getKey(), new String(entry.getValue().content(), StandardCharsets.UTF_8));
        }
        return strings;
    }

    private static Map<String, byte[]> readAr(Path deb) throws Exception {
        Map<String, byte[]> members = new HashMap<>();
        try (InputStream in = Files.newInputStream(deb);
                ArArchiveInputStream ar = new ArArchiveInputStream(in)) {
            ArArchiveEntry entry;
            while ((entry = ar.getNextEntry()) != null) {
                members.put(entry.getName(), ar.readAllBytes());
            }
        }
        return members;
    }

    private static Map<String, TarMember> readTarGz(byte[] tarGz) throws Exception {
        Map<String, TarMember> members = new HashMap<>();
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(tarGz));
                TarArchiveInputStream tar = new TarArchiveInputStream(in)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.startsWith("./")) {
                    name = name.substring(2);
                }
                members.put(name, new TarMember(tar.readAllBytes(), entry.getMode()));
            }
        }
        return members;
    }

    /**
     * A member read from a tar archive.
     *
     * @author rawvoid
     */
    private record TarMember(byte[] content, int mode) {
    }
}
