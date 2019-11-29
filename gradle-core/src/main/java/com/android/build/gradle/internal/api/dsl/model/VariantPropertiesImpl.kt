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

package com.android.build.gradle.internal.api.dsl.model

import com.android.build.api.dsl.model.TypedValue
import com.android.build.api.dsl.model.VariantProperties
import com.android.build.api.dsl.options.ExternalNativeBuildOptions
import com.android.build.api.dsl.options.JavaCompileOptions
import com.android.build.api.dsl.options.NdkOptions
import com.android.build.api.dsl.options.ShaderOptions
import com.android.build.api.dsl.options.SigningConfig
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.options.ExternalNativeBuildOptionsImpl
import com.android.build.gradle.internal.api.dsl.options.JavaCompileOptionsImpl
import com.android.build.gradle.internal.api.dsl.options.NdkOptionsImpl
import com.android.build.gradle.internal.api.dsl.options.ShaderOptionsImpl
import com.android.build.gradle.internal.api.dsl.options.SigningConfigImpl
import com.android.build.gradle.internal.api.dsl.sealing.OptionalSupplier
import com.android.build.gradle.internal.api.dsl.sealing.SealableList
import com.android.build.gradle.internal.api.dsl.sealing.SealableMap
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.EvalIssueReporter
import org.gradle.api.Action
import java.io.File

class VariantPropertiesImpl(dslScope: DslScope)
        : SealableObject(dslScope), VariantProperties {

    // backing properties for lists/sets/maps
    private val _buildConfigFields: SealableList<TypedValue> = SealableList.new(dslScope)
    private val _resValues: SealableList<TypedValue> = SealableList.new(dslScope)
    private val _manifestPlaceholders: SealableMap<String, Any> = SealableMap.new(dslScope)

    private val _ndkOptions = OptionalSupplier(this, NdkOptionsImpl::class.java, dslScope)
    private val _javaCompileOptions = OptionalSupplier(
            this, JavaCompileOptionsImpl::class.java, dslScope)
    private val _externalNativeBuildOptions = OptionalSupplier(
            this, ExternalNativeBuildOptionsImpl::class.java, dslScope)
    private val _shaders = OptionalSupplier(this, ShaderOptionsImpl::class.java, dslScope)

    override val ndkOptions: NdkOptions
        get() = _ndkOptions.get()
    override val javaCompileOptions: JavaCompileOptions
        get() = _javaCompileOptions.get()
    override val externalNativeBuildOptions: ExternalNativeBuildOptions
        get() = _externalNativeBuildOptions.get()
    override val shaders: ShaderOptions
        get() = _shaders.get()

    override var signingConfig: SigningConfig? = null
        set(value) {
            if (checkSeal()) {
                if (value !is SigningConfigImpl) {
                    dslScope.issueReporter.reportError(
                            EvalIssueReporter.Type.GENERIC,
                        EvalIssueException("BuildType.signingConfig set with an object not from android.signingConfigs"))
                }
                field = value
            }
        }

    override var buildConfigFields: MutableList<TypedValue>
        get() = _buildConfigFields
        set(value) {
            _buildConfigFields.reset(value)
        }

    override fun buildConfigField(type: String, name: String, value: String) {
        _buildConfigFields.add(TypedValueImpl(type, name, value))
    }

    override var resValues: MutableList<TypedValue>
        get() = _resValues
        set(value) {
            _resValues.reset(value)
        }

    override fun resValue(type: String, name: String, value: String) {
        if (checkSeal()) {
            _resValues.add(TypedValueImpl(type, name, value))
        }
    }

    override var manifestPlaceholders: MutableMap<String, Any>
        get() = _manifestPlaceholders
        set(value) {
            _manifestPlaceholders.reset(value)
        }

    override var multiDexEnabled: Boolean? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var multiDexKeepFile: File? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var multiDexKeepProguard: File? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override fun ndkOptions(action: Action<NdkOptions>) {
        action.execute(_ndkOptions.get())
    }

    override fun javaCompileOptions(action: Action<JavaCompileOptions>) {
        action.execute(_javaCompileOptions.get())
    }

    override fun externalNativeBuild(action: Action<ExternalNativeBuildOptions>) {
        // FIXME deduplicate?
        action.execute(_externalNativeBuildOptions.get())
    }

    override fun externalNativeBuildOptions(action: Action<ExternalNativeBuildOptions>) {
        // FIXME deduplicate?
        action.execute(_externalNativeBuildOptions.get())
    }

    override fun shaderOptions(action: Action<ShaderOptions>) {
        action.execute(_shaders.get())
    }

    fun initWith(that: VariantPropertiesImpl) {

        if (checkSeal()) {
            _buildConfigFields.reset(that._buildConfigFields)
            _resValues.reset(that._resValues)
            _manifestPlaceholders.reset(that._manifestPlaceholders)

            _ndkOptions.copyFrom(that._ndkOptions)
            _javaCompileOptions.copyFrom(that._javaCompileOptions)

            _externalNativeBuildOptions.copyFrom(that._externalNativeBuildOptions)
            _shaders.copyFrom(that._shaders)

            multiDexEnabled = that.multiDexEnabled
            multiDexKeepFile = that.multiDexKeepFile
            multiDexKeepProguard = that.multiDexKeepProguard
        }
    }

    override fun seal() {
        super.seal()

        _buildConfigFields.seal()
        _resValues.seal()
        _manifestPlaceholders.seal()

        _ndkOptions.seal()
        _javaCompileOptions.seal()
        _externalNativeBuildOptions.seal()
        _shaders.seal()
        // enforced in the setter.
        (signingConfig as? SigningConfigImpl)?.seal()
    }

    // DEPRECATED STUFF

    @Suppress("OverridingDeprecatedMember")
    override val compileOptions: JavaCompileOptions
        get()  {
            dslScope.deprecationReporter.reportDeprecatedUsage(
                    "javaCompilationOptions",
                    "compilationOptions",
                    DeprecationReporter.DeprecationTarget.OLD_DSL)
            return _javaCompileOptions.get()
        }

    @Suppress("OverridingDeprecatedMember")
    override fun compileOptions(action: Action<JavaCompileOptions>) {
        dslScope.deprecationReporter.reportDeprecatedUsage(
                "javaCompilationOptions",
                "compilationOptions",
                DeprecationReporter.DeprecationTarget.OLD_DSL)
        action.execute(_javaCompileOptions.get())
    }
}