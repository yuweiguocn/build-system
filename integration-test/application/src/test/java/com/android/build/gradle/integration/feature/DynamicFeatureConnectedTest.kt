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

package com.android.build.gradle.integration.feature

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.Adb
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.TEST_SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(DeviceTests::class)
class DynamicFeatureConnectedTest {

    private val build = MultiModuleTestProject.builder().apply {
        val app = MinimalSubProject.app("com.example.app")
            .appendToBuild(
                """android.dynamicFeatures = [':dynamicFeature']
                            |android.defaultConfig.versionCode = 4""".trimMargin()
            )
            .withFile(
                "src/main/java/com/example/app/MyProductionClass.java",
                """package com.example.app;
                    |
                    |public class MyProductionClass {
                    |    public static int getThree() {
                    |        return 3;
                    |    };
                    |}""".trimMargin())
            .withFile(
                "src/main/res/values/strings.xml", """
                    |<resources>
                    |    <string name="df_title">Dynamic Feature Title</string>
                    |    <string name="app_title">App Title</string>
                    |</resources>
                """.trimMargin())
            .apply { replaceFile(TestSourceFile("src/main/AndroidManifest.xml",
                """<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    |        xmlns:dist="http://schemas.android.com/apk/distribution"
                    |        package="com.example.app">
                    |    <dist:module dist:title="@string/app_title">
                    |    </dist:module>
                    |    <application />
                    |</manifest>""".trimMargin()))}

        val dynamicFeature = MinimalSubProject.dynamicFeature("com.example.app.dynamic.feature")
            .appendToBuild("android.defaultConfig.testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'")
            .apply { replaceFile(TestSourceFile("src/main/AndroidManifest.xml",
                """<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    |        xmlns:dist="http://schemas.android.com/apk/distribution"
                    |        package="com.example.app.dynamic.feature">
                    |    <dist:module dist:onDemand="true" dist:title="@string/df_title">
                    |        <dist:fusing dist:include="true" />
                    |    </dist:module>
                    |    <application />
                    |</manifest>""".trimMargin()))}
            .withFile("src/main/java/com/example/dynamic/feature/FeatureProductionClass.java",
                """package com.example.dynamic.feature;
                    |
                    |public class FeatureProductionClass {
                    |    public static int getFour() { return 4; }
                    |}""".trimMargin())
            .withFile(
                "src/androidTest/java/com/example/dynamic/feature/test/MyTest.java",
                """package com.example.dynamic.feature.test;
                    |
                    |import android.support.test.runner.AndroidJUnit4;
                    |import com.example.app.MyProductionClass;
                    |import com.example.dynamic.feature.FeatureProductionClass;
                    |import org.junit.Assert;
                    |import org.junit.Test;
                    |import org.junit.internal.runners.JUnit4ClassRunner;
                    |import org.junit.runner.RunWith;
                    |import org.junit.runners.BlockJUnit4ClassRunner;
                    |
                    |@RunWith(AndroidJUnit4.class)
                    |public class MyTest {
                    |    @Test
                    |    public void useBaseClass() {
                    |        // Check both compiles and runs against a production class in
                    |        // the base feature
                    |        Assert.assertEquals(3, MyProductionClass.getThree());
                    |    }
                    |
                    |    @Test
                    |    public void useFeatureClass() {
                    |        // Check both compiles and runs against a production class in
                    |        // this dynamic feature
                    |        Assert.assertEquals(4, FeatureProductionClass.getFour());
                    |    }
                    |}
                """.trimMargin())

        subproject(":app", app)
        subproject(":dynamicFeature", dynamicFeature)
        dependency(dynamicFeature, app)
        androidTestDependency(
            dynamicFeature,
            "com.android.support.test:runner:$TEST_SUPPORT_LIB_VERSION"
        )
        androidTestDependency(
            dynamicFeature,
            "com.android.support.test:rules:$TEST_SUPPORT_LIB_VERSION"
        )
        androidTestDependency(
            dynamicFeature,
            "com.android.support:support-annotations:$SUPPORT_LIB_VERSION"
        )
    }
        .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(build).create()

    @get:Rule
    val adb = Adb()

    @Test
    fun runTestInDynamicFeature() {
        adb.exclusiveAccess()
        project.executor().run(":app:uninstallDebug")
        project.executor().run(":dynamicFeature:connectedAndroidTest")
        project.executor().run(":app:uninstallDebug")
    }
}