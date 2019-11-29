/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.featuresplit;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidVariantTask;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * Task that writes the FeatureSplitDeclaration file and publish it for other modules to consume.
 */
public class FeatureSplitDeclarationWriterTask extends AndroidVariantTask {

    @VisibleForTesting String uniqueIdentifier;
    @VisibleForTesting Supplier<String> originalApplicationIdSupplier;
    @VisibleForTesting File outputDirectory;

    @Input
    public String getUniqueIdentifier() {
        return uniqueIdentifier;
    }

    @Input
    public String getApplicationId() {
        return originalApplicationIdSupplier.get();
    }

    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    @TaskAction
    public void fullTaskAction() throws IOException {
        FeatureSplitDeclaration declaration =
                new FeatureSplitDeclaration(uniqueIdentifier, getApplicationId());
        declaration.save(outputDirectory);
    }

    public static class CreationAction
            extends VariantTaskCreationAction<FeatureSplitDeclarationWriterTask> {

        private File outputDirectory;

        public CreationAction(@NonNull VariantScope variantScope) {
            super(variantScope);
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("feature", "Writer");
        }

        @NonNull
        @Override
        public Class<FeatureSplitDeclarationWriterTask> getType() {
            return FeatureSplitDeclarationWriterTask.class;
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);
            outputDirectory =
                    getVariantScope()
                            .getArtifacts()
                            .appendArtifact(
                                    InternalArtifactType.METADATA_FEATURE_DECLARATION,
                                    taskName,
                                    "out");
        }

        @Override
        public void configure(@NonNull FeatureSplitDeclarationWriterTask task) {
            super.configure(task);

            task.uniqueIdentifier = getVariantScope().getGlobalScope().getProject().getPath();
            task.originalApplicationIdSupplier =
                    getVariantScope().getVariantConfiguration()::getOriginalApplicationId;
            task.outputDirectory = outputDirectory;
        }
    }
}
