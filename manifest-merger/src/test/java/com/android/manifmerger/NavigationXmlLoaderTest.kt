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

package com.android.manifmerger

import com.android.ide.common.blame.SourceFile.UNKNOWN
import com.google.common.truth.Truth.assertThat

import org.junit.Test

/** Tests for [NavigationXmlLoader]  */
class NavigationXmlLoaderTest {

    @Test
    fun testLoad() {

        val input =
                """|<navigation
                   |    xmlns:android="http://schemas.android.com/apk/res/android"
                   |    xmlns:app="http://schemas.android.com/apk/res-auto">
                   |    <include app:graph="@navigation/foo" />
                   |    <deepLink app:uri="www.example.com" />
                   |</navigation>""".trimMargin()

        val navigationXmlDocument = NavigationXmlLoader.load(UNKNOWN, input)
        assertThat(navigationXmlDocument.navigationXmlIds).containsExactly("foo")
        assertThat(navigationXmlDocument.deepLinks.size).isEqualTo(1)
        val deepLink = navigationXmlDocument.deepLinks[0]
        assertThat(deepLink.host).isEqualTo("www.example.com")
    }
}
