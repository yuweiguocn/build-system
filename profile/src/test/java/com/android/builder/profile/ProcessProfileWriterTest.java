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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;

/** Tests for the {@link ProcessProfileWriter} class */
public class ProcessProfileWriterTest {


    private Path outputFile;
    private Recorder threadRecorder;

    @Before
    public void setUp() throws IOException {
        // reset for each test.
        outputFile = Jimfs.newFileSystem(Configuration.unix()).getPath("/tmp/profile_proto");
        ProcessProfileWriterFactory.initializeForTests();
        threadRecorder = ThreadRecorder.get();
    }

    @Test
    public void testBasicRecord() throws Exception {
        threadRecorder.record(ExecutionType.SOME_RANDOM_PROCESSING,
                ":projectName", null, () -> 10);
        ProcessProfileWriterFactory.shutdownAndMaybeWrite(outputFile).get();
        GradleBuildProfile profile = loadProfile();
        assertThat(profile.getSpanList()).hasSize(1);
        assertThat(profile.getSpan(0).getType()).isEqualTo(ExecutionType.SOME_RANDOM_PROCESSING);
        assertThat(profile.getSpan(0).getId()).isNotEqualTo(0);
        assertThat(profile.getSpan(0).getVariant()).isEqualTo(0);
        assertThat(profile.getSpan(0).getStartTimeInMs()).isNotEqualTo(0);
    }

    @Test
    public void testRecordWithAttributes() throws Exception {
        threadRecorder.record(
                ExecutionType.SOME_RANDOM_PROCESSING, ":projectName", "foo", () -> 10);
        ProcessProfileWriterFactory.shutdownAndMaybeWrite(outputFile).get();
        GradleBuildProfile profile = loadProfile();
        assertThat(profile.getSpanList()).hasSize(1);
        assertThat(profile.getSpan(0).getType()).isEqualTo(ExecutionType.SOME_RANDOM_PROCESSING);
        assertThat(profile.getSpan(0).getVariant()).isNotEqualTo(0);
        assertThat(profile.getSpan(0).getStartTimeInMs()).isNotEqualTo(0);
    }

    @Test
    public void testRecordsOrder() throws Exception {
        threadRecorder.record(
                ExecutionType.SOME_RANDOM_PROCESSING, ":projectName", null, () ->
                        threadRecorder.record(ExecutionType.SOME_RANDOM_PROCESSING,
                                ":projectName", null, () -> 10));
        ProcessProfileWriterFactory.shutdownAndMaybeWrite(outputFile).get();
        GradleBuildProfile profile = loadProfile();
        assertThat(profile.getSpanList()).hasSize(2);
        GradleBuildProfileSpan parent = profile.getSpan(1);
        GradleBuildProfileSpan child = profile.getSpan(0);
        assertThat(child.getId()).isGreaterThan(parent.getId());
        assertThat(child.getParentId()).isEqualTo(parent.getId());
    }

    @Test
    public void testMultipleSpans() throws Exception {

        Integer value = threadRecorder.record(
                ExecutionType.SOME_RANDOM_PROCESSING,
                ":projectName",
                null,
                () -> threadRecorder.record(
                        ExecutionType.SOME_RANDOM_PROCESSING,
                        ":projectName",
                        null,
                        () -> {
                            Integer first = threadRecorder.record(
                                    ExecutionType.SOME_RANDOM_PROCESSING,
                                    ":projectName", null, () -> 1);
                            Integer second = threadRecorder.record(
                                    ExecutionType.SOME_RANDOM_PROCESSING,
                                    ":projectName", null, () -> 3);
                            Integer third = threadRecorder.record(
                                    ExecutionType.SOME_RANDOM_PROCESSING,
                                    ":projectName", null, () -> {
                                        Integer value1 = threadRecorder.record(
                                                ExecutionType.SOME_RANDOM_PROCESSING,
                                                ":projectName", null,
                                                () -> 7);
                                        assertNotNull(value1);
                                        return 5 + value1;
                                    });
                            assertNotNull(first);
                            assertNotNull(second);
                            assertNotNull(third);
                            return first + second + third;
                        }));

        assertNotNull(value);
        assertEquals(16, value.intValue());
        ProcessProfileWriterFactory.shutdownAndMaybeWrite(outputFile).get();
        GradleBuildProfile profile = loadProfile();
        assertThat(profile.getSpanList()).hasSize(6);

        List<GradleBuildProfileSpan> records =
                profile.getSpanList()
                        .stream()
                        .sorted((a, b) -> Long.signum(a.getId() - b.getId()))
                        .collect(Collectors.toList());
        assertEquals(records.get(0).getId(), records.get(1).getParentId());
        assertEquals(records.get(1).getId(), records.get(2).getParentId());
        assertEquals(records.get(1).getId(), records.get(3).getParentId());
        assertEquals(records.get(1).getId(), records.get(4).getParentId());
        assertEquals(records.get(4).getId(), records.get(5).getParentId());

        assertThat(records.get(1).getDurationInMs())
                .isAtLeast(records.get(2).getDurationInMs()
                        + records.get(3).getDurationInMs()
                        + records.get(4).getDurationInMs());

        assertThat(records.get(4).getDurationInMs()).isAtLeast(records.get(5).getDurationInMs());
    }

    @Test
    public void testConcurrentRecording() throws InterruptedException {
        Runnable recordRunnable =
                () -> {
                    for (int i = 0; i < 20; i++) {
                        threadRecorder
                                .record(
                                        ExecutionType.TASK_EXECUTION,
                                        ":projectName",
                                        "variant",
                                        () -> null);
                    }
                };

        List<Thread> threads =
                Stream.generate(() -> new Thread(recordRunnable))
                        .limit(20)
                        .collect(Collectors.toList());

        threads.forEach(Thread::start);
        for (Thread thread : threads) {
            thread.join();
        }
        ProcessProfileWriter.get().finishAndWrite(outputFile);
    }

    @Test
    public void testThreadNumbering() throws Exception {
        Runnable recordRunnable =
                () ->
                        threadRecorder
                                .record(
                                        ExecutionType.SOME_RANDOM_PROCESSING,
                                        ":projectName",
                                        "variant",
                                        () -> null);

        ImmutableList.Builder<Thread> threadBuilder = ImmutableList.builder();
        for (int i = 0; i < 20; i++) {
            threadBuilder.add(new Thread(recordRunnable));
        }
        ImmutableList<Thread> threads = threadBuilder.build();

        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        ProcessProfileWriter.get().finishAndWrite(outputFile);

        GradleBuildProfile profile = loadProfile();
        List<Long> threadValues =
                profile.getSpanList()
                        .stream()
                        .map(GradleBuildProfileSpan::getThreadId)
                        .collect(Collectors.toList());
        assertThat(threadValues).hasSize(20);
        assertThat(threadValues).containsNoDuplicates();
    }

    @Test
    public void checkApplicationIdStorage() throws Exception {
        ProcessProfileWriter.get().recordApplicationId(() -> "com.example.app.a");
        ProcessProfileWriter.get().recordApplicationId(() -> "com.example.app.b");
        // Duplicates should be ignored.
        ProcessProfileWriter.get().recordApplicationId(() -> "com.example.app.a");
        ProcessProfileWriterFactory.shutdownAndMaybeWrite(outputFile).get();
        GradleBuildProfile profile = loadProfile();
        assertThat(profile.getRawProjectIdList())
                .containsExactly("com.example.app.a", "com.example.app.b");
    }

    private GradleBuildProfile loadProfile() throws IOException {
        return GradleBuildProfile.parseFrom(Files.readAllBytes(outputFile));
    }
}
