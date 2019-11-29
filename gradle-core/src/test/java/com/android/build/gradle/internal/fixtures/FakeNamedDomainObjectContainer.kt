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
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectCollectionSchema
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Namer
import org.gradle.api.Rule
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec
import java.util.SortedMap
import java.util.SortedSet

/**
 * implementation of [NamedDomainObjectFactory] over a simple [Map] for tests.
 */
open class FakeNamedDomainObjectContainer<T>(
        private val itemFactory: NamedDomainObjectFactory<out T>,
        private val nameSupplier: (T) -> String) // this is because Configuration does not extend Named
    : NamedDomainObjectContainer<T> {

    override fun named(p0: String, p1: Action<in T>): NamedDomainObjectProvider<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <S : T> named(p0: String, p1: Class<S>): NamedDomainObjectProvider<S> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <S : T> named(
        p0: String,
        p1: Class<S>,
        p2: Action<in S>
    ): NamedDomainObjectProvider<S> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun addAllLater(p0: Provider<out MutableIterable<T>>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private val items: MutableMap<String, T> = mutableMapOf()
    private val whenAddedActions: MutableList<Action<in T>> = mutableListOf()
    private val whenRemovedActions: MutableList<Action<in T>> = mutableListOf()

    override fun create(name: String): T {
        if (items[name] == null) {
            return createAndAddItem(name)
        }

        throw RuntimeException("Item '$name' already exists")
    }

    override fun maybeCreate(name: String): T {
        val item = items[name]
        if (item != null) {
            return item
        }

        return createAndAddItem(name)
    }

    override fun getByName(name: String?): T =
            items[name] ?: throw RuntimeException("Item '$name' not found")

    override fun findByName(name: String?): T? = items[name]

    override fun whenObjectAdded(action: Action<in T>): Action<in T> {
        whenAddedActions.add(action)
        return action
    }

    override fun whenObjectRemoved(action: Action<in T>): Action<in T> {
        whenRemovedActions.add(action)
        return action
    }

    override fun iterator() = items.values.iterator()

    override val size: Int
        get() = items.size

    override fun isEmpty() = items.isEmpty()

    // --- internal fun

    private fun createAndAddItem(name: String): T {
        val item = itemFactory.create(name)
        items[name] = item

        whenAddedActions.forEach {
            it.execute(item)
        }

        return item
    }

    private fun removeItemFromMap(item: T) {
        items.remove(nameSupplier(item))
        whenRemovedActions.forEach {
            it.execute(item)
        }
    }

    // ---
    override fun create(p0: String?, p1: Action<in T>?): T {
        TODO("not implemented")
    }


    override fun create(p0: String?, p1: Closure<*>?): T {
        TODO("not implemented")
    }

    override fun clear() {
        TODO("not implemented")
    }

    override fun whenObjectAdded(p0: Closure<*>?) {
        TODO("not implemented")
    }

    override fun all(p0: Closure<*>?) {
        TODO("not implemented")
    }

    override fun all(p0: Action<in T>?) {
        TODO("not implemented")
    }

    override fun <S : T> withType(p0: Class<S>?): NamedDomainObjectSet<S> {
        TODO("not implemented")
    }

    override fun <S : T> withType(p0: Class<S>?, p1: Closure<*>?): DomainObjectCollection<S> {
        TODO("not implemented")
    }

    override fun <S : T> withType(p0: Class<S>?, p1: Action<in S>?): DomainObjectCollection<S> {
        TODO("not implemented")
    }

    override fun addAll(elements: Collection<T>): Boolean {
        TODO("not implemented")
    }

    override fun addRule(p0: String?, p1: Closure<*>?): Rule {
        TODO("not implemented")
    }

    override fun addRule(p0: Rule?): Rule {
        TODO("not implemented")
    }

    override fun addRule(p0: String?, p1: Action<String>?): Rule {
        TODO("not implemented")
    }

    override fun getRules(): MutableList<Rule> {
        TODO("not implemented")
    }

    override fun contains(element: T): Boolean {
        TODO("not implemented")
    }

    override fun getByName(p0: String?, p1: Action<in T>?): T {
        TODO("not implemented")
    }

    override fun getByName(p0: String?, p1: Closure<*>?): T {
        TODO("not implemented")
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        TODO("not implemented")
    }

    override fun getNames(): SortedSet<String> {
        TODO("not implemented")
    }

    override fun findAll(p0: Closure<*>?): MutableSet<T> {
        TODO("not implemented")
    }

    override fun getAsMap(): SortedMap<String, T> {
        TODO("not implemented")
    }

    override fun matching(p0: Closure<*>?): NamedDomainObjectSet<T> {
        TODO("not implemented")
    }

    override fun matching(p0: Spec<in T>?): NamedDomainObjectSet<T> {
        TODO("not implemented")
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        TODO("not implemented")
    }

    override fun add(element: T): Boolean {
        TODO("not implemented")
    }

    override fun whenObjectRemoved(p0: Closure<*>?) {
        TODO("not implemented")
    }

    override fun getAt(p0: String?): T {
        TODO("not implemented")
    }

    override fun remove(element: T): Boolean {
        TODO("not implemented")
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        TODO("not implemented")
    }

    override fun getNamer(): Namer<T> {
        TODO("not implemented")
    }

    override fun configure(p0: Closure<*>?): NamedDomainObjectContainer<T> {
        TODO("not implemented")
    }

    override fun configureEach(p0: Action<in T>?) {
        TODO("not implemented")
    }

    override fun addLater(p0: Provider<out T>?) {
        TODO("not implemented")
    }

    override fun register(p0: String?, p1: Action<in T>?): NamedDomainObjectProvider<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun register(p0: String?): NamedDomainObjectProvider<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun named(p0: String?): NamedDomainObjectProvider<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getCollectionSchema(): NamedDomainObjectCollectionSchema {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}