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

package com.android.build.gradle.integration.common.utils;

import static com.android.build.gradle.integration.common.utils.NdkHelper.implPlatformSupported;
import static com.google.common.truth.Truth.assertThat;

import com.android.repository.Revision;
import com.android.sdklib.AndroidVersion;
import org.junit.Test;

public class NdkHelperTest {
    @Test
    public void test() {
        assertThat(implPlatformSupported(null, "android-10"))
                .isEqualTo(new AndroidVersion(10, null));

        assertThat(implPlatformSupported(null, "android-24"))
                .isEqualTo(new AndroidVersion(21, null));

        assertThat(implPlatformSupported(new Revision(11), "android-24"))
                .isEqualTo(new AndroidVersion(24, null));

        assertThat(implPlatformSupported(new Revision(11), "android-L"))
                .isEqualTo(new AndroidVersion(20, "L"));

        assertThat(implPlatformSupported(new Revision(15), "android-O"))
                .isEqualTo(new AndroidVersion(26, null));

        assertThat(implPlatformSupported(new Revision(15), "android-27"))
                .isEqualTo(new AndroidVersion(26, null));
    }
}
