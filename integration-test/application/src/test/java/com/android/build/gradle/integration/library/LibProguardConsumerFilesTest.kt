/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.library

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/** Check the merging of proguard files in AARs. */
class LibProguardConsumerFilesTest {

    @Rule @JvmField
    var project = GradleTestProject.builder().fromTestProject("libProguardConsumerFiles").create()

    @Test
    fun checkProguardDotTxtHasBeenCorrectlyMerged() {
        project.execute("assembleDebug", "assembleRelease")

        val debugProguardFile =
                project.getAar("debug").getEntry("proguard.txt")
        assertThat(nonEmptyLines(debugProguardFile!!)).containsExactly("A")

        val releaseProguardFile =
                project.getAar("release").getEntry("proguard.txt")
        assertThat(nonEmptyLines(releaseProguardFile!!)).containsExactly("A", "B", "C")
    }

    private fun nonEmptyLines(path: Path) =
            Files.readAllLines(path)
                    .map { it.trim() }
                    .filter { !it.isEmpty()}
                    .toList()
}
