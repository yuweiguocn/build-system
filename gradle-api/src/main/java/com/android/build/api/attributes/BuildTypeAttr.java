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

import org.gradle.api.attributes.Attribute;

/**
 * Type for the attribute holding BuildType information.
 *
 * <p>There should only be one build type attribute associated to each {@link
 * org.gradle.api.artifacts.Configuration} object. The key should be {@link #ATTRIBUTE}.
 */
public interface BuildTypeAttr extends org.gradle.api.Named {

    Attribute<BuildTypeAttr> ATTRIBUTE = Attribute.of(BuildTypeAttr.class);
}
