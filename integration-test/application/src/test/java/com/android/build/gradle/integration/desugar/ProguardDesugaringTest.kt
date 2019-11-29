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

package com.android.build.gradle.integration.desugar

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import org.junit.Rule
import org.junit.Test

/**
 * A basic integration test to make sure we can dex code desugared with Proguard.
 */
class ProguardDesugaringTest {
    @JvmField
    @Rule
    var project =
        GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create()

    @Test
    fun testDesugarWithProguard() {
        desugarWithProguard()
        project.mainSrcDir.resolve("com/example/helloworld/Data.java").printWriter().use {
            it.write(
                """
                    |package com.example.helloworld;
                    |class Data {
                    |  public void lambdaMethod() {
                    |    Runnable r = () -> {};
                    |    Iface.foo();
                    |    new Iface(){}.baz();
                    |  }
                    |  interface Iface {
                    |    static void foo() {}
                    |    default void baz() {}
                    |  }
                    |}""".trimMargin()
            )
        }

        project.executor().run("assembleDebug")
        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        assertThatApk(apk).containsClass("Lcom/example/helloworld/Data;")
        assertThat(apk).hasDexVersion(35)
    }

    private fun desugarWithProguard() {
        project.buildFile.appendText("""
            |android {
            |  compileOptions {
            |    sourceCompatibility 1.8
            |    targetCompatibility 1.8
            |  }
            |  buildTypes {
            |    debug {
            |      minifyEnabled true
            |      proguardFiles getDefaultProguardFile('proguard-android.txt'),'proguard-rules.pro'
            |    }
            |  }
            |}""".trimMargin())

        project.testDir.resolve("proguard-rules.pro").printWriter().use {
            it.write("""
                |-target 7
                |-keep class ***""".trimMargin())
        }
    }
}
