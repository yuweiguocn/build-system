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

package com.android.build.gradle.internal.cxx.process

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.PrintWriter

class ChunkBytesToLineOutputStreamTest {

    @Test
    fun basic() {
        val sb = StringBuilder()
        val out = ChunkBytesToLineOutputStream(
            { message -> sb.append("->$message<-") },
            ""
        )
        PrintWriter(out).use({ p -> p.println("Hello") })
        assertThat(sb.toString()).isEqualTo("->Hello<-")
    }

    @Test
    fun fuzz() {
        for (lf in listOf("\r", "\n", "\r\n")) {
            val string = "a{lf}bb{lf}ccc{lf}dddd{lf}"
            val replaced = string.replace("{lf}", lf)


            for (i in 0 until replaced.length) {
                val left = replaced.substring(0..i)
                val right = replaced.substring(i+1 until replaced.length)
                for (start in 1..100) {
                    val sb = StringBuilder()
                    val out = ChunkBytesToLineOutputStream(
                        { message -> sb.append("->$message<-") },
                        "@",
                        start
                    )
                    PrintWriter(out).use({ p ->
                        p.print(left)
                        p.print(right)
                    })
                    assertThat(sb.toString()).isEqualTo("->@a<-->@bb<-->@ccc<-->@dddd<-")
                }
            }
        }
    }
}