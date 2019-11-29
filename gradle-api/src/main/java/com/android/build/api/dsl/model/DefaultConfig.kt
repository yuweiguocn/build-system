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

package com.android.build.api.dsl.model

import org.gradle.api.Incubating

/**
 * Specifies defaults for variant properties that the Android plugin applies to all build
 * variants.
 *
 * You can override any `defaultConfig` property when
 * [configuring product flavors](https://d.android.com/studio/build/build-variants.html#product-flavors).
 *
 * This interface is not currently usable. It is a work in progress.
 *
 * @see [ProductFlavor][com.android.build.api.dsl.model.ProductFlavor]
 */
@Incubating
interface DefaultConfig : BaseFlavor, BuildTypeOrProductFlavor, ProductFlavorOrVariant, VariantProperties
