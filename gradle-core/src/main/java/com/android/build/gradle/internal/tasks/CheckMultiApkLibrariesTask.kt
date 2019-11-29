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

import com.android.annotations.NonNull
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.utils.FileUtils
import com.google.common.io.Files
import org.apache.commons.io.Charsets
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Task to check that no two APKs in a multi-APK project package the same library
 */
@CacheableTask
open class CheckMultiApkLibrariesTask : AndroidVariantTask() {

    private lateinit var featureTransitiveDeps : ArtifactCollection

    @InputFiles
    @NonNull
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getFeatureTransitiveDepsFiles() : FileCollection = featureTransitiveDeps.artifactFiles

    // include fakeOutputDir to allow up-to-date checking
    @get:OutputDirectory
    lateinit var fakeOutputDir: File
        private set

    @TaskAction
    fun taskAction() {
        // Build a map of libraries to their corresponding modules. If two modules package the same
        // library, we will use the map to output a user-friendly error message.
        val map = mutableMapOf<String, MutableList<String>>()
        var found = false
        for (artifact in featureTransitiveDeps) {
            // Sanity check. This should never happen.
            if (artifact.id.componentIdentifier !is ProjectComponentIdentifier) {
                throw GradleException(
                    artifact.id.componentIdentifier.displayName + " is not a Gradle project.")
            }

            val projectPath =
                    (artifact.id.componentIdentifier as ProjectComponentIdentifier).projectPath
            if (artifact.file.isFile) {
                found = found || updateLibraryMap(artifact.file, projectPath, map)
            }
        }

        if (found) {
            // Build the error message. Sort map and projectPaths for consistency.
            val output = StringBuilder()
            for ((library, projectPaths) in map.toSortedMap()) {
                if (projectPaths.size > 1) {
                    output
                        .append(projectPaths.sorted().joinToString(prefix = "[", postfix = "]"))
                        .append(" all package the same library [$library].\n")
                }
            }
            output.append(
                """

                    Multiple APKs packaging the same library can cause runtime errors.
                    Adding the above library as a dependency of the base module will resolve this
                    issue by packaging the library with the base APK instead.
                    """.trimIndent()
            )
            throw GradleException(output.toString())
        }
    }

    class CreationAction(private val variantScope: VariantScope) :
        TaskCreationAction<CheckMultiApkLibrariesTask>() {

        override val name: String
            get() = variantScope.getTaskName("check", "Libraries")
        override val type: Class<CheckMultiApkLibrariesTask>
            get() = CheckMultiApkLibrariesTask::class.java

        override fun configure(task: CheckMultiApkLibrariesTask) {
            task.variantName = variantScope.fullVariantName

            task.featureTransitiveDeps =
                    variantScope.getArtifactCollection(
                        AndroidArtifacts.ConsumedConfigType.METADATA_VALUES,
                        AndroidArtifacts.ArtifactScope.MODULE,
                        AndroidArtifacts.ArtifactType.FEATURE_TRANSITIVE_DEPS
                    )
            task.fakeOutputDir =
                    FileUtils.join(
                        variantScope.globalScope.intermediatesDir,
                        "check-libraries",
                        variantScope.variantConfiguration.dirName
                    )
        }
    }

    private fun updateLibraryMap(
        file: File,
        projectPath: String,
        map: MutableMap<String, MutableList<String>>
    ): Boolean {
        var found = false
        for (library in Files.readLines(file, Charsets.UTF_8)) {
            val libraryWithoutVariant = library.substringBeforeLast("::")
            if (map.containsKey(libraryWithoutVariant)) {
                found = true
                map[libraryWithoutVariant]?.add(projectPath)
            } else {
                map[libraryWithoutVariant] = mutableListOf(projectPath)
            }
        }
        return found
    }
}