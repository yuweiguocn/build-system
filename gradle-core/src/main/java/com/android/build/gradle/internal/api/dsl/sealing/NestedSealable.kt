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
import java.lang.ref.WeakReference

/**
 * A sealable object that deals with other sealable objects are not properties but instead
 * instantiated on the fly and returned via methods.
 *
 * This keeps tracks of these objects and seals them when the main class is sealed.
 */
open class NestedSealable(dslScope: DslScope) : SealableObject(dslScope) {

    private var toBeSealed: MutableList<WeakReference<SealableObject>>? = null

    protected fun <T: SealableObject> handleSealableSubItem(sealableObject: T): T {
        // if the list is already sealed, seal the sub-item.
        if (isSealed()) {
            sealableObject.seal()
        } else {
            // else keep record of it to seal it with the object.
            if (toBeSealed == null) {
                toBeSealed = ArrayList()
            }

            // this should be non null but in threaded env it could have been reset
            // so make sure it's not in a way that will fail if it has been.
            toBeSealed!!.add(WeakReference(sealableObject))
        }

        return sealableObject
    }

    override fun seal() {
        super.seal()

        // seal all the existing weak reference and then clear the list as we don't need them
        // anymore
        toBeSealed?.forEach { it.get()?.seal() }
        toBeSealed = null
    }

}