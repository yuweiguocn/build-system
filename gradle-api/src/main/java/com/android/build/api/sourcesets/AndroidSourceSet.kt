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

package com.android.build.api.sourcesets

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.Named

/**
 * An AndroidSourceSet represents a logical group of Java, aidl and RenderScript sources as well as
 * Android and non-Android (Java-style) resources.
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface AndroidSourceSet : Named {

    /**
     * Returns the Java resources which are to be copied into the javaResources output directory.
     *
     * @return the java resources. Never returns null.
     */
    val resources: AndroidSourceDirectorySet

    /**
     * Configures the Java resources for this set.
     *
     *
     * The given action is used to configure the [AndroidSourceDirectorySet] which contains
     * the java resources.
     *
     * @param action The action to use to configure the javaResources.
     * @return this
     */
    fun resources(action: Action<AndroidSourceDirectorySet>): AndroidSourceSet

    /**
     * Returns the Java source which is to be compiled by the Java compiler into the class output
     * directory.
     *
     * @return the Java source. Never returns null.
     */
    val java: AndroidSourceDirectorySet

    /**
     * Configures the Java source for this set.
     *
     *
     * The given action is used to configure the [AndroidSourceDirectorySet] which contains
     * the Java source.
     *
     * @param action The action to use to configure the Java source.
     * @return this
     */
    fun java(action: Action<AndroidSourceDirectorySet>): AndroidSourceSet

    /**
     * Returns the name of the compile configuration for this source set.
     *
     */
    @Deprecated("use {@link #getImplementationConfigurationName()}")
    val compileConfigurationName: String

    /**
     * Returns the name of the runtime configuration for this source set.
     *
     */
    @Deprecated("use {@link #getRuntimeOnlyConfigurationName()}")
    val packageConfigurationName: String

    /**
     * Returns the name of the compiled-only configuration for this source set.
     *
     */
    @Deprecated("use {@link #getCompileOnlyConfigurationName()}")
    val providedConfigurationName: String

    /** Returns the name of the api configuration for this source set.  */
    val apiConfigurationName: String

    /**
     * Returns the name of the compileOnly configuration for this source set.
     */
    val compileOnlyConfigurationName: String

    /**
     * Returns the name of the implemenation configuration for this source set.
     */
    val implementationConfigurationName: String

    /**
     * Returns the name of the implemenation configuration for this source set.
     */
    val runtimeOnlyConfigurationName: String

    /**
     * Returns the name of the wearApp configuration for this source set.
     */
    val wearAppConfigurationName: String

    /**
     * Returns the name of the annotation processing tool classpath for this source set.
     */
    val annotationProcessorConfigurationName: String

    /**
     * The Android Manifest file for this source set.
     *
     * @return the manifest. Never returns null.
     */
    val manifest: AndroidSourceFile

    /**
     * Configures the location of the Android Manifest for this set.
     *
     *
     * The given action is used to configure the [AndroidSourceFile] which contains the
     * manifest.
     *
     * @param action The action to use to configure the Android Manifest.
     * @return this
     */
    fun manifest(action: Action<AndroidSourceFile>): AndroidSourceSet

    /**
     * The Android Resources directory for this source set.
     *
     * @return the resources. Never returns null.
     */
    val res: AndroidSourceDirectorySet

    /**
     * Configures the location of the Android Resources for this set.
     *
     *
     * The given action is used to configure the [AndroidSourceDirectorySet] which contains
     * the resources.
     *
     * @param action The action to use to configure the Resources.
     * @return this
     */
    fun res(action: Action<AndroidSourceDirectorySet>): AndroidSourceSet

    /**
     * The Android Assets directory for this source set.
     *
     * @return the assets. Never returns null.
     */
    val assets: AndroidSourceDirectorySet

    /**
     * Configures the location of the Android Assets for this set.
     *
     *
     * The given action is used to configure the [AndroidSourceDirectorySet] which contains
     * the assets.
     *
     * @param action The action to use to configure the Assets.
     * @return this
     */
    fun assets(action: Action<AndroidSourceDirectorySet>): AndroidSourceSet

    /**
     * The Android AIDL source directory for this source set.
     *
     * @return the source. Never returns null.
     */
    val aidl: AndroidSourceDirectorySet

    /**
     * Configures the location of the Android AIDL source for this set.
     *
     *
     * The given action is used to configure the [AndroidSourceDirectorySet] which contains
     * the AIDL source.
     *
     * @param action The action to use to configure the AIDL source.
     * @return this
     */
    fun aidl(action: Action<AndroidSourceDirectorySet>): AndroidSourceSet

    /**
     * The Android RenderScript source directory for this source set.
     *
     * @return the source. Never returns null.
     */
    val renderscript: AndroidSourceDirectorySet

    /**
     * Configures the location of the Android RenderScript source for this set.
     *
     *
     * The given action is used to configure the [AndroidSourceDirectorySet] which contains
     * the Renderscript source.
     *
     * @param action The action to use to configure the Renderscript source.
     * @return this
     */
    fun renderscript(action: Action<AndroidSourceDirectorySet>): AndroidSourceSet

    /**
     * The Android JNI source directory for this source set.
     *
     * @return the source. Never returns null.
     */
    val jni: AndroidSourceDirectorySet

    /**
     * Configures the location of the Android JNI source for this set.
     *
     *
     * The given action is used to configure the [AndroidSourceDirectorySet] which contains
     * the JNI source.
     *
     * @param action The action to use to configure the JNI source.
     * @return this
     */
    fun jni(action: Action<AndroidSourceDirectorySet>): AndroidSourceSet

    /**
     * The Android JNI libs directory for this source set.
     *
     * @return the libs. Never returns null.
     */
    val jniLibs: AndroidSourceDirectorySet

    /**
     * Configures the location of the Android JNI libs for this set.
     *
     *
     * The given action is used to configure the [AndroidSourceDirectorySet] which contains
     * the JNI libs.
     *
     * @param action The action to use to configure the JNI libs.
     * @return this
     */
    fun jniLibs(action: Action<AndroidSourceDirectorySet>): AndroidSourceSet

    /**
     * The Android shaders directory for this source set.
     *
     * @return the shaders. Never returns null.
     */
    val shaders: AndroidSourceDirectorySet

    /**
     * Configures the location of the Android shaders for this set.
     *
     *
     * The given action is used to configure the [AndroidSourceDirectorySet] which contains
     * the shaders.
     *
     * @param action The action to use to configure the shaders.
     * @return this
     */
    fun shaders(action: Action<AndroidSourceDirectorySet>): AndroidSourceSet

    /**
     * Sets the root of the source sets to a given path.
     *
     * All entries of the source set are located under this root directory.
     *
     * @param path the root directory.
     * @return this
     */
    fun setRoot(path: String): AndroidSourceSet
}
