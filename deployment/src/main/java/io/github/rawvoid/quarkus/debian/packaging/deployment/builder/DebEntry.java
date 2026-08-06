package io.github.rawvoid.quarkus.debian.packaging.deployment.builder;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A single file or directory entry that will appear in the Debian data or control archive.
 */
public final class DebEntry {

    public static final int MODE_FILE = 0644;
    public static final int MODE_EXEC = 0755;
    public static final int MODE_DIR = 0755;

    private final String packagePath;
    private final Path source;
    private final byte[] content;
    private final int mode;
    private final boolean directory;
    private final boolean confFile;

    private DebEntry(String packagePath, Path source, byte[] content, int mode, boolean directory, boolean confFile) {
        this.packagePath = normalize(packagePath);
        this.source = source;
        this.content = content;
        this.mode = mode;
        this.directory = directory;
        this.confFile = confFile;
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

    public String packagePath() {
        return packagePath;
    }

    public Path source() {
        return source;
    }

    public byte[] content() {
        return content;
    }

    public int mode() {
        return mode;
    }

    public boolean directory() {
        return directory;
    }

    public boolean confFile() {
        return confFile;
    }
}
