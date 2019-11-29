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

package com.android.build.api.sourcesets

import org.gradle.api.Incubating
import java.io.File
import org.gradle.api.Named
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.util.PatternFilterable

/** An AndroidSourceDirectorySet represents a set of directory inputs for an Android project.
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface AndroidSourceDirectorySet : PatternFilterable, Named {

    /**
     * Adds the given source directory to this set.
     *
     * @param srcDir The source directory. This is evaluated as for
     * [org.gradle.api.Project.file]
     */
    fun srcDir(srcDir: Any)

    /**
     * Adds the given source directories to this set.
     *
     * @param srcDirs The source directories. These are evaluated as for
     * [org.gradle.api.Project.files]
     */
    fun srcDirs(vararg srcDirs: Any)

    /**
     * Sets the source directories for this set.
     *
     * @param srcDirs The source directories. These are evaluated as for
     * [org.gradle.api.Project.files]
     */
    fun setSrcDirs(srcDirs: Iterable<*>)

    /**
     * Returns the list of source files as a [org.gradle.api.file.FileTree]
     *
     * @return a non null [FileTree] for all the source files in this set.
     */
    val sourceFiles: FileTree

    /**
     * Returns the filter used to select the source from the source directories.
     *
     * @return a non null [org.gradle.api.tasks.util.PatternFilterable]
     */
    val filter: PatternFilterable

    /**
     * Returns the source folders as a list of [org.gradle.api.file.ConfigurableFileTree]
     *
     *
     * This is used as the input to the java compile to enable incremental compilation.
     *
     * @return a non null list of [ConfigurableFileTree]s, one per source dir in this set.
     */
    val sourceDirectoryTrees: List<ConfigurableFileTree>

    /**
     * Returns the resolved directories.
     *
     *
     * Setter can be called with a collection of [Object]s, just like
     * Gradle's `project.file(...)`.
     *
     * @return a non null set of File objects.
     */
    val srcDirs: Set<File>
}
