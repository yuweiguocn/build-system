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
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.JavaVersion

/** Options for configuring Java compilation.
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface JavaCompileOptions : Initializable<JavaCompileOptions> {

    /**
     * Language level of the java source code.
     */
    var sourceCompatibility: JavaVersion

    /**
     * Sets the source Compatibility using a String ("1.6"), or a Number (1.7)
     */
    fun setSourceCompatibility(value: Any)

    /**
     * Version of the generated Java bytecode.
     */
    var targetCompatibility: JavaVersion

    /**
     * Sets the target Compatibility using a String ("1.6"), or a Number (1.7)
     */
    fun setTargetCompatibility(value: Any)

    /**
     * Java source files encoding.
     */
    var encoding: String

    /**
     * Whether java compilation should use Gradle's new incremental model.
     *
     * This may cause issues in projects that rely on annotation processing etc.
     *
     * A null value lets the Android plugin choose what to do.
     */
    var incremental: Boolean?

    /** Returns the [AnnotationProcessorOptions] for configuring Java annotation processor.  */
    val annotationProcessorOptions: AnnotationProcessorOptions

    fun annotationProcessorOptions(action: Action<AnnotationProcessorOptions>)
}
