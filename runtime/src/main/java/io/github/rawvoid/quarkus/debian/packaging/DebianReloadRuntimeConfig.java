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

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Runtime configuration for the Debian packaging control socket and reload server.
 *
 * @author rawvoid
 */
@ConfigMapping(prefix = "quarkus.debian.reload")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface DebianReloadRuntimeConfig {

    /**
     * Whether to enable UNIX domain socket reload support at runtime.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Path to the control UNIX domain socket. Defaults to {@code /run/${packageName}/control.sock}.
     */
    Optional<String> socketPath();
}
