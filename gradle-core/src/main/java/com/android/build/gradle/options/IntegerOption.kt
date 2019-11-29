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

package com.android.build.gradle.options

import com.android.build.gradle.options.Option.Status.STABLE
import com.android.builder.model.AndroidProject

enum class IntegerOption(
    override val propertyName: String,
    override val status: Option.Status = Option.Status.EXPERIMENTAL
) : Option<Int> {
    ANDROID_TEST_SHARD_COUNT("android.androidTest.numShards"),
    ANDROID_SDK_CHANNEL("android.sdk.channel"),

    /**
     * Returns the level of model-only mode.
     *
     * The model-only mode is triggered when the IDE does a sync, and therefore we do things a
     * bit differently (don't throw exceptions for instance). Things evolved a bit over versions and
     * the behavior changes. This reflects the mode to use.
     *
     * @see AndroidProject#MODEL_LEVEL_0_ORIGINAL
     * @see AndroidProject#MODEL_LEVEL_1_SYNC_ISSUE
     * @see AndroidProject#MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD
     * @see AndroidProject#MODEL_LEVEL_4_NEW_DEP_MODEL
     */
    IDE_BUILD_MODEL_ONLY_VERSION(AndroidProject.PROPERTY_BUILD_MODEL_ONLY_VERSIONED, status = STABLE),

    /**
     * The api level for the target device.
     *
     * <p>For preview versions that is the last stable version, and the {@link
     * StringOption#IDE_TARGET_DEVICE_CODENAME} will also be set.
     */
    IDE_TARGET_DEVICE_API(AndroidProject.PROPERTY_BUILD_API, status = STABLE),

    IDE_VERSION_CODE_OVERRIDE(AndroidProject.PROPERTY_VERSION_CODE, status = STABLE),

    /**
     * Size of the buffers in kilobytes used to read .class files and storage for writing .dex files
     * translations into.
     */
    DEXING_READ_BUFFER_SIZE("android.dexingReadBuffer.size"),
    DEXING_WRITE_BUFFER_SIZE("android.dexingWriteBuffer.size"),
    DEXING_NUMBER_OF_BUCKETS("android.dexingNumberOfBuckets"),

    /**
     * Maximum number of dynamic features that can be allocated before Oreo platforms.
     */
    PRE_O_MAX_NUMBER_OF_FEATURES("android.maxNumberOfFeaturesBeforeOreo"),
    ;

    override fun parse(value: Any): Int {
        if (value is CharSequence) {
            try {
                return Integer.parseInt(value.toString())
            } catch (ignored: NumberFormatException) {
                // Throws below.
            }

        }
        if (value is Number) {
            return value.toInt()
        }
        throw IllegalArgumentException(
            "Cannot parse project property "
                    + this.propertyName
                    + "='"
                    + value
                    + "' of type '"
                    + value.javaClass
                    + "' as integer."
        )
    }
}
