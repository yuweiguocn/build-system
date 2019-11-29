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

package com.android.build.gradle.internal.tasks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.FeaturePlugin;
import com.android.build.gradle.ProguardFiles;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.builder.errors.EvalIssueException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.TaskAction;

/** Configuration action for a merge-Proguard-files task. */
public class MergeConsumerProguardFilesTask extends MergeFileTask {

    private boolean isDynamicFeature;
    private boolean isBaseFeature;
    private boolean hasFeaturePlugin;
    private List<File> consumerProguardFiles;

    @Override
    @TaskAction
    public void mergeFiles() throws IOException {
        final Project project = getProject();

        // We check for default files unless it's a base feature, which can include default files.
        if (!isBaseFeature) {
            checkProguardFiles(
                    project,
                    isDynamicFeature,
                    hasFeaturePlugin,
                    consumerProguardFiles,
                    exception -> {
                        throw exception;
                    });
        }
        super.mergeFiles();
    }

    public static void checkProguardFiles(
            Project project,
            boolean isDynamicFeature,
            boolean hasFeaturePlugin,
            List<File> consumerProguardFiles,
            Consumer<EvalIssueException> exceptionHandler) {
        Map<File, String> defaultFiles = new HashMap<>();
        for (String knownFileName : ProguardFiles.KNOWN_FILE_NAMES) {
            defaultFiles.put(
                    ProguardFiles.getDefaultProguardFile(knownFileName, project), knownFileName);
        }

        for (File consumerProguardFile : consumerProguardFiles) {
            if (defaultFiles.containsKey(consumerProguardFile)) {
                final String errorMessage;
                if (isDynamicFeature || hasFeaturePlugin) {
                    errorMessage =
                            "Default file "
                                    + defaultFiles.get(consumerProguardFile)
                                    + " should not be specified in this module."
                                    + " It can be specified in the base module instead.";

                } else {
                    errorMessage =
                            "Default file "
                                    + defaultFiles.get(consumerProguardFile)
                                    + " should not be used as a consumer configuration file.";
                }

                exceptionHandler.accept(new EvalIssueException(errorMessage));
            }
        }
    }

    public static class CreationAction extends TaskCreationAction<MergeConsumerProguardFilesTask> {

        @NonNull private final VariantScope variantScope;
        private File outputFile;

        public CreationAction(@NonNull VariantScope variantScope) {
            this.variantScope = variantScope;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("merge", "ConsumerProguardFiles");
        }

        @NonNull
        @Override
        public Class<MergeConsumerProguardFilesTask> getType() {
            return MergeConsumerProguardFilesTask.class;
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);
            outputFile =
                    variantScope
                            .getArtifacts()
                            .appendArtifact(
                                    InternalArtifactType.CONSUMER_PROGUARD_FILE,
                                    taskName,
                                    SdkConstants.FN_PROGUARD_TXT);
        }

        @Override
        public void configure(@NonNull MergeConsumerProguardFilesTask task) {
            task.setVariantName(variantScope.getFullVariantName());
            GlobalScope globalScope = variantScope.getGlobalScope();
            Project project = globalScope.getProject();
            task.setOutputFile(outputFile);

            task.hasFeaturePlugin = project.getPlugins().hasPlugin(FeaturePlugin.class);
            task.isBaseFeature =
                    task.hasFeaturePlugin && globalScope.getExtension().getBaseFeature();
            if (task.isBaseFeature) {
                task.isDynamicFeature = variantScope.getType().isDynamicFeature();
            }

            task.consumerProguardFiles = variantScope.getConsumerProguardFilesForFeatures();

            ConfigurableFileCollection inputFiles = project.files(task.consumerProguardFiles);
            if (variantScope.getType().isFeatureSplit()) {
                inputFiles.from(
                        variantScope.getArtifactFileCollection(
                                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                                AndroidArtifacts.ArtifactScope.ALL,
                                AndroidArtifacts.ArtifactType.CONSUMER_PROGUARD_RULES));
            }
            task.setInputFiles(inputFiles);
        }
    }
}
