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

@file:JvmName("AndroidProjectUtils")
package com.android.build.gradle.integration.common.utils

import com.android.build.gradle.integration.common.fixture.getExtraSourceProviderContainer
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.builder.core.VariantType
import com.android.builder.model.AndroidProject
import com.android.builder.model.ArtifactMetaData
import com.android.builder.model.ProductFlavorContainer
import com.android.builder.model.SigningConfig
import com.android.builder.model.Variant
import java.io.File

/**
 * Returns a Variant object from a given name
 * @param name the name of the variant to return
 * @return the matching variant or null if not found
 */
fun AndroidProject.findVariantByName(name: String): Variant? {
    for (item in variants) {
        if (name == item.name) {
            return item
        }
    }

    return null
}

fun AndroidProject.getVariantByName(name: String): Variant {
    return searchForExistingItem(variants, name, Variant::getName, "Variant")
}

fun AndroidProject.getDebugVariant() = getVariantByName("debug")

fun AndroidProject.getSigningConfig(
        name: String): SigningConfig {
    return searchForExistingItem(
            signingConfigs, name, SigningConfig::getName, "SigningConfig")
}

fun AndroidProject.getArtifactMetaData(name: String): ArtifactMetaData {
    return searchForExistingItem(
            extraArtifacts, name, ArtifactMetaData::getName, "ArtifactMetaData")
}

fun AndroidProject.getProductFlavor(name: String): ProductFlavorContainer {
    return searchForExistingItem(
            productFlavors, name, { it.productFlavor.name }, "ProductFlavorContainer")
}

fun AndroidProject.findTestedBuildType(): String? {
    return variants
            .stream()
            .filter { variant ->
                variant.getOptionalAndroidArtifact(AndroidProject.ARTIFACT_ANDROID_TEST) != null
            }
            .map { it.buildType }
            .findAny()
            .orElse(null)
}

fun AndroidProject.testDefaultSourceSets(projectDir: File) {

    // test the main source provider
    SourceProviderHelper(name, projectDir,
            "main", defaultConfig.sourceProvider)
            .test()

    // test the main androidTest source provider
    val androidTestSourceProviders = defaultConfig.getExtraSourceProviderContainer(
            AndroidProject.ARTIFACT_ANDROID_TEST)

    SourceProviderHelper(
            name,
            projectDir,
            VariantType.ANDROID_TEST_PREFIX,
            androidTestSourceProviders.sourceProvider)
            .test()

    // test the source provider for the build types
    val buildTypes = buildTypes
    TruthHelper.assertThat(buildTypes).named("build types").hasSize(2)

    val testedBuildType = findTestedBuildType()

    for (btContainer in buildTypes) {
        SourceProviderHelper(
                name,
                projectDir,
                btContainer.buildType.name,
                btContainer.sourceProvider)
                .test()

        // For every build type there's the unit test source provider and the android test
        // one (optional).
        val extraSourceProviderNames = btContainer
                .extraSourceProviders
                .map { it.artifactName }
                .toSet()

        if (btContainer.buildType.name == testedBuildType) {
            TruthHelper.assertThat(extraSourceProviderNames)
                    .containsExactly(AndroidProject.ARTIFACT_ANDROID_TEST,
                            AndroidProject.ARTIFACT_UNIT_TEST)
        } else {
            TruthHelper.assertThat(extraSourceProviderNames).containsExactly(AndroidProject.ARTIFACT_UNIT_TEST)
        }
    }
}
