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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.ModelContainer
import com.android.build.gradle.integration.common.utils.getVariantByName
import com.android.build.gradle.integration.common.utils.testSubModuleDependencies
import com.android.builder.model.AndroidProject
import com.android.builder.model.Dependencies.ProjectIdentifier
import com.android.builder.model.level2.GlobalLibraryMap
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import kotlin.test.fail

/**
 * Test the dependencies of a complex multi module/multi build setup with android modules
 * in the included build(s).
 *
 * The dependencies from the root app looks like this:
 * :app:debugCompileClasspath
 * +--- project :composite0
 * |    +--- com.test.composite:composite2:1.0 -> project :composite2
 * |    \--- com.test.composite:composite3:1.0 -> project :TestCompositeLib3:composite3
 * |         \--- com.test.composite:composite4:1.0 -> project :composite4
 * +--- com.test.composite:composite1:1.0 -> project :TestCompositeLib1:composite1
 * |    +--- com.test.composite:composite2:1.0 -> project :composite2
 * |    \--- com.test.composite:composite3:1.0 -> project :TestCompositeLib3:composite3
 * |         \--- com.test.composite:composite4:1.0 -> project :composite4
 * \--- com.test.composite:composite4:1.0 -> project :composite4
 *
 * The modules are of the following types:
 * TestCompositeLib1 :app        -> android app
 * TestCompositeLib1 :composite1 -> android lib
 * TestCompositeLib2 :           -> java
 * TestCompositeLib3 :app        -> android app
 * TestCompositeLib3 :composite3 -> android lib
 * TestCompositeLib4 :           -> java
 *
 */
@RunWith(Parameterized::class)
class MultiCompositeBuildTest {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "model_level_{0}")
        fun data(): Iterable<Any> {
            return listOf(
                    AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD,
                    AndroidProject.MODEL_LEVEL_4_NEW_DEP_MODEL)
        }
    }

    @JvmField
    @Parameterized.Parameter
    var modelLevel: Int = 0

    @JvmField
    @Rule
    val project = GradleTestProject.builder()
            .fromTestProject("multiCompositeBuild")
            .withIncludedBuilds(
                    "TestCompositeApp",
                    "TestCompositeLib1",
                    "TestCompositeLib3")
            .withoutNdk()
            .create()

    lateinit var modelContainer: ModelContainer<AndroidProject>

    // build identifiers for root and included builds
    lateinit var testCompositeApp: String
    lateinit var testCompositeLib1: String
    lateinit var testCompositeLib2: String
    lateinit var testCompositeLib3: String
    lateinit var testCompositeLib4: String

    @Before
    fun setup() {
        modelContainer = project.getSubproject("TestCompositeApp")
                .model()
                .level(modelLevel)
                .fetchAndroidProjects()

        val rootDir = project.testDir

        testCompositeApp = File(rootDir, "TestCompositeApp").absolutePath
        testCompositeLib1 = File(rootDir, "TestCompositeLib1").absolutePath
        testCompositeLib2 = File(rootDir, "TestCompositeLib2").absolutePath
        testCompositeLib3 = File(rootDir, "TestCompositeLib3").absolutePath
        testCompositeLib4 = File(rootDir, "TestCompositeLib4").absolutePath
    }

    @Test
    fun `dependencies for root app module`() {
        // get the root project, and its :app module
        val rootModelMap: Map<String, AndroidProject> = modelContainer.rootBuildModelMap

        assertThat(rootModelMap.entries).hasSize(2)
        assertThat(rootModelMap.keys).containsExactly(":app", ":composite0")

        val rootApp: AndroidProject = rootModelMap[":app"]!!

        rootApp.getVariantByName("debug").testSubModuleDependencies(
                moduleName = "<root>:app",
                expectedAndroidModules = listOf(
                        testCompositeApp to ":composite0",
                        testCompositeLib1 to ":composite1",
                        testCompositeLib3 to ":composite3"),
                expectedJavaModules = listOf(
                        testCompositeLib2 to ":",
                        testCompositeLib4 to ":"),
                globalLibrary = getGlobalLibrary())
    }

    @Test
    fun `dependencies for included build module`() {
        val modelMap = findModelMapByRootDir(testCompositeLib1)

        assertThat(modelMap.entries).hasSize(2)
        assertThat(modelMap.keys).containsExactly(":app", ":composite1")

        // test the composite1 module
        modelMap[":composite1"]!!.getVariantByName("debug").testSubModuleDependencies(
                moduleName = "TestCompositeLib1:composite1",
                expectedAndroidModules = listOf(testCompositeLib3 to ":composite3"),
                expectedJavaModules = listOf(
                        testCompositeLib2 to ":",
                        testCompositeLib4 to ":"),
                globalLibrary = getGlobalLibrary())

        // test the app module
        modelMap[":app"]!!.getVariantByName("debug").testSubModuleDependencies(
                moduleName = "TestCompositeLib1:app",
                expectedAndroidModules = listOf(
                        testCompositeLib1 to ":composite1",
                        testCompositeLib3 to ":composite3"),
                expectedJavaModules = listOf(
                        testCompositeLib2 to ":",
                        testCompositeLib4 to ":"),
                globalLibrary = getGlobalLibrary())
    }

    private fun findModelMapByRootDir(rootDir: String): Map<String, AndroidProject> {
        for ((buildId, map) in modelContainer.modelMaps) {
            if (buildId.rootDir.absolutePath == rootDir) {
                return map
            }
        }

        fail("Failed to find model map with rootDir=$rootDir")
    }

    private fun getGlobalLibrary() =
            if (modelLevel == AndroidProject.MODEL_LEVEL_4_NEW_DEP_MODEL)
                modelContainer.globalLibraryMap
            else
                null

}