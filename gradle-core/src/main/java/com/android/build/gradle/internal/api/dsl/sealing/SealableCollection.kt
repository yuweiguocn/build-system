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
 * A [MutableCollection] that can be sealed to prevent further updates.
 *
 * It can behave two different ways:
 * - It can wrap another existing collection, providing a sealable view on that collection to
 *   specific clients. Use [SealableCollection.wrap] to create such an instance.
 *
 * - It can act as a self contained collection with its own internal storage using an [ArrayList].
 *   To save on memory in the DSL, the backing collection can be null until items are actually
 *   added. Use [SealableCollection.new] to create such an instance.
 *
 * In both cases, all methods returning sub collections, or iterators will return sealable versions
 * of this classes. Sealing the main collection will seal all the sub-items.
 *
 * @see SealableObject
 */
class SealableCollection<T> private constructor(
            wrappedCollection: MutableCollection<T>?,
            instantiator: () -> MutableCollection<T>,
            cloner: (MutableCollection<T>) -> MutableCollection<T>,
            dslScope: DslScope)
        : AbstractSealableCollection<T, MutableCollection<T>>(
                wrappedCollection,
                instantiator,
                cloner,
                dslScope) {

    companion object {
        fun <T> wrap(wrappedList: MutableCollection<T>, dslScope: DslScope) =
                SealableCollection(
                        wrappedList,
                        { throw RuntimeException("Calling instantiator on a WrappedSealableCollection") },
                        { collection -> collection },
                        dslScope)

        fun <T> new(dslScope: DslScope) = SealableCollection(
                null,
                { ArrayList<T>() },
                { collection -> ArrayList(collection) },
                dslScope)

    }
}