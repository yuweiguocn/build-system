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

import org.gradle.api.Action
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.api.tasks.incremental.InputFileDetails
import java.io.File

class FakeIncrementalTaskInputs(
    val incremental: Boolean = false,
    val added: Collection<File> = emptyList(),
    val removed: Collection<File> = emptyList(),
    val modified: Collection<File> = emptyList()
    ): IncrementalTaskInputs {

    override fun outOfDate(action: Action<in InputFileDetails>?) {
        for (file in added) {
            action?.execute(InputFileDetailsImpl(file, FileStatus.ADDED))
        }
        for (file in removed) {
            action?.execute(InputFileDetailsImpl(file, FileStatus.REMOVED))
        }
        for (file in modified) {
            action?.execute(InputFileDetailsImpl(file, FileStatus.ADDED))
        }
    }

    override fun isIncremental(): Boolean = incremental

    override fun removed(action: Action<in InputFileDetails>?) {
        for (file in removed) {
            action?.execute(InputFileDetailsImpl(file, FileStatus.REMOVED))
        }
    }
}

class InputFileDetailsImpl(val myFile: File, val status: FileStatus = FileStatus.ADDED): InputFileDetails {
    override fun getFile(): File = myFile

    override fun isModified(): Boolean = status == FileStatus.MODIFIED

    override fun isAdded(): Boolean = status == FileStatus.ADDED

    override fun isRemoved(): Boolean = status == FileStatus.REMOVED
}

enum class FileStatus {
    MODIFIED,
    ADDED,
    REMOVED
}