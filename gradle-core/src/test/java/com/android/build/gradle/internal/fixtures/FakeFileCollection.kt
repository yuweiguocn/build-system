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

package com.android.build.gradle.internal.fixtures

import groovy.lang.Closure
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskDependency
import java.io.File

/**
 * A fake [FileCollection] that can be created without [Project].
 *
 * Limitations:
 *   - This does not handle some of the object type accepted by [Project.files].
 *   - It is not suitable for integration test because Gradle expect all implementation of
 *     FileCollection to also implement FileCollectionInternal.
 */
open class FakeFileCollection(vararg collection : Any?) : FileCollection {

    protected val rawFiles = collection.toMutableSet()
    private val resolvedFiles = mutableSetOf<File>()
    protected var resolved = false

    private fun resolveObject(obj : Any?) {
        when (obj) {
            is String -> resolvedFiles.add(File(obj))
            is File -> resolvedFiles.add(obj)
            is Iterable<*> -> obj.forEach { resolveObject(it) }
            null -> throw NullPointerException("Null object found in FakeFileCollection")
            else -> throw IllegalStateException(
                    """FakeFileCollection can only resolve object of type:
                         |    String
                         |    File
                         |    Iterable<String>
                         |    Iterable<File>
                         |It cannot resolve '$obj' of type ${obj.javaClass}""".trimMargin())
        }
    }

    override fun getFiles(): MutableSet<File> {
        if (!resolved) {
            resolvedFiles.clear()
            resolveObject(rawFiles)
            resolved = true
        }
        return resolvedFiles
    }

    override fun contains(file: File?): Boolean {
        return files.contains(file)
    }

    override fun getAsFileTree(): FileTree {
        TODO("not implemented")
    }

    override fun isEmpty() : Boolean {
        return files.isEmpty()
    }

    override fun addToAntBuilder(
            builder : Any?, nodeName : String?, type : FileCollection.AntType?) {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun addToAntBuilder(builder : Any?, nodeName : String?) : Any {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun getBuildDependencies() : TaskDependency {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun minus(collection : FileCollection?) : FileCollection {
        TODO("not implemented")
    }

    override fun getAsPath() : String {
        TODO("not implemented")
    }

    override fun iterator(): MutableIterator<File> {
        return files.iterator()
    }

    override fun filter(filter: Closure<*>?): FileCollection {
        TODO("not implemented")
    }

    override fun filter(spec : Spec<in File>?) : FileCollection {
        TODO("not implemented")
    }

    override fun plus(collection : FileCollection?) : FileCollection {
        TODO("not implemented")
    }

    override fun getSingleFile() : File {
        return if (files.size == 1) first() else
            throw IllegalStateException(
                    "Failed to get single file.  File collection contains ${files.size} " +
                            "files: $files")
    }

}
