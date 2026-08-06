package io.github.rawvoid.quarkus.debian.packaging.deployment.builder;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A single file or directory entry that will appear in the Debian data or control archive.
 */
public record DebEntry(
        String packagePath,
        Path source,
        byte[] content,
        int mode,
        boolean directory,
        boolean confFile) {

    /** Unix permission bits corresponding to {@code 0644}. */
    public static final int MODE_FILE = Integer.parseInt("644", 8);
    /** Unix permission bits corresponding to {@code 0755}. */
    public static final int MODE_EXEC = Integer.parseInt("755", 8);
    /** Unix permission bits corresponding to {@code 0755}. */
    public static final int MODE_DIR = Integer.parseInt("755", 8);

    public DebEntry {
        packagePath = normalize(packagePath);
    }

    public static DebEntry directory(String packagePath) {
        return new DebEntry(packagePath, null, null, MODE_DIR, true, false);
    }

    public static DebEntry file(String packagePath, Path source, int mode, boolean confFile) {
        Objects.requireNonNull(source, "source");
        return new DebEntry(packagePath, source, null, mode, false, confFile);
    }

    public static DebEntry bytes(String packagePath, byte[] content, int mode, boolean confFile) {
        Objects.requireNonNull(content, "content");
        return new DebEntry(packagePath, null, content, mode, false, confFile);
    }

    private static String normalize(String packagePath) {
        Objects.requireNonNull(packagePath, "packagePath");
        String path = packagePath.replace('\\', '/');
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Package path must not be empty");
        }
        return path;
    }
}
