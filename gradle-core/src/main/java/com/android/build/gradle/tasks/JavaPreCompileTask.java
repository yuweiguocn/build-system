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

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.PROCESSED_JAR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.ANNOTATION_PROCESSOR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.scope.BuildArtifactsHolder.OperationType.APPEND;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.api.AnnotationProcessorOptions;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidBuilderTask;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.google.common.base.Joiner;
import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/** Tasks to perform necessary action before a JavaCompile. */
@CacheableTask
public class JavaPreCompileTask extends AndroidBuilderTask {

    private File processorListFile;

    private String annotationProcessorConfigurationName;

    private ArtifactCollection annotationProcessorConfiguration;

    private ArtifactCollection compileClasspaths;

    private AnnotationProcessorOptions annotationProcessorOptions;

    private boolean isTestComponent;

    @VisibleForTesting
    void init(
            @NonNull File processorListFile,
            @NonNull String annotationProcessorConfigurationName,
            @NonNull ArtifactCollection annotationProcessorConfiguration,
            @NonNull ArtifactCollection compileClasspaths,
            @NonNull AnnotationProcessorOptions annotationProcessorOptions,
            boolean isTestComponent) {
        this.processorListFile = processorListFile;
        this.annotationProcessorConfigurationName = annotationProcessorConfigurationName;
        this.annotationProcessorConfiguration = annotationProcessorConfiguration;
        this.compileClasspaths = compileClasspaths;
        this.annotationProcessorOptions = annotationProcessorOptions;
        this.isTestComponent = isTestComponent;
    }

    @OutputFile
    public File getProcessorListFile() {
        return processorListFile;
    }

    @Classpath
    public FileCollection getAnnotationProcessorConfiguration() {
        return annotationProcessorConfiguration.getArtifactFiles();
    }

    @Classpath
    public FileCollection getCompileClasspaths() {
        return compileClasspaths.getArtifactFiles();
    }

    @TaskAction
    public void preCompile() {
        if (annotationProcessorOptions.getIncludeCompileClasspath() == null) {
            FileCollection processorClasspath = annotationProcessorConfiguration.getArtifactFiles();

            // Detect processors that are on the compile classpath but not on the annotation
            // processor classpath
            Collection<ResolvedArtifactResult> violatingProcessors =
                    JavaCompileUtils.detectAnnotationProcessors(compileClasspaths).keySet();
            violatingProcessors =
                    violatingProcessors
                            .stream()
                            .filter(artifact -> !processorClasspath.contains(artifact.getFile()))
                            .collect(Collectors.toList());

            if (!violatingProcessors.isEmpty()) {
                Collection<String> violatingProcessorNames =
                        violatingProcessors
                                .stream()
                                .map(artifact -> artifact.getId().getDisplayName())
                                .collect(Collectors.toList());
                String message =
                        "Annotation processors must be explicitly declared now.  The following "
                                + "dependencies on the compile classpath are found to contain "
                                + "annotation processor.  Please add them to the "
                                + annotationProcessorConfigurationName
                                + " configuration.\n  - "
                                + Joiner.on("\n  - ").join(violatingProcessorNames)
                                + "\nAlternatively, set "
                                + "android.defaultConfig.javaCompileOptions.annotationProcessorOptions.includeCompileClasspath = true "
                                + "to continue with previous behavior.  Note that this option "
                                + "is deprecated and will be removed in the future.\n"
                                + "See "
                                + "https://developer.android.com/r/tools/annotation-processor-error-message.html "
                                + "for more details.";
                if (isTestComponent) {
                    getLogger().warn(message);
                } else {
                    throw new RuntimeException(message);
                }
            }
        }

        Map<String, Boolean> annotationProcessors =
                JavaCompileUtils.detectAnnotationProcessors(
                        annotationProcessorOptions,
                        annotationProcessorConfiguration,
                        compileClasspaths);
        JavaCompileUtils.writeAnnotationProcessorsToJsonFile(
                annotationProcessors, processorListFile);
    }

    public static class CreationAction extends VariantTaskCreationAction<JavaPreCompileTask> {

        private Provider<RegularFile> apList;

        public CreationAction(VariantScope scope) {
            super(scope);
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("javaPreCompile");
        }

        @NonNull
        @Override
        public Class<JavaPreCompileTask> getType() {
            return JavaPreCompileTask.class;
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);
            apList =
                    getVariantScope()
                            .getArtifacts()
                            .createArtifactFile(
                                    InternalArtifactType.ANNOTATION_PROCESSOR_LIST,
                                    APPEND,
                                    taskName,
                                    "annotationProcessors.json");
        }

        @Override
        public void configure(@NonNull JavaPreCompileTask task) {
            super.configure(task);
            VariantScope scope = getVariantScope();

            task.init(
                    apList.get().getAsFile(),
                    scope.getVariantData().getType().isTestComponent()
                            ? scope.getVariantData().getType().getPrefix() + "AnnotationProcessor"
                            : "annotationProcessor",
                    scope.getArtifactCollection(ANNOTATION_PROCESSOR, ALL, PROCESSED_JAR),
                    scope.getJavaClasspathArtifacts(COMPILE_CLASSPATH, CLASSES, null),
                    scope.getVariantConfiguration()
                            .getJavaCompileOptions()
                            .getAnnotationProcessorOptions(),
                    scope.getVariantData().getType().isTestComponent());
        }
    }
}
