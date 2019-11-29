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

@file:JvmName("VariantBuildOutputUtils")
package com.android.build.gradle.integration.common.utils

import com.android.build.OutputFile
import com.android.build.VariantOutput
import com.android.builder.model.VariantBuildOutput

/**
 * Searches the given collection of OutputFiles and returns the single item with outputType
 * equal to [VariantOutput.MAIN].
 *
 * @return the single item with type MAIN
 * @throws AssertionError if none of the outputFiles has type MAIN
 * @throws IllegalArgumentException if multiple items have type MAIN
 */
fun VariantBuildOutput.getMainOutputFile(): OutputFile {
    return outputs
            .stream()
            .filter { file -> file.outputType == VariantOutput.MAIN }
            .reduce(toSingleItem())
            .orElseThrow {
                AssertionError(
                        "Unable to find main output file. Options are: " + outputs)
            } // Unsure about this
}
