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

package com.android.build.gradle.internal.res.namespaced

import com.android.builder.packaging.JarMerger
import java.io.File
import java.io.Serializable
import java.util.function.Predicate
import javax.inject.Inject

class JarWorkerRunnable @Inject constructor(val params: JarRequest) : Runnable {
    override fun run() {
        JarMerger(params.toFile.toPath(), params.filter).use { out ->
            if (params.manifestProperties.isNotEmpty()) {
                out.setManifestProperties(params.manifestProperties)
            }
            params.fromDirectories.forEach { dir -> out.addDirectory(dir.toPath()) }
            params.fromJars.forEach { jar -> out.addJar(jar.toPath()) }
            params.fromFiles.forEach { (path, file) -> out.addFile(path, file.toPath()) }
        }
    }
}

data class JarRequest(
    val toFile: File,
    val fromDirectories: List<File> = listOf(),
    val fromJars: List<File> = listOf(),
    val fromFiles: Map<String, File> = mapOf(),
    val manifestProperties: Map<String, String> = mapOf(),
    val filter: ((className: String) -> Boolean)? = null
): Serializable
