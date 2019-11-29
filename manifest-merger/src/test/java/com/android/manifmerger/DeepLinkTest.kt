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

import com.android.ide.common.blame.SourceFilePosition.UNKNOWN
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail

import com.android.manifmerger.DeepLink.DeepLinkException
import org.junit.Test

/** Testing [DeepLink].  */
class DeepLinkTest {

    @Test
    fun testSchemes() {
        assertThat(DeepLink.fromUri("https://www.example.com", UNKNOWN, false).schemes)
                .containsExactly("https")

        assertThat(DeepLink.fromUri("www.example.com", UNKNOWN, false).schemes)
                .containsExactly("http", "https")

        assertThat(DeepLink.fromUri("file:///foo", UNKNOWN, false).schemes)
                .containsExactly("file")

        assertThat(DeepLink.fromUri("file:/foo", UNKNOWN, false).schemes)
                .containsExactly("file")

        assertThat(DeepLink.fromUri("file:/c:/foo", UNKNOWN, false).schemes)
                .containsExactly("file")

        // test support for '+', '-', and '.' as per https://tools.ietf.org/html/rfc3986#section-3.1
        assertThat(DeepLink.fromUri("a+-.://www.example.com", UNKNOWN, false).schemes)
            .containsExactly("a+-.")
    }

    @Test
    fun testSchemesExceptions() {

        try {
            DeepLink.fromUri("http.*://www.example.com", UNKNOWN, false)
            fail("Expecting DeepLinkException")
        } catch (e: DeepLinkException) {
            // should throw DeepLinkException because wildcards not allowed in scheme
        }

        try {
            DeepLink.fromUri("%http://www.example.com", UNKNOWN, false)
            fail("Expecting DeepLinkException")
        } catch (e: DeepLinkException) {
            // should throw DeepLinkException because of invalid URI syntax
        }

    }

    @Test
    fun testHost() {
        assertThat(DeepLink.fromUri("https://foo:bar@www.example.com/baz", UNKNOWN, false).host)
                .isEqualTo("www.example.com")

        assertThat(DeepLink.fromUri("www.example.com", UNKNOWN, false).host)
                .isEqualTo("www.example.com")

        assertThat(DeepLink.fromUri("www.example.com/c:/foo", UNKNOWN, false).host)
                .isEqualTo("www.example.com")

        assertThat(DeepLink.fromUri("\${applicationId}", UNKNOWN, false).host)
                .isEqualTo("\${applicationId}")

        assertThat(DeepLink.fromUri(".*.example.com", UNKNOWN, false).host)
                .isEqualTo("*.example.com")

        assertThat(DeepLink.fromUri("*.example.com", UNKNOWN, false).host).isNull()

        assertThat(DeepLink.fromUri("file:///foo", UNKNOWN, false).host).isNull()

        assertThat(DeepLink.fromUri("file:/foo", UNKNOWN, false).host).isNull()
    }

    @Test
    fun testHostExceptions() {

        try {
            DeepLink.fromUri("http://www.{placeholder}.com", UNKNOWN, false)
            fail("Expecting DeepLinkException")
        } catch (e: DeepLinkException) {
            // should throw DeepLinkException because host wildcards must be at beginning of host
        }

        try {
            DeepLink.fromUri("http://www.{.com", UNKNOWN, false)
            fail("Expecting DeepLinkException")
        } catch (e: DeepLinkException) {
            // should throw DeepLinkException because of invalid URI syntax
        }

    }

    @Test
    fun testPort() {
        assertThat(DeepLink.fromUri("https://foo:bar@www.example.com:200/baz", UNKNOWN, false).port)
                .isEqualTo(200)

        assertThat(DeepLink.fromUri("www.example.com:201", UNKNOWN, false).port)
                .isEqualTo(201)

        assertThat(DeepLink.fromUri("www.example.com", UNKNOWN, false).port).isEqualTo(-1)

        assertThat(DeepLink.fromUri("www.example.com:foo", UNKNOWN, false).port).isEqualTo(-1)
    }

    @Test
    fun testPath() {
        assertThat(
                DeepLink.fromUri(
                        "https://foo:bar@www.example.com/baz?query#fragment",
                        UNKNOWN,
                        false)
                        .path)
                .isEqualTo("/baz")

        assertThat(DeepLink.fromUri("www.example.com", UNKNOWN, false).path).isEqualTo("/")

        assertThat(DeepLink.fromUri("www.example.com/c:/foo", UNKNOWN, false).path)
                .isEqualTo("/c:/foo")

        assertThat(DeepLink.fromUri("/foo", UNKNOWN, false).path).isEqualTo("/foo")

        assertThat(DeepLink.fromUri("/c:/foo", UNKNOWN, false).path).isEqualTo("/c:/foo")

        assertThat(DeepLink.fromUri("file:///foo", UNKNOWN, false).path).isEqualTo("/foo")

        assertThat(DeepLink.fromUri("file:/foo", UNKNOWN, false).path).isEqualTo("/foo")

        assertThat(DeepLink.fromUri("file:/foo.*", UNKNOWN, false).path).isEqualTo("/foo.*")

        assertThat(DeepLink.fromUri("file:/foo{placeholder}", UNKNOWN, false).path)
                .isEqualTo("/foo.*")

        assertThat(DeepLink.fromUri("file:/foo\${applicationId}", UNKNOWN, false).path)
                .isEqualTo("/foo\${applicationId}")

        assertThat(DeepLink.fromUri("file:/{1}foo{2}", UNKNOWN, false).path).isEqualTo("/.*foo.*")
    }

    @Test
    fun testPathExceptions() {

        try {
            DeepLink.fromUri("http://www.example.com/{{nested}}", UNKNOWN, false)
            fail("Expecting DeepLinkException")
        } catch (e: DeepLinkException) {
            // should throw DeepLinkException because of invalid URI syntax
        }

        try {
            DeepLink.fromUri("http://www.example.com/hanging{", UNKNOWN, false)
            fail("Expecting DeepLinkException")
        } catch (e: DeepLinkException) {
            // should throw DeepLinkException because of invalid URI syntax
        }

        try {
            DeepLink.fromUri("http://www.example.com/nested/hanging{{}", UNKNOWN, false)
            fail("Expecting DeepLinkException")
        } catch (e: DeepLinkException) {
            // should throw DeepLinkException because of invalid URI syntax
        }

    }

    @Test
    fun testSourceFilePosition() {
        assertThat(DeepLink.fromUri("http://www.example.com", UNKNOWN, false).sourceFilePosition)
                .isEqualTo(UNKNOWN)
    }

    @Test
    fun testAutoVerify() {
        assertThat(DeepLink.fromUri("http://www.example.com", UNKNOWN, false).isAutoVerify)
                .isFalse()

        assertThat(DeepLink.fromUri("http://www.example.com", UNKNOWN, true).isAutoVerify).isTrue()
    }

    @Test
    fun testChooseEncoder() {
        val encoder = DeepLink.DeepLinkUri
                .chooseEncoder("http://www.example.com", 'w', 'x')
        assertThat(encoder).isEqualTo("wwwwx")
    }

    @Test
    fun testChooseEncoderExceptions() {
        try {
            DeepLink.DeepLinkUri.chooseEncoder("file:///foo", 'a', 'a')
            fail("Expecting IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // should throw IllegalArgumentException because char1 and char2 must be different
        }

    }
}
