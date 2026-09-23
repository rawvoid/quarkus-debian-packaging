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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void rendersLauncherScript() {
        String launcher = TemplateRenderer.render("launcher.sh", Map.of(
                "packageName", "demo",
                "installDir", "/usr/share/demo",
                "defaultsFile", "/etc/default/demo"));
        assertTrue(launcher.contains("INSTALL_DIR=\"/usr/share/demo\""));
        assertTrue(launcher.contains("DEFAULTS_FILE=\"/etc/default/demo\""));
        assertTrue(launcher.contains("--reload)"));
        assertTrue(launcher.contains("exec \"${INSTALL_DIR}/reload\" \"$@\""));
        assertTrue(launcher.contains("export \"${key}=${val}\""));
        assertTrue(launcher.contains("exec \"${INSTALL_DIR}/startup\" \"$@\""));
    }

    @Test
    void rendersJvmStartupScript() {
        String startup = TemplateRenderer.render("startup-jvm.sh", Map.of(
                "jvmOptionsFile", "/etc/demo/jvm.options",
                "quarkusRunner", "/usr/share/demo/quarkus-run.jar"));
        assertTrue(startup.contains("QUARKUS_RUNNER=\"/usr/share/demo/quarkus-run.jar\""));
        assertTrue(startup.contains("JVM_OPTIONS_FILE=\"/etc/demo/jvm.options\""));
        assertTrue(startup.contains("JDK_JAVA_OPTIONS"));
        assertTrue(startup.contains("exec \"${JAVA}\" -jar \"${QUARKUS_RUNNER}\" \"$@\""));
    }

    @Test
    void rendersNativeStartupScript() {
        String startup = TemplateRenderer.render("startup-native.sh", Map.of(
                "quarkusRunner", "/usr/share/demo/demo-runner"));
        assertTrue(startup.contains("QUARKUS_RUNNER=\"/usr/share/demo/demo-runner\""));
        assertTrue(startup.contains("exec \"${QUARKUS_RUNNER}\" \"$@\""));
    }

    @Test
    void rendersReloadScript() {
        String reload = TemplateRenderer.render("reload.py", Map.of(
                "packageName", "demo",
                "systemdServiceName", "demo.service"));
        assertTrue(reload.contains("/run/demo/control.sock"));
        assertTrue(reload.contains("demo.service"));
        assertTrue(reload.contains("import socket"));
    }

    @Test
    void rendersUnitService() {
        String service = TemplateRenderer.render("unit.service", Map.of(
                "description", "Demo Service",
                "serviceUser", "demo",
                "serviceGroup", "demo",
                "installDir", "/usr/share/demo",
                "defaultsFile", "/etc/default/demo",
                "executableFile", "/usr/bin/demo",
                "packageName", "demo"));
        assertTrue(service.contains("ExecStart=/usr/bin/demo"));
        assertTrue(service.contains("ExecReload=/usr/bin/demo --reload"));
        assertTrue(service.contains("RuntimeDirectory=demo"));
        assertTrue(service.contains("RuntimeDirectoryMode=0750"));
        assertFalse(service.contains("EnvironmentFile"));
    }

    @Test
    void jvmLauncherParsesDefaultsWithSpecialCharacters(@TempDir Path tempDir) throws Exception {
        Path defaultsFile = tempDir.resolve("defaults");
        Files.writeString(defaultsFile, """
                # Comment
                ; Semicolon comment
                   # Indented comment
                PASSWORD=y]@95#<r1Yxg
                URL=https://example.com/api?a=1&b=2
                AMP=foo&bar
                REDIR=<foo>bar
                PIPE=a|b
                SEMICOLON=a;b
                QUOTED_SPACES="  spaced  "
                SINGLE_QUOTED='single quoted'
                export EXPORTED_VAR=exported value
                EMPTY=
                """);

        Path binDir = tempDir.resolve("bin");
        Files.createDirectories(binDir);
        Path mockJava = binDir.resolve("java");
        Files.writeString(mockJava, """
                #!/bin/sh
                echo "PASSWORD=$PASSWORD"
                echo "URL=$URL"
                echo "AMP=$AMP"
                echo "REDIR=$REDIR"
                echo "PIPE=$PIPE"
                echo "SEMICOLON=$SEMICOLON"
                echo "QUOTED_SPACES=$QUOTED_SPACES"
                echo "SINGLE_QUOTED=$SINGLE_QUOTED"
                echo "EXPORTED_VAR=$EXPORTED_VAR"
                echo "EMPTY=$EMPTY"
                """);
        setPosixExecutable(mockJava);

        Path mainJar = tempDir.resolve("dummy.jar");
        Files.writeString(mainJar, "jar-content");

        Path jvmOptions = tempDir.resolve("jvm.options");
        Files.writeString(jvmOptions, "");

        Path startupScript = tempDir.resolve("startup");
        Files.writeString(startupScript, TemplateRenderer.render("startup-jvm.sh", Map.of(
                "jvmOptionsFile", jvmOptions.toString(),
                "quarkusRunner", mainJar.toString())));
        setPosixExecutable(startupScript);

        String scriptContent = TemplateRenderer.render("launcher.sh", Map.of(
                "packageName", "demo",
                "installDir", tempDir.toString(),
                "defaultsFile", defaultsFile.toString()));

        Path launcherScript = tempDir.resolve("launcher.sh");
        Files.writeString(launcherScript, scriptContent);
        setPosixExecutable(launcherScript);

        var pb = new ProcessBuilder("sh", launcherScript.toString());
        pb.environment().put("JAVA_HOME", tempDir.toString());
        var process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String errOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertEquals(0, exitCode, "Launcher failed with error: " + errOutput);
        assertTrue(output.contains("PASSWORD=y]@95#<r1Yxg"));
        assertTrue(output.contains("URL=https://example.com/api?a=1&b=2"));
        assertTrue(output.contains("AMP=foo&bar"));
        assertTrue(output.contains("REDIR=<foo>bar"));
        assertTrue(output.contains("PIPE=a|b"));
        assertTrue(output.contains("SEMICOLON=a;b"));
        assertTrue(output.contains("QUOTED_SPACES=  spaced  "));
        assertTrue(output.contains("SINGLE_QUOTED=single quoted"));
        assertTrue(output.contains("EXPORTED_VAR=exported value"));
        assertTrue(output.contains("EMPTY="));
    }

    @Test
    void nativeLauncherParsesDefaultsWithSpecialCharacters(@TempDir Path tempDir) throws Exception {
        Path defaultsFile = tempDir.resolve("defaults");
        Files.writeString(defaultsFile, "PASSWORD=y]@95#<r1Yxg\n");

        Path mockRunner = tempDir.resolve("demo-runner");
        Files.writeString(mockRunner, """
                #!/bin/sh
                echo "PASSWORD=$PASSWORD"
                """);
        setPosixExecutable(mockRunner);

        Path startupScript = tempDir.resolve("startup");
        Files.writeString(startupScript, TemplateRenderer.render("startup-native.sh", Map.of(
                "quarkusRunner", mockRunner.toString())));
        setPosixExecutable(startupScript);

        String scriptContent = TemplateRenderer.render("launcher.sh", Map.of(
                "packageName", "demo",
                "installDir", tempDir.toString(),
                "defaultsFile", defaultsFile.toString()));

        Path launcherScript = tempDir.resolve("launcher.sh");
        Files.writeString(launcherScript, scriptContent);
        setPosixExecutable(launcherScript);

        var pb = new ProcessBuilder("sh", launcherScript.toString());
        var process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String errOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertEquals(0, exitCode, "Launcher failed with error: " + errOutput);
        assertTrue(output.contains("PASSWORD=y]@95#<r1Yxg"));
    }

    @Test
    void launcherHandlesReloadFastPathWithoutEvaluatingDefaults(@TempDir Path tempDir) throws Exception {
        Path defaultsFile = tempDir.resolve("defaults");
        Files.writeString(defaultsFile, "THIS IS COMPLETELY CORRUPTED CONTENT NOT KEY VAL");

        Path reloadScript = tempDir.resolve("reload");
        Files.writeString(reloadScript, """
                #!/bin/sh
                echo "RELOAD_INVOKED"
                """);
        setPosixExecutable(reloadScript);

        String scriptContent = TemplateRenderer.render("launcher.sh", Map.of(
                "packageName", "demo",
                "installDir", tempDir.toString(),
                "defaultsFile", defaultsFile.toString()));

        Path launcherScript = tempDir.resolve("launcher.sh");
        Files.writeString(launcherScript, scriptContent);
        setPosixExecutable(launcherScript);

        var pb = new ProcessBuilder("sh", launcherScript.toString(), "--reload");
        var process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertEquals(0, exitCode);
        assertTrue(output.contains("RELOAD_INVOKED"));
    }

    @Test
    void jvmLauncherFailsOnMissingEquals(@TempDir Path tempDir) throws Exception {
        Path defaultsFile = tempDir.resolve("defaults");
        Files.writeString(defaultsFile, "DANGLING_LINE_WITHOUT_EQUALS\n");

        Path binDir = tempDir.resolve("bin");
        Files.createDirectories(binDir);
        Path mockJava = binDir.resolve("java");
        Files.writeString(mockJava, "#!/bin/sh\nexit 0\n");
        setPosixExecutable(mockJava);

        Path mainJar = tempDir.resolve("dummy.jar");
        Files.writeString(mainJar, "jar-content");

        Path startupScript = tempDir.resolve("startup");
        Files.writeString(startupScript, TemplateRenderer.render("startup-jvm.sh", Map.of(
                "jvmOptionsFile", tempDir.resolve("jvm.options").toString(),
                "quarkusRunner", mainJar.toString())));
        setPosixExecutable(startupScript);

        String scriptContent = TemplateRenderer.render("launcher.sh", Map.of(
                "packageName", "demo",
                "installDir", tempDir.toString(),
                "defaultsFile", defaultsFile.toString()));

        Path launcherScript = tempDir.resolve("launcher.sh");
        Files.writeString(launcherScript, scriptContent);
        setPosixExecutable(launcherScript);

        var pb = new ProcessBuilder("sh", launcherScript.toString());
        pb.environment().put("JAVA_HOME", tempDir.toString());
        var process = pb.start();
        String errOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertNotEquals(0, exitCode);
        assertTrue(errOutput.contains("missing '='"));
    }

    @Test
    void jvmLauncherFailsOnInvalidVariableName(@TempDir Path tempDir) throws Exception {
        Path defaultsFile = tempDir.resolve("defaults");
        Files.writeString(defaultsFile, "INVALID-KEY=val\n");

        Path binDir = tempDir.resolve("bin");
        Files.createDirectories(binDir);
        Path mockJava = binDir.resolve("java");
        Files.writeString(mockJava, "#!/bin/sh\nexit 0\n");
        setPosixExecutable(mockJava);

        Path mainJar = tempDir.resolve("dummy.jar");
        Files.writeString(mainJar, "jar-content");

        Path startupScript = tempDir.resolve("startup");
        Files.writeString(startupScript, TemplateRenderer.render("startup-jvm.sh", Map.of(
                "jvmOptionsFile", tempDir.resolve("jvm.options").toString(),
                "quarkusRunner", mainJar.toString())));
        setPosixExecutable(startupScript);

        String scriptContent = TemplateRenderer.render("launcher.sh", Map.of(
                "packageName", "demo",
                "installDir", tempDir.toString(),
                "defaultsFile", defaultsFile.toString()));

        Path launcherScript = tempDir.resolve("launcher.sh");
        Files.writeString(launcherScript, scriptContent);
        setPosixExecutable(launcherScript);

        var pb = new ProcessBuilder("sh", launcherScript.toString());
        pb.environment().put("JAVA_HOME", tempDir.toString());
        var process = pb.start();
        int exitCode = process.waitFor();

        assertNotEquals(0, exitCode);
    }

    @Test
    void launcherFailsWhenStartupScriptMissing(@TempDir Path tempDir) throws Exception {
        String scriptContent = TemplateRenderer.render("launcher.sh", Map.of(
                "packageName", "demo",
                "installDir", tempDir.toString(),
                "defaultsFile", tempDir.resolve("defaults").toString()));

        Path launcherScript = tempDir.resolve("launcher.sh");
        Files.writeString(launcherScript, scriptContent);
        setPosixExecutable(launcherScript);

        var pb = new ProcessBuilder("sh", launcherScript.toString());
        var process = pb.start();
        String errOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertNotEquals(0, exitCode);
        assertTrue(errOutput.contains("Startup script not found"));
    }

    private static void setPosixExecutable(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (UnsupportedOperationException ignored) {
            path.toFile().setExecutable(true);
        }
    }
}
