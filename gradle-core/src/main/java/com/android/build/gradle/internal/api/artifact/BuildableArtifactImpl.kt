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

package com.android.build.gradle.internal.api.artifact

import com.android.build.api.artifact.BuildableArtifact
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.TaskDependency
import java.io.File
import java.util.Collections

/**
 * Implementation of [BuildableArtifact].
 */
class BuildableArtifactImpl(
    val fileCollection: FileCollection
)
    : BuildableArtifact {

    override fun get(): FileCollection {
        return fileCollection
    }

    override fun iterator(): Iterator<File> {
        return files.iterator()
    }

    override val files : Set<File>
        get() {
            return Collections.unmodifiableSet(fileCollection.files)
        }

    override fun isEmpty() : Boolean {
        return fileCollection.isEmpty
    }

    override fun getBuildDependencies(): TaskDependency = fileCollection.buildDependencies

    val asFileTree : FileTree
        get() {
            return fileCollection.asFileTree
        }

    override fun toString(): String =
        "BuildableArtifactImpl (${get()})"
}
