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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ReloadableConfigRegistryTest {

    interface DummyConfig {
        String name();
        int port();
    }

    record DummyConfigImpl(String name, int port) implements DummyConfig {}

    @AfterEach
    void cleanup() {
        ReloadableConfigRegistry.clear();
    }

    @Test
    void testRegisterAndGet() {
        assertNull(ReloadableConfigRegistry.get(DummyConfig.class, "test"));

        var initial = new DummyConfigImpl("app", 8080);
        ReloadableConfigRegistry.register(DummyConfig.class, "test", initial);

        assertSame(initial, ReloadableConfigRegistry.get(DummyConfig.class, "test"));
    }

    @Test
    void testAtomicSwap() {
        var v1 = new DummyConfigImpl("app-v1", 8080);
        var v2 = new DummyConfigImpl("app-v2", 9090);

        ReloadableConfigRegistry.register(DummyConfig.class, "scoot", v1);
        assertEquals("app-v1", ReloadableConfigRegistry.get(DummyConfig.class, "scoot").name());

        ReloadableConfigRegistry.swap(DummyConfig.class, "scoot", v2);
        assertEquals("app-v2", ReloadableConfigRegistry.get(DummyConfig.class, "scoot").name());
        assertEquals(9090, ReloadableConfigRegistry.get(DummyConfig.class, "scoot").port());
    }

    @Test
    void testPrefixDifferentiation() {
        var cfgA = new DummyConfigImpl("alpha", 1111);
        var cfgB = new DummyConfigImpl("beta", 2222);

        ReloadableConfigRegistry.register(DummyConfig.class, "cluster.a", cfgA);
        ReloadableConfigRegistry.register(DummyConfig.class, "cluster.b", cfgB);

        assertEquals("alpha", ReloadableConfigRegistry.get(DummyConfig.class, "cluster.a").name());
        assertEquals("beta", ReloadableConfigRegistry.get(DummyConfig.class, "cluster.b").name());

        var cfgA2 = new DummyConfigImpl("alpha-updated", 3333);
        ReloadableConfigRegistry.swap(DummyConfig.class, "cluster.a", cfgA2);

        assertEquals("alpha-updated", ReloadableConfigRegistry.get(DummyConfig.class, "cluster.a").name());
        assertEquals("beta", ReloadableConfigRegistry.get(DummyConfig.class, "cluster.b").name());
    }

    @Test
    void testConcurrentReadsAndSwap() throws Exception {
        var initial = new DummyConfigImpl("v-0", 0);
        ReloadableConfigRegistry.register(DummyConfig.class, "", initial);

        int readerCount = 8;
        int iterations = 10_000;
        var executor = Executors.newFixedThreadPool(readerCount + 1);
        var startLatch = new CountDownLatch(1);
        var running = new AtomicBoolean(true);

        var readerTasks = new ArrayList<Callable<Void>>();
        for (int i = 0; i < readerCount; i++) {
            readerTasks.add(() -> {
                startLatch.await();
                while (running.get()) {
                    DummyConfig cfg = ReloadableConfigRegistry.get(DummyConfig.class, "");
                    assertNotNull(cfg);
                    assertNotNull(cfg.name());
                }
                return null;
            });
        }

        var futures = new ArrayList<Future<Void>>();
        for (var task : readerTasks) {
            futures.add(executor.submit(task));
        }

        var writerFuture = executor.submit(() -> {
            startLatch.await();
            for (int i = 1; i <= iterations; i++) {
                ReloadableConfigRegistry.swap(DummyConfig.class, "", new DummyConfigImpl("v-" + i, i));
            }
            running.set(false);
            return null;
        });

        startLatch.countDown();
        writerFuture.get(10, TimeUnit.SECONDS);
        for (var f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }

        executor.shutdown();
        assertEquals("v-" + iterations, ReloadableConfigRegistry.get(DummyConfig.class, "").name());
    }
}
