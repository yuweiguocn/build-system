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

package com.android.build.gradle.integration.application;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

/** Assert that the list of tasks run for a basic project matches a golden list. */
public class TasksRunTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Test
    @Ignore("b/79563107")
    public void checkTasksRun() throws Exception {
        GradleBuildResult result = project.executor().run("assembleDebug");

        assertThat(result.getTasks())
                .containsExactly(
                        ":preBuild",
                        ":preDebugBuild",
                        ":mainApkListPersistenceDebug",
                        ":prepareLintJar",
                        ":compileDebugAidl",
                        ":compileDebugRenderscript",
                        ":checkDebugManifest",
                        ":generateDebugBuildConfig",
                        ":generateDebugResValues",
                        ":generateDebugResources",
                        ":mergeDebugResources",
                        ":createDebugCompatibleScreenManifests",
                        ":processDebugManifest",
                        ":splitsDiscoveryTaskDebug",
                        ":processDebugResources",
                        ":generateDebugSources",
                        ":javaPreCompileDebug",
                        ":compileDebugJavaWithJavac",
                        ":compileDebugNdk",
                        ":compileDebugSources",
                        ":mergeDebugShaders",
                        ":compileDebugShaders",
                        ":generateDebugAssets",
                        ":mergeDebugAssets",
                        ":transformClassesWithDexBuilderForDebug",
                        ":transformDexArchiveWithExternalLibsDexMergerForDebug",
                        ":transformDexArchiveWithDexMergerForDebug",
                        ":mergeDebugJniLibFolders",
                        ":transformNativeLibsWithMergeJniLibsForDebug",
                        ":processDebugJavaRes",
                        ":transformResourcesWithMergeJavaResForDebug",
                        ":validateSigningDebug",
                        ":packageDebug",
                        ":assembleDebug");
    }
}
