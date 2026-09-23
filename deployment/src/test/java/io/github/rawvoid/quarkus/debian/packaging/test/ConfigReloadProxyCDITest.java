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

package io.github.rawvoid.quarkus.debian.packaging.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.github.rawvoid.quarkus.debian.packaging.runtime.config.ReloadableConfigRegistry;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.config.ConfigMapping;

public class ConfigReloadProxyCDITest {

    @ConfigMapping(prefix = "greeting")
    public interface GreetingConfig {
        String message();
        int repeat();
    }

    public record GreetingConfigSnapshot(String message, int repeat) implements GreetingConfig {}

    @ApplicationScoped
    public static class GreetingService {
        @Inject
        GreetingConfig config;

        public String getMessage() {
            return config.message();
        }

        public int getRepeat() {
            return config.repeat();
        }

        public GreetingConfig getConfig() {
            return config;
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(GreetingConfig.class, GreetingConfigSnapshot.class, GreetingService.class)
                    .addAsResource(new StringAsset("greeting.message=Hello\ngreeting.repeat=3\n"), "application.properties"));

    @Inject
    GreetingService service;

    @Test
    void testInjectedProxySwapsSnapshotAtomically() {
        GreetingConfig proxyInstance = service.getConfig();
        assertNotNull(proxyInstance);
        assertTrue(proxyInstance.getClass().getName().contains("$$ReloadProxy"),
                "Expected injected bean to be the generated $$ReloadProxy class, got: " + proxyInstance.getClass().getName());

        assertEquals("Hello", service.getMessage());
        assertEquals(3, service.getRepeat());

        // Perform atomic snapshot swap in registry
        GreetingConfig updatedSnapshot = new GreetingConfigSnapshot("Bonjour", 5);
        ReloadableConfigRegistry.swap(GreetingConfig.class, "greeting", updatedSnapshot);

        // Verify service immediately reads new values through same proxy instance
        assertSame(proxyInstance, service.getConfig(), "Proxy instance identity should remain constant");
        assertEquals("Bonjour", service.getMessage());
        assertEquals(5, service.getRepeat());
    }
}
