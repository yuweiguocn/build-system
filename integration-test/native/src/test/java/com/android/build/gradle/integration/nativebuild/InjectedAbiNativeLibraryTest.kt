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

package com.android.build.gradle.integration.nativebuild

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.AndroidTestModule
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.options.StringOption
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Test injected ABI with ndk.abiFilters in a library project.
 */
class InjectedAbiNativeLibraryTest {

    val testapp = HelloWorldLibraryApp.create()
    @Rule @JvmField
    val project = GradleTestProject.builder().fromTestApp(testapp)
        .setCmakeVersion("3.10.4819442")
        .setWithCmakeDirInLocalProp(true)
        .create()

    init {
        val lib = testapp.getSubproject(":lib") as AndroidTestModule
        lib.addFile(HelloWorldJniApp.cmakeLists(""))
        lib.addFile(HelloWorldJniApp.cSource("src/main/cpp"))
    }

    @Before
    fun setUp() {
        project.getSubproject(":lib").buildFile.appendText(
                """
android {
    defaultConfig {
        ndk {
            abiFilters "x86"
        }
    }
    externalNativeBuild {
        cmake {
            path "CMakeLists.txt"
        }
    }
}
""")
    }

    @Test fun normalBuild() {
        project.executor()
                .with(StringOption.IDE_BUILD_TARGET_ABI, "x86")
                .run(":app:assembleDebug")
        assertThat(project.getSubproject(":app")
                .getApk(GradleTestProject.ApkType.DEBUG))
                .containsFile("lib/x86/libhello-jni.so")
    }

    @Test fun missingAbi() {
        project.executor()
                .with(StringOption.IDE_BUILD_TARGET_ABI, "armeabi-v7a")
                .run(":app:assembleDebug")
        assertThat(project.getSubproject(":app")
            .getApk(GradleTestProject.ApkType.DEBUG))
            .doesNotContainFile("lib/armeabi-v7a/libhello-jni.so")
    }
}
