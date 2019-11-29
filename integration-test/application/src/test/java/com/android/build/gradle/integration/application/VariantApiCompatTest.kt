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
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Rule
import org.junit.Test

/**
 * Tests various APIs on the Variant API interface.
 */
class VariantApiCompatTest {

    @get:Rule
    var project: GradleTestProject = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Test
    @Throws(Exception::class)
    fun testMappingFile() {
        val build = ("android {\n"
                + "    buildTypes {\n"
                + "        release {\n"
                + "            minifyEnabled true\n"
                + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'\n"
                + "        }\n"
                + "    }\n"
                + "    applicationVariants.all { variant ->\n"
                + "        if (variant.buildType.name == \"release\") {\n"
                + "            assert variant.mappingFile != null \n"
                + "        }"
                + "    }"
                + "}")

        TestFileUtils.appendToFile(
            project.buildFile,
            build
        )

        // this test only requires configuration.
        project.execute("tasks")
    }
}
