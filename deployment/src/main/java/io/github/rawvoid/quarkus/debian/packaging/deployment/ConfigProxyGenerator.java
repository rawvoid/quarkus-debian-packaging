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
import java.util.concurrent.atomic.AtomicReference;

import io.github.rawvoid.quarkus.debian.packaging.runtime.config.ReloadableConfigRegistry;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.gizmo.AssignableResultHandle;
import io.quarkus.gizmo.BranchResult;
import io.quarkus.gizmo.BytecodeCreator;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.FieldCreator;
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

            // Direct AtomicReference holder field to guarantee zero-allocation and zero-lookup hot path
            FieldCreator holderField = cc.getFieldCreator("holder", AtomicReference.class)
                    .setModifiers(Modifier.PRIVATE | Modifier.FINAL);

            // Default constructor binding the holder
            MethodCreator ctor = cc.getMethodCreator(MethodDescriptor.INIT, void.class);
            ctor.invokeSpecialMethod(MethodDescriptor.ofConstructor(Object.class), ctor.getThis());
            ResultHandle holderHandle = ctor.invokeStaticMethod(
                    MethodDescriptor.ofMethod(ReloadableConfigRegistry.class, "getHolder", AtomicReference.class, Class.class, String.class),
                    ctor.loadClass(interfaceClass),
                    ctor.load(prefix != null ? prefix : "")
            );
            ctor.writeInstanceField(holderField.getFieldDescriptor(), ctor.getThis(), holderHandle);
            ctor.returnVoid();

            // Delegate all interface methods via holder.get()
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
                ResultHandle holder = mc.readInstanceField(holderField.getFieldDescriptor(), mc.getThis());
                ResultHandle target = mc.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(AtomicReference.class, "get", Object.class),
                        holder
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
            ResultHandle tsHolder = ts.readInstanceField(holderField.getFieldDescriptor(), ts.getThis());
            ResultHandle tsTarget = ts.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(AtomicReference.class, "get", Object.class),
                    tsHolder
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
            ResultHandle hcHolder = hc.readInstanceField(holderField.getFieldDescriptor(), hc.getThis());
            ResultHandle hcTarget = hc.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(AtomicReference.class, "get", Object.class),
                    hcHolder
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
            ResultHandle other = eq.getMethodParam(0);

            // 1. Reference equality: if (this == other) return true;
            BranchResult sameRef = eq.ifReferencesEqual(eq.getThis(), other);
            sameRef.trueBranch().returnValue(sameRef.trueBranch().load(true));

            // 2. Null check: if (other == null) return false;
            BytecodeCreator notSame = sameRef.falseBranch();
            BranchResult isNull = notSame.ifNull(other);
            isNull.trueBranch().returnValue(isNull.trueBranch().load(false));

            // 3. Resolve target
            BytecodeCreator notNull = isNull.falseBranch();
            ResultHandle eqHolder = notNull.readInstanceField(holderField.getFieldDescriptor(), notNull.getThis());
            ResultHandle myTarget = notNull.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(AtomicReference.class, "get", Object.class),
                    eqHolder
            );

            // Unwrap other if it is another instance of proxyClassName
            AssignableResultHandle otherTarget = notNull.createVariable(Object.class);
            notNull.assign(otherTarget, other);
            BranchResult isProxy = notNull.ifTrue(notNull.instanceOf(other, proxyClassName));
            BytecodeCreator proxyBranch = isProxy.trueBranch();
            ResultHandle castOther = proxyBranch.checkCast(other, proxyClassName);
            ResultHandle otherHolder = proxyBranch.readInstanceField(holderField.getFieldDescriptor(), castOther);
            ResultHandle unwrapped = proxyBranch.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(AtomicReference.class, "get", Object.class),
                    otherHolder
            );
            proxyBranch.assign(otherTarget, unwrapped);

            // Compare myTarget and otherTarget
            BranchResult myNull = notNull.ifNull(myTarget);
            BytecodeCreator myNullBranch = myNull.trueBranch();
            BranchResult otherNull = myNullBranch.ifNull(otherTarget);
            otherNull.trueBranch().returnValue(otherNull.trueBranch().load(true));
            otherNull.falseBranch().returnValue(otherNull.falseBranch().load(false));

            BytecodeCreator myNotNullBranch = myNull.falseBranch();
            myNotNullBranch.returnValue(myNotNullBranch.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(Object.class, "equals", boolean.class, Object.class),
                    myTarget,
                    otherTarget
            ));
        }

        return proxyClassName;
    }
}
