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

package com.android.build.gradle.integration.nativebuild;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.SyncIssue;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for ndk-build splits. */
public class NdkBuildSplitTest {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldJniApp.builder().withNativeDir("cxx").build())
                    .addFile(HelloWorldJniApp.androidMkC("src/main/cxx"))
                    .create();

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        AssumeUtil.assumeNotWindowsBot(); // https://issuetracker.google.com/70931936
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "import com.android.build.OutputFile;\n"
                        + "ext.versionCodes = [\"armeabi-v7a\":1, \"mips\":2, \"x86\":3, \"mips64\":4, \"all\":0]\n"
                        + "android {\n"
                        + "    compileSdkVersion rootProject.latestCompileSdk\n"
                        + "    buildToolsVersion = rootProject.buildToolsVersion\n"
                        + "    generatePureSplits true\n"
                        + "\n"
                        + "    // This actual the app version code. Giving ourselves 100,000 values [0, 99999]\n"
                        + "    defaultConfig.versionCode = 123\n"
                        + "\n"
                        + "    externalNativeBuild {\n"
                        + "      ndkBuild {\n"
                        + "        path file(\"src/main/cxx/Android.mk\")\n"
                        + "      }\n"
                        + "    }\n"
                        + "\n"
                        + "    flavorDimensions \"androidVersion\"\n"
                        + "    productFlavors {\n"
                        + "        gingerbread {\n"
                        + "            minSdkVersion 10\n"
                        + "            versionCode = 1\n"
                        + "        }\n"
                        + "        icecreamSandwich {\n"
                        + "            minSdkVersion 14\n"
                        + "            versionCode = 2\n"
                        + "        }\n"
                        + "        current {\n"
                        + "            minSdkVersion rootProject.latestCompileSdk\n"
                        + "            versionCode = 3\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    splits {\n"
                        + "        abi {\n"
                        + "            enable = true\n"
                        + "            universalApk = true\n"
                        + "            exclude \"x86_64\", \"arm64-v8a\", \"armeabi\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    // make per-variant version code\n"
                        + "    applicationVariants.all { variant ->\n"
                        + "        // get the version code for the flavor\n"
                        + "        def apiVersion = variant.productFlavors.get(0).versionCode\n"
                        + "\n"
                        + "        // assign a composite version code for each output, based on the flavor above\n"
                        + "        // and the density component.\n"
                        + "        variant.outputs.all { output ->\n"
                        + "            // get the key for the abi component\n"
                        + "            def key = output.getFilter(OutputFile.ABI) == null ? \"all\" : output.getFilter(OutputFile.ABI)\n"
                        + "            // set the versionCode on the output.\n"
                        + "            output.versionCodeOverride = apiVersion * 1000000 + project.ext.versionCodes.get(key) * 100000 + defaultConfig.versionCode\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        project.execute(
                "clean",
                "assembleDebug",
                "generateJsonModelcurrentDebug",
                "generateJsonModelicecreamSandwichDebug",
                "generateJsonModelcurrentRelease",
                "generateJsonModelgingerbreadRelease",
                "generateJsonModelicecreamSandwichRelease",
                "generateJsonModelgingerbreadDebug");
    }

    @Test
    public void checkApkContent() throws IOException {
        assertThatApk(project.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG, "current"))
                .hasVersionCode(3100123);
        assertThatApk(project.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG, "current"))
                .contains("lib/armeabi-v7a/libhello-jni.so");
    }

    @Test
    public void checkModel() throws IOException {
        // Make sure we can get the AndroidProject
        AndroidProject nonNativeModel =
                project.model()
                        .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                        .fetchAndroidProjects()
                        .getOnlyModel();
        assertThat(nonNativeModel.getSyncIssues()).hasSize(1);
        assertThat(Iterables.getOnlyElement(nonNativeModel.getSyncIssues()).getMessage())
                .contains(
                        "Configuration APKs are supported by the Google Play Store only when publishing Android Instant Apps. To instead generate stand-alone APKs for different device configurations, set generatePureSplits=false.");

        NativeAndroidProject model = project.model().fetch(NativeAndroidProject.class);
        assertThat(model.getBuildFiles()).hasSize(1);

        assertThat(model).isNotNull();
        assertThat(model.getName()).isEqualTo("project");
        assertThat(model.getArtifacts()).hasSize(12); // # of ABI (2) * # of variants (6)
        assertThat(model.getFileExtensions()).hasSize(1);

        for (File file : model.getBuildFiles()) {
            assertThat(file).isFile();
        }

        Multimap<String, NativeArtifact> groupToArtifacts = ArrayListMultimap.create();

        for (NativeArtifact artifact : model.getArtifacts()) {
            List<String> pathElements = TestFileUtils.splitPath(artifact.getOutputFile());
            assertThat(pathElements).contains("obj");
            groupToArtifacts.put(artifact.getGroupName(), artifact);
        }

        assertThat(groupToArtifacts.keySet())
                .containsExactly(
                        "currentDebug",
                        "icecreamSandwichDebug",
                        "currentRelease",
                        "gingerbreadRelease",
                        "icecreamSandwichRelease",
                        "gingerbreadDebug");
        assertThat(groupToArtifacts.get("currentDebug"))
                .hasSize(groupToArtifacts.get("currentRelease").size());
    }
}
