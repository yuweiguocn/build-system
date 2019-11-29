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

package com.android.build.gradle.integration.common.fixture.app

/** Builder for the contents of a build.gradle file. */
class BuildFileBuilder {

    var plugin: String? = null
    var compileSdkVersion: String? = null
    var minSdkVersion: String? = null

    private val dependencies: StringBuilder = StringBuilder()

    fun addDependency(configuration: String = "implementation", dependency: String) {
        dependencies.append("\n    $configuration $dependency")
    }

    fun build(): String {
        val contents = StringBuilder()
        if (plugin != null) {
            contents.append("apply plugin: '$plugin'")
        }
        if (compileSdkVersion != null) {
            contents.append("\n\nandroid.compileSdkVersion = $compileSdkVersion")
        }
        if (minSdkVersion != null) {
            contents.append("\n\nandroid.defaultConfig.minSdkVersion = $minSdkVersion")
        }
        if (!dependencies.isEmpty()) {
            contents.append("\n\ndependencies{")
            contents.append("$dependencies")
            contents.append("\n}")
        }
        return contents.toString()
    }
}