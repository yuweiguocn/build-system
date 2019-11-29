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

import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.ide.common.resources.CompileResourceRequest
import com.android.testutils.MockLog
import com.android.utils.FileUtils
import com.google.common.base.Throwables
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestName
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.reflect.KClass
import kotlin.test.assertFailsWith

/** Tests for [Aapt2DaemonImpl], including error conditions */
class Aapt2DaemonImplInternalErrorHandlingTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @get:Rule
    val testName = TestName()

    private val logger = MockLog()

    @Test
    fun testBinaryThatDoesNotExist() {
        val compiledDir = temporaryFolder.newFolder()
        val daemon =
            Aapt2DaemonImpl(
                displayId = "'Aapt2DaemonImplInternalErrorHandlingTest.${testName.methodName}'",
                aaptExecutable = temporaryFolder.newFolder("invalidBuildTools").toPath().resolve("aapt2"),
                logger = logger,
                daemonTimeouts = Aapt2DaemonTimeouts()
            )
        val exception = assertFailsWith(Aapt2InternalException::class) {
            daemon.compile(
                    CompileResourceRequest(
                            inputFile = File("values/does_not_matter.xml"),
                            outputDirectory = compiledDir),
                    logger)
        }
        assertThat(exception.message).contains("Daemon startup failed")
        assertThat(exception.cause).isInstanceOf(IOException::class.java)
        assertThat(daemon.state).isEqualTo(Aapt2Daemon.State.SHUTDOWN)
    }

    private fun createCommand(implementationClass: KClass<*>) = listOf<String>(
        FileUtils.join(System.getProperty("java.home"), "bin", "java"),
        "-cp",
        System.getProperty("java.class.path"),
        implementationClass.java.name)

    @Test
    fun testBinaryThatExitsBeforeReady() {
        val daemon = Aapt2DaemonImpl(
            displayId = "'Aapt2DaemonImplTest.${testName.methodName}'",
            aaptPath = "fake_path",
            aaptCommand = createCommand(ExitsBeforeReadyAapt2Daemon::class),
            versionString = "fake_version",
            daemonTimeouts = Aapt2DaemonTimeouts(
                start = 10, startUnit = TimeUnit.SECONDS,
                stop = 10, stopUnit = TimeUnit.SECONDS),
            logger = logger)

        val compiledDir = temporaryFolder.newFolder()
        val exception = assertFailsWith(Aapt2InternalException::class) {
            daemon.compile(
                CompileResourceRequest(
                    inputFile = File("values/does_not_matter.xml"),
                    outputDirectory = compiledDir),
                logger)
        }
        assertThat(daemon.state).isEqualTo(Aapt2Daemon.State.SHUTDOWN)
        assertThat(exception.javaClass).isEqualTo(Aapt2InternalException::class.java)
        assertThat(exception.message).isEqualTo("AAPT2 fake_version Daemon 'Aapt2DaemonImplTest.testBinaryThatExitsBeforeReady': Daemon startup failed\n" +
                "This should not happen under normal circumstances, please file an issue if it does.")
        assertThat(exception.cause).isNotNull()
        // The inner startup failure
        assertThat(exception.cause!!.javaClass).isEqualTo(Aapt2InternalException::class.java)
        // The original cause was the deamon exiting before it was ready.
        assertThat(Throwables.getRootCause(exception).javaClass).isEqualTo(IOException::class.java)
        assertThat(Throwables.getRootCause(exception).message).isEqualTo("Process unexpectedly exit.")
    }

    @Test
    fun testBinaryThatExitsAfterReady() {
        val daemon = Aapt2DaemonImpl(
            displayId = "'Aapt2DaemonImplTest.${testName.methodName}'",
            aaptPath = "fake_path",
            aaptCommand = createCommand(ExitsAfterReadyAapt2Daemon::class),
            versionString = "fake_version",
            daemonTimeouts = Aapt2DaemonTimeouts(
                start = 10, startUnit = TimeUnit.SECONDS,
                stop = 10, stopUnit = TimeUnit.SECONDS),
            logger = logger)

        val compiledDir = temporaryFolder.newFolder()
        val exception = assertFailsWith(Aapt2InternalException::class) {
            daemon.compile(
                CompileResourceRequest(
                    inputFile = File("values/does_not_matter.xml"),
                    outputDirectory = compiledDir),
                logger)
        }
        assertThat(daemon.state).isEqualTo(Aapt2Daemon.State.SHUTDOWN)
        assertThat(exception.javaClass).isEqualTo(Aapt2InternalException::class.java)
        assertThat(exception.message).isEqualTo(
                "AAPT2 fake_version Daemon " +
                        "'Aapt2DaemonImplTest.testBinaryThatExitsAfterReady': " +
                        "Unexpected error during compile 'values" + File.separatorChar +
                        "does_not_matter.xml', " +
                        "attempting to stop daemon.\n" +
                        "This should not happen under normal circumstances, " +
                        "please file an issue if it does.")
        assertThat(exception.cause).isNotNull()
    }

    @Test
    fun testBinaryThatHangsBeforeReady() {
        val daemon = Aapt2DaemonImpl(
                displayId = "'Aapt2DaemonImplTest.${testName.methodName}'",
                aaptPath = "fake_path",
                aaptCommand = createCommand(NeverReadyAapt2Daemon::class),
                versionString = "fake_version",
                daemonTimeouts = Aapt2DaemonTimeouts(
                        start = 1, startUnit = TimeUnit.NANOSECONDS,
                        stop = 1, stopUnit = TimeUnit.NANOSECONDS),
                logger = logger)

        val compiledDir = temporaryFolder.newFolder()
        val exception = assertFailsWith(Aapt2InternalException::class) {
            daemon.compile(
                    CompileResourceRequest(
                            inputFile = File("values/does_not_matter.xml"),
                            outputDirectory = compiledDir),
                    logger)
        }
        assertThat(daemon.state).isEqualTo(Aapt2Daemon.State.SHUTDOWN)
        assertThat(exception.javaClass).isEqualTo(Aapt2InternalException::class.java)
        assertThat(exception.cause).isNotNull()
        // The inner startup failure
        assertThat(exception.cause!!.javaClass).isEqualTo(Aapt2InternalException::class.java)
        // The original cause was a timeout waiting for ready.
        assertThat(Throwables.getRootCause(exception).javaClass).isEqualTo(TimeoutException::class.java)

        // The shutdown failure should be a suppressed exception on the inner startup failure,
        // which is also a timeout.
        assertThat(exception.cause!!.suppressed).hasLength(1)
        exception.cause!!.suppressed[0].let {
            assertThat(it.javaClass).isEqualTo(TimeoutException::class.java)
            assertThat(it.cause).isNull()
        }
    }

    @Test
    fun testCrashesDuringCompile() {
        testCrashesDuringInteraction { daemon ->
            val inputFile = File(temporaryFolder.newFolder(), "values/values.xml")
            val outputFile = temporaryFolder.newFolder()
            val request = CompileResourceRequest(
                inputFile = inputFile,
                outputDirectory =  outputFile
            )
            daemon.compile(request, logger)
        }
    }

    @Test
    fun testCrashesDuringLink() {
        testCrashesDuringInteraction { daemon ->
            val directory = temporaryFolder.newFolder()
            val androidJar = File(directory, "android.jar")
            val manifest = File(directory, "AndroidManifest.xml")
            val outputFile = File(directory, "lib.apk")
            val request = AaptPackageConfig(
                androidJarPath = androidJar.absolutePath,
                manifestFile = manifest,
                resourceDirs = ImmutableList.of(),
                resourceOutputApk = outputFile,
                options = AaptOptions(),
                variantType = VariantTypeImpl.BASE_APK
            )
            daemon.link(request, logger)
        }
    }
    private fun testCrashesDuringInteraction(action: (Aapt2Daemon) -> Unit) {
        val args = listOf<String>(
            FileUtils.join(System.getProperty("java.home"), "bin", "java"),
            "-cp",
            System.getProperty("java.class.path"),
            ExitsDuringCompileOrLinkAapt2Daemon::class.java.name)

        val daemon = Aapt2DaemonImpl(
            displayId = "'Aapt2DaemonImplInternalErrorHandlingTest.${testName.methodName}'",
            aaptPath = "fake_path",
            aaptCommand = args,
            versionString = "fake_version",
            daemonTimeouts = Aapt2DaemonTimeouts(),
            logger = logger)

        assertThat(daemon.state).isEqualTo(Aapt2Daemon.State.NEW)

        val exception = assertFailsWith(Aapt2InternalException::class) {
            action(daemon)
        }

        assertThat(daemon.state).isEqualTo(Aapt2Daemon.State.SHUTDOWN)

        assertThat(exception.javaClass).isEqualTo(Aapt2InternalException::class.java)
        assertThat(exception.cause).isNotNull()
        // The original cause was the aapt2 process failing to start.
        assertThat(exception.cause!!.javaClass).isEqualTo(IOException::class.java)
        assertThat(exception.cause!!.message).isEqualTo("AAPT2 process unexpectedly exit. Error output:\nCrashes\n")
    }

    @After
    fun assertNoWarningOrErrorLogs() {
        assertThat(
            logger.messages.filter { !(isStartOrShutdownLog(it) || it.startsWith("V")) }
        ).isEmpty()
    }

    private fun isStartOrShutdownLog(line: String) =
            line.startsWith("P") && (line.contains("starting") || line.contains("shutdown"))


}