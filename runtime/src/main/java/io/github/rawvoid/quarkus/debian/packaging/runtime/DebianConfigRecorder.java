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

import java.nio.file.Path;

import io.github.rawvoid.quarkus.debian.packaging.runtime.config.DebianConfigReloadService;
import io.github.rawvoid.quarkus.debian.packaging.runtime.socket.DebianControlSocketServer;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;

/**
 * Quarkus bytecode recorder for registering config mappings and starting the Debian control socket server.
 *
 * @author rawvoid
 */
@Recorder
public class DebianConfigRecorder {

    private static final DebianConfigReloadService RELOAD_SERVICE = new DebianConfigReloadService();

    public static DebianConfigReloadService getReloadService() {
        return RELOAD_SERVICE;
    }

    public void registerMapping(String className, String prefix) {
        if (className == null || className.isBlank()) {
            return;
        }
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> clazz = Class.forName(className, false, cl);
            RELOAD_SERVICE.registerMapping(clazz, prefix);
        } catch (ClassNotFoundException ignored) {
        }
    }

    public void startControlServer(ShutdownContext shutdownContext, boolean enabled, String socketPathStr) {
        if (!enabled || socketPathStr == null || socketPathStr.isBlank()) {
            return;
        }

        Path socketPath = Path.of(socketPathStr);
        DebianControlSocketServer server = new DebianControlSocketServer(socketPath, RELOAD_SERVICE);
        if (server.start()) {
            shutdownContext.addShutdownTask(server::close);
        }
    }
}
