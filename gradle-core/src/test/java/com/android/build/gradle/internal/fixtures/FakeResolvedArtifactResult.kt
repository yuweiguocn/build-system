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

package com.android.build.gradle.internal.fixtures

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.component.Artifact
import java.io.File

class FakeResolvedArtifactResult(
    private val file: File, private val identifier: ComponentIdentifier
) : ResolvedArtifactResult {
    override fun getFile() = file

    override fun getId() = object : ComponentArtifactIdentifier {
        override fun getDisplayName() = identifier.displayName

        override fun getComponentIdentifier() = identifier
    }

    override fun getType(): Class<out Artifact> {
        TODO("not implemented")
    }

    override fun getVariant(): ResolvedVariantResult {
        TODO("not implemented")
    }
}
