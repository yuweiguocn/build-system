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
import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationPublications
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyConstraintSet
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskDependency
import java.io.File

class FakeConfiguration(private val name: String): Configuration {

    private var visible: Boolean = true
    private var description: String? = null
    private var canBeConsumed: Boolean = true
    private var canBeResolved: Boolean = true
    private var extendsConfigs: MutableSet<Configuration> = mutableSetOf()
    private val dependencySet = FakeDependencySet()

    override fun getName() = name
    override fun toString() = name

    override fun setVisible(p0: Boolean): Configuration {
        visible = p0
        return this
    }

    override fun setDescription(p0: String?): Configuration {
        description = p0
        return this
    }

    override fun setCanBeConsumed(p0: Boolean) {
        canBeConsumed = p0
    }

    override fun setCanBeResolved(p0: Boolean) {
        canBeResolved = p0
    }

    override fun getAllDependencies() = dependencySet

    override fun getDependencies() = dependencySet

    override fun extendsFrom(vararg p0: Configuration): Configuration {
        extendsConfigs.addAll(p0.asList())
        return this
    }

    override fun getExtendsFrom() = extendsConfigs

    // -----

    override fun getUploadTaskName(): String {
        TODO("not implemented")
    }

    override fun getIncoming(): ResolvableDependencies {
        TODO("not implemented")
    }

    override fun getAll(): MutableSet<Configuration> {
        TODO("not implemented")
    }

    override fun copy(): Configuration {
        TODO("not implemented")
    }

    override fun copy(p0: Spec<in Dependency>?): Configuration {
        TODO("not implemented")
    }

    override fun copy(p0: Closure<*>?): Configuration {
        TODO("not implemented")
    }

    override fun getAttributes(): AttributeContainer {
        TODO("not implemented")
    }

    override fun attributes(p0: Action<in AttributeContainer>?): Configuration {
        TODO("not implemented")
    }

    override fun getResolutionStrategy(): ResolutionStrategy {
        TODO("not implemented")
    }

    override fun resolutionStrategy(p0: Closure<*>?): Configuration {
        TODO("not implemented")
    }

    override fun resolutionStrategy(p0: Action<in ResolutionStrategy>?): Configuration {
        TODO("not implemented")
    }

    override fun getAllArtifacts(): PublishArtifactSet {
        TODO("not implemented")
    }

    override fun getDescription(): String {
        TODO("not implemented")
    }

    override fun filter(p0: Closure<*>?): FileCollection {
        TODO("not implemented")
    }

    override fun filter(p0: Spec<in File>?): FileCollection {
        TODO("not implemented")
    }

    override fun getTaskDependencyFromProjectDependency(p0: Boolean, p1: String?): TaskDependency {
        TODO("not implemented")
    }

    override fun copyRecursive(): Configuration {
        TODO("not implemented")
    }

    override fun copyRecursive(p0: Spec<in Dependency>?): Configuration {
        TODO("not implemented")
    }

    override fun copyRecursive(p0: Closure<*>?): Configuration {
        TODO("not implemented")
    }

    override fun isEmpty(): Boolean {
        TODO("not implemented")
    }

    override fun defaultDependencies(p0: Action<in DependencySet>?): Configuration {
        TODO("not implemented")
    }

    override fun fileCollection(p0: Spec<in Dependency>?): FileCollection {
        TODO("not implemented")
    }

    override fun fileCollection(p0: Closure<*>?): FileCollection {
        TODO("not implemented")
    }

    override fun fileCollection(vararg p0: Dependency?): FileCollection {
        TODO("not implemented")
    }

    override fun getArtifacts(): PublishArtifactSet {
        TODO("not implemented")
    }

    override fun resolve(): MutableSet<File> {
        TODO("not implemented")
    }

    override fun setExtendsFrom(p0: MutableIterable<Configuration>?): Configuration {
        TODO("not implemented")
    }

    override fun getOutgoing(): ConfigurationPublications {
        TODO("not implemented")
    }

    override fun isVisible(): Boolean {
        TODO("not implemented")
    }

    override fun getResolvedConfiguration(): ResolvedConfiguration {
        TODO("not implemented")
    }

    override fun exclude(p0: MutableMap<String, String>?): Configuration {
        TODO("not implemented")
    }

    override fun getFiles(): MutableSet<File> {
        TODO("not implemented")
    }

    override fun contains(p0: File?): Boolean {
        TODO("not implemented")
    }

    override fun isCanBeResolved(): Boolean {
        TODO("not implemented")
    }

    override fun getBuildDependencies(): TaskDependency {
        TODO("not implemented")
    }

    override fun getExcludeRules(): MutableSet<ExcludeRule> {
        TODO("not implemented")
    }

    override fun iterator(): MutableIterator<File> {
        TODO("not implemented")
    }

    override fun isTransitive(): Boolean {
        TODO("not implemented")
    }

    override fun setTransitive(p0: Boolean): Configuration {
        TODO("not implemented")
    }

    override fun isCanBeConsumed(): Boolean {
        TODO("not implemented")
    }

    override fun getSingleFile(): File {
        TODO("not implemented")
    }

    override fun getState(): Configuration.State {
        TODO("not implemented")
    }

    override fun getHierarchy(): MutableSet<Configuration> {
        TODO("not implemented")
    }

    override fun files(p0: Closure<*>?): MutableSet<File> {
        TODO("not implemented")
    }

    override fun files(p0: Spec<in Dependency>?): MutableSet<File> {
        TODO("not implemented")
    }

    override fun files(vararg p0: Dependency?): MutableSet<File> {
        TODO("not implemented")
    }

    override fun getAsFileTree(): FileTree {
        TODO("not implemented")
    }

    override fun addToAntBuilder(p0: Any?, p1: String?, p2: FileCollection.AntType?) {
        TODO("not implemented")
    }

    override fun addToAntBuilder(p0: Any?, p1: String?): Any {
        TODO("not implemented")
    }

    override fun minus(p0: FileCollection?): FileCollection {
        TODO("not implemented")
    }

    override fun outgoing(p0: Action<in ConfigurationPublications>?) {
        TODO("not implemented")
    }

    override fun getAsPath(): String {
        TODO("not implemented")
    }

    override fun plus(p0: FileCollection?): FileCollection {
        TODO("not implemented")
    }

    override fun withDependencies(p0: Action<in DependencySet>?): Configuration {
        TODO("not implemented")
    }

    override fun getAllDependencyConstraints(): DependencyConstraintSet {
        TODO("not implemented")
    }

    override fun getDependencyConstraints(): DependencyConstraintSet {
        TODO("not implemented")
    }
}