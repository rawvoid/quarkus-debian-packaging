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

package io.github.rawvoid.quarkus.debian.packaging.it;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;

/**
 * Verifies that packaging the integration-tests module produces a Debian package
 * with the expected layout (no jdeb / src/deb required).
 *
 * @author rawvoid
 */
public class DebianPackagingDebIT {

    @Test
    void debPackageIsProducedWithExpectedLayout() throws Exception {
        Path targetDir = Path.of("target").toAbsolutePath().normalize();
        Path deb = findDeb(targetDir);
        assertTrue(Files.isRegularFile(deb), () -> "Expected a .deb under " + targetDir);

        Map<String, byte[]> ar = readAr(deb);
        assertTrue(ar.containsKey("control.tar.gz"));
        assertTrue(ar.containsKey("data.tar.gz"));

        Map<String, String> controlFiles = readTarGzAsStrings(ar.get("control.tar.gz"));
        assertTrue(controlFiles.containsKey("control"));
        assertTrue(controlFiles.containsKey("postinst"));
        assertTrue(controlFiles.containsKey("prerm"));
        assertTrue(controlFiles.containsKey("postrm"));
        assertTrue(controlFiles.containsKey("conffiles"));
        assertTrue(controlFiles.containsKey("md5sums"));

        String control = controlFiles.get("control");
        assertTrue(control.contains("Depends: systemd"));
        assertTrue(control.contains("Architecture: all"));
        assertTrue(control.contains("Installed-Size:"));

        String postinst = controlFiles.get("postinst");
        assertTrue(postinst.contains("create_service_account()"));
        assertTrue(postinst.contains("systemctl --system daemon-reload"));

        Map<String, String> dataFiles = readTarGzAsStrings(ar.get("data.tar.gz"));
        assertTrue(containsPathSuffix(dataFiles, "/quarkus-run.jar") || dataFiles.keySet().stream()
                .anyMatch(p -> p.endsWith("quarkus-run.jar")),
                "Expected quarkus-run.jar in package payload");
        assertTrue(dataFiles.keySet().stream().anyMatch(p -> p.startsWith("usr/bin/")),
                "Expected launcher under usr/bin");
        assertTrue(dataFiles.keySet().stream().anyMatch(p -> p.contains("systemd/system/") && p.endsWith(".service")),
                "Expected systemd unit");
        assertTrue(dataFiles.keySet().stream().anyMatch(p -> p.startsWith("etc/default/")),
                "Expected /etc/default file");
        assertTrue(dataFiles.keySet().stream().anyMatch(p -> p.endsWith("/application.properties")),
                "Expected external application.properties");
        assertTrue(dataFiles.keySet().stream().anyMatch(p -> p.endsWith("/jvm.options")),
                "Expected jvm.options for JVM package");
        assertTrue(dataFiles.keySet().stream().anyMatch(p -> p.endsWith("/startup")),
                "Expected startup script in package payload");
        assertFalse(dataFiles.keySet().stream().anyMatch(p -> p.endsWith("/environment")),
                "Did not expect environment script in package payload");

        String defaults = dataFiles.entrySet().stream()
                .filter(e -> e.getKey().startsWith("etc/default/"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow();
        assertTrue(defaults.contains("QUARKUS_CONFIG_LOCATIONS=file:"));

        String launcher = dataFiles.entrySet().stream()
                .filter(e -> e.getKey().startsWith("usr/bin/"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow();
        assertTrue(launcher.contains("exec \"${INSTALL_DIR}/startup\""));
        assertFalse(launcher.contains("[["), "Launcher must not contain unresolved codestart placeholders");

        String startup = dataFiles.entrySet().stream()
                .filter(e -> e.getKey().endsWith("/startup"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow();
        assertTrue(startup.contains("exec \"${JAVA}\" -jar"));
    }

    private static boolean containsPathSuffix(Map<String, String> dataFiles, String suffix) {
        return dataFiles.keySet().stream().anyMatch(p -> p.endsWith(suffix.startsWith("/") ? suffix.substring(1) : suffix));
    }

    private static Path findDeb(Path targetDir) throws IOException {
        try (Stream<Path> paths = Files.walk(targetDir, 2)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".deb"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No .deb found under " + targetDir));
        }
    }

    private static Map<String, byte[]> readAr(Path deb) throws IOException {
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

    private static Map<String, String> readTarGzAsStrings(byte[] tarGz) throws IOException {
        Map<String, String> members = new HashMap<>();
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
                byte[] bytes = tar.readAllBytes();
                // Skip binary jars for text assertions; store empty for presence checks
                if (name.endsWith(".jar") || name.endsWith(".dat")) {
                    members.put(name, "");
                } else {
                    members.put(name, new String(bytes, StandardCharsets.UTF_8));
                }
            }
        }
        return members;
    }
}
