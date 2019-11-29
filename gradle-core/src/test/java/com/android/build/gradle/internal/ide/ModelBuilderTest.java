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

package com.android.build.gradle.internal.ide;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.VariantManager;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.publishing.PublishingSpecs;
import com.android.build.gradle.internal.scope.AnchorOutputType;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.OutputFactory;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.core.VariantType;
import com.android.builder.core.VariantTypeImpl;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.TestVariantBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.impldep.com.google.common.base.Charsets;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Tests for the {@link com.android.build.gradle.internal.ide.ModelBuilder} */
public class ModelBuilderTest {

    private static final String PROJECT = "project";

    @Mock GlobalScope globalScope;
    @Mock Project project;
    @Mock Gradle gradle;
    @Mock VariantManager variantManager;
    @Mock TaskManager taskManager;
    @Mock AndroidConfig androidConfig;
    @Mock ExtraModelInfo extraModelInfo;
    @Mock BuildArtifactsHolder artifacts;

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    ModelBuilder modelBuilder;
    File apkLocation;

    @Before
    public void setUp() throws IOException {

        MockitoAnnotations.initMocks(this);

        when(globalScope.getProject()).thenReturn(project);

        when(project.getGradle()).thenReturn(gradle);
        when(project.getProjectDir()).thenReturn(new File(""));

        when(gradle.getRootProject()).thenReturn(project);
        when(gradle.getParent()).thenReturn(null);
        when(gradle.getIncludedBuilds()).thenReturn(ImmutableList.of());

        apkLocation = temporaryFolder.newFolder("apk");

        modelBuilder =
                new ModelBuilder(
                        globalScope,
                        variantManager,
                        taskManager,
                        androidConfig,
                        extraModelInfo,
                        AndroidProject.PROJECT_TYPE_APP,
                        AndroidProject.GENERATION_ORIGINAL);
    }

    @Test
    public void testEmptyMinimalisticModel() {
        assertThat(modelBuilder.buildMinimalisticModel()).isNotNull();
        assertThat(modelBuilder.buildMinimalisticModel().getVariantsBuildOutput()).isEmpty();
    }

    @Test
    public void testSingleVariantNoOutputMinimalisticModel() {

        GradleVariantConfiguration variantConfiguration =
                Mockito.mock(GradleVariantConfiguration.class);
        when(variantConfiguration.getDirName()).thenReturn("variant/name");
        when(variantConfiguration.getType()).thenReturn(VariantTypeImpl.BASE_APK);

        VariantScope variantScope =
                createVariantScope("variantName", "variant/name", variantConfiguration);
        createVariantData(variantScope, variantConfiguration);
        when(variantManager.getVariantScopes()).thenReturn(ImmutableList.of(variantScope));

        assertThat(modelBuilder.buildMinimalisticModel()).isNotNull();
        Collection<VariantBuildOutput> variantsBuildOutput =
                modelBuilder.buildMinimalisticModel().getVariantsBuildOutput();
        assertThat(variantsBuildOutput).hasSize(1);
        VariantBuildOutput variantBuildOutput =
                Iterators.getOnlyElement(variantsBuildOutput.iterator());
        assertThat(variantBuildOutput.getName()).isEqualTo("variantName");
        Collection<OutputFile> variantOutputs = variantBuildOutput.getOutputs();
        assertThat(variantOutputs).isNotNull();
        assertThat(variantOutputs).hasSize(0);
    }

