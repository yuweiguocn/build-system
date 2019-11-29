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

import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.SourceSetContainerUtils;
import com.android.build.gradle.integration.common.utils.SourceProviderHelper;
import com.android.builder.core.VariantType;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SourceProviderContainer;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for migrated. */
public class MigratedTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("migrated").create();

    @Test
    public void checkModelReflectsMigratedSourceProviders() throws Exception {
        AndroidProject model =
                project.executeAndReturnModel("clean", "assembleDebug").getOnlyModel();
        File projectDir = project.getTestDir();

        assertNotEquals(
                "Library Project", AndroidProject.PROJECT_TYPE_LIBRARY, model.getProjectType());
        assertEquals("Project Type", AndroidProject.PROJECT_TYPE_APP, model.getProjectType());

        ProductFlavorContainer defaultConfig = model.getDefaultConfig();

        new SourceProviderHelper(
                        model.getName(), projectDir, "main", defaultConfig.getSourceProvider())
                .setJavaDir("src")
                .setResourcesDir("src")
                .setAidlDir("src")
                .setRenderscriptDir("src")
                .setResDir("res")
                .setAssetsDir("assets")
                .setManifestFile("AndroidManifest.xml")
                .test();

        SourceProviderContainer testSourceProviderContainer =
                SourceSetContainerUtils.getExtraSourceProviderContainer(
                        defaultConfig, ARTIFACT_ANDROID_TEST);

        new SourceProviderHelper(
                        model.getName(),
                        projectDir,
                        VariantType.ANDROID_TEST_PREFIX,
                        testSourceProviderContainer.getSourceProvider())
                .setJavaDir("tests/java")
                .setResourcesDir("tests/resources")
                .setAidlDir("tests/aidl")
                .setJniDir("tests/jni")
                .setRenderscriptDir("tests/rs")
                .setResDir("tests/res")
                .setAssetsDir("tests/assets")
                .setManifestFile("tests/AndroidManifest.xml")
                .test();
    }
}
