/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.cxx.cmake.isCmakeConstantTruthy

/**
 * Classes and functions in this file are for dealing with CMake command-line parameters.
 * This is complete enough for compiler settings cache purposes. Any unrecognized flags are
 * classified as UnknownArgument.
 */

/**
 * Interface that represents a single CMake command-line argument.
 */
interface CommandLineArgument {
    /**
     * This is the raw text value of the argument.
     */
    val sourceArgument : String
}

/**
 * This is an argument that was not recognized.
 */
data class UnknownArgument(override val sourceArgument : String) : CommandLineArgument

/**
 * For example, -H<path-to-cmake lists>
 *
 * This is the path to the folder that contains a CMakeLists.txt file.
 */
data class CmakeListsPath(
    override val sourceArgument: String,
    val path : String) : CommandLineArgument {
    companion object {
        /**
         * Create a CmakeListsPath from a path.
         * Don't use if you also have an an original sourceArgument.
         */
        fun from(path : String) : CmakeListsPath {
            return CmakeListsPath("-H$path", path)
        }
    }
}

/**
 * For example, -B<path-to-binary-output-dir>
 *
 * This is the build output folder. This is where the Ninja project is generated.
 * For us, it usually has a value like .externalNativeBuild/cmake/debug/x86.
 */
data class BinaryOutputPath(
    override val sourceArgument: String,
    val path : String) : CommandLineArgument

/**
 * For example, -GAndroid Gradle - Ninja
 *
 * The generator to use for this project.
 **/
data class GeneratorName(
    override val sourceArgument: String,
    val generator : String) : CommandLineArgument

/**
 * For example, -DANDROID_PLATFORM=android-19
 *
 * Defines a build property passed in from the command-line.
 */
data class DefineProperty(
    override val sourceArgument : String,
    val propertyName : String,
    val propertyValue : String) : CommandLineArgument {
    companion object {
        /**
         * Create a DefineProperty from a name and value.
         * Don't use if you also have an an original sourceArgument.
         */
        fun from(property : CmakeProperty, value : String) : DefineProperty {
            return DefineProperty("-D${property.name}=$value", property.name, value)
        }
    }
}

/**
 * Given a list of flags that probably came from android.defaultConfig.cmake.arguments
 * augmented by Json generator classes like CmakeServerExternalNativeJsonGenerator parse
 * the flags into implementors of CommandLineArgument.
 */
fun parseCmakeArguments(args : List<String>) : List<CommandLineArgument> {
    return args.map { arg ->
        when {
            /*
            Parse a property like -DX=Y. CMake supports typed properties as in -DX:STRING=Y but
            we don't currently need to support that. When these properties appear, the type will
            be passed through as part of the name.
             */
            arg.startsWith("-D") && arg.contains("=") -> {
                val propertyName = arg.substringAfter("-D").substringBefore("=")
                val propertyValue = arg.substringAfter("=")
                DefineProperty(arg, propertyName, propertyValue)
            }
            arg.startsWith("-H") -> {
                val path = arg.substringAfter("-H")
                CmakeListsPath(arg, path)
            }
            arg.startsWith("-B") -> {
                val path = arg.substringAfter("-B")
                BinaryOutputPath(arg, path)
            }
            arg.startsWith("-G") -> {
                val path = arg.substringAfter("-G")
                GeneratorName(arg, path)
            }
            else ->
                // Didn't recognize the flag so return unknown argument
                UnknownArgument(arg)
        }
    }
}

/**
 * Returns true when a set of args contains a property whose value would be considered true by
 * CMake. If a property is set more than one time then CMake behavior is to use the last. If
 * the property isn't in the list then returns null.
 */
fun List<CommandLineArgument>.getCmakeBooleanProperty(property : CmakeProperty) : Boolean? {
    val value = getCmakeProperty(property) ?: return null
    return isCmakeConstantTruthy(value)
}

/**
 * Returns the value of the property. Null if not present. If the value is present more than once
 * then the last value is taken.
 */
fun List<CommandLineArgument>.getCmakeProperty(property : CmakeProperty) : String? {
    var result : String? = null
    onEach { arg ->
        when(arg) {
            is DefineProperty -> {
                if (arg.propertyName == property.name) {
                    result = arg.propertyValue
                }
            }
        }
    }
    return result
}

/**
 * Returns the value CMakeLists.txt folder (the -H arg). Null if not present. If the value is
 * present more than once then the last value is taken.
 */
fun List<CommandLineArgument>.getCmakeListsPathValue() : String? {
    var result : String? = null
    onEach { arg ->
        when(arg) {
            is CmakeListsPath -> {
                result = arg.path
            }
        }
    }
    return result
}

/**
 * Remove all instances of the given property from the list of args
 */
fun List<CommandLineArgument>.removeProperty(property : CmakeProperty)
        : List<CommandLineArgument> {
    return filter {
        !(it is DefineProperty &&  it.propertyName == property.name)
    }
}