    @Test
    public void testSingleVariantWithOutputMinimalisticModel() throws IOException {

        GradleVariantConfiguration variantConfiguration =
                Mockito.mock(GradleVariantConfiguration.class);
        when(variantConfiguration.getDirName()).thenReturn("variant/name");
        when(variantConfiguration.getType()).thenReturn(VariantTypeImpl.BASE_APK);

        VariantScope variantScope =
                createVariantScope("variantName", "variant/name", variantConfiguration);
        createVariantData(variantScope, variantConfiguration);

        when(variantManager.getVariantScopes()).thenReturn(ImmutableList.of(variantScope));

        File variantOutputFolder = new File(apkLocation, FileUtils.join("variant", "name"));
        File apkOutput = new File(variantOutputFolder, "main.apk");
        Files.createParentDirs(apkOutput);
        Files.asCharSink(apkOutput, Charsets.UTF_8).write("some apk");

        OutputFactory outputFactory = new OutputFactory(PROJECT, variantConfiguration);
        new BuildElements(
                        ImmutableList.of(
                                new BuildOutput(
                                        InternalArtifactType.APK,
                                        outputFactory.addMainApk(),
                                        apkOutput)))
                .save(variantOutputFolder);

        ProjectBuildOutput projectBuildOutput = modelBuilder.buildMinimalisticModel();
        assertThat(projectBuildOutput).isNotNull();
        Collection<VariantBuildOutput> variantsBuildOutput =
                projectBuildOutput.getVariantsBuildOutput();
        assertThat(variantsBuildOutput).hasSize(1);

        VariantBuildOutput variantBuildOutput =
                Iterators.getOnlyElement(variantsBuildOutput.iterator());
        assertThat(variantBuildOutput.getName()).isEqualTo("variantName");
        Collection<OutputFile> variantOutputs = variantBuildOutput.getOutputs();
        assertThat(variantOutputs).isNotNull();
        assertThat(variantOutputs).hasSize(1);

        OutputFile buildOutput = Iterators.getOnlyElement(variantOutputs.iterator());
        assertThat(buildOutput.getOutputType()).isEqualTo("MAIN");
        assertThat(buildOutput.getFilters()).isEmpty();
        assertThat(buildOutput.getFilterTypes()).isEmpty();
        assertThat(buildOutput.getMainOutputFile()).isEqualTo(buildOutput);
        assertThat(buildOutput.getOutputFile().exists()).isTrue();
        assertThat(Files.readFirstLine(buildOutput.getOutputFile(), Charsets.UTF_8))
                .isEqualTo("some apk");
    }

    @Test
    public void testSingleVariantWithMultipleOutputMinimalisticModel() throws IOException {

        GradleVariantConfiguration variantConfiguration =
                Mockito.mock(GradleVariantConfiguration.class);
        when(variantConfiguration.getDirName()).thenReturn("variant/name");
        when(variantConfiguration.getType()).thenReturn(VariantTypeImpl.BASE_APK);

        VariantScope variantScope =
                createVariantScope("variantName", "variant/name", variantConfiguration);
        createVariantData(variantScope, variantConfiguration);

        when(variantManager.getVariantScopes()).thenReturn(ImmutableList.of(variantScope));

        OutputFactory outputFactory = new OutputFactory(PROJECT, variantConfiguration);

        File variantOutputFolder = new File(apkLocation, FileUtils.join("variant", "name"));

        File apkOutput = createApk(variantOutputFolder, "main.apk");

        ImmutableList.Builder<BuildOutput> buildOutputBuilder = ImmutableList.builder();
        buildOutputBuilder.add(
                new BuildOutput(InternalArtifactType.APK, outputFactory.addMainApk(), apkOutput));

        for (int i = 0; i < 5; i++) {
            apkOutput = createApk(variantOutputFolder, "split_" + i + ".apk");

            buildOutputBuilder.add(
                    new BuildOutput(
                            InternalArtifactType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT,
                            outputFactory.addConfigurationSplit(
                                    VariantOutput.FilterType.DENSITY, "hdpi", apkOutput.getName()),
                            apkOutput));
        }

        new BuildElements(buildOutputBuilder.build()).save(variantOutputFolder);

        ProjectBuildOutput projectBuildOutput = modelBuilder.buildMinimalisticModel();
        assertThat(projectBuildOutput).isNotNull();
        Collection<VariantBuildOutput> variantsBuildOutput =
                projectBuildOutput.getVariantsBuildOutput();
        assertThat(variantsBuildOutput).hasSize(1);

        VariantBuildOutput variantBuildOutput =
                Iterators.getOnlyElement(variantsBuildOutput.iterator());
        assertThat(variantBuildOutput.getName()).isEqualTo("variantName");
        Collection<OutputFile> variantOutputs = variantBuildOutput.getOutputs();
        assertThat(variantOutputs).isNotNull();
        assertThat(variantOutputs).hasSize(6);

        for (OutputFile buildOutput : variantOutputs) {
            assertThat(buildOutput.getOutputType()).isAnyOf("MAIN", "SPLIT");
            if (buildOutput.getOutputType().equals("MAIN")) {
                assertThat(buildOutput.getFilters()).isEmpty();
                assertThat(buildOutput.getFilterTypes()).isEmpty();
            } else {
                assertThat(buildOutput.getFilters()).hasSize(1);
                FilterData filterData =
                        Iterators.getOnlyElement(buildOutput.getFilters().iterator());
                assertThat(filterData.getFilterType()).isEqualTo("DENSITY");
                assertThat(filterData.getIdentifier()).isEqualTo("hdpi");
                assertThat(buildOutput.getFilterTypes()).containsExactly("DENSITY");
            }
            assertThat(buildOutput.getMainOutputFile()).isEqualTo(buildOutput);
            assertThat(buildOutput.getOutputFile().exists()).isTrue();
            assertThat(Files.readFirstLine(buildOutput.getOutputFile(), Charsets.UTF_8))
                    .isEqualTo("some apk");
        }
    }

