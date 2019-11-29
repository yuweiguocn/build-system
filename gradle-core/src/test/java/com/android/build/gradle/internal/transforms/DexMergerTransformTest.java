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

import static com.android.build.gradle.internal.transforms.DexMergerTransform.ANDROID_L_MAX_DEX_FILES;
import static com.android.build.gradle.internal.transforms.DexMergerTransform.EXTERNAL_DEPS_DEX_FILES;
import static com.android.testutils.truth.MoreTruth.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.mockito.Mockito.when;

import android.databinding.tool.util.Preconditions;
import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.tasks.DexMergingTaskTestKt;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexingType;
import com.android.testutils.TestUtils;
import com.android.testutils.apk.Dex;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;

/** Tests for the {@link DexMergerTransform}. */
public class DexMergerTransformTest {

    private static final String PKG = "com/example/tools";
    private static final int NUM_INPUTS = 10;

    @Rule public TemporaryFolder tmpDir = new TemporaryFolder();

    @Mock private BuildableArtifact dummyArtifact;

    private TransformOutputProvider outputProvider;
    private Path out;

    @Before
    public void setUp() throws IOException {
        out = tmpDir.getRoot().toPath().resolve("out");
        Files.createDirectories(out);
        outputProvider = new TestTransformOutputProvider(out);
        dummyArtifact = Mockito.mock(BuildableArtifact.class);
    }

    @Test
    public void testBasic() throws Exception {
        Path dexArchive = tmpDir.getRoot().toPath().resolve("archive.jar");
        generateArchive(ImmutableList.of(PKG + "/A", PKG + "/B", PKG + "/C"), dexArchive);

        TransformInput input =
                TransformTestHelper.singleJarBuilder(dexArchive.toFile())
                        .setStatus(Status.ADDED)
                        .setContentTypes(ExtendedContentType.DEX_ARCHIVE)
                        .setScopes(QualifiedContent.Scope.PROJECT)
                        .build();
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setInputs(input)
                        .setTransformOutputProvider(outputProvider)
                        .build();

        getTransform(DexingType.MONO_DEX).transform(invocation);

        Dex mainDex = new Dex(out.resolve("main/classes.dex"));
        assertThat(mainDex)
                .containsExactlyClassesIn(
                        ImmutableList.of("L" + PKG + "/A;", "L" + PKG + "/B;", "L" + PKG + "/C;"));
        Truth.assertThat(FileUtils.find(out.toFile(), Pattern.compile(".*\\.dex"))).hasSize(1);
    }

    @Test
    public void test_legacyAndMono_alwaysFullMerge() throws Exception {
        List<String> expectedClasses = Lists.newArrayList();
        for (int i = 0; i < NUM_INPUTS; i++) {
            expectedClasses.add("L" + PKG + "/A" + i + ";");
        }

        Set<TransformInput> inputs =
                getTransformInputs(NUM_INPUTS, QualifiedContent.Scope.EXTERNAL_LIBRARIES);

        getTransform(DexingType.MONO_DEX)
                .transform(
                        TransformTestHelper.invocationBuilder()
                                .setTransformOutputProvider(outputProvider)
                                .setInputs(inputs)
                                .build());

        Dex mainDex = new Dex(out.resolve("main/classes.dex"));
        assertThat(mainDex).containsExactlyClassesIn(expectedClasses);
        Truth.assertThat(FileUtils.find(out.toFile(), Pattern.compile(".*\\.dex"))).hasSize(1);
    }

    @Test
    public void test_native_externalLibsMerged() throws Exception {
        List<String> expectedClasses = Lists.newArrayList();
        for (int i = 0; i < NUM_INPUTS; i++) {
            expectedClasses.add("L" + PKG + "/A" + i + ";");
        }

        Set<TransformInput> inputs =
                getTransformInputs(NUM_INPUTS, QualifiedContent.Scope.EXTERNAL_LIBRARIES);

        getTransform(DexingType.NATIVE_MULTIDEX)
                .transform(
                        TransformTestHelper.invocationBuilder()
                                .setInputs(inputs)
                                .setTransformOutputProvider(outputProvider)
                                .build());

        Dex mainDex = new Dex(out.resolve("externalLibs/classes.dex"));
        assertThat(mainDex).containsExactlyClassesIn(expectedClasses);
        Truth.assertThat(FileUtils.find(out.toFile(), Pattern.compile(".*\\.dex"))).hasSize(1);
    }

    @Test
    public void test_native_deletedExternalLib() throws Exception {
        Set<TransformInput> inputs =
                getTransformInputs(
                        NUM_INPUTS,
                        QualifiedContent.Scope.EXTERNAL_LIBRARIES,
                        "Prefix",
                        Status.REMOVED);

        getTransform(DexingType.NATIVE_MULTIDEX)
                .transform(
                        TransformTestHelper.invocationBuilder()
                                .setInputs(inputs)
                                .setTransformOutputProvider(outputProvider)
                                .setIncremental(true)
                                .build());

        // make sure we do not create classes.dex
        assertThat(out.resolve("externalLibs/classes.dex")).doesNotExist();
    }

