package io.github.rawvoid.quarkus.debian.packaging.it;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartCatalog.Language;
import io.quarkus.devtools.testing.codestarts.QuarkusCodestartTest;

public class QuarkusDebianPackagingCodestartTest {

    @RegisterExtension
    public static QuarkusCodestartTest codestartTest = QuarkusCodestartTest.builder()
        .languages(Language.JAVA)
        .setupStandaloneExtensionTest("io.github.rawvoid:quarkus-debian-packaging")
        .build();

    @Test
    void testContent() throws Throwable {
        Path projectDir = codestartTest.getProjectWithRealDataDir(Language.JAVA);
        Path pom = projectDir.resolve("pom.xml");

        assertFileContains(pom,
                "<resources-plugin.version>3.5.0</resources-plugin.version>",
                "<jdeb-plugin.version>1.14</jdeb-plugin.version>",
                "<deb.package.name>${project.artifactId}</deb.package.name>",
                "<artifactId>maven-resources-plugin</artifactId>",
                "<artifactId>jdeb</artifactId>");

        assertFileContains(projectDir.resolve("src/deb/control/control"),
                "Package: [[deb.package.name]]",
                "Depends: systemd",
                "Description: [[deb.description]]");
        assertFileContains(projectDir.resolve("src/deb/control/postinst"),
                "create_service_account()",
                "systemctl --system daemon-reload");
        assertFileContains(projectDir.resolve("src/deb/control/prerm"),
                "mark_service_for_upgrade_restart()",
                "systemctl --system stop");
        assertFileContains(projectDir.resolve("src/deb/control/postrm"),
                "remove_service_account()",
                "remove_directories()");

        Path launcher = singleFile(projectDir.resolve("src/deb/data/usr/bin"));
        assertFileContains(launcher,
                "MAIN_JAR=\"[[deb.main.jar]]\"",
                "JDK_JAVA_OPTIONS",
                "exec \"${JAVA}\" -jar \"${MAIN_JAR}\"");

        Path systemdUnit = singleFile(projectDir.resolve("src/deb/data/usr/lib/systemd/system"));
        assertFileContains(systemdUnit,
                "Description=[[deb.description]]",
                "EnvironmentFile=-[[deb.defaults.file]]",
                "ExecStart=[[deb.bin.file]]");

        Path defaults = singleFile(projectDir.resolve("src/deb/data/etc/default"));
        assertFileContains(defaults,
                "QUARKUS_CONFIG_LOCATIONS=file:[[deb.config.file]]");

        Path etcDir = projectDir.resolve("src/deb/data/etc").resolve(defaults.getFileName().toString());
        Path externalConfig = etcDir.resolve("application.properties");
        assertFileContains(externalConfig,
                "quarkus.http.port=8080",
                "quarkus.log.level=INFO");

        Path jvmOptions = etcDir.resolve("jvm.options");
        assertFileContains(jvmOptions,
                "-XX:+PrintCommandLineFlags",
                "-XX:+ExitOnOutOfMemoryError");

        assertDebFilesDoNotContain(projectDir, "fare-ingress");
    }

    @Test
    void buildAllProjects() throws Throwable {
        codestartTest.buildAllProjects();
        assertDebPackageGenerated();
    }

    private static Path singleFile(Path dir) throws IOException {
        try (Stream<Path> paths = Files.list(dir)) {
            List<Path> files = paths.filter(Files::isRegularFile).toList();
            assertTrue(files.size() == 1, () -> "Expected exactly one generated file under " + dir + " but found " + files);
            return files.get(0);
        }
    }

    private static void assertFileContains(Path path, String... expected) throws IOException {
        assertTrue(Files.isRegularFile(path), () -> "Expected file to exist: " + path);
        String content = Files.readString(path);
        for (String value : expected) {
            assertTrue(content.contains(value), () -> "Expected " + path + " to contain: " + value);
        }
    }

    private static void assertDebFilesDoNotContain(Path projectDir, String unexpected) throws IOException {
        try (Stream<Path> paths = Files.walk(projectDir.resolve("src/deb"))) {
            List<Path> files = paths.filter(Files::isRegularFile).toList();
            assertFalse(files.isEmpty(), () -> "Expected generated Debian files under " + projectDir.resolve("src/deb"));
            for (Path file : files) {
                String content = Files.readString(file);
                assertFalse(content.contains(unexpected), () -> "Did not expect " + file + " to contain: " + unexpected);
            }
        }
    }

    private static void assertDebPackageGenerated() throws IOException {
        Path projectDir = codestartTest.getProjectWithRealDataDir(Language.JAVA);
        try (Stream<Path> paths = Files.walk(projectDir.resolve("target"))) {
            boolean hasDebPackage = paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .anyMatch(name -> name.endsWith("_all.deb"));
            assertTrue(hasDebPackage, () -> "Expected generated project build to create a Debian package under "
                    + projectDir.resolve("target"));
        }
    }
}