    @Test
    public void testMultipleVariantWithOutputMinimalisticModel() throws IOException {

        List<String> expectedVariantNames = new ArrayList<>();
        ImmutableList.Builder<VariantScope> scopes = ImmutableList.builder();
        for (int i = 0; i < 5; i++) {
            GradleVariantConfiguration variantConfiguration =
                    Mockito.mock(GradleVariantConfiguration.class);
            when(variantConfiguration.getDirName()).thenReturn("variant/name" + i);
            when(variantConfiguration.getType()).thenReturn(VariantTypeImpl.BASE_APK);

            String variantName = "variantName" + i;
            VariantScope variantScope =
                    createVariantScope(variantName, "variant/name" + i, variantConfiguration);
            expectedVariantNames.add(variantName);
            createVariantData(variantScope, variantConfiguration);

            File variantOutputFolder = new File(apkLocation, FileUtils.join("variant", "name" + i));
            File apkOutput = new File(variantOutputFolder, "main.apk");
            Files.createParentDirs(apkOutput);
            Files.asCharSink(apkOutput, Charsets.UTF_8).write("some apk");

            OutputFactory outputFactory = new OutputFactory(PROJECT, variantConfiguration);
            new BuildElements(
                            ImmutableList.of(
                                    new BuildOutput(
                                            InternalArtifactType.APK,
                                            outputFactory.addMainApk(),
                                            apkOutput)))
                    .save(variantOutputFolder);
            scopes.add(variantScope);
        }

        when(variantManager.getVariantScopes()).thenReturn(scopes.build());

        ProjectBuildOutput projectBuildOutput = modelBuilder.buildMinimalisticModel();
        assertThat(projectBuildOutput).isNotNull();
        Collection<VariantBuildOutput> variantsBuildOutput =
                projectBuildOutput.getVariantsBuildOutput();
        assertThat(variantsBuildOutput).hasSize(5);

        for (VariantBuildOutput variantBuildOutput : variantsBuildOutput) {
            assertThat(variantBuildOutput.getName()).startsWith("variantName");
            expectedVariantNames.remove(variantBuildOutput.getName());
            Collection<OutputFile> variantOutputs = variantBuildOutput.getOutputs();
            assertThat(variantOutputs).isNotNull();
            assertThat(variantOutputs).hasSize(1);

            OutputFile buildOutput = Iterators.getOnlyElement(variantOutputs.iterator());
            assertThat(buildOutput.getOutputType()).isEqualTo("MAIN");
            assertThat(buildOutput.getFilters()).isEmpty();
            assertThat(buildOutput.getFilterTypes()).isEmpty();
            assertThat(buildOutput.getMainOutputFile()).isEqualTo(buildOutput);
            assertThat(buildOutput.getOutputFile().exists()).isTrue();
            assertThat(Files.readFirstLine(buildOutput.getOutputFile(), Charsets.UTF_8))
                    .isEqualTo("some apk");
        }
        assertThat(expectedVariantNames).isEmpty();
    }

