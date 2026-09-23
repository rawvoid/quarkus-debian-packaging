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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Command-line client invoked by launcher scripts (or systemd ExecReload) to trigger configuration reload.
 *
 * @author rawvoid
 */
public final class ControlSocketClient {

    private ControlSocketClient() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: ControlSocketClient <socket-path>");
            System.exit(1);
        }

        Path socketPath = Path.of(args[0]);
        int exitCode = executeReload(socketPath);
        System.exit(exitCode);
    }

    /**
     * Executes the reload request against the control socket and returns the exit status code.
     *
     * @param socketPath the path to the control UNIX domain socket
     * @return 0 on success, non-zero on error
     */
    public static int executeReload(Path socketPath) {
        try (var socketChannel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            socketChannel.connect(UnixDomainSocketAddress.of(socketPath));

            var writer = new PrintWriter(new OutputStreamWriter(Channels.newOutputStream(socketChannel), StandardCharsets.UTF_8), true);
            var reader = new BufferedReader(new InputStreamReader(Channels.newInputStream(socketChannel), StandardCharsets.UTF_8));

            writer.println(ControlSocketServer.CMD_RELOAD);

            String response = reader.readLine();
            if (response == null) {
                System.err.println("Error: Empty response from control socket");
                return 1;
            }

            if (response.startsWith(ControlSocketServer.PREFIX_OK.trim())) {
                System.out.println(response);
                return 0;
            } else {
                System.err.println(response);
                return 1;
            }
        } catch (Exception e) {
            System.err.println("Error: Failed to connect to control socket (" + socketPath + "): " + e.getMessage());
            return 1;
        }
    }
}
