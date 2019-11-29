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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.cxx.configure.GradleBuildLoggingEnvironment;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidVariantTask;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.ide.common.process.ProcessException;
import java.io.IOException;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;

/** Task wrapper around ExternalNativeJsonGenerator. */
public class ExternalNativeBuildJsonTask extends AndroidVariantTask {

    private ExternalNativeJsonGenerator generator;

    @TaskAction
    public void build() throws ProcessException, IOException {
        try (GradleBuildLoggingEnvironment ignore =
                new GradleBuildLoggingEnvironment(getLogger(), getVariantName())) {
            generator.build();
        }
    }

    @Nested
    public ExternalNativeJsonGenerator getExternalNativeJsonGenerator() {
        return generator;
    }

    @NonNull
    public static VariantTaskCreationAction<ExternalNativeBuildJsonTask> createTaskConfigAction(
            @NonNull final ExternalNativeJsonGenerator generator,
            @NonNull final VariantScope scope) {
        return new CreationAction(scope, generator);
    }

    private static class CreationAction
            extends VariantTaskCreationAction<ExternalNativeBuildJsonTask> {

        private final ExternalNativeJsonGenerator generator;

        private CreationAction(VariantScope scope, ExternalNativeJsonGenerator generator) {
            super(scope);
            this.generator = generator;
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("generateJsonModel");
        }

        @NonNull
        @Override
        public Class<ExternalNativeBuildJsonTask> getType() {
            return ExternalNativeBuildJsonTask.class;
        }

        @Override
        public void configure(@NonNull ExternalNativeBuildJsonTask task) {
            super.configure(task);
            task.generator = generator;
        }
    }
}
