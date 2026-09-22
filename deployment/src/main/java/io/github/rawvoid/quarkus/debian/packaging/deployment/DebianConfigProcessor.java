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

package io.github.rawvoid.quarkus.debian.packaging.deployment;

import java.util.List;

import io.github.rawvoid.quarkus.debian.packaging.DebianPackagingConfig;
import io.github.rawvoid.quarkus.debian.packaging.runtime.DebianConfigRecorder;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;
import io.quarkus.deployment.builditem.ConfigMappingBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;

/**
 * Deployment processor that registers config mappings and initializes runtime reload server.
 *
 * @author rawvoid
 */
public class DebianConfigProcessor {

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void setupConfigReload(
            DebianConfigRecorder recorder,
            ShutdownContextBuildItem shutdownContext,
            DebianPackagingConfig config,
            ApplicationInfoBuildItem appInfo,
            List<ConfigMappingBuildItem> configMappings) {

        for (ConfigMappingBuildItem mapping : configMappings) {
            recorder.registerMapping(mapping.getConfigClass().getName(), mapping.getPrefix());
        }

        boolean reloadEnabled = config.config().reload().enabled();
        String packageName = config.name().orElse(appInfo.getName());
        if (packageName == null || packageName.isBlank()) {
            packageName = "quarkus-app";
        }

        String socketPath = config.config().reload().socketPath()
                .orElse("/run/" + packageName + "/control.sock");

        recorder.startControlServer(shutdownContext, reloadEnabled, socketPath);
    }
}
