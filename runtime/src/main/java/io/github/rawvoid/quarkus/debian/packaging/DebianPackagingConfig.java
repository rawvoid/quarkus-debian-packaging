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

package io.github.rawvoid.quarkus.debian.packaging;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Build-time configuration for Debian package generation.
 *
 * @author rawvoid
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
    @WithDefault("systemd, python3")
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

    /**
     * Debian configuration bridging and reload settings.
     */
    DebianConfigSection config();

    interface DebianConfigSection {
        /**
         * Whether to automatically bridge external configuration from {@code /etc/${packageName}/application.properties}.
         */
        @WithDefault("true")
        boolean autoBridge();

        /**
         * Optional override path to the external configuration file.
         */
        Optional<String> filePath();

        /**
         * Reload options.
         */
        ReloadConfig reload();

        interface ReloadConfig {
            /**
             * Whether to enable UNIX domain socket reload support.
             */
            @WithDefault("true")
            boolean enabled();

            /**
             * Path to the control UNIX domain socket. Defaults to {@code /run/${packageName}/control.sock}.
             */
            Optional<String> socketPath();
        }
    }
}
