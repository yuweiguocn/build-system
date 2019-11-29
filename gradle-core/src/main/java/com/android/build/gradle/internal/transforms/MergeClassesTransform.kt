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

import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.res.namespaced.JarRequest
import com.android.build.gradle.internal.res.namespaced.JarWorkerRunnable
import com.android.build.gradle.internal.tasks.Workers
import java.io.File
import java.util.regex.Pattern

/**
 * A transform that takes the FULL_PROJECT's CLASSES streams and merges the class files into a
 * single jar.
 *
 * This transform is necessary to run in a feature module when minification is enabled in the base
 * because the base needs access to the feature's runtime dependencies to perform minification and
 * eventually get the libraries' classes to the correct APKs.
 *
 * Regarding Streams, this is a no-op transform as it does not write any output to any stream. It
 * uses secondary outputs to write directly into the given folder.
 */
class MergeClassesTransform(
    private val outputJarFile: File
) : Transform() {

    override fun getName() = "mergeClasses"

    override fun getSecondaryFileOutputs() = listOf(outputJarFile)

    override fun isIncremental() = false

    override fun getInputTypes(): Set<QualifiedContent.ContentType> {
        return TransformManager.CONTENT_CLASS
    }

    override fun getScopes(): MutableSet<QualifiedContent.Scope> {
        return TransformManager.EMPTY_SCOPES
    }

    override fun getReferencedScopes(): MutableSet<in QualifiedContent.Scope> {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    override fun transform(invocation: TransformInvocation) {

        val fromJars =
            invocation.referencedInputs.flatMap { it.jarInputs }.map { it.file }
        val fromDirectories =
            invocation.referencedInputs.flatMap { it.directoryInputs }.map { it.file }

        // Filter out everything but the .class and .kotlin_module files.
        val classFilter: (className: String) -> Boolean =
            { it -> CLASS_PATTERN.matcher(it).matches() || KOTLIN_MODULE_PATTERN.matcher(it).matches() }

        val workers = Workers.getWorker(invocation.context.workerExecutor)

        workers.use {
            it.submit(
                JarWorkerRunnable::class.java,
                JarRequest(
                    toFile = outputJarFile,
                    fromJars = fromJars,
                    fromDirectories = fromDirectories,
                    filter = classFilter
                )
            )
        }
    }

    companion object {
        private val CLASS_PATTERN = Pattern.compile(".*\\.class$")
        private val KOTLIN_MODULE_PATTERN = Pattern.compile("^META-INF/.*\\.kotlin_module$")
    }
}
