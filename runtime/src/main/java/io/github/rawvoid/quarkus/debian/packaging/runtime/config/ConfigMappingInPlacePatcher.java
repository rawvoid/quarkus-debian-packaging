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

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * In-place hot patcher for SmallRye ConfigMapping instances.
 * <p>
 * Directly writes updated values from a newly validated configuration snapshot into
 * the existing singleton instance fields using reflection and {@link VarHandle}, followed
 * by a release fence. This provides zero-overhead hot reload on the business hot path
 * (standard single-cycle {@code GETFIELD} without any proxy, wrapper, or lock).
 *
 * @author rawvoid
 */
public final class ConfigMappingInPlacePatcher {

    private ConfigMappingInPlacePatcher() {
    }

    /**
     * Patches the existing singleton instance in-place with values from the new snapshot.
     *
     * @param existingInstance the current live instance injected into application beans
     * @param newSnapshot      the new snapshot instance created from the reloaded config
     */
    public static void patch(Object existingInstance, Object newSnapshot) {
        if (existingInstance == null || newSnapshot == null) {
            return;
        }
        patchInternal(existingInstance, newSnapshot, Collections.newSetFromMap(new IdentityHashMap<>()));
        VarHandle.releaseFence();
    }

    private static void patchInternal(Object existingInstance, Object newSnapshot, Set<Object> visited) {
        if (existingInstance == newSnapshot || !visited.add(existingInstance)) {
            return;
        }

        Class<?> clazz = existingInstance.getClass();
        if (!clazz.equals(newSnapshot.getClass())) {
            return;
        }

        for (Field field : clazz.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (Modifier.isStatic(mods)) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object newVal = field.get(newSnapshot);
                Object oldVal = field.get(existingInstance);

                // If this is a nested config mapping instance, recursively patch it in-place
                if (oldVal != null && newVal != null && oldVal.getClass().equals(newVal.getClass())
                        && isConfigMappingGenerated(oldVal.getClass())) {
                    patchInternal(oldVal, newVal, visited);
                } else {
                    field.set(existingInstance, newVal);
                }
            } catch (Exception ignored) {
                // Inaccessible fields or security manager restrictions
            }
        }
    }

    private static boolean isConfigMappingGenerated(Class<?> type) {
        return type.getName().contains("$$CMImpl");
    }
}
