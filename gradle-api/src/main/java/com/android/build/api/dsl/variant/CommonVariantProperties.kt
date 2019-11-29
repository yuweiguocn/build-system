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

package com.android.build.api.dsl.variant

import com.android.build.api.sourcesets.AndroidSourceSet
import org.gradle.api.Action
import org.gradle.api.Incubating

/** common variant properties to all variants
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface CommonVariantProperties {

    val name: String

    val buildTypeName: String
    val flavorNames: List<String>

    /**
     * Base source sets for the build type, default config, and product flavors
     * The list is immutable and the sourcesets themselves have been sealed
     */
    val baseSourceSets: List<AndroidSourceSet>

    val variantSourceSet: AndroidSourceSet?

    fun variantSourceSet(action: Action<AndroidSourceSet>)

    val multiFlavorSourceSet: AndroidSourceSet?

    fun multiFlavorSourceSet(action: Action<AndroidSourceSet>)
}