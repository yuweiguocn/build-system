/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.TransformException
import com.android.builder.dexing.ERROR_DUPLICATE
import com.android.builder.dexing.ERROR_DUPLICATE_HELP_PAGE
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.google.common.collect.Iterators
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier
import kotlin.test.assertFailsWith

/**
 * Test for calculating the main dex list using D8.
 */
class D8MainDexListTransformTest {

    @Rule @JvmField
    val tmpDir: TemporaryFolder = TemporaryFolder()

    @Test
    fun testProguardRules() {

        val output = tmpDir.newFile().toPath()

        val inputJar = tmpDir.root.toPath().resolve("input.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar, listOf("test/A"))

        val proguardRules = tmpDir.root.toPath().resolve("proguard_rules")
        Files.write(proguardRules, listOf("-keep class test.A"))

        val input = TransformTestHelper.singleJarBuilder(inputJar.toFile())
                .setScopes(QualifiedContent.Scope.PROJECT)
                .build()
        val invocation = TransformTestHelper.invocationBuilder().addReferenceInput(input).build()

        val transform =
                D8MainDexListTransform(
                        manifestProguardRules = proguardRules.stubBuildableArtifact(),
                        bootClasspath = Supplier { getBootClasspath() },
                        messageReceiver = NoOpMessageReceiver())
        transform.setMainDexListOutputFile(output.toFile())
        transform.transform(invocation)

        Truth.assertThat(Files.readAllLines(output)).containsExactly("test/A.class")
    }

    @Test
    fun testUserProguardRules() {
        val output = tmpDir.newFile().toPath()

        val inputJar = tmpDir.root.toPath().resolve("input.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar, listOf("test/A"))

        val userProguardRules = tmpDir.root.toPath().resolve("user_proguard_rules")
        Files.write(userProguardRules, listOf("-keep class test.A"))

        val input = TransformTestHelper.singleJarBuilder(inputJar.toFile())
                .setScopes(QualifiedContent.Scope.PROJECT)
                .build()
        val invocation = TransformTestHelper.invocationBuilder().addReferenceInput(input).build()

        val transform =
                D8MainDexListTransform(
                        manifestProguardRules = tmpDir.newFile().toPath().stubBuildableArtifact(),
                        userProguardRules = userProguardRules,
                        bootClasspath = Supplier { getBootClasspath() },
                        messageReceiver = NoOpMessageReceiver())
        transform.setMainDexListOutputFile(output.toFile())
        transform.transform(invocation)

        Truth.assertThat(Files.readAllLines(output)).containsExactly("test/A.class")
    }

    @Test
    fun testAllInputs() {
        val output = tmpDir.newFile().toPath()

        val inputJar = tmpDir.root.toPath().resolve("input.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar, listOf("test/A", "test/B"))

        val proguardRules = tmpDir.root.toPath().resolve("proguard_rules")
        Files.write(proguardRules, listOf("-keep class test.A"))

        val userProguardRules = tmpDir.root.toPath().resolve("user_proguard_rules")
        Files.write(userProguardRules, listOf("-keep class test.B"))

        val input = TransformTestHelper.singleJarBuilder(inputJar.toFile())
                .setScopes(QualifiedContent.Scope.PROJECT)
                .build()
        val invocation = TransformTestHelper.invocationBuilder().addReferenceInput(input).build()

        val transform =
                D8MainDexListTransform(
                        manifestProguardRules = proguardRules.stubBuildableArtifact(),
                        userProguardRules = userProguardRules,
                        bootClasspath = Supplier { getBootClasspath() },
                        messageReceiver = NoOpMessageReceiver())
        transform.setMainDexListOutputFile(output.toFile())
        transform.transform(invocation)

        Truth.assertThat(Files.readAllLines(output))
                .containsExactly("test/A.class", "test/B.class")
    }

    @Test
    fun testUserClassesKeptAndDeDuped() {
        val output = tmpDir.newFile().toPath()

        val inputJar = tmpDir.root.toPath().resolve("input.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar, listOf("test/A"))

        val userClasses = tmpDir.root.toPath().resolve("user_rules.txt")
        Files.write(userClasses, listOf("test/User1.class", "test/User2.class", "test/User2.class"))

        val input = TransformTestHelper.singleJarBuilder(inputJar.toFile()).build()
        val invocation = TransformTestHelper.invocationBuilder().addReferenceInput(input).build()

        val transform =
                D8MainDexListTransform(
                        manifestProguardRules = tmpDir.newFile().toPath().stubBuildableArtifact(),
                        userClasses = userClasses,
                        bootClasspath = Supplier { getBootClasspath() },
                        messageReceiver = NoOpMessageReceiver())
        transform.setMainDexListOutputFile(output.toFile())
        transform.transform(invocation)

        Truth.assertThat(Files.readAllLines(output))
                .containsExactly("test/User1.class", "test/User2.class")
    }

    @Test
    fun testNoneKept() {
        val output = tmpDir.newFile().toPath()

        val inputJar = tmpDir.root.toPath().resolve("input.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar, listOf("test/A"))

        val input = TransformTestHelper.singleJarBuilder(inputJar.toFile()).build()
        val invocation = TransformTestHelper.invocationBuilder().addReferenceInput(input).build()

        val transform =
                D8MainDexListTransform(
                        manifestProguardRules = tmpDir.newFile().toPath().stubBuildableArtifact(),
                        bootClasspath = Supplier { getBootClasspath() },
                        messageReceiver = NoOpMessageReceiver())
        transform.setMainDexListOutputFile(output.toFile())
        transform.transform(invocation)

        Truth.assertThat(Files.readAllLines(output)).isEmpty()
    }

    @Test
    fun testThrowsIfDuplicateClasses() {
        val output = tmpDir.newFile().toPath()

        val inputJar1 = tmpDir.root.toPath().resolve("input1.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar1, listOf("test/A"))

        val inputJar2 = tmpDir.root.toPath().resolve("input2.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar2, listOf("test/A"))

        val input1 = TransformTestHelper
            .singleJarBuilder(inputJar1.toFile())
            .setScopes(QualifiedContent.Scope.PROJECT)
            .build()
        val input2 = TransformTestHelper
            .singleJarBuilder(inputJar2.toFile())
            .setScopes(QualifiedContent.Scope.PROJECT)
            .build()
        val invocation = TransformTestHelper
            .invocationBuilder()
            .addReferenceInput(input1)
            .addReferenceInput(input2)
            .build()

        val transform =
            D8MainDexListTransform(
                manifestProguardRules = tmpDir.newFile().toPath().stubBuildableArtifact(),
                bootClasspath = Supplier { getBootClasspath() },
                messageReceiver = NoOpMessageReceiver())
        transform.setMainDexListOutputFile(output.toFile())

        val exception = assertFailsWith(TransformException::class) {
            transform.transform(invocation)
        }

        Truth.assertThat(exception.message).contains(ERROR_DUPLICATE)
        Truth.assertThat(exception.message).contains(ERROR_DUPLICATE_HELP_PAGE)
    }

    private fun Path.stubBuildableArtifact() : BuildableArtifact {
        return Mockito.mock(BuildableArtifact::class.java).apply {
            Mockito.`when`(iterator()).thenReturn(Iterators.singletonIterator(
                this@stubBuildableArtifact.toFile()))
        }
    }

    private fun getBootClasspath():
            List<Path> = listOf(TestUtils.getPlatformFile("android.jar").toPath())
}