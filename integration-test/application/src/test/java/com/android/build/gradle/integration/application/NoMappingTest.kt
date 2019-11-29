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
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.FileSubject.assertThat
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test
import java.io.File

class NoMappingTest {
    @Rule
    @JvmField
    val project = GradleTestProject.builder().fromTestProject("minify").create()

    @Test
    fun checkEmptyMapping() {
        File(project.testDir, "proguard-rules.pro").appendText("\n-dontobfuscate")

        project.executor().with(BooleanOption.ENABLE_R8, true).run("assembleMinified")
        val mappingFile = project.file("build/outputs/mapping/minified/mapping.txt")
        assertThat(mappingFile).contains("com.android.tests.basic.Main -> com.android.tests.basic.Main")

        project.executor().with(BooleanOption.ENABLE_R8, false).run("assembleMinified")
        assertThat(mappingFile).hasContents("")
    }
}