package io.github.rawvoid.quarkus.debian.packaging.deployment.builder;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

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
                "jvmOptionsFile", "/etc/demo/jvm.options",
                "mainExecutable", "/usr/share/demo/quarkus-run.jar"));
        assertTrue(launcher.contains("MAIN_JAR=\"/usr/share/demo/quarkus-run.jar\""));
        assertTrue(launcher.contains("JDK_JAVA_OPTIONS"));
    }
}
