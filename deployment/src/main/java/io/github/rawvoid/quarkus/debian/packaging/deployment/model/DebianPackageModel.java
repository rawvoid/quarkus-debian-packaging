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
 */
public final class DebianPackageModel {

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

    private final String packageName;
    private final String version;
    private final String description;
    private final String maintainer;
    private final String section;
    private final String priority;
    private final String architecture;
    private final String depends;
    private final String installDir;
    private final String configDir;
    private final String dataDir;
    private final String logDir;
    private final String configFile;
    private final String jvmOptionsFile;
    private final String defaultsFile;
    private final String binFile;
    private final String mainExecutable;
    private final String serviceUser;
    private final String serviceGroup;
    private final String systemdServiceName;
    private final String systemdUnitFile;
    private final Path outputFile;
    private final PackagePayload payload;

    private DebianPackageModel(
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
            String binFile,
            String mainExecutable,
            String serviceUser,
            String serviceGroup,
            String systemdServiceName,
            String systemdUnitFile,
            Path outputFile,
            PackagePayload payload) {
        this.packageName = packageName;
        this.version = version;
        this.description = description;
        this.maintainer = maintainer;
        this.section = section;
        this.priority = priority;
        this.architecture = architecture;
        this.depends = depends;
        this.installDir = installDir;
        this.configDir = configDir;
        this.dataDir = dataDir;
        this.logDir = logDir;
        this.configFile = configFile;
        this.jvmOptionsFile = jvmOptionsFile;
        this.defaultsFile = defaultsFile;
        this.binFile = binFile;
        this.mainExecutable = mainExecutable;
        this.serviceUser = serviceUser;
        this.serviceGroup = serviceGroup;
        this.systemdServiceName = systemdServiceName;
        this.systemdUnitFile = systemdUnitFile;
        this.outputFile = outputFile;
        this.payload = payload;
    }

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
        String binFile = normalizeAbsolutePath(
                config.binPath().orElse("/usr/bin/" + packageName), "quarkus.debian.bin-path");
        String defaultsFile = normalizeAbsolutePath(
                config.defaultsPath().orElse("/etc/default/" + packageName), "quarkus.debian.defaults-path");
        String systemdServiceName = packageName + ".service";
        String systemdUnitFile = normalizeAbsolutePath(
                config.systemdUnitPath().orElse("/usr/lib/systemd/system/" + systemdServiceName),
                "quarkus.debian.systemd-unit-path");
        String serviceUser = config.serviceUser()
                .map(value -> validateUnixAccount(value, "quarkus.debian.service-user"))
                .orElseGet(() -> deriveUnixAccountName(packageName));
        String serviceGroup = config.serviceGroup()
                .map(value -> validateUnixAccount(value, "quarkus.debian.service-group"))
                .orElse(serviceUser);
        String configFile = configDir + "/application.properties";
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
                binFile,
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
        vars.put("binFile", binFile);
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

    public String packageName() {
        return packageName;
    }

    public String version() {
        return version;
    }

    public String description() {
        return description;
    }

    public String maintainer() {
        return maintainer;
    }

    public String section() {
        return section;
    }

    public String priority() {
        return priority;
    }

    public String architecture() {
        return architecture;
    }

    public String depends() {
        return depends;
    }

    public String installDir() {
        return installDir;
    }

    public String configDir() {
        return configDir;
    }

    public String dataDir() {
        return dataDir;
    }

    public String logDir() {
        return logDir;
    }

    public String configFile() {
        return configFile;
    }

    public String jvmOptionsFile() {
        return jvmOptionsFile;
    }

    public String defaultsFile() {
        return defaultsFile;
    }

    public String binFile() {
        return binFile;
    }

    public String mainExecutable() {
        return mainExecutable;
    }

    public String serviceUser() {
        return serviceUser;
    }

    public String serviceGroup() {
        return serviceGroup;
    }

    public String systemdServiceName() {
        return systemdServiceName;
    }

    public String systemdUnitFile() {
        return systemdUnitFile;
    }

    public Path outputFile() {
        return outputFile;
    }

    public PackagePayload payload() {
        return payload;
    }
}
