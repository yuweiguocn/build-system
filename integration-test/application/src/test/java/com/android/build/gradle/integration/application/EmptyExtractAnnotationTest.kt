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
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Integration test for extracting annotations with no sources.
 */
class EmptyExtractAnnotationTest {

    private val emptylib = MinimalSubProject.lib("com.example.lib")
        // Disable BuildConfig generation to make the library truly empty.
        .appendToBuild("android.libraryVariants.all { it.generateBuildConfig.enabled = false }")

    @get:Rule
    var project = GradleTestProject.builder().fromTestApp(emptylib).create()

    @Test
    fun checkExtractAnnotation() {
        project.execute("assembleRelease")
        val aar = project.getAar("release")
        assertThat(aar).contains("classes.jar")
    }
}
