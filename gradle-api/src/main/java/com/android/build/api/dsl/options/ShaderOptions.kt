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
import com.google.common.collect.ListMultimap
import org.gradle.api.Incubating

/** Options for configuring scoped shader options.
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface ShaderOptions : Initializable<ShaderOptions> {

    /** Returns the list of glslc args.  */
    var glslcArgs: MutableList<String>

    /** Returns the list of scoped glsl args.  */
    var scopedGlslcArgs: ListMultimap<String, String>
}
