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

package com.android.build.gradle.internal;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.InstantAppProvisionTask;
import com.android.build.gradle.internal.tasks.InstantAppSideLoadTask;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryUtils;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.BundleInstantApp;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.profile.Recorder;
import java.io.File;
import java.util.Collection;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** TaskManager for creating tasks in an Android InstantApp project. */
public class InstantAppTaskManager extends TaskManager {

    public InstantAppTaskManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig extension,
            @NonNull SdkHandler sdkHandler,
            @NonNull VariantFactory variantFactory,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder threadRecorder) {
        super(
                globalScope,
                project,
                projectOptions,
                dataBindingBuilder,
                extension,
                sdkHandler,
                variantFactory,
                toolingRegistry,
                threadRecorder);
    }

    @Override
    public void createTasksForVariantScope(@NonNull final VariantScope variantScope) {
        // Create the bundling task.
        File bundleDir = variantScope.getApkLocation();
        TaskProvider<BundleInstantApp> bundleTask =
                taskFactory.register(new BundleInstantApp.CreationAction(variantScope, bundleDir));

        TaskFactoryUtils.dependsOn(variantScope.getTaskContainer().getAssembleTask(), bundleTask);

        taskFactory.register(new InstantAppSideLoadTask.CreationAction(variantScope));

        // FIXME: Stop creating a dummy task just to make the IDE sync shut up.
        taskFactory.register(variantScope.getTaskName("dummy"));
    }

    @Override
    public void createTasksBeforeEvaluate() {
        super.createTasksBeforeEvaluate();

        taskFactory.register(new InstantAppProvisionTask.CreationAction(globalScope));
    }

    @NonNull
    @Override
    protected Set<QualifiedContent.Scope> getResMergingScopes(@NonNull VariantScope variantScope) {
        return TransformManager.EMPTY_SCOPES;
    }

    @Override
    protected void postJavacCreation(@NonNull VariantScope scope) {
        // do nothing.
    }

    @Override
    public void createGlobalLintTask() {
        // do nothing.
    }

    @Override
    public void configureGlobalLintTask(@NonNull Collection<VariantScope> variants) {
        // do nothing.
    }
}
