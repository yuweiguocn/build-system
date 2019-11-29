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
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.FileSubject.assertThat
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test
import java.io.File

class ConditionalKeepRulesTest {
    @JvmField
    @Rule
    var project =
        GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create()

    @Test
    fun checkConditionalRulesGenerations() {
        project.buildFile.appendText("""
android {
  buildTypes {
    debug {
      minifyEnabled true
      proguardFiles getDefaultProguardFile('proguard-android.txt'),'proguard-rules.pro'
    }
  }
}""".trimMargin())

        project.testDir.resolve("proguard-rules.pro").printWriter().use {
            it.write("-keep class ***")
        }

        val layouts = FileUtils.join(project.mainSrcDir.parentFile, "res", "layout")
        FileUtils.writeToFile(
            File(layouts, "layoutone.xml"),
            """<?xml version="1.0" encoding="utf-8"?>
<com.custom.MyView1
    xmlns:android="http://schemas.android.com/apk/res/android" android:layout_width="match_parent"
    android:layout_height="match_parent">

</com.custom.MyView1>""")
        FileUtils.writeToFile(
            File(layouts, "layouttwo.xml"),
            """<?xml version="1.0" encoding="utf-8"?>
<com.custom.MyView2
    xmlns:android="http://schemas.android.com/apk/res/android" android:layout_width="match_parent"
    android:layout_height="match_parent">

    <include layout="@layout/layoutone"/>

</com.custom.MyView2>"""
        )

        // First make sure the conditional rules were generated if the flag is used.
        project.executor().with(BooleanOption.CONDITIONAL_KEEP_RULES, true).run("assembleDebug")
        var rules = project.getIntermediateFile("proguard-rules", "debug", "aapt_rules.txt")
        assertThat(rules).exists()
        assertThat(rules).contains("-if class **.R\$layout { int layouttwo; }")
        assertThat(rules).contains("-keep class com.custom.MyView2")

        // And also check that conditional rules are not generated when the flag is turned off.
        project.executor().with(BooleanOption.CONDITIONAL_KEEP_RULES, false).run("clean", "assembleDebug")
        rules = project.getIntermediateFile("proguard-rules", "debug", "aapt_rules.txt")
        assertThat(rules).exists()
        assertThat(rules).doesNotContain("-if class **.R\$layout { int layouttwo; }")
    }
}