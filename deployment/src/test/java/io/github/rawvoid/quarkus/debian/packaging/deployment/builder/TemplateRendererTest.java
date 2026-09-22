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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests Debian template rendering.
 *
 * @author rawvoid
 */
class TemplateRendererTest {

    @Test
    void rendersControlTemplate() {
        String control = TemplateRenderer.render("control", Map.of(
                "packageName", "demo",
                "version", "1.0.0",
                "section", "web",
                "priority", "optional",
                "architecture", "all",
                "depends", "systemd",
                "installedSize", "12",
                "maintainer", "Dev <dev@example.com>",
                "description", "demo service"));
        assertTrue(control.contains("Package: demo"));
        assertTrue(control.contains("Installed-Size: 12"));
        assertTrue(control.contains("Description: demo service"));
    }

    @Test
    void rendersJvmLauncher() {
        String launcher = TemplateRenderer.render("launcher-jvm.sh", Map.of(
                "packageName", "demo",
                "systemdServiceName", "demo.service",
                "defaultsFile", "/etc/default/demo",
                "jvmOptionsFile", "/etc/demo/jvm.options",
                "mainExecutable", "/usr/share/demo/quarkus-run.jar"));
        assertTrue(launcher.contains("DEFAULTS_FILE=\"/etc/default/demo\""));
        assertTrue(launcher.contains("MAIN_JAR=\"/usr/share/demo/quarkus-run.jar\""));
        assertTrue(launcher.contains("JDK_JAVA_OPTIONS"));
        assertTrue(launcher.contains("if [ \"${1:-}\" = \"--reload\" ]; then"));
        assertTrue(launcher.contains("SOCKET_FILE=\"/run/demo/control.sock\""));
        assertTrue(launcher.contains("exec \"${JAVA}\" -jar \"${MAIN_JAR}\" \"$@\""));
    }

    @Test
    void rendersNativeLauncher() {
        String launcher = TemplateRenderer.render("launcher-native.sh", Map.of(
                "packageName", "demo",
                "systemdServiceName", "demo.service",
                "defaultsFile", "/etc/default/demo",
                "mainExecutable", "/usr/share/demo/demo-runner"));
        assertTrue(launcher.contains("DEFAULTS_FILE=\"/etc/default/demo\""));
        assertTrue(launcher.contains("MAIN_EXECUTABLE=\"/usr/share/demo/demo-runner\""));
        assertTrue(launcher.contains("if [ \"${1:-}\" = \"--reload\" ]; then"));
        assertTrue(launcher.contains("SOCKET_FILE=\"/run/demo/control.sock\""));
        assertTrue(launcher.contains("exec \"${MAIN_EXECUTABLE}\" \"$@\""));
    }

    @Test
    void rendersUnitService() {
        String service = TemplateRenderer.render("unit.service", Map.of(
                "description", "Demo Service",
                "serviceUser", "demo",
                "serviceGroup", "demo",
                "installDir", "/usr/share/demo",
                "defaultsFile", "/etc/default/demo",
                "binFile", "/usr/bin/demo",
                "packageName", "demo"));
        assertTrue(service.contains("ExecStart=/usr/bin/demo"));
        assertTrue(service.contains("ExecReload=/usr/bin/demo --reload"));
        assertTrue(service.contains("RuntimeDirectory=demo"));
        assertTrue(service.contains("RuntimeDirectoryMode=0750"));
    }
}
