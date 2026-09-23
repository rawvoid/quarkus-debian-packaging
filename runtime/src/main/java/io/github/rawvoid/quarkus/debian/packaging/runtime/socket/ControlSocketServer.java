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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.logging.Logger;

import io.github.rawvoid.quarkus.debian.packaging.runtime.config.ConfigReloadService;

/**
 * UNIX domain socket server listening for reload control commands from systemd or the launcher CLI.
 *
 * @author rawvoid
 */
public class ControlSocketServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(ControlSocketServer.class);

    private final Path socketPath;
    private final ConfigReloadService reloadService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocketChannel serverChannel;
    private Thread listenerThread;

    public ControlSocketServer(Path socketPath, ConfigReloadService reloadService) {
        this.socketPath = Objects.requireNonNull(socketPath, "socketPath");
        this.reloadService = Objects.requireNonNull(reloadService, "reloadService");
    }

    public synchronized boolean start() {
        if (running.get()) {
            return true;
        }

        try {
            if (socketPath.getParent() != null) {
                Files.createDirectories(socketPath.getParent());
            }
            Files.deleteIfExists(socketPath);

            serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            serverChannel.bind(UnixDomainSocketAddress.of(socketPath));
            running.set(true);

            listenerThread = new Thread(this::listenLoop, "debian-control-socket");
            listenerThread.setDaemon(true);
            listenerThread.start();
            LOG.info("Control socket listening on " + socketPath);
            return true;
        } catch (Exception e) {
            LOG.warn("Could not bind control socket on " + socketPath + " (" + e.getMessage()
                    + "). Reload via 'systemctl reload' will be unavailable.");
            return false;
        }
    }

    private void listenLoop() {
        while (running.get() && serverChannel != null && serverChannel.isOpen()) {
            try {
                SocketChannel clientChannel = serverChannel.accept();
                handleClient(clientChannel);
            } catch (IOException e) {
                if (!running.get()) {
                    break;
                }
                LOG.debug("Error accepting control socket connection: " + e.getMessage());
            }
        }
    }

    private void handleClient(SocketChannel clientChannel) {
        try (clientChannel) {
            var reader = new BufferedReader(new InputStreamReader(Channels.newInputStream(clientChannel), StandardCharsets.UTF_8));
            var writer = new PrintWriter(new OutputStreamWriter(Channels.newOutputStream(clientChannel), StandardCharsets.UTF_8), true);

            String command = reader.readLine();
            if (command == null) {
                return;
            }

            command = command.trim();
            if ("RELOAD".equalsIgnoreCase(command)) {
                var result = reloadService.reload();
                if (result.success()) {
                    writer.println("OK " + result.message());
                } else {
                    writer.println("ERROR " + result.message());
                }
            } else if ("STATUS".equalsIgnoreCase(command)) {
                writer.println("OK Control Socket Server is running on " + socketPath);
            } else {
                writer.println("ERROR Unknown command: " + command);
            }
        } catch (Exception e) {
            LOG.debug("Error handling control socket client: " + e.getMessage());
        }
    }

    public Path getSocketPath() {
        return socketPath;
    }

    public boolean isRunning() {
        return running.get();
    }

    @Override
    public synchronized void close() {
        if (running.compareAndSet(true, false)) {
            try {
                if (serverChannel != null && serverChannel.isOpen()) {
                    serverChannel.close();
                }
            } catch (IOException ignored) {
            }
            try {
                Files.deleteIfExists(socketPath);
            } catch (IOException ignored) {
            }
        }
    }
}
