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

package com.android.build.gradle.internal.api

import com.android.build.api.artifact.BuildArtifactTransformBuilder
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.api.artifact.InputArtifactProvider
import com.android.build.api.artifact.OutputFileProvider
import com.android.build.gradle.api.AndroidSourceDirectorySet
import com.android.build.gradle.internal.api.artifact.BuildArtifactTransformBuilderImpl
import com.android.build.gradle.internal.api.artifact.OutputFileProviderImpl
import com.android.build.gradle.internal.api.artifact.SourceArtifactType
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.DelayedActionsExecutor
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Lists
import groovy.lang.Closure
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileTreeElement
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import java.io.File
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.Callable

/**
 * Default implementation of the AndroidSourceDirectorySet.
 */
class DefaultAndroidSourceDirectorySet(
    private val name: String,
    private val project: Project,
    private val type: SourceArtifactType,
    private val dslScope: DslScope,
    private val artifactsHolder: BuildArtifactsHolder? = null,
    private val delayedActionsExecutor: DelayedActionsExecutor? = null)
    : AndroidSourceDirectorySet {
    private val source = Lists.newArrayList<Any>()
    private val filter = PatternSet()

    init {
        artifactsHolder?.appendArtifact(
                type,
                project.files( Callable<Collection<File>> { srcDirs }))
    }

    constructor(name: String, project: Project, type: SourceArtifactType, dslScope : DslScope) : this(
            name = name,
            project = project,
            type = type,
            dslScope = dslScope,
            artifactsHolder = null
    )

    override fun getName(): String {
        return name
    }

    override fun srcDir(srcDir: Any): AndroidSourceDirectorySet {
        source.add(srcDir)
        return this
    }

    override fun srcDirs(vararg srcDirs: Any): AndroidSourceDirectorySet {
        Collections.addAll(source, *srcDirs)
        return this
    }

    override fun setSrcDirs(srcDirs: Iterable<*>): AndroidSourceDirectorySet {
        source.clear()
        for (srcDir in srcDirs) {
            source.add(srcDir)
        }
        return this
    }

    override fun getSourceFiles(): FileTree {
        if (artifactsHolder != null) {
            val files = artifactsHolder.getArtifactFiles(type)
            return project.files(files).builtBy(files).asFileTree.matching(filter)
        }

        var src: FileTree? = null
        val sources = srcDirs
        if (!sources.isEmpty()) {
            src = project.files(ArrayList<Any>(sources)).asFileTree.matching(filter)
        }
        return if (src == null) project.files().asFileTree else src
    }

    override fun getSourceDirectoryTrees(): List<ConfigurableFileTree> {
        return source.stream()
                .map { sourceDir ->
                    project.fileTree(
                            ImmutableMap.of(
                                    "dir", sourceDir,
                                    "includes", includes,
                                    "excludes", excludes))
                }
                .collect(ImmutableList.toImmutableList())
    }

    override fun getSrcDirs(): Set<File> {
        return ImmutableSet.copyOf(project.files(*source.toTypedArray()).files)
    }

    override fun getFilter(): PatternFilterable {
        return filter
    }

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

    override fun     exclude(excludes: Iterable<String>): PatternFilterable {
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

    override fun <T : Task> appendTo(
            taskName: String,
            taskType: Class<T>,
            configurationAction: BuildArtifactTransformBuilder.ConfigurationAction<T>) {
        if (artifactsHolder == null || delayedActionsExecutor == null) {
            throw UnsupportedOperationException("appendTo is not supported by source set '$name'")
        }
        BuildArtifactTransformBuilderImpl(
                project,
                artifactsHolder,
                delayedActionsExecutor,
                taskName,
                taskType,
                dslScope)
                .append(type)
                .create(configurationAction)
    }

    override fun <T : Task> replace(taskName: String,
            taskType: Class<T>,
            configurationAction: BuildArtifactTransformBuilder.ConfigurationAction<T>) {
        if (artifactsHolder == null || delayedActionsExecutor == null) {
            throw UnsupportedOperationException("replace is not supported by source set '$name'")
        }
        BuildArtifactTransformBuilderImpl(
                project,
                artifactsHolder,
                delayedActionsExecutor,
                taskName,
                taskType,
                dslScope)
                .replace(type)
                .create(configurationAction)
    }

    override fun getBuildableArtifact() : BuildableArtifact {
        return artifactsHolder?.getArtifactFiles(type)
                ?: throw UnsupportedOperationException(
                    "getBuildableArtifact is not supported by source set '$name'")
    }
}

