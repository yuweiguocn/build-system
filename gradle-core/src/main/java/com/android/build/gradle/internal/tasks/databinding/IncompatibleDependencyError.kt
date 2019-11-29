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

package com.android.build.gradle.internal.tasks.databinding

import com.android.build.gradle.options.BooleanOption
import org.gradle.api.GradleException

/**
 * Thrown when a data binding project tries to compile with v1 while it has v2 dependencies
 */
class IncompatibleDependencyError(val packages : List<String>) : GradleException(
        """
            You have some Data Binding dependencies which are compiled with data binding v2.
            You must set ${BooleanOption.ENABLE_DATA_BINDING_V2.propertyName} to true in your
            gradle properties file.
            ${packages.joinToString(",")}
        """.trimIndent()
)