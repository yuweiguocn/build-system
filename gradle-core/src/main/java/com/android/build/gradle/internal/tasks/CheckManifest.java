/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import java.io.File;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

/** Class that checks the presence of the manifest. */
public class CheckManifest extends AndroidVariantTask {

    private File manifest;
    private Boolean isOptional;
    private File fakeOutputDir;

    @Optional
    @Input // we don't care about the content, just that the file is there.
    public File getManifest() {
        return manifest;
    }

    @Input // force rerunning the task if the manifest shows up or disappears.
    public boolean getManifestPresence() {
        return manifest != null && manifest.isFile();
    }

    public void setManifest(@NonNull File manifest) {
        this.manifest = manifest;
    }

    @Input
    public Boolean getOptional() {
        return isOptional;
    }

    public void setOptional(Boolean optional) {
        isOptional = optional;
    }

    @OutputDirectory
    public File getFakeOutputDir() {
        return fakeOutputDir;
    }

    @TaskAction
    void check() {
        if (!isOptional && manifest != null && !manifest.isFile()) {
            throw new IllegalArgumentException(
                    String.format(
                            "Main Manifest missing for variant %1$s. Expected path: %2$s",
                            getVariantName(), getManifest().getAbsolutePath()));
        }
    }

    public static class CreationAction extends VariantTaskCreationAction<CheckManifest> {

        private final boolean isManifestOptional;
        private File output;

        public CreationAction(@NonNull VariantScope scope, boolean isManifestOptional) {
            super(scope);
            this.isManifestOptional = isManifestOptional;
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("check", "Manifest");
        }

        @NonNull
        @Override
        public Class<CheckManifest> getType() {
            return CheckManifest.class;
        }

        @Override
        public void handleProvider(@NonNull TaskProvider<? extends CheckManifest> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope().getTaskContainer().setCheckManifestTask(taskProvider);
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);
            output =
                    getVariantScope()
                            .getArtifacts()
                            .appendArtifact(
                                    InternalArtifactType.CHECK_MANIFEST_RESULT, taskName, "out");
        }

        @Override
        public void configure(@NonNull CheckManifest task) {
            super.configure(task);
            VariantScope scope = getVariantScope();

            task.setOptional(isManifestOptional);
            task.manifest = scope.getVariantConfiguration().getMainManifest();
            task.fakeOutputDir = output;
        }
    }
}
