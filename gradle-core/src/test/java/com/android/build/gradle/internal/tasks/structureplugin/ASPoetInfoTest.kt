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

package com.android.build.gradle.internal.tasks.structureplugin

import com.android.testutils.TestResources
import com.android.utils.FileUtils.loadFileWithUnixLineSeparators
import com.google.common.truth.Truth
import org.junit.Test
import java.io.File

class ASPoetInfoTest {
    @Test
    fun testMultiModuleProject() {
        val project = getFullProjectInfo()
        validate(project, "project.json")
    }

    @Test
    fun testMultiModuleProjectAnonymized() {
        val project = getFullProjectInfo()
        project.anonymize()
        validate(project, "project_anon.json")
    }

    @Test
    fun testSimpleJavaLibrary() {
        validate(getKotlinJavaSubmodule(), "java_library_module.json")
    }

    @Test
    fun testSimpleKotlinLibrary() {
        validate(getBaseKotlinModule(), "kotlin_library_module.json")
    }

    @Test
    fun testAndroidApp() {
        validate(getBaseAndroidAppModule(), "android_app_module.json")
    }
}

private fun getFullProjectInfo(): ASPoetInfo {
    val project = getBaseProjectInfo()
    project.modules.add(getKotlinJavaSubmodule())
    project.modules.add(getBaseKotlinModule())
    project.modules.add(getBaseAndroidAppModule())

    return project
}

private fun getBaseProjectInfo(): ASPoetInfo {
    val poetInfo = ASPoetInfo()
    poetInfo.agpVersion = "3.2.0"
    poetInfo.gradleVersion = "4.6"
    return poetInfo
}

private fun getKotlinJavaSubmodule(): ModuleInfo {
    val module = ModuleInfo()
    module.name = "kotlinlib_javalib"
    module.type = ModuleType.PURE
    module.javaSourceInfo = SourceFilesInfo(10, 10, 10, 5)
    module.addDependency(
        PoetDependenciesInfo(DependencyType.EXTERNAL_LIBRARY, "implementation", "my.org:lib1:1.0")
    )
    module.addDependency(
        PoetDependenciesInfo(DependencyType.EXTERNAL_LIBRARY, "api", "my.org:lib2:3.1")
    )
    return module
}

private fun getBaseKotlinModule(): ModuleInfo {
    val module = ModuleInfo()
    module.name = "kotlinlib"
    module.type = ModuleType.PURE
    module.useKotlin = true
    module.kotlinSourceInfo = SourceFilesInfo(10, 10, 10, 5)
    module.addDependency(
        PoetDependenciesInfo(DependencyType.MODULE, "implementation", "kotlinlib_javalib"))
    module.addDependency(
        PoetDependenciesInfo(DependencyType.EXTERNAL_LIBRARY, "implementation", "my.org:lib1:1.0"))
    module.addDependency(
        PoetDependenciesInfo(DependencyType.EXTERNAL_LIBRARY, "api", "my.org:lib2:3.1"))
    return module
}

private fun getBaseAndroidAppModule(): ModuleInfo {
    val module = ModuleInfo()
    module.name = "myapp"
    module.type = ModuleType.ANDROID
    module.androidBuildConfig.minSdkVersion = 21
    module.androidBuildConfig.targetSdkVersion = 28
    module.androidBuildConfig.compileSdkVersion = 28

    module.resources.stringCount = 2
    module.resources.imageCount = 3
    module.resources.layoutCount = 4


    module.javaSourceInfo = SourceFilesInfo(5, 5, 5, 5)
    module.useKotlin = true
    module.kotlinSourceInfo = SourceFilesInfo(5, 5, 5, 5)
    module.addDependency(
        PoetDependenciesInfo(DependencyType.MODULE, "implementation", "kotlinlib"))
    module.addDependency(
        PoetDependenciesInfo(DependencyType.EXTERNAL_LIBRARY, "implementation", "my.org:lib1:1.0"))
    module.addDependency(
        PoetDependenciesInfo(DependencyType.EXTERNAL_LIBRARY, "api", "my.org:lib2:3.1"))
    return module
}

private fun ModuleInfo.addDependency(dep: PoetDependenciesInfo): ModuleInfo {
    dependencies.add(dep)
    return this
}

private fun validate(project: ASPoetInfo, targetGoldenFile: String) {
    val expectedJson = loadFileWithUnixLineSeparators(loadGoldenFile(targetGoldenFile))
    Truth.assertThat(project.toJson()).isEqualTo(expectedJson)
}

private fun validate(module: ModuleInfo, targetGoldenFile: String) {
    val expectedJson = loadFileWithUnixLineSeparators(loadGoldenFile(targetGoldenFile))
    Truth.assertThat(module.toJson()).isEqualTo(expectedJson)

    // reload and check it's same value
    val loadedModule = ModuleInfo.fromJson(expectedJson)
    Truth.assertThat(loadedModule.toJson()).isEqualTo(expectedJson)

    // check if both java objects contains the same information
    Truth.assertThat(module).isEqualTo(loadedModule)
}

private fun loadGoldenFile(filename: String): File {
    return TestResources.getFile(
        "/com/android/build/gradle/internal/tasks/structureplugin/$filename")
}