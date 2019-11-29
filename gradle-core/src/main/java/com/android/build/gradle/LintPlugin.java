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

package com.android.build.gradle;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.dsl.LintOptions;
import com.android.build.gradle.internal.tasks.LintStandaloneTask;
import java.io.File;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.TaskContainer;

/**
 * Plugin for running lint <b>without</b> the Android Gradle plugin, such as in a pure Kotlin
 * project.
 */
public class LintPlugin implements Plugin<Project> {
    private Project project;
    private LintOptions lintOptions;

    @Override
    public void apply(Project project) {
        // We run by default in headless mode, so the JVM doesn't steal focus.
        System.setProperty("java.awt.headless", "true");

        this.project = project;
        createExtension(project);
        BasePlugin.createLintClasspathConfiguration(project);
        withJavaPlugin(
                plugin -> {
                    JavaPluginConvention javaConvention = getJavaPluginConvention();
                    if (javaConvention != null) {
                        Configuration customLintChecksConfig =
                                TaskManager.createCustomLintChecksConfig(project);

                        String projectName = project.getName();
                        LintStandaloneTask task =
                                createTask(
                                        "lint",
                                        "Run Android Lint analysis on project '"
                                                + projectName
                                                + "'",
                                        project,
                                        javaConvention,
                                        customLintChecksConfig);
                        // Make the check task depend on the lint
                        project.getTasks()
                                .findByName(JavaBasePlugin.CHECK_TASK_NAME)
                                .dependsOn(task);

                        LintStandaloneTask lintVital =
                                createTask(
                                        "lintVital",
                                        "Runs lint on just the fatal issues in the project '"
                                                + projectName
                                                + "'",
                                        project,
                                        javaConvention,
                                        customLintChecksConfig);
                        lintVital.setFatalOnly(true);

                        LintStandaloneTask lintFix =
                                createTask(
                                        "lintFix",
                                        "Runs lint on `"
                                                + projectName
                                                + "` and applies any safe suggestions to the source code.",
                                        project,
                                        javaConvention,
                                        customLintChecksConfig);
                        lintFix.setAutoFix(true);
                        lintFix.setGroup("cleanup");
                    }
                });
    }

    private void createExtension(Project project) {
        lintOptions = project.getExtensions().create("lintOptions", LintOptions.class);
    }

    private void withJavaPlugin(Action<Plugin> action) {
        project.getPlugins().withType(JavaBasePlugin.class, action);
    }

    @NonNull
    private LintStandaloneTask createTask(
            @NonNull String taskName,
            @NonNull String description,
            @NonNull Project project,
            @NonNull JavaPluginConvention javaConvention,
            @NonNull Configuration customLintChecksConfig) {
        File testResultsDir = javaConvention.getTestResultsDir();
        TaskContainer tasks = project.getTasks();
        LintStandaloneTask task = tasks.create(taskName, LintStandaloneTask.class);
        task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        task.setDescription(description);
        task.setReportDir(testResultsDir);
        task.setLintOptions(lintOptions);
        task.setLintChecks(customLintChecksConfig);
        task.getOutputs().upToDateWhen(task1 -> false);
        return task;
    }

    @Nullable
    private JavaPluginConvention getJavaPluginConvention() {
        Convention convention = project.getConvention();
        JavaPluginConvention javaConvention = convention.getPlugin(JavaPluginConvention.class);
        if (javaConvention == null) {
            project.getLogger().warn("Cannot apply lint if the java or kotlin Gradle plugins " +
                    "have also been applied");
            return null;
        }
        return javaConvention;
    }
}
