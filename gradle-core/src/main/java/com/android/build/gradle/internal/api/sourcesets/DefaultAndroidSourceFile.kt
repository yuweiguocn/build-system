/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle.internal.api.sourcesets

import com.android.build.api.sourcesets.AndroidSourceFile
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import java.io.File

/**
 */
class DefaultAndroidSourceFile internal constructor(private val name: String,
        private val filesProvider: FilesProvider,
        dslScope: DslScope) : SealableObject(dslScope), AndroidSourceFile {

    private lateinit var _srcFile: File

    override fun getName(): String {
        return name
    }

    override var srcFile: File
        get() = _srcFile
        set(value) {
            if (checkSeal()) {
                _srcFile = value
            }
        }

    override fun srcFile(srcPath: Any): AndroidSourceFile {
        srcFile = filesProvider.file(srcPath)
        return this
    }

    override fun toString(): String {
        return srcFile.toString()
    }
}