    @Test
    public void test_native_deletedExternalLib_mergedTogether() throws Exception {

        // create fake 100 directory inputs to force the merging together of the
        // non external libraries.
        Set<TransformInput> inputs = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            inputs.add(
                    TransformTestHelper.directoryBuilder(tmpDir.newFolder())
                            .setScope(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                            .build());
        }

        inputs.addAll(
                getTransformInputs(
                        NUM_INPUTS,
                        QualifiedContent.Scope.EXTERNAL_LIBRARIES,
                        "Prefix",
                        Status.REMOVED));

        outputProvider =
                new TestTransformOutputProvider(out) {
                    @NonNull
                    @Override
                    public File getContentLocation(
                            @NonNull String name,
                            @NonNull Set<QualifiedContent.ContentType> types,
                            @NonNull Set<? super QualifiedContent.Scope> scopes,
                            @NonNull Format format) {
                        // simulates implementation checks.
                        com.google.common.base.Preconditions.checkState(!scopes.isEmpty());
                        com.google.common.base.Preconditions.checkState(!types.isEmpty());
                        return super.getContentLocation(name, types, scopes, format);
                    }
                };

        getTransform(DexingType.NATIVE_MULTIDEX)
                .transform(
                        TransformTestHelper.invocationBuilder()
                                .setInputs(inputs)
                                .setTransformOutputProvider(outputProvider)
                                .setIncremental(true)
                                .build());

        // make sure we do not merge non existing libraries
        assertThat(out.resolve("nonExternalJars").toFile()).doesNotExist();
    }

    @Test
    public void test_native_nonExternalHaveDexEach() throws Exception {
        Set<TransformInput> inputs = getTransformInputs(NUM_INPUTS, QualifiedContent.Scope.PROJECT);

        getTransform(DexingType.NATIVE_MULTIDEX)
                .transform(
                        TransformTestHelper.invocationBuilder()
                                .setInputs(inputs)
                                .setTransformOutputProvider(outputProvider)
                                .build());

        Truth.assertThat(FileUtils.find(out.toFile(), Pattern.compile(".*\\.dex")))
                .hasSize(inputs.size());
    }

    @Test
    public void test_native_changedInput() throws Exception {
        Set<TransformInput> inputs =
                getTransformInputs(NUM_INPUTS, QualifiedContent.Scope.EXTERNAL_LIBRARIES);
        Set<TransformInput> projectInputs =
                getTransformInputs(1, QualifiedContent.Scope.PROJECT, "B");
        inputs.addAll(projectInputs);

        getTransform(DexingType.NATIVE_MULTIDEX)
                .transform(
                        TransformTestHelper.invocationBuilder()
                                .setInputs(inputs)
                                .setTransformOutputProvider(outputProvider)
                                .setIncremental(true)
                                .build());

        File externalMerged = out.resolve("externalLibs/classes.dex").toFile();
        long lastModified =
                FileUtils.getAllFiles(out.toFile())
                        .filter(f -> !Objects.equals(f, externalMerged))
                        .last()
                        .get()
                        .lastModified();
        TestUtils.waitForFileSystemTick();

        Path libArchive = tmpDir.getRoot().toPath().resolve("added.jar");
        generateArchive(ImmutableList.of(PKG + "/C"), libArchive);

        TransformInput updatedInput =
                TransformTestHelper.singleJarBuilder(libArchive.toFile())
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setStatus(Status.ADDED)
                        .build();

        getTransform(DexingType.NATIVE_MULTIDEX)
                .transform(
                        TransformTestHelper.invocationBuilder()
                                .addInput(updatedInput)
                                .setTransformOutputProvider(outputProvider)
                                .setIncremental(true)
                                .build());

        Truth.assertThat(externalMerged.lastModified()).isGreaterThan(lastModified);
        FileUtils.getAllFiles(out.toFile())
                .filter(f -> !Objects.equals(f, externalMerged))
                .forEach(f -> Truth.assertThat(f.lastModified()).isEqualTo(lastModified));
    }

    @Test(timeout = 20_000)
    public void test_native_doesNotDeadlock() throws Exception {
        int inputCnt = 3 * Runtime.getRuntime().availableProcessors();
        Set<TransformInput> inputs =
                getTransformInputs(inputCnt, QualifiedContent.Scope.SUB_PROJECTS);
        getTransform(DexingType.NATIVE_MULTIDEX)
                .transform(
                        TransformTestHelper.invocationBuilder()
                                .setInputs(inputs)
                                .setTransformOutputProvider(outputProvider)
                                .build());
    }

