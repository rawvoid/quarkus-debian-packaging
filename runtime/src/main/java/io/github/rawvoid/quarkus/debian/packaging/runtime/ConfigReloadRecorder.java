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

import org.eclipse.microprofile.config.ConfigProvider;

import io.github.rawvoid.quarkus.debian.packaging.ReloadConfig;
import io.github.rawvoid.quarkus.debian.packaging.runtime.config.ConfigReloadService;
import io.github.rawvoid.quarkus.debian.packaging.runtime.config.ReloadableConfigRegistry;
import io.github.rawvoid.quarkus.debian.packaging.runtime.socket.ControlSocketServer;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.SmallRyeConfig;

/**
 * Quarkus bytecode recorder for registering config mappings and starting the control socket server.
 *
 * @author rawvoid
 */
@Recorder
public class ConfigReloadRecorder {

    private static final ConfigReloadService RELOAD_SERVICE = new ConfigReloadService();

    private final RuntimeValue<ReloadConfig> runtimeConfig;

    public ConfigReloadRecorder(RuntimeValue<ReloadConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

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
            if (ReloadableConfigRegistry.get(clazz, prefix) == null) {
                SmallRyeConfig currentConfig = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
                Object snapshot = (prefix != null && !prefix.isEmpty())
                        ? currentConfig.getConfigMapping(clazz, prefix)
                        : currentConfig.getConfigMapping(clazz);
                ReloadableConfigRegistry.register(clazz, prefix, snapshot);
            }
        } catch (ClassNotFoundException ignored) {
        }
    }

    public void startControlServer(ShutdownContext shutdownContext, String defaultSocketPathStr) {
        ReloadConfig config = runtimeConfig != null ? runtimeConfig.getValue() : null;
        if (config == null || !config.enabled()) {
            return;
        }

        String socketPathStr = config.socketPath().filter(s -> !s.isBlank()).orElse(defaultSocketPathStr);
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
