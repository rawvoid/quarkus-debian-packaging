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

import java.util.Locale;

/**
 * Utility for sanitizing Debian package names according to Debian policy.
 *
 * @author rawvoid
 */
public final class DebianPackageNames {

    private DebianPackageNames() {
    }

    /**
     * Sanitizes a raw name into a valid Debian package name:
     * converts to lowercase, replaces underscores and whitespace with hyphens,
     * strips invalid characters, collapses repeated hyphens, and removes leading/trailing hyphens.
     */
    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank() || "<<unset>>".equals(raw)) {
            throw new IllegalArgumentException(
                    "Debian package name is unset. Configure quarkus.application.name or quarkus.debian.name.");
        }
        String name = raw.trim().toLowerCase(Locale.ROOT);
        name = name.replace('_', '-');
        name = name.replaceAll("\\s+", "-");
        name = name.replaceAll("[^a-z0-9+.-]+", "-");
        name = name.replaceAll("-{2,}", "-");
        name = name.replaceAll("^-+", "");
        name = name.replaceAll("-+$", "");
        return name;
    }
}
