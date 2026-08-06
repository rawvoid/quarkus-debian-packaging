package io.github.rawvoid.quarkus.debian.packaging.deployment.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DebBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsValidDebWithControlAndData() throws Exception {
        Path payload = tempDir.resolve("app.txt");
        Files.writeString(payload, "hello-deb");

        List<DebEntry> data = List.of(
                DebEntry.file("usr/share/demo/app.txt", payload, DebEntry.MODE_FILE, false),
                DebEntry.bytes("usr/bin/demo", "#!/bin/sh\necho demo\n".getBytes(StandardCharsets.UTF_8), DebEntry.MODE_EXEC,
                        false),
                DebEntry.bytes("etc/default/demo", "FOO=bar\n".getBytes(StandardCharsets.UTF_8), DebEntry.MODE_FILE, true));

        String control = """
                Package: demo
                Version: 1.0.0
                Section: web
                Priority: optional
                Architecture: all
                Depends: systemd
                Installed-Size: 1
                Maintainer: Test <test@example.com>
                Description: demo service
                """;

        List<DebEntry> controlEntries = List.of(
                DebEntry.bytes("postinst", "#!/bin/sh\nexit 0\n".getBytes(StandardCharsets.UTF_8), DebEntry.MODE_EXEC, false));

        Path deb = tempDir.resolve("demo_1.0.0_all.deb");
        DebBuilder.build(deb, control, controlEntries, data);

        assertTrue(Files.isRegularFile(deb));
        assertTrue(Files.size(deb) > 0);

        Map<String, byte[]> arMembers = readAr(deb);
        assertTrue(arMembers.containsKey("debian-binary"));
        assertEquals("2.0\n", new String(arMembers.get("debian-binary"), StandardCharsets.US_ASCII));
        assertTrue(arMembers.containsKey("control.tar.gz"));
        assertTrue(arMembers.containsKey("data.tar.gz"));

        Map<String, TarMember> controlMembers = readTarGz(arMembers.get("control.tar.gz"));
        assertTrue(controlMembers.containsKey("control"));
        assertTrue(controlMembers.containsKey("md5sums"));
        assertTrue(controlMembers.containsKey("conffiles"));
        assertTrue(controlMembers.containsKey("postinst"));
        assertEquals(DebEntry.MODE_EXEC, controlMembers.get("postinst").mode() & 0777);

        String conffiles = new String(controlMembers.get("conffiles").content(), StandardCharsets.UTF_8);
        assertTrue(conffiles.contains("/etc/default/demo"));

        Map<String, TarMember> dataMembers = readTarGz(arMembers.get("data.tar.gz"));
        assertTrue(dataMembers.containsKey("usr/share/demo/app.txt"));
        assertTrue(dataMembers.containsKey("usr/bin/demo"));
        assertTrue(dataMembers.containsKey("etc/default/demo"));
        assertEquals("hello-deb", new String(dataMembers.get("usr/share/demo/app.txt").content(), StandardCharsets.UTF_8));
        assertEquals(DebEntry.MODE_EXEC, dataMembers.get("usr/bin/demo").mode() & 0777);
        assertEquals(DebEntry.MODE_FILE, dataMembers.get("etc/default/demo").mode() & 0777);
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
                String name = entry.getName();
                if (name.startsWith("./")) {
                    name = name.substring(2);
                }
                if (name.endsWith("/")) {
                    name = name.substring(0, name.length() - 1);
                }
                byte[] content = entry.isDirectory() ? new byte[0] : tar.readAllBytes();
                members.put(name, new TarMember(content, entry.getMode()));
            }
        }
        return members;
    }

    private record TarMember(byte[] content, int mode) {
    }
}
