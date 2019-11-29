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

package com.android.build.gradle.internal.ndk;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.core.Abi;
import com.google.gson.annotations.SerializedName;

/** Information about an ABI. */
public class AbiInfo {
    public Abi abi;

    public boolean deprecated;

    @SerializedName("default")
    public boolean defaultAbi;

    // Default constructor to be used by GSON to initialize default values.
    public AbiInfo() {
        abi = null;
        deprecated = false;
        defaultAbi = true;
    }

    public AbiInfo(@NonNull Abi abi, boolean deprecated, boolean isDefault) {
        this.abi = abi;
        this.deprecated = deprecated;
        this.defaultAbi = isDefault;
    }

    @NonNull
    public Abi getAbi() {
        return abi;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public boolean isDefault() {
        return defaultAbi;
    }
}
