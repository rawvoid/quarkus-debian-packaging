package io.github.rawvoid.quarkus.debian.packaging.deployment;

import java.util.function.BooleanSupplier;

import io.github.rawvoid.quarkus.debian.packaging.DebianPackagingConfig;

/**
 * Boolean supplier that enables Debian packaging build steps when configured.
 */
public final class DebianEnabled implements BooleanSupplier {

    private final DebianPackagingConfig config;

    public DebianEnabled(DebianPackagingConfig config) {
        this.config = config;
    }

    @Override
    public boolean getAsBoolean() {
        return config.enabled();
    }
}
