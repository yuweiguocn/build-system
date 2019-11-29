/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.internal.incremental.annotated.OuterClassFor21;
import com.android.build.gradle.internal.incremental.annotated.SomeClassImplementingInterfaces;
import com.android.build.gradle.internal.incremental.annotated.SomeInterface;
import com.android.build.gradle.internal.incremental.annotated.SomeInterfaceWithDefaultMethods;
import com.android.utils.ILogger;
import com.android.utils.NullLogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import java.io.IOException;
import java.util.stream.Collectors;
import org.junit.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

/**
 * Tests for the {@link AsmUtils} class
 */
public class AsmUtilsTest {

    static final AsmUtils.ClassNodeProvider classReaderProvider = new ClassNodeProviderForTests();

    ILogger iLogger = new NullLogger();

    @Test
    public void testGetOuterClassName() throws IOException {

        {
            ClassNode classNode = AsmUtils
                    .readClass(OuterClassFor21.class.getClassLoader(),
                            Type.getType(OuterClassFor21.InnerClass.InnerInnerClass.class)
                                    .getInternalName());

            assertThat(AsmUtils.getOuterClassName(classNode)).isEqualTo(
                    Type.getType(OuterClassFor21.InnerClass.class).getInternalName());
        }
        {
            ClassNode classNode = AsmUtils
                    .readClass(OuterClassFor21.class.getClassLoader(),
                            Type.getType(OuterClassFor21.InnerClass.class).getInternalName());

            assertThat(AsmUtils.getOuterClassName(classNode)).isEqualTo(
                    Type.getType(OuterClassFor21.class).getInternalName());
        }
        {
            ClassNode classNode = AsmUtils
                    .readClass(OuterClassFor21.class.getClassLoader(),
                            Type.getType(OuterClassFor21.class)
                                    .getInternalName());

            assertThat(AsmUtils.getOuterClassName(classNode)).isNull();
        }
    }

    @Test
    public void testReadInterfaces() throws IOException {
        ClassNode classNode =
                AsmUtils.loadClass(
                        classReaderProvider,
                        Type.getType(SomeClassImplementingInterfaces.class).getInternalName(),
                        iLogger);

        ImmutableList.Builder<AsmInterfaceNode> listBuilder = ImmutableList.builder();
        assertThat(AsmUtils.readInterfaces(classNode, classReaderProvider, listBuilder, iLogger))
                .isTrue();

        ImmutableList<AsmInterfaceNode> interfaceNodes = listBuilder.build();
        assertThat(interfaceNodes).hasSize(2);
        for (AsmInterfaceNode interfaceNode : interfaceNodes) {
            assertThat(interfaceNode.getClassNode().name)
                    .isAnyOf(
                            Type.getType(SomeInterface.class).getInternalName(),
                            Type.getType(SomeInterfaceWithDefaultMethods.class).getInternalName());
        }
    }

    @Test
    public void testReadClassAndInterfaces() throws IOException {

        AsmClassNode classNodeAndInterfaces =
                AsmUtils.readClassAndInterfaces(
                        classReaderProvider,
                        Type.getType(SomeClassImplementingInterfaces.class).getInternalName(),
                        "Object",
                        21,
                        iLogger);

        assertThat(classNodeAndInterfaces).isNotNull();
        assertThat(classNodeAndInterfaces.getClassNode().name)
                .isEqualTo(Type.getType(SomeClassImplementingInterfaces.class).getInternalName());
        assertThat(classNodeAndInterfaces.getInterfaces()).hasSize(2);
        for (AsmInterfaceNode implementedInterface : classNodeAndInterfaces.getInterfaces()) {
            assertThat(implementedInterface.getClassNode().name)
                    .isAnyOf(
                            Type.getType(SomeInterface.class).getInternalName(),
                            Type.getType(SomeInterfaceWithDefaultMethods.class).getInternalName());
        }
    }

    private interface itfA {
        void methodA();
    }

    private interface itfB {
        void methodB();
    }

    private interface itfC extends itfA, itfB {
        void methodC();
    }

    private interface itfD {
        void methodD();
    }

    private interface itfE extends itfD {
        void methodE();
    }

    private static class classA implements itfA {

        @Override
        public void methodA() {}
    }

    private static class classB extends classA implements itfC {

        @Override
        public void methodA() {}

        @Override
        public void methodB() {}

        @Override
        public void methodC() {}
    }

    public static class classC implements itfC {

        @Override
        public void methodA() {}

        @Override
        public void methodB() {}

        @Override
        public void methodC() {}
    }

    public static class classD extends classB implements itfE {
        @Override
        public void methodE() {}

        @Override
        public void methodD() {}
    }

    public static class classE implements itfE {

        @Override
        public void methodD() {}

        @Override
        public void methodE() {}
    }

