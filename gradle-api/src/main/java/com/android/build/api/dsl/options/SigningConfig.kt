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
import java.io.File
import org.gradle.api.Named

/**
 * A Signing Configuration.
 *
 *
 * See [Signing Your
 * Applications](http://developer.android.com/tools/publishing/app-signing.html)
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface SigningConfig : Named, Initializable<SigningConfig> {

    /**
     * The keystore file.
     */
    var storeFile: File?

    /**
     * The keystore password.
     */
    var storePassword: String?

    /**
     * Returns the key alias name.
     *
     * @return the key alias name.
     */
    var keyAlias: String?

    /**
     * return the key password.
     *
     * @return the password.
     */
    var keyPassword: String?

    /**
     * Returns the store type.
     *
     * @return the store type.
     */
    var storeType: String?

    /** Returns `true` if signing using JAR Signature Scheme (aka v1 scheme) is enabled.  */
    var v1SigningEnabled: Boolean

    /** Returns `true` if signing using APK Signature Scheme v2 (aka v2 scheme) is enabled.  */
    var v2SigningEnabled: Boolean

    /**
     * Returns whether the config is fully configured for signing.
     *
     * @return true if all the required information are present.
     */
    fun isSigningReady(): Boolean

    @Deprecated("Use property v1SigningEnabled")
    fun isV1SigningEnabled(): Boolean

    @Deprecated("Use property v2SigningEnabled")
    fun isV2SigningEnabled(): Boolean
}
