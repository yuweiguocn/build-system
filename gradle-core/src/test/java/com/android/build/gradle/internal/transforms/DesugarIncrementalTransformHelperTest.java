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

package com.android.build.gradle.internal.transforms;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.transforms.testdata.Animal;
import com.android.build.gradle.internal.transforms.testdata.CarbonForm;
import com.android.build.gradle.internal.transforms.testdata.Cat;
import com.android.build.gradle.internal.transforms.testdata.Tiger;
import com.android.build.gradle.internal.transforms.testdata.Toy;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.testutils.TestInputsGenerator;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DesugarIncrementalTransformHelperTest {

    public static final String PROJECT_VARIANT = "app:debug";
    @Rule public TemporaryFolder tmpDir = new TemporaryFolder();

    public DesugarIncrementalTransformHelperTest() {
    }

    @Before
    public void setUp() throws InterruptedException {
        // remove any previous state first
        getDesugarIncrementalTransformHelper(
                        TransformTestHelper.invocationBuilder().setIncremental(false).build())
                .getAdditionalPaths();

    }

    @Test
    public void testBasicFull() throws IOException, InterruptedException {
        Path input = tmpDir.getRoot().toPath().resolve("input");
        initializeGraph(input);
    }

    @Test
    public void testIncremental_baseInterfaceChange() throws IOException, InterruptedException {
        Path input = tmpDir.getRoot().toPath().resolve("input");
        initializeGraph(input);

        TransformInput inputDir =
                TransformTestHelper.directoryBuilder(input.toFile())
                        .putChangedFiles(getChangedStatusMap(input, CarbonForm.class))
                        .build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(inputDir)
                        .setIncremental(true)
                        .build();
        Set<Path> impactedPaths =
                getDesugarIncrementalTransformHelper(invocation).getAdditionalPaths();

        assertThat(impactedPaths)
                .containsExactlyElementsIn(getPaths(input, Animal.class, Cat.class, Tiger.class));
    }

    @Test
    public void testIncremental_intermediateInterfaceChange()
            throws IOException, InterruptedException {
        Path input = tmpDir.getRoot().toPath().resolve("input");
        initializeGraph(input);

        TransformInput inputDir =
                TransformTestHelper.directoryBuilder(input.toFile())
                        .putChangedFiles(getChangedStatusMap(input, Animal.class))
                        .build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(inputDir)
                        .setIncremental(true)
                        .build();
        Set<Path> impactedPaths =
                getDesugarIncrementalTransformHelper(invocation).getAdditionalPaths();

        assertThat(impactedPaths)
                .containsExactlyElementsIn(getPaths(input, Cat.class, Tiger.class));
    }

    @Test
    public void testIncremental_functionalInterfaceChange()
            throws IOException, InterruptedException {
        Path input = tmpDir.getRoot().toPath().resolve("input");
        initializeGraph(input);

        TransformInput inputDir =
                TransformTestHelper.directoryBuilder(input.toFile())
                        .putChangedFiles(getChangedStatusMap(input, Toy.class))
                        .build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(inputDir)
                        .setIncremental(true)
                        .build();
        Set<Path> impactedPaths =
                getDesugarIncrementalTransformHelper(invocation).getAdditionalPaths();

        assertThat(impactedPaths)
                .containsExactlyElementsIn(getPaths(input, Cat.class, Tiger.class));
    }

    @Test
    public void testIncremental_superClassChange() throws IOException, InterruptedException {
        Path input = tmpDir.getRoot().toPath().resolve("input");
        initializeGraph(input);

        TransformInput inputDir =
                TransformTestHelper.directoryBuilder(input.toFile())
                        .putChangedFiles(getChangedStatusMap(input, Cat.class))
                        .build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(inputDir)
                        .setIncremental(true)
                        .build();
        Set<Path> impactedPaths =
                getDesugarIncrementalTransformHelper(invocation).getAdditionalPaths();

        assertThat(impactedPaths).containsExactlyElementsIn(getPaths(input, Tiger.class));
    }

    @Test
    public void testIncremental_classChange() throws IOException, InterruptedException {
        Path input = tmpDir.getRoot().toPath().resolve("input");
        initializeGraph(input);

        TransformInput inputDir =
                TransformTestHelper.directoryBuilder(input.toFile())
                        .putChangedFiles(getChangedStatusMap(input, Tiger.class))
                        .build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(inputDir)
                        .setIncremental(true)
                        .build();
        Set<Path> impactedPaths =
                getDesugarIncrementalTransformHelper(invocation).getAdditionalPaths();

        assertThat(impactedPaths).isEmpty();
    }

    @Test
    public void testIncremental_multipleChange() throws IOException, InterruptedException {
        Path input = tmpDir.getRoot().toPath().resolve("input");
        initializeGraph(input);

        TransformInput inputDir =
                TransformTestHelper.directoryBuilder(input.toFile())
                        .putChangedFiles(getChangedStatusMap(input, CarbonForm.class, Toy.class))
                        .build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(inputDir)
                        .setIncremental(true)
                        .build();
        Set<Path> impactedPaths =
                getDesugarIncrementalTransformHelper(invocation).getAdditionalPaths();

        assertThat(impactedPaths)
                .containsExactlyElementsIn(getPaths(input, Cat.class, Animal.class, Tiger.class));
    }

    @Test
    public void testIncremental_noneChange() throws IOException, InterruptedException {
        Path input = tmpDir.getRoot().toPath().resolve("input");
        initializeGraph(input);

        TransformInput inputDir = TransformTestHelper.directoryBuilder(input.toFile()).build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(inputDir)
                        .setIncremental(true)
                        .build();
        Set<Path> impactedPaths =
                getDesugarIncrementalTransformHelper(invocation).getAdditionalPaths();

        assertThat(impactedPaths).isEmpty();
    }

    @Test
    public void testDirAndJarInput_incremental() throws IOException, InterruptedException {
        Path inputDir = tmpDir.getRoot().toPath().resolve("input_dir");
        Path inputJar = tmpDir.getRoot().toPath().resolve("input.jar");

        TestInputsGenerator.pathWithClasses(
                inputDir, ImmutableList.of(Cat.class, Toy.class, Tiger.class));
        TestInputsGenerator.pathWithClasses(
                inputJar, ImmutableList.of(Animal.class, CarbonForm.class));

        TransformInput transformDir =
                TransformTestHelper.directoryBuilder(inputDir.toFile()).build();
        TransformInput transformJar =
                TransformTestHelper.singleJarBuilder(inputJar.toFile()).build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(transformDir)
                        .addInput(transformJar)
                        .setIncremental(true)
                        .build();
        assertThat(getDesugarIncrementalTransformHelper(invocation).getAdditionalPaths()).isEmpty();

        transformJar =
                TransformTestHelper.singleJarBuilder(inputJar.toFile())
                        .setStatus(Status.CHANGED)
                        .build();

        invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(transformDir)
                        .addInput(transformJar)
                        .setIncremental(true)
                        .build();
        assertThat(getDesugarIncrementalTransformHelper(invocation).getAdditionalPaths())
                .containsExactlyElementsIn(getPaths(inputDir, Cat.class, Tiger.class));

        transformDir =
                TransformTestHelper.directoryBuilder(inputDir.toFile())
                        .putChangedFiles(getChangedStatusMap(inputDir, Toy.class))
                        .build();

        invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(transformDir)
                        .addInput(transformJar)
                        .setIncremental(true)
                        .build();

        assertThat(getDesugarIncrementalTransformHelper(invocation).getAdditionalPaths())
                .containsExactlyElementsIn(getPaths(inputDir, Cat.class, Tiger.class));
    }

    @Test
    public void testDirAndJarInput_incremental_jarDependsOnDir()
            throws IOException, InterruptedException {
        Path inputDir = tmpDir.getRoot().toPath().resolve("input_dir");
        Path inputJar = tmpDir.getRoot().toPath().resolve("input.jar");

        TestInputsGenerator.pathWithClasses(
                inputDir, ImmutableList.of(Animal.class, CarbonForm.class));
        TestInputsGenerator.pathWithClasses(
                inputJar, ImmutableList.of(Cat.class, Toy.class, Tiger.class));

        TransformInput transformDir =
                TransformTestHelper.directoryBuilder(inputDir.toFile()).build();
        TransformInput transformJar =
                TransformTestHelper.singleJarBuilder(inputJar.toFile()).build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(transformDir)
                        .addInput(transformJar)
                        .setIncremental(true)
                        .build();
        assertThat(getDesugarIncrementalTransformHelper(invocation).getAdditionalPaths()).isEmpty();

        transformJar =
                TransformTestHelper.directoryBuilder(inputJar.toFile())
                        .putChangedFiles(getChangedStatusMap(inputDir, Animal.class))
                        .build();

        invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(transformDir)
                        .addInput(transformJar)
                        .setIncremental(true)
                        .build();
        assertThat(getDesugarIncrementalTransformHelper(invocation).getAdditionalPaths())
                .containsExactly(inputJar);
    }

    @Test
    public void testTwoJars_incremental() throws IOException, InterruptedException {
        Path fstJar = tmpDir.getRoot().toPath().resolve("input1.jar");
        Path sndJar = tmpDir.getRoot().toPath().resolve("input2.jar");

        TestInputsGenerator.pathWithClasses(
                fstJar, ImmutableList.of(Cat.class, Toy.class, Tiger.class));
        TestInputsGenerator.pathWithClasses(
                sndJar, ImmutableList.of(Animal.class, CarbonForm.class));

        TransformInput transformFst = TransformTestHelper.singleJarBuilder(fstJar.toFile()).build();
        TransformInput transformSnd = TransformTestHelper.singleJarBuilder(sndJar.toFile()).build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(transformFst)
                        .addInput(transformSnd)
                        .setIncremental(true)
                        .build();
        assertThat(getDesugarIncrementalTransformHelper(invocation).getAdditionalPaths()).isEmpty();

        transformSnd =
                TransformTestHelper.singleJarBuilder(sndJar.toFile())
                        .setStatus(Status.CHANGED)
                        .build();

        invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(transformFst)
                        .addInput(transformSnd)
                        .setIncremental(true)
                        .build();
        assertThat(getDesugarIncrementalTransformHelper(invocation).getAdditionalPaths())
                .containsExactly(fstJar);
    }

    @Test
    public void test_typeInMultiplePaths() throws IOException, InterruptedException {
        Path fstJar = tmpDir.getRoot().toPath().resolve("input1.jar");
        Path sndJar = tmpDir.getRoot().toPath().resolve("input2.jar");
        Path trdJar = tmpDir.getRoot().toPath().resolve("input3.jar");

        TestInputsGenerator.pathWithClasses(fstJar, ImmutableList.of(Animal.class));
        TestInputsGenerator.pathWithClasses(sndJar, ImmutableList.of(Cat.class));
        TestInputsGenerator.pathWithClasses(trdJar, ImmutableList.of(Cat.class));

        TransformInput transformFst = TransformTestHelper.singleJarBuilder(fstJar.toFile()).build();
        TransformInput transformSnd = TransformTestHelper.singleJarBuilder(sndJar.toFile()).build();
        TransformInput transformTrd = TransformTestHelper.singleJarBuilder(trdJar.toFile()).build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(transformFst)
                        .addInput(transformSnd)
                        .addInput(transformTrd)
                        .setIncremental(true)
                        .build();
        assertThat(getDesugarIncrementalTransformHelper(invocation).getAdditionalPaths()).isEmpty();

        transformFst =
                TransformTestHelper.singleJarBuilder(fstJar.toFile())
                        .setStatus(Status.CHANGED)
                        .build();

        invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(transformFst)
                        .addInput(transformSnd)
                        .addInput(transformTrd)
                        .setIncremental(true)
                        .build();
        assertThat(getDesugarIncrementalTransformHelper(invocation).getAdditionalPaths())
                .containsExactly(sndJar, trdJar);
    }

    @Test
    public void test_incrementalDeletedNonInitialized() throws IOException, InterruptedException {
        Path fstJar = tmpDir.getRoot().toPath().resolve("input1.jar");

        TransformInput transformFst =
                TransformTestHelper.singleJarBuilder(fstJar.toFile())
                        .setStatus(Status.REMOVED)
                        .build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(transformFst)
                        .setIncremental(true)
                        .build();
        assertThat(getDesugarIncrementalTransformHelper(invocation).getAdditionalPaths()).isEmpty();
    }

    private static void initializeGraph(@NonNull Path input)
            throws IOException, InterruptedException {
        TestInputsGenerator.pathWithClasses(
                input,
                ImmutableList.of(
                        Animal.class, CarbonForm.class, Cat.class, Toy.class, Tiger.class));

        TransformInput inputDir = TransformTestHelper.directoryBuilder(input.toFile()).build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(inputDir)
                        .setIncremental(true)
                        .build();
        Set<Path> impactedPaths =
                getDesugarIncrementalTransformHelper(invocation).getAdditionalPaths();

        assertThat(impactedPaths).isEmpty();
    }

    @NonNull
    private static DesugarIncrementalTransformHelper getDesugarIncrementalTransformHelper(
            TransformInvocation invocation) {
        return new DesugarIncrementalTransformHelper(
                PROJECT_VARIANT, invocation, WaitableExecutor.useDirectExecutor());
    }

    @NonNull
    private Map<File, Status> getChangedStatusMap(
            @NonNull Path root, @NonNull Class<?>... classes) {
        Map<File, Status> statusMap = new HashMap<>();
        for (Path classPath : getPaths(root, classes)) {
            statusMap.put(classPath.toFile(), Status.CHANGED);
        }
        return statusMap;
    }

    @NonNull
    private static Collection<Path> getPaths(@NonNull Path root, @NonNull Class<?>... classes) {
        List<Path> path = new ArrayList<>(classes.length);
        for (Class<?> klass : classes) {
            path.add(root.resolve(TestInputsGenerator.getPath(klass)));
        }
        return path;
    }
}
