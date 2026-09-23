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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DebianPathsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "/",
            "//",
            "/etc",
            "/etc/",
            "/etc/.",
            "/./etc",
            "//etc",
            "/usr/share",
            "/var/run",
            "/usr/lib/systemd/system",
            "/etc/default",
            "/tmp",
            "/usr/share/../etc",
            "/run//socket"
    })
    void rejectsInvalidAndReservedPaths(String path) {
        assertThrows(IllegalArgumentException.class, () -> DebianPaths.normalize(path, 2));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/etc/my$(whoami)",
            "/etc/my`whoami`",
            "/etc/my\"app",
            "/etc/my'app",
            "/etc/my app",
            "/etc/my;app",
            "/etc/my&app",
            "/etc/my|app",
            "/etc/my<app",
            "/etc/my>app",
            "/etc/my!app",
            "/etc/my#app",
            "/etc/my*app",
            "/etc/my?app",
            "/etc/my[app",
            "/etc/my]app"
    })
    void rejectsShellMetacharactersAndWhitespace(String path) {
        assertThrows(IllegalArgumentException.class, () -> DebianPaths.normalize(path, 2));
    }

    @Test
    void acceptsValidCanonicalPaths() {
        assertEquals("/etc/my-app", DebianPaths.normalize("/etc/my-app", 2));
        assertEquals("/usr/share/my-app", DebianPaths.normalize("/usr/share/my-app", 2));
        assertEquals("/var/lib/my-app", DebianPaths.normalize("/var/lib/my-app", 2));
        assertEquals("/opt/my-app", DebianPaths.normalize("/opt/my-app", 2));
        assertEquals("/run/my-app/control.sock", DebianPaths.normalize("/run/my-app/control.sock", 2));
    }

    @Test
    void normalizesBackslashesAndTrailingSlashes() {
        assertEquals("/run/my-app/control.sock", DebianPaths.normalize("\\run\\my-app\\control.sock", 2));
        assertEquals("/run/my-app/control.sock", DebianPaths.normalize("/run/my-app/control.sock/", 2));
        assertEquals("/etc/my-app", DebianPaths.normalize("/etc/my-app/", 2));
    }

    @Test
    void resolvesConfigFile() {
        assertEquals("/etc/my-app/application.properties", DebianPaths.configFile("my-app", null, null));
        assertEquals("/opt/my-app/application.properties", DebianPaths.configFile("my-app", "/opt/my-app", null));
        assertEquals("/opt/custom/conf.props", DebianPaths.configFile("my-app", "/opt/my-app", "/opt/custom/conf.props"));
    }

    @Test
    void resolvesSocketPath() {
        assertEquals("/run/my-app/control.sock", DebianPaths.socketPath("my-app", null));
        assertEquals("/var/run/custom.sock", DebianPaths.socketPath("my-app", "/var/run/custom.sock"));
        assertEquals("/var/run/custom.sock", DebianPaths.socketPath("my-app", "\\var\\run\\custom.sock"));
        assertEquals("/var/run/custom.sock", DebianPaths.socketPath("my-app", "/var/run/custom.sock/"));
    }
}
