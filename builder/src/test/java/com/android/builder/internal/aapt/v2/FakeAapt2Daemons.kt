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

package com.android.builder.internal.aapt.v2

class NeverReadyAapt2Daemon {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            while(true) {
                Thread.sleep(10000)
            }
        }
    }
}


class ExitsBeforeReadyAapt2Daemon {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Thread.sleep(100)
        }
    }
}

class ExitsAfterReadyAapt2Daemon {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            println("Ready")
            Thread.sleep(100)
        }
    }
}


class ExitsDuringCompileOrLinkAapt2Daemon {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            println("Ready")
            val reader = System.`in`.bufferedReader(Charsets.UTF_8)
            var running = true
            while (running) {
                val action = reader.readLine()
                when (action) {
                    "c", "l" -> {
                        System.err.println("Crashes")
                        running = false
                    }
                    "quit" -> running = false
                    else -> System.err.println("Unknown action $action\nDone")
                }
            }
        }
    }
}
