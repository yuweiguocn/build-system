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

package com.android.build.api.sourcesets

import org.gradle.api.Incubating
import java.io.File
import org.gradle.api.Named

/** An AndroidSourceFile represents a single file input for an Android project.
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface AndroidSourceFile : Named {

    /**
     * Returns the file.
     * @return the file input.
     */
    var srcFile: File

    /**
     * Sets the location of the file. Returns this object.
     *
     * @param srcPath The source directory. This is evaluated as for
     * [org.gradle.api.Project.file]
     * @return the AndroidSourceFile object
     */
    fun srcFile(srcPath: Any): AndroidSourceFile
}