    @Test
    public void testReadMultipleInterfaceInheritance() throws IOException {
        AsmClassNode classNodeAndInterfaces =
                AsmUtils.readClassAndInterfaces(
                        classReaderProvider,
                        Type.getType(classC.class).getInternalName(),
                        "Object",
                        21,
                        iLogger);

        assertThat(classNodeAndInterfaces).isNotNull();
        assertThat(classNodeAndInterfaces.getClassNode().name)
                .isEqualTo("com/android/build/gradle/internal/incremental/AsmUtilsTest$classC");

        assertThat(classNodeAndInterfaces.getInterfaces()).hasSize(1);
        AsmInterfaceNode interfaceNode = classNodeAndInterfaces.getInterfaces().get(0);
        assertThat(interfaceNode).isNotNull();
        assertThat(interfaceNode.getClassNode().name)
                .isEqualTo("com/android/build/gradle/internal/incremental/AsmUtilsTest$itfC");
        assertThat(interfaceNode.getSuperInterfaces()).hasSize(2);
        assertThat(
                        Streams.stream(interfaceNode.getSuperInterfaces())
                                .map(superInterface -> superInterface.getClassNode().name)
                                .collect(Collectors.toList()))
                .containsExactly(
                        "com/android/build/gradle/internal/incremental/AsmUtilsTest$itfA",
                        "com/android/build/gradle/internal/incremental/AsmUtilsTest$itfB");
    }

    @Test
    public void testReadSingleInterfaceInheritance() throws IOException {
        AsmClassNode classNodeAndInterfaces =
                AsmUtils.readClassAndInterfaces(
                        classReaderProvider,
                        Type.getType(classE.class).getInternalName(),
                        "Object",
                        21,
                        iLogger);

        assertThat(classNodeAndInterfaces).isNotNull();
        assertThat(classNodeAndInterfaces.getClassNode().name)
                .isEqualTo("com/android/build/gradle/internal/incremental/AsmUtilsTest$classE");

        assertThat(classNodeAndInterfaces.getInterfaces()).hasSize(1);
        AsmInterfaceNode interfaceNode = classNodeAndInterfaces.getInterfaces().get(0);
        assertThat(interfaceNode).isNotNull();
        assertThat(interfaceNode.getClassNode().name)
                .isEqualTo("com/android/build/gradle/internal/incremental/AsmUtilsTest$itfE");
        assertThat(interfaceNode.getSuperInterfaces()).hasSize(1);
        interfaceNode = Iterables.getOnlyElement(interfaceNode.getSuperInterfaces());
        assertThat(interfaceNode).isNotNull();
        assertThat(interfaceNode.getClassNode().name)
                .isEqualTo("com/android/build/gradle/internal/incremental/AsmUtilsTest$itfD");
        assertThat(interfaceNode.getSuperInterfaces()).isEmpty();
    }

    @Test
    public void testReadMixedInterfaceInheritance() throws IOException {
        AsmClassNode classNode =
                AsmUtils.readClassAndInterfaces(
                        classReaderProvider,
                        Type.getType(classD.class).getInternalName(),
                        "Object",
                        21,
                        iLogger);

        assertThat(classNode).isNotNull();
        assertThat(classNode.getClassNode().name)
                .isEqualTo("com/android/build/gradle/internal/incremental/AsmUtilsTest$classD");

        assertThat(classNode.getInterfaces()).hasSize(1);
        AsmInterfaceNode interfaceNode = classNode.getInterfaces().get(0);
        assertThat(interfaceNode).isNotNull();
        assertThat(interfaceNode.getClassNode().name)
                .isEqualTo("com/android/build/gradle/internal/incremental/AsmUtilsTest$itfE");
        assertThat(interfaceNode.getSuperInterfaces()).hasSize(1);

        // now go to parent classB
        classNode = classNode.getParent();
        assertThat(classNode).isNotNull();
        assertThat(classNode.getClassNode().name)
                .isEqualTo("com/android/build/gradle/internal/incremental/AsmUtilsTest$classB");

        assertThat(classNode.getInterfaces()).hasSize(1);
        interfaceNode = classNode.getInterfaces().get(0);
        assertThat(interfaceNode).isNotNull();
        assertThat(interfaceNode.getClassNode().name)
                .isEqualTo("com/android/build/gradle/internal/incremental/AsmUtilsTest$itfC");
        assertThat(interfaceNode.getSuperInterfaces()).hasSize(2);
        assertThat(
                        Streams.stream(interfaceNode.getSuperInterfaces())
                                .map(superInterface -> superInterface.getClassNode().name)
                                .collect(Collectors.toList()))
                .containsExactly(
                        "com/android/build/gradle/internal/incremental/AsmUtilsTest$itfA",
                        "com/android/build/gradle/internal/incremental/AsmUtilsTest$itfB");

        // now to go parent classA
        classNode = classNode.getParent();
        assertThat(classNode).isNotNull();
        assertThat(classNode.getClassNode().name)
                .isEqualTo("com/android/build/gradle/internal/incremental/AsmUtilsTest$classA");

        assertThat(classNode.getInterfaces()).hasSize(1);
        interfaceNode = classNode.getInterfaces().get(0);
        assertThat(interfaceNode).isNotNull();
        assertThat(interfaceNode.getClassNode().name)
                .isEqualTo("com/android/build/gradle/internal/incremental/AsmUtilsTest$itfA");
        assertThat(interfaceNode.getSuperInterfaces()).isEmpty();

        // final Object.
        classNode = classNode.getParent();
        assertThat(classNode).isNotNull();
        assertThat(classNode.getClassNode().name).isEqualTo("java/lang/Object");
    }
}
