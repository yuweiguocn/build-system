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
import com.android.repository.testframework.FakeProgressIndicator
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.MockLog
import com.android.testutils.NoErrorsOrWarningsLogger
import com.android.testutils.TestUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestName
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.test.assertFailsWith

/** Tests for [Aapt2Daemon] timeout handling logic. */
class Aapt2DaemonTimeoutTest {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Rule
    @JvmField
    val testName = TestName()

    @Test
    fun testStartupTimeout() {
        val logger = MockLog()
        val compiledDir = temporaryFolder.newFolder()
        val daemon = StartupTimeoutAapt2Daemon(name = testName.methodName)
        val exception = assertFailsWith(Aapt2InternalException::class) {
            daemon.compile(
                    CompileResourceRequest(
                            inputFile = File("values/does_not_matter.xml"),
                            outputDirectory = compiledDir),
                    logger)
        }
        assertThat(exception.message).contains("Daemon startup timed out")
        assertThat(exception.suppressed).isEmpty()
        // The daemon should be shut down.
        assertThat(daemon.state).isEqualTo(Aapt2Daemon.State.SHUTDOWN)
    }

    @Test
    fun testCompileTimeout() {
        val logger = MockLog()
        val compiledDir = temporaryFolder.newFolder()
        val daemon = CompileLinkTimeoutAapt2Daemon(name = testName.methodName)
        val exception = assertFailsWith(Aapt2InternalException::class) {
            daemon.compile(
                    CompileResourceRequest(
                            inputFile = File("values/does_not_matter.xml"),
                            outputDirectory = compiledDir),
                    logger)
        }
        assertThat(exception.message).contains("Compile")
        assertThat(exception.message).contains("timed out, attempting to stop daemon")
        assertThat(exception.suppressed).isEmpty()
        // The daemon should be shut down.
        assertThat(daemon.state).isEqualTo(Aapt2Daemon.State.SHUTDOWN)
    }

    @Test
    fun testLinkTimeout() {
        val logger = MockLog()
        val manifest = resourceFile(
                "src",
                "AndroidManifest.xml",
                """<""")

        val outputFile = File(temporaryFolder.newFolder(), "lib.apk")

        val request = AaptPackageConfig(
                androidJarPath = target.getPath(IAndroidTarget.ANDROID_JAR),
                manifestFile = manifest,
                resourceOutputApk = outputFile,
                options = AaptOptions(),
                variantType = VariantTypeImpl.BASE_APK
        )

        val daemon = CompileLinkTimeoutAapt2Daemon(name = testName.methodName)
        val exception = assertFailsWith(Aapt2InternalException::class) {
            daemon.link(request, logger)
        }
        assertThat(exception.message).contains("Link timed out, attempting to stop daemon")
        assertThat(exception.suppressed).isEmpty()
        // The daemon should be shut down.
        assertThat(daemon.state).isEqualTo(Aapt2Daemon.State.SHUTDOWN)
    }

    @Test
    fun testShutdownTimeout() {
        val logger = MockLog()
        val compiledDir = temporaryFolder.newFolder()
        val daemon = ShutdownTimeoutAapt2Daemon(name = testName.methodName, logger = logger)
        daemon.compile(
                CompileResourceRequest(
                        inputFile = File("values/does_not_matter.xml"),
                        outputDirectory = compiledDir),
                NoErrorsOrWarningsLogger())
        assertThat(daemon.state).isEqualTo(Aapt2Daemon.State.RUNNING)
        daemon.shutDown()
        assertThat(daemon.state).isEqualTo(Aapt2Daemon.State.SHUTDOWN)
        assertThat(logger.messages).contains(
                "E testShutdownTimeout Shutdown Timeout AAPT Daemon " +
                        "Failed to shutdown within timeout")
    }

    private fun resourceFile(directory: String, name: String, content: String) =
            temporaryFolder.newFolder()
                    .toPath()
                    .resolve(directory)
                    .resolve(name)
                    .apply {
                        Files.createDirectories(this.parent)
                        Files.write(this, content.toByteArray(StandardCharsets.UTF_8))
                    }
                    .toFile()

    companion object {
        private val target: IAndroidTarget by lazy(LazyThreadSafetyMode.NONE) {
            AndroidSdkHandler.getInstance(TestUtils.getSdk())
                    .getAndroidTargetManager(FakeProgressIndicator())
                    .getTargets(FakeProgressIndicator())
                    .maxBy { it.version }!!
        }
    }
}