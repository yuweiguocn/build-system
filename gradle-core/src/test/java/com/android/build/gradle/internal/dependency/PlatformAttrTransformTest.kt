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

package com.android.build.gradle.internal.dependency

import com.android.testutils.TestResources
import com.google.common.io.Files
import com.google.common.truth.Truth
import org.junit.Test
import java.io.File

class PlatformAttrTransformTest {

    @Test
    fun testExtraction() {
        val inputJar = TestResources.getFile(
                PlatformAttrTransformTest::class.java, "PlatformAttrTransform.jar")

        val outputDir = Files.createTempDir()

        val transform = PlatformAttrTransform()
        transform.outputDirectory = outputDir
        val outputFiles = transform.transform(inputJar)


        val lines = Files.readLines(outputFiles.single(), Charsets.UTF_8)

        Truth.assertThat(lines).containsExactly("int attr one 0x00000001", "int attr two 0x00000002")
    }
}