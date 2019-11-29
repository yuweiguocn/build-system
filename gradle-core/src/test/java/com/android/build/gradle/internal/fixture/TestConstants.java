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

package com.android.build.gradle.internal.fixture;

import com.android.builder.core.AndroidBuilder;
import com.android.sdklib.AndroidVersion;

public final class TestConstants {
    public static final int COMPILE_SDK_VERSION = AndroidVersion.VersionCodes.O_MR1;
    public static final int COMPILE_SDK_VERSION_WITH_GOOGLE_APIS = AndroidVersion.VersionCodes.N;
    public static final String BUILD_TOOL_VERSION =
            AndroidBuilder.DEFAULT_BUILD_TOOLS_REVISION.toString();

    private TestConstants() {}
}
