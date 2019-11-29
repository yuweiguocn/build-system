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
import java.util.List;
import java.util.function.Function;
import org.objectweb.asm.tree.ClassNode;

/**
 * Java Class encapsulation. Each node will have a pointer to it's parent class (or null if there is
 * no parent) as well as a list of implemented interfaces.
 */
class AsmClassNode extends AsmAbstractNode {
    @Nullable private final AsmClassNode parent;
    @NonNull private final List<AsmInterfaceNode> implementedInterfaces;

    /**
     * Construct a new class encapsulation with an optional parent class information and a
     * potentially empty list of implemented interfaces.
     *
     * @param classNode the ASM class node reference.
     * @param parent the parent class encapsulation or null if no parent.
     * @param implementedInterfaces the potentially empty list of implemented interfaces.
     */
    AsmClassNode(
            @NonNull ClassNode classNode,
            @Nullable AsmClassNode parent,
            @NonNull List<AsmInterfaceNode> implementedInterfaces) {
        super(classNode);
        this.parent = parent;
        this.implementedInterfaces = implementedInterfaces;
    }

    /**
     * Perform some work on the entire class hierarchy but not on its implemented interfaces. The
     * passed function will be invoked on this class first and then on its parents in the natural
     * order. If the passed function returns a non null value, the processing halts and the value is
     * returned. If the processing reach the top of the hierarchy and no value was ever returned by
     * the passed function invocations, this function will in turn return null.
     *
     * @param function some processing to apply on each class definition.
     * @param <T> the type of data produced by the function.
     * @return the first non null value returned by the passed function or null if all returned
     *     null.
     */
    @Nullable
    <T> T onHierarchy(Function<ClassNode, T> function) {
        T value = function.apply(getClassNode());
        if (value != null) {
            return value;
        }
        if (parent != null) {
            return parent.onHierarchy(function);
        }
        // no parent, we are done.
        return null;
    }

    /**
     * Perform some work on the parent class hierarchy but not on its implemented interfaces. The
     * passed function will be invoked on this class' parents in the natural order. If the passed
     * function returns a non null value, the processing halts and the value is returned. If the
     * processing reach the top of the hierarchy and no value was ever returned by the passed
     * function invocations, this function will in turn return null.
     *
     * @param function some processing to apply on each class definition.
     * @param <T> the type of data produced by the function.
     * @return the first non null value returned by the passed function or null if all returned
     *     null.
     */
    @Nullable
    <T> T onParents(Function<ClassNode, T> function) {
        if (parent != null) {
            T value = function.apply(parent.getClassNode());
            if (value != null) {
                return value;
            }
            return parent.onParents(function);
        }
        return null;
    }

    /**
     * Perform some work on the parent class hierarchy and on all its implemented interfaces.
     *
     * <p>The passed function will be invoked on this class, then on all implemented interfaces by
     * this class and finally it will call on all this class's parents in the natural order. If the
     * passed function returns a non null value, the processing halts and the value is returned. If
     * the processing reach the top of the hierarchy and no value was ever returned by the passed
     * function invocations, this function will in turn return null.
     *
     * @param function some processing to apply on each class definition.
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
        for (AsmInterfaceNode implementedInterface : implementedInterfaces) {
            value = implementedInterface.onAll(function);
            if (value != null) {
                return value;
            }
        }
        if (parent != null) {
            return parent.onAll(function);
        }
        // no parent, we are done.
        return null;
    }

    /** Returns true if this class has a parent, false otherwise */
    public boolean hasParent() {
        return parent != null;
    }

    /** Returns the parent of this class or null if it has no parent. */
    @NonNull
    public AsmClassNode getParent() {
        if (parent == null) {
            throw new IllegalStateException(
                    String.format(
                            "getParent() called on %s which has not parent", getClassNode().name));
        }
        return parent;
    }

    /**
     * Returns the list of implemented interfaces by this class.
     *
     * @return the interface hierarchy list.
     */
    public List<AsmInterfaceNode> getInterfaces() {
        return implementedInterfaces;
    }
}
