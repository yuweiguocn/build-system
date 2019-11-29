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

package com.android.builder.profile;

import com.android.annotations.NonNull;
import com.google.common.jimfs.Jimfs;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for the {@link ThreadRecorder} class.
 */
public class ThreadRecorderTest {

    Recorder threadRecorder;

    @Before
    public void setUp() throws IOException {
        Path outputFile = Jimfs.newFileSystem().getPath("profile_proto");
        ProcessProfileWriterFactory.initializeForTests();
        threadRecorder = ThreadRecorder.get();
    }

    @Test
    public void testBasicTracing() {
        Integer value = threadRecorder.record(ExecutionType.SOME_RANDOM_PROCESSING,
                ":projectName", null, () -> 10);

        Assert.assertNotNull(value);
        Assert.assertEquals(10, value.intValue());
    }

    @Test
    public void testBasicNoExceptionHandling() {
        final AtomicBoolean handlerCalled = new AtomicBoolean(false);
        Integer value = threadRecorder.record(ExecutionType.SOME_RANDOM_PROCESSING,
                ":projectName", null, new Recorder.Block<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        return 10;
                    }

                    @Override
                    public void handleException(@NonNull Exception e) {
                        handlerCalled.set(true);
                    }
                });

        Assert.assertNotNull(value);
        Assert.assertEquals(10, value.intValue());
        // exception handler shouldn't have been called.
        Assert.assertFalse(handlerCalled.get());
    }

    @Test
    public void testBasicExceptionHandling() {
        final Exception toBeThrown = new Exception("random");
        final AtomicBoolean handlerCalled = new AtomicBoolean(false);
        Integer value = threadRecorder.record(ExecutionType.SOME_RANDOM_PROCESSING,
                ":projectName", null, new Recorder.Block<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        throw toBeThrown;
                    }

                    @Override
                    public void handleException(@NonNull Exception e) {
                        handlerCalled.set(true);
                        Assert.assertEquals(toBeThrown, e);
                    }
                });

        Assert.assertTrue(handlerCalled.get());
        Assert.assertNull(value);
    }

    @Test
    public void testBlocks() {
        Integer value = threadRecorder.record(ExecutionType.SOME_RANDOM_PROCESSING,
                ":projectName", null, () ->
                        threadRecorder.record(
                                ExecutionType.SOME_RANDOM_PROCESSING,
                                ":projectName", null, () -> 10));

        Assert.assertNotNull(value);
        Assert.assertEquals(10, value.intValue());
    }

    @Test
    public void testBlocksWithInnerException() {
        final Exception toBeThrown = new Exception("random");
        final AtomicBoolean handlerCalled = new AtomicBoolean(false);
        Integer value = threadRecorder.record(ExecutionType.SOME_RANDOM_PROCESSING,
                ":projectName", null, () -> threadRecorder.record(
                        ExecutionType.SOME_RANDOM_PROCESSING,
                        ":projectName", null, new Recorder.Block<Integer>() {
                            @Override
                            public Integer call() throws Exception {
                                throw toBeThrown;
                            }

                            @Override
                            public void handleException(@NonNull Exception e) {
                                handlerCalled.set(true);
                                Assert.assertEquals(toBeThrown, e);
                            }
                        }));
        Assert.assertTrue(handlerCalled.get());
        Assert.assertNull(value);
    }

    @Test
    public void testBlocksWithOuterException() {
        final Exception toBeThrown = new Exception("random");
        final AtomicBoolean handlerCalled = new AtomicBoolean(false);
        Integer value = threadRecorder.record(ExecutionType.SOME_RANDOM_PROCESSING,
                ":projectName", null, new Recorder.Block<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        threadRecorder.record(ExecutionType.SOME_RANDOM_PROCESSING,
                                ":projectName", null, () -> 10);
                        throw toBeThrown;
                    }

                    @Override
                    public void handleException(@NonNull Exception e) {
                        handlerCalled.set(true);
                        Assert.assertEquals(toBeThrown, e);
                    }
                });
        Assert.assertTrue(handlerCalled.get());
        Assert.assertNull(value);
    }

    @Test
    public void testBlocksWithInnerExceptionRepackaged() {
        final Exception toBeThrown = new Exception("random");
        final AtomicBoolean handlerCalled = new AtomicBoolean(false);
        Integer value = threadRecorder.record(ExecutionType.SOME_RANDOM_PROCESSING,
                ":projectName", null, new Recorder.Block<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        return threadRecorder.record(ExecutionType.SOME_RANDOM_PROCESSING,
                                ":projectName", null, new Recorder.Block<Integer>() {
                                    @Override
                                    public Integer call() throws Exception {
                                        throw toBeThrown;
                                    }
                                });
                    }

                    @Override
                    public void handleException(@NonNull Exception e) {
                        handlerCalled.set(true);
                        Assert.assertTrue(e instanceof RuntimeException);
                        Assert.assertEquals(toBeThrown, e.getCause());
                    }
                });
        Assert.assertTrue(handlerCalled.get());
        Assert.assertNull(value);
    }

    @Test
    public void testWithMultipleInnerBlocksWithExceptionRepackaged() {
        final Exception toBeThrown = new Exception("random");
        final AtomicBoolean handlerCalled = new AtomicBoolean(false);
        // make three layers and throw an exception from the bottom layer, ensure the exception
        // is not repackaged in a RuntimeException several times as it makes its way back up
        // to the handler.
        Integer value = threadRecorder.record(ExecutionType.SOME_RANDOM_PROCESSING,
                ":projectName", null, new Recorder.Block<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        return threadRecorder.record(
                                ExecutionType.SOME_RANDOM_PROCESSING,
                                ":projectName",
                                null,
                                () -> threadRecorder.record(
                                        ExecutionType.SOME_RANDOM_PROCESSING,
                                        ":projectName",
                                        null,
                                        () -> {
                                            throw toBeThrown;
                                        }));
                    }

                    @Override
                    public void handleException(@NonNull Exception e) {
                        handlerCalled.set(true);
                        Assert.assertTrue(e instanceof RuntimeException);
                        Assert.assertEquals(toBeThrown, e.getCause());
                    }
                });
        Assert.assertTrue(handlerCalled.get());
        Assert.assertNull(value);
    }

    @Test
    public void testExceptionPropagation() {
        final Exception toBeThrown = new Exception("random");
        try {
            threadRecorder.record(ExecutionType.SOME_RANDOM_PROCESSING,
                    ":projectName", null, new Recorder.Block<Integer>() {
                        @Override
                        public Integer call() throws Exception {
                            throw toBeThrown;
                        }
                    });
        } catch (Exception e) {
            Assert.assertEquals(toBeThrown, e.getCause());
            return;
        }
        Assert.fail("Exception not propagated.");
    }


    @Test
    public void testVoidBlockExceptionPropagation() {
        final IOException toBeThrown = new IOException("random");
        try {
            threadRecorder.record(ExecutionType.SOME_RANDOM_PROCESSING,
                    ":projectName", null, (Recorder.VoidBlock) () -> {throw toBeThrown;});
        } catch (UncheckedIOException e) {
            Assert.assertEquals(toBeThrown, e.getCause());
            return;
        }
        Assert.fail("Exception not propagated.");
    }
}
