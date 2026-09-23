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
import io.github.rawvoid.quarkus.debian.packaging.runtime.ConfigReloadRecorder;
import io.github.rawvoid.quarkus.debian.packaging.runtime.config.ReloadableConfigCreator;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;
import io.quarkus.deployment.builditem.ConfigClassBuildItem;
import io.quarkus.deployment.builditem.ConfigMappingBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.runtime.annotations.ConfigRoot;
import org.jboss.jandex.DotName;

/**
 * Deployment processor that registers config mappings and initializes runtime reload server.
 *
 * @author rawvoid
 */
public class ConfigReloadProcessor {

    private static final String DEFAULT_PACKAGE_NAME = "quarkus-app";
    private static final String DEFAULT_SOCKET_DIR = "/run/";
    private static final String CONTROL_SOCKET_FILENAME = "control.sock";
    private static final String QUARKUS_PREFIX = "quarkus";
    private static final String QUARKUS_PREFIX_DOT = "quarkus.";
    private static final String QUARKUS_PACKAGE_PREFIX = "io.quarkus.";

    @BuildStep(onlyIf = DebianEnabled.class)
    public void registerReloadableProxies(
            List<ConfigClassBuildItem> configClasses,
            BuildProducer<GeneratedClassBuildItem> generatedClasses,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {

        for (ConfigClassBuildItem configClass : configClasses) {
            if (!configClass.isMapping()) {
                continue;
            }
            Class<?> mappingClass = configClass.getConfigClass();
            String prefix = configClass.getPrefix();
            if (isExcludedFrameworkClass(mappingClass, prefix)) {
                continue;
            }

            String proxyClassName = ConfigProxyGenerator.generate(mappingClass, prefix, generatedClasses);

            syntheticBeans.produce(SyntheticBeanBuildItem.configure(DotName.createSimple(mappingClass.getName()))
                    .identifier(mappingClass.getName() + "_reloadable_proxy")
                    .scope(BuiltinScope.SINGLETON.getInfo())
                    .alternative(true)
                    .priority(1000)
                    .creator(ReloadableConfigCreator.class)
                    .param("proxyClassName", proxyClassName)
                    .param("mappingClassName", mappingClass.getName())
                    .param("prefix", prefix != null ? prefix : "")
                    .unremovable()
                    .done());

            reflectiveClasses.produce(ReflectiveClassBuildItem.builder(proxyClassName).constructors().build());
        }
    }

    @BuildStep(onlyIf = DebianEnabled.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    public void setupConfigReload(
            ConfigReloadRecorder recorder,
            ShutdownContextBuildItem shutdownContext,
            DebianPackagingConfig config,
            ApplicationInfoBuildItem appInfo,
            List<ConfigMappingBuildItem> configMappings) {

        for (ConfigMappingBuildItem mapping : configMappings) {
            if (isExcludedFrameworkMapping(mapping)) {
                continue;
            }
            recorder.registerMapping(mapping.getConfigClass().getName(), mapping.getPrefix());
        }

        String packageName = config.name().orElse(appInfo.getName());
        if (packageName == null || packageName.isBlank()) {
            packageName = DEFAULT_PACKAGE_NAME;
        }

        String defaultSocketPath = DEFAULT_SOCKET_DIR + packageName + "/" + CONTROL_SOCKET_FILENAME;

        recorder.startControlServer(shutdownContext, defaultSocketPath);
    }

    private static boolean isExcludedFrameworkClass(Class<?> configClass, String prefix) {
        if (configClass.isAnnotationPresent(ConfigRoot.class)) {
            return true;
        }
        if (prefix != null && (prefix.equals(QUARKUS_PREFIX) || prefix.startsWith(QUARKUS_PREFIX_DOT))) {
            return true;
        }
        return configClass.getPackageName().startsWith(QUARKUS_PACKAGE_PREFIX);
    }

    private static boolean isExcludedFrameworkMapping(ConfigMappingBuildItem mapping) {
        return isExcludedFrameworkClass(mapping.getConfigClass(), mapping.getPrefix());
    }
}
