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

import org.gradle.api.file.Directory
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import java.io.File

class FakeGradleDirectory(private val dir: File): Directory {
    override fun getAsFileTree(): FileTree {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun file(p0: String?): RegularFile {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun file(p0: Provider<out CharSequence>?): Provider<RegularFile> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun dir(p0: String?): Directory {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun dir(p0: Provider<out CharSequence>?): Provider<Directory> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getAsFile() = dir

}