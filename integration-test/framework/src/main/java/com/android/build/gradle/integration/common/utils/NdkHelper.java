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

package com.android.build.gradle.integration.common.utils;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.ndk.DefaultNdkInfo;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.ndk.NdkInfo;
import com.android.repository.Revision;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.Collection;
import java.util.Objects;

/**
 * Ndk related helper functions.
 */
public class NdkHelper {

    /**
     * Gets the platform version supported by the specified ndk directory with specified upper bound
     * version.
     *
     * @param ndkDir path to the NDK dir
     * @param upperBoundVersionHash maximum allowed version (e.g. 'android-23'), can also be a
     *     preview version (e.g. 'android-O')
     * @return the platform version supported by this ndk
     */
    @NonNull
    public static AndroidVersion getPlatformSupported(
            @NonNull File ndkDir, @NonNull String upperBoundVersionHash) {
        Revision ndkRevision = NdkHandler.findRevision(ndkDir);
        return implPlatformSupported(ndkRevision, upperBoundVersionHash);
    }

    @VisibleForTesting
    static AndroidVersion implPlatformSupported(
            @Nullable Revision ndkRevision, @NonNull String upperBoundVersionHash) {
        int major = ndkRevision != null ? ndkRevision.getMajor() : 10;
        // for r10 max platform is 21, r11 max is 24, r12 max platform is 24
        ImmutableMap<Integer, AndroidVersion> perVersion =
                ImmutableMap.<Integer, AndroidVersion>builder()
                        .put(10, new AndroidVersion(21, null))
                        .put(11, new AndroidVersion(24, null))
                        .put(12, new AndroidVersion(24, null))
                        .put(13, new AndroidVersion(24, null))
                        .put(14, new AndroidVersion(24, null))
                        .put(15, new AndroidVersion(26, null))
                        .build();

        AndroidVersion maxVersion =
                Objects.requireNonNull(AndroidTargetHash.getPlatformVersion(upperBoundVersionHash));
        AndroidVersion ndkMaxSupported = perVersion.get(major);

        if (maxVersion.getFeatureLevel() < ndkMaxSupported.getFeatureLevel()) {
            return maxVersion;
        } else {
            return ndkMaxSupported;
        }
    }

    @NonNull
    public static NdkInfo getNdkInfo() {
        return new DefaultNdkInfo(new File(SdkHelper.findSdkDir(), SdkConstants.FD_NDK));
    }

    @NonNull
    public static NdkInfo getNdkInfo(GradleTestProject project) {
        return new DefaultNdkInfo(project.getAndroidNdkHome());
    }

    @NonNull
    public static Collection<Abi> getAbiList(GradleTestProject project) {
        NdkInfo info = getNdkInfo(project);
        return info.getDefaultAbis();
    }
}
