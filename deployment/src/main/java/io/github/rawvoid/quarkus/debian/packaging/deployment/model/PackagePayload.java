package io.github.rawvoid.quarkus.debian.packaging.deployment.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Describes the application payload to embed under the Debian install directory.
 */
public final class PackagePayload {

    public enum Kind {
        FAST_JAR_TREE,
        UBER_JAR,
        LEGACY_JAR,
        NATIVE
    }

    private final Kind kind;
    private final Path primaryPath;
    private final Path libraryDir;
    private final String mainRelativePath;

    private PackagePayload(Kind kind, Path primaryPath, Path libraryDir, String mainRelativePath) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.primaryPath = Objects.requireNonNull(primaryPath, "primaryPath");
        this.libraryDir = libraryDir;
        this.mainRelativePath = Objects.requireNonNull(mainRelativePath, "mainRelativePath");
    }

    public static PackagePayload fastJarTree(Path quarkusAppDir) {
        return new PackagePayload(Kind.FAST_JAR_TREE, quarkusAppDir, quarkusAppDir.resolve("lib"), "quarkus-run.jar");
    }

    public static PackagePayload uberJar(Path runnerJar) {
        return new PackagePayload(Kind.UBER_JAR, runnerJar, null, runnerJar.getFileName().toString());
    }

    public static PackagePayload legacyJar(Path runnerJar, Path libraryDir) {
        return new PackagePayload(Kind.LEGACY_JAR, runnerJar, libraryDir, runnerJar.getFileName().toString());
    }

    public static PackagePayload nativeImage(Path nativeBinary) {
        return new PackagePayload(Kind.NATIVE, nativeBinary, null, nativeBinary.getFileName().toString());
    }

    public Kind kind() {
        return kind;
    }

    public Path primaryPath() {
        return primaryPath;
    }

    public Path libraryDir() {
        return libraryDir;
    }

    public String mainRelativePath() {
        return mainRelativePath;
    }

    public boolean isNative() {
        return kind == Kind.NATIVE;
    }
}
