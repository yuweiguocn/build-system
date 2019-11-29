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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.fixtures.FakeArtifactCollection
import com.android.build.gradle.internal.fixtures.FakeComponentIdentifier
import com.android.build.gradle.internal.fixtures.FakeResolvedArtifactResult
import com.android.ide.common.workers.ExecutorServiceAdapter
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.testutils.TestInputsGenerator
import com.google.common.truth.Truth
import com.google.common.util.concurrent.MoreExecutors
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertFailsWith


private const val RECOMMENDATION =
    "Go to the documentation to learn how to <a href=\"d.android.com/r/tools/classpath-sync-errors\">Fix dependency resolution errors</a>."

class CheckDuplicateClassesDelegateTest {
    @JvmField
    @Rule
    val tmp = TemporaryFolder()
    lateinit var workers: WorkerExecutorFacade

    val lineSeparator: String = System.lineSeparator()

    @Before
    fun setUp() {
        workers = ExecutorServiceAdapter(MoreExecutors.newDirectExecutorService())
    }

    @Test
    fun testNoArtifacts() {
        val classesArtifacts = FakeArtifactCollection(mutableSetOf())

        // Nothing should happen, no fails
        CheckDuplicateClassesDelegate(classesArtifacts).run(workers)
    }

    @Test
    fun testSingleArtifacts() {

        val jar = tmp.root.toPath().resolve("jar.jar")
        TestInputsGenerator.jarWithEmptyClasses(jar, listOf("test/A"))

        val classesArtifacts = FakeArtifactCollection(mutableSetOf(
            FakeResolvedArtifactResult(jar.toFile(), FakeComponentIdentifier("foo"))
        ))

        // Nothing should happen, no fails
        CheckDuplicateClassesDelegate(classesArtifacts).run(workers)
    }

    @Test
    fun test2Artifacts_noDuplicates() {

        val jar1 = tmp.root.toPath().resolve("jar1.jar")
        TestInputsGenerator.jarWithEmptyClasses(jar1, listOf("test/A"))

        val jar2 = tmp.root.toPath().resolve("jar2.jar")
        TestInputsGenerator.jarWithEmptyClasses(jar2, listOf("test/B"))

        val classesArtifacts = FakeArtifactCollection(mutableSetOf(
            FakeResolvedArtifactResult(jar1.toFile(), FakeComponentIdentifier("foo")),
            FakeResolvedArtifactResult(jar2.toFile(), FakeComponentIdentifier("bar"))
        ))

        // Nothing should happen, no fails
        CheckDuplicateClassesDelegate(classesArtifacts).run(workers)
    }

    @Test
    fun test2Artifacts_withDuplicates() {

        val jar1 = tmp.root.toPath().resolve("jar1.jar")
        TestInputsGenerator.jarWithEmptyClasses(jar1, listOf("test/A"))

        val jar2 = tmp.root.toPath().resolve("jar2.jar")
        TestInputsGenerator.jarWithEmptyClasses(jar2, listOf("test/A"))

        val classesArtifacts = FakeArtifactCollection(mutableSetOf(
            FakeResolvedArtifactResult(jar1.toFile(), FakeComponentIdentifier("identifier1")),
            FakeResolvedArtifactResult(jar2.toFile(), FakeComponentIdentifier("identifier2"))
        ))

        val exception = assertFailsWith(RuntimeException::class) {
            CheckDuplicateClassesDelegate(classesArtifacts).run(workers)
        }

        Truth.assertThat(exception.message)
            .contains(
                "Duplicate class test.A found in modules identifier1 and identifier2$lineSeparator$lineSeparator$RECOMMENDATION")
    }

    @Test
    fun test2Artifacts_with2Duplicates() {

        val jar1 = tmp.root.toPath().resolve("jar1.jar")
        TestInputsGenerator.jarWithEmptyClasses(jar1, listOf("test/A", "test/B"))

        val jar2 = tmp.root.toPath().resolve("jar2.jar")
        TestInputsGenerator.jarWithEmptyClasses(jar2, listOf("test/A", "test/B"))

        val classesArtifacts = FakeArtifactCollection(mutableSetOf(
            FakeResolvedArtifactResult(jar1.toFile(), FakeComponentIdentifier("identifier1")),
            FakeResolvedArtifactResult(jar2.toFile(), FakeComponentIdentifier("identifier2"))
        ))

        val exception = assertFailsWith(RuntimeException::class) {
            CheckDuplicateClassesDelegate(classesArtifacts).run(workers)
        }

        Truth.assertThat(exception.message)
            .contains(
                "Duplicate class test.A found in modules identifier1 and identifier2$lineSeparator" +
                        "Duplicate class test.B found in modules identifier1 and identifier2$lineSeparator" +
                        "$lineSeparator$RECOMMENDATION")
    }

    @Test
    fun test3Artifacts_2ofWhichHasDuplicates() {

        val jar1 = tmp.root.toPath().resolve("jar1.jar")
        TestInputsGenerator.jarWithEmptyClasses(jar1, listOf("test/A"))

        val jar2 = tmp.root.toPath().resolve("jar2.jar")
        TestInputsGenerator.jarWithEmptyClasses(jar2, listOf("test/A"))

        val jar3 = tmp.root.toPath().resolve("jar3.jar")
        TestInputsGenerator.jarWithEmptyClasses(jar3, listOf("test/B"))

        val classesArtifacts = FakeArtifactCollection(mutableSetOf(
            FakeResolvedArtifactResult(jar1.toFile(), FakeComponentIdentifier("identifier1")),
            FakeResolvedArtifactResult(jar2.toFile(), FakeComponentIdentifier("identifier2")),
            FakeResolvedArtifactResult(jar3.toFile(), FakeComponentIdentifier("identifier3"))
        ))

        val exception = assertFailsWith(RuntimeException::class) {
            CheckDuplicateClassesDelegate(classesArtifacts).run(workers)
        }

        Truth.assertThat(exception.message)
            .contains(
                "Duplicate class test.A found in modules identifier1 and identifier2$lineSeparator$lineSeparator$RECOMMENDATION")
    }

    @Test
    fun test3Artifacts_withDuplicates() {

        val jar1 = tmp.root.toPath().resolve("jar1.jar")
        TestInputsGenerator.jarWithEmptyClasses(jar1, listOf("test/A"))

        val jar2 = tmp.root.toPath().resolve("jar2.jar")
        TestInputsGenerator.jarWithEmptyClasses(jar2, listOf("test/A"))

        val jar3 = tmp.root.toPath().resolve("jar3.jar")
        TestInputsGenerator.jarWithEmptyClasses(jar3, listOf("test/A"))

        val classesArtifacts = FakeArtifactCollection(mutableSetOf(
            FakeResolvedArtifactResult(jar1.toFile(), FakeComponentIdentifier("identifier1")),
            FakeResolvedArtifactResult(jar2.toFile(), FakeComponentIdentifier("identifier2")),
            FakeResolvedArtifactResult(jar3.toFile(), FakeComponentIdentifier("identifier3"))
        ))

        val exception = assertFailsWith(RuntimeException::class) {
            CheckDuplicateClassesDelegate(classesArtifacts).run(workers)
        }

        Truth.assertThat(exception.message)
            .contains(
                "Duplicate class test.A found in the following modules: identifier1, identifier2 and identifier3$lineSeparator$lineSeparator$RECOMMENDATION")
    }
}