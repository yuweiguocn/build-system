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

package com.android.build.gradle.integration.common.fixture.app

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_MIN_SDK

/** An empty subproject.  */
class MinimalSubProject private constructor(val plugin: String, val packageName: String?) :
    AbstractAndroidTestModule(),
    AndroidTestModule {

    init {
        var content = "\napply plugin: '$plugin'\n"
        if (plugin != "java-library" && plugin != "com.android.instantapp") {
            content += "\nandroid.compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}\n" +
                    "\nandroid.defaultConfig.minSdkVersion $SUPPORT_LIB_MIN_SDK\n"
            val manifest = TestSourceFile(
                "src/main/AndroidManifest.xml", """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                         xmlns:dist="http://schemas.android.com/apk/distribution"
                    package="${packageName!!}">
                    <application />
                </manifest>""".trimMargin()
            )
            if (plugin == "com.android.application") {
                content += "android.defaultConfig.versionCode 1\n";
            }
            addFiles(manifest)
        }
        val build = TestSourceFile("build.gradle", content)
        addFiles(build)
    }

    override fun containsFullBuildScript(): Boolean {
        return false
    }

    fun withFile(relativePath: String, content: ByteArray): MinimalSubProject {
        replaceFile(TestSourceFile(relativePath, content))
        return this
    }

    fun withFile(relativePath: String, content: String): MinimalSubProject {
        replaceFile(TestSourceFile(relativePath, content))
        return this
    }

    fun appendToBuild(snippet: String): MinimalSubProject {
        replaceFile(getFile("build.gradle").appendContent("\n" + snippet + "\n"))
        return this
    }

    companion object {

        fun lib(packageName: String): MinimalSubProject {
            return MinimalSubProject("com.android.library", packageName)
        }

        fun feature(packageName: String): MinimalSubProject {
            return MinimalSubProject("com.android.feature", packageName)
        }

        fun app(packageName: String): MinimalSubProject {
            return MinimalSubProject("com.android.application", packageName)
        }

        fun dynamicFeature(packageName: String): MinimalSubProject {
            return MinimalSubProject("com.android.dynamic-feature", packageName)
        }

        fun instantApp(): MinimalSubProject {
            return MinimalSubProject("com.android.instantapp", null)
        }

        fun test(packageName: String): MinimalSubProject {
            return MinimalSubProject("com.android.test", packageName)
        }

        fun javaLibrary(): MinimalSubProject {
            return MinimalSubProject("java-library", null)
        }
    }
}
