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

package com.android.build.api.dsl.extension

import com.android.build.api.dsl.options.AaptOptions
import com.android.build.api.dsl.options.DexOptions
import com.android.build.api.dsl.options.LintOptions
import com.android.builder.model.DataBindingOptions
import org.gradle.api.Action
import org.gradle.api.Incubating

/** properties common to the extension and the generated variants.
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface VariantOrExtensionProperties {
    fun aaptOptions(action: Action<AaptOptions>)

    /** Options for aapt, tool for packaging resources.  */
    val aaptOptions: AaptOptions

    fun dexOptions(action: Action<DexOptions>)

    /** Dex options.  */
    val dexOptions: DexOptions

    /** Lint options.  */
    val lintOptions: LintOptions

    fun lintOptions(action: Action<LintOptions>)

    /** Data Binding options.  */
    val dataBinding: DataBindingOptions

    fun dataBinding(action: Action<DataBindingOptions>)

}