    @Test
    public void test_native_inputsMergedForWhenNeeded() throws Exception {
        TransformTestHelper.InvocationBuilder invocationBuilder =
                TransformTestHelper.invocationBuilder();
        int NUM_INPUTS = (ANDROID_L_MAX_DEX_FILES - EXTERNAL_DEPS_DEX_FILES) / 2 + 1;
        for (int i = 0; i < NUM_INPUTS; i++) {
            Path dirArchive = tmpDir.getRoot().toPath().resolve("dir_input_" + i);
            generateArchive(ImmutableList.of(PKG + "/C" + i), dirArchive);
            invocationBuilder.addInput(
                    TransformTestHelper.directoryBuilder(dirArchive.toFile()).build());

            Path nonExternalJar = tmpDir.getRoot().toPath().resolve("non_external_" + i + ".jar");
            generateArchive(ImmutableList.of(PKG + "/A" + i), nonExternalJar);
            invocationBuilder.addInput(
                    TransformTestHelper.singleJarBuilder(nonExternalJar.toFile())
                            .setScopes(QualifiedContent.Scope.SUB_PROJECTS)
                            .build());
        }

        DexMergerTransform androidLDexMerger =
                new DexMergerTransform(
                        DexingType.NATIVE_MULTIDEX,
                        null,
                        dummyArtifact,
                        new NoOpMessageReceiver(),
                        DexMergerTool.DX,
                        21,
                        true,
                        false,
                        false);
        androidLDexMerger.transform(
                invocationBuilder.setTransformOutputProvider(outputProvider).build());
        Truth.assertThat(
                        Files.walk(out)
                                .filter(p -> p.toString().endsWith(SdkConstants.DOT_DEX))
                                .collect(Collectors.toList()))
                .hasSize(2);

        DexMergerTransform postLDexMerger =
                new DexMergerTransform(
                        DexingType.NATIVE_MULTIDEX,
                        null,
                        dummyArtifact,
                        new NoOpMessageReceiver(),
                        DexMergerTool.DX,
                        23,
                        true,
                        false,
                        false);
        postLDexMerger.transform(
                invocationBuilder.setTransformOutputProvider(outputProvider).build());
        Truth.assertThat(
                        Files.walk(out)
                                .filter(p -> p.toString().endsWith(SdkConstants.DOT_DEX))
                                .collect(Collectors.toList()))
                .hasSize(2 * NUM_INPUTS);
    }

    @Test
    public void test_native_allMergedForRelease() throws Exception {
        Set<TransformInput> inputs = getTransformInputs(NUM_INPUTS, QualifiedContent.Scope.PROJECT);

        getTransform(DexingType.NATIVE_MULTIDEX, null, false)
                .transform(
                        TransformTestHelper.invocationBuilder()
                                .setInputs(inputs)
                                .setTransformOutputProvider(outputProvider)
                                .build());

        Truth.assertThat(FileUtils.find(out.toFile(), Pattern.compile(".*\\.dex"))).hasSize(1);
    }

    @Test
    public void test_legacyInDebug() throws Exception {
        List<String> secondaryClasses = Lists.newArrayList();
        for (int i = 1; i < NUM_INPUTS; i++) {
            secondaryClasses.add("L" + PKG + "/A" + i + ";");
        }

        Set<TransformInput> inputs =
                getTransformInputs(NUM_INPUTS, QualifiedContent.Scope.EXTERNAL_LIBRARIES);

        getTransform(DexingType.LEGACY_MULTIDEX, ImmutableSet.of(PKG + "/A0.class"), true)
                .transform(
                        TransformTestHelper.invocationBuilder()
                                .setTransformOutputProvider(outputProvider)
                                .setInputs(inputs)
                                .build());

        assertThat(new Dex(out.resolve("main/classes.dex")))
                .containsExactlyClassesIn(ImmutableList.of("L" + PKG + "/A0;"));
        assertThat(new Dex(out.resolve("main/classes2.dex")))
                .containsExactlyClassesIn(secondaryClasses);
    }

    @Test
    public void test_legacyInRelease() throws Exception {
        List<String> expectedClasses = Lists.newArrayList();
        for (int i = 0; i < NUM_INPUTS; i++) {
            expectedClasses.add("L" + PKG + "/A" + i + ";");
        }

        Set<TransformInput> inputs =
                getTransformInputs(NUM_INPUTS, QualifiedContent.Scope.EXTERNAL_LIBRARIES);

        getTransform(DexingType.LEGACY_MULTIDEX, ImmutableSet.of(PKG + "/A0.class"), false)
                .transform(
                        TransformTestHelper.invocationBuilder()
                                .setTransformOutputProvider(outputProvider)
                                .setInputs(inputs)
                                .build());

        assertThat(new Dex(out.resolve("main/classes.dex")))
                .containsExactlyClassesIn(expectedClasses);
    }

