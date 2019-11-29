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

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.ndk.AbiInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.gradle.api.logging.Logging
import java.io.File
import java.io.FileReader

/**
 * <pre>
 *
 * Read and parse the NDK file meta/abis.json. This file contains a list of ABIs supported by this
 * NDK along with relevant metadata.
 *
 * {
 *    "armeabi-v7a": {
 *       "bitness": 32,
 *       "default": true,
 *       "deprecated": false
 *     },
 *     etc.
 *  }
 *
 *  </pre>
 */
class NdkAbiFile(abiFile: File) {
    private val mapTypeToken = object : TypeToken<Map<String, AbiInfo>>() {}.type
    private val logger = Logging.getLogger(javaClass)
    val abiInfoList: List<AbiInfo>

    init {
        abiInfoList = if (abiFile.isFile) {
            try {
                Gson().fromJson<Map<String, AbiInfo>>(FileReader(abiFile), mapTypeToken)
                    .entries.mapNotNull { entry ->
                    val abi = Abi.getByName(entry.key)
                    if (abi == null) {
                        logger.warn(
                            "Ignoring invalid ABI '${entry.key}' found in ABI " +
                                    "metadata file '$abiFile'."
                        )
                        null

                    } else {
                        AbiInfo(abi, entry.value.isDeprecated, entry.value.isDefault)
                    }
                }
            } catch (e: Throwable) {
                logger.error("Could not parse '$abiFile'.")
                Abi.values().map { AbiInfo(it, false, true) }
            }

        } else {
            Abi.values().map { AbiInfo(it, false, true) }
        }
    }
}

/**
 * Given an NDK root file path, return the name of the ABI metadata JSON file.
 */
fun ndkMetaAbisFile(ndkRoot: File): File {
    return File(ndkRoot, "meta/abis.json")
}