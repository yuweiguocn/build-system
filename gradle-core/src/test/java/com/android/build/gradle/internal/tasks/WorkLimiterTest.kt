/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.builder.tasks.BooleanLatch
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertTrue

/** Tests for WorkLimiter */
class WorkLimiterTest {

    /**
     * This test verifies that the work limiter both
     *    (a) Prevents more than workLimit items from running in parallel.
     *    (b) Allows workLimit items to run in parallel.
     */
    @Test
    fun checkConcurrencyIsCappedCorrectly() {
        val workLimiter = WorkLimiter(concurrencyLimit)
        val workItemStartAttemptCount = AtomicInteger()
        val workItemConcurrentRunningCount = AtomicInteger()
        val callers = mutableListOf<ForkJoinTask<*>>()
        val allAttemptsStarted = BooleanLatch()
        val maxRunningInParallel = BooleanLatch()
        val callerPool = ForkJoinPool(concurrentAttempts)
        try {
            // Schedule more work items and ensure that not too many run in parallel.
            for (workItemIndex in 1..concurrentAttempts) {
                callers.add(
                    callerPool.submit(
                        TestRunnable(
                            workLimiter, workItemIndex, workItemStartAttemptCount,
                            allAttemptsStarted,
                            workItemConcurrentRunningCount,
                            maxRunningInParallel
                        )
                    )
                )
            }
            allAttemptsStarted.await()
            for (caller in callers) {
                caller.get()
            }
        } finally {
            callerPool.shutdown()
        }


    }

    private inner class TestRunnable(
        val workLimiter: WorkLimiter,
        val id: Int,
        val workItemStartAttemptCount: AtomicInteger,
        val allAttemptsStarted: BooleanLatch,
        val workItemConcurrentRunningCount: AtomicInteger,
        val maxRunningInParallel: BooleanLatch

    ) : Runnable {
        override fun run() {
            val startAttemptCount = workItemStartAttemptCount.incrementAndGet()
            if (startAttemptCount == concurrentAttempts) {
                // All the attempts have started, allow them to continue to execute.
                allAttemptsStarted.signal()
            }
            workLimiter.limit(Callable<Void> {
                val currentlyRunning = workItemConcurrentRunningCount.incrementAndGet()
                try {
                    when {
                        currentlyRunning == concurrencyLimit -> {
                            // We have reached the desired level of concurrency, allow the test to
                            // continue.

                            maxRunningInParallel.signal()
                        }
                        currentlyRunning > concurrencyLimit -> {
                            throw AssertionError("Work item $id: Too many concurrent jobs")
                        }
                    }
                    // Block until all items have started, to attempt to prevent the test from
                    // trivially passing because of previous work items finishing before the next
                    // are scheduled.
                    assertTrue(
                        allAttemptsStarted.await(waitTimeoutNanos),
                        "Attempt count $id timed out waiting for all work items to be scheduled."
                    )
                    // Expectation: At most concurrencyLimit threads were waiting on
                    // allAttemptsStarted when it is first signalled, as the others will be blocked
                    // by the workLimiter.

                    // Block items until sufficient items have started to prevent the test from
                    // passing if the work limiter is limiting the number of concurrent items
                    // to below the work limit.
                    assertTrue(
                        maxRunningInParallel.await(waitTimeoutNanos),
                        "Attempt count $id timed out waiting for $concurrencyLimit work items to be scheduled."
                    )
                } finally {
                    workItemConcurrentRunningCount.decrementAndGet()
                }
                null
            })
        }
    }

    companion object {
        // Timeout to avoid the making the rest of the suite time out if something is wrong
        val waitTimeoutNanos = TimeUnit.SECONDS.toNanos(600)
        const val concurrencyLimit = 3
        const val concurrentAttempts = 10
    }
}
