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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;

/** Test property values in Variant API. */
public class VariantApiPropertiesTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Test
    public void checkMergedJavaCompileOptions() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    buildTypes {\n"
                        + "        debug {\n"
                        + "            javaCompileOptions.annotationProcessorOptions {\n"
                        + "                className 'Foo'\n"
                        + "                argument 'value', 'debugArg'\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "    flavorDimensions 'dimension'\n"
                        + "    productFlavors {\n"
                        + "        flavor1 {\n"
                        + "            javaCompileOptions.annotationProcessorOptions {\n"
                        + "                className 'Bar'\n"
                        + "                argument 'value', 'flavor1Arg'\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "    applicationVariants.all { variant ->\n"
                        + "        def options = variant.javaCompileOptions.annotationProcessorOptions\n"
                        + "        if (variant.name == 'flavor1Debug') {\n"
                        + "            assert options.classNames == ['Bar', 'Foo']\n"
                        + "            assert options.arguments == ['value': 'debugArg']\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        project.executor().run("help");
    }

    @Test
    public void checkOutputFileName() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    flavorDimensions 'dimension'\n"
                        + "    productFlavors {\n"
                        + "        flavor1 {\n"
                        + "        }\n"
                        + "    }\n"
                        + "    applicationVariants.all { variant ->\n"
                        + "        if (variant.name == 'flavor1Debug') {\n"
                        + "            assert variant.outputs.first().outputFileName == 'project-flavor1-debug.apk'\n"
                        + "            def outputFileName = variant.outputs.first().outputFileName\n"
                        + "            def variantOutput = variant.outputs.first()\n"
                        + "            variantOutput.outputFileName = outputFileName.replace('flavor1', \"flavor1-${variant.versionName}\")\n"
                        + "            assert variantOutput.outputFile == project.file(\"build/outputs/apk/flavor1/debug/${variantOutput.outputFileName}\")\n"
                        + "        }\n"
                        + "        if (variant.name == 'flavor1Release') {\n"
                        + "            assert variant.outputs.first().outputFileName == 'project-flavor1-release-unsigned.apk'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        project.executor().run("assembleFlavor1Debug");
        assertThat(project.getApk(ApkType.DEBUG, "flavor1")).doesNotExist();
        assertThat(project.getApk("1.0", ApkType.DEBUG, "flavor1")).exists();
    }
}
