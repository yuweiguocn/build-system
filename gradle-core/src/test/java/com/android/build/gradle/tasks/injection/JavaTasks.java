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

package com.android.build.gradle.tasks.injection;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.tasks.InternalID;
import com.android.build.gradle.tasks.Replace;
import com.android.build.gradle.tasks.TaskArtifactsHolderTest;
import java.io.File;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;

public class JavaTasks {

    public static class ValidInputTask extends TaskArtifactsHolderTest.TestTask {

        @InputFiles
        @InternalID(InternalArtifactType.APP_CLASSES)
        public BuildableArtifact getAppClasses() {
            return appClasses;
        }

        private BuildableArtifact appClasses;

        @InputFiles
        @InternalID(InternalArtifactType.APK_MAPPING)
        @Optional
        public BuildableArtifact getApkMapping() {
            return apkMapping;
        }

        private BuildableArtifact apkMapping;

        @Override
        public void executeTask(@NonNull Object... parameters) {
            assertThat(appClasses).isNotNull();
            assertThat(apkMapping).isNull();
        }
    }

    public static class ValidInputSubTask extends ValidInputTask {

        @Override
        public void executeTask(@NonNull Object... parameters) {
            assertThat(getAppClasses()).isNotNull();
            assertThat(getApkMapping()).isNull();
        }
    }

    public static class ValidOutputTask extends TaskArtifactsHolderTest.TestTask {
        @OutputDirectory
        @InternalID(InternalArtifactType.APP_CLASSES)
        @Replace
        public Provider<Directory> getClasses() {
            return classes;
        }

        @SuppressWarnings("unused")
        private Provider<Directory> classes;

        @Override
        public void executeTask(@NonNull Object... parameters) {
            assertThat(classes).isNotNull();
            assertThat(classes).isEqualTo(parameters[0]);
        }
    }

    public static class InvalidOutputTypeTask extends TaskArtifactsHolderTest.TestTask {
        @OutputDirectory
        @InternalID(InternalArtifactType.APP_CLASSES)
        @Replace
        public Directory getClasses() {
            return classes;
        }

        private Directory classes;
    }

    public static class InvalidParameterizedOutputTypeTask
            extends TaskArtifactsHolderTest.TestTask {
        @OutputDirectory
        @InternalID(InternalArtifactType.APP_CLASSES)
        @Replace
        public Directory getClasses() {
            return classes;
        }

        private Directory classes;
    }

    public static class NoParameterizedOutputTypeTask extends TaskArtifactsHolderTest.TestTask {
        @OutputDirectory
        @InternalID(InternalArtifactType.APP_CLASSES)
        @Replace
        public Provider<?> getClasses() {
            return classes;
        }

        private Provider<?> classes;
    }

    public static class MismatchedOutputTypeTask extends TaskArtifactsHolderTest.TestTask {
        @OutputDirectory
        @InternalID(InternalArtifactType.BUNDLE)
        @Replace
        public Provider<Directory> getClasses() {
            return classes;
        }

        private Provider<Directory> classes;
    }

    public static class NoIDOnInputProvidedTask extends TaskArtifactsHolderTest.TestTask {
        @InputFiles
        public File getClasses() {
            return classes;
        }

        private File classes;
    }
}
