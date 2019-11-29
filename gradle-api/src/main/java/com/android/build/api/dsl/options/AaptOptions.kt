/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.build.api.dsl.model.BuildType
import org.gradle.api.Incubating

/** DSL object for configuring aapt options.
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface AaptOptions : Initializable<AaptOptions> {

    /**
     * Pattern describing assets to be ignored.
     *
     *
     * See `aapt --help`
     */
    var ignoreAssetsPattern: String?

    /**
     * Extensions of files that will not be stored compressed in the APK. Adding an empty extension,
     * *i.e.*, setting `noCompress ''` will trivially disable compression for all files.
     *
     *
     * Equivalent of the -0 flag. See `aapt --help`
     */
    val noCompress: Collection<String>

    var useNewCruncher: Boolean

    /**
     * Whether to crunch PNGs.
     *
     *
     * This will reduce the size of the APK if PNGs resources are not already optimally
     * compressed, at the cost of extra time to build.
     *
     *
     * PNG crunching is enabled by default in the release build type and disabled by default in
     * the debug build type.
     *
     *
     * This is replaced by [BuildType.isCrunchPngs].
     */
    @Deprecated("")
    var cruncherEnabled: Boolean

    val cruncherEnabledOverride: Boolean?

    /**
     * Forces aapt to return an error if it fails to find an entry for a configuration.
     *
     *
     * See `aapt --help`
     */
    var failOnMissingConfigEntry: Boolean

    // -- DSL Methods. TODO remove once the instantiator does what I expect it to do.

    /** Returns the list of additional parameters to pass to `aapt`.  */
    var additionalParameters: List<String>?

    /**
     * Obtains the number of cruncher processes to use. More cruncher processes will crunch files
     * faster, but will require more memory and CPU.
     *
     * @return the number of cruncher processes, `0` to use the default
     */
    var cruncherProcesses: Int

    /**
     * Returns true if the resources in this sub-project are fully namespaced.
     *
     *
     * This property is incubating and may change in a future release.
     */
    var namespaced: Boolean?
}
