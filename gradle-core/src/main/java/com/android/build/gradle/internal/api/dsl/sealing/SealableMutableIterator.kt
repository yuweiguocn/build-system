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

class SealableMutableIterator<out T>(
            private val iterator: MutableIterator<T>,
            dslScope: DslScope)
        : SealableObject(dslScope), MutableIterator<T> {

    override fun remove() {
        if (checkSeal()) {
            iterator.remove()
        }
    }

    override fun hasNext(): Boolean = iterator.hasNext()

    override fun next(): T = iterator.next()
}