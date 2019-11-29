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
import org.gradle.api.DomainObjectCollection
import org.gradle.api.DomainObjectSet
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskDependency

class FakeDependencySet: DependencySet {

    override fun addAllLater(p0: Provider<out MutableIterable<Dependency>>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun whenObjectAdded(p0: Action<in Dependency>): Action<in Dependency> {
        // ignore for now
        return p0
    }

    // ----

    override fun contains(element: Dependency?): Boolean {
        TODO("not implemented")
    }

    override fun addAll(elements: Collection<Dependency>): Boolean {
        TODO("not implemented")
    }

    override fun matching(p0: Spec<in Dependency>?): DomainObjectSet<Dependency> {
        TODO("not implemented")
    }

    override fun matching(p0: Closure<*>?): DomainObjectSet<Dependency> {
        TODO("not implemented")
    }

    override fun containsAll(elements: Collection<Dependency>): Boolean {
        TODO("not implemented")
    }

    override fun clear() {
        TODO("not implemented")
    }

    override fun getBuildDependencies(): TaskDependency {
        TODO("not implemented")
    }

    override fun isEmpty(): Boolean {
        TODO("not implemented")
    }

    override fun add(element: Dependency?): Boolean {
        TODO("not implemented")
    }

    override fun removeAll(elements: Collection<Dependency>): Boolean {
        TODO("not implemented")
    }

    override fun all(p0: Closure<*>?) {
        TODO("not implemented")
    }

    override fun all(p0: Action<in Dependency>?) {
        TODO("not implemented")
    }

    override fun iterator(): MutableIterator<Dependency> {
        TODO("not implemented")
    }

    override fun whenObjectRemoved(p0: Closure<*>?) {
        TODO("not implemented")
    }

    override fun whenObjectRemoved(p0: Action<in Dependency>?): Action<in Dependency> {
        TODO("not implemented")
    }

    override fun findAll(p0: Closure<*>?): MutableSet<Dependency> {
        TODO("not implemented")
    }

    override fun remove(element: Dependency?): Boolean {
        TODO("not implemented")
    }

    override fun <S : Dependency?> withType(p0: Class<S>?,
            p1: Action<in S>?): DomainObjectCollection<S> {
        TODO("not implemented")
    }

    override fun <S : Dependency?> withType(p0: Class<S>?,
            p1: Closure<*>?): DomainObjectCollection<S> {
        TODO("not implemented")
    }

    override fun <S : Dependency?> withType(p0: Class<S>?): DomainObjectSet<S> {
        TODO("not implemented")
    }

    override fun whenObjectAdded(p0: Closure<*>?) {
        TODO("not implemented")
    }

    override val size: Int
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun retainAll(elements: Collection<Dependency>): Boolean {
        TODO("not implemented")
    }

    override fun configureEach(p0: Action<in Dependency>?) {
        TODO("not implemented")
    }

    override fun addLater(p0: Provider<out Dependency>?) {
        TODO("not implemented")
    }
}