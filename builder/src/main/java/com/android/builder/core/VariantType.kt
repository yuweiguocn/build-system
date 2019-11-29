/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.builder.core

import com.android.builder.model.AndroidProject
import com.android.builder.model.ArtifactMetaData
import com.google.common.collect.ImmutableList
import com.google.wireless.android.sdk.stats.GradleBuildVariant

/**
 * Type of a variant.
 */
interface VariantType {
    /**
     * Returns true is the variant outputs an AAR.
     */
    val isAar: Boolean

    /**
     * Returns true is the variant outputs an APK.
     */
    val isApk: Boolean

    /**
     * Returns true is the variant is a base module. This is only true if it can have features.
     * If the variant can never have feature (TEST modules for instance), then this is false.
     */
    val isBaseModule: Boolean

    /**
     * Returns true if the variant is a feature split/optional module.
     */
    val isFeatureSplit: Boolean

    /**
     * Returns true if the variant is a dual-type. This is only valid for BASE_FEATURE/FEATURE.
     * The library component of a feature returns false.
     */
    val isHybrid: Boolean

    /**
     * Returns true if the variant is a dynamic feature i.e. an optional apk. [isFeatureSplit]
     * differs from this property as it will be true for feature splits, while this property will
     * be false for those.
     */
    val isDynamicFeature: Boolean

    /**
     * Returns true if the variant is a instant app feature split.
     *
     * This does not include instant app base features.
     *
     * [isFeatureSplit] differs from this property as it will be true for dynamic app features,
     * while this property will be false for those.
     */
    val isInstantAppFeatureSplit: Boolean

    /**
     * Returns true if the variant publishes artifacts to meta-data.
     */
    val publishToMetadata: Boolean

    /**
     * Returns true if this is the test component of the module.
     */
    val isTestComponent: Boolean
    /**
     * Returns true if the variant is a test variant, whether this is the test component of a module
     * (testing the prod component of the same module) or a separate test-only module.
     */
    val isForTesting: Boolean
    /**
     * Returns prefix used for naming source directories. This is an empty string in
     * case of non-testing variants and a camel case string otherwise, e.g. "androidTest".
     */
    val prefix: String
    /**
     * Returns suffix used for naming Gradle tasks. This is an empty string in
     * case of non-testing variants and a camel case string otherwise, e.g. "AndroidTest".
     */
    val suffix: String
    /**
     * Whether the artifact type supports only a single build type.
     */
    val isSingleBuildType: Boolean
    /**
     * Returns the name used in the builder model for artifacts that correspond to this variant
     * type.
     */
    val artifactName: String
    /**
     * Returns the artifact type used in the builder model.
     */
    val artifactType: Int
    /**
     * Whether the artifact type should export the data binding class list.
     */
    val isExportDataBindingClassList: Boolean
    /**
     * Returns the corresponding variant type used by the analytics system.
     */
    val analyticsVariantType: GradleBuildVariant.VariantType
    /** Whether this variant can have split outputs.  */
    val canHaveSplits: Boolean

    val consumeType: String
    val publishType: String?

    /** the name of the hybrid sub-type */
    val hybridName: String?

    val name: String

    companion object {
        const val ANDROID_TEST_PREFIX = "androidTest"
        const val ANDROID_TEST_SUFFIX = "AndroidTest"
        const val UNIT_TEST_PREFIX = "test"
        const val UNIT_TEST_SUFFIX = "UnitTest"

        val testComponents: ImmutableList<VariantType>
            get() {
                val result = ImmutableList.builder<VariantType>()
                for (variantType in VariantTypeImpl.values()) {
                    if (variantType.isTestComponent) {
                        result.add(variantType)
                    }
                }
                return result.build()
            }

    }
}

// TODO: synchronize with AndroidTypeAttr somehow. Probably move this to gradle-core with new API/DSL...
const val ATTR_APK = "Apk"
const val ATTR_AAR = "Aar"
const val ATTR_FEATURE = "Feature"
const val ATTR_METADATA = "Metadata"

