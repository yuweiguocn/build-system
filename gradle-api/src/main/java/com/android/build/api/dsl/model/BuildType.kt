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

import com.android.build.api.dsl.Initializable
import com.android.build.api.dsl.options.PostProcessingOptions
import org.gradle.api.Incubating
import org.gradle.api.Named

/**
 * a Build Type. This is only the configuration of the build type.
 *
 *
 * It does not include the sources or the dependencies. Those are available on the container or
 * in the artifact info.
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface BuildType : BuildTypeOrProductFlavor, BuildTypeOrVariant, VariantProperties, FallbackStrategy, Initializable<BuildType>, Named {

    /**
     * @see PostProcessingOptions.isObfuscate
     * @see PostProcessingOptions.isRemoveUnusedCode
     * @see .getPostProcessing
     */
    // -- deprecated

    @Deprecated("Use property [crunchPngs]")
    fun isCrunchPngs(): Boolean

    /**
     * This methods sets both obfuscation and shrinking.
     * @see PostProcessingOptions.isObfuscate
     * @see PostProcessingOptions.isRemoveUnusedCode
     * @see .getPostProcessing
     */
    @Deprecated("Use setters on [PostProcessingOptions].")
    var minifyEnabled: Boolean

    @Deprecated("Use setters on [PostProcessingOptions]")
    fun isMinifiedEnabled(): Boolean

    /**
     * @see .getPostProcessing
     */
    @Deprecated("Use {@link PostProcessingOptions#setRemoveUnusedResources(boolean)}")
    var shrinkResources: Boolean

    @Deprecated("Use {@link PostProcessingOptions#setRemoveUnusedResources(boolean)}")
    fun isShrinkResources(): Boolean

    /**
     * @see .getPostProcessing
     */
    @Deprecated("Use {@link PostProcessingOptions#getCodeShrinker()}")
    val useProguard: Boolean?

    @Deprecated("Use {@link PostProcessingOptions#getCodeShrinker()}")
    fun isUseProguard(): Boolean?

    /*
     * (Non javadoc): Whether png crunching should be enabled if not explicitly overridden.
     *
     * Can be removed once the AaptOptions crunch method is removed.
     */
    @Deprecated("")
    var crunchPngsDefault: Boolean

    @Deprecated("")
    fun isCrunchPngsDefault(): Boolean
}
