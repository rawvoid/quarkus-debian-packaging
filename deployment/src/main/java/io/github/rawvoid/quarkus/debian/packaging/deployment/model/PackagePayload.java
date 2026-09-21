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
import java.util.Objects;

/**
 * Describes the application payload to embed under the Debian install directory.
 *
 * @author rawvoid
 */
public record PackagePayload(Kind kind, Path primaryPath, Path libraryDir, String mainRelativePath) {

    /**
     * Identifies the packaging layout of the application payload.
     *
     * @author rawvoid
     */
    public enum Kind {
        FAST_JAR_TREE,
        UBER_JAR,
        LEGACY_JAR,
        NATIVE
    }

    public PackagePayload {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(primaryPath, "primaryPath");
        Objects.requireNonNull(mainRelativePath, "mainRelativePath");
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

    public boolean isNative() {
        return kind == Kind.NATIVE;
    }
}
