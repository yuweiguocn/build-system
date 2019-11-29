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

package com.android.build.gradle.internal.api.sourcesets

import com.android.SdkConstants
import com.android.build.api.sourcesets.AndroidSourceDirectorySet
import com.android.build.api.sourcesets.AndroidSourceFile
import com.android.build.api.sourcesets.AndroidSourceSet
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_ANNOTATION_PROCESSOR
import com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_API
import com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_APK
import com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_COMPILE
import com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_COMPILE_ONLY
import com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_IMPLEMENTATION
import com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_PROVIDED
import com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_PUBLISH
import com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_RUNTIME_ONLY
import com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_WEAR_APP
import com.android.build.gradle.internal.errors.DeprecationReporter
import org.gradle.api.Action
import org.gradle.api.tasks.SourceSet
import org.gradle.util.GUtil
import javax.inject.Inject

/**
 */
open class DefaultAndroidSourceSet @Inject constructor(
        private val name: String,
        filesProvider: FilesProvider,
        private val publishPackage: Boolean,
        dslScope: DslScope) : SealableObject(dslScope), AndroidSourceSet {

    private val _javaSource: DefaultAndroidSourceDirectorySet
    private val _javaResources: DefaultAndroidSourceDirectorySet
    private val _manifest: DefaultAndroidSourceFile
    private val _assets: DefaultAndroidSourceDirectorySet
    private val _res: DefaultAndroidSourceDirectorySet
    private val _aidl: DefaultAndroidSourceDirectorySet
    private val _renderscript: DefaultAndroidSourceDirectorySet
    private val _jni: DefaultAndroidSourceDirectorySet
    private val _jniLibs: DefaultAndroidSourceDirectorySet
    private val _shaders: DefaultAndroidSourceDirectorySet
    private val displayName: String = GUtil.toWords(this.name)

    init {
        val javaSrcDisplayName = "$displayName Java source"
        _javaSource = DefaultAndroidSourceDirectorySet(javaSrcDisplayName, filesProvider, dslScope)
        _javaSource.filter.include("**/*.java")

        val javaResourcesDisplayName = "$displayName Java resources"
        _javaResources = DefaultAndroidSourceDirectorySet(
                javaResourcesDisplayName,
                filesProvider,
                dslScope)
        _javaResources.filter.exclude("**/*.java")

        val manifestDisplayName = "$displayName manifest"
        _manifest = DefaultAndroidSourceFile(manifestDisplayName, filesProvider, dslScope)

        val assetsDisplayName = "$displayName assets"
        _assets = DefaultAndroidSourceDirectorySet(assetsDisplayName, filesProvider, dslScope)

        val resourcesDisplayName = "$displayName resources"
        _res = DefaultAndroidSourceDirectorySet(resourcesDisplayName, filesProvider, dslScope)

        val aidlDisplayName = "$displayName aidl"
        _aidl = DefaultAndroidSourceDirectorySet(aidlDisplayName, filesProvider, dslScope)

        val renderscriptDisplayName = "$displayName renderscript"
        _renderscript = DefaultAndroidSourceDirectorySet(
                renderscriptDisplayName,
                filesProvider,
                dslScope)

        val jniDisplayName = "$displayName jni"
        _jni = DefaultAndroidSourceDirectorySet(jniDisplayName, filesProvider, dslScope)

        val libsDisplayName = "$displayName jniLibs"
        _jniLibs = DefaultAndroidSourceDirectorySet(libsDisplayName, filesProvider, dslScope)

        val shaderDisplayName = "$displayName shaders"
        _shaders = DefaultAndroidSourceDirectorySet(shaderDisplayName, filesProvider, dslScope)
    }

    override fun getName() = name

    override fun seal() {
        super.seal()

        _manifest.seal()
        _javaSource.seal()
        _javaResources.seal()
        _assets.seal()
        _res.seal()
        _aidl.seal()
        _renderscript.seal()
        _jni.seal()
        _jniLibs.seal()
        _shaders.seal()
    }

    override fun toString() = "source set $displayName"

    override val res: AndroidSourceDirectorySet
        get() = _res

    override val assets: AndroidSourceDirectorySet
        get() = _assets

    override val resources: AndroidSourceDirectorySet
        get() = _javaResources

    override val java: AndroidSourceDirectorySet
        get() = _javaSource

    override val manifest: AndroidSourceFile
        get() = _manifest

    override val aidl: AndroidSourceDirectorySet
        get() = _aidl

    override val renderscript: AndroidSourceDirectorySet
        get() = _renderscript

    override val jni: AndroidSourceDirectorySet
        get() = _jni

    override val jniLibs: AndroidSourceDirectorySet
        get() = _jniLibs

    override val shaders: AndroidSourceDirectorySet
        get() = _shaders

    override val apiConfigurationName: String
        get() = if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
            CONFIG_NAME_API
        } else {
            "${name}Api"
        }

    override val compileOnlyConfigurationName: String
        get() = if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
            CONFIG_NAME_COMPILE_ONLY
        } else {
            "${name}CompileOnly"
        }

    override val implementationConfigurationName: String
        get() = if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
            CONFIG_NAME_IMPLEMENTATION
        } else {
            "${name}Implementation"
        }

    override val runtimeOnlyConfigurationName: String
        get() = if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
            CONFIG_NAME_RUNTIME_ONLY
        } else {
            "${name}RuntimeOnly"
        }

    override val wearAppConfigurationName: String
        get() = if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
            CONFIG_NAME_WEAR_APP
        } else {
            "${name}WearApp"
        }

    override val annotationProcessorConfigurationName: String
        get() = if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
            CONFIG_NAME_ANNOTATION_PROCESSOR
        } else {
            "${name}AnnotationProcessor"
        }

    override fun manifest(action: Action<AndroidSourceFile>): AndroidSourceSet {
        action.execute(_manifest)
        return this
    }

    override fun res(action: Action<AndroidSourceDirectorySet>): AndroidSourceSet {
        action.execute(_res)
        return this
    }


    override fun assets(action: Action<AndroidSourceDirectorySet>): AndroidSourceSet {
        action.execute(_assets)
        return this
    }

    override fun aidl(action: Action<AndroidSourceDirectorySet>): AndroidSourceSet {
        action.execute(_aidl)
        return this
    }

    override fun renderscript(action: Action<AndroidSourceDirectorySet>): AndroidSourceSet {
        action.execute(_renderscript)
        return this
    }

    override fun jni(action: Action<AndroidSourceDirectorySet>): AndroidSourceSet {
        action.execute(_jni)
        return this
    }

    override fun jniLibs(action: Action<AndroidSourceDirectorySet>): AndroidSourceSet {
        action.execute(_jniLibs)
        return this
    }

    override fun shaders(action: Action<AndroidSourceDirectorySet>): AndroidSourceSet {
        action.execute(_shaders)
        return this
    }

    override fun java(action: Action<AndroidSourceDirectorySet>): AndroidSourceSet {
        action.execute(_javaSource)
        return this
    }

    override fun resources(action: Action<AndroidSourceDirectorySet>): AndroidSourceSet {
        action.execute(_javaResources)
        return this
    }

    override fun setRoot(path: String): AndroidSourceSet {
        _javaSource.setSrcDirs(listOf(path + "/java"))
        _javaResources.setSrcDirs(listOf(path + "/resources"))
        _res.setSrcDirs(listOf(path + "/" + SdkConstants.FD_RES))
        _assets.setSrcDirs(listOf(path + "/" + SdkConstants.FD_ASSETS))
        _manifest.srcFile(path + "/" + SdkConstants.FN_ANDROID_MANIFEST_XML)
        _aidl.setSrcDirs(listOf(path + "/aidl"))
        _renderscript.setSrcDirs(listOf(path + "/rs"))
        _jni.setSrcDirs(listOf(path + "/jni"))
        _jniLibs.setSrcDirs(listOf(path + "/jniLibs"))
        _shaders.setSrcDirs(listOf(path + "/shaders"))
        return this
    }

    // DEPRECATED

    @Suppress("OverridingDeprecatedMember")
    override val compileConfigurationName: String
        get() {
            dslScope.deprecationReporter.reportDeprecatedUsage(
                    "AndroidSourceSet.implementationConfigurationName",
                    "AndroidSourceSet.compileConfigurationName",
                    DeprecationReporter.DeprecationTarget.OLD_DSL)
            return _compileConfigurationName
        }

    internal val _compileConfigurationName: String
        get() = if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
            CONFIG_NAME_COMPILE
        } else {
            "${name}Compile"
        }

    @Suppress("OverridingDeprecatedMember")
    override val packageConfigurationName: String
        get() {
            dslScope.deprecationReporter.reportDeprecatedUsage(
                    "AndroidSourceSet.runtimeOnlyConfigurationName",
                    "AndroidSourceSet.packageConfigurationName",
                    DeprecationReporter.DeprecationTarget.OLD_DSL)
            return _packageConfigurationName
        }

    internal val _packageConfigurationName: String
        get() {
            if (publishPackage) {
                return if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
                    CONFIG_NAME_PUBLISH
                } else {
                    "${name}Publish"
                }
            }

            return if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
                CONFIG_NAME_APK
            } else {
                "${name}Apk"
            }
        }

    @Suppress("OverridingDeprecatedMember")
    override val providedConfigurationName: String
        get() {
            dslScope.deprecationReporter.reportDeprecatedUsage(
                    "AndroidSourceSet.compileOnlyConfigurationName",
                    "AndroidSourceSet.providedConfigurationName",
                    DeprecationReporter.DeprecationTarget.OLD_DSL)
            return _providedConfigurationName
        }

    internal val _providedConfigurationName: String
        get() = if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
            CONFIG_NAME_PROVIDED
        } else {
            "${name}Provided"
        }
}
