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

import com.android.build.api.dsl.options.ExternalNativeBuildOptions
import com.android.build.api.dsl.options.JavaCompileOptions
import com.android.build.api.dsl.options.NdkOptions
import com.android.build.api.dsl.options.ShaderOptions
import com.android.build.api.dsl.options.SigningConfig
import org.gradle.api.Action
import org.gradle.api.Incubating
import java.io.File

/** Properties common to Build Type, Product Flavors and Variants.
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface VariantProperties {

    /** Sets the signing configuration. e.g.: `signingConfig signingConfigs.myConfig`  */
    var signingConfig: SigningConfig?


    var buildConfigFields: MutableList<TypedValue>

    /**
     * Adds a new field to the generated BuildConfig class.
     *
     *
     * The field is generated as: `<type> <name> = <value>;`
     *
     *
     * This means each of these must have valid Java content. If the type is a String, then the
     * value should include quotes.
     *
     * @param type the type of the field
     * @param name the name of the field
     * @param value the value of the field
     */
    fun buildConfigField(type: String, name: String, value: String)

    var resValues: MutableList<TypedValue>

    /**
     * Adds a new generated resource.
     *
     *
     * This is equivalent to specifying a resource in res/values.
     *
     *
     * See [Resource
 * Types](http://developer.android.com/guide/topics/resources/available-resources.html).
     *
     * @param type the type of the resource
     * @param name the name of the resource
     * @param value the value of the resource
     */
    fun resValue(type: String, name: String, value: String)

    /**
     * Returns the map of key value pairs for placeholder substitution in the android manifest file.
     *
     *
     * This map will be used by the manifest merger.
     *
     * @return the map of key value pairs.
     */
    var manifestPlaceholders: MutableMap<String, Any>

    /**
     * Returns whether multi-dex is enabled.
     *
     *
     * This can be null if the flag is not set, in which case the default value is used.
     */
    var multiDexEnabled: Boolean?

    var multiDexKeepFile: File?

    var multiDexKeepProguard: File?

    /** Encapsulates per-variant configurations for the NDK, such as ABI filters.  */
    val ndkOptions: NdkOptions

    /** Configures the ndk options with the given action.  */
    fun ndkOptions(action: Action<NdkOptions>)

    val javaCompileOptions: JavaCompileOptions
    @Deprecated("Use javaCompilation instead")
    val compileOptions: JavaCompileOptions

    /** Configures the java compile options with the given action.  */
    fun javaCompileOptions(action: Action<JavaCompileOptions>)

    /** Configures the java compile options with the given action.  */
    @Deprecated("Use javaCompilation instead")
    fun compileOptions(action: Action<JavaCompileOptions>)

    /**
     * Encapsulates per-variant CMake and ndk-build configurations for your external native build.
     *
     *
     * To learn more, see [Add C and C++ Code
 * to Your Project](http://developer.android.com/studio/projects/add-native-code.html#).
     */
    val externalNativeBuildOptions: ExternalNativeBuildOptions

    /** Configures the external build options with the given action.  */
    fun externalNativeBuild(action: Action<ExternalNativeBuildOptions>)

    /** Configures the external build options with the given action.  */
    fun externalNativeBuildOptions(action: Action<ExternalNativeBuildOptions>)

    /** Options for configuring the shader compiler.  */
    val shaders: ShaderOptions

    /** Configures the shader options with the given action.  */
    fun shaderOptions(action: Action<ShaderOptions>)
}
