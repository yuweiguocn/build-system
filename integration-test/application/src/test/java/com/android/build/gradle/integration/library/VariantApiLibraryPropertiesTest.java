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

package com.android.build.gradle.integration.library;

import static com.android.build.gradle.integration.common.truth.AarSubject.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;

/** Test property values in Variant API for library plugin. */
public class VariantApiLibraryPropertiesTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.library"))
                    .create();

    @Test
    public void checkOutputFileName() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    libraryVariants.all { variant ->\n"
                        + "        if (variant.name == 'debug') {\n"
                        + "            assert variant.outputs.first().outputFileName == 'project-debug.aar'\n"
                        + "            def outputFileName = variant.outputs.first().outputFileName\n"
                        + "            def variantOutput = variant.outputs.first()\n"
                        + "            variantOutput.outputFileName = outputFileName.replace('project', \"project-1.0\")\n"
                        + "            assert variantOutput.outputFile == project.file(\"build/outputs/aar/${variantOutput.outputFileName}\")\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        project.executor().run("assembleDebug");
        assertThat(project.getAar("debug").getFile()).doesNotExist();
        assertThat(project.getAar("1.0", "debug")).exists();
    }
}
