package io.github.rawvoid.quarkus.debian.packaging;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Build-time configuration for Debian package generation.
 */
@ConfigMapping(prefix = "quarkus.debian")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface DebianPackagingConfig {

    /**
     * Whether to build a Debian package during the package phase.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Debian package name. Defaults to {@code quarkus.application.name}.
     */
    Optional<String> name();

    /**
     * Debian package version. Defaults to {@code quarkus.application.version}.
     */
    Optional<String> version();

    /**
     * Package description used in control and the systemd unit.
     */
    Optional<String> description();

    /**
     * Maintainer field for the control file.
     */
    @WithDefault("Unknown <unknown@unknown>")
    String maintainer();

    /**
     * Debian section.
     */
    @WithDefault("web")
    String section();

    /**
     * Debian priority.
     */
    @WithDefault("optional")
    String priority();

    /**
     * Debian architecture. When unset: {@code all} for JVM packages, host architecture for native.
     */
    Optional<String> architecture();

    /**
     * Control Depends field.
     */
    @WithDefault("systemd")
    String depends();

    /**
     * Installation directory for the application payload (e.g. {@code /usr/share/my-app}).
     */
    Optional<String> installDir();

    /**
     * External configuration directory (e.g. {@code /etc/my-app}).
     */
    Optional<String> configDir();

    /**
     * Application data directory created at install time (e.g. {@code /var/lib/my-app}).
     */
    Optional<String> dataDir();

    /**
     * Log directory created at install time (e.g. {@code /var/log/my-app}).
     */
    Optional<String> logDir();

    /**
     * Absolute path of the launcher script (e.g. {@code /usr/bin/my-app}).
     */
    Optional<String> binPath();

    /**
     * Absolute path of the defaults EnvironmentFile (e.g. {@code /etc/default/my-app}).
     */
    Optional<String> defaultsPath();

    /**
     * Absolute path of the systemd unit file.
     */
    Optional<String> systemdUnitPath();

    /**
     * System service user.
     */
    Optional<String> serviceUser();

    /**
     * System service group.
     */
    Optional<String> serviceGroup();

    /**
     * Output file name under the build output directory.
     * Default: {@code {name}_{version}_{architecture}.deb}.
     */
    Optional<String> outputName();
}
