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

package com.android.build.gradle.internal.cxx.configure

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class CmakeCompilerSettingsCacheTest {

    @Rule
    @JvmField
    val tmpFolder = TemporaryFolder()

    private val ndkProperties = SdkSourceProperties(mapOf("x" to "y"))

    private fun makeKey(vararg properties: String) : CmakeCompilerCacheKey {
        return CmakeCompilerCacheKey(
            ndkInstallationFolder = tmpFolder.root,
            ndkSourceProperties = ndkProperties,
            args = properties.toList()
        )
    }

    @Test
    fun testCacheMiss() {
        val cacheFolder = cacheFolder("withCacheMiss")
        val cache = CmakeCompilerSettingsCache(cacheFolder)

        val key = makeKey("abc")
        val initial = cache.tryGetValue(key)
        assertThat(initial).isNull()
    }

    @Test
    fun testCacheHit() {
        val key = makeKey("abc")
        val cacheFolder = cacheFolder("withCacheHit")
        val cache = CmakeCompilerSettingsCache(cacheFolder)

        cache.saveKeyValue(key, "My Value")
        val final = cache.tryGetValue(key)!!
        assertThat(final).isEqualTo("My Value")
    }

    @Test
    fun basicWithHashCollision() {
        val cacheFolder = cacheFolder("basicWithHashCollision")

        // Configure with a hash function that just takes the first character.
        val cache = CmakeCompilerSettingsCache(cacheFolder) { _ ->
            "A"
        }
        val key1 = makeKey("abc")
        val key2 = makeKey("abd")

        cache.saveKeyValue(key1, "ABC")
        assertThat(cache.tryGetValue(key1)).isEqualTo("ABC")
        assertThat(cache.tryGetValue(key2)).isNull()

        cache.saveKeyValue(key2, "ABD")
    }

    private fun cacheFolder(subFolder : String): File {
        val cacheFile = File(tmpFolder.root,"my-cache/$subFolder")
        cacheFile.deleteRecursively()
        return cacheFile
    }
}