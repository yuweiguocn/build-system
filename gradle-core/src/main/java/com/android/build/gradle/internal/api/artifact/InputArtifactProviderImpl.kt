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

package com.android.build.gradle.internal.api.artifact

import com.android.build.api.artifact.ArtifactConfigurationException
import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.api.artifact.InputArtifactProvider
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.EvalIssueReporter
import com.google.common.base.Joiner
import org.gradle.api.file.FileCollection

/**
 * Implementation for InputProvider
 *
 * @param artifactsHolder singleton holding all [BuildableArtifact]s
 * @param referencedInputTypes list of [ArtifactType] that are declared as references to this
 * transform. The "final" version of these artifacts will be returned from the [getArtifact]
 * method.
 * @param transformedInputTypes list of [ArtifactType] that are declared to be appended to or
 * replaced by this transform. The "current" version of these artifacts will be returned from the
 * [getArtifact] method.
 * @param dslScope scoping object for all DSL parsing error reporting.
 */
class InputArtifactProviderImpl(
        private var artifactsHolder: BuildArtifactsHolder,
        private var referencedInputTypes: Collection<ArtifactType>,
        private var transformedInputTypes: Collection<ArtifactType>,
        private var defaultValue : FileCollection,
        private val dslScope: DslScope) : InputArtifactProvider {

    private fun mapOfInputs() : Map<ArtifactType, BuildableArtifact> {
        return transformedInputTypes.map { it to artifactsHolder.getArtifactFiles(it) }.toMap().plus(
            referencedInputTypes.map { it to artifactsHolder.getFinalArtifactFiles(it) }.toMap())
    }

    init {
        // make sure there are not types requested as final values and as intermediate values.
        val common = referencedInputTypes.intersect(transformedInputTypes)
        if (!common.isEmpty()) {
            throw ArtifactConfigurationException(Joiner.on(",").join(common)
                    + " types are requested as intermediates and final types input")
        }
    }

    override val artifact: BuildableArtifact
        get() = with(mapOfInputs()) {
            when {
                isEmpty() -> {
                    dslScope.issueReporter.reportError(
                        EvalIssueReporter.Type.GENERIC,
                        EvalIssueException("No artifacts was defined for input.")
                    )
                    BuildableArtifactImpl(defaultValue)
                }
                size > 1 -> {
                    dslScope.issueReporter.reportError(
                        EvalIssueReporter.Type.GENERIC,
                        EvalIssueException("Multiple inputs types were defined, use getArtifact(ArtifactType) " +
                                "method to disambiguate : " +
                                Joiner.on(",").join(mapOfInputs().keys))
                    )
                    // when doing sync, return empty file collection.
                    BuildableArtifactImpl(defaultValue)
                }
                else -> mapOfInputs().values.single()
            }
        }

    override fun getArtifact(type : ArtifactType): BuildableArtifact {
        val buildableArtifact = mapOfInputs()[type]
        if (buildableArtifact == null) {
            dslScope.issueReporter.reportError(
                EvalIssueReporter.Type.GENERIC,
                EvalIssueException("Artifact was not defined for input of type: $type.")
            )
            return BuildableArtifactImpl(defaultValue)
        }
        return buildableArtifact
    }
}
