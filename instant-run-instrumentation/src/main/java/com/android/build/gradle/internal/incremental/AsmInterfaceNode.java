/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal.incremental;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.ImmutableList;
import java.util.function.Function;
import org.objectweb.asm.tree.ClassNode;

/**
 * Java Interface hierarchy encapsulation. Each node will keep a reference to the ASM {@link
 * ClassNode} representing the interface as well as reference to the parent interface encapsulated
 * reference.
 */
class AsmInterfaceNode extends AsmAbstractNode {
    @NonNull private final ImmutableList<AsmInterfaceNode> superInterfaces;

    /**
     * Construct a new interface encapsulation with an optional super interface.
     *
     * @param classNode the interface {@link ClassNode}
     * @param superInterfaces potentially empty list of interfaces this interface extend.
     */
    AsmInterfaceNode(
            @NonNull ClassNode classNode,
            @NonNull ImmutableList<AsmInterfaceNode> superInterfaces) {
        super(classNode);
        this.superInterfaces = superInterfaces;
    }

    /**
     * Perform some work on the entire interface hierarchy. The passed function will be invoked on
     * this interface first and then on its parents in the natural order. If the passed function
     * returns a non null value, the processing halts and the value is returned. If the processing
     * reach the top of the hierarchy and no value was ever returned by the passed function
     * invocations, this function will in turn return null.
     *
     * @param function some processing to apply on each interface definition.
     * @param <T> the type of data produced by the function.
     * @return the first non null value returned by the passed function or null if all returned
     *     null.
     */
    @Nullable
    <T> T onAll(Function<ClassNode, T> function) {
        T value = function.apply(getClassNode());
        if (value != null) {
            return value;
        }
        for (AsmInterfaceNode extendedInterface : superInterfaces) {
            value = extendedInterface.onAll(function);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    Iterable<AsmInterfaceNode> getSuperInterfaces() {
        return superInterfaces;
    }
}