    @Test
    public void testSingleVariantWithOutputWithSingleTestVariantMinimalisticModel()
            throws IOException {

        GradleVariantConfiguration variantConfiguration =
                Mockito.mock(GradleVariantConfiguration.class);
        when(variantConfiguration.getDirName()).thenReturn("variant/name");
        when(variantConfiguration.getType()).thenReturn(VariantTypeImpl.LIBRARY);

        VariantScope variantScope =
                createVariantScope("variantName", "variant/name", variantConfiguration);
        when(variantScope.getArtifacts()).thenReturn(artifacts);
        BaseVariantData variantData = createVariantData(variantScope, variantConfiguration);

        BuildableArtifact buildableArtifact = Mockito.mock(BuildableArtifact.class);
        when(buildableArtifact.iterator())
                .thenReturn(ImmutableSet.of(temporaryFolder.getRoot()).iterator());
        when(artifacts.getFinalArtifactFiles(ArgumentMatchers.eq(InternalArtifactType.AAR)))
                .thenReturn(buildableArtifact);

        GradleVariantConfiguration testVariantConfiguration =
                Mockito.mock(GradleVariantConfiguration.class);
        when(testVariantConfiguration.getDirName()).thenReturn("test/name");
        when(testVariantConfiguration.getType()).thenReturn(VariantTypeImpl.UNIT_TEST);

        VariantScope testVariantScope =
                createVariantScope("testVariant", "test/name", testVariantConfiguration);
        BaseVariantData testVariantData =
                createVariantData(testVariantScope, testVariantConfiguration);
        when(testVariantData.getType()).thenReturn(VariantTypeImpl.UNIT_TEST);
        when(testVariantScope.getTestedVariantData()).thenReturn(variantData);

        when(testVariantScope.getArtifacts()).thenReturn(artifacts);
        BuildableArtifact testBuildableArtifact = Mockito.mock(BuildableArtifact.class);
        when(artifacts.getFinalArtifactFiles(AnchorOutputType.ALL_CLASSES))
                .thenReturn(testBuildableArtifact);
        when(testBuildableArtifact.iterator())
                .thenReturn(ImmutableSet.of(temporaryFolder.getRoot()).iterator());

        when(variantManager.getVariantScopes())
                .thenReturn(ImmutableList.of(variantScope, testVariantScope));

        File variantOutputFolder = new File(apkLocation, FileUtils.join("variant", "name"));
        File apkOutput = new File(variantOutputFolder, "main.apk");
        Files.createParentDirs(apkOutput);
        Files.asCharSink(apkOutput, Charsets.UTF_8).write("some apk");

        OutputFactory outputFactory = new OutputFactory(PROJECT, variantConfiguration);
        new BuildElements(
                        ImmutableList.of(
                                new BuildOutput(
                                        InternalArtifactType.APK,
                                        outputFactory.addMainApk(),
                                        apkOutput)))
                .save(variantOutputFolder);

        ProjectBuildOutput projectBuildOutput = modelBuilder.buildMinimalisticModel();
        assertThat(projectBuildOutput).isNotNull();
        Collection<VariantBuildOutput> variantsBuildOutput =
                projectBuildOutput.getVariantsBuildOutput();
        assertThat(variantsBuildOutput).hasSize(1);

        VariantBuildOutput variantBuildOutput =
                Iterators.getOnlyElement(variantsBuildOutput.iterator());
        assertThat(variantBuildOutput.getName()).isEqualTo("variantName");
        Collection<OutputFile> variantOutputs = variantBuildOutput.getOutputs();
        assertThat(variantOutputs).isNotNull();
        assertThat(variantOutputs).hasSize(1);

        // check the test variant.
        Collection<TestVariantBuildOutput> testingVariants =
                variantBuildOutput.getTestingVariants();
        assertThat(testingVariants).hasSize(1);
        TestVariantBuildOutput testVariant = Iterators.getOnlyElement(testingVariants.iterator());
        assertThat(testVariant.getName()).isEqualTo("testVariant");
        assertThat(testVariant.getTestedVariantName()).isEqualTo("variantName");
        assertThat(testVariant.getOutputs()).hasSize(1);
    }

    private static BaseVariantData createVariantData(
            VariantScope variantScope, GradleVariantConfiguration variantConfiguration) {
        BaseVariantData variantData = Mockito.mock(BaseVariantData.class);
        final VariantType type = variantConfiguration.getType();
        when(variantData.getType()).thenReturn(type);
        when(variantData.getScope()).thenReturn(variantScope);
        when(variantData.getVariantConfiguration()).thenReturn(variantConfiguration);

        when(variantScope.getVariantData()).thenReturn(variantData);

        return variantData;
    }

    private VariantScope createVariantScope(
            String variantName, String dirName, GradleVariantConfiguration variantConfiguration) {
        VariantScope variantScope = Mockito.mock(VariantScope.class);
        when(variantScope.getFullVariantName()).thenReturn(variantName);
        when(variantScope.getGlobalScope()).thenReturn(globalScope);
        when(variantScope.getApkLocation()).thenReturn(new File(apkLocation, dirName));
        when(variantScope.getVariantConfiguration()).thenReturn(variantConfiguration);

        final VariantType type = variantConfiguration.getType();
        when(variantScope.getType()).thenReturn(type);

        //noinspection ConstantConditions
        if (type != null) {
            when(variantScope.getPublishingSpec()).thenReturn(PublishingSpecs.getVariantSpec(type));
        }

        return variantScope;
    }

    private static File createApk(File variantOutputFolder, String fileName) throws IOException {
        File apkOutput = new File(variantOutputFolder, fileName);
        Files.createParentDirs(apkOutput);
        Files.asCharSink(apkOutput, Charsets.UTF_8).write("some apk");
        return apkOutput;
    }
}
