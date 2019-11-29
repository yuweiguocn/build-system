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

import com.android.dex.Dex
import com.android.testutils.TestClassesGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.truth.MoreTruth.assertThatDex
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.pow

/** Tests for dexing artifact transform. */
class DexingTransformTest {

    @Rule
    @JvmField
    val tmp = TemporaryFolder()

    @Test
    fun testDexingJar() {
        val dexingTransform = DexingTransform(12, true)
        dexingTransform.outputDirectory = tmp.newFolder()

        val input = tmp.newFile("classes.jar")
        TestInputsGenerator.jarWithEmptyClasses(input.toPath(), listOf("test/A"))
        dexingTransform.transform(input)
        assertThatDex(dexingTransform.outputDirectory.resolve("classes.dex"))
            .containsExactlyClassesIn(listOf("Ltest/A;"))
    }

    @Test
    fun testDexingDir() {
        val dexingTransform = DexingTransform(12, true)
        dexingTransform.outputDirectory = tmp.newFolder()

        val input = tmp.newFolder("classes")
        TestInputsGenerator.dirWithEmptyClasses(input.toPath(), listOf("test/A"))
        dexingTransform.transform(input)
        assertThatDex(dexingTransform.outputDirectory.resolve("classes.dex"))
            .containsExactlyClassesIn(listOf("Ltest/A;"))
    }

    @Test
    fun testDexingBigJar() {
        val transform = DexingTransform(12, true)
        transform.outputDirectory = tmp.newFolder()

        val methodsPerClass = 200
        // more than 64K methods
        val totalMethods = (2.0.pow(16) + methodsPerClass).toInt()

        val input = tmp.newFile("classes.jar")
        ZipOutputStream(input.outputStream()).use {
            for (i in 0 until (totalMethods / methodsPerClass)) {
                val methodNames = (0 until methodsPerClass).map { "foo$it:()V" }
                val classContent = TestClassesGenerator.classWithEmptyMethods(
                    "test/A$i",
                    *methodNames.toTypedArray()
                )

                it.putNextEntry(ZipEntry("test/A$i.class"))
                it.write(classContent)
                it.closeEntry()
            }
        }
        transform.transform(input)

        assertThat(
            Dex(transform.outputDirectory.resolve("classes.dex")).classDefs().count() +
                    Dex(transform.outputDirectory.resolve("classes2.dex")).classDefs().count()
        ).isEqualTo(totalMethods / methodsPerClass)
    }
}