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

package com.android.build.gradle.internal.utils

import com.android.build.gradle.internal.fixtures.FakeModuleVersionIdentifier
import com.android.build.gradle.internal.fixtures.FakeResolvedComponentResult
import com.android.build.gradle.internal.fixtures.FakeResolvedDependencyResult
import com.android.ide.common.repository.GradleVersion
import com.google.common.truth.Truth.assertThat
import org.gradle.api.artifacts.result.DependencyResult
import org.junit.Test

/**
 * Unit test for GradlePluginUtils methods.
 */
class GradlePluginUtilsTest {

    @Test
    fun testViolatingDependency() {
        val violatingDependency = createDependency(
            group = "org.jetbrains.kotlin",
            name = "kotlin-gradle-plugin",
            version = "1.0"
        )
        val projectPath = "root project 'project'"
        val dependencyInfo = DependencyInfo(
            displayName = "Kotlin",
            dependencyGroup = "org.jetbrains.kotlin",
            dependencyName = "kotlin-gradle-plugin",
            minimumVersion = GradleVersion.parse("1.3.10")
        )
        val pathsToViolatingPlugins = mutableListOf<String>()

        visitDependency(
            violatingDependency,
            projectPath,
            dependencyInfo,
            pathsToViolatingPlugins,
            mutableSetOf()
        )
        assertThat(pathsToViolatingPlugins)
            .contains("root project 'project' -> org.jetbrains.kotlin:kotlin-gradle-plugin:1.0")
    }

    @Test
    fun testNonViolatingDependency() {
        val nonViolatingDependency = createDependency(
            group = "org.jetbrains.kotlin",
            name = "kotlin-gradle-plugin",
            version = "1.3.10"
        )
        val projectPath = "root project 'project'"
        val dependencyInfo = DependencyInfo(
            displayName = "Kotlin",
            dependencyGroup = "org.jetbrains.kotlin",
            dependencyName = "kotlin-gradle-plugin",
            minimumVersion = GradleVersion.parse("1.3.10")
        )
        val pathsToViolatingPlugins = mutableListOf<String>()

        visitDependency(
            nonViolatingDependency,
            projectPath,
            dependencyInfo,
            pathsToViolatingPlugins,
            mutableSetOf()
        )
        assertThat(pathsToViolatingPlugins).isEmpty()
    }

    private fun createDependency(group: String, name: String, version: String): DependencyResult {
        val dependencyIdentifier =
            FakeModuleVersionIdentifier(group = group, name = name, version = version)
        val dependencyResult = FakeResolvedComponentResult(
            moduleVersion = dependencyIdentifier,
            dependencies = mutableSetOf()
        )
        return FakeResolvedDependencyResult(selected = dependencyResult)
    }
}