enum class VariantTypeImpl(
    override val isAar: Boolean = false,
    override val isApk: Boolean = false,
    override val isBaseModule: Boolean = false,
    override val isHybrid: Boolean = false,
    override val isDynamicFeature: Boolean = false,
    override val isInstantAppFeatureSplit: Boolean = false,
    override val publishToMetadata: Boolean = false,
    override val isForTesting: Boolean = false,
    override val prefix: String,
    override val suffix: String,
    override val isSingleBuildType: Boolean = false,
    override val artifactName: String,
    override val artifactType: Int,
    override val isExportDataBindingClassList: Boolean = false,
    override val analyticsVariantType: GradleBuildVariant.VariantType,
    override val canHaveSplits: Boolean = false,
    private val consumeTypeOptional: String?,
    override val publishType: String?,
    override val hybridName: String? = null
): VariantType {
    BASE_APK(
        isApk = true,
        isBaseModule = true,
        publishToMetadata = true,
        prefix = "",
        suffix = "",
        artifactName = AndroidProject.ARTIFACT_MAIN,
        artifactType = ArtifactMetaData.TYPE_ANDROID,
        analyticsVariantType = GradleBuildVariant.VariantType.APPLICATION,
        canHaveSplits = true,
        consumeTypeOptional = ATTR_AAR,
        publishType = ATTR_APK
    ),
    OPTIONAL_APK(
        isApk = true,
        isDynamicFeature = true,
        publishToMetadata = true,
        prefix = "",
        suffix = "",
        artifactName = AndroidProject.ARTIFACT_MAIN,
        artifactType = ArtifactMetaData.TYPE_ANDROID,
        analyticsVariantType = GradleBuildVariant.VariantType.OPTIONAL_APK,
        canHaveSplits = true,
        consumeTypeOptional = ATTR_APK,
        publishType = ATTR_APK),
    BASE_FEATURE(
        isApk = true,
        isBaseModule = true,
        isHybrid = true,
        prefix = "",
        suffix = "",
        artifactName = AndroidProject.ARTIFACT_MAIN,
        artifactType = ArtifactMetaData.TYPE_ANDROID,
        analyticsVariantType = GradleBuildVariant.VariantType.FEATURE,
        canHaveSplits = true,
        consumeTypeOptional = ATTR_AAR,
        publishType = ATTR_FEATURE,
        hybridName = "feature"
    ),
    FEATURE(
        isApk = true,
        isInstantAppFeatureSplit = true,
        isHybrid = true,
        publishToMetadata = true,
        prefix = "",
        suffix = "",
        artifactName = AndroidProject.ARTIFACT_MAIN,
        artifactType = ArtifactMetaData.TYPE_ANDROID,
        analyticsVariantType = GradleBuildVariant.VariantType.FEATURE,
        canHaveSplits = true,
        consumeTypeOptional = ATTR_FEATURE,
        publishType = ATTR_FEATURE,
        hybridName = "feature"
    ),
    LIBRARY(
        isAar = true,
        prefix = "",
        suffix = "",
        artifactName = AndroidProject.ARTIFACT_MAIN,
        artifactType = ArtifactMetaData.TYPE_ANDROID,
        isExportDataBindingClassList = true,
        analyticsVariantType = GradleBuildVariant.VariantType.LIBRARY,
        canHaveSplits = true,
        consumeTypeOptional = ATTR_AAR,
        publishType = ATTR_AAR,
        hybridName = "aar"), // aar is a non hybrid but has a hybrid name to differentiate from (BASE_)FEATURE.
    INSTANTAPP(
        prefix = "",
        suffix = "",
        artifactName = AndroidProject.ARTIFACT_MAIN,
        artifactType = ArtifactMetaData.TYPE_ANDROID,
        analyticsVariantType = GradleBuildVariant.VariantType.INSTANTAPP,
        consumeTypeOptional = ATTR_FEATURE,
        publishType = null
    ),
    TEST_APK(
        isApk = true,
        isForTesting = true,
        prefix = "",
        suffix = "",
        artifactName = AndroidProject.ARTIFACT_MAIN,
        artifactType = ArtifactMetaData.TYPE_ANDROID,
        analyticsVariantType = GradleBuildVariant.VariantType.TEST_APK,
        consumeTypeOptional = ATTR_APK,
        publishType = null),
    ANDROID_TEST(
        isApk = true,
        isForTesting = true,
        prefix = VariantType.ANDROID_TEST_PREFIX,
        suffix = VariantType.ANDROID_TEST_SUFFIX,
        isSingleBuildType = true,
        artifactName = AndroidProject.ARTIFACT_ANDROID_TEST,
        artifactType = ArtifactMetaData.TYPE_ANDROID,
        analyticsVariantType = GradleBuildVariant.VariantType.ANDROID_TEST,
        consumeTypeOptional = null,
        publishType = null),
    UNIT_TEST(
        isForTesting = true,
        prefix = VariantType.UNIT_TEST_PREFIX,
        suffix = VariantType.UNIT_TEST_SUFFIX,
        isSingleBuildType = true,
        artifactName = AndroidProject.ARTIFACT_UNIT_TEST,
        artifactType = ArtifactMetaData.TYPE_JAVA,
        analyticsVariantType = GradleBuildVariant.VariantType.UNIT_TEST,
        consumeTypeOptional = null,
        publishType = null);

    override val isFeatureSplit: Boolean
        get() = isInstantAppFeatureSplit || isDynamicFeature

    override val isTestComponent: Boolean
        get() = isForTesting && this != TEST_APK

    override val consumeType: String
        get() = consumeTypeOptional ?: throw RuntimeException("Unsupported consumeType for VariantType: ${this.name}")
}
