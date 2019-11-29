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
package com.android.build.gradle.internal.tasks;

import com.android.annotations.NonNull;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.coverage.JacocoConfigurations;
import com.android.build.gradle.internal.scope.AnchorOutputType;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.builder.dexing.DexerTool;
import java.io.File;
import java.io.IOException;
import javax.inject.Inject;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.workers.WorkerExecutor;

@CacheableTask
public class JacocoTask extends AndroidVariantTask {
    @NonNull private final WorkerExecutor executor;
    private FileCollection jacocoAntTaskConfiguration;
    private BuildableArtifact inputClasses;
    private File output;
    private JacocoTaskDelegate delegate;

    @Inject
    public JacocoTask(@NonNull WorkerExecutor executor) {
        this.executor = executor;
    }

    @InputFiles
    public FileCollection getJacocoAntTaskConfiguration() {
        return jacocoAntTaskConfiguration;
    }

    @InputFiles
    public BuildableArtifact getInputClasses() {
        return inputClasses;
    }

    @OutputDirectory
    public File getOutput() {
        return output;
    }

    /** Returns which Jacoco version to use. */
    @NonNull
    public static String getJacocoVersion(@NonNull VariantScope scope) {
        if (scope.getDexer() == DexerTool.DX) {
            return JacocoConfigurations.VERSION_FOR_DX;
        } else {
            return scope.getGlobalScope().getExtension().getJacoco().getVersion();
        }
    }

    @TaskAction
    public void run(@NonNull IncrementalTaskInputs inputs) throws IOException {
        delegate.run(executor, inputs);
    }

    public static class CreationAction extends VariantTaskCreationAction<JacocoTask> {

        private File output;

        public CreationAction(@NonNull VariantScope scope) {
            super(scope);
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("jacoco");
        }

        @NonNull
        @Override
        public Class<JacocoTask> getType() {
            return JacocoTask.class;
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);
            output =
                    getVariantScope()
                            .getArtifacts()
                            .appendArtifact(
                                    InternalArtifactType.JACOCO_INSTRUMENTED_CLASSES,
                                    taskName,
                                    "out");
        }

        @Override
        public void configure(@NonNull JacocoTask task) {
            super.configure(task);
            VariantScope scope = getVariantScope();

            task.inputClasses =
                    scope.getArtifacts().getFinalArtifactFiles(AnchorOutputType.ALL_CLASSES);
            task.jacocoAntTaskConfiguration =
                    JacocoConfigurations.getJacocoAntTaskConfiguration(
                            scope.getGlobalScope().getProject(), getJacocoVersion(scope));
            task.output = output;
            task.delegate =
                    new JacocoTaskDelegate(
                            task.jacocoAntTaskConfiguration, task.output, task.inputClasses);
        }
    }
}
