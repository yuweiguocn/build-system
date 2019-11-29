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

@file:JvmName("BuildableArtifactUtil")
package com.android.build.gradle.internal.api.artifact

import com.android.build.api.artifact.BuildableArtifact
import com.google.common.collect.Iterators
import java.io.File
import java.nio.file.Path

/**
 * Returns the first file in the [BuildableArtifact] by name if it exists, or null otherwise.
 *
 * @name the file name
 */
fun BuildableArtifact.forName(name: String) : File? {
    return forNameInFolder(files.toTypedArray(), name)
}

private fun forNameInFolder(files: Array<File>, name:String): File? {
    files.filter { it.isDirectory }.forEach {
        val file = forNameInFolder(it.listFiles(), name)
        if (file != null) {
            return file
        }
    }
    return files.filter { it.isFile }.find { it.name == name }
}

/**
 * Returns the single element of a [BuildableArtifact]
 *
 * @throws IllegalArgumentException if the BuildableArtifact contains zero or more than one element.
 */
fun BuildableArtifact.singleFile() : File {
    return Iterators.getOnlyElement(iterator())
}

/**
 * Returns the single element of a [BuildableArtifact] as a [Path]
 *
 * @throws IllegalArgumentException if the BuildableArtifact contains zero or more than one element.
 */
fun BuildableArtifact.singlePath() : Path {
    return Iterators.getOnlyElement(iterator()).toPath()
}