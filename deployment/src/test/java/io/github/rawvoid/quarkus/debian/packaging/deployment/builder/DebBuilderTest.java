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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

/**
 * Tests deterministic Debian archive construction.
 *
 * @author rawvoid
 */
class DebBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void installedSizeCountsDirectoriesAndCeilFileKiB() throws Exception {
        Path small = tempDir.resolve("small.txt");
        Files.writeString(small, "x"); // 1 byte -> 1 KiB
        Path large = tempDir.resolve("large.bin");
        Files.write(large, new byte[2048]); // 2 KiB exactly -> 2 KiB

        List<DebEntry> entries = List.of(
                DebEntry.file("usr/share/demo/small.txt", small, DebEntry.MODE_FILE, false),
                DebEntry.file("usr/share/demo/large.bin", large, DebEntry.MODE_FILE, false));

        // parent dirs: usr, usr/share, usr/share/demo => 3; files => 1 + 2
        assertEquals(6L, DebBuilder.installedSizeKiB(entries));
    }

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

        Map<String, ArMember> arMembers = readAr(deb);
        assertTrue(arMembers.containsKey("debian-binary"));
        assertEquals("2.0\n", new String(arMembers.get("debian-binary").content(), StandardCharsets.US_ASCII));
        assertEquals(0L, arMembers.get("debian-binary").lastModified(), "Ar entry timestamp must be 0 for reproducibility");
        assertTrue(arMembers.containsKey("control.tar.gz"));
        assertEquals(0L, arMembers.get("control.tar.gz").lastModified(), "Ar entry timestamp must be 0 for reproducibility");
        assertTrue(arMembers.containsKey("data.tar.gz"));
        assertEquals(0L, arMembers.get("data.tar.gz").lastModified(), "Ar entry timestamp must be 0 for reproducibility");

        Map<String, TarMember> controlMembers = readTarGz(arMembers.get("control.tar.gz").content());
        assertTrue(controlMembers.containsKey("control"));
        assertTrue(controlMembers.containsKey("md5sums"));
        assertTrue(controlMembers.containsKey("conffiles"));
        assertTrue(controlMembers.containsKey("postinst"));
        assertEquals(DebEntry.MODE_EXEC, controlMembers.get("postinst").mode() & 0777);

        String conffiles = new String(controlMembers.get("conffiles").content(), StandardCharsets.UTF_8);
        assertTrue(conffiles.contains("/etc/default/demo"));

        Map<String, TarMember> dataMembers = readTarGz(arMembers.get("data.tar.gz").content());
        assertTrue(dataMembers.containsKey("usr/share/demo/app.txt"));
        assertTrue(dataMembers.containsKey("usr/bin/demo"));
        assertTrue(dataMembers.containsKey("etc/default/demo"));
        assertEquals("hello-deb", new String(dataMembers.get("usr/share/demo/app.txt").content(), StandardCharsets.UTF_8));
        assertEquals(DebEntry.MODE_EXEC, dataMembers.get("usr/bin/demo").mode() & 0777);
        assertEquals(DebEntry.MODE_FILE, dataMembers.get("etc/default/demo").mode() & 0777);
    }

    @Test
    void testReproducibleBuildDeterminism() throws Exception {
        List<DebEntry> data = List.of(
                DebEntry.bytes("usr/share/demo/app.txt", "hello-deb".getBytes(StandardCharsets.UTF_8), DebEntry.MODE_FILE, false),
                DebEntry.bytes("usr/bin/demo", "#!/bin/sh\necho hi\n".getBytes(StandardCharsets.UTF_8), DebEntry.MODE_EXEC, false));
        String control = """
                Package: demo
                Version: 1.0.0
                Architecture: all
                Maintainer: Test <test@example.com>
                Description: demo service
                """;
        List<DebEntry> controlEntries = List.of();

        Path deb1 = tempDir.resolve("demo_run1.deb");
        Path deb2 = tempDir.resolve("demo_run2.deb");

        DebBuilder.build(deb1, control, controlEntries, data);
        DebBuilder.build(deb2, control, controlEntries, data);

        byte[] bytes1 = Files.readAllBytes(deb1);
        byte[] bytes2 = Files.readAllBytes(deb2);
        assertArrayEquals(bytes1, bytes2, "Repeated builds must produce bitwise identical deb packages");
    }

    private static Map<String, ArMember> readAr(Path deb) throws Exception {
        Map<String, ArMember> members = new HashMap<>();
        try (InputStream in = Files.newInputStream(deb);
                ArArchiveInputStream ar = new ArArchiveInputStream(in)) {
            ArArchiveEntry entry;
            while ((entry = ar.getNextEntry()) != null) {
                members.put(entry.getName(), new ArMember(ar.readAllBytes(), entry.getLastModified()));
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

    private record ArMember(byte[] content, long lastModified) {
    }

    /**
     * A member read from a tar archive.
     *
     * @author rawvoid
     */
    private record TarMember(byte[] content, int mode) {
    }
}
