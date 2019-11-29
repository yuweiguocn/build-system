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

package com.android.build.gradle.integration.common.fixture

import com.android.builder.model.AndroidProject
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeVariantAbi
import com.android.builder.model.Variant
import java.io.Serializable

/**
 * Represents a composite model for AndroidProject model plus Variant models.
 * When AndroidProject is requested with parameterized APIs, the AndroidProject model doesn't
 * contain Variant, and Variant model is requested separately.
 */
data class ParameterizedAndroidProject(
        val androidProject: AndroidProject,
        val variants: List<Variant>,
        val nativeAndroidProject: NativeAndroidProject?,
        val nativeVariantAbis: List<NativeVariantAbi>) : Serializable