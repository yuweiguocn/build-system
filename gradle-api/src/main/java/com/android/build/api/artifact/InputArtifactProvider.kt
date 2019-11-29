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

package com.android.build.api.artifact

import org.gradle.api.Incubating

/** A provider of [BuildableArtifact] created via [BuildArtifactTransformBuilder].
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface InputArtifactProvider {
    /**
     * Returns a [BuildableArtifact] for the specified [BuildArtifactType].
     */
    fun getArtifact(type: ArtifactType): BuildableArtifact

    /**
     * Returns a [BuildableArtifact] for the default [BuildArtifactType].
     *
     * Only valid if there is a default [BuildArtifactType].
     */
    val artifact: BuildableArtifact
}
