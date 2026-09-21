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

import java.nio.file.Path;

import org.jboss.logging.Logger;

import io.github.rawvoid.quarkus.debian.packaging.DebianPackagingConfig;
import io.github.rawvoid.quarkus.debian.packaging.deployment.builder.DebPackager;
import io.github.rawvoid.quarkus.debian.packaging.deployment.model.DebianPackageModel;
import io.github.rawvoid.quarkus.debian.packaging.deployment.model.PackagePayload;
import io.quarkus.deployment.IsProduction;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;
import io.quarkus.deployment.pkg.PackageConfig;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.JarBuildItem;
import io.quarkus.deployment.pkg.builditem.NativeImageBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.deployment.pkg.steps.NativeBuild;

/**
 * Registers the build steps that produce Debian packages.
 *
 * @author rawvoid
 */
class QuarkusDebianPackagingProcessor {

    private static final Logger LOG = Logger.getLogger(QuarkusDebianPackagingProcessor.class);

    /**
     * Produce {@link ArtifactResultBuildItem} only as a pseudo-target so this step always runs in production
     * packaging without registering {@code deb} as the primary runnable artifact (which would break
     * {@code @QuarkusIntegrationTest}).
     */
    @BuildStep(onlyIf = { IsProduction.class, DebianEnabled.class }, onlyIfNot = NativeBuild.class)
    @Produce(ArtifactResultBuildItem.class)
    void packageJvmDeb(
            DebianPackagingConfig config,
            ApplicationInfoBuildItem applicationInfo,
            OutputTargetBuildItem outputTarget,
            PackageConfig packageConfig,
            JarBuildItem jar) {
        PackagePayload payload = PayloadResolver.fromJar(jar, packageConfig);
        buildDeb(config, applicationInfo, outputTarget, payload);
    }

    @BuildStep(onlyIf = { IsProduction.class, NativeBuild.class, DebianEnabled.class })
    @Produce(ArtifactResultBuildItem.class)
    void packageNativeDeb(
            DebianPackagingConfig config,
            ApplicationInfoBuildItem applicationInfo,
            OutputTargetBuildItem outputTarget,
            NativeImageBuildItem nativeImage) {
        PackagePayload payload = PayloadResolver.fromNative(nativeImage);
        buildDeb(config, applicationInfo, outputTarget, payload);
    }

    private static void buildDeb(
            DebianPackagingConfig config,
            ApplicationInfoBuildItem applicationInfo,
            OutputTargetBuildItem outputTarget,
            PackagePayload payload) {
        DebianPackageModel model = DebianPackageModel.resolve(config, applicationInfo, outputTarget, payload);
        Path deb = DebPackager.packageDeb(model);
        LOG.debugf(
                "Debian package model: name=%s version=%s arch=%s payload=%s path=%s",
                model.packageName(),
                model.version(),
                model.architecture(),
                payload.kind(),
                deb.toAbsolutePath());
    }
}
