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
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test

/**
 *                                +-> lib1
 *                                |
 * app --------+----> feature1 ---+-> javalib1
 *             |                  |
 *             +------------------+-> baseModule
 *             |
 *             |
 * instantApp -+
 */


private const val BASE_BUILD_GRADLE =
    """
android {
    baseFeature true
    buildTypes {
        minified.initWith(buildTypes.debug)
        minified {
            minifyEnabled true
            useProguard true
            proguardFiles getDefaultProguardFile('proguard-android.txt')
            consumerProguardFiles "proguard-rules.pro"
        }
    }
}
"""

private const val FEATURE1_BUILD_GRADLE =
"""
android {
    buildTypes {
        minified.initWith(buildTypes.debug)
        minified {
            consumerProguardFiles "proguard-rules.pro"
        }
    }
}
"""

private const val APP_BUILD_GRADLE =
    """
android {
    buildTypes {
        minified.initWith(buildTypes.debug)
        minified {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android.txt')
        }
    }
}
"""

class RulesExtractionInstantAppTest {

    private val lib1 = testLib(1)
    private val javalib1 = testJavalib(1)

    private val feature1 = MinimalSubProject.feature("com.example.feature1")
                .appendToBuild(FEATURE1_BUILD_GRADLE)
                .withFile(
                    "src/main/java/com/example/feature1/Feature1ClassToKeep.java",
                    FEATURE1_CLASS_KEEP)
                .withFile(
                    "src/main/java/com/example/feature1/Feature1ClassToRemove.java",
                    FEATURE_CLASS_REMOVE)
                .withFile(
                    "proguard-rules.pro",
                    "-keep public class com.example.feature1.Feature1ClassToKeep")

    private val baseModule = MinimalSubProject.feature("com.example.base")
        .appendToBuild(BASE_BUILD_GRADLE)
        .withFile(
            "src/main/java/com/example/baseModule/BaseClassToKeep.java",
            BASE_CLASS_KEEP)
        .withFile(
            "src/main/java/com/example/baseModule/BaseClassToRemove.java",
            BASE_CLASS_REMOVE)
        .withFile(
            "proguard-rules.pro",
            "-keep public class com.example.baseModule.BaseClassToKeep")

    private val app =
        MinimalSubProject.app("com.example.app")
            .appendToBuild(APP_BUILD_GRADLE)

    private val instantApp = MinimalSubProject.instantApp()

    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":lib1", lib1)
            .subproject(":javalib1", javalib1)
            .subproject(":baseModule", baseModule)
            .subproject(":foo:feature1", feature1)
            .dependency(feature1, lib1)
            .dependency(feature1, javalib1)
            .dependency(feature1, baseModule)
            .subproject(":app", app)
            .subproject(":instantApp", instantApp)
            .dependency(app, baseModule)
            .dependency(app, feature1)
            .dependency(instantApp, baseModule)
            .dependency(instantApp, feature1)
            .dependency("application", baseModule, app)
            .dependency("feature", baseModule, feature1)
            .build()

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(testApp)
            .create()

    @Test
    fun testRightClassesAreKept() {

        project.executor().run("assembleMinified")

        val baseModuleApk = project.getSubproject("baseModule").getFeatureApk(APK_TYPE)
        assertThat(baseModuleApk).containsClass("Lcom/example/baseModule/BaseClassToKeep;")
        assertThat(baseModuleApk).doesNotContainClass("Lcom/example/lib1/Lib1ClassToKeep;")
        assertThat(baseModuleApk).doesNotContainClass("Lcom/example/feature1/Feature1ClassToKeep;")
        assertThat(baseModuleApk).doesNotContainClass("Lcom/example/javalib1/Javalib1ClassToKeep;")
        assertThat(baseModuleApk).doesNotContainClass("Lcom/example/baseModule/baseClassToRemove;")
        assertThat(baseModuleApk).doesNotContainClass("Lcom/example/lib1/Lib1ClassToRemove;")
        assertThat(baseModuleApk).doesNotContainClass("Lcom/example/javalib1/Javalib1ClassToremove;")
        assertThat(baseModuleApk).doesNotContainClass("Lcom/example/feature1/Feature1ClassToRemove;")

        val feature1Apk = project.getSubproject(":foo:feature1").getFeatureApk(APK_TYPE)
        assertThat(feature1Apk).containsClass("Lcom/example/feature1/Feature1ClassToKeep;")
        assertThat(feature1Apk).containsClass("Lcom/example/lib1/Lib1ClassToKeep;")
        assertThat(feature1Apk).containsClass("Lcom/example/javalib1/Javalib1ClassToKeep;")
        assertThat(feature1Apk).doesNotContainClass("Lcom/example/feature1/Feature1ClassToRemove;")
        assertThat(feature1Apk).doesNotContainClass("Lcom/example/lib1/Lib1ClassToRemove;")
        assertThat(feature1Apk).doesNotContainClass("Lcom/example/javalib1/Javalib1ClassToremove;")

        val appApk = project.getSubproject("app").getApk(APK_TYPE)
        assertThat(appApk).containsClass("Lcom/example/baseModule/BaseClassToKeep;")
        assertThat(appApk).containsClass("Lcom/example/lib1/Lib1ClassToKeep;")
        assertThat(appApk).containsClass("Lcom/example/feature1/Feature1ClassToKeep;")
        assertThat(appApk).containsClass("Lcom/example/javalib1/Javalib1ClassToKeep;")
        assertThat(appApk).doesNotContainClass("Lcom/example/baseModule/baseClassToRemove;")
        assertThat(appApk).doesNotContainClass("Lcom/example/lib1/Lib1ClassToRemove;")
        assertThat(appApk).doesNotContainClass("Lcom/example/javalib1/Javalib1ClassToremove;")
        assertThat(appApk).doesNotContainClass("Lcom/example/feature1/Feature1ClassToRemove;")
    }
}
