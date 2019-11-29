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
import org.junit.Assert.fail

import org.junit.Test

/** Tests for [NavigationXmlDocument]  */
class NavigationXmlDocumentTest {

    @Test
    fun testGetNavigationXmlIds() {

        val input =
                """"|<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <include app:graph="@navigation/foo" />
                    |    <include app:graph="@navigation/bar" />
                    |    <deepLink app:uri="www.example1.com"
                    |            android:autoVerify="true" />
                    |    <navigation>
                    |        <include app:graph="@navigation/bar" />
                    |        <deepLink app:uri="www.example2.com" />
                    |    </navigation>
                    |</navigation>""".trimMargin()

        val navigationXmlDocument = NavigationXmlLoader.load(UNKNOWN, input)
        assertThat(navigationXmlDocument.navigationXmlIds).containsExactly("foo", "bar", "bar")
    }

    @Test
    fun testGetDeepLinks() {

        val input =
                """"|<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <include app:graph="@navigation/foo" />
                    |    <include app:graph="@navigation/bar" />
                    |    <deepLink app:uri="www.example1.com"
                    |            android:autoVerify="true" />
                    |    <navigation>
                    |        <include app:graph="@navigation/bar" />
                    |        <deepLink app:uri="www.example2.com" />
                    |    </navigation>
                    |</navigation>""".trimMargin()

        val navigationXmlDocument = NavigationXmlLoader.load(UNKNOWN, input)
        val deepLinks = navigationXmlDocument.deepLinks
        assertThat(deepLinks.size).isEqualTo(2)
        var foundExample1 = false
        var foundExample2 = false
        for (deepLink in deepLinks) {
            if (deepLink.host == "www.example1.com") {
                foundExample1 = true
                assertThat(deepLink.isAutoVerify).isTrue()
                assertThat(deepLink.sourceFilePosition.file).isEqualTo(UNKNOWN)
                assertThat(deepLink.sourceFilePosition.position.toString())
                        .isEqualTo("6:5-7:41")
            }
            if (deepLink.host == "www.example2.com") {
                foundExample2 = true
                assertThat(deepLink.isAutoVerify).isFalse()
                assertThat(deepLink.sourceFilePosition.file).isEqualTo(UNKNOWN)
                assertThat(deepLink.sourceFilePosition.position.toString())
                        .isEqualTo("10:9-48")
            }
        }
        assertThat(foundExample1).isTrue()
        assertThat(foundExample2).isTrue()
    }

    @Test
    fun testCustomNamespacePrefix() {

        val input =
                """"|<navigation
                    |    xmlns:custom1="http://schemas.android.com/apk/res/android"
                    |    xmlns:custom2="http://schemas.android.com/apk/res-auto">
                    |    <include custom2:graph="@navigation/foo" />
                    |    <deepLink custom2:uri="www.example1.com"
                    |            custom1:autoVerify="true" />
                    |</navigation>""".trimMargin()

        val navigationXmlDocument = NavigationXmlLoader.load(UNKNOWN, input)
        assertThat(navigationXmlDocument.navigationXmlIds).containsExactly("foo")
        val deepLinks = navigationXmlDocument.deepLinks
        assertThat(deepLinks.size).isEqualTo(1)
        val deepLink = deepLinks[0]
        assertThat(deepLink.host).isEqualTo("www.example1.com")
        assertThat(deepLink.isAutoVerify).isTrue()
        assertThat(deepLink.sourceFilePosition.file).isEqualTo(UNKNOWN)
        assertThat(deepLink.sourceFilePosition.position.toString()).isEqualTo("5:5-6:41")
    }

    @Test
    fun testEmptyElementExceptions() {

        val input =
                """"|<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <include />
                    |    <deepLink />
                    |</navigation>""".trimMargin()

        val navigationXmlDocument = NavigationXmlLoader.load(UNKNOWN, input)

        try {
            navigationXmlDocument.navigationXmlIds
            fail("Expecting NavigationXmlDocumentException")
        } catch (e: NavigationXmlDocument.NavigationXmlDocumentException) {
            // should throw NavigationXmlDocumentException because of empty <include> element
        }

        try {
            navigationXmlDocument.deepLinks
            fail("Expecting NavigationXmlDocumentException")
        } catch (e: NavigationXmlDocument.NavigationXmlDocumentException) {
            // should throw NavigationXmlDocumentException because of empty <deepLink> element
        }
    }
}
