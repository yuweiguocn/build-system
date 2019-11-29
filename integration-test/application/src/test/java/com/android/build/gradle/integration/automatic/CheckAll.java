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

package com.android.build.gradle.integration.automatic;

import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TestProjectPaths;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.Collection;
import java.util.List;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Test case that executes "standard" gradle tasks in all our tests projects.
 *
 * <p>You can run only one test like this:
 *
 * <p>{@code ./gradlew :base:build-system:integration-test:application:automaticTest
 * --tests=*[abiPureSplits]}
 */
@RunWith(FilterableParameterized.class)
public class CheckAll {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        List<Object[]> parameters = Lists.newArrayList();

        File[] testProjects = TestProjectPaths.getTestProjectDir().listFiles();
        checkState(testProjects != null);

        for (File testProject : testProjects) {
            if (!isValidProjectDirectory(testProject)) {
                continue;
            }

            parameters.add(new Object[] {testProject.getName()});
        }

        return parameters;
    }

    private static boolean isValidProjectDirectory(File testProject) {
        if (!testProject.isDirectory()) {
            return false;
        }

        File buildGradle = new File(testProject, "build.gradle");
        File settingsGradle = new File(testProject, "settings.gradle");

        return buildGradle.exists() || settingsGradle.exists();
    }

    @Rule public GradleTestProject project;

    public CheckAll(String projectName) {
        this.project =
                GradleTestProject.builder()
                        .fromTestProject(projectName)
                        .create();
    }

    @Test
    public void assembleAndLint() throws Exception {
        Assume.assumeTrue(canAssemble(project));
        project
                .executor()
                .withEnableInfoLogging(false)
                .run("assembleDebug", "assembleAndroidTest", "lint");
    }

    private static boolean canAssemble(@NonNull GradleTestProject project) {
        return !BROKEN_ALWAYS_ASSEMBLE.contains(project.getName());
    }

    private static final ImmutableSet<String> BROKEN_ALWAYS_ASSEMBLE =
            ImmutableSet.of(
                    // NDK + Renderscript is currently broken, see http://b.android.com/191791.
                    "ndkRsHelloCompute",
                    "renderscriptNdk",

                    // Component model is currently disabled.
                    "ndkSanAngeles2",
                    "ndkVariants",

                    // Data binding projects are tested in tools/base/build-system/integration-test/databinding
                    "databindingIncremental",
                    "databindingAndDagger",
                    "databinding",
                    "databindingAndKotlin",
                    "databindingAndJetifier",
                    "databindingMultiModule",
                    "databindingWithFeatures",

                    // These are all right:
                    "genFolderApi", // Has a required injectable property
                    "ndkJniPureSplitLib", // Doesn't build until externalNativeBuild {} is added.
                    "duplicateNameImport", // Fails on purpose.
                    "filteredOutBuildType", // assembleDebug does not exist as debug build type is removed.
                    "instant-unit-tests", // Specific to testing instant run, not a "real" project.
                    "projectWithLocalDeps", // Doesn't have a build.gradle, not much to check anyway.
                    "simpleManifestMergingTask", // Not an Android project.
                    "externalBuildPlugin", // Not an Android Project.
                    "lintKotlin", // deliberately contains lint errors
                    "lintStandalone", // Not an Android project
                    "lintStandaloneVital", // Not an Android project
                    "lintStandaloneCustomRules", // Not an Android project
                    "lintCustomRules", // contains integ test for lint itself
                    "lintCustomLocalAndPublishRules", // contains integ test for lint itself
                    "simpleCompositeBuild", // broken composite build project.
                    "multiCompositeBuild" // too complex composite build project to setup
                    );
}
