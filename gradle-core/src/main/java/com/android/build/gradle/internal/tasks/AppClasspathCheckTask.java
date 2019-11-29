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

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.EXTERNAL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.builder.errors.EvalIssueException;
import com.android.builder.errors.EvalIssueReporter;
import com.android.ide.common.repository.GradleVersion;
import com.android.utils.FileUtils;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.TaskAction;

/**
 * Pre build task that performs comparison of runtime and compile classpath for application. If
 * there are any differences between the two, that could lead to runtime issues.
 */
@CacheableTask
public class AppClasspathCheckTask extends ClasspathComparisionTask {

    private EvalIssueReporter reporter;

    @Override
    void onDifferentVersionsFound(
            @NonNull String group,
            @NonNull String module,
            @NonNull String runtimeVersion,
            @NonNull String compileVersion) {

        String suggestedVersion;
        try {
            GradleVersion runtime = GradleVersion.parse(runtimeVersion);
            GradleVersion compile = GradleVersion.parse(compileVersion);
            if (runtime.compareTo(compile) > 0) {
                suggestedVersion = runtimeVersion;
            } else {
                suggestedVersion = compileVersion;
            }
        } catch (Throwable e) {
            // in case we are unable to parse versions for some reason, choose runtime
            suggestedVersion = runtimeVersion;
        }

        String message =
                String.format(
                        "Conflict with dependency '%1$s:%2$s' in project '%3$s'. Resolved versions for "
                                + "runtime classpath (%4$s) and compile classpath (%5$s) differ. This "
                                + "can lead to runtime crashes. To resolve this issue follow "
                                + "advice at https://developer.android.com/studio/build/gradle-tips#configure-project-wide-properties. "
                                + "Alternatively, you can try to fix the problem "
                                + "by adding this snippet to %6$s:\n"
                                + "dependencies {\n"
                                + "    implementation(\"%1$s:%2$s:%7$s\")\n"
                                + "}\n",
                        group,
                        module,
                        getProject().getPath(),
                        runtimeVersion,
                        compileVersion,
                        getProject().getBuildFile(),
                        suggestedVersion);

        reporter.reportError(EvalIssueReporter.Type.GENERIC, new EvalIssueException(message));
    }

    @TaskAction
    void run() {
        compareClasspaths();
    }

    public static class CreationAction extends TaskCreationAction<AppClasspathCheckTask> {

        @NonNull private final VariantScope variantScope;

        public CreationAction(@NonNull VariantScope variantScope) {
            this.variantScope = variantScope;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("check", "Classpath");
        }

        @NonNull
        @Override
        public Class<AppClasspathCheckTask> getType() {
            return AppClasspathCheckTask.class;
        }

        @Override
        public void configure(@NonNull AppClasspathCheckTask task) {
            task.setVariantName(variantScope.getFullVariantName());

            task.runtimeClasspath =
                    variantScope.getArtifactCollection(RUNTIME_CLASSPATH, EXTERNAL, CLASSES);
            task.compileClasspath =
                    variantScope.getArtifactCollection(COMPILE_CLASSPATH, EXTERNAL, CLASSES);
            task.fakeOutputDirectory =
                    FileUtils.join(
                            variantScope.getGlobalScope().getIntermediatesDir(),
                            getName(),
                            variantScope.getVariantConfiguration().getDirName());
            task.reporter = variantScope.getGlobalScope().getErrorHandler();
        }
    }
}
