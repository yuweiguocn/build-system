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
import java.io.File

/** A provider of File defined in [BuildArtifactTransformBuilder].
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface OutputFileProvider {
    /**
     * Returns a File with the specified filename to be used as the output of a task.
     *
     * The artifactTypes parameter list will be used to associate the file with. All
     * artifacts types must have been declared as output of the transform task through the
     * [BuildArtifactTransformBuilder.append] or [BuildArtifactTransformBuilder.replace] methods.
     * If the parameter is omitted, all transform task's output artifact types will be used.
     *
     * @param filename the name used when the file is defined through the VariantTaskBuilder.
     * @param artifactTypes the list of artifact types this file will be registered under or the
     * task's output type if not provided.
     * @throws ArtifactConfigurationException if the file name is already defined or one of the
     * passed artifact type has not been declared as a task output.
     */
    @Throws(ArtifactConfigurationException::class)
    fun getFile(filename: String, vararg artifactTypes: ArtifactType): File

    /**
     * Returns a File to be used as the output of a task, with a unique name.
     */
    val file : File
}
