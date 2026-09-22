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

package io.github.rawvoid.quarkus.debian.packaging.runtime.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.rawvoid.quarkus.debian.packaging.runtime.config.DebianConfigReloadService;
import io.github.rawvoid.quarkus.debian.packaging.runtime.config.DebianExternalConfigSource;

class DebianControlSocketTest {

    @TempDir
    Path tempDir;

    @Test
    void testSocketServerLifecycleAndCommands() throws Exception {
        Path socketPath = tempDir.resolve("control.sock");
        Path configFile = tempDir.resolve("application.properties");
        Files.writeString(configFile, "greeting=hello\n");

        new DebianExternalConfigSource(configFile);
        var reloadService = new DebianConfigReloadService();

        try (var server = new DebianControlSocketServer(socketPath, reloadService)) {
            boolean started = server.start();
            assertTrue(started, "Server should start successfully");
            assertTrue(Files.exists(socketPath), "Socket file should be created");

            // Test STATUS command directly
            try (var channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
                channel.connect(UnixDomainSocketAddress.of(socketPath));
                var writer = new PrintWriter(new OutputStreamWriter(Channels.newOutputStream(channel), StandardCharsets.UTF_8), true);
                var reader = new BufferedReader(new InputStreamReader(Channels.newInputStream(channel), StandardCharsets.UTF_8));

                writer.println("STATUS");
                String response = reader.readLine();
                assertTrue(response.startsWith("OK"), "Expected OK response, got: " + response);
            }

            // Test RELOAD command via DebianReloadClient
            int reloadExitCode = DebianReloadClient.executeReload(socketPath);
            assertEquals(0, reloadExitCode, "Reload should succeed with exit code 0");
        }

        // After close, socket file should be removed
        assertFalse(Files.exists(socketPath), "Socket file should be deleted on close");
    }

    @Test
    void testReloadClientFailureWhenSocketMissing() {
        Path nonExistentSocket = tempDir.resolve("missing.sock");
        int exitCode = DebianReloadClient.executeReload(nonExistentSocket);
        assertEquals(1, exitCode, "Missing socket should return exit code 1");
    }
}
