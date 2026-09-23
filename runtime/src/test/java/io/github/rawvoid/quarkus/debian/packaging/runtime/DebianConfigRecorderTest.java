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

package io.github.rawvoid.quarkus.debian.packaging.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.runtime.ShutdownContext;

class DebianConfigRecorderTest {

    private final List<Runnable> shutdownTasks = new ArrayList<>();
    private final ShutdownContext shutdownContext = new ShutdownContext() {
        @Override
        public void addShutdownTask(Runnable runnable) {
            shutdownTasks.add(runnable);
        }

        @Override
        public void addLastShutdownTask(Runnable runnable) {
            shutdownTasks.add(runnable);
        }
    };

    @AfterEach
    void tearDown() {
        for (Runnable task : shutdownTasks) {
            try {
                task.run();
            } catch (Exception ignored) {
            }
        }
        shutdownTasks.clear();
        System.clearProperty("quarkus.debian.enabled");
        System.clearProperty("quarkus.debian.reload.enabled");
        System.clearProperty("quarkus.debian.reload.socket-path");
        System.clearProperty("quarkus.debian.config.reload.enabled");
        System.clearProperty("quarkus.debian.config.reload.socket-path");
    }

    @Test
    void testDisabledViaDebianEnabled(@TempDir Path tempDir) {
        Path socketPath = tempDir.resolve("control.sock");
        System.setProperty("quarkus.debian.enabled", "false");

        DebianConfigRecorder recorder = new DebianConfigRecorder();
        recorder.startControlServer(shutdownContext, true, socketPath.toString());

        assertFalse(Files.exists(socketPath));
        assertTrue(shutdownTasks.isEmpty());
    }

    @Test
    void testDisabledViaReloadEnabled(@TempDir Path tempDir) {
        Path socketPath = tempDir.resolve("control.sock");
        System.setProperty("quarkus.debian.reload.enabled", "false");

        DebianConfigRecorder recorder = new DebianConfigRecorder();
        recorder.startControlServer(shutdownContext, true, socketPath.toString());

        assertFalse(Files.exists(socketPath));
        assertTrue(shutdownTasks.isEmpty());
    }

    @Test
    void testSocketPathOverride(@TempDir Path tempDir) {
        Path defaultSocket = tempDir.resolve("default.sock");
        Path overrideSocket = tempDir.resolve("override.sock");
        System.setProperty("quarkus.debian.reload.socket-path", overrideSocket.toString());

        DebianConfigRecorder recorder = new DebianConfigRecorder();
        recorder.startControlServer(shutdownContext, true, defaultSocket.toString());

        assertFalse(Files.exists(defaultSocket));
        assertTrue(Files.exists(overrideSocket));
        assertFalse(shutdownTasks.isEmpty());
    }
}
