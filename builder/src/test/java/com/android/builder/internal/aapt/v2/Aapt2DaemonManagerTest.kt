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

package com.android.builder.internal.aapt.v2

import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.ide.common.resources.CompileResourceRequest
import com.android.testutils.NoErrorsOrWarningsLogger
import com.android.utils.ILogger
import com.google.common.base.Ticker
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFailsWith

class Aapt2DaemonManagerTest {

    /**
     * These tests are doing manual concurrency control.
     * Add a timeout so they don't prevent other tests from running if they deadlock
     */
    @Rule
    @JvmField
    val timeout: Timeout = Timeout.seconds(120)

    private var nanoTime = 0L
    private val ticker: Ticker = object : Ticker() {
        override fun read() = nanoTime
    }

    @Test
    fun testListenerEvents() {
        val manager = createManager { throw AssertionError("No daemons created") }
        Listener.assertStartCountEquals(0)
        Listener.assertStopCountEquals(0)
        manager.shutdown()
        Listener.assertStartCountEquals(0)
        Listener.assertStopCountEquals(0)
    }

    @Test
    fun testExpiryLogic() {
        val daemon = TestAapt2Daemon(100)
        val manager = createManager { daemon }
        assertThat(daemon.state).isEqualTo(Aapt2Daemon.State.NEW)

        manager.leaseDaemon().use { process ->
            manager.maintain()
            process.compile(
                    CompileResourceRequest(
                            inputFile = File("in1"),
                            outputDirectory = File("out1"),
                            inputDirectoryName = "values"),
                    NoErrorsOrWarningsLogger())
            manager.maintain()
        }

        assertThat(daemon.compileRequests).hasSize(1)
        assertThat(daemon.linkRequests).isEmpty()
        assertThat(daemon.state).isEqualTo(Aapt2Daemon.State.RUNNING)
        assertThat(manager.stats().poolSize).isEqualTo(1)
        Listener.assertStartCountEquals(1)
        Listener.assertStopCountEquals(0)

        // Daemon should not have timed out yet.
        setTime(59, TimeUnit.SECONDS)
        manager.maintain()
        assertThat(manager.stats().poolSize).isEqualTo(1)


        manager.leaseDaemon().use { process ->
            manager.maintain()
            process.link(
                    AaptPackageConfig(
                            manifestFile = File(""),
                            androidJarPath = "",
                            options = AaptOptions(),
                            variantType = VariantTypeImpl.BASE_APK
                    ),
                    NoErrorsOrWarningsLogger()
            )
            manager.maintain()
        }
        assertThat(daemon.compileRequests).hasSize(1)
        assertThat(daemon.linkRequests).hasSize(1)

        // Daemon should not have timed out yet.
        setTime(118, TimeUnit.SECONDS)
        manager.maintain()
        assertThat(manager.stats().poolSize).isEqualTo(1)

        // Check daemon is shut down.
        setTime(2, TimeUnit.MINUTES)
        manager.maintain()
        assertThat(manager.stats().poolSize).isEqualTo(0)
        assertThat(daemon.state).isEqualTo(Aapt2Daemon.State.SHUTDOWN)
        Listener.assertStartCountEquals(1)
        Listener.assertStopCountEquals(1)
        manager.shutdown()
        Listener.assertStartCountEquals(1)
        Listener.assertStopCountEquals(1)
    }

    @Test
    fun testAbnormalShutdown() {
        val daemon = TestAapt2Daemon(101)
        val manager = createManager { daemon }
        val daemonReady = Semaphore(0)
        val daemonWait = Semaphore(0)
        val thread = Thread(Runnable {
            manager.leaseDaemon().use {
                daemonReady.release()
                daemonWait.acquire()
            }
        })
        thread.start()
        // Wait for daemon to be in use.
        daemonReady.acquire()
        val exception = assertFailsWith(IllegalStateException::class) {
            manager.shutdown()
        }
        daemonWait.release()
        thread.join()
        assertThat(exception)
                .hasMessage("AAPT Process manager cannot be shut down while daemons are in use")
        manager.shutdown()
    }

