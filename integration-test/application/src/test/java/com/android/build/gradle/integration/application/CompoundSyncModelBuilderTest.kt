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
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.builder.model.Variant
import com.android.builder.tasks.BooleanLatch
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.streams.toList

class CompoundSyncModelBuilderTest {
    @Rule
    @JvmField
    var project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Test
    @Throws(Exception::class)
    fun getParameterizedAndroidProjectWithSourceGeneration() {
        val latch = BooleanLatch()
        val generateSourcesTasks = ConcurrentLinkedQueue<String>()

        // Get AndroidProject with parameterized API and schedule ide setup tasks to be run.
        val gradleBuildResult = project.model().fetchAndroidModelAndGenerateSources {
            val androidProject = it.androidProject
            val variants = it.variants

            // Verify that AndroidProject model doesn't contain Variant instance.
            assertThat<Variant, Iterable<Variant>>(androidProject.variants).isEmpty()

            // Verify that AndroidProject model contains a list of variant names.
            assertThat<String, Iterable<String>>(androidProject.variantNames).containsExactly(
                "debug",
                "release"
            )

            // Verify that Variant models have the correct name.
            assertThat<String, Iterable<String>>(
                variants.stream().map<String>{ it.name }.toList()
            ).containsExactly("debug", "release")

            // Add all ide setup tasks to a list to later guarantee they are run
            generateSourcesTasks.addAll(variants.flatMap {
                listOf(
                    it.mainArtifact.ideSetupTaskNames.map { ":$it" },
                    it.extraJavaArtifacts.flatMap { it.ideSetupTaskNames }.map { ":$it" },
                    it.extraAndroidArtifacts.flatMap { it.ideSetupTaskNames }.map { ":$it" }
                )
            }.flatten())

            latch.signal()
        }

        assertThat(latch.await(TimeUnit.SECONDS.toNanos(10))).isTrue()
        generateSourcesTasks.forEach {
            if (it == ":createMockableJar") {
                assertThat(gradleBuildResult.getTask(it)).wasUpToDate()
            } else {
                assertThat(gradleBuildResult.getTask(it)).wasSkipped()
            }
        }
        assertThat(gradleBuildResult.exception).isNull()
    }
}