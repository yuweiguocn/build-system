/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.fixture.TestVersions.ANDROID_ARCH_VERSION;
import static com.android.build.gradle.integration.common.fixture.TestVersions.PLAY_SERVICES_VERSION;
import static com.android.build.gradle.integration.common.fixture.TestVersions.SUPPORT_LIB_VERSION;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.COORDINATES;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.ANDROID;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.annotations.NonNull;
import com.android.build.OutputFile;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.ProductFlavorHelper;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.build.gradle.integration.common.utils.VariantUtils;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.ClassField;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.TestVariantBuildOutput;
import com.android.builder.model.Variant;
import com.android.builder.model.VariantBuildOutput;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.Library;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.truth.Truth;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Assemble tests for basic that loads the model but doesn't build.
 */
public class BasicTest2 {
    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("basic")
            .create();

    private static final Set<String> coordinates =
            ImmutableSet.of(
                    "com.google.android.gms:play-services-base:" + PLAY_SERVICES_VERSION + "@aar",
                    "com.android.support:support-v13:" + SUPPORT_LIB_VERSION + "@aar",
                    "com.android.support:support-v4:" + SUPPORT_LIB_VERSION + "@aar",
                    "com.google.android.gms:play-services-tasks:" + PLAY_SERVICES_VERSION + "@aar",
                    "com.google.android.gms:play-services-basement:"
                            + PLAY_SERVICES_VERSION
                            + "@aar",
                    "com.google.android.gms:play-services-basement:"
                            + PLAY_SERVICES_VERSION
                            + "@aar",
                    "com.android.support:loader:" + SUPPORT_LIB_VERSION + "@aar",
                    "com.android.support:viewpager:" + SUPPORT_LIB_VERSION + "@aar",
                    "com.android.support:coordinatorlayout:" + SUPPORT_LIB_VERSION + "@aar",
                    "com.android.support:drawerlayout:" + SUPPORT_LIB_VERSION + "@aar",
                    "com.android.support:slidingpanelayout:" + SUPPORT_LIB_VERSION + "@aar",
                    "com.android.support:customview:" + SUPPORT_LIB_VERSION + "@aar",
                    "com.android.support:swiperefreshlayout:" + SUPPORT_LIB_VERSION + "@aar",
                    "com.android.support:asynclayoutinflater:" + SUPPORT_LIB_VERSION + "@aar",
                    "com.android.support:versionedparcelable:" + SUPPORT_LIB_VERSION + "@aar",
                    "com.android.support:documentfile:" + SUPPORT_LIB_VERSION + "@aar",
                    "com.android.support:localbroadcastmanager:" + SUPPORT_LIB_VERSION + "@aar",
                    "com.android.support:print:" + SUPPORT_LIB_VERSION + "@aar",
                    "com.android.support:interpolator:" + SUPPORT_LIB_VERSION + "@aar",
                    "com.android.support:cursoradapter:" + SUPPORT_LIB_VERSION + "@aar",
                    "com.android.support:support-fragment:" + SUPPORT_LIB_VERSION + "@aar",
                    "com.android.support:support-media-compat:" + SUPPORT_LIB_VERSION + "@aar",
                    "com.android.support:support-core-ui:" + SUPPORT_LIB_VERSION + "@aar",
                    "com.android.support:support-core-utils:" + SUPPORT_LIB_VERSION + "@aar",
                    "com.android.support:support-compat:" + SUPPORT_LIB_VERSION + "@aar",
                    "android.arch.lifecycle:runtime:" + ANDROID_ARCH_VERSION + "@aar",
                    "android.arch.lifecycle:livedata:" + ANDROID_ARCH_VERSION + "@aar",
                    "android.arch.lifecycle:livedata-core:" + ANDROID_ARCH_VERSION + "@aar",
                    "android.arch.core:runtime:" + ANDROID_ARCH_VERSION + "@aar",
                    "android.arch.lifecycle:viewmodel:" + ANDROID_ARCH_VERSION + "@aar");

    public static ModelContainer<AndroidProject> modelContainer;
    public static ProjectBuildOutput outputModel;

