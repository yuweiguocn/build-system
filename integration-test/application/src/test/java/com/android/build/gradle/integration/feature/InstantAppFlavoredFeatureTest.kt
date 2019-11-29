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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Rule
import org.junit.Test

class InstantAppFlavoredFeatureTest {

    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestProject("instantAppSimpleProject")
        .withoutNdk()
        .create()

    @Test
    @Throws(Exception::class)
    fun testBuild() {
        TestFileUtils.appendToFile(
            project.getSubproject(":feature").buildFile, ""
                    + "android { \n"
                    + "    flavorDimensions('demo')\n"
                    + "    productFlavors {\n"
                    + "        flavor1 {\n"
                    + "        }\n"
                    + "    }\n"
                    + "}\n"
        )
        project.execute("clean", "assembleDebug")
    }
}
