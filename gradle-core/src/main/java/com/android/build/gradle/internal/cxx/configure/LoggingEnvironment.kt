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

import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.EvalIssueReporter
import com.android.builder.errors.EvalIssueReporter.Type.EXTERNAL_NATIVE_BUILD_CONFIGURATION
import com.android.utils.ILogger
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * This file exposes functions for logging where the logger is held in a stack on thread-local
 * storage.
 *
 * Example usage,
 *
 *      GradleSyncLoggingEnvironment(...).use {
 *          warn("falling rocks")
 *       }
 *
 * The purpose is to separate the concerns of other classes and functions from the need to log
 * and warn.
 *
 * You can make your own logger by inheriting from ThreadLoggingEnvironment. This can be useful
 * for testing.
 */

/**
 * Stack of logger environments.
 */
private val loggerStack = ThreadLocal.withInitial { mutableListOf<ThreadLoggingEnvironment>() }

/**
 * The logger environment to use if there is no other environment. There should always be an
 * intentional logging environment so throw print a callstack to the console and throw a
 * RuntimeException.
 */
private val BOTTOM_LOGGING_ENVIRONMENT = GradleBuildLoggingEnvironment(
    Logging.getLogger(GradleBuildLoggingEnvironment::class.java))

/**
 * The current logger.
 */
private val logger : ThreadLoggingEnvironment
    get() = loggerStack.get().firstOrNull() ?: BOTTOM_LOGGING_ENVIRONMENT

/**
 * Report an error.
 */
fun error(format: String, vararg args: Any) = logger.error(checkedFormat(format, args))

/**
 * Report a warning.
 */
fun warn(format: String, vararg args: Any) = logger.warn(checkedFormat(format, args))

/**
 * Report diagnostic/informational message.
 */
fun info(format: String, vararg args: Any) = logger.info(checkedFormat(format, args))

/**
 * If caller from Java side misuses %s-style formatting (too many %s for example), the exception
 * from String.format can be concealed by Gradle's logging system. It will appear as
 * "Invalid format (%s)" with no other indication about the source of the problem. Since this is
 * effectively a code bug not a build error this method catches those exceptions and provides a
 * prominent message about the problem.
 */
private fun checkedFormat(format: String, args: Array<out Any>): String {
    try {
        return String.format(format, *args)
    } catch (e: Throwable) {
        println(
            """
            ${e.message}
            format = $format
            args[${args.size}] = ${args.joinToString("\n")}
            stacktrace = ${e.stackTrace.joinToString("\n")}"""
                .trimIndent()
        )
        throw e
    }
}

/**
 * Push a new logging environment onto the stack of environments.
 */
private fun push(logger: ThreadLoggingEnvironment) = loggerStack.get().add(0, logger)

/**
 * Pop the top logging environment.
 */
private fun pop() = loggerStack.get().removeAt(0)

/**
 * Logger base class. When used from Java try-with-resources or Kotlin use() function it will
 * automatically register and deregister with the thread-local stack of loggers.
 */
abstract class ThreadLoggingEnvironment : AutoCloseable {
    init {
        // Okay to suppress because push doesn't have knowledge of derived classes.
        @Suppress("LeakingThis")
        push(this)
    }
    abstract fun error(message : String)
    abstract fun warn(message : String)
    abstract fun info(message : String)
    override fun close() {
        pop()
    }
}

/**
 * A logger suitable for the gradle sync environment. Warnings and errors are reported so that they
 * can be seen in Android Studio.
 *
 * Configuration errors are also recorded in a set. The purpose is to be able to replay errors at
 * sync time if Android Studio requests the model multiple times.
 */
class GradleSyncLoggingEnvironment(
    private val variantName: String,
    private val tag: String,
    private val errors: MutableSet<String>,
    private val issueReporter: EvalIssueReporter,
    private val logger: ILogger
) : ThreadLoggingEnvironment() {

    override fun error(message: String) {
        errors += message
        val e = GradleException(message)
        issueReporter
            .reportError(
                EXTERNAL_NATIVE_BUILD_CONFIGURATION,
                EvalIssueException(e, message)
            )
    }

    override fun warn(message: String) {
        logger.warning(message)
    }

    override fun info(message: String) {
        logger.info("$variantName|$tag $message")
    }
}

/**
 * A logger suitable for the gradle build environment. This just forwards to Logger which is what
 * decides to show at command-line and in Android Studio.
 */
class GradleBuildLoggingEnvironment(
    private val logger: Logger,
    private val variantName: String = ""
) : ThreadLoggingEnvironment() {

    override fun error(message: String) {
        logger.error(message)
    }

    override fun warn(message: String) {
        logger.warn(message)
    }

    override fun info(message: String) {
        if (variantName.isEmpty()) {
            logger.info(message)
        } else {
            logger.info("$variantName $message")
        }
    }
}


