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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.List;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;

/**
 * Scope for all variant scoped information related to supporting the Instant Run features.
 */
public interface InstantRunVariantScope {

    @NonNull
    String getFullVariantName();

    @NonNull
    TransformVariantScope getTransformVariantScope();

    @NonNull
    TransformGlobalScope getGlobalScope();

    @NonNull
    File getBuildInfoOutputFolder();

    @NonNull
    File getReloadDexOutputFolder();

    @NonNull
    File getRestartDexOutputFolder();

    @NonNull
    File getManifestCheckerDir();

    @NonNull
    File getIncrementalVerifierDir();

    @NonNull
    InstantRunBuildContext getInstantRunBuildContext();

    @NonNull
    File getInstantRunPastIterationsFolder();

    @NonNull
    File getIncrementalRuntimeSupportJar();

    /** The {@code *.ap_} with added assets, used for hot and cold swaps. */
    @NonNull
    File getInstantRunResourcesFile();

    /**
     * Returns the boot class path which matches the target device API level.
     */
    @NonNull
    ImmutableList<File> getInstantRunBootClasspath();

    List<TaskProvider<? extends Task>> getColdSwapBuildTasks();

    void addColdSwapBuildTask(@NonNull TaskProvider<? extends Task> task);

    @NonNull
    MutableTaskContainer getTaskContainer();
}
