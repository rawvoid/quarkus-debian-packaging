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
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class ConfigMappingInPlacePatcherTest {

    static class SampleNestedConfig$$CMImpl {
        private String host;
        private int port;

        public SampleNestedConfig$$CMImpl(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public String host() {
            return host;
        }

        public int port() {
            return port;
        }
    }

    static class SampleConfig$$CMImpl {
        private String name;
        private int timeout;
        private SampleNestedConfig$$CMImpl database;

        public SampleConfig$$CMImpl(String name, int timeout, SampleNestedConfig$$CMImpl database) {
            this.name = name;
            this.timeout = timeout;
            this.database = database;
        }

        public String name() {
            return name;
        }

        public int timeout() {
            return timeout;
        }

        public SampleNestedConfig$$CMImpl database() {
            return database;
        }
    }

    @Test
    void testInPlacePatchingPreservesIdentityAndUpdatesFields() {
        var existingNested = new SampleNestedConfig$$CMImpl("localhost", 5432);
        var existing = new SampleConfig$$CMImpl("initial-app", 30, existingNested);

        var newNested = new SampleNestedConfig$$CMImpl("remote-host", 6432);
        var snapshot = new SampleConfig$$CMImpl("updated-app", 60, newNested);

        // Before patch
        assertEquals("initial-app", existing.name());
        assertEquals(30, existing.timeout());
        assertEquals("localhost", existing.database().host());
        assertEquals(5432, existing.database().port());

        // Perform in-place hot patching
        ConfigMappingInPlacePatcher.patch(existing, snapshot);

        // Verify that existing instance fields have been updated in-place
        assertEquals("updated-app", existing.name());
        assertEquals(60, existing.timeout());

        // Verify that existing nested instance fields have ALSO been updated in-place
        assertSame(existingNested, existing.database());
        assertEquals("remote-host", existing.database().host());
        assertEquals(6432, existing.database().port());
        assertEquals("remote-host", existingNested.host());
        assertEquals(6432, existingNested.port());
    }
}