    @BeforeClass
    public static void getModel() throws Exception {
        outputModel =
                project.executeAndReturnOutputModel("clean", "assemble", "assembleAndroidTest");
        // basic project overwrites buildConfigField which emits a sync warning
        modelContainer = project.model().ignoreSyncIssues().fetchAndroidProjects();
        modelContainer
                .getOnlyModel()
                .getSyncIssues()
                .forEach(
                        issue -> {
                            Truth.assertThat(issue.getSeverity())
                                    .isEqualTo(SyncIssue.SEVERITY_WARNING);
                            Truth.assertThat(issue.getMessage())
                                    .containsMatch(Pattern.compile(".*value is being replaced.*"));
                        });
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        modelContainer = null;
    }

    @Test
    public void checkDebugVariant() {
        LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);
        AndroidProject model = modelContainer.getOnlyModel();

        Collection<Variant> variants = model.getVariants();
        assertThat(variants).named("variant list").hasSize(2);

        // debug variant
        Variant debugVariant = AndroidProjectUtils.getVariantByName(model, BuilderConstants.DEBUG);
        new ProductFlavorHelper(debugVariant.getMergedFlavor(), "Debug Merged Flavor")
                .setVersionCode(12)
                .setVersionName("2.0")
                .setMinSdkVersion(16)
                .setTargetSdkVersion(16)
                .setTestInstrumentationRunner("android.support.test.runner.AndroidJUnitRunner")
                .setTestHandleProfiling(Boolean.FALSE)
                .setTestFunctionalTest(null)
                .test();

        // debug variant, tested.
        AndroidArtifact debugMainInfo = debugVariant.getMainArtifact();
        assertThat(debugMainInfo)
                .named("debug main artifact")
                .isNotNull();
        assertThat(debugMainInfo.getApplicationId())
                .named("debug package name")
                .isEqualTo("com.android.tests.basic.debug");
        assertThat(debugMainInfo.isSigned())
                .named("debug isSigned")
                .isTrue();
        assertThat(debugMainInfo.getSourceGenTaskName())
                .named("debug source gen task name")
                .isEqualTo("generateDebugSources");
        assertThat(debugMainInfo.getCompileTaskName())
                .named("debug compile task name")
                .isEqualTo("compileDebugSources");

        VariantBuildOutput debugVariantOutput =
                ProjectBuildOutputUtils.getDebugVariantBuildOutput(outputModel);

        Collection<OutputFile> debugVariantOutputFiles = debugVariantOutput.getOutputs();
        assertThat(debugVariantOutputFiles).named("debug outputs").isNotNull();
        assertThat(debugVariantOutputFiles).named("debug outputs").hasSize(1);

        OutputFile debugMainOutput = Iterables.getOnlyElement(debugVariantOutputFiles);
        assertThat(debugMainOutput)
                .named("debug output")
                .isNotNull();
        assertThat(debugMainOutput.getOutputFile()).named("debug output file").isNotNull();
        assertThat(debugMainOutput.getOutputFile()).named("debug output file").exists();
        assertThat(debugMainOutput.getVersionCode())
                .named("debug output versionCode")
                .isEqualTo(12);

        // check debug dependencies
        DependencyGraphs compileGraph = debugMainInfo.getDependencyGraphs();
        assertThat(compileGraph).named("debug compile graph").isNotNull();

        assertThat(helper.on(compileGraph).withType(JAVA).mapTo(COORDINATES))
                .named("debug compile java libs")
                .containsExactly(
                        "com.android.support:support-annotations:" + SUPPORT_LIB_VERSION + "@jar",
                        "com.android.support:collections:" + SUPPORT_LIB_VERSION + "@jar",
                        "android.arch.lifecycle:common:" + ANDROID_ARCH_VERSION + "@jar",
                        "android.arch.core:common:" + ANDROID_ARCH_VERSION + "@jar");

        LibraryGraphHelper.Items androidItems = helper.on(compileGraph).withType(ANDROID);

        Set<String> coordinateCopies = Sets.newHashSet(coordinates);

        assertThat(androidItems.mapTo(COORDINATES))
                .named("debug compile android libs")
                .containsExactlyElementsIn(coordinateCopies);

        for (Library androidLibrary : androidItems.asLibraries()) {
            assertThat(androidLibrary).isNotNull();
            assertThat(androidLibrary.getFolder())
                    .named("Folder for " + androidLibrary.getArtifactAddress())
                    .isDirectory();
            coordinateCopies.remove(androidLibrary.getArtifactAddress());
        }

        // this variant is tested.
        AndroidArtifact debugTestInfo = VariantUtils.getAndroidTestArtifact(debugVariant);

        assertThat(debugTestInfo.getApplicationId())
                .named("test package")
                .isEqualTo("com.android.tests.basic.debug.test");
        assertThat(debugTestInfo.isSigned())
                .named("test isSigned")
                .isTrue();
        assertThat(debugTestInfo.getSourceGenTaskName())
                .named("test source gen task name")
                .isEqualTo("generateDebugAndroidTestSources");
        assertThat(debugTestInfo.getCompileTaskName())
                .named("test compile task name")
                .isEqualTo("compileDebugAndroidTestSources");

        Collection<File> generatedResFolders = debugTestInfo.getGeneratedResourceFolders();
        assertThat(generatedResFolders).named("test generated res folders").isNotNull();
        // size 2 = rs output + resValue output
        assertThat(generatedResFolders).named("test generated res folders").hasSize(2);

        Collection<TestVariantBuildOutput> testVariantBuildOutputs =
                debugVariantOutput.getTestingVariants();
        List<TestVariantBuildOutput> androidTestVariantOutputs =
                testVariantBuildOutputs
                        .stream()
                        .filter(
                                testVariant ->
                                        testVariant
                                                .getType()
                                                .equals(TestVariantBuildOutput.ANDROID_TEST))
                        .collect(Collectors.toList());

        assertThat(androidTestVariantOutputs)
                .named("Test Variant Outputs with type ANDROID_TEST")
                .hasSize(1);
        TestVariantBuildOutput androidTestVariantOutput =
                Iterables.getOnlyElement(androidTestVariantOutputs);

        assertThat(androidTestVariantOutput.getOutputs())
                .named("Test Variant Output output files")
                .hasSize(1);
        OutputFile androidTestOutputFile =
                Iterables.getOnlyElement(androidTestVariantOutput.getOutputs());

        assertThat(androidTestOutputFile).named("test output").isNotNull();
        assertThat(androidTestOutputFile.getOutputFile()).named("test output file").isNotNull();
        assertThat(androidTestOutputFile.getOutputFile()).named("test output file").exists();

        // test the resValues and buildConfigFields.
        ProductFlavor defaultConfig = model.getDefaultConfig().getProductFlavor();
        Map<String, ClassField> buildConfigFields = defaultConfig.getBuildConfigFields();
        testMap(buildConfigFields, "defaultConfig buildconfig fields", ClassField::getValue,
                "DEFAULT", "true", "FOO", "\"foo2\"");

        Map<String, ClassField> resValues = defaultConfig.getResValues();
        testMap(resValues, "defaultConfig resValues", ClassField::getValue, "foo", "foo");

        // test on the debug build type.
        Collection<BuildTypeContainer> buildTypes = model.getBuildTypes();
        for (BuildTypeContainer buildTypeContainer : buildTypes) {
            if (buildTypeContainer.getBuildType().getName().equals(BuilderConstants.DEBUG)) {
                buildConfigFields = buildTypeContainer.getBuildType().getBuildConfigFields();

                testMap(buildConfigFields, "debug buildconfig fields", ClassField::getValue,
                        "FOO", "\"bar\"");

                resValues = buildTypeContainer.getBuildType().getResValues();
                testMap(resValues, "debug resValues", ClassField::getValue, "foo", "foo2");
            }
        }

        // now test the merged flavor
        ProductFlavor mergedFlavor = debugVariant.getMergedFlavor();

        buildConfigFields = mergedFlavor.getBuildConfigFields();
        testMap(buildConfigFields, "mergedFlavor buildconfig fields", ClassField::getValue,
                "DEFAULT", "true", "FOO", "\"foo2\"");

