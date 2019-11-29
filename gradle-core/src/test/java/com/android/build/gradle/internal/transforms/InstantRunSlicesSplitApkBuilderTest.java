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

package com.android.build.gradle.internal.transforms;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.build.VariantOutput;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.internal.aapt.AaptOptions;
import com.android.builder.sdk.TargetInfo;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests for the {@link InstantRunSliceSplitApkBuilder}
 */
public class InstantRunSlicesSplitApkBuilderTest {

    @Mock Logger logger;
    @Mock Project project;
    @Mock InstantRunBuildContext buildContext;
    @Mock AndroidBuilder androidBuilder;
    @Mock FileCollection coreSigningConfig;
    @Mock BuildableArtifact mainResources;
    @Mock BuildableArtifact splitApkResources;

    @Mock TargetInfo targetInfo;
    @Mock BuildToolInfo buildTools;
    @Mock BuildableArtifact apkList;

    @Rule public TemporaryFolder outputDirectory = new TemporaryFolder();
    @Rule public TemporaryFolder supportDirectory = new TemporaryFolder();
    @Rule public TemporaryFolder dexFileFolder = new TemporaryFolder();
    @Rule public TemporaryFolder apkListDirectory = new TemporaryFolder();
    @Rule public TemporaryFolder apkResources = new TemporaryFolder();

    InstantRunSliceSplitApkBuilder instantRunSliceSplitApkBuilder;
    final List<InstantRunSplitApkBuilder.DexFiles> dexFilesList =
            new CopyOnWriteArrayList<>();

    final ApkData apkInfo = ApkData.of(VariantOutput.OutputType.MAIN, ImmutableList.of(), 12345);

    @Before
    public void setUpMock() throws IOException {
        MockitoAnnotations.initMocks(this);
        when(androidBuilder.getTargetInfo()).thenReturn(targetInfo);
        when(targetInfo.getBuildTools()).thenReturn(buildTools);
        when(buildTools.getPath(BuildToolInfo.PathId.ZIP_ALIGN)).thenReturn("/path/to/zip-align");
        when(splitApkResources.getFiles()).thenReturn(ImmutableSet.of(apkResources.getRoot()));

    }

    @Before
    public void setup() throws IOException {

        File apkListFile = apkListDirectory.newFile("apk-list.json");
        org.apache.commons.io.FileUtils.write(
                apkListFile, ExistingBuildElements.persistApkList(ImmutableList.of(apkInfo)));
        when(apkList.iterator()).thenReturn(ImmutableList.of(apkListFile).iterator());

        FileUtils.createFile(new File(apkResources.getRoot(), "dex_one/resources_ap"), "resources");
        FileUtils.createFile(new File(apkResources.getRoot(), "dex_two/resources_ap"), "resources");
        FileUtils.createFile(
                new File(apkResources.getRoot(), "dex_three/resources_ap"), "resources");


        instantRunSliceSplitApkBuilder =
                new InstantRunSliceSplitApkBuilder(
                        logger,
                        project,
                        buildContext,
                        androidBuilder,
                        null,
                        () -> "com.foo.test",
                        coreSigningConfig,
                        new AaptOptions(null, false, null),
                        outputDirectory.getRoot(),
                        supportDirectory.newFolder("instant-run"),
                        mainResources,
                        mainResources,
                        apkList,
                        splitApkResources,
                        apkInfo) {

                    @Override
                    void generateSplitApk(
                            String uniqueName,
                            File resPackageFile,
                            DexFiles split,
                            File outputFile) {
                        dexFilesList.add(split);
                    }
                };
    }

    @Test
    public void testTransformInterface() {
        assertThat(instantRunSliceSplitApkBuilder.getScopes()).containsExactly(
                QualifiedContent.Scope.PROJECT, QualifiedContent.Scope.SUB_PROJECTS);
        assertThat(instantRunSliceSplitApkBuilder.getInputTypes()).containsExactly(
                ExtendedContentType.DEX);
        assertThat(instantRunSliceSplitApkBuilder.isIncremental()).isTrue();
    }

