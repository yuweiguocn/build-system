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
 * A [MutableList] that can be sealed to prevent further updates.
 *
 * It can behave two different ways:
 * - It can wrap another existing collection, providing a sealable view on that collection to
 *   specific clients. Use [SealableList.wrap] to create such an instance.
 *
 * - It can act as a self contained collection with its own internal storage using an [ArrayList].
 *   To save on memory in the DSL, the backing collection can be null until items are actually
 *   added. Use [SealableList.new] to create such an instance.
 *
 * In both cases, all methods returning sub collections, or iterators will return sealable versions
 * of this classes. Sealing the main collection will seal all the sub-items.
 *
 * @see SealableObject
 */
class SealableList<T> private constructor(
            wrappedList: MutableList<T>?,
            instantiator: () -> MutableList<T>,
            cloner: (MutableList<T>) -> MutableList<T>,
            dslScope: DslScope)
        : AbstractSealableCollection<T, MutableList<T>>(
                wrappedList,
                instantiator,
                cloner,
        dslScope),
        MutableList<T>  {

    companion object {
        fun <T> wrap(wrappedList: MutableList<T>, dslScope: DslScope) =
                SealableList(
                        wrappedList,
                        { throw RuntimeException("Calling instantiator on a WrappedSealableList") },
                        { collection -> collection },
                        dslScope)

        fun <T> new(dslScope: DslScope) = SealableList(
                null,
                { ArrayList<T>() },
                { collection -> ArrayList(collection) },
                dslScope)
    }

    override fun get(index: Int) = internalCollection?.get(index) ?: throw ArrayIndexOutOfBoundsException(index)

    override fun indexOf(element: T) = internalCollection?.indexOf(element) ?: -1

    override fun lastIndexOf(element: T): Int = internalCollection?.lastIndexOf(element) ?: -1

    override fun add(index: Int, element: T) {
        if (checkSeal()) {
            getBackingCollection().add(index, element)
        }
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        if (checkSeal()) {
            return getBackingCollection().addAll(index, elements)
        }

        return false
    }

    override fun listIterator(): MutableListIterator<T> {
        // if there is no backing collection, we return an empty iterator to avoid allocating
        // it for nothing
        val finalCollection = internalCollection ?: return ImmutableList.of<T>().listIterator()

        return handleSealableSubItem(
                SealableMutableListIterator(finalCollection.listIterator(), dslScope))
    }

    override fun listIterator(index: Int): MutableListIterator<T> {
        // if there is no backing collection, we return an empty iterator to avoid allocating
        // it for nothing
        val finalCollection = internalCollection ?: return ImmutableList.of<T>().listIterator(index)

        return handleSealableSubItem(
                SealableMutableListIterator(finalCollection.listIterator(index), dslScope))
    }

    override fun removeAt(index: Int): T {
        if (checkSeal()) {
            return getBackingCollection().removeAt(index)
        }

        // we need to return something, since we cannot return null. We are returning the item
        // that should have been removed.
        return getBackingCollection()[index]
    }

    override fun set(index: Int, element: T): T {
        if (checkSeal()) {
            return getBackingCollection().set(index, element)
        }

        return element
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> {
        if (checkSeal()) {
            return new<T>(dslScope)
                    .reset(getBackingCollection().subList(fromIndex, toIndex))
        }

        // this is the case where the check seal fail but we are syncing in studio.
        // we just return a random list. Doesn't actually matter.
        return mutableListOf()
    }
}