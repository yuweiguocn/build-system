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

/** Base class for NDK config file.
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface NdkOptions : Initializable<NdkOptions> {

    /** The module name  */
    var moduleName: String?

    /** The C Flags  */
    var cFlags: String?

    /** The LD Libs  */
    var ldLibs: MutableList<String>

    /** The ABI Filters  */
    var abiFilters: MutableSet<String>

    /** The APP_STL value  */
    var stl: String?

    /** Number of parallel threads to spawn.  */
    var jobs: Int?
}
