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

package io.github.rawvoid.quarkus.debian.packaging.deployment.model;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import io.github.rawvoid.quarkus.debian.packaging.DebianPackagingConfig;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;

/**
 * Fully resolved Debian package metadata used for template rendering and packaging.
 *
 * @author rawvoid
 */
public record DebianPackageModel(
        String packageName,
        String version,
        String description,
        String maintainer,
        String section,
        String priority,
        String architecture,
        String depends,
        String installDir,
        String configDir,
        String dataDir,
        String logDir,
        String configFile,
        String jvmOptionsFile,
        String defaultsFile,
        String executableFile,
        String mainExecutable,
        String serviceUser,
        String serviceGroup,
        String systemdServiceName,
        String systemdUnitFile,
        Path outputFile,
        PackagePayload payload) {

    /**
     * Debian Policy: package names must be at least two characters and match this pattern.
     */
    private static final Pattern PACKAGE_NAME = Pattern.compile("^[a-z0-9][a-z0-9+.-]+$");

    /**
     * Conservative account name accepted by useradd/systemd ({@code User=}/{@code Group=}).
     * Max length 32 matches historical glibc/util-linux limits.
     */
    private static final Pattern UNIX_ACCOUNT = Pattern.compile("^[a-z_][a-z0-9_-]{0,31}$");
    private static final int MAX_UNIX_ACCOUNT_LENGTH = 32;

    public static DebianPackageModel resolve(
            DebianPackagingConfig config,
            ApplicationInfoBuildItem appInfo,
            OutputTargetBuildItem outputTarget,
            PackagePayload payload) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(appInfo, "appInfo");
        Objects.requireNonNull(outputTarget, "outputTarget");
        Objects.requireNonNull(payload, "payload");

        String packageName = sanitizePackageName(config.name().orElse(appInfo.getName()));
        if (!PACKAGE_NAME.matcher(packageName).matches()) {
            throw new IllegalArgumentException(
                    "Invalid Debian package name '" + packageName
                            + "'. Names must be at least two characters and match [a-z0-9][a-z0-9+.-]+ "
                            + "(configure quarkus.debian.name).");
        }

        String version = config.version().orElse(appInfo.getVersion());
        if (version == null || version.isBlank() || ApplicationInfoBuildItem.UNSET_VALUE.equals(version)) {
            throw new IllegalArgumentException(
                    "Debian package version is unset. Configure quarkus.application.version or quarkus.debian.version.");
        }

        String description = config.description().orElse(packageName + " service");
        String architecture = config.architecture().orElseGet(() -> payload.isNative() ? detectNativeArchitecture() : "all");

        String installDir = normalizeAbsolutePath(
                config.installDir().orElse("/usr/share/" + packageName), "quarkus.debian.install-dir");
        String configDir = normalizeAbsolutePath(
                config.configDir().orElse("/etc/" + packageName), "quarkus.debian.config-dir");
        String dataDir = normalizeAbsolutePath(
                config.dataDir().orElse("/var/lib/" + packageName), "quarkus.debian.data-dir");
        String logDir = normalizeAbsolutePath(
                config.logDir().orElse("/var/log/" + packageName), "quarkus.debian.log-dir");
        String executableFile = normalizeAbsolutePath(
                config.executableFile().orElse("/usr/bin/" + packageName), "quarkus.debian.executable-file");
        String defaultsFile = normalizeAbsolutePath(
                config.defaultsFile().orElse("/etc/default/" + packageName), "quarkus.debian.defaults-file");
        String systemdServiceName = packageName + ".service";
        String systemdUnitFile = normalizeAbsolutePath(
                config.systemdUnitFile().orElse("/usr/lib/systemd/system/" + systemdServiceName),
                "quarkus.debian.systemd-unit-file");
        String serviceUser = config.serviceUser()
                .map(value -> validateUnixAccount(value, "quarkus.debian.service-user"))
                .orElseGet(() -> deriveUnixAccountName(packageName));
        String serviceGroup = config.serviceGroup()
                .map(value -> validateUnixAccount(value, "quarkus.debian.service-group"))
                .orElse(serviceUser);
        String configFile = normalizeAbsolutePath(
                config.configFile().orElse(configDir + "/application.properties"), "quarkus.debian.config-file");
        String jvmOptionsFile = configDir + "/jvm.options";
        String mainExecutable = installDir + "/" + payload.mainRelativePath();

        String outputName = config.outputName().orElse(packageName + "_" + version + "_" + architecture + ".deb");
        Path outputFile = outputTarget.getOutputDirectory().resolve(outputName);

        return new DebianPackageModel(
                packageName,
                version,
                description,
                config.maintainer(),
                config.section(),
                config.priority(),
                architecture,
                config.depends(),
                installDir,
                configDir,
                dataDir,
                logDir,
                configFile,
                jvmOptionsFile,
                defaultsFile,
                executableFile,
                mainExecutable,
                serviceUser,
                serviceGroup,
                systemdServiceName,
                systemdUnitFile,
                outputFile,
                payload);
    }

    public Map<String, String> templateVariables() {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("packageName", packageName);
        vars.put("version", version);
        vars.put("description", description);
        vars.put("maintainer", maintainer);
        vars.put("section", section);
        vars.put("priority", priority);
        vars.put("architecture", architecture);
        vars.put("depends", depends);
        vars.put("installDir", installDir);
        vars.put("configDir", configDir);
        vars.put("dataDir", dataDir);
        vars.put("logDir", logDir);
        vars.put("configFile", configFile);
        vars.put("jvmOptionsFile", jvmOptionsFile);
        vars.put("defaultsFile", defaultsFile);
        vars.put("executableFile", executableFile);
        vars.put("binFile", executableFile);
        vars.put("mainExecutable", mainExecutable);
        vars.put("serviceUser", serviceUser);
        vars.put("serviceGroup", serviceGroup);
        vars.put("systemdServiceName", systemdServiceName);
        vars.put("systemdUnitFile", systemdUnitFile);
        return vars;
    }

    /**
     * Normalizes a raw application/artifact name into a Debian package name candidate:
     * lowercases, maps {@code _} and whitespace to {@code -}, collapses repeated hyphens,
     * and strips leading/trailing hyphens. The result is still validated against Policy.
     */
    static String sanitizePackageName(String raw) {
        if (raw == null || raw.isBlank() || ApplicationInfoBuildItem.UNSET_VALUE.equals(raw)) {
            throw new IllegalArgumentException(
                    "Debian package name is unset. Configure quarkus.application.name or quarkus.debian.name.");
        }
        String name = raw.trim().toLowerCase(Locale.ROOT);
        name = name.replace('_', '-');
        name = name.replaceAll("\\s+", "-");
        // Drop characters outside the Debian package name alphabet (keep [a-z0-9+.-]).
        name = name.replaceAll("[^a-z0-9+.-]+", "-");
        name = name.replaceAll("-{2,}", "-");
        name = name.replaceAll("^-+", "");
        name = name.replaceAll("-+$", "");
        return name;
    }

    /**
     * Derives a useradd/systemd-safe account name from a Debian package name.
     * Replaces {@code .} / {@code +} with {@code -}, prefixes a leading digit with {@code _},
     * collapses separators, and truncates to 32 characters.
     */
    static String deriveUnixAccountName(String packageName) {
        String normalized = packageName.toLowerCase(Locale.ROOT)
                .replace('+', '-')
                .replace('.', '-');
        normalized = normalized.replaceAll("[^a-z0-9_-]", "-");
        normalized = normalized.replaceAll("-{2,}", "-");
        normalized = normalized.replaceAll("^-+", "");
        normalized = normalized.replaceAll("-+$", "");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot derive a Unix service account from package name '" + packageName
                            + "'. Configure quarkus.debian.service-user and quarkus.debian.service-group.");
        }
        if (Character.isDigit(normalized.charAt(0))) {
            normalized = "_" + normalized;
        }
        if (normalized.length() > MAX_UNIX_ACCOUNT_LENGTH) {
            normalized = normalized.substring(0, MAX_UNIX_ACCOUNT_LENGTH);
            normalized = normalized.replaceAll("-+$", "");
        }
        return validateUnixAccount(normalized, "derived service account from package name '" + packageName + "'");
    }

    /**
     * Ensures configured install paths are absolute and free of trailing slashes.
     */
    static String normalizeAbsolutePath(String raw, String source) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Path is unset (" + source + ").");
        }
        String path = raw.trim().replace('\\', '/');
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException(
                    "Path '" + raw + "' (" + source + ") must be an absolute path starting with '/'.");
        }
        if (path.contains("//")) {
            path = path.replaceAll("/{2,}", "/");
        }
        return path;
    }

    static String validateUnixAccount(String raw, String source) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Unix account name is unset (" + source + ").");
        }
        String account = raw.trim().toLowerCase(Locale.ROOT);
        if (!UNIX_ACCOUNT.matcher(account).matches()) {
            throw new IllegalArgumentException(
                    "Invalid Unix account name '" + account + "' (" + source + "). "
                            + "Names must match [a-z_][a-z0-9_-]{0,31} (configure quarkus.debian.service-user / "
                            + "quarkus.debian.service-group).");
        }
        return account;
    }

    private static String detectNativeArchitecture() {
        String arch = System.getProperty("os.arch", "unknown").toLowerCase(Locale.ROOT);
        return switch (arch) {
            case "x86_64", "amd64" -> "amd64";
            case "aarch64", "arm64" -> "arm64";
            case "x86", "i386", "i686" -> "i386";
            case "ppc64le" -> "ppc64el";
            case "s390x" -> "s390x";
            case "riscv64" -> "riscv64";
            default -> arch;
        };
    }
}
