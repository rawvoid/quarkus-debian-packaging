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

package io.github.rawvoid.quarkus.debian.packaging.test;

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
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

/**
 * Verifies Debian package generation in production mode.
 *
 * @author rawvoid
 */
public class DebianPackagingProdModeTest {

    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("application.properties"))
            .setApplicationName("prod-deb-app")
            .setApplicationVersion("1.0.0")
            .overrideConfigKey("quarkus.debian.maintainer", "CI <ci@example.com>");

    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    public void producesDebPackage() throws Exception {
        Path buildDir = prodModeTestResults.getBuildDir();
        Path deb = findDeb(buildDir);
        assertTrue(Files.isRegularFile(deb), () -> "Expected .deb under " + buildDir);

        Map<String, byte[]> ar = readAr(deb);
        Map<String, String> control = readTarGzAsStrings(ar.get("control.tar.gz"));
        Map<String, String> data = readTarGzAsStrings(ar.get("data.tar.gz"));

        assertTrue(control.get("control").contains("Package: prod-deb-app"));
        assertTrue(control.get("control").contains("Maintainer: CI <ci@example.com>"));
        assertTrue(control.get("control").contains("Depends: systemd, default-jre-headless | java-runtime-headless"));
        assertFalse(control.get("control").contains("python3"));
        assertTrue(control.get("control").contains("Architecture: all"));
        assertTrue(control.containsKey("postinst"));
        assertTrue(control.containsKey("conffiles"));
        assertTrue(control.get("conffiles").contains("/etc/default/prod-deb-app"));
        assertTrue(control.get("conffiles").contains("/etc/prod-deb-app/application.properties"));
        assertTrue(control.get("conffiles").contains("/etc/prod-deb-app/jvm.options"));

        assertFalse(data.containsKey("usr/share/prod-deb-app/reload"));
        assertTrue(data.containsKey("usr/share/prod-deb-app/startup"));
        assertTrue(data.containsKey("usr/bin/prod-deb-app"));
        assertTrue(data.containsKey("usr/lib/systemd/system/prod-deb-app.service"));
        assertFalse(data.get("usr/lib/systemd/system/prod-deb-app.service").contains("ExecReload="));
        assertTrue(data.containsKey("etc/default/prod-deb-app"));
        String launcher = data.get("usr/bin/prod-deb-app");
        assertTrue(launcher.contains("exec \"${INSTALL_DIR}/startup\""));
        assertFalse(launcher.contains("${installDir}"), "Launcher template variables must be resolved");
        assertFalse(launcher.contains("${defaultsFile}"), "Launcher template variables must be resolved");

        String startup = data.get("usr/share/prod-deb-app/startup");
        assertTrue(startup.contains("exec \"${JAVA}\" -jar"));
        assertTrue(startup.contains("/usr/share/prod-deb-app/quarkus-run.jar"));
        assertFalse(startup.contains("${quarkusRunner}"), "Startup template variables must be resolved");
        assertFalse(startup.contains("${jvmOptionsFile}"), "Startup template variables must be resolved");
        assertTrue(data.get("etc/default/prod-deb-app")
                .contains("# Runtime options for prod-deb-app.service."));
        assertFalse(data.get("etc/default/prod-deb-app")
                .contains("QUARKUS_CONFIG_LOCATIONS"));
        assertTrue(data.get("usr/lib/systemd/system/prod-deb-app.service").contains("ExecStart=/usr/bin/prod-deb-app"));
        assertTrue(data.get("usr/lib/systemd/system/prod-deb-app.service").contains("AmbientCapabilities=CAP_NET_BIND_SERVICE"));
        assertTrue(data.get("usr/lib/systemd/system/prod-deb-app.service").contains("CapabilityBoundingSet=CAP_NET_BIND_SERVICE"));
        assertFalse(data.get("usr/lib/systemd/system/prod-deb-app.service").contains("NoNewPrivileges"));

        String postinst = control.get("postinst");
        assertTrue(postinst.contains("CONFIG_FILE=\"/etc/prod-deb-app/application.properties\""));
        assertTrue(postinst.contains("JVM_OPTIONS_FILE=\"/etc/prod-deb-app/jvm.options\""));
        assertTrue(postinst.contains("chmod 0640 \"${CONFIG_FILE}\""));
        assertTrue(postinst.contains("chmod 0640 \"${JVM_OPTIONS_FILE}\""));
        assertTrue(postinst.contains("chown root:\"${SERVICE_GROUP}\" \"${CONFIG_FILE}\""));
        assertTrue(postinst.contains("chown root:\"${SERVICE_GROUP}\" \"${JVM_OPTIONS_FILE}\""));
    }

    private static Path findDeb(Path buildDir) throws IOException {
        try (Stream<Path> paths = Files.walk(buildDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".deb"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No .deb under " + buildDir));
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
