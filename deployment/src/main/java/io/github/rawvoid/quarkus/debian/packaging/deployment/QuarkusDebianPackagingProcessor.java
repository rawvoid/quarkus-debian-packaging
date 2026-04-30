package io.github.rawvoid.quarkus.debian.packaging.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class QuarkusDebianPackagingProcessor {

    private static final String FEATURE = "quarkus-debian-packaging";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}
