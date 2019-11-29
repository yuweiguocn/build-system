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

package com.android.build.gradle.internal.res.namespaced

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.charset.StandardCharsets

class NamespaceRemoverTest {
    private val namespaceRemover = NamespaceRemover

    @Test
    fun noChange() {
        assertUnchanged("""<?xml version="1.0" encoding="utf-8"?>
                    |<resources xmlns:android="http://schemas.android.com/apk/res/android" xmlns:lib="http://schemas.android.com/apk/res-auto" xmlns:tools="http://schemas.android.com/tools">
                    |
                    |    <string name="unchanged">@android:string/foo</string>
                    |
                    |</resources>""".trimMargin())
    }

    @Test
    fun namespaceValueReference() {
        assertThat(
                rewrite("""<?xml version="1.0" encoding="utf-8"?>
                    |<resources xmlns:android="http://schemas.android.com/apk/res/android" xmlns:lib="http://schemas.android.com/apk/res/lib" xmlns:tools="http://schemas.android.com/tools">
                    |
                    |    <string name="libString_from_lib">@lib:string/libString</string>
                    |
                    |</resources>""".trimMargin()))
                .isEqualTo("""<?xml version="1.0" encoding="utf-8"?>
                    |<resources xmlns:android="http://schemas.android.com/apk/res/android" xmlns:lib="http://schemas.android.com/apk/res-auto" xmlns:tools="http://schemas.android.com/tools">
                    |
                    |    <string name="libString_from_lib">@string/libString</string>
                    |
                    |</resources>""".trimMargin())
    }

    @Test
    fun androidValueReference() {
        assertThat(
                rewrite("""<?xml version="1.0" encoding="utf-8"?>
                    |<resources xmlns:android="http://schemas.android.com/apk/res/android" xmlns:lib="http://schemas.android.com/apk/res/lib" xmlns:tools="http://schemas.android.com/tools">
                    |
                    |    <string name="some_android_ref">@android:string/androidString</string>
                    |
                    |</resources>""".trimMargin()))
                .isEqualTo("""<?xml version="1.0" encoding="utf-8"?>
                    |<resources xmlns:android="http://schemas.android.com/apk/res/android" xmlns:lib="http://schemas.android.com/apk/res-auto" xmlns:tools="http://schemas.android.com/tools">
                    |
                    |    <string name="some_android_ref">@android:string/androidString</string>
                    |
                    |</resources>""".trimMargin())
    }

    @Test
    fun namespaceValueReference2() {
        assertThat(
                rewrite("""<?xml version="1.0" encoding="utf-8"?>
                    |<resources xmlns:android="http://schemas.android.com/apk/res/android" xmlns:lib="http://schemas.android.com/apk/res/lib" xmlns:tools="http://schemas.android.com/tools">
                    |
                    |    <string name="some_android_ref">@android:string/androidString</string>
                    |    <string name="libString_from_lib">@com.example.lib:string/libString</string>
                    |
                    |</resources>""".trimMargin()))
                .isEqualTo("""<?xml version="1.0" encoding="utf-8"?>
                    |<resources xmlns:android="http://schemas.android.com/apk/res/android" xmlns:lib="http://schemas.android.com/apk/res-auto" xmlns:tools="http://schemas.android.com/tools">
                    |
                    |    <string name="some_android_ref">@android:string/androidString</string>
                    |    <string name="libString_from_lib">@string/libString</string>
                    |
                    |</resources>""".trimMargin())
    }

    @Test
    fun testNamespacedLayout() {
        assertThat(
                rewrite("""<?xml version="1.0" encoding="utf-8"?>
                    |<LinearLayout
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:appcompat="http://schemas.android.com/apk/res/com.android.support.v7.appcompat"
                    |    android:layout_width="match_parent"
                    |    android:layout_height="match_parent"
                    |    appcompat:customAttr="@appcompat:drawable/icon"
                    |    appcompat:customAttr2="@com.android.support.v7.appcompat:drawable/icon2" />
                    |""".trimMargin()))
                .isEqualTo("""<?xml version="1.0" encoding="utf-8"?>
                    |<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:appcompat="http://schemas.android.com/apk/res-auto"
                    |    android:layout_width="match_parent"
                    |    android:layout_height="match_parent"
                    |    appcompat:customAttr="@drawable/icon"
                    |    appcompat:customAttr2="@drawable/icon2" />
                    |""".trimMargin())
    }

    @Test
    fun testSimpleCase() {
        assertThat(
                rewrite("""<?xml version="1.0" encoding="utf-8"?>
                    |<resources>
                    |
                    |    <string name="libString">@*com.example.lib:string/foo</string>
                    |
                    |</resources>""".trimMargin()))
                .isEqualTo("""<?xml version="1.0" encoding="utf-8"?>
                    |<resources>
                    |
                    |    <string name="libString">@string/foo</string>
                    |
                    |</resources>""".trimMargin()
        )
    }

    @Test
    fun customNameForToolsNamespace() {
        assertThat(
                rewrite("""<?xml version="1.0" encoding="utf-8"?>
                    |<GridLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:bar="http://schemas.android.com/my/custom/uri"
                    |    xmlns:foo="http://schemas.android.com/tools"
                    |    foo:targetApi="14" />
                    |""".trimMargin()))
                .isEqualTo("""<?xml version="1.0" encoding="utf-8"?>
                    |<GridLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:bar="http://schemas.android.com/apk/res-auto"
                    |    xmlns:foo="http://schemas.android.com/tools"
                    |    foo:targetApi="14" />
                    |""".trimMargin())
    }

    private fun assertUnchanged(original: String) {
        assertThat(rewrite(original)).isEqualTo(original)
    }

    private fun rewrite(original: String) : String {
        return namespaceRemover.rewrite(original.byteInputStream(StandardCharsets.UTF_8), "\n")
    }
}