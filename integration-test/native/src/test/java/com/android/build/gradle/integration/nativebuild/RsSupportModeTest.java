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
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.Library;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for rsSupportMode. */
public class RsSupportModeTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("rsSupportMode")
                    .setCmakeVersion("3.10.4819442")
                    .setWithCmakeDirInLocalProp(true)
                    .create();

    private static ModelContainer<AndroidProject> model;

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        model =
                project.executeAndReturnModel(
                        "clean", "assembleDebug", "assembleX86DebugAndroidTest");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void testRsSupportMode() throws Exception {
        LibraryGraphHelper helper = new LibraryGraphHelper(model);

        Variant debugVariant =
                AndroidProjectUtils.getVariantByName(model.getOnlyModel(), "x86Debug");

        AndroidArtifact mainArtifact = debugVariant.getMainArtifact();

        DependencyGraphs graph = mainArtifact.getDependencyGraphs();

        List<Library> libraries = helper.on(graph).withType(JAVA).asLibraries();
        assertThat(libraries).isNotEmpty();

        boolean foundSupportJar = false;
        for (Library lib : libraries) {
            File file = lib.getArtifact();
            if (SdkConstants.FN_RENDERSCRIPT_V8_JAR.equals(file.getName())) {
                foundSupportJar = true;
                break;
            }
        }

        assertThat(foundSupportJar).isTrue();
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "x86"))
                .containsClass("Landroid/support/v8/renderscript/RenderScript;");
    }
}
