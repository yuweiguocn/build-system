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

package com.android.build.gradle.integration.common.truth;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.task.TaskFailureResult;
import org.gradle.tooling.events.task.TaskFinishEvent;
import org.gradle.tooling.events.task.TaskOperationResult;
import org.gradle.tooling.events.task.TaskSkippedResult;
import org.gradle.tooling.events.task.TaskSuccessResult;

/**
 * List of the task state for a build.
 */
public class TaskStateList {

    /** State of a task during a build. These states are mutually exclusive. */
    public enum ExecutionState {
        UP_TO_DATE,
        FROM_CACHE,
        DID_WORK,
        SKIPPED,
        FAILED
    }

    public static final class TaskInfo {

        @NonNull private final String taskName;
        @NonNull private final ExecutionState executionState;
        @NonNull private final TaskStateList taskStateList;

        public TaskInfo(
                @NonNull String taskName,
                @NonNull ExecutionState executionState,
                @NonNull TaskStateList taskStateList) {
            this.taskName = taskName;
            this.executionState = executionState;
            this.taskStateList = taskStateList;
        }

        @NonNull
        public String getTaskName() {
            return taskName;
        }

        @SuppressWarnings("unused")
        @NonNull
        public ExecutionState getExecutionState() {
            return executionState;
        }

        public boolean wasUpToDate() {
            return executionState == ExecutionState.UP_TO_DATE;
        }

        public boolean wasFromCache() {
            return executionState == ExecutionState.FROM_CACHE;
        }

        public boolean didWork() {
            return executionState == ExecutionState.DID_WORK;
        }

        public boolean wasSkipped() {
            return executionState == ExecutionState.SKIPPED;
        }

        public boolean failed() {
            return executionState == ExecutionState.FAILED;
        }

        @NonNull
        TaskStateList getTaskStateList() {
            return taskStateList;
        }
    }

    public static final Pattern NO_ACTIONS_PATTERN =
            Pattern.compile("Skipping task '(.*)' as it has no actions.");

    @NonNull private final ImmutableList<String> taskList;
    @NonNull private final ImmutableMap<String, TaskInfo> taskInfoMap;
    @NonNull private final ImmutableMap<ExecutionState, ImmutableSet<String>> taskStateMap;

    public TaskStateList(
            @NonNull List<ProgressEvent> progressEvents, @NonNull String gradleOutput) {
        ImmutableList.Builder<String> taskListBuilder = ImmutableList.builder();
        Map<ExecutionState, Set<String>> taskMap = new EnumMap<>(ExecutionState.class);
        for (ExecutionState state : ExecutionState.values()) {
            taskMap.put(state, new HashSet<>());
        }

        for (ProgressEvent progressEvent : progressEvents) {
            if (progressEvent instanceof TaskFinishEvent) {
                String task = progressEvent.getDescriptor().getName();
                TaskOperationResult result = ((TaskFinishEvent) progressEvent).getResult();

                taskListBuilder.add(task);
                if (result instanceof TaskSuccessResult) {
                    TaskSuccessResult successResult = (TaskSuccessResult) result;
                    // When the task's output is retrieved from the cache, Gradle returns both
                    // upToDate() and fromCache() as true (see
                    // https://github.com/gradle/gradle/issues/5252). Therefore, we need to check
                    // isFromCache() before wasUpToDate().
                    if (successResult.isFromCache()) {
                        taskMap.get(ExecutionState.FROM_CACHE).add(task);
                    } else if (successResult.isUpToDate()) {
                        taskMap.get(ExecutionState.UP_TO_DATE).add(task);
                    } else {
                        taskMap.get(ExecutionState.DID_WORK).add(task);
                    }
                } else if (result instanceof TaskSkippedResult) {
                    taskMap.get(ExecutionState.SKIPPED).add(task);
                } else if (result instanceof TaskFailureResult) {
                    taskMap.get(ExecutionState.FAILED).add(task);
                }
            }
        }

        taskList = taskListBuilder.build();

        // Among the tasks that did work, detect those that were skipped and correct their state to
        // SKIPPED. (For "anchor" tasks such as "build", "check", Gradle does not report them with
        // TaskSkippedResult, so we need to detect them in the Gradle output.)
        ImmutableSet<String> noActionsTasks =
                getTasksByPatternFromGradleOutput(gradleOutput, NO_ACTIONS_PATTERN);
        Preconditions.checkState(taskList.containsAll(noActionsTasks));
        for (String noActionTask : noActionsTasks) {
            if (taskMap.get(ExecutionState.DID_WORK).contains(noActionTask)) {
                taskMap.get(ExecutionState.DID_WORK).remove(noActionTask);
                taskMap.get(ExecutionState.SKIPPED).add(noActionTask);
            }
        }

        ImmutableMap.Builder<String, TaskInfo> taskInfoMapBuilder = ImmutableMap.builder();
        for (ExecutionState state : taskMap.keySet()) {
            for (String task : taskMap.get(state)) {
                taskInfoMapBuilder.put(task, new TaskInfo(task, state, this));
            }
        }
        taskInfoMap = taskInfoMapBuilder.build();

        ImmutableMap.Builder<ExecutionState, ImmutableSet<String>> taskStateMapBuilder =
                ImmutableMap.builder();
        for (ExecutionState state : ExecutionState.values()) {
            taskStateMapBuilder.put(state, ImmutableSet.copyOf(taskMap.get(state)));
        }
        taskStateMap = taskStateMapBuilder.build();
    }

    @NonNull
    private static ImmutableSet<String> getTasksByPatternFromGradleOutput(
            @NonNull String gradleOutput, @NonNull Pattern pattern) {
        ImmutableSet.Builder<String> result = ImmutableSet.builder();
        Matcher matcher = pattern.matcher(gradleOutput);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result.build();
    }

    @Nullable
    public TaskInfo findTask(@NonNull String task) {
        return taskInfoMap.get(task);
    }

    @NonNull
    public TaskInfo getTask(@NonNull String task) {
        return checkNotNull(taskInfoMap.get(task), "Task %s not found", task);
    }

    @NonNull
    public List<String> getTasks() {
        return taskList;
    }

    @NonNull
    public Set<String> getUpToDateTasks() {
        return taskStateMap.get(ExecutionState.UP_TO_DATE);
    }

    @NonNull
    public Set<String> getFromCacheTasks() {
        return taskStateMap.get(ExecutionState.FROM_CACHE);
    }

    @NonNull
    public Set<String> getDidWorkTasks() {
        return taskStateMap.get(ExecutionState.DID_WORK);
    }

    @NonNull
    public Set<String> getSkippedTasks() {
        return taskStateMap.get(ExecutionState.SKIPPED);
    }

    @NonNull
    public Set<String> getFailedTasks() {
        return taskStateMap.get(ExecutionState.FAILED);
    }

    int getTaskIndex(String taskName) {
        Preconditions.checkArgument(
                taskName.startsWith(":"), "Task name (\"" + taskName + "\") must start with ':'");
        Preconditions.checkArgument(taskList.contains(taskName), "Task %s not run", taskName);
        return taskList.indexOf(taskName);
    }
}
