/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle.internal.api.sourcesets

import com.android.build.api.sourcesets.AndroidSourceDirectorySet
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Lists
import groovy.lang.Closure
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileTreeElement
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import java.io.File
import java.util.ArrayList
import java.util.Collections

/**
 * Default implementation of the AndroidSourceDirectorySet.
 */
class DefaultAndroidSourceDirectorySet(
        private val name: String,
        private val filesProvider: FilesProvider,
        dslScope: DslScope) : SealableObject(dslScope), AndroidSourceDirectorySet {
    private val source = Lists.newArrayList<Any>()
    private val _filter = PatternSet()

    override fun getName(): String {
        return name
    }

    override fun srcDir(srcDir: Any) {
        if (checkSeal()) {
            source.add(srcDir)
        }
    }

    override fun srcDirs(vararg srcDirs: Any) {
        if (checkSeal()) {
            Collections.addAll(source, *srcDirs)
        }
    }

    override fun setSrcDirs(srcDirs: Iterable<*>) {
        if (checkSeal()) {
            source.clear()
            for (srcDir in srcDirs) {
                source.add(srcDir)
            }
        }
    }

    override val sourceFiles: FileTree
        get() {
            var src: FileTree? = null
            val sources = srcDirs
            if (!sources.isEmpty()) {
                src = filesProvider.files(ArrayList<Any>(sources)).asFileTree.matching(filter)
            }
            return if (src == null) filesProvider.files().asFileTree else src
        }

    override val sourceDirectoryTrees: List<ConfigurableFileTree>
        get() = source
                .stream()
                .map { sourceDir ->
                    filesProvider.fileTree(
                            ImmutableMap.of(
                                    "dir", sourceDir,
                                    "includes", includes,
                                    "excludes", excludes))
                }
                .collect(ImmutableList.toImmutableList())

    override val srcDirs: Set<File>
        get() = ImmutableSet.copyOf(filesProvider.files(*source.toTypedArray()).files)

    override val filter: PatternFilterable
        get() = _filter

    override fun toString(): String {
        return source.toString()
    }

    override fun getIncludes(): Set<String> {
        return filter.includes
    }

    override fun getExcludes(): Set<String> {
        return filter.excludes
    }

    override fun setIncludes(includes: Iterable<String>): PatternFilterable {
        filter.setIncludes(includes)
        return this
    }

    override fun setExcludes(excludes: Iterable<String>): PatternFilterable {
        filter.setExcludes(excludes)
        return this
    }

    override fun include(vararg includes: String): PatternFilterable {
        filter.include(*includes)
        return this
    }

    override fun include(includes: Iterable<String>): PatternFilterable {
        filter.include(includes)
        return this
    }

    override fun include(includeSpec: Spec<FileTreeElement>): PatternFilterable {
        filter.include(includeSpec)
        return this
    }

    override fun include(includeSpec: Closure<*>): PatternFilterable {
        filter.include(includeSpec)
        return this
    }

    override fun exclude(excludes: Iterable<String>): PatternFilterable {
        filter.exclude(excludes)
        return this
    }

    override fun exclude(vararg excludes: String): PatternFilterable {
        filter.exclude(*excludes)
        return this
    }

    override fun exclude(excludeSpec: Spec<FileTreeElement>): PatternFilterable {
        filter.exclude(excludeSpec)
        return this
    }

    override fun exclude(excludeSpec: Closure<*>): PatternFilterable {
        filter.exclude(excludeSpec)
        return this
    }
}