    @Test
    public void testNonIncrementalBuild()
            throws TransformException, InterruptedException, IOException {

        TransformOutputProvider transformOutputProvider = new TransformOutputProviderForTests();
        File dexFolderOne = Mockito.mock(File.class);
        File[] dexFolderOneFiles = { new File("dexFile1-1.dex") };
        when(dexFolderOne.listFiles()).thenReturn(dexFolderOneFiles);

        File dexFolderTwo = Mockito.mock(File.class);
        File[] dexFolderTwoFiles = { new File("dexFile2-1.dex") };
        when(dexFolderTwo.listFiles()).thenReturn(dexFolderTwoFiles);

        File dexFolderThree = Mockito.mock(File.class);
        File[] dexFolderThreeFiles = { new File("dexFile3-1.dex"), new File("dexFile3-2.dex") };
        when(dexFolderThree.listFiles()).thenReturn(dexFolderThreeFiles);

        TransformInput dexOne =
                TransformTestHelper.directoryBuilder(dexFolderOne)
                        .setScope(QualifiedContent.Scope.PROJECT)
                        .setName("dex-one")
                        .build();
        TransformInput dexTwo =
                TransformTestHelper.directoryBuilder(dexFolderTwo)
                        .setScope(QualifiedContent.Scope.SUB_PROJECTS)
                        .setName("dex-two")
                        .build();
        TransformInput dexThree =
                TransformTestHelper.directoryBuilder(dexFolderThree)
                        .setScope(QualifiedContent.Scope.PROJECT)
                        .setName("dex-three")
                        .build();
        TransformInvocation transformInvocation =
                TransformTestHelper.invocationBuilder()
                        .setTransformOutputProvider(transformOutputProvider)
                        .setInputs(dexOne, dexTwo, dexThree)
                        .build();

        instantRunSliceSplitApkBuilder.transform(transformInvocation);
        assertThat(dexFilesList).hasSize(3);
        for (InstantRunSplitApkBuilder.DexFiles dexFiles : dexFilesList) {
            switch(dexFiles.encodeName()) {
                case "dex_one" :
                    assertThat(dexFiles.getDexFiles()).hasSize(1);
                    break;
                case "dex_two" :
                    assertThat(dexFiles.getDexFiles()).hasSize(1);
                    break;
                case "dex_three" :
                    assertThat(dexFiles.getDexFiles()).hasSize(2);
                    break;
                default:
                    fail("Unexpected split apk generation request : " + dexFiles.encodeName());
            }
        }
    }

    @Test
    public void testIncrementalBuild()
            throws TransformException, InterruptedException, IOException {

        TransformOutputProvider transformOutputProvider = new TransformOutputProviderForTests();
        File dexFolderOne = dexFileFolder.newFolder();
        FileUtils.createFile(new File(dexFolderOne, "dexFile1-1.dex"), "some dex");
        FileUtils.createFile(new File(dexFolderOne, "dexFile1-2.dex"), "some dex");

        File dexFolderTwo = dexFileFolder.newFolder();
        FileUtils.createFile(new File(dexFolderTwo, "dexFile2-1.dex"), "some dex");

        File dexFolderThree = dexFileFolder.newFolder();
        FileUtils.createFile(new File(dexFolderThree, "dexFile3-1.dex"), "some dex");
        FileUtils.createFile(new File(dexFolderThree, "dexFile3-2.dex"), "some dex");

        TransformInput dexOne =
                TransformTestHelper.directoryBuilder(dexFolderOne)
                        .setName("dex_one")
                        .setScope(QualifiedContent.Scope.PROJECT)
                        .putChangedFiles(
                                ImmutableMap.of(
                                        new File(dexFolderOne, "dexFile1-1.dex"), Status.CHANGED,
                                        new File(dexFolderOne, "dexFile1-2.dex"),
                                                Status.NOTCHANGED))
                        .build();
        TransformInput dexTwo =
                TransformTestHelper.directoryBuilder(dexFolderTwo)
                        .setName("dex_two")
                        .setScope(QualifiedContent.Scope.SUB_PROJECTS)
                        .putChangedFiles(
                                ImmutableMap.of(
                                        new File(dexFolderTwo, "dexFile2-1.dex"), Status.REMOVED))
                        .build();
        TransformInput dexThree =
                TransformTestHelper.directoryBuilder(dexFolderThree)
                        .setName("dex_three")
                        .setScope(QualifiedContent.Scope.PROJECT)
                        .putChangedFiles(
                                ImmutableMap.of(
                                        new File(dexFolderThree, "dexFile3-2.dex"), Status.ADDED))
                        .build();
        TransformInvocation transformInvocation =
                TransformTestHelper.invocationBuilder()
                        .setInputs(dexOne, dexTwo, dexThree)
                        .setTransformOutputProvider(transformOutputProvider)
                        .setIncremental(true)
                        .build();

        instantRunSliceSplitApkBuilder.transform(transformInvocation);
        assertThat(dexFilesList).hasSize(2);
        for (InstantRunSplitApkBuilder.DexFiles dexFiles : dexFilesList) {
            switch(dexFiles.encodeName()) {
                case "dex_one" :
                    assertThat(dexFiles.getDexFiles()).hasSize(2);
                    break;
                case "dex_two" :
                    assertThat(dexFiles.getDexFiles()).isEmpty();
                    break;
                case "dex_three" :
                    assertThat(dexFiles.getDexFiles()).hasSize(2);
                    break;
                default:
                    fail("Unexpected split apk generation request : " + dexFiles.encodeName());
            }
        }
    }

    @Test
    public void testIncrementalBuild_sliceContainsMultipleChanged()
            throws TransformException, InterruptedException, IOException {

        TransformOutputProvider transformOutputProvider = new TransformOutputProviderForTests();
        File dexFolderOne = dexFileFolder.newFolder();
        FileUtils.createFile(new File(dexFolderOne, "dexFile1-1.dex"), "some dex");
        FileUtils.createFile(new File(dexFolderOne, "dexFile1-2.dex"), "some dex");

        File dexFolderTwo = dexFileFolder.newFolder();
        FileUtils.createFile(new File(dexFolderTwo, "dexFile2-1.dex"), "some dex");

        File dexFolderThree = dexFileFolder.newFolder();
        FileUtils.createFile(new File(dexFolderThree, "dexFile3-1.dex"), "some dex");
        FileUtils.createFile(new File(dexFolderThree, "dexFile3-2.dex"), "some dex");

        TransformInput dexOne =
                TransformTestHelper.directoryBuilder(dexFolderOne)
                        .setName("dex_one")
                        .setScope(QualifiedContent.Scope.PROJECT)
                        .putChangedFiles(
                                ImmutableMap.of(
                                        new File(dexFolderOne, "dexFile1-1.dex"),
                                        Status.CHANGED,
                                        new File(dexFolderOne, "dexFile1-2.dex"),
                                        Status.CHANGED))
                        .build();
        TransformInput dexTwo =
                TransformTestHelper.directoryBuilder(dexFolderTwo)
                        .setName("dex_two")
                        .setScope(QualifiedContent.Scope.SUB_PROJECTS)
                        .putChangedFiles(
                                ImmutableMap.of(
                                        new File(dexFolderTwo, "dexFile2-1.dex"),
                                        Status.NOTCHANGED,
                                        new File(dexFolderTwo, "dexFile2-2.dex"),
                                        Status.REMOVED))
                        .build();
        TransformInput dexThree =
                TransformTestHelper.directoryBuilder(dexFolderThree)
                        .setName("dex_three")
                        .setScope(QualifiedContent.Scope.PROJECT)
                        .putChangedFiles(
                                ImmutableMap.of(
                                        new File(dexFolderThree, "dexFile3-2.dex"), Status.ADDED))
                        .build();
        TransformInvocation transformInvocation =
                TransformTestHelper.invocationBuilder()
                        .setInputs(dexOne, dexTwo, dexThree)
                        .setTransformOutputProvider(transformOutputProvider)
                        .setIncremental(true)
                        .build();


        instantRunSliceSplitApkBuilder.transform(transformInvocation);
        assertThat(
                        dexFilesList
                                .stream()
                                .map(InstantRunSplitApkBuilder.DexFiles::encodeName)
                                .collect(Collectors.toList()))
                .containsExactly("dex_one", "dex_two", "dex_three");
        for (InstantRunSplitApkBuilder.DexFiles dexFiles : dexFilesList) {
            switch (dexFiles.encodeName()) {
                case "dex_one":
                    assertThat(dexFiles.getDexFiles()).hasSize(2);
                    break;
                case "dex_two":
                    assertThat(dexFiles.getDexFiles()).hasSize(1);
                    break;
                case "dex_three":
                    assertThat(dexFiles.getDexFiles()).hasSize(2);
                    break;
                default:
                    fail("Unexpected split apk generation request : " + dexFiles.encodeName());
            }
        }
    }

    private static class TransformOutputProviderForTests implements TransformOutputProvider {

        @Override
        public void deleteAll() {}

        @NonNull
        @Override
        public File getContentLocation(@NonNull String name,
                @NonNull Set<QualifiedContent.ContentType> types,
                @NonNull Set<? super QualifiedContent.Scope> scopes, @NonNull Format format) {
            fail("Unexpected call to getContentLocation");
            throw new RuntimeException("Unexpected call to getContentLocation");
        }
    }
}
