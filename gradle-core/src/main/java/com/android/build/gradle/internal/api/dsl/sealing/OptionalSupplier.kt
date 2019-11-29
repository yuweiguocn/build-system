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

/**
 * An optional supplier of a instance
 *
 * Once the object is query the first time and instantiated, each new query returns the same
 * instance.
 *
 * The supplier can handle being initialized by another supplier that has not instantiated its
 * object yet, without triggering the instantiation.
 *
 * @param parent the sealable parent of the object. If the parent is sealed at creation time, then
 * the object is sealed right away.
 * @param theClass the class of the object to instantiate
 * @param args the arguments passed to the constructor.
 */
class OptionalSupplier<T: InitializableSealable<T>>(
        private val parent: SealableObject,
        private val theClass: Class<T>,
        vararg private val args: Any) : Sealable {

    private var localInstance: T? = null

    fun get(): T {
        if (localInstance == null) {
            localInstance = parent.dslScope.objectFactory.newInstance(theClass, *args)
            if (parent.isSealed()) {
                localInstance?.seal()
            }
        }

        return localInstance!!
    }

    fun copyFrom(from: OptionalSupplier<T>) {
        val value = from.localInstance

        if (value != null) {
            get().initWith(value)
        } else {
            localInstance = null
        }
    }

    override fun seal() {
        localInstance?.seal()
    }
}