    @Test
    fun multiThreadedCheck() {
        val count = 8
        val daemonsReady = Semaphore(0)
        val daemonsWait = Semaphore(0)
        val manager = createManager { TestAapt2Daemon(it) }
        val threads = (1..count).map {
            Thread(Runnable {
                val logger = NoErrorsOrWarningsLogger()
                manager.leaseDaemon().use { process ->
                    process.compile(
                            CompileResourceRequest(
                                    inputFile = File("in1"),
                                    outputDirectory = File("out1"),
                                    inputDirectoryName = "values"),
                            logger)
                    daemonsReady.release()
                    daemonsWait.acquire()
                    process.compile(
                            CompileResourceRequest(
                                    inputFile = File("in2"),
                                    outputDirectory = File("out1"),
                                    inputDirectoryName = "values"),
                            logger)
                }
            })
        }

        Listener.assertStartCountEquals(0)
        Listener.assertStopCountEquals(0)
        // Wait for all the threads to start and lease a daemon.
        threads.forEach(Thread::start)
        daemonsReady.acquire(count)
        // Now there should be $count daemons in the pool, all busy.
        assertThat(manager.stats().poolSize).isEqualTo(count)
        assertThat(manager.stats().busyCount).isEqualTo(count)
        Listener.assertStartCountEquals(1)
        Listener.assertStopCountEquals(0)

        // allow all the threads to continue.
        daemonsWait.release(count)
        threads.forEach(Thread::join)
        // Now there should be $count daemons in the pool, none busy
        assertThat(manager.stats().poolSize).isEqualTo(count)
        assertThat(manager.stats().busyCount).isEqualTo(0)

        // Allow all but one of the daemons to expire
        setTime(2, TimeUnit.MINUTES)
        manager.leaseDaemon().use { }
        manager.maintain()
        // Now there should be 1 daemon in the pool.
        assertThat(manager.stats().poolSize).isEqualTo(1)
        assertThat(manager.stats().busyCount).isEqualTo(0)
        Listener.assertStartCountEquals(1)
        Listener.assertStopCountEquals(0)

        // Allow the last one to expire.
        setTime(4, TimeUnit.MINUTES)
        manager.maintain()
        // Now there should be no daemons in the pool.
        assertThat(manager.stats().poolSize).isEqualTo(0)
        assertThat(manager.stats().busyCount).isEqualTo(0)
        Listener.assertStartCountEquals(1)
        Listener.assertStopCountEquals(1)

        manager.shutdown()
        Listener.assertStartCountEquals(1)
        Listener.assertStopCountEquals(1)
    }

    @Test
    fun testTimeoutHandling() {
        val manager = createManager { CompileLinkTimeoutAapt2Daemon() }
        manager.leaseDaemon().use { daemon ->
            val exception = assertFailsWith(Aapt2InternalException::class) {
                daemon.compile(CompileResourceRequest(
                        inputFile = File("in1"),
                        outputDirectory = File("out1"),
                        inputDirectoryName = "values"), NoErrorsOrWarningsLogger())
            }
            assertThat(exception.message).contains("Compile")
            assertThat(exception.message).contains("timed out, attempting to stop daemon")
        }

        // Daemon should be removed from pool, as it is now stopped.
        assertThat(manager.stats().poolSize).isEqualTo(0)
        manager.shutdown()
        Listener.assertStartCountEquals(1)
        Listener.assertStopCountEquals(1)
    }

    class TestAapt2Daemon(displayId: Int) : Aapt2Daemon("Test AAPT Daemon #$displayId",
            NoErrorsOrWarningsLogger()) {
        val compileRequests = mutableListOf<CompileResourceRequest>()
        val linkRequests = mutableListOf<AaptPackageConfig>()

        override fun startProcess() {
        }

        override fun doCompile(request: CompileResourceRequest, logger: ILogger) {
            compileRequests.add(request)
        }

        override fun doLink(request: AaptPackageConfig, logger: ILogger) {
            linkRequests.add(request)
        }

        override fun stopProcess() {
        }
    }

    private fun createManager(daemonFactory: (Int) -> Aapt2Daemon) =
            Aapt2DaemonManager(
                    logger = NoErrorsOrWarningsLogger(),
                    daemonFactory = daemonFactory,
                    expiryTime = 1,
                    expiryTimeUnit = TimeUnit.MINUTES,
                    timeSource = ticker,
                    listener = Listener)

    private fun setTime(value: Long, timeUnit: TimeUnit) {
        nanoTime = timeUnit.toNanos(value)
    }

    @Before
    fun reset() {
        Listener.startCount.set(0)
        Listener.stopCount.set(0)
    }

    private object Listener : Aapt2DaemonManager.Listener {
        val startCount = AtomicInteger(0)
        val stopCount = AtomicInteger(0)

        override fun firstDaemonStarted(manager: Aapt2DaemonManager) {
            startCount.incrementAndGet()
        }

        override fun lastDaemonStopped() {
            stopCount.incrementAndGet()
        }

        fun assertStartCountEquals(expected: Int) {
            assertThat(startCount.get()).named("Number of calls to firstDaemonStarted()").isEqualTo(
                    expected)
        }

        fun assertStopCountEquals(expected: Int) {
            assertThat(stopCount.get()).named("Number of calls to lastDaemonStopped()").isEqualTo(
                    expected)
        }
    }
}