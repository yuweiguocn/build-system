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

@file:JvmName("ArtifactTypeUtils")
package com.android.build.gradle.internal.api.artifact

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.BuildArtifactType
import com.android.build.gradle.internal.scope.AnchorOutputType
import com.android.build.gradle.internal.scope.InternalArtifactType

/**
 * Utility class for [ArtifactType]
 */

private val sourceArtifactMap : Map<String, ArtifactType> =
        SourceArtifactType.values().associateBy(ArtifactType::name)
private val buildArtifactMap : Map<String, ArtifactType> =
        BuildArtifactType.values().associateBy(ArtifactType::name)
private val internalArtifactMap : Map<String, ArtifactType> =
        InternalArtifactType.values().associateBy(ArtifactType::name)
private val anchorArtifactMap : Map<String, ArtifactType> =
        AnchorOutputType.values().associateBy(ArtifactType::name)

/**
 * Return the enum of [ArtifactType] base on the name.
 *
 * The typical implementation of valueOf in an enum class cannot be used because there are
 * multiple implementations of [ArtifactType].  For this to work, the name of all
 * [ArtifactType] must be unique across all implementations.
 */
fun String.toArtifactType() : ArtifactType =
    sourceArtifactMap[this] ?:
            buildArtifactMap[this]  ?:
            internalArtifactMap[this] ?:
            anchorArtifactMap[this] ?:
            throw IllegalArgumentException("'$this' is not a value ArtifactType.")
