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
package io.github.rawvoid.quarkus.debian.packaging.it;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/**
 * REST resource used by the integration tests.
 *
 * @author rawvoid
 */
@Path("/quarkus-debian-packaging")
@ApplicationScoped
public class QuarkusDebianPackagingResource {
    // add some rest methods here

    @GET
    public String hello() {
        return "Hello quarkus-debian-packaging";
    }
}
