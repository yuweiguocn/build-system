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

@file:JvmName("ProjectBuildOutputUtils")
package com.android.build.gradle.integration.common.utils

import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.builder.core.BuilderConstants
import com.android.builder.model.ProjectBuildOutput
import com.android.builder.model.VariantBuildOutput
import com.google.common.collect.Iterables
import com.google.common.truth.Truth
import org.junit.Assert
import java.io.File

/**
 * Returns the APK file for a single-output variant.
 *
 * @param variantName the name of the variant to return
 * @return the output file, always, or assert before.
 */
fun ProjectBuildOutput.findOutputFileByVariantName(variantName: String): File {

    val variantOutput = getVariantBuildOutput(variantName)
    Assert.assertNotNull("variant '$variantName' null-check", variantOutput)

    val variantOutputFiles = variantOutput.outputs
    Assert.assertNotNull("variantName '$variantName' outputs null-check", variantOutputFiles)
    // we only support single output artifact in this helper method.
    Assert.assertEquals(
            "variantName '$variantName' outputs size check",
            1,
            variantOutputFiles.size.toLong())

    val output = variantOutputFiles.iterator().next()
    Assert.assertNotNull(
            "variantName '$variantName' single output null-check",
            output)

    val outputFile = output.outputFile
    Assert.assertNotNull("variantName '$variantName' mainOutputFile null-check", outputFile)

    return outputFile
}

fun ProjectBuildOutput.compareDebugAndReleaseOutput() {
    Truth.assertThat(variantsBuildOutput).named("variant count").hasSize(2)

    // debug variant
    val debugVariant = getVariantBuildOutput(BuilderConstants.DEBUG)

    // release variant
    val releaseVariant = getVariantBuildOutput(BuilderConstants.RELEASE)

    val debugFile = Iterables.getOnlyElement(debugVariant.outputs).outputFile
    val releaseFile = Iterables.getOnlyElement(releaseVariant.outputs).outputFile

    Assert.assertFalse("debug: $debugFile / release: $releaseFile",
            debugFile == releaseFile)
}

/**
 * Convenience method to verify that the given ProjectBuildOutput contains exactly two variants,
 * then return the "debug" variant. This is most useful for integration tests building projects
 * with no extra buildTypes and no specified productFlavors.
 *
 * @return the build output for the "debug" variant
 * @throws AssertionError if the model contains more than two variants, or does not have a
 * "debug" variant
 */
fun ProjectBuildOutput.getDebugVariantBuildOutput(): VariantBuildOutput {
    TruthHelper.assertThat(variantsBuildOutput).hasSize(2)
    val debugVariantOutput = getVariantBuildOutput(BuilderConstants.DEBUG)
    TruthHelper.assertThat(debugVariantOutput).isNotNull()
    return debugVariantOutput
}

/**
 * Gets the VariantBuildOutput with the given name.
 *
 * @param name the name to match, e.g. [com.android.builder.core.BuilderConstants.DEBUG]
 * @return the only item with the given name
 * @throws AssertionError if no items match or if multiple items match
 */
fun ProjectBuildOutput.getVariantBuildOutput(name: String): VariantBuildOutput {
    return searchForExistingItem(
            variantsBuildOutput, name, VariantBuildOutput::getName, "VariantBuildOutput")
}
