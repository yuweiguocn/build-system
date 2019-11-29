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

package com.android.build.api.attributes;

import org.gradle.api.Named;

/**
 * Type for the attribute holding ProductFlavor information.
 *
 * <p>There can be more than one attribute associated to each {@link
 * org.gradle.api.artifacts.Configuration} object, where each represents a different flavor
 * dimension.
 *
 * <p>The key should be created with <code>Attribute.of(dimensionName, ProductFlavorAttr.class)
 * </code>
 */
public interface ProductFlavorAttr extends Named {}
