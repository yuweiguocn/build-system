/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.nativebuild;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.build.OutputFile;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.build.gradle.integration.common.utils.VariantOutputUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.VariantBuildOutput;
import com.android.testutils.apk.Zip;
import com.android.testutils.truth.MoreTruth;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

/** Test drive for the abiPureSplits samples test. */
public class AbiPureSplits {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("abiPureSplits").create();

    @BeforeClass
    public static void setup() {
        AssumeUtil.assumeBuildToolsAtLeast(21);
    }

    @Test
    public void testAbiPureSplits() throws Exception {
        AssumeUtil.assumeNotWindowsBot(); // https://issuetracker.google.com/70931936
        ProjectBuildOutput outputModel = assembleAndGetModel();

        // build a set of expected outputs
        Set<String> expected = Sets.newHashSetWithExpectedSize(5);
        expected.add("mips");
        expected.add("x86");
        expected.add("armeabi-v7a");

        List<? extends OutputFile> outputs = getOutputs(outputModel);
        assertEquals(4, outputs.size());
        for (OutputFile outputFile : outputs) {
            String filter = VariantOutputUtils.getFilter(outputFile, OutputFile.ABI);
            assertEquals(
                    filter == null ? OutputFile.MAIN : OutputFile.SPLIT,
                    outputFile.getOutputType());

            // with pure splits, all split have the same version code.
            if (filter != null) {
                assertTrue(expected.remove(filter));

                if (outputFile.getFilterTypes().contains(OutputFile.ABI)) {
                    // if this is an ABI split, ensure the .so file presence (and only one)
                    MoreTruth.assertThatZip(outputFile.getOutputFile())
                            .entries("/lib/.*")
                            .containsExactly("/lib/" + filter + "/libhello-jni.so");
                }


            } else {
                try (Zip zip = new Zip(outputFile.getOutputFile())) {
                    // main file should not have any lib/ entries.
                    assertThat(zip.getEntries(Pattern.compile("^/lib/.*"))).isEmpty();
                    // assert that our resources got packaged in the main file.
                    assertThat(zip.getEntries(Pattern.compile("^/res/.*"))).hasSize(5);
                }

            }

        }

        // this checks we didn't miss any expected output.
        assertTrue(expected.isEmpty());
    }

    @Test
    public void testAddingAnAbiPureSplit() throws Exception {
        // This test uses the deprecated NDK integration, which does not work properly on Windows.
        AssumeUtil.assumeNotWindows();

        ProjectBuildOutput outputModel = assembleAndGetModel();

        // get the last modified time of the initial APKs so we can make sure incremental build
        // does not rebuild things unnecessarily.
        Map<String, Long> lastModifiedTimePerAbi =
                getApkModifiedTimePerAbi(getOutputs(outputModel));

        TemporaryProjectModification.doTest(
                project,
                modifiedProject -> {
                    modifiedProject.replaceInFile(
                            "build.gradle",
                            "include 'x86', 'armeabi-v7a', 'mips'",
                            "include 'x86', 'armeabi-v7a', 'mips', 'armeabi'");
                    ProjectBuildOutput incrementalModel =
                            project.executeAndReturnOutputModel("assembleDebug");

                    List<? extends OutputFile> outputs = getOutputs(incrementalModel);
                    for (OutputFile output : outputs) {
                        System.out.println("found " + output.getOutputFile().getAbsolutePath());
                    }

                    assertThat(outputs).hasSize(5);
                    boolean foundAddedAPK = false;
                    for (OutputFile output : outputs) {

                        String filter = VariantOutputUtils.getFilter(output, OutputFile.ABI);

                        if (Objects.equals(filter, "armeabi")) {
                            // found our added abi, done.
                            foundAddedAPK = true;
                        } else {
                            // check that the APK was not rebuilt.
                            // uncomment once packageAbiRes is incremental.
                            //                    assertTrue("APK should not have been rebuilt in incremental mode : " + filter,
                            //                            lastModifiedTimePerAbi.get(filter).longValue()
                            //                                    == output.getOuputFile().lastModified())

                        }
                    }

                    if (!foundAddedAPK) {
                        Assert.fail("Could not find added ABI split : armeabi");
                    }
                });
    }

    @Test
    public void testDeletingAnAbiPureSplit() throws Exception {
        // This test uses the deprecated NDK integration, which does not work properly on Windows.
        AssumeUtil.assumeNotWindows();

        ProjectBuildOutput outputModel = assembleAndGetModel();

        // record the build time of each APK to ensure we don't rebuild those in incremental mode.
        Map<String, Long> lastModifiedTimePerAbi =
                getApkModifiedTimePerAbi(getOutputs(outputModel));

        TemporaryProjectModification.doTest(
                project,
                modifiedProject -> {
                    modifiedProject.replaceInFile(
                            "build.gradle",
                            "include 'x86', 'armeabi-v7a', 'mips'",
                            "include 'x86', 'armeabi-v7a'");
                    ProjectBuildOutput incrementalModel =
                            project.executeAndReturnOutputModel("assembleDebug");

                    List<? extends OutputFile> outputs = getOutputs(incrementalModel);
                    assertThat(outputs).hasSize(3);
                    for (OutputFile output : outputs) {

                        String filter = VariantOutputUtils.getFilter(output, OutputFile.ABI);
                        if (Objects.equals(filter, "mips")) {
                            Assert.fail("Found deleted ABI split : mips");
                        } else {
                            // check that the APK was not rebuilt.
                            //                    assertNotNull("Cannot find initial APK for ABI : " + filter);
                            // uncomment once packageAbiRes is incremental.
                            //                    assertTrue("APK should not have been rebuilt in incremental mode",
                            //                            lastModifiedTimePerAbi.get(filter).longValue()
                            //                                    == output.getOuputFile().lastModified())
                        }
                    }
                });
    }

    private List<? extends OutputFile> getOutputs(ProjectBuildOutput outputModel)
            throws IOException {
        // Load the project build outputs
        VariantBuildOutput debugBuildOutput =
                ProjectBuildOutputUtils.getDebugVariantBuildOutput(outputModel);

        // all splits have the same version.
        debugBuildOutput
                .getOutputs()
                .forEach(
                        output -> {
                            assertEquals(123, output.getVersionCode());
                        });

        return ImmutableList.copyOf(debugBuildOutput.getOutputs());
    }

    @NonNull
    private static Map<String, Long> getApkModifiedTimePerAbi(
            Collection<? extends OutputFile> outputs) {
        ImmutableMap.Builder<String, Long> builder = ImmutableMap.builder();
        for (OutputFile output : outputs) {
            String key =
                    output.getOutputType() + VariantOutputUtils.getFilter(output, OutputFile.ABI);
            builder.put(key, output.getOutputFile().lastModified());
        }

        return builder.build();
    }

    private ProjectBuildOutput assembleAndGetModel() throws IOException, InterruptedException {
        ProjectBuildOutput outputModel =
                project.executeAndReturnOutputModel("clean", "assembleDebug");
        AndroidProject syncModel =
                project.model()
                        .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                        .fetchAndroidProjects()
                        .getOnlyModel();
        assertThat(syncModel.getSyncIssues()).hasSize(1);
        assertThat(Iterables.getOnlyElement(syncModel.getSyncIssues()).getMessage())
                .contains(
                        "Configuration APKs are supported by the Google Play Store only when publishing Android Instant Apps. To instead generate stand-alone APKs for different device configurations, set generatePureSplits=false.");
        return outputModel;
    }
}
