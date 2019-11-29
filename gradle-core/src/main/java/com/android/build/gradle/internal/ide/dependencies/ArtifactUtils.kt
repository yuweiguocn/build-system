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

@file:JvmName("ArtifactUtils")
package com.android.build.gradle.internal.ide.dependencies

import com.android.build.gradle.internal.dependency.ArtifactCollectionWithExtraArtifact
import com.android.build.gradle.internal.ide.DependencyFailureHandler
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.VariantScope
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Maps
import com.google.common.collect.Sets
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import java.util.HashSet

/**
 * Returns a set of ResolvedArtifact where the [ResolvedArtifact.getDependencyType] and
 * [ResolvedArtifact.isWrappedModule] fields have been setup properly.
 *
 * @param variantScope the variant to get the artifacts from
 * @param consumedConfigType the type of the dependency to resolve (compile vs runtime)
 * @param dependencyFailureHandler handler for dependency resolution errors
 * @param buildMapping a build mapping from build name to root dir.
 */
fun getAllArtifacts(
    variantScope: VariantScope,
    consumedConfigType: AndroidArtifacts.ConsumedConfigType,
    dependencyFailureHandler: DependencyFailureHandler?,
    buildMapping: ImmutableMap<String, String>
): Set<ResolvedArtifact> {
    // FIXME change the way we compare dependencies b/64387392

    // we need to figure out the following:
    // - Is it an external dependency or a sub-project?
    // - Is it an android or a java dependency

    // Querying for JAR type gives us all the dependencies we care about, and we can use this
    // to differentiate external vs sub-projects (to a certain degree).
    // Note: Query for JAR instead of PROCESSED_JAR due to b/110054209
    val allArtifactList = computeArtifactList(
        variantScope,
        consumedConfigType,
        AndroidArtifacts.ArtifactScope.ALL,
        AndroidArtifacts.ArtifactType.JAR
    )

    // Then we can query for MANIFEST that will give us only the Android project so that we
    // can detect JAVA vs ANDROID.
    val manifestList = computeArtifactList(
        variantScope,
        consumedConfigType,
        AndroidArtifacts.ArtifactScope.ALL,
        AndroidArtifacts.ArtifactType.MANIFEST
    )
    val nonNamespacedManifestList = computeArtifactList(
        variantScope,
        consumedConfigType,
        AndroidArtifacts.ArtifactScope.ALL,
        AndroidArtifacts.ArtifactType.NON_NAMESPACED_MANIFEST
    )

    // We still need to understand wrapped jars and aars. The former is difficult (TBD), but
    // the latter can be done by querying for EXPLODED_AAR. If a sub-project is in this list,
    // then we need to override the type to be external, rather than sub-project.
    // This is why we query for Scope.ALL
    // But we also simply need the exploded AARs for external Android dependencies so that
    // Studio can access the content.
    val explodedAarList = computeArtifactList(
        variantScope,
        consumedConfigType,
        AndroidArtifacts.ArtifactScope.ALL,
        AndroidArtifacts.ArtifactType.EXPLODED_AAR
    )

    // We also need the actual AARs so that we can get the artifact location and find the source
    // location from it.
    // Note: Query for AAR instead of PROCESSED_AAR due to b/110054209
    val aarList = computeArtifactList(
        variantScope,
        consumedConfigType,
        AndroidArtifacts.ArtifactScope.EXTERNAL,
        AndroidArtifacts.ArtifactType.AAR
    )

    // collect dependency resolution failures
    if (dependencyFailureHandler != null) {
        // compute the name of the configuration
        dependencyFailureHandler!!.addErrors(
            variantScope.globalScope.project.path
                    + "@"
                    + variantScope.fullVariantName
                    + "/"
                    + consumedConfigType.getName(),
            allArtifactList.failures
        )
    }

    // build a list of wrapped AAR, and a map of all the exploded-aar artifacts
    val wrapperModules = HashSet<ComponentIdentifier>()
    val explodedAarArtifacts = explodedAarList.artifacts
    val explodedAarResults =
        Maps.newHashMapWithExpectedSize<ComponentIdentifier, ResolvedArtifactResult>(
            explodedAarArtifacts.size
        )
    for (result in explodedAarArtifacts) {
        val componentIdentifier = result.id.componentIdentifier
        if (componentIdentifier is ProjectComponentIdentifier) {
            wrapperModules.add(componentIdentifier)
        }
        explodedAarResults[componentIdentifier] = result
    }

    val aarArtifacts = aarList.artifacts
    val aarResults =
        Maps.newHashMapWithExpectedSize<ComponentIdentifier, ResolvedArtifactResult>(aarArtifacts.size)
    for (result in aarArtifacts) {
        aarResults[result.id.componentIdentifier] = result
    }

    // build a list of android dependencies based on them publishing a MANIFEST element
    val manifestArtifacts = HashSet<ResolvedArtifactResult>()
    manifestArtifacts.addAll(manifestList.artifacts)
    manifestArtifacts.addAll(nonNamespacedManifestList.artifacts)

    val manifestIds = Sets.newHashSetWithExpectedSize<ComponentIdentifier>(manifestArtifacts.size)
    for (result in manifestArtifacts) {
        manifestIds.add(result.id.componentIdentifier)
    }

    // build the final list, using the main list augmented with data from the previous lists.
    val allArtifacts = allArtifactList.artifacts

    // use a linked hash set to keep the artifact order.
    val artifacts = Sets.newLinkedHashSetWithExpectedSize<ResolvedArtifact>(allArtifacts.size)

    for (artifact in allArtifacts) {
        val componentIdentifier = artifact.id.componentIdentifier

        // check if this is a wrapped module
        val isWrappedModule = wrapperModules.contains(componentIdentifier)

        // check if this is an android external module. In this case, we want to use the exploded
        // aar as the artifact we depend on rather than just the JAR, so we swap out the
        // ResolvedArtifactResult.
        var dependencyType = ResolvedArtifact.DependencyType.JAVA

        // in case of AAR, the current artifact is the extracted artifacts. It needs to
        // be swapped with the AAR bundle one.
        var mainArtifact = artifact
        var extractedAar: ResolvedArtifactResult? = null

        // optional result that will point to the artifact (AAR) when the current result
        // is the exploded AAR.
        if (manifestIds.contains(componentIdentifier)) {
            dependencyType = ResolvedArtifact.DependencyType.ANDROID
            // if it's an android dependency, we swap out the manifest result for the exploded
            // AAR result.
            // If the exploded AAR is null then it's a sub-project and we can keep the manifest
            // as the Library we'll create will be a ModuleLibrary which doesn't care about
            // the artifact file anyway.
            val explodedAar = explodedAarResults[componentIdentifier]
            if (explodedAar != null) {
                extractedAar = explodedAar
                // and we need the AAR bundle itself (if it exists)
                mainArtifact = aarResults[componentIdentifier] ?: mainArtifact
            }
        }

        artifacts.add(
            ResolvedArtifact(
                mainArtifact,
                extractedAar,
                dependencyType,
                isWrappedModule,
                buildMapping
            )
        )
    }

    return artifacts
}


fun computeArtifactList(
    variantScope: VariantScope,
    consumedConfigType: AndroidArtifacts.ConsumedConfigType,
    scope: AndroidArtifacts.ArtifactScope,
    type: AndroidArtifacts.ArtifactType
): ArtifactCollection {
    val artifacts = variantScope.getArtifactCollection(consumedConfigType, scope, type)

    // because the ArtifactCollection could be a collection over a test variant which ends
    // up being a ArtifactCollectionWithExtraArtifact, we need to get the actual list
    // without the tested artifact.
    return (artifacts as? ArtifactCollectionWithExtraArtifact)?.parentArtifacts ?: artifacts
}
