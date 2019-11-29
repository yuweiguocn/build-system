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

package com.android.build.gradle.integration.common.fixture.app

/** Builder for the contents of a Java source file. */
class JavaSourceFileBuilder(private val packageName: String) {

    private val imports = mutableListOf<String>()
    private val classes = StringBuilder()

    fun addImports(vararg imports: String) {
        this.imports.addAll(imports)
    }

    fun addClass(classContents: String) {
        classes.append("\n${classContents.trim()}")
    }

    fun build(): String {
        val contents = StringBuilder()
        contents.append("package $packageName;")
        if (!imports.isEmpty()) {
            contents.append("\n")
            for (import in imports) {
                contents.append("\nimport $import;")
            }
        }
        if (!classes.isEmpty()) {
            contents.append("\n\n${classes.trim()}")
        }
        return contents.toString()
    }
}