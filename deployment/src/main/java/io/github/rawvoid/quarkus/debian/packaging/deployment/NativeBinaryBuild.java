package io.github.rawvoid.quarkus.debian.packaging.deployment;

import java.util.function.BooleanSupplier;

import io.quarkus.deployment.pkg.NativeConfig;

/**
 * True when the build produces a native binary (not {@code native-sources} only).
 * <p>
 * Local replacement for the deprecated {@code NativeBuild} supplier with the same predicate.
 */
public final class NativeBinaryBuild implements BooleanSupplier {

    private final NativeConfig nativeConfig;

    public NativeBinaryBuild(NativeConfig nativeConfig) {
        this.nativeConfig = nativeConfig;
    }

    @Override
    public boolean getAsBoolean() {
        return nativeConfig.enabled() && !nativeConfig.sourcesOnly();
    }
}
