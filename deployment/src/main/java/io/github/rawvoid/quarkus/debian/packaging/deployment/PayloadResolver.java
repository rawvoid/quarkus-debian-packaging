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

package io.github.rawvoid.quarkus.debian.packaging.deployment;

import java.nio.file.Files;
import java.nio.file.Path;

import io.github.rawvoid.quarkus.debian.packaging.deployment.model.PackagePayload;
import io.quarkus.deployment.pkg.PackageConfig;
import io.quarkus.deployment.pkg.builditem.JarBuildItem;
import io.quarkus.deployment.pkg.builditem.NativeImageBuildItem;
import io.quarkus.deployment.pkg.jar.FastJarFormat;

/**
 * Resolves the Debian package payload from Quarkus packaging build items.
 *
 * @author rawvoid
 */
public final class PayloadResolver {

    private PayloadResolver() {
    }

    public static PackagePayload fromNative(NativeImageBuildItem nativeImage) {
        Path path = nativeImage.getPath();
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Native image binary not found: " + path);
        }
        return PackagePayload.nativeImage(path);
    }

    public static PackagePayload fromJar(JarBuildItem jar, PackageConfig packageConfig) {
        PackageConfig.JarConfig.JarType type = jar.getType() != null ? jar.getType() : packageConfig.jar().type();
        Path jarPath = jar.getPath();
        Path libraryDir = jar.getLibraryDir();

        return switch (type) {
            case FAST_JAR, MUTABLE_JAR, AOT_JAR -> {
                Path quarkusApp = resolveFastJarRoot(jarPath, libraryDir);
                if (!Files.isDirectory(quarkusApp)) {
                    throw new IllegalStateException("fast-jar directory not found: " + quarkusApp);
                }
                yield PackagePayload.fastJarTree(quarkusApp);
            }
            case UBER_JAR -> {
                if (!Files.isRegularFile(jarPath)) {
                    throw new IllegalStateException("uber-jar not found: " + jarPath);
                }
                yield PackagePayload.uberJar(jarPath);
            }
            case LEGACY_JAR -> {
                if (!Files.isRegularFile(jarPath)) {
                    throw new IllegalStateException("legacy-jar not found: " + jarPath);
                }
                yield PackagePayload.legacyJar(jarPath, libraryDir);
            }
        };
    }

    private static Path resolveFastJarRoot(Path jarPath, Path libraryDir) {
        if (libraryDir != null) {
            Path parent = libraryDir.getParent();
            if (parent != null) {
                return parent;
            }
        }
        // jarPath is typically quarkus-app/quarkus-run.jar
        Path parent = jarPath.getParent();
        if (parent != null && FastJarFormat.QUARKUS_RUN_JAR.equals(jarPath.getFileName().toString())) {
            return parent;
        }
        if (parent != null) {
            return parent;
        }
        return jarPath;
    }
}
