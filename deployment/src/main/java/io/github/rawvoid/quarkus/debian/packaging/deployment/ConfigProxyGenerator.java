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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import io.github.rawvoid.quarkus.debian.packaging.runtime.config.ReloadableConfigRegistry;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.gizmo.BranchResult;
import io.quarkus.gizmo.BytecodeCreator;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;

/**
 * Generates AOT bytecode proxies for reloadable configuration mappings using Gizmo.
 *
 * @author rawvoid
 */
public final class ConfigProxyGenerator {

    public static final String PROXY_SUFFIX = "$$ReloadProxy";

    private ConfigProxyGenerator() {
    }

    public static String getProxyClassName(Class<?> interfaceClass) {
        Objects.requireNonNull(interfaceClass, "interfaceClass must not be null");
        return interfaceClass.getName() + PROXY_SUFFIX;
    }

    public static String generate(
            Class<?> interfaceClass,
            String prefix,
            BuildProducer<GeneratedClassBuildItem> generatedClassProducer) {

        Objects.requireNonNull(interfaceClass, "interfaceClass must not be null");
        String proxyClassName = getProxyClassName(interfaceClass);
        ClassOutput classOutput = (name, data) -> generatedClassProducer.produce(new GeneratedClassBuildItem(true, name, data));

        try (ClassCreator cc = ClassCreator.builder()
                .classOutput(classOutput)
                .className(proxyClassName)
                .interfaces(interfaceClass)
                .build()) {

            // Default constructor
            MethodCreator ctor = cc.getMethodCreator(MethodDescriptor.INIT, void.class);
            ctor.invokeSpecialMethod(MethodDescriptor.ofConstructor(Object.class), ctor.getThis());
            ctor.returnVoid();

            // Delegate all interface methods
            Set<String> generatedSignatures = new HashSet<>();
            for (Method method : interfaceClass.getMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || method.getDeclaringClass().equals(Object.class)) {
                    continue;
                }
                String sig = method.getName() + Arrays.toString(method.getParameterTypes());
                if (!generatedSignatures.add(sig)) {
                    continue;
                }

                MethodCreator mc = cc.getMethodCreator(MethodDescriptor.ofMethod(method));
                ResultHandle target = mc.invokeStaticMethod(
                        MethodDescriptor.ofMethod(ReloadableConfigRegistry.class, "get", Object.class, Class.class, String.class),
                        mc.loadClass(interfaceClass),
                        mc.load(prefix != null ? prefix : "")
                );
                ResultHandle typedTarget = mc.checkCast(target, interfaceClass);

                ResultHandle[] params = new ResultHandle[method.getParameterCount()];
                for (int i = 0; i < params.length; i++) {
                    params[i] = mc.getMethodParam(i);
                }

                ResultHandle ret = mc.invokeInterfaceMethod(MethodDescriptor.ofMethod(method), typedTarget, params);
                if (method.getReturnType().equals(void.class)) {
                    mc.returnVoid();
                } else {
                    mc.returnValue(ret);
                }
            }

            // Object#toString delegation
            MethodCreator ts = cc.getMethodCreator("toString", String.class);
            ResultHandle tsTarget = ts.invokeStaticMethod(
                    MethodDescriptor.ofMethod(ReloadableConfigRegistry.class, "get", Object.class, Class.class, String.class),
                    ts.loadClass(interfaceClass),
                    ts.load(prefix != null ? prefix : "")
            );
            BranchResult tsBranch = ts.ifNull(tsTarget);
            BytecodeCreator tsNull = tsBranch.trueBranch();
            tsNull.returnValue(tsNull.load(interfaceClass.getSimpleName() + PROXY_SUFFIX + "[uninitialized]"));
            BytecodeCreator tsNotNull = tsBranch.falseBranch();
            tsNotNull.returnValue(tsNotNull.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(Object.class, "toString", String.class),
                    tsTarget
            ));

            // Object#hashCode delegation
            MethodCreator hc = cc.getMethodCreator("hashCode", int.class);
            ResultHandle hcTarget = hc.invokeStaticMethod(
                    MethodDescriptor.ofMethod(ReloadableConfigRegistry.class, "get", Object.class, Class.class, String.class),
                    hc.loadClass(interfaceClass),
                    hc.load(prefix != null ? prefix : "")
            );
            BranchResult hcBranch = hc.ifNull(hcTarget);
            BytecodeCreator hcNull = hcBranch.trueBranch();
            hcNull.returnValue(hcNull.invokeStaticMethod(
                    MethodDescriptor.ofMethod(System.class, "identityHashCode", int.class, Object.class),
                    hcNull.getThis()
            ));
            BytecodeCreator hcNotNull = hcBranch.falseBranch();
            hcNotNull.returnValue(hcNotNull.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(Object.class, "hashCode", int.class),
                    hcTarget
            ));

            // Object#equals delegation
            MethodCreator eq = cc.getMethodCreator("equals", boolean.class, Object.class);
            ResultHandle eqTarget = eq.invokeStaticMethod(
                    MethodDescriptor.ofMethod(ReloadableConfigRegistry.class, "get", Object.class, Class.class, String.class),
                    eq.loadClass(interfaceClass),
                    eq.load(prefix != null ? prefix : "")
            );
            BranchResult eqBranch = eq.ifNull(eqTarget);
            BytecodeCreator eqNull = eqBranch.trueBranch();
            BranchResult refBranch = eqNull.ifReferencesEqual(eqNull.getThis(), eqNull.getMethodParam(0));
            refBranch.trueBranch().returnValue(refBranch.trueBranch().load(true));
            refBranch.falseBranch().returnValue(refBranch.falseBranch().load(false));
            BytecodeCreator eqNotNull = eqBranch.falseBranch();
            eqNotNull.returnValue(eqNotNull.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(Object.class, "equals", boolean.class, Object.class),
                    eqTarget,
                    eqNotNull.getMethodParam(0)
            ));
        }

        return proxyClassName;
    }
}
