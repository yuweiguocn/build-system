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

package com.android.build.gradle.internal.process

import java.io.File
import java.io.Serializable

/**
 * JarSigner facility using the JDK jarsigner binary.
 */
class JarSigner {

    /**
     * WorkerItem compatible parameter to pass signature information to the jarsigner utility
     */
    data class Signature(
        val keystoreFile: File,
        val keystorePassword: String?,
        val keyAlias: String?,
        val keyPassword: String?) : Serializable

    /**
     * Default delegate to sign the files.
     */
    val signer = OpenJDKJarSigner()::sign

    /**
     * Signs the passed [toBeSigned] with the [signature]
     * information.
     */
    fun sign(toBeSigned: File, signature: Signature) = signer(toBeSigned, signature)
}