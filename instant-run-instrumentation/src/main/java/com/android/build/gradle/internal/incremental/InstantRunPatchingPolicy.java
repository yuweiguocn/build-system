/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.incremental;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidProject;
import com.android.sdklib.AndroidVersion;


/**
 * Patching policy for delivering incremental code changes and triggering a cold start (application
 * restart).
 */
public enum InstantRunPatchingPolicy {

    /** Denotes instant-run disabled builds. */
    UNKNOWN_PATCHING_POLICY,

    /**
     * For L and above, each shard dex file described above will be packaged in a single pure split
     * APK that will be pushed and installed on the device using adb install-multiple commands.
     */
    MULTI_APK,

    /**
     * For O and above, we ship the resources in a separate APK from the main APK.
     *
     * <p>In a near future, this can be merged with the {@link #MULTI_APK} case but - we need to
     * test this thoroughly back to 21. - we need aapt2 in the stable build tools to support all
     * cases.
     */
    MULTI_APK_SEPARATE_RESOURCES;


    /**
     * Returns the patching policy following the {@link AndroidProject#PROPERTY_BUILD_API} value
     * passed by Android Studio.
     *
     * @param androidVersion the android version of the target device
     * @return a {@link InstantRunPatchingPolicy} instance.
     */
    @NonNull
    public static InstantRunPatchingPolicy getPatchingPolicy(
            AndroidVersion androidVersion,
            boolean createSeparateApkForResources) {

        if (androidVersion.getFeatureLevel() < AndroidVersion.ART_RUNTIME.getFeatureLevel()) {
            return UNKNOWN_PATCHING_POLICY;
        } else {
            return androidVersion.getFeatureLevel() >= AndroidVersion.VersionCodes.O
                            && createSeparateApkForResources
                    ? MULTI_APK_SEPARATE_RESOURCES
                    : MULTI_APK;
        }
    }

}
