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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.rawvoid.quarkus.debian.packaging.deployment.model.PackagePayload;
import io.quarkus.deployment.pkg.PackageConfig;
import io.quarkus.deployment.pkg.builditem.JarBuildItem;
import io.quarkus.deployment.pkg.builditem.NativeImageBuildItem;

/**
 * Tests resolution of application payloads from Quarkus build items.
 *
 * @author rawvoid
 */
class PayloadResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesFastJarTreeFromLibraryDirParent() throws Exception {
        Path quarkusApp = tempDir.resolve("quarkus-app");
        Path lib = quarkusApp.resolve("lib");
        Files.createDirectories(lib);
        Path runner = quarkusApp.resolve("quarkus-run.jar");
        Files.writeString(runner, "runner");

        JarBuildItem jar = new JarBuildItem(runner, null, lib, PackageConfig.JarConfig.JarType.FAST_JAR, null);
        PackagePayload payload = PayloadResolver.fromJar(jar, null);

        assertEquals(PackagePayload.Kind.FAST_JAR_TREE, payload.kind());
        assertEquals(quarkusApp, payload.primaryPath());
        assertEquals("quarkus-run.jar", payload.mainRelativePath());
    }

    @Test
    void resolvesUberJar() throws Exception {
        Path runner = tempDir.resolve("app-runner.jar");
        Files.writeString(runner, "uber");

        JarBuildItem jar = new JarBuildItem(runner, null, null, PackageConfig.JarConfig.JarType.UBER_JAR, null);
        PackagePayload payload = PayloadResolver.fromJar(jar, null);

        assertEquals(PackagePayload.Kind.UBER_JAR, payload.kind());
        assertEquals(runner, payload.primaryPath());
        assertEquals("app-runner.jar", payload.mainRelativePath());
    }

    @Test
    void resolvesLegacyJarWithLib() throws Exception {
        Path runner = tempDir.resolve("app-runner.jar");
        Path lib = tempDir.resolve("lib");
        Files.createDirectories(lib);
        Files.writeString(runner, "legacy");
        Files.writeString(lib.resolve("dep.jar"), "dep");

        JarBuildItem jar = new JarBuildItem(runner, null, lib, PackageConfig.JarConfig.JarType.LEGACY_JAR, null);
        PackagePayload payload = PayloadResolver.fromJar(jar, null);

        assertEquals(PackagePayload.Kind.LEGACY_JAR, payload.kind());
        assertEquals(runner, payload.primaryPath());
        assertEquals(lib, payload.libraryDir());
        assertEquals("app-runner.jar", payload.mainRelativePath());
    }

    @Test
    void resolvesNativeImage() throws Exception {
        Path binary = tempDir.resolve("app-runner");
        Files.writeString(binary, "native");

        NativeImageBuildItem nativeImage = new NativeImageBuildItem(
                binary, NativeImageBuildItem.GraalVMVersion.unknown(), false);
        PackagePayload payload = PayloadResolver.fromNative(nativeImage);

        assertEquals(PackagePayload.Kind.NATIVE, payload.kind());
        assertTrue(payload.isNative());
        assertEquals(binary, payload.primaryPath());
        assertEquals("app-runner", payload.mainRelativePath());
    }
}
