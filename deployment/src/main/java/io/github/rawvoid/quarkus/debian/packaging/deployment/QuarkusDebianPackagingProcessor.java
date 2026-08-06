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
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.pkg.PackageConfig;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.JarBuildItem;
import io.quarkus.deployment.pkg.builditem.NativeImageBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;

class QuarkusDebianPackagingProcessor {

    private static final String FEATURE = "debian-packaging";
    private static final Logger LOG = Logger.getLogger(QuarkusDebianPackagingProcessor.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Produce {@link ArtifactResultBuildItem} only as a pseudo-target so this step always runs in production
     * packaging without registering {@code deb} as the primary runnable artifact (which would break
     * {@code @QuarkusIntegrationTest}).
     */
    @BuildStep(onlyIf = { IsProduction.class, DebianEnabled.class }, onlyIfNot = NativeBinaryBuild.class)
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

    @BuildStep(onlyIf = { IsProduction.class, NativeBinaryBuild.class, DebianEnabled.class })
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