    @Test
    public void testStreamNameChange() throws Exception {
        // make output provider that outputs based on name
        outputProvider =
                new TestTransformOutputProvider(out) {
                    @NonNull
                    @Override
                    public File getContentLocation(
                            @NonNull String name,
                            @NonNull Set<QualifiedContent.ContentType> types,
                            @NonNull Set<? super QualifiedContent.Scope> scopes,
                            @NonNull Format format) {
                        return out.resolve(Long.toString(name.hashCode())).toFile();
                    }
                };
        Path dexArchive = tmpDir.getRoot().toPath().resolve("dexArchive_input");
        generateArchive(ImmutableList.of(PKG + "/C"), dexArchive);

        TransformInput dirInput =
                TransformTestHelper.directoryBuilder(dexArchive.toFile())
                        .setName("first-run")
                        .setScope(QualifiedContent.Scope.PROJECT)
                        .build();
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(dirInput)
                        .setTransformOutputProvider(outputProvider)
                        .build();
        getTransform(DexingType.NATIVE_MULTIDEX).transform(invocation);

        dirInput =
                TransformTestHelper.directoryBuilder(dexArchive.toFile())
                        .setName("second-run")
                        .setScope(QualifiedContent.Scope.PROJECT)
                        .putChangedFiles(
                                ImmutableMap.of(
                                        dexArchive.resolve(PKG + "C.dex").toFile(), Status.REMOVED))
                        .build();
        invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(dirInput)
                        .setTransformOutputProvider(outputProvider)
                        .setIncremental(true)
                        .build();
        getTransform(DexingType.NATIVE_MULTIDEX).transform(invocation);

        List<File> dexFiles = FileUtils.find(out.toFile(), Pattern.compile(".*\\.dex$"));
        Truth.assertThat(dexFiles).hasSize(1);
    }

    private DexMergerTransform getTransform(@NonNull DexingType dexingType) throws IOException {
        Preconditions.check(
                dexingType != DexingType.LEGACY_MULTIDEX,
                "Main dex list required for legacy multidex");
        return getTransform(dexingType, null, true);
    }

    private DexMergerTransform getTransform(
            @NonNull DexingType dexingType,
            @Nullable ImmutableSet<String> mainDex,
            boolean isDebuggable)
            throws IOException {
        BuildableArtifact collection;
        if (mainDex != null) {
            Preconditions.check(
                    dexingType == DexingType.LEGACY_MULTIDEX,
                    "Main dex list must only be used for legacy multidex");
            File tmpFile = tmpDir.newFile();
            Files.write(tmpFile.toPath(), mainDex, StandardOpenOption.TRUNCATE_EXISTING);
            collection = Mockito.mock(BuildableArtifact.class);
            when(collection.iterator()).thenReturn(Iterators.singletonIterator(tmpFile));
        } else {
            collection = null;
        }
        return new DexMergerTransform(
                dexingType,
                collection,
                dummyArtifact,
                new NoOpMessageReceiver(),
                DexMergerTool.DX,
                1,
                isDebuggable,
                false,
                false);
    }

    @NonNull
    private Set<TransformInput> getTransformInputs(int cnt, @NonNull QualifiedContent.Scope scope)
            throws Exception {
        return getTransformInputs(cnt, scope, "A");
    }

    @NonNull
    private Set<TransformInput> getTransformInputs(
            int cnt, @NonNull QualifiedContent.Scope scope, @NonNull String classPrefix)
            throws Exception {
        return getTransformInputs(cnt, scope, classPrefix, Status.ADDED);
    }

    @NonNull
    private Set<TransformInput> getTransformInputs(
            int cnt,
            @NonNull QualifiedContent.Scope scope,
            @NonNull String classPrefix,
            @NonNull Status status)
            throws Exception {
        List<Path> archives = Lists.newArrayList();
        for (int i = 0; i < cnt; i++) {
            archives.add(tmpDir.newFolder().toPath().resolve("archive" + i + ".jar"));
            generateArchive(
                    ImmutableList.of(PKG + "/" + classPrefix + i), Iterables.getLast(archives));
        }

        Set<TransformInput> inputs = new HashSet<>(archives.size());
        for (Path dexArchive : archives) {
            inputs.add(
                    TransformTestHelper.singleJarBuilder(dexArchive.toFile())
                            .setScopes(scope)
                            .setStatus(status)
                            .build());
        }
        return inputs;
    }

    private void generateArchive(
            @NonNull Collection<String> classes, @NonNull Path dexArchivePath) {
        DexMergingTaskTestKt.generateArchive(tmpDir, dexArchivePath, classes);
    }
}