        resValues = mergedFlavor.getResValues();
        testMap(resValues, "mergedFlavor resValues", ClassField::getValue, "foo", "foo");
    }

    @Test
    public void checkReleaseVariant() {
        LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);
        AndroidProject model = modelContainer.getOnlyModel();

        // release variant, not tested.
        Variant releaseVariant = AndroidProjectUtils.getVariantByName(model, "release");

        AndroidArtifact relMainInfo = releaseVariant.getMainArtifact();
        assertThat(relMainInfo).named("release artifact").isNotNull();
        assertThat(relMainInfo.getApplicationId())
                .named("release package")
                .isEqualTo("com.android.tests.basic");
        assertThat(relMainInfo.isSigned())
                .named("release isSigned")
                .isFalse();
        assertThat(relMainInfo.getSourceGenTaskName())
                .named("release source gen task name")
                .isEqualTo("generateReleaseSources");
        assertThat(relMainInfo.getCompileTaskName())
                .named("release compile task name")
                .isEqualTo("compileReleaseSources");

        Collection<VariantBuildOutput> variantBuildOutputs = outputModel.getVariantsBuildOutput();
        assertThat(variantBuildOutputs).hasSize(2);
        VariantBuildOutput releaseVariantOutput =
                ProjectBuildOutputUtils.getVariantBuildOutput(outputModel, "release");

        Collection<OutputFile> releaseVariantOutputFiles = releaseVariantOutput.getOutputs();
        assertThat(releaseVariantOutputFiles).named("debug outputs").isNotNull();
        assertThat(releaseVariantOutputFiles).named("debug outputs").hasSize(1);

        OutputFile relMainOutput = Iterables.getOnlyElement(releaseVariantOutputFiles);
        assertThat(relMainOutput)
                .named("release output")
                .isNotNull();
        assertThat(relMainOutput.getOutputFile()).named("release output file").isNotNull();
        assertThat(relMainOutput.getOutputFile()).named("release output file").exists();
        assertThat(relMainOutput.getVersionCode())
                .named("release output versionCode")
                .isEqualTo(13);

        AndroidArtifact relTestInfo =
                VariantUtils.getOptionalAndroidArtifact(
                        releaseVariant, AndroidProject.ARTIFACT_ANDROID_TEST);
        assertThat(relTestInfo).named("release test artifact").isNull();

        // check release dependencies
        DependencyGraphs releaseGraph = relMainInfo.getDependencyGraphs();
        assertThat(releaseGraph).named("release compile graph").isNotNull();

        assertThat(helper.on(releaseGraph).withType(JAVA).mapTo(COORDINATES))
                .named("release compile java libs")
                .containsExactly(
                        "com.android.support:support-annotations:" + SUPPORT_LIB_VERSION + "@jar",
                        "com.android.support:collections:" + SUPPORT_LIB_VERSION + "@jar",
                        "android.arch.lifecycle:common:" + ANDROID_ARCH_VERSION + "@jar",
                        "android.arch.core:common:" + ANDROID_ARCH_VERSION + "@jar");

        LibraryGraphHelper.Items androidItems = helper.on(releaseGraph).withType(ANDROID);
        Set<String> coordinateCopies = Sets.newHashSet(coordinates);

        assertThat(androidItems.mapTo(COORDINATES))
                .named("release compile android libs")
                .containsExactlyElementsIn(coordinateCopies);

        for (Library androidLibrary : androidItems.asLibraries()) {
            assertThat(androidLibrary).isNotNull();
            assertThat(androidLibrary.getFolder())
                    .named("Folder for " + androidLibrary.getArtifactAddress())
                    .isNotNull();
            assertThat(androidLibrary.getArtifactAddress())
                    .named("coordinates for " + androidLibrary.getArtifactAddress())
                    .isIn(coordinateCopies);
            coordinateCopies.remove(androidLibrary.getArtifactAddress());
            assertThat(androidLibrary.getLocalJars())
                    .named("local jar count for " + androidLibrary.getArtifactAddress())
                    .isEmpty();
        }
    }

    private static <K, V, W> void testMap(
            @NonNull Map<K, V> map,
            @NonNull String name,
            @NonNull Function<V, W> function,
            @NonNull Object... keyValues) {
        assertThat(keyValues.length % 2).named("key/value length").isEqualTo(0);

        assertThat(map).named(name).isNotNull();
        assertThat(map).named(name).hasSize(keyValues.length / 2);

        for (int i = 0; i < keyValues.length; i+=2) {
            //noinspection unchecked
            K key = (K) keyValues[i];
            //noinspection unchecked
            W value = (W) keyValues[i+1];
            // check the map contains the key
            assertThat(map).named(name).containsKey(key);
            // check the map value for key, transformed with the function is equal to the value.
            assertThat(function.apply(map.get(key)))
                    .named(name + "[" + key + "]")
                    .isEqualTo(value);
        }
    }
}
