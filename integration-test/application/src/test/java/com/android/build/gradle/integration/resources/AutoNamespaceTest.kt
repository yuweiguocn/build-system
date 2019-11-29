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

package com.android.build.gradle.integration.resources

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.common.utils.getDebugGenerateSourcesCommands
import com.android.build.gradle.integration.common.utils.getDebugVariant
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.AndroidProject
import com.android.testutils.truth.FileSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class AutoNamespaceTest {
    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("namespacedApp")
        .create()

    @Test
    fun rewriteJavaBytecodeRClassesAndResources() {

        // Check model level 3
        val modelContainer =
            project.model().level(AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD)
                .with(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES, true)
                .fetchAndroidProjects()
        val model = modelContainer.onlyModel

        val libraries = model.getDebugVariant().mainArtifact.dependencies.libraries
        libraries.forEach { lib ->
            // Not auto namespaced yet
            assertThat(lib.resStaticLibrary).doesNotExist()
        }

        project.executor()
            .with(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES, true)
            .run(modelContainer.getDebugGenerateSourcesCommands())

        libraries.forEach { lib ->
            assertThat(lib.resStaticLibrary).exists()
        }

        project.executor()
            .with(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES, true)
            .run("assembleDebug")

        // TODO(b/110879504): level 4

        assertThat(
                FileUtils.join(
                        project.intermediatesDir,
                        "namespaced_classes_jar",
                        "debug",
                        "autoNamespaceDebugDependencies",
                        "namespaced-classes.jar"))
            .exists()

        assertThat(
                FileUtils.join(
                        project.intermediatesDir,
                        "compile_only_namespaced_dependencies_r_jar",
                        "debug",
                        "autoNamespaceDebugDependencies",
                        "namespaced-R.jar"))
            .exists()

        TestFileUtils.searchAndReplace(
                FileUtils.join(project.mainSrcDir, "com", "example", "namespacedApp", "Test.java"),
                "layout_constraintBaseline_creator",
                "layout_constraintBaseline_toBaselineOf"
        )

        val result = project.executor()
                .with(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES, true)
                .run("assembleDebug")

        TruthHelper.assertThat(result.getTask(":autoNamespaceDebugDependencies")).wasUpToDate();
        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        assertThat(apk).containsClass("Landroid/support/constraint/Guideline;")
        assertThat(apk).containsClass("Landroid/support/constraint/R\$attr;")
    }

    @Test
    fun incorrectReferencesAreStillInvalid() {
        TestFileUtils.searchRegexAndReplace(
                FileUtils.join(project.mainSrcDir, "com", "example", "namespacedApp", "Test.java"),
                "int resRef = .*;",
                "int resRef = android.support.constraint.R.attr.invalid_reference;"
        )

        val result = project.executor()
            .with(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES, true)
            .expectFailure()
            .run("assembleDebug")

        Truth.assertThat(result.stderr).contains("error: cannot find symbol")
        Truth.assertThat(result.stderr)
                .contains("int resRef = android.support.constraint.R.attr.invalid_reference;")
    }
}
