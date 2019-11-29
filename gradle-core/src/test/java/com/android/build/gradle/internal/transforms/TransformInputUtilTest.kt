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

package com.android.build.gradle.internal.transforms

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TransformInputUtilTest {

    @JvmField
    @Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    @Test
    fun testDeletedNotReturned() {
        val rootPath = tmp.root
        val notExists1 =
                TransformTestHelper
                    .directoryBuilder(rootPath.resolve("not_exists1"))
                    .build()
        val notExists2 =
                TransformTestHelper
                    .directoryBuilder(rootPath.resolve("dir/not_exists2"))
                    .build()
        val notExists3 =
                TransformTestHelper
                    .singleJarBuilder(rootPath.resolve("not_exists3.jar"))
                    .build()
        val dir = rootPath.resolve("exists")
        dir.mkdirs()
        val exists1 = TransformTestHelper.directoryBuilder(dir).build()
        val jar = rootPath.resolve("exists.jar")
        jar.createNewFile()
        val exists2 = TransformTestHelper.singleJarBuilder(jar).build()

        val allFiles =
                TransformInputUtil.getAllFiles(
                        listOf(exists1, exists2, notExists1, notExists2, notExists3))
        assertThat(allFiles).containsExactly(dir, jar)
    }
}