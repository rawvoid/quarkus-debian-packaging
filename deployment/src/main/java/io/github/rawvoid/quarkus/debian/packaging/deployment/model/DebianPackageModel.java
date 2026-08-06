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

        String installDir = config.installDir().orElse("/usr/share/" + packageName);
        String configDir = config.configDir().orElse("/etc/" + packageName);
        String dataDir = config.dataDir().orElse("/var/lib/" + packageName);
        String logDir = config.logDir().orElse("/var/log/" + packageName);
        String binFile = config.binPath().orElse("/usr/bin/" + packageName);
        String defaultsFile = config.defaultsPath().orElse("/etc/default/" + packageName);
        String systemdServiceName = packageName + ".service";
        String systemdUnitFile = config.systemdUnitPath().orElse("/usr/lib/systemd/system/" + systemdServiceName);
        String serviceUser = config.serviceUser().orElse(packageName);
        String serviceGroup = config.serviceGroup().orElse(packageName);
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

    private static String sanitizePackageName(String raw) {
        if (raw == null || raw.isBlank() || ApplicationInfoBuildItem.UNSET_VALUE.equals(raw)) {
            throw new IllegalArgumentException(
                    "Debian package name is unset. Configure quarkus.application.name or quarkus.debian.name.");
        }
        return raw.trim().toLowerCase(Locale.ROOT);
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
