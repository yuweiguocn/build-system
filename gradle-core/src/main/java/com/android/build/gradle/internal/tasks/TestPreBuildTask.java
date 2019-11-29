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

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.EXTERNAL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.scope.VariantScope;
import java.io.File;
import java.util.Objects;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.TaskAction;

/**
 * Pre build task that checks that there are not differences between artifact versions between the
 * runtime classpath of tested variant, and runtime classpath of test variant.
 */
@CacheableTask
public class TestPreBuildTask extends ClasspathComparisionTask {

    @Override
    void onDifferentVersionsFound(
            @NonNull String group,
            @NonNull String module,
            @NonNull String runtimeVersion,
            @NonNull String compileVersion) {
        throw new GradleException(
                String.format(
                        "Conflict with dependency '%s:%s' in project '%s'. Resolved versions for"
                                + " app (%s) and test app (%s) differ. See"
                                + " https://d.android.com/r/tools/test-apk-dependency-conflicts.html"
                                + " for details.",
                        group, module, getProject().getPath(), compileVersion, runtimeVersion));
    }

    @TaskAction
    void run() {
        compareClasspaths();
    }

    public static class CreationAction
            extends TaskManager.AbstractPreBuildCreationAction<TestPreBuildTask> {

        public CreationAction(@NonNull VariantScope variantScope) {
            super(variantScope);
        }

        @NonNull
        @Override
        public Class<TestPreBuildTask> getType() {
            return TestPreBuildTask.class;
        }

        @Override
        public void configure(@NonNull TestPreBuildTask task) {
            super.configure(task);
            task.setVariantName(variantScope.getFullVariantName());

            task.runtimeClasspath =
                    variantScope.getArtifactCollection(RUNTIME_CLASSPATH, EXTERNAL, CLASSES);

            task.compileClasspath =
                    Objects.requireNonNull(variantScope.getTestedVariantData())
                            .getScope()
                            .getArtifactCollection(RUNTIME_CLASSPATH, EXTERNAL, CLASSES);

            task.fakeOutputDirectory =
                    new File(
                            variantScope.getGlobalScope().getIntermediatesDir(),
                            "prebuild/" + variantScope.getVariantConfiguration().getDirName());
        }
    }
}
