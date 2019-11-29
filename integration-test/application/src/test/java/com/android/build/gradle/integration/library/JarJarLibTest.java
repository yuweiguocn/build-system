/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.library;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAar;
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.VariantBuildOutput;
import com.android.ide.common.process.ProcessException;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Test for the jarjar integration.
 */
@Ignore("http://b/37529666")
public class JarJarLibTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("jarjarIntegrationLib").create();

    @After
    public void cleanUp() {
        project = null;
    }

    @Test
    public void checkRepackagedGsonLibrary()
            throws IOException, InterruptedException, ProcessException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                ""
                        + "android {\n"
                        + "    registerTransform(new com.android.test.jarjar.JarJarTransform(false /*broken transform*/))\n"
                        + "}\n");

        ProjectBuildOutput outputModel =
                project.executeAndReturnOutputModel("clean", "assembleDebug");

        VariantBuildOutput debugBuildOutput =
                ProjectBuildOutputUtils.getDebugVariantBuildOutput(outputModel);
        assertEquals(1, debugBuildOutput.getOutputs().size());

        // make sure the Gson library has been renamed and the original one is not present.
        File outputFile =
                Iterators.getOnlyElement(debugBuildOutput.getOutputs().iterator()).getOutputFile();
        assertThatAar(outputFile).containsClass("Lcom/android/tests/basic/Main;");

        // libraries do not include their dependencies unless they are local (which is not
        // the case here), so neither versions of Gson should be present here).
        assertThatAar(outputFile).doesNotContainClass("Lcom/google/repacked/gson/Gson;");
        assertThatAar(outputFile).doesNotContainClass("Lcom/google/gson/Gson;");

        // check we do not have the R class of the library in there.
        assertThatAar(outputFile).doesNotContainClass("Lcom/android/tests/basic/R;");
        assertThatAar(outputFile).doesNotContainClass("Lcom/android/tests/basic/R$drawable;");

        // check the content of the Main class.
        File jarFile =
                project.file(
                        "build/"
                                + AndroidProject.FD_INTERMEDIATES
                                + "/"
                                + "bundles/"
                                + "debug/"
                                + "classes.jar");
        checkClassFile(jarFile);
    }

    @Test
    public void checkBrokenTransform() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                ""
                        + "android {\n"
                        + "    registerTransform(new com.android.test.jarjar.JarJarTransform(true /*broken transform*/))\n"
                        + "}\n");

        AndroidProject model =
                project.model().ignoreSyncIssues().fetchAndroidProjects().getOnlyModel();

        Collection<SyncIssue> issues = model.getSyncIssues();
        assertThat(issues).hasSize(2);

        Collection<SyncIssue> errors =
                issues.stream()
                        .filter(syncIssue -> syncIssue.getSeverity() == SyncIssue.SEVERITY_ERROR)
                        .collect(Collectors.toList());

        assertThat(errors).hasSize(1);
        SyncIssue error = Iterables.getOnlyElement(errors);
        assertThat(error.getType()).isEqualTo(SyncIssue.TYPE_GENERIC);
        assertThat(error.getMessage())
                .isEqualTo(
                        "Transforms with scopes '[SUB_PROJECTS, EXTERNAL_LIBRARIES, PROJECT_LOCAL_DEPS, SUB_PROJECTS_LOCAL_DEPS]' cannot be applied to library projects.");

        Collection<SyncIssue> warnings =
                issues.stream()
                        .filter(syncIssue -> syncIssue.getSeverity() == SyncIssue.SEVERITY_WARNING)
                        .collect(Collectors.toList());
        assertThat(warnings).hasSize(1);
        SyncIssue warning = Iterables.getOnlyElement(warnings);
        assertThat(warning.getType()).isEqualTo(SyncIssue.TYPE_GENERIC);
        assertThat(warning.getMessage())
                .isEqualTo(
                        "Transform 'jarjar' uses scope SUB_PROJECTS_LOCAL_DEPS which is deprecated and replaced with EXTERNAL");

    }

    private static void checkClassFile(@NonNull File jarFile) throws IOException {
        ZipFile zipFile = new ZipFile(jarFile);
        try {
            ZipEntry entry = zipFile.getEntry("com/android/tests/basic/Main.class");
            assertThat(entry).named("Main.class entry").isNotNull();
            ClassReader classReader = new ClassReader(zipFile.getInputStream(entry));
            ClassNode mainTestClassNode = new ClassNode(Opcodes.ASM5);
            classReader.accept(mainTestClassNode, 0);

            // Make sure bytecode got rewritten to point to renamed classes.

            // search for the onCrate method.
            List<MethodNode> methods = mainTestClassNode.methods;
            Optional<MethodNode> onCreateMethod =
                    methods.stream().filter(p -> p.name.equals("onCreate")).findFirst();
            assertThat(onCreateMethod).named("onCreate method").isPresent();

            // find the new instruction, and verify it's using the repackaged Gson.
            TypeInsnNode newInstruction = null;
            int count = onCreateMethod.get().instructions.size();
            for (int i = 0; i < count ; i++) {
                AbstractInsnNode instruction = onCreateMethod.get().instructions.get(i);
                if (instruction.getOpcode() == Opcodes.NEW) {
                    newInstruction = (TypeInsnNode) instruction;
                    break;
                }
            }
            assertThat(newInstruction).named("new instruction").isNotNull();
            assertThat(newInstruction.desc).isEqualTo("com/google/repacked/gson/Gson");

        } finally {
            zipFile.close();
        }
    }
}
