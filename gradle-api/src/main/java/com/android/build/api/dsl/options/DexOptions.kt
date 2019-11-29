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

package com.android.build.api.dsl.options

import com.android.build.api.dsl.Initializable
import org.gradle.api.Incubating

/**
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface DexOptions : Initializable<DexOptions> {

    /**
     * Whether to pre-dex libraries. This can improve incremental builds, but clean builds may be
     * slower.
     */
    var preDexLibraries: Boolean

    /** Enable jumbo mode in dx (`--force-jumbo`).  */
    var jumboMode: Boolean

    /**
     * Whether to run the `dx` compiler as a separate process or inside the Gradle daemon JVM.
     *
     *
     * Running `dx` in-process can greatly improve performance, but is still experimental.
     */
    var dexInProcess: Boolean

    /**
     * Keep all classes with runtime annotations in the main dex in legacy multidex.
     *
     *
     * This is enabled by default and works around an issue that will cause the app to crash when
     * using java.lang.reflect.Field.getDeclaredAnnotations on older android versions.
     *
     *
     * This can be disabled for for apps that do not use reflection and need more space in their
     * main dex.
     *
     *
     * See [http://b.android.com/78144](http://b.android.com/78144).
     */
    var keepRuntimeAnnotatedClasses: Boolean

    /** Number of threads to use when running dx. Defaults to 4.  */
    var threadCount: Int?

    /** Specifies the `-Xmx` value when calling dx. Example value is `"2048m"`.  */
    var javaMaxHeapSize: String?

    /** List of additional parameters to be passed to `dx`.  */
    var additionalParameters: MutableList<String>

    /**
     * Returns the maximum number of concurrent processes that can be used to dex. Defaults to 4.
     *
     *
     * Be aware that the number of concurrent process times the memory requirement represent the
     * minimum amount of memory that will be used by the dx processes:
     *
     *
     * `Total Memory = maxProcessCount * javaMaxHeapSize`
     *
     *
     * To avoid thrashing, keep these two settings appropriate for your configuration.
     *
     * @return the max number of concurrent dx processes.
     */
    var maxProcessCount: Int?

    /**
     * Whether to run the `dx` compiler with the `--no-optimize` flag.
     *
     */


    // --- DEPRECATED

    @Deprecated("Dex will always be optimized. This property has no effect.")
    var optimize: Boolean?

    @set:Deprecated("This property has no effect ")
    var incremental: Boolean
}
