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

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates and normalizes filesystem installation paths according to Debian policy and Linux FHS.
 *
 * @author rawvoid
 */
public final class DebianPaths {

    private static final Pattern FORBIDDEN_CHARACTERS = Pattern.compile("[\\s\"$`'\\r\\n;&|<>!#()*?\\[\\]]");

    private static final Set<String> FORBIDDEN_PATHS = Set.of(
            "/etc",
            "/usr",
            "/usr/share",
            "/usr/bin",
            "/usr/lib",
            "/usr/lib/systemd",
            "/usr/lib/systemd/system",
            "/lib",
            "/bin",
            "/sbin",
            "/var",
            "/var/lib",
            "/var/log",
            "/var/run",
            "/run",
            "/tmp",
            "/opt",
            "/home",
            "/root",
            "/etc/default");

    private DebianPaths() {
    }

    /**
     * Normalizes a raw filesystem path and verifies it satisfies absolute path, minimum segment,
     * shell-safety, and reserved system directory constraints.
     */
    public static String normalize(String raw, int minSegments) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Path must not be blank.");
        }
        String path = raw.trim().replace('\\', '/');
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException(
                    "Path '" + raw + "' must be an absolute path starting with '/'.");
        }
        if (FORBIDDEN_CHARACTERS.matcher(path).find()) {
            throw new IllegalArgumentException(
                    "Path '" + raw + "' contains forbidden shell metacharacters or whitespace.");
        }

        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if ("/".equals(path)) {
            throw new IllegalArgumentException(
                    "Path '" + raw + "' cannot be the filesystem root '/'.");
        }

        String withoutLeading = path.substring(1);
        String[] segments = withoutLeading.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty()) {
                throw new IllegalArgumentException(
                    "Path '" + raw + "' contains empty directory segment.");
            }
            if (".".equals(segment)) {
                throw new IllegalArgumentException(
                    "Path '" + raw + "' contains '.' segment.");
            }
            if ("..".equals(segment)) {
                throw new IllegalArgumentException(
                    "Path '" + raw + "' contains forbidden directory traversal '..'.");
            }
        }

        if (segments.length < minSegments) {
            throw new IllegalArgumentException(
                    "Path '" + raw + "' must contain at least " + minSegments + " directory segments.");
        }
        if (FORBIDDEN_PATHS.contains(path)) {
            throw new IllegalArgumentException(
                    "Configured path '" + raw + "' is a reserved system directory.");
        }

        return path;
    }

    /**
     * Resolves and normalizes the configuration file path.
     */
    public static String configFile(String rawName, String configDir, String configFile) {
        if (configFile != null && !configFile.isBlank()) {
            return normalize(configFile, 2);
        }
        if (configDir != null && !configDir.isBlank()) {
            return normalize(configDir, 2) + "/application.properties";
        }
        return "/etc/" + DebianPackageNames.sanitize(rawName) + "/application.properties";
    }

    /**
     * Resolves and normalizes the control socket path.
     */
    public static String socketPath(String rawName, String socketPath) {
        if (socketPath != null && !socketPath.isBlank()) {
            return normalize(socketPath, 2);
        }
        return "/run/" + DebianPackageNames.sanitize(rawName) + "/control.sock";
    }
}
