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

import org.gradle.api.Incubating

/**
 * Interface for variant control, allowing to query a variant for some base
 * data and allowing to disable some variants.
 *
 * This represents a group of variants sharing the same build type, product flavors combination.
 * They are generally a main production variant (app, library, feature) and it's associated test
 * variants.
 *
 * It is possible to disable all or just specific ones.
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface VariantFilter {

    /** ignores all the versions of this variant */
    fun ignoreAll()
    /** ignores the tests only */
    fun ignoreTests()
    /** ignores a specific variant type */
    fun ignoreUnitTests()
    fun ignoreAndroidTests()

    /**
     * Returns the name of the build type
     */
    val buildType: String

    /**
     * Returns the list of flavor names, or an empty list.
     */
    val flavors: List<String>
}
