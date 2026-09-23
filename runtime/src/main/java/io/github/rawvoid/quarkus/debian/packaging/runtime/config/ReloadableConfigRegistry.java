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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe registry providing atomic pointer swapping for reloadable configuration mappings.
 *
 * @author rawvoid
 */
public final class ReloadableConfigRegistry {

    public record MappingKey(Class<?> mappingClass, String prefix) {
        public MappingKey {
            Objects.requireNonNull(mappingClass, "mappingClass must not be null");
            prefix = prefix != null ? prefix : "";
        }
    }

    private static final Map<MappingKey, AtomicReference<Object>> REGISTRY = new ConcurrentHashMap<>();

    private ReloadableConfigRegistry() {
    }

    public static void register(Class<?> mappingClass, String prefix, Object initialSnapshot) {
        Objects.requireNonNull(mappingClass, "mappingClass must not be null");
        REGISTRY.computeIfAbsent(new MappingKey(mappingClass, prefix), k -> new AtomicReference<>(initialSnapshot))
                .set(initialSnapshot);
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> mappingClass, String prefix) {
        var ref = REGISTRY.get(new MappingKey(mappingClass, prefix));
        if (ref == null) {
            return null;
        }
        return (T) ref.get();
    }

    public static void swap(Class<?> mappingClass, String prefix, Object newSnapshot) {
        Objects.requireNonNull(mappingClass, "mappingClass must not be null");
        Objects.requireNonNull(newSnapshot, "newSnapshot must not be null");
        REGISTRY.computeIfAbsent(new MappingKey(mappingClass, prefix), k -> new AtomicReference<>(newSnapshot))
                .set(newSnapshot);
    }

    public static void clear() {
        REGISTRY.clear();
    }
}
