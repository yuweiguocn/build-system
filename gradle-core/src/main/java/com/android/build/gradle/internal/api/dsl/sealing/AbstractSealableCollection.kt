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

package com.android.build.gradle.internal.api.dsl.sealing

import com.android.build.gradle.internal.api.dsl.DslScope
import com.google.common.collect.ImmutableList

/**
 * An abstract collection that can be sealed to prevent further updates.
 *
 * It is backed by a standard collection. To save on memory in the DSL, the backing collection can
 * be null until items are actually added.
 *
 * This is not actually abstract but the setup is a bit involved as it requires providing
 * lambdas to allocate the backing collection or to duplicate a collection into a new backing
 * collection. Prefer to use [SealableList], or [SealableSet].
 *
 * @see SealableObject
 */
abstract class AbstractSealableCollection<T, C: MutableCollection<T>> protected constructor(
            originCollection: C?,
            private val instantiator: () -> C,
            private val cloner: (C) -> C,
            dslScope: DslScope)
        : NestedSealable(dslScope), MutableCollection<T> {

    // Use the cloner to clone the original collection into the internal version.
    // Behavior of the cloner will depend on the expected behavior of the sealable wrapper:
    // - cloner makes a copy for cases where we want to disconnect the original list from the wrapper
    // - cloner returns the original copy if we want to create a live wrap. This should only happens
    //   if the user doesn't own the original collection
    protected var internalCollection: C? = if (originCollection == null) originCollection else cloner.invoke(
            originCollection)

    override val size: Int
        get() = internalCollection?.size ?: 0

    override fun contains(element: T) = internalCollection?.contains(element) ?: false

    override fun containsAll(elements: Collection<T>) =
            internalCollection?.containsAll(elements) ?: false

    override fun isEmpty() = internalCollection?.isEmpty() ?: true

    override fun add(element: T): Boolean {
        if (checkSeal()) {
            return getBackingCollection().add(element)
        }

        return false
    }

    override fun addAll(elements: Collection<T>): Boolean {
        if (checkSeal()) {
            return getBackingCollection().addAll(elements)
        }

        return false
    }

    override fun remove(element: T): Boolean {
        if (checkSeal()) {
            return getBackingCollection().remove(element)
        }

        return false
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        if (checkSeal()) {
            return getBackingCollection().removeAll(elements)
        }

        return false
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        if (checkSeal()) {
            return getBackingCollection().retainAll(elements)
        }

        return false
    }

    override fun clear() {
        if (checkSeal()) {
            internalCollection?.clear()
        }
    }

    override fun iterator(): MutableIterator<T> {
        // if there is no backing collection, we return an empty iterator to avoid allocating
        // it for nothing
        val finalCollection = internalCollection ?: return ImmutableList.of<T>().iterator()

        return handleSealableSubItem(
                SealableMutableIterator(finalCollection.iterator(), dslScope))
    }

    /**
     * Resets the content of the collection with the given one.
     */
    internal fun reset(newCollectionToWrap: C): C {
        if (checkSeal()) {
            internalCollection = cloner.invoke(newCollectionToWrap)
        }

        @Suppress("UNCHECKED_CAST")
        return this as C
    }

    /**
     * returns the backing collection. If none are currently present, it instantiates one.
     *
     * This should only be call in order to add items to the backing collection.
     */
    protected fun getBackingCollection(): C {
        if (internalCollection == null) {
            internalCollection = instantiator.invoke()
        }

        // ensure it's still non-null
        return internalCollection!!
    }
}