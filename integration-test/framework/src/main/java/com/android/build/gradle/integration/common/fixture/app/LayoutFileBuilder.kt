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

/** Builder for the contents of an XML layout file. */
class LayoutFileBuilder {

    private val widgets = StringBuilder()

    fun addTextView(id: String, text: String? = "") {
        widgets.append(
            """
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:id="@+id/$id"
                android:text="$text"
                app:layout_constraintBottom_toBottomOf="parent"
                app:layout_constraintLeft_toLeftOf="parent"
                app:layout_constraintRight_toRightOf="parent"
                app:layout_constraintTop_toTopOf="parent" />
            """.trimIndent()
        )
    }

    fun build(): String {
        val contents = StringBuilder()
        contents.append(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <android.support.constraint.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
            """.trimIndent()
        )
        if (!widgets.isEmpty()) {
            contents.append("\n\n$widgets\n\n")
        }
        contents.append("</android.support.constraint.ConstraintLayout>")
        return contents.toString()
    }
}