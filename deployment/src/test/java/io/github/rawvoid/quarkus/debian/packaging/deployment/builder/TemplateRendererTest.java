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
                "version", "1.0.0",
                "architecture", "amd64",
                "installDir", "/usr/share/demo",
                "defaultsFile", "/etc/default/demo",
                "configFile", "/etc/demo/application.properties",
                "systemdServiceName", "demo.service"));
        assertTrue(launcher.contains("INSTALL_DIR=\"/usr/share/demo\""));
        assertTrue(launcher.contains("SYSTEMD_SERVICE_NAME=\"demo.service\""));
        assertTrue(launcher.contains("DEFAULTS_FILE=\"/etc/default/demo\""));
        assertTrue(launcher.contains("--reload)"));
        assertTrue(launcher.contains("exec \"${INSTALL_DIR}/reload\" \"$@\""));
        assertTrue(launcher.contains("--status|status)"));
        assertTrue(launcher.contains("exec systemctl status \"${SYSTEMD_SERVICE_NAME}\" \"$@\""));
        assertTrue(launcher.contains("--version|-v)"));
        assertTrue(launcher.contains("echo \"demo 1.0.0 (amd64)\""));
        assertTrue(launcher.contains("--help|-h)"));
        assertTrue(launcher.contains("Usage: demo [OPTIONS|COMMAND]"));
        assertTrue(launcher.contains("sudo systemctl {start|stop|restart|status|reload} demo.service"));
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

        String scriptContent = TemplateRenderer.render("launcher.sh", launcherVars("demo", tempDir, defaultsFile));

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

        String scriptContent = TemplateRenderer.render("launcher.sh", launcherVars("demo", tempDir, defaultsFile));

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

        String scriptContent = TemplateRenderer.render("launcher.sh", launcherVars("demo", tempDir, defaultsFile));

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
    void launcherReportsDisabledReloadWhenHelperScriptMissing(@TempDir Path tempDir) throws Exception {
        Path defaultsFile = tempDir.resolve("defaults");
        Files.writeString(defaultsFile, "KEY=val\n");

        String scriptContent = TemplateRenderer.render("launcher.sh", launcherVars("demo", tempDir, defaultsFile));

        Path launcherScript = tempDir.resolve("launcher.sh");
        Files.writeString(launcherScript, scriptContent);
        setPosixExecutable(launcherScript);

        var pb = new ProcessBuilder("sh", launcherScript.toString(), "--reload");
        var process = pb.start();
        String errorOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertEquals(1, exitCode);
        assertTrue(errorOutput.contains("Configuration hot-reload is disabled for this build"));
        assertTrue(errorOutput.contains("quarkus.debian.reload.enabled=true"));
    }

    @Test
    void launcherHandlesVersionFastPath(@TempDir Path tempDir) throws Exception {
        String scriptContent = TemplateRenderer.render("launcher.sh", launcherVars("demo", tempDir, tempDir.resolve("defaults")));
        Path launcherScript = tempDir.resolve("launcher.sh");
        Files.writeString(launcherScript, scriptContent);
        setPosixExecutable(launcherScript);

        var pb = new ProcessBuilder("sh", launcherScript.toString(), "--version");
        var process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exitCode = process.waitFor();

        assertEquals(0, exitCode);
        assertEquals("demo 1.0.0 (all)", output);

        var pbShort = new ProcessBuilder("sh", launcherScript.toString(), "-v");
        var processShort = pbShort.start();
        String outputShort = new String(processShort.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertEquals(0, processShort.waitFor());
        assertEquals("demo 1.0.0 (all)", outputShort);
    }

    @Test
    void launcherHandlesHelpFastPath(@TempDir Path tempDir) throws Exception {
        String scriptContent = TemplateRenderer.render("launcher.sh", launcherVars("demo", tempDir, tempDir.resolve("defaults")));
        Path launcherScript = tempDir.resolve("launcher.sh");
        Files.writeString(launcherScript, scriptContent);
        setPosixExecutable(launcherScript);

        var pb = new ProcessBuilder("sh", launcherScript.toString(), "--help");
        var process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertEquals(0, exitCode);
        assertTrue(output.contains("Usage: demo [OPTIONS|COMMAND]"));
        assertTrue(output.contains("--reload"));
        assertTrue(output.contains("--status, status"));
        assertTrue(output.contains("sudo systemctl {start|stop|restart|status|reload} demo.service"));

        var pbShort = new ProcessBuilder("sh", launcherScript.toString(), "-h");
        var processShort = pbShort.start();
        String outputShort = new String(processShort.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, processShort.waitFor());
        assertTrue(outputShort.contains("Usage: demo [OPTIONS|COMMAND]"));
    }

    @Test
    void launcherHandlesStatusDelegation(@TempDir Path tempDir) throws Exception {
        Path binDir = tempDir.resolve("bin");
        Files.createDirectories(binDir);
        Path mockSystemctl = binDir.resolve("systemctl");
        Files.writeString(mockSystemctl, """
                #!/bin/sh
                echo "mocked-systemctl: $*"
                exit 0
                """);
        setPosixExecutable(mockSystemctl);

        String scriptContent = TemplateRenderer.render("launcher.sh", launcherVars("demo", tempDir, tempDir.resolve("defaults")));
        Path launcherScript = tempDir.resolve("launcher.sh");
        Files.writeString(launcherScript, scriptContent);
        setPosixExecutable(launcherScript);

        var pb = new ProcessBuilder("sh", launcherScript.toString(), "status", "-n", "10");
        String pathEnv = binDir + ":" + System.getenv().getOrDefault("PATH", "");
        pb.environment().put("PATH", pathEnv);
        var process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exitCode = process.waitFor();

        assertEquals(0, exitCode);
        assertEquals("mocked-systemctl: status demo.service -n 10", output);

        var pbFlag = new ProcessBuilder("sh", launcherScript.toString(), "--status");
        pbFlag.environment().put("PATH", pathEnv);
        var processFlag = pbFlag.start();
        String outputFlag = new String(processFlag.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertEquals(0, processFlag.waitFor());
        assertEquals("mocked-systemctl: status demo.service", outputFlag);
    }

    @Test
    void launcherFailsStatusWhenSystemctlMissing(@TempDir Path tempDir) throws Exception {
        Path emptyBinDir = tempDir.resolve("empty-bin");
        Files.createDirectories(emptyBinDir);

        String scriptContent = TemplateRenderer.render("launcher.sh", launcherVars("demo", tempDir, tempDir.resolve("defaults")));
        Path launcherScript = tempDir.resolve("launcher.sh");
        Files.writeString(launcherScript, scriptContent);
        setPosixExecutable(launcherScript);

        var pb = new ProcessBuilder("sh", launcherScript.toString(), "status");
        pb.environment().put("PATH", emptyBinDir.toString());
        var process = pb.start();
        String errOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertNotEquals(0, exitCode);
        assertTrue(errOutput.contains("systemctl command not found"));
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

        String scriptContent = TemplateRenderer.render("launcher.sh", launcherVars("demo", tempDir, defaultsFile));

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

        String scriptContent = TemplateRenderer.render("launcher.sh", launcherVars("demo", tempDir, defaultsFile));

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
        String scriptContent = TemplateRenderer.render("launcher.sh", launcherVars("demo", tempDir, tempDir.resolve("defaults")));

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

    private static Map<String, String> launcherVars(String packageName, Path installDir, Path defaultsFile) {
        return Map.of(
                "packageName", packageName,
                "version", "1.0.0",
                "architecture", "all",
                "installDir", installDir.toString(),
                "defaultsFile", defaultsFile.toString(),
                "configFile", installDir.resolve("application.properties").toString(),
                "systemdServiceName", packageName + ".service");
    }

    private static void setPosixExecutable(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (UnsupportedOperationException ignored) {
            path.toFile().setExecutable(true);
        }
    }
}
