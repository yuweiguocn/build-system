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

package com.android.build.api.dsl.model

import org.gradle.api.Incubating

/** Properties common to Build Type and Product flavor.
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface BuildTypeOrProductFlavor {

    /**
     * Returns the application id suffix applied to this base config.
     *
     * @return the application id
     */
    var applicationIdSuffix: String?
    /**
     * Returns the version name suffix of this flavor or null if none have been set. This is only
     * the value set on this product flavor, not necessarily the actual version name suffix used.
     *
     * @return the version name suffix, or `null` if not specified
     */
    var versionNameSuffix: String?

    // --- DEPRECATED

    @Deprecated("Use postprocessingOptions")
    fun proguardFile(proguardFile: Any)

    @Deprecated("Use postprocessingOptions")
    fun proguardFiles(vararg files: Any)

    @Deprecated("Use postprocessingOptions")
    fun setProguardFiles(proguardFileIterable: Iterable<Any>)

    @Deprecated("Use postprocessingOptions")
    fun testProguardFile(proguardFile: Any)

    @Deprecated("Use postprocessingOptions")
    fun testProguardFiles(vararg proguardFiles: Any)

    @Deprecated("Use postprocessingOptions")
    fun setTestProguardFiles(files: Iterable<Any>)

    @Deprecated("Use postprocessingOptions")
    fun consumerProguardFile(proguardFile: Any)

    @Deprecated("Use postprocessingOptions")
    fun consumerProguardFiles(vararg proguardFiles: Any)

    @Deprecated("Use postprocessingOptions")
    fun setConsumerProguardFiles(proguardFileIterable: Iterable<Any>)
}
