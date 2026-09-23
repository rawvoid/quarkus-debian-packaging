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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.rawvoid.quarkus.debian.packaging.runtime.config.ReloadableConfigRegistry;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;

class ConfigProxyGeneratorTest {

    public interface SampleServiceConfig {
        String host();
        int port();
        boolean active();
    }

    public record SampleServiceConfigImpl(String host, int port, boolean active) implements SampleServiceConfig {}

    private static class ByteArrayClassLoader extends ClassLoader {
        private final Map<String, byte[]> classMap = new HashMap<>();

        ByteArrayClassLoader(ClassLoader parent) {
            super(parent);
        }

        void register(String name, byte[] data) {
            classMap.put(name, data);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classMap.get(name);
            if (bytes != null) {
                return defineClass(name, bytes, 0, bytes.length);
            }
            return super.findClass(name);
        }
    }

    @AfterEach
    void cleanup() {
        ReloadableConfigRegistry.clear();
    }

    @Test
    void testProxyGenerationAndDelegation() throws Exception {
        var cl = new ByteArrayClassLoader(Thread.currentThread().getContextClassLoader());
        Map<String, byte[]> generated = new HashMap<>();

        String proxyClassName = ConfigProxyGenerator.generate(
                SampleServiceConfig.class,
                "service",
                item -> generated.put(item.binaryName(), item.getClassData())
        );

        assertTrue(generated.containsKey(proxyClassName));
        for (var entry : generated.entrySet()) {
            cl.register(entry.getKey(), entry.getValue());
        }

        Class<?> proxyClass = cl.loadClass(proxyClassName);
        assertNotNull(proxyClass);
        assertTrue(SampleServiceConfig.class.isAssignableFrom(proxyClass));

        SampleServiceConfig proxy = (SampleServiceConfig) proxyClass.getDeclaredConstructor().newInstance();

        // Initial snapshot
        var initial = new SampleServiceConfigImpl("127.0.0.1", 8080, true);
        ReloadableConfigRegistry.register(SampleServiceConfig.class, "service", initial);

        assertEquals("127.0.0.1", proxy.host());
        assertEquals(8080, proxy.port());
        assertTrue(proxy.active());
        assertEquals(initial.toString(), proxy.toString());
        assertEquals(initial.hashCode(), proxy.hashCode());

        // Swap to updated snapshot
        var updated = new SampleServiceConfigImpl("10.0.0.1", 9090, false);
        ReloadableConfigRegistry.swap(SampleServiceConfig.class, "service", updated);

        assertEquals("10.0.0.1", proxy.host());
        assertEquals(9090, proxy.port());
        assertEquals(false, proxy.active());
        assertEquals(updated.toString(), proxy.toString());
        assertEquals(updated.hashCode(), proxy.hashCode());
    }
}
