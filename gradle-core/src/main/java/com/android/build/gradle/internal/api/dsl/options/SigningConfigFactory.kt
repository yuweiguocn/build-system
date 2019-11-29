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
import com.android.builder.core.BuilderConstants
import org.gradle.api.NamedDomainObjectFactory
import java.io.File

private const val DEFAULT_PASSWORD = "android"
private const val DEFAULT_ALIAS = "AndroidDebugKey"

class SigningConfigFactory(
            private val dslScope: DslScope,
            private val defaultDebugKeystoreLocation: File)
        : NamedDomainObjectFactory<SigningConfig> {

    override fun create(name: String): SigningConfig {
        val newInstance = dslScope.objectFactory.newInstance(SigningConfigImpl::class.java,
                name, dslScope)

        if (BuilderConstants.DEBUG == name) {
            newInstance.storeFile = defaultDebugKeystoreLocation
            newInstance.storePassword = DEFAULT_PASSWORD
            newInstance.keyAlias = DEFAULT_ALIAS
            newInstance.keyPassword = DEFAULT_PASSWORD
        }

        return newInstance
    }
}