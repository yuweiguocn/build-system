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

package com.android.build.gradle.integration.dexing

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class DexingArtifactTransformMultiModuleTest {

    private val app = MinimalSubProject.app("com.example.app")
    private val lib = MinimalSubProject.lib("com.example.lib")
    private val javaLib = MinimalSubProject.javaLibrary()

    @Rule
    @JvmField
    val project =
        GradleTestProject.builder().fromTestApp(
            MultiModuleTestProject.builder().subproject(":app", app)
                .subproject(":lib", lib)
                .subproject(":javaLib", javaLib)
                .dependency(app, lib)
                .dependency(app, javaLib)
                .build()
        ).create()

    @Test
    fun testMonoDex() {
        project.getSubproject("app")
            .buildFile.appendText("\nandroid.defaultConfig.multiDexEnabled = false")
        executor().run(":app:assembleDebug")

        project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG).use {
            assertThatApk(it).containsClass("Lcom/example/app/BuildConfig;")
            assertThatApk(it).containsClass("Lcom/example/app/R;")
            assertThatApk(it).containsClass("Lcom/example/lib/R;")
        }
    }

    @Test
    fun testAndroidLibrary() {
        project.getSubproject("lib").mainSrcDir.resolve("com/Data.java").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                package com;
                public class Data {}
            """.trimIndent()
            )
        }
        executor().run(":app:assembleDebug")

        project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG).use {
            assertThatApk(it).containsClass("Lcom/Data;")
        }
    }

    @Test
    fun testAndroidLibraryIncremental() {
        project.getSubproject("app").buildFile.appendText(
            """
            android.defaultConfig.multiDexEnabled = true
            android.defaultConfig.minSdkVersion = 21
        """.trimIndent()
        )
        project.getSubproject("lib").mainSrcDir.resolve("com/Data.java").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                package com;
                public class Data {
                }
            """.trimIndent()
            )
        }
        executor().run(":app:assembleDebug")

        TestFileUtils.addMethod(
            project.getSubproject("lib").mainSrcDir.resolve("com/Data.java"),
            "int i = 0;"
        )
        val result = executor().run(":app:assembleDebug")
        assertThat(result.upToDateTasks).containsAllOf(
            ":app:mergeProjectDexDebug",
            ":app:mergeExtDexDebug"
        )
        assertThat(result.didWorkTasks).contains(":app:mergeLibDexDebug")
    }

    @Test
    fun testJavaLib() {
        project.getSubproject("javaLib").mainSrcDir.resolve("com/Data.java").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                package com;
                public class Data { }
            """.trimIndent()
            )
        }
        executor().run(":app:assembleDebug")

        project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG).use {
            assertThatApk(it).containsClass("Lcom/Data;")
        }
    }

    @Test
    fun testJavaLibIncremental() {
        project.getSubproject("app").buildFile.appendText(
            """
            android.defaultConfig.multiDexEnabled = true
            android.defaultConfig.minSdkVersion = 21
        """.trimIndent()
        )
        project.getSubproject("javaLib").mainSrcDir.resolve("com/Data.java").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                package com;
                public class Data {
                }
            """.trimIndent()
            )
        }
        executor().run(":app:assembleDebug")

        TestFileUtils.addMethod(
            project.getSubproject("javaLib").mainSrcDir.resolve("com/Data.java"),
            "int i = 0;"
        )
        val result = executor().run(":app:assembleDebug")
        assertThat(result.upToDateTasks).containsAllOf(
            ":app:mergeProjectDexDebug",
            ":app:mergeExtDexDebug"
        )
        assertThat(result.didWorkTasks).contains(":app:mergeLibDexDebug")
    }

    @Test
    fun testLibraryAndExternalDeps() {
        project.getSubproject("app").buildFile.appendText(
            """
           dependencies {
               implementation 'com.google.guava:guava:19.0'
           }
        """.trimIndent()
        )
        project.getSubproject("lib").mainSrcDir.resolve("lib/Data.java").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                package lib;
                public class Data { }
            """.trimIndent()
            )
        }
        project.getSubproject("javaLib").mainSrcDir.resolve("javaLib/Data.java").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                package javaLib;
                public class Data { }
            """.trimIndent()
            )
        }
        executor().run(":app:assembleDebug")

        project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG).use {
            assertThatApk(it).containsClass("Llib/Data;")
            assertThatApk(it).containsClass("LjavaLib/Data;")
            assertThatApk(it).containsClass("Lcom/google/common/collect/ImmutableList;")
        }
    }

    @Test
    fun testLibraryAndroidTest() {
        project.getSubproject("lib").mainSrcDir.resolve("lib/Data.java").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                package lib;
                public class Data { }
            """.trimIndent()
            )
        }
        executor().run(":lib:assembleAndroidTest")
        val apk = project.getSubproject("lib").getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG)
        assertThatApk(apk).containsClass("Llib/Data;")
    }

    private fun executor() =
        project.executor().with(
            BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM,
            true
        )
}