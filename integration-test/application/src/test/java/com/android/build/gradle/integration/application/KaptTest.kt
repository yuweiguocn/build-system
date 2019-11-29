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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.AnnotationProcessorLib
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.google.common.collect.ImmutableMap
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class KaptTest() {
    @JvmField @Rule
    var project: GradleTestProject

    val sApp = HelloWorldApp.noBuildFile()

    init {
        initApp()
        project = GradleTestProject.builder()
                .fromTestApp(MultiModuleTestProject(
                        ImmutableMap.of(
                                ":app", sApp,
                                ":lib", AnnotationProcessorLib.createLibrary(),
                                ":lib-compiler", AnnotationProcessorLib.createCompiler()
                        )))
                .withDependencyChecker(false)  // kotlin plugin is resolving kapt on configuration
                .create()
    }

    fun initApp() {
        sApp.replaceFile(TestSourceFile ("src/main/java/com/example/helloworld/HelloWorld.java",
                "package com.example.helloworld;\n" + "\n"
                        + "import android.app.Activity;\n"
                        + "import android.widget.TextView;\n"
                        + "import android.os.Bundle;\n"
                        + "import com.example.annotation.ProvideString;\n"
                        + "\n"
                        + "@ProvideString\n"
                        + "public class HelloWorld extends Activity {\n"
                        + "    /** Called when the activity is first created. */\n"
                        + "    @Override\n"
                        + "    public void onCreate(Bundle savedInstanceState) {\n"
                        + "        super.onCreate(savedInstanceState);\n"
                        + "        TextView tv = new TextView(this);\n"
                        + "        tv.setText(getString());\n"
                        + "        setContentView(tv);\n"
                        + "    }\n"
                        + "\n"
                        + "    public static String getString() {\n"
                        + "        return new com.example.helloworld.HelloWorldStringValue().value;\n"
                        + "    }\n"
                        + "\n"
                        + "    public static String getProcessor() {\n"
                        + "        return new com.example.helloworld.HelloWorldStringValue().processor;\n"
                        + "    }\n"
                        + "}\n"))
    }

    @Before
    @Throws(Exception::class)
    fun setUp() {
        val buildScript = """
apply from: "../../commonHeader.gradle"
buildscript {
    apply from: "../../commonBuildScript.gradle"
    dependencies {
        // Provides the 'android-kotlin' build plugin for the app
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:${"$"}rootProject.kotlinVersion"
    }
}
apply from: "../../commonLocalRepo.gradle"

apply plugin: 'com.android.application'
apply plugin: 'kotlin-android'
apply plugin: 'kotlin-kapt'

android {
    compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
    buildToolsVersion '${GradleTestProject.DEFAULT_BUILD_TOOL_VERSION}'
}

dependencies {
    implementation project(':lib')
    kapt project(':lib-compiler')
}
"""
project.getSubproject(":app").file("build.gradle").writeText(buildScript)
    }

    @Test
    fun checkIncrementalCompilation() {
        project.executor().run(":lib-compiler:jar", ":app:assembleDebug")
        val app = project.getSubproject(":app")
        val apk = app.getApk(GradleTestProject.ApkType.DEBUG)
        TruthHelper.assertThat(apk).containsClass("Lcom/example/helloworld/HelloWorldStringValue;")
        TruthHelper.assertThat(apk).containsClass("Lcom/example/helloworld/HelloWorld\$\$InnerClass;")

        // Modify the main file and rerun compilation. (b/65519025)
        TestFileUtils.addMethod(app.file("src/main/java/com/example/helloworld/HelloWorld.java"), "void foo() {}")
        project.executor().run(":app:assembleDebug")
        TruthHelper.assertThat(apk).containsClass("Lcom/example/helloworld/HelloWorldStringValue;")
        TruthHelper.assertThat(apk).containsClass("Lcom/example/helloworld/HelloWorld\$\$InnerClass;")
    }
}