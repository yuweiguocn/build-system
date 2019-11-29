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

package com.android.build.gradle.internal.api.dsl.options

import com.android.build.api.dsl.options.SigningConfig
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.build.gradle.internal.errors.DeprecationReporter
import java.io.File
import javax.inject.Inject

@Suppress("OverridingDeprecatedMember")
open class SigningConfigImpl @Inject constructor(
            private val named : String,
            dslScope: DslScope)
        : SealableObject(dslScope), SigningConfig {

    override fun getName(): String = named

    override var storeFile: File? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var storePassword: String? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var keyAlias: String? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var keyPassword: String? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var storeType: String? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var v1SigningEnabled: Boolean = true
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var v2SigningEnabled: Boolean = true
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override fun isSigningReady() = storeFile != null &&  storePassword != null && keyAlias != null && keyPassword != null

    override fun isV1SigningEnabled(): Boolean {
        dslScope.deprecationReporter.reportDeprecatedUsage(
                "SigningConfig.v1SigningEnabled",
                "SigningConfig.isV1SigningEnabled()",
                DeprecationReporter.DeprecationTarget.OLD_DSL)
        return v1SigningEnabled
    }

    override fun isV2SigningEnabled(): Boolean {
        dslScope.deprecationReporter.reportDeprecatedUsage(
                "SigningConfig.v2SigningEnabled",
                "SigningConfig.isV2SigningEnabled()",
                DeprecationReporter.DeprecationTarget.OLD_DSL)
        return v2SigningEnabled
    }

    override fun initWith(that: SigningConfig) {
        if (checkSeal()) {
            storeFile = that.storeFile
            storePassword = that.storePassword
            keyAlias = that.keyAlias
            keyPassword = that.keyPassword
            v1SigningEnabled = that.v1SigningEnabled
            v2SigningEnabled = that.v2SigningEnabled
        }
    }
}