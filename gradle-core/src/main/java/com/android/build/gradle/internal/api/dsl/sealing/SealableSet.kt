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
 * A [MutableSet] that can be sealed to prevent further updates.
 *
 * It can behave two different ways:
 * - It can wrap another existing collection, providing a sealable view on that collection to
 *   specific clients. Use [SealableSet.wrap] to create such an instance.
 *
 * - It can act as a self contained collection with its own internal storage using a
 *   [LinkedHashSet].
 *   To save on memory in the DSL, the backing collection can be null until items are actually
 *   added. Use [SealableSet.new] to create such an instance.
 *
 * In both cases, all methods returning sub collections, or iterators will return sealable versions
 * of this classes. Sealing the main collection will seal all the sub-items.
 *
 * @see SealableObject
 */
class SealableSet<T> private constructor(
            wrappedSet: MutableSet<T>?,
            instantiator: () -> MutableSet<T>,
            cloner: (MutableSet<T>) -> MutableSet<T>,
        dslScope: DslScope)
        : AbstractSealableCollection<T, MutableSet<T>>(
                wrappedSet, instantiator, cloner, dslScope),
        MutableSet<T> {

    companion object {
        fun <T> wrap(wrappedList: MutableSet<T>, dslScope: DslScope) =
                SealableSet(
                        wrappedList,
                        { throw RuntimeException("Calling objectFactory on a WrappedSealableSet") },
                        { collection -> collection },
                        dslScope)

        fun <T> new(dslScope: DslScope) = SealableSet(
                null,
                { LinkedHashSet<T>() },
                { collection -> LinkedHashSet(collection) },
                dslScope)
    }
}