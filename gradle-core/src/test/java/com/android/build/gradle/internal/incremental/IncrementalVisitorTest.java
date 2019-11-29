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

import com.android.utils.ILogger;
import com.android.utils.NullLogger;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * tests for the {@link IncrementalVisitor}
 */
public class IncrementalVisitorTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testClassEligibility() throws IOException {
        File fooDotBar = temporaryFolder.newFile("foo.bar");
        assertThat(IncrementalVisitor.isClassEligibleForInstantRun(fooDotBar)).isFalse();

        File RDotClass = temporaryFolder.newFile("R.class");
        assertThat(IncrementalVisitor.isClassEligibleForInstantRun(RDotClass)).isFalse();

        File RdimenDotClass = temporaryFolder.newFile("R$dimen.class");
        assertThat(IncrementalVisitor.isClassEligibleForInstantRun(RdimenDotClass)).isFalse();

        File someClass = temporaryFolder.newFile("Some.class");
        assertThat(IncrementalVisitor.isClassEligibleForInstantRun(someClass)).isTrue();

        File RSomethingClass = temporaryFolder.newFile("Rsomething.class");
        assertThat(IncrementalVisitor.isClassEligibleForInstantRun(RSomethingClass)).isTrue();

        File RSomethingWithInner = temporaryFolder.newFile("Rsome$dimen.class");
        assertThat(IncrementalVisitor.isClassEligibleForInstantRun(RSomethingWithInner)).isTrue();
    }

    /**
     * It's possible for us to be given a class to instument for instant run that inherits from a
     * class that does not exist. The canonical use case is when a class inherits from a class that
     * exists in some API level, but we're compiling against an API lower than that. In these
     * situations we want to skip instrumenting these classes because they're not going to be used
     * anyway (if they do get used the app will crash anyway).
     *
     * <p>Another use-case is when a library has a compileOnly dependency on something, and a class
     * inherits from something in that compileOnly dependency.
     *
     * <p>See https://issuetracker.google.com/72811718 for discussions about this problem.
     */
    @Test
    public void testClassWithNonExistentSuperClass() throws Exception {
        ClassWriter cw;

        // Class A: this class inherits from Object and is perfectly valid, and should be
        // instrumented for instant run.
        cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_6, Opcodes.ACC_SUPER, "A", null, "java/lang/Object", null);
        cw.visitEnd();
        File a = temporaryFolder.newFile("A.class");
        Files.write(a.toPath(), cw.toByteArray());

        // Class B: this class inherits from a non-existent class, and is not valid, and thus
        // should not be instrumented for instant run.
        cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_6, Opcodes.ACC_SUPER, "B", null, "NonExistentSuperClass", null);
        cw.visitEnd();
        File b = temporaryFolder.newFile("B.class");
        Files.write(b.toPath(), cw.toByteArray());

        int apiLevel = 24;
        File root = temporaryFolder.getRoot();
        File outDir = temporaryFolder.newFolder();
        ILogger logger = new NullLogger();

        // Instrument class A, producing a new file that should not contain the same bytecode as
        // A originally did (because we have instrumented it).
        File aOut =
                IncrementalVisitor.instrumentClass(
                        apiLevel,
                        root,
                        a,
                        outDir,
                        IncrementalSupportVisitor.VISITOR_BUILDER,
                        logger);

        // Instrument class B, producing a new file that should contain the same bytecode as B
        // originally did (because we have not instrumented it).
        File bOut =
                IncrementalVisitor.instrumentClass(
                        apiLevel,
                        root,
                        b,
                        outDir,
                        IncrementalSupportVisitor.VISITOR_BUILDER,
                        logger);

        // instrumentClass() _can_ return null, but we shouldn't hit any of the situations in which
        // that happens. If we do, something has gone wrong.
        assertThat(aOut).isNotNull();
        assertThat(bOut).isNotNull();

        assertThat(Files.readAllBytes(a.toPath())).isNotEqualTo(Files.readAllBytes(aOut.toPath()));
        assertThat(Files.readAllBytes(b.toPath())).isEqualTo(Files.readAllBytes(bOut.toPath()));
    }

    interface NoDefault {
        String func();
    }

    interface WithDefault {
        default String func() {
            return "func";
        }
    }

    private static class ImplWithDefault implements WithDefault {
        public void anotherFunc() {}
    }

    @Test
    public void testHasDefaultMethods() throws IOException {

        ClassNode interfaceNode =
                AsmUtils.readClass(
                        NoDefault.class.getClassLoader(),
                        Type.getType(NoDefault.class).getInternalName());

        assertThat(IncrementalVisitor.hasDefaultMethods(interfaceNode)).isFalse();

        interfaceNode =
                AsmUtils.readClass(
                        WithDefault.class.getClassLoader(),
                        Type.getType(WithDefault.class).getInternalName());

        assertThat(IncrementalVisitor.hasDefaultMethods(interfaceNode)).isTrue();
    }

    @Test
    public void testGetMethodByNameInClass() throws IOException {
        AsmUtils.ClassNodeProvider classReaderProvider = new ClassNodeProviderForTests();
        ILogger iLogger = new NullLogger();

        AsmClassNode classAndInterfacesNode =
                AsmUtils.readClassAndInterfaces(
                        classReaderProvider,
                        Type.getType(ImplWithDefault.class).getInternalName(),
                        "Object",
                        21,
                        iLogger);

        MethodNode method =
                IncrementalVisitor.getMethodByNameInClass(
                        "func", "()Ljava/lang/String;", classAndInterfacesNode);

        assertThat(method).isNotNull();
        assertThat(method.name).isEqualTo("func");

        method =
                IncrementalVisitor.getMethodByNameInClass(
                        "anotherFunc", "()V", classAndInterfacesNode);

        assertThat(method).isNotNull();
        assertThat(method.name).isEqualTo("anotherFunc");
    }
}
