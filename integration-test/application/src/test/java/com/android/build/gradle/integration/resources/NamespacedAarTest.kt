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

package com.android.build.gradle.integration.resources

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.utils.AssumeUtil
import com.android.build.gradle.integration.common.utils.getDebugVariant
import com.android.builder.model.AndroidProject
import com.android.testutils.truth.FileSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files

/**
 * Sanity tests for the new namespaced resource pipeline with publication and consumption of an aar.
 *
 * Project structured such that app an lib depend on an aar (flatdir) from publishedLib
 * </pre>
 */
class NamespacedAarTest {

    private val buildScriptContent = """
        android.aaptOptions.namespaced = true
    """

    val publishedLib = MinimalSubProject.lib("com.example.publishedLib")
            .appendToBuild(buildScriptContent)
            .withFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                        <string name="foo">publishedLib</string>
                        <string name="my_version_name">1.0</string>
                    </resources>""".trimMargin())
            .withFile(
                    "src/main/java/com/example/publishedLib/Example.java",
                    """package com.example.publishedLib;
                    public class Example {
                        public static int CONSTANT = 4;
                        public static int getFooString() { return R.string.foo; }
                    }""")
            .withFile(
                    "src/main/AndroidManifest.xml",
                    """
                                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                                         xmlns:dist="http://schemas.android.com/apk/distribution"
                                    package="com.example.publishedLib"
                                    android:versionName="@com.example.publishedLib:string/my_version_name">
                                </manifest>""")

    val lib = MinimalSubProject.lib("com.example.lib")
            .appendToBuild(
                    """$buildScriptContent
                    repositories { flatDir { dirs rootProject.file('publishedLib/build/outputs/aar/') } }
                    dependencies { implementation name: 'publishedLib-release', ext:'aar' }""")
            .withFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                        <string name="from_published_lib">@*com.example.publishedLib:string/foo</string>
                    </resources>""")
            .withFile(
                    "src/main/java/com/example/lib2/Example.java",
                    """package com.example.lib2;
                    public class Example {
                        public static int PUBLISHED_LIB_CONSTANT = com.example.publishedLib.Example.CONSTANT;
                        public static int FROM_PUBLISHED_LIB =
                                com.example.publishedLib.R.string.foo;
                    }
                    """)

    val app = MinimalSubProject.app("com.example.app")
            .appendToBuild(
                    """$buildScriptContent
                    repositories { flatDir { dirs rootProject.file('publishedLib/build/outputs/aar/') } }
                    dependencies { implementation name: 'publishedLib-release', ext:'aar' }""")
            .withFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                        <string name="mystring">My String</string>
                    </resources>""")
            .withFile(
                    "src/main/java/com/example/app/Example.java",
                    """package com.example.app;
                    public class Example {
                        public static int PUBLISHED_LIB_CONSTANT = com.example.publishedLib.Example.CONSTANT;
                        public static int FROM_PUBLISHED_LIB =
                                com.example.publishedLib.R.string.foo;
                        public static final int APP_STRING = R.string.mystring;
                        public static final int PUBLISHED_LIB_STRING =
                                com.example.publishedLib.R.string.foo;
                    }
                    """)

    val testApp =
            MultiModuleTestProject.builder()
                    .subproject(":publishedLib", publishedLib)
                    .subproject(":lib", lib)
                    .subproject(":app", app)
                    .build()

    @get:Rule val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun checkBuilds() {
        AssumeUtil.assumeNotWindowsBot() // https://issuetracker.google.com/70931936
        project.executor().run(":publishedLib:assembleRelease")
        project.executor().run(":lib:assembleDebug", ":app:assembleDebug")

        run {
            // Check model level 3
            val models =
                project.model().level(AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD)
                    .fetchAndroidProjects().onlyModelMap
            val libraries = models[":lib"]!!.getDebugVariant().mainArtifact.dependencies.libraries
            assertThat(libraries).hasSize(1)
            val lib = libraries.single()
            assertThat(lib.resStaticLibrary).exists()
        }

        run {
            // Check model level 4
            val models =
                project.model().level(AndroidProject.MODEL_LEVEL_LATEST).fetchAndroidProjects()
            val libraries =
                models.onlyModelMap[":lib"]!!.getDebugVariant().mainArtifact.dependencyGraphs.compileDependencies
            assertThat(libraries).hasSize(1)
            val lib = models.globalLibraryMap.libraries[libraries.single().artifactAddress]!!
            assertThat(lib.resStaticLibrary).exists()
        }

        // Check that the AndroidManifest.xml in the AAR does not contain namespaces.
        val aar = project.getSubproject("publishedLib").getAar("release")
        assertThat(aar.exists()).isTrue()
        val manifest = aar.getEntry("AndroidManifest.xml")!!
        Files.readAllLines(manifest).forEach {
            assertThat(it).doesNotContain("@com.example.publishedLib:string/my_version_name")
        }
        assertThat(Files.readAllLines(manifest).any { it.contains ("@string/my_version_name")})
            .isTrue()
        // TODO: use the full namespaced manifest when creating res.apk and test its contents.
    }
}
