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

package com.android.build.gradle.tasks;

import static com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy.MULTI_APK_SEPARATE_RESOURCES;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.build.VariantOutput;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.fixtures.DirectWorkerExecutor;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.BuildElementActionScheduler;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildElementsTransformParams;
import com.android.build.gradle.internal.scope.BuildElementsTransformRunnable;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.Workers;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import javax.inject.Inject;
import kotlin.jvm.functions.Function2;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.workers.WorkerExecutor;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Tests for {@link InstantRunResourcesApkBuilder} class */
public class InstantRunResourcesApkBuilderTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    InstantRunResourcesApkBuilder task;
    Project project;
    File outputFolder;
    File testDir;

    @Mock BuildableArtifact buildableArtifact;
    @Mock VariantScope variantScope;
    @Mock BuildArtifactsHolder artifacts;
    @Mock GradleVariantConfiguration variantConfiguration;
    @Mock GlobalScope globalScope;
    @Mock AndroidBuilder androidBuilder;
    @Mock FileCollection signingConfig;
    @Mock InstantRunBuildContext buildContext;
    @Mock FileTree signingConfigFileTree;

    @Rule public TemporaryFolder signingConfigDirectory = new TemporaryFolder();

    public static class InstantRunResourcesApkBuilderForTest extends InstantRunResourcesApkBuilder {
        @Inject
        public InstantRunResourcesApkBuilderForTest(WorkerExecutor workerExecutor) {
            super(workerExecutor);
        }

        @Override
        protected BuildElements getResInputBuildArtifacts() {
            return new BuildElements(super.getResInputBuildArtifacts().getElements()) {

                @NotNull
                @Override
                public BuildElementActionScheduler transform(
                        @NotNull WorkerExecutorFacade workers,
                        @NotNull
                                Class<? extends BuildElementsTransformRunnable>
                                        transformRunnableClass,
                        @NotNull
                                Function2<
                                                ? super ApkData, ? super File,
                                                ? extends BuildElementsTransformParams>
                                        paramsFactory) {
                    return super.transform(
                            Workers.INSTANCE.getWorker(new DirectWorkerExecutor()),
                            transformRunnableClass,
                            paramsFactory);
                }
            };
        }
    }

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        testDir = temporaryFolder.newFolder();
        project = ProjectBuilder.builder().withProjectDir(testDir).build();

        task = project.getTasks().create("test", InstantRunResourcesApkBuilderForTest.class);

        InstantRunResourcesApkBuilder.doPackageCodeSplitApk = false;

        when(variantScope.getFullVariantName()).thenReturn("testVariant");
        when(variantScope.getGlobalScope()).thenReturn(globalScope);
        when(variantScope.getVariantConfiguration()).thenReturn(variantConfiguration);
        when(globalScope.getAndroidBuilder()).thenReturn(androidBuilder);
        when(variantScope.getInstantRunBuildContext()).thenReturn(buildContext);
        when(buildContext.getPatchingPolicy()).thenReturn(MULTI_APK_SEPARATE_RESOURCES);
        when(signingConfig.getAsFileTree()).thenReturn(signingConfigFileTree);
        File signingConfigFile = signingConfigDirectory.newFile("signing-config.json");
        when(signingConfigFileTree.getFiles())
                .thenReturn(new HashSet<>(Arrays.asList(signingConfigFile)));
        when(signingConfigFileTree.getSingleFile()).thenReturn(signingConfigFile);

        File incrementalDir = temporaryFolder.newFolder("test-incremental");

        when(variantScope.getIncrementalDir(eq(task.getName()))).thenReturn(incrementalDir);
        outputFolder = temporaryFolder.newFolder("test-output-folder");
        when(variantScope.getInstantRunResourceApkFolder()).thenReturn(outputFolder);
        when(variantScope.getArtifacts()).thenReturn(artifacts);
        when(artifacts.getFinalArtifactFiles(InternalArtifactType.PROCESSED_RES))
                .thenReturn(buildableArtifact);
        when(buildableArtifact.getFiles()).thenReturn(ImmutableSet.of());
        when(variantScope.getSigningConfigFileCollection()).thenReturn(signingConfig);
    }

    @After
    public void tearDown() {
        task = null;
        project = null;
    }

    @Test
    public void testConfigAction() {

        InstantRunResourcesApkBuilder.CreationAction configAction =
                new InstantRunResourcesApkBuilder.CreationAction(
                        InternalArtifactType.PROCESSED_RES, variantScope);

        configAction.preConfigure(task.getName());
        configAction.configure(task);

        assertThat(task.getResInputType()).isEqualTo(InternalArtifactType.PROCESSED_RES.name());
        assertThat(task.getResourcesFile()).isEqualTo(buildableArtifact);
        assertThat(task.getOutputDirectory()).isEqualTo(outputFolder);
        assertThat(task.getSigningConf()).isEqualTo(signingConfig);
        assertThat(task.getVariantName()).isEqualTo("testVariant");
    }

    @Test
    public void testNoSplitExecution() {

        InstantRunResourcesApkBuilder.CreationAction configAction =
                new InstantRunResourcesApkBuilder.CreationAction(
                        InternalArtifactType.PROCESSED_RES, variantScope);

        configAction.preConfigure(task.getName());
        configAction.configure(task);

        task.doFullTaskAction();
        verify(buildContext).getPatchingPolicy();

        verifyNoMoreInteractions(androidBuilder, buildContext);
    }

    @Test
    public void testOtherPatchingPolicy() throws IOException {
        InstantRunResourcesApkBuilder.CreationAction configAction =
                new InstantRunResourcesApkBuilder.CreationAction(
                        InternalArtifactType.PROCESSED_RES, variantScope);

        when(buildContext.getPatchingPolicy()).thenReturn(InstantRunPatchingPolicy.MULTI_APK);

        configAction.preConfigure(task.getName());
        configAction.configure(task);

        List<ApkData> apkDatas = new ArrayList<>();
        List<File> resourcesFiles = new ArrayList<>();
        ImmutableList<BuildOutput> inputResources =
                createApkDataInputs(apkDatas, resourcesFiles, 3);
        new BuildElements(inputResources).save(temporaryFolder.getRoot());

        File[] inputFiles = temporaryFolder.getRoot().listFiles();
        assertThat(inputFiles).isNotNull();
        when(buildableArtifact.getFiles()).thenReturn(ImmutableSet.copyOf(inputFiles));

        // create dummy output files
        List<File> expectedOutputFiles = new ArrayList<>();
        apkDatas.forEach(
                apkData -> {
                    File expectedOutputFile =
                            new File(
                                    outputFolder,
                                    InstantRunResourcesApkBuilder.mangleApkName(apkData)
                                            + SdkConstants.DOT_ANDROID_PACKAGE);
                    try {
                        FileUtils.createFile(expectedOutputFile, "dummy apk");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    expectedOutputFiles.add(expectedOutputFile);
                });
        expectedOutputFiles.forEach(outputFile -> assertThat(outputFile.exists()).isTrue());

        task.doFullTaskAction();

        // assert that all output files have been cleaned.
        expectedOutputFiles.forEach(outputFile -> assertThat(outputFile.exists()).isFalse());
    }

    @Test
    public void testAnotherSingleSplitExecution() throws IOException {
        testExecution(1);
    }

    @Test
    public void testMultipleSplitExecution() throws IOException {
        testExecution(3);
    }

    @SuppressWarnings("unchecked")
    private void testExecution(int numberOfSplits) throws IOException {

        InstantRunResourcesApkBuilder.CreationAction configAction =
                new InstantRunResourcesApkBuilder.CreationAction(
                        InternalArtifactType.PROCESSED_RES, variantScope);

        configAction.preConfigure(task.getName());
        configAction.configure(task);

        List<ApkData> apkDatas = new ArrayList<>();
        List<File> resourcesFiles = new ArrayList<>();
        ImmutableList<BuildOutput> resources =
                createApkDataInputs(apkDatas, resourcesFiles, numberOfSplits);
        new BuildElements(resources).save(temporaryFolder.getRoot());

        File[] inputFiles = temporaryFolder.getRoot().listFiles();
        assertThat(inputFiles).isNotNull();
        when(buildableArtifact.getFiles()).thenReturn(ImmutableSet.copyOf(inputFiles));

        task.doFullTaskAction();

        for (int i = 0; i < numberOfSplits; i++) {
            File expectedOutputFile =
                    new File(
                            outputFolder,
                            InstantRunResourcesApkBuilder.mangleApkName(apkDatas.get(i))
                                    + SdkConstants.DOT_ANDROID_PACKAGE);

            verify(buildContext).addChangedFile(FileType.SPLIT, expectedOutputFile);
        }
    }

    private ImmutableList<BuildOutput> createApkDataInputs(
            List<ApkData> apkDatas, List<File> resourcesFiles, int numberOfSplits)
            throws IOException {

        ImmutableList.Builder<BuildOutput> resources = ImmutableList.builder();

        for (int i = 0; i < numberOfSplits; i++) {
            // invoke per split action with one split.
            ApkData apkData = Mockito.mock(ApkData.class);
            apkDatas.add(apkData);
            when(apkData.getBaseName()).thenReturn("feature-" + i);
            when(apkData.getType()).thenReturn(VariantOutput.OutputType.SPLIT);

            File resourcesFile = temporaryFolder.newFile("fake-resources-" + i + ".apk");

            resourcesFiles.add(resourcesFile);
            resources.add(
                    new BuildOutput(InternalArtifactType.PROCESSED_RES, apkData, resourcesFile));
        }
        return resources.build();
    }
}
