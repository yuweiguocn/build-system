/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.api.dsl.options

import com.android.build.api.dsl.Initializable
import org.gradle.api.Action
import org.gradle.api.Incubating

/**
 * DSL object for configuring APK Splits options.
 *
 *
 * See [APK
 * Splits](https://developer.android.com/studio/build/configure-apk-splits.html).
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface Splits : Initializable<Splits> {

    /** Density settings.  */
    val density: DensitySplitOptions

    /** Configures density split settings.  */
    fun density(action: Action<DensitySplitOptions>)

    /** ABI settings.  */
    val abi: AbiSplitOptions

    /** Configures ABI split settings.  */
    fun abi(action: Action<AbiSplitOptions>)

    /** Language settings.  */
    val language: LanguageSplitOptions

    /** Configures the language split settings.  */
    fun language(action: Action<LanguageSplitOptions>)

    /**
     * Returns the list of Density filters used for multi-apk.
     *
     *
     * null value is allowed, indicating the need to generate an apk with all densities.
     *
     * @return a set of filters.
     */
    val densityFilters: Set<String>

    /**
     * Returns the list of ABI filters used for multi-apk.
     *
     *
     * null value is allowed, indicating the need to generate an apk with all abis.
     *
     * @return a set of filters.
     */
    val abiFilters: Set<String>

    /**
     * Returns the list of language filters used for multi-apk.
     *
     *
     * null value is allowed, indicating the need to generate an apk with all languages.
     *
     * @return a set of language filters.
     */
    val languageFilters: Set<String>
}
