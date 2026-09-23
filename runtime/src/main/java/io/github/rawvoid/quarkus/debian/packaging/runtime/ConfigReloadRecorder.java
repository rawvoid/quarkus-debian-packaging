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

import io.github.rawvoid.quarkus.debian.packaging.runtime.config.ConfigReloadService;
import io.github.rawvoid.quarkus.debian.packaging.runtime.config.ReloadableConfigCreator;
import io.github.rawvoid.quarkus.debian.packaging.runtime.config.ReloadableConfigRegistry;
import io.github.rawvoid.quarkus.debian.packaging.runtime.socket.ControlSocketServer;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;

/**
 * Quarkus bytecode recorder for registering config mappings and starting the control socket server.
 *
 * @author rawvoid
 */
@Recorder
public class ConfigReloadRecorder {

    private static final ConfigReloadService RELOAD_SERVICE = new ConfigReloadService();

    public static ConfigReloadService getReloadService() {
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
            ReloadableConfigRegistry.registerIfAbsent(clazz, prefix, () -> ReloadableConfigCreator.fetchCurrentSnapshot(clazz, prefix));
        } catch (ClassNotFoundException ignored) {
        }
    }

    public void startControlServer(ShutdownContext shutdownContext, String socketPathStr) {
        if (socketPathStr == null || socketPathStr.isBlank()) {
            return;
        }

        Path socketPath = Path.of(socketPathStr);
        ControlSocketServer server = new ControlSocketServer(socketPath, RELOAD_SERVICE);
        if (server.start()) {
            shutdownContext.addShutdownTask(server::close);
        }
    }
}
