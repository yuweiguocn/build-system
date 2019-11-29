/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.common.runner;

import com.android.build.gradle.internal.LoggerWrapper;
import com.android.testutils.TestUtils;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.runners.model.RunnerScheduler;

/**
 * Version of {@link org.junit.runners.Parameterized} that executes methods in parallel.
 */
public class CheckAllRunner extends FilterableParameterized {

    private static class ThreadPoolScheduler implements RunnerScheduler {
        private static final LoggerWrapper logger =
                LoggerWrapper.getLogger(ThreadPoolScheduler.class);

        private ExecutorService executor;

        public ThreadPoolScheduler() {
            int cpus = Runtime.getRuntime().availableProcessors();
            int threads = Math.min(cpus / 4, 8);
            executor = Executors.newFixedThreadPool(threads);
        }

        @Override
        public void finished() {
            executor.shutdown();
            try {
                boolean waitResult = executor.awaitTermination(90, TimeUnit.MINUTES);
                logger.info("ThreadPoolScheduler awaitTermination = %s", waitResult);
            }
            catch (InterruptedException exc) {
                logger.info("ThreadPoolScheduler awaitTermination was interrupted.");
                Thread.currentThread().interrupt();
                throw new RuntimeException(exc);
            }
        }

        @Override
        public void schedule(Runnable childStatement) {
            executor.submit(childStatement);
        }
    }

    public CheckAllRunner(Class klass) throws Throwable {
        super(klass);
        if (!TestUtils.runningFromBazel()) {
            setScheduler(new ThreadPoolScheduler());
        }
    }
}

