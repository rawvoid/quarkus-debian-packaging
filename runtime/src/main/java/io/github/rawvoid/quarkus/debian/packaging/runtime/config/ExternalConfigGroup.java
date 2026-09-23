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

package io.github.rawvoid.quarkus.debian.packaging.runtime.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Manages the active group of external configuration sources (main configuration file
 * and profile-specific companion files).
 *
 * @author rawvoid
 */
public final class ExternalConfigGroup {

    private static volatile ExternalConfigGroup instance;

    private final Path mainConfigFile;
    private final List<ExternalConfigSource> sources;

    public ExternalConfigGroup(Path mainConfigFile, List<ExternalConfigSource> sources) {
        this.mainConfigFile = Objects.requireNonNull(mainConfigFile, "mainConfigFile");
        this.sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
    }

    public static ExternalConfigGroup getInstance() {
        return instance;
    }

    public static void register(ExternalConfigGroup group) {
        instance = Objects.requireNonNull(group, "group");
    }

    public static void clear() {
        instance = null;
    }

    public Path getMainConfigFile() {
        return mainConfigFile;
    }

    public List<ExternalConfigSource> getSources() {
        return sources;
    }

    public ExternalConfigSource getMainSource() {
        return sources.isEmpty() ? null : sources.get(0);
    }
}
