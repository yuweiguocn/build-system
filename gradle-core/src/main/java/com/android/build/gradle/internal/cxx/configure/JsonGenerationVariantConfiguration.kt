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

import com.android.repository.Revision
import java.io.File

/**
 * Configuration information for generating C++ android_gradle_build.json.
 */
data class JsonGenerationVariantConfiguration(
    @JvmField val rootBuildGradlePath : File,
    @JvmField val buildSystem : NativeBuildSystemVariantConfig,
    @JvmField val variantName : String,
    @JvmField val makefile: File,
    @JvmField val sdkFolder: File,
    @JvmField val ndkFolder: File,
    @JvmField val soFolder: File,
    @JvmField val objFolder: File,
    @JvmField val jsonFolder: File,
    @JvmField val debuggable: Boolean,
    @JvmField val abiConfigurations: List<JsonGenerationAbiConfiguration>,
    @JvmField val ndkVersion : Revision,
    @JvmField val generatedJsonFiles : List<File>,
    @JvmField val compilerSettingsCacheFolder : File,
    @JvmField val enableCmakeCompilerSettingsCache : Boolean
)