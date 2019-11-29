/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.profile;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.profile.ProcessProfileWriter;
import com.android.builder.profile.ProcessProfileWriterFactory;
import com.android.builder.profile.ProfileRecordWriter;
import com.android.builder.profile.ThreadRecorder;
import com.android.tools.build.gradle.internal.profile.GradleTaskExecutionType;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.jimfs.Jimfs;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import com.google.wireless.android.sdk.stats.GradleTaskExecution;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskState;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Tests for {@link RecordingBuildListener} */
public class RecordingBuildListenerTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule().silent();

    @Mock Task task;

    @Mock Task secondTask;

    @Mock TaskState taskState;

    @Mock Project project;

    @Mock ILogger logger;

    private Path mProfileProtoFile;

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @NonNull
    private static GradleBuildProfileSpan getRecordForId(
            @NonNull List<GradleBuildProfileSpan> records, long recordId) {
        for (GradleBuildProfileSpan record : records) {
            if (record.getId() == recordId) {
                return record;
            }
        }
        throw new AssertionError(
                "No record with id "
                        + recordId
                        + " found in ["
                        + Joiner.on(", ").join(records)
                        + "]");
    }

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        when(project.getPath()).thenReturn(":projectName");
        when(task.getName()).thenThrow(new AssertionError("Nothing should be using task name"));
        when(task.getPath()).thenReturn(":projectName:taskName");
        when(task.getProject()).thenReturn(project);
        when(secondTask.getPath()).thenReturn(":projectName:task2Name");
        when(secondTask.getName())
                .thenThrow(new AssertionError("Nothing should be using task name"));
        when(secondTask.getProject()).thenReturn(project);
        mProfileProtoFile = Jimfs.newFileSystem().getPath(
                tmpFolder.newFile("profile_proto.rawproto").getAbsolutePath());
        ProcessProfileWriterFactory.initializeForTests();
    }

    @Test
    public void singleThreadInvocation() {
        TestProfileRecordWriter writer = new TestProfileRecordWriter();
        RecordingBuildListener listener = new RecordingBuildListener(writer);

        listener.beforeExecute(task);
        listener.afterExecute(task, taskState);
        assertEquals(1, writer.getRecords().size());
        GradleBuildProfileSpan record = writer.getRecords().get(0);
        assertEquals(1, record.getId());
        assertEquals(0, record.getParentId());
        assertEquals(0, record.getThreadId());
    }

    @Test
    public void singleThreadWithMultipleSpansInvocation() throws Exception {

        RecordingBuildListener listener = new RecordingBuildListener(ProcessProfileWriter.get());

        listener.beforeExecute(task);
        ThreadRecorder.get()
                .record(
                        ExecutionType.SOME_RANDOM_PROCESSING,
                        ":projectName",
                        null,
                        () -> {
                            Logger.getAnonymousLogger().finest("useless block");
                            return null;
                        });
        listener.afterExecute(task, taskState);
        ProcessProfileWriterFactory.shutdownAndMaybeWrite(mProfileProtoFile).get();

        GradleBuildProfile profile = loadProfile();
        assertEquals("Span count", 2, profile.getSpanCount());

        GradleBuildProfileSpan record = getRecordForId(profile.getSpanList(), 2);
        assertEquals(0, record.getParentId());
        assertEquals(0, record.getThreadId());

        record = getRecordForId(profile.getSpanList(), 3);
        assertNotNull(record);
        assertEquals(0, record.getParentId());
        assertEquals(ExecutionType.SOME_RANDOM_PROCESSING, record.getType());
        assertNotEquals(0, record.getThreadId());
    }

    @Test
    public void simulateTasksUnorderedLifecycleEventsDelivery() throws Exception {
        RecordingBuildListener listener = new RecordingBuildListener(ProcessProfileWriter.get());

        listener.beforeExecute(task);
        listener.beforeExecute(secondTask);
        ThreadRecorder.get()
                .record(
                        ExecutionType.SOME_RANDOM_PROCESSING,
                        ":projectName",
                        null,
                        () -> {
                            logger.verbose("useless block");
                            return null;
                        });
        listener.afterExecute(task, taskState);
        listener.afterExecute(secondTask, taskState);

        ProcessProfileWriterFactory.shutdownAndMaybeWrite(mProfileProtoFile).get();
        GradleBuildProfile profile = loadProfile();

        assertEquals(3, profile.getSpanCount());
        GradleBuildProfileSpan record = getRecordForId(profile.getSpanList(), 2);
        assertEquals(1, record.getProject());

        record = getRecordForId(profile.getSpanList(), 3);
        assertEquals(0, record.getParentId());
        assertEquals(1, record.getProject());
        assertEquals(0, record.getThreadId());

        record = getRecordForId(profile.getSpanList(), 4);
        assertNotNull(record);
        assertEquals(ExecutionType.SOME_RANDOM_PROCESSING, record.getType());
        assertNotEquals(0, record.getThreadId());
    }

    @Test
    public void multipleThreadsInvocation() {
        TestProfileRecordWriter writer = new TestProfileRecordWriter();
        RecordingBuildListener listener = new RecordingBuildListener(writer);
        Task secondTask = mock(Task.class);
        when(secondTask.getPath()).thenReturn(":projectName:secondTaskName");
        when(secondTask.getProject()).thenReturn(project);

        // first thread start
        listener.beforeExecute(task);

        // now second threads start
        listener.beforeExecute(secondTask);

        // first thread finishes
        listener.afterExecute(task, taskState);

        // and second thread finishes
        listener.afterExecute(secondTask, taskState);

        assertEquals(2, writer.getRecords().size());
        GradleBuildProfileSpan record = getRecordForId(writer.getRecords(), 1);
        assertEquals(1, record.getId());
        assertEquals(0, record.getParentId());
        assertEquals(0, record.getThreadId());

        record = getRecordForId(writer.getRecords(), 2);
        assertEquals(2, record.getId());
        assertEquals(0, record.getParentId());
        assertEquals(1, record.getProject());
        assertEquals(0, record.getThreadId());
    }

    @Test
    public void multipleThreadsOrderInvocation() {
        TestProfileRecordWriter writer = new TestProfileRecordWriter();
        RecordingBuildListener listener = new RecordingBuildListener(writer);
        Task secondTask = mock(Task.class);
        when(secondTask.getPath()).thenReturn(":projectName:secondTaskName");
        when(secondTask.getProject()).thenReturn(project);

        // first thread start
        listener.beforeExecute(task);

        // now second threads start
        listener.beforeExecute(secondTask);

        // second thread finishes
        listener.afterExecute(secondTask, taskState);

        // and first thread finishes
        listener.afterExecute(task, taskState);

        assertEquals(2, writer.getRecords().size());
        GradleBuildProfileSpan record = getRecordForId(writer.getRecords(), 1);
        assertEquals(1, record.getId());
        assertEquals(0, record.getParentId());
        assertEquals(1, record.getProject());
        assertEquals(0, record.getThreadId());

        record = getRecordForId(writer.getRecords(), 2);
        assertEquals(2, record.getId());
        assertEquals(0, record.getParentId());
        assertEquals(1, record.getProject());
        assertEquals(0, record.getThreadId());
    }

    @Test
    public void ensureTaskStateRecorded() {
        TestProfileRecordWriter writer = new TestProfileRecordWriter();
        RecordingBuildListener listener = new RecordingBuildListener(writer);

        when(taskState.getDidWork()).thenReturn(true);
        when(taskState.getExecuted()).thenReturn(true);
        when(taskState.getFailure()).thenReturn(new RuntimeException("Task failure"));
        when(taskState.getSkipped()).thenReturn(false);
        when(taskState.getUpToDate()).thenReturn(false);

        listener.beforeExecute(task);
        listener.afterExecute(task, taskState);

        assertEquals(1, writer.getRecords().size());
        assertThat(writer.getRecords().get(0).getType())
                .named("execution type")
                .isEqualTo(ExecutionType.TASK_EXECUTION);
        GradleTaskExecution task = writer.getRecords().get(0).getTask();
        assertThat(task.getDidWork()).named("task.did_work").isTrue();
        assertThat(task.getFailed()).named("task.failed").isTrue();
        assertThat(task.getSkipped()).named("task.skipped").isFalse();
        assertThat(task.getUpToDate()).named("task.up_to_date").isFalse();
    }

    @Test
    public void checkTasksEnum() {
        assertThat(
                        AnalyticsUtil.getTaskExecutionType(
                                org.gradle.api.tasks.compile.JavaCompile.class))
                .named("JavaCompile")
                .isEqualTo(GradleTaskExecutionType.JAVA_COMPILE);
    }

    private GradleBuildProfile loadProfile() throws IOException {
        return GradleBuildProfile.parseFrom(Files.readAllBytes(mProfileProtoFile));
    }

    static final class TestProfileRecordWriter implements ProfileRecordWriter {

        private final List<GradleBuildProfileSpan> records =
                Collections.synchronizedList(new ArrayList<>());
        private final AtomicLong recordId = new AtomicLong(1);

        @Override
        public long allocateRecordId() {
            return recordId.getAndIncrement();
        }

        @Override
        public void writeRecord(
                @NonNull String project,
                @Nullable String variant,
                @NonNull GradleBuildProfileSpan.Builder executionRecord) {
            if (project.equals(":projectName")) {
                executionRecord.setProject(1);
            }
            if ("variantName".equals(variant)) {
                executionRecord.setVariant(1);
            }

            records.add(executionRecord.build());
        }

        public List<GradleBuildProfileSpan> getRecords() {
            return records;
        }
    }
}
