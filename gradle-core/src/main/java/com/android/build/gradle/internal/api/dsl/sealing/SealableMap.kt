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

/**
 * A [MutableMap] that can be sealed to prevent further updates.
 *
 * It can behave two different ways:
 * - It can wrap another existing map, providing a sealable view on that collection to
 *   specific clients. Use [SealableMap.wrap] to create such an instance.
 *
 * - It can act as a self contained map with its own internal storage using an [LinkedHashMap].
 *   To save on memory in the DSL, the backing collection can be null until items are actually
 *   added. Use [SealableMap.new] to create such an instance.
 *
 * In both cases, all methods returning sub collections, or iterators will return sealable versions
 * of this classes. Sealing the main collection will seal all the sub-items.
 *
 * @see SealableObject
 */
class SealableMap<K,V> private constructor(
            originMap: MutableMap<K,V>?,
            private val instantiator: () -> MutableMap<K,V>,
            private val cloner: (MutableMap<K,V>) -> MutableMap<K,V>,
            dslScope: DslScope)
    : NestedSealable(dslScope), MutableMap<K,V> {

    companion object {
        fun <K,V> wrap(originMap: MutableMap<K,V>, dslScope: DslScope) =
                SealableMap(
                        originMap,
                        { throw RuntimeException("Calling objectFactory on a wrapped SealableMap") },
                        { collection -> collection },
                        dslScope)

        fun <K,V> new(dslScope: DslScope) = SealableMap(
                null,
                { LinkedHashMap<K,V>() },
                { collection -> LinkedHashMap(collection) },
                dslScope)
    }

    // make a copy if we are wrapping an existing list so that the underlying list cannot be
    // modified from the outside
    private var internalMap: MutableMap<K,V>? = if (originMap == null) originMap else cloner.invoke(
            originMap)

    override val size: Int
        get() = internalMap?.size ?: 0

    override fun get(key: K) = internalMap?.get(key)

    override fun isEmpty() = internalMap?.isEmpty() ?: true

    override fun containsKey(key: K) = internalMap?.containsKey(key) ?: false

    override fun containsValue(value: V) = internalMap?.containsValue(value) ?: false

    override fun clear() {
        if (checkSeal()) {
            internalMap = null
        }
    }

    override val values: MutableCollection<V>
        // since the returned collection is actually backed by the map and is updated
        // when the map is updated, we cannot optimize and return an empty collection
        // if there is no backing map. We have to create the map here.
        get() = handleSealableSubItem(SealableCollection.wrap(getOrCreateMap().values,
                dslScope))

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        // since the returned collection is actually backed by the map and is updated
        // when the map is updated, we cannot optimize and return an empty collection
        // if there is no backing map. We have to create the map here.
        get() = handleSealableSubItem(SealableSet.wrap(getOrCreateMap().entries, dslScope))

    override val keys: MutableSet<K>
        // since the returned collection is actually backed by the map and is updated
        // when the map is updated, we cannot optimize and return an empty collection
        // if there is no backing map. We have to create the map here.
        // otherwise wrap the set in sealable one
        get() = handleSealableSubItem(SealableSet.wrap(getOrCreateMap().keys, dslScope))

    override fun put(key: K, value: V): V? {
        if (checkSeal()) {
            return getOrCreateMap().put(key, value)
        }

        return null
    }

    override fun putAll(from: Map<out K, V>) {
        if (checkSeal()) {
            getOrCreateMap().putAll(from)
        }
    }

    override fun remove(key: K): V? {
        if (checkSeal()) {
            return getOrCreateMap().remove(key)
        }

        return null
    }

    /**
     * Resets the content of the collection with the given one.
     */
    internal fun reset(newMap: MutableMap<K,V>): MutableMap<K,V> {
        if (checkSeal()) {
            internalMap = cloner.invoke(newMap)
        }

        return this
    }

    /**
     * returns a non-null version of the wrapped map. If there is no wrapped list it instantiates
     * one.
     */
    private fun getOrCreateMap(): MutableMap<K,V> {
        if (internalMap == null) {
            internalMap = instantiator.invoke()
        }

        // ensure it's still non-null
        return internalMap!!
    }
}