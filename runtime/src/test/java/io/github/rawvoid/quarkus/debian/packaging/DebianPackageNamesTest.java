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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DebianPackageNamesTest {

    @ParameterizedTest
    @CsvSource({
            "My_App, my-app",
            "my_service, my-service",
            "my service, my-service",
            "--hello--world--, hello-world",
            "MyApp_v2.0, myapp-v2.0",
            "test+app, test+app"
    })
    void testSanitizeValid(String input, String expected) {
        assertEquals(expected, DebianPackageNames.sanitize(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "<<unset>>" })
    void testSanitizeInvalid(String input) {
        assertThrows(IllegalArgumentException.class, () -> DebianPackageNames.sanitize(input));
    }
}
