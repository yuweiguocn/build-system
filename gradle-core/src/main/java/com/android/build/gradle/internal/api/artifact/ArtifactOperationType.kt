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

package com.android.build.gradle.internal.api.artifact

import com.android.build.api.artifact.BuildableArtifact

/**
 * Defines how the output of the new task interacts with the original [BuildableArtifact].
 */
enum class ArtifactOperationType {
    // Replaces the output of the original task.  Subsequent tasks using the output will see
    // only the output of the new task.
    REPLACE,

    // Supplement the output of the original task.  Subsequent tasks using the output will see
    // *both* the output of the new task and the original task.
    APPEND
}
