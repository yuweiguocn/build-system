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

package com.android.build.gradle.internal.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.builder.core.DesugarProcessBuilder;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Extracts jar containing classes necessary for try-with-resources support that will be packages in
 * the final APK.
 */
public class ExtractTryWithResourcesSupportJar extends AndroidVariantTask {

    public static final String TASK_NAME = "extractTryWithResourcesSupportJar";

    private ConfigurableFileCollection outputLocation;

    @TaskAction
    public void run() throws IOException {
        try (InputStream in =
                DesugarProcessBuilder.class
                        .getClassLoader()
                        .getResourceAsStream("libthrowable_extension.jar")) {
            FileUtils.cleanOutputDir(outputLocation.getSingleFile().getParentFile());
            Files.copy(in, outputLocation.getSingleFile().toPath());
        }
    }

    @OutputFile
    public File getOutputLocation() {
        return outputLocation.getSingleFile();
    }

    public static class CreationAction
            extends TaskCreationAction<ExtractTryWithResourcesSupportJar> {

        @NonNull private final ConfigurableFileCollection outputLocation;
        @NonNull private final String taskName;
        @NonNull private final String variantName;

        public CreationAction(
                @NonNull ConfigurableFileCollection outputLocation,
                @NonNull String taskName,
                @NonNull String variantName) {
            this.outputLocation = outputLocation;
            this.taskName = taskName;
            this.variantName = variantName;
        }

        @NonNull
        @Override
        public String getName() {
            return taskName;
        }

        @NonNull
        @Override
        public Class<ExtractTryWithResourcesSupportJar> getType() {
            return ExtractTryWithResourcesSupportJar.class;
        }

        @Override
        public void configure(@NonNull ExtractTryWithResourcesSupportJar task) {
            task.outputLocation = outputLocation;
            task.setVariantName(variantName);
        }
    }
}
