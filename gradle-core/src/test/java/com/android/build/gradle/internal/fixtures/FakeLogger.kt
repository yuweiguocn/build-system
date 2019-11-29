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

package com.android.build.gradle.internal.fixtures

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.slf4j.Marker

open class FakeLogger: Logger {
    override fun debug(p0: String?, p1: Any?) {
        // ignore
    }

    override fun warn(p0: String?, p1: Any?) {
        TODO("not implemented")
    }

    override fun warn(p0: String?, vararg p1: Any?) {
        TODO("not implemented")
    }

    override fun warn(p0: String?, p1: Any?, p2: Any?) {
        TODO("not implemented")
    }

    override fun warn(p0: String?, p1: Throwable?) {
        TODO("not implemented")
    }

    override fun warn(p0: Marker?, p1: String?) {
        TODO("not implemented")
    }

    override fun warn(p0: Marker?, p1: String?, p2: Any?) {
        TODO("not implemented")
    }

    override fun warn(p0: Marker?, p1: String?, p2: Any?, p3: Any?) {
        TODO("not implemented")
    }

    override fun warn(p0: Marker?, p1: String?, vararg p2: Any?) {
        TODO("not implemented")
    }

    override fun warn(p0: Marker?, p1: String?, p2: Throwable?) {
        TODO("not implemented")
    }

    override fun isQuietEnabled(): Boolean {
        TODO("not implemented")
    }

    override fun getName(): String {
        TODO("not implemented")
    }

    override fun info(p0: String?, vararg p1: Any?) {
        TODO("not implemented")
    }

    override fun info(p0: String?) {
        TODO("not implemented")
    }

    override fun info(p0: String?, p1: Any?) {
        TODO("not implemented")
    }

    override fun info(p0: String?, p1: Any?, p2: Any?) {
        TODO("not implemented")
    }

    override fun info(p0: String?, p1: Throwable?) {
        TODO("not implemented")
    }

    override fun info(p0: Marker?, p1: String?) {
        TODO("not implemented")
    }

    override fun info(p0: Marker?, p1: String?, p2: Any?) {
        TODO("not implemented")
    }

    override fun info(p0: Marker?, p1: String?, p2: Any?, p3: Any?) {
        TODO("not implemented")
    }

    override fun info(p0: Marker?, p1: String?, vararg p2: Any?) {
        TODO("not implemented")
    }

    override fun info(p0: Marker?, p1: String?, p2: Throwable?) {
        TODO("not implemented")
    }

    override fun isErrorEnabled(): Boolean {
        TODO("not implemented")
    }

    override fun isErrorEnabled(p0: Marker?): Boolean {
        TODO("not implemented")
    }

    override fun error(p0: String?) {
        TODO("not implemented")
    }

    override fun error(p0: String?, p1: Any?) {
        TODO("not implemented")
    }

    override fun error(p0: String?, p1: Any?, p2: Any?) {
        TODO("not implemented")
    }

    override fun error(p0: String?, vararg p1: Any?) {
        TODO("not implemented")
    }

    override fun error(p0: String?, p1: Throwable?) {
        TODO("not implemented")
    }

    override fun error(p0: Marker?, p1: String?) {
        TODO("not implemented")
    }

    override fun error(p0: Marker?, p1: String?, p2: Any?) {
        TODO("not implemented")
    }

    override fun error(p0: Marker?, p1: String?, p2: Any?, p3: Any?) {
        TODO("not implemented")
    }

    override fun error(p0: Marker?, p1: String?, vararg p2: Any?) {
        TODO("not implemented")
    }

    override fun error(p0: Marker?, p1: String?, p2: Throwable?) {
        TODO("not implemented")
    }

    override fun isDebugEnabled(): Boolean {
        TODO("not implemented")
    }

    override fun isDebugEnabled(p0: Marker?): Boolean {
        TODO("not implemented")
    }

    override fun log(p0: LogLevel?, p1: String?) {
        TODO("not implemented")
    }

    override fun log(p0: LogLevel?, p1: String?, vararg p2: Any?) {
        TODO("not implemented")
    }

    override fun log(p0: LogLevel?, p1: String?, p2: Throwable?) {
        TODO("not implemented")
    }

    override fun debug(p0: String?, vararg p1: Any?) {
        TODO("not implemented")
    }

    override fun debug(p0: String?) {
        TODO("not implemented")
    }

    override fun debug(p0: String?, p1: Any?, p2: Any?) {
        TODO("not implemented")
    }

    override fun debug(p0: String?, p1: Throwable?) {
        TODO("not implemented")
    }

    override fun debug(p0: Marker?, p1: String?) {
        TODO("not implemented")
    }

    override fun debug(p0: Marker?, p1: String?, p2: Any?) {
        TODO("not implemented")
    }

    override fun debug(p0: Marker?, p1: String?, p2: Any?, p3: Any?) {
        TODO("not implemented")
    }

    override fun debug(p0: Marker?, p1: String?, vararg p2: Any?) {
        TODO("not implemented")
    }

    override fun debug(p0: Marker?, p1: String?, p2: Throwable?) {
        TODO("not implemented")
    }

    override fun isEnabled(p0: LogLevel?): Boolean {
        TODO("not implemented")
    }

    override fun lifecycle(p0: String?) {
        TODO("not implemented")
    }

    override fun lifecycle(p0: String?, vararg p1: Any?) {
        TODO("not implemented")
    }

    override fun lifecycle(p0: String?, p1: Throwable?) {
        TODO("not implemented")
    }

    override fun quiet(p0: String?) {
        TODO("not implemented")
    }

    override fun quiet(p0: String?, vararg p1: Any?) {
        TODO("not implemented")
    }

    override fun quiet(p0: String?, p1: Throwable?) {
        TODO("not implemented")
    }

    override fun isLifecycleEnabled(): Boolean {
        TODO("not implemented")
    }

    override fun isInfoEnabled(): Boolean {
        TODO("not implemented")
    }

    override fun isInfoEnabled(p0: Marker?): Boolean {
        TODO("not implemented")
    }

    override fun trace(p0: String?) {
        TODO("not implemented")
    }

    override fun trace(p0: String?, p1: Any?) {
        TODO("not implemented")
    }

    override fun trace(p0: String?, p1: Any?, p2: Any?) {
        TODO("not implemented")
    }

    override fun trace(p0: String?, vararg p1: Any?) {
        TODO("not implemented")
    }

    override fun trace(p0: String?, p1: Throwable?) {
        TODO("not implemented")
    }

    override fun trace(p0: Marker?, p1: String?) {
        TODO("not implemented")
    }

    override fun trace(p0: Marker?, p1: String?, p2: Any?) {
        TODO("not implemented")
    }

    override fun trace(p0: Marker?, p1: String?, p2: Any?, p3: Any?) {
        TODO("not implemented")
    }

    override fun trace(p0: Marker?, p1: String?, vararg p2: Any?) {
        TODO("not implemented")
    }

    override fun trace(p0: Marker?, p1: String?, p2: Throwable?) {
        TODO("not implemented")
    }

    override fun isWarnEnabled(): Boolean {
        TODO("not implemented")
    }

    override fun isWarnEnabled(p0: Marker?): Boolean {
        TODO("not implemented")
    }

    override fun isTraceEnabled(): Boolean {
        TODO("not implemented")
    }

    override fun isTraceEnabled(p0: Marker?): Boolean {
        TODO("not implemented")
    }

    override fun warn(p0: String?) {
        TODO("not implemented")
    }
}