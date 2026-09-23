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

import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;
import io.smallrye.config.SmallRyeConfig;

/**
 * Arc synthetic bean creator that instantiates AOT reloadable configuration proxies.
 *
 * @author rawvoid
 */
public class ReloadableConfigCreator implements BeanCreator<Object> {

    @Override
    public Object create(SyntheticCreationalContext<Object> context) {
        String proxyClassName = (String) context.getParams().get("proxyClassName");
        String mappingClassName = (String) context.getParams().get("mappingClassName");
        String prefix = (String) context.getParams().get("prefix");

        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> clazz = Class.forName(mappingClassName, false, cl);
            if (ReloadableConfigRegistry.get(clazz, prefix) == null) {
                SmallRyeConfig currentConfig = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
                Object snapshot = (prefix != null && !prefix.isEmpty())
                        ? currentConfig.getConfigMapping(clazz, prefix)
                        : currentConfig.getConfigMapping(clazz);
                ReloadableConfigRegistry.register(clazz, prefix, snapshot);
            }
            Class<?> proxyClass = Class.forName(proxyClassName, false, cl);
            return proxyClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create reloadable config proxy for " + mappingClassName, e);
        }
    }
}
