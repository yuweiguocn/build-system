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

package com.android.build.gradle.tasks;

import static com.android.build.VariantOutput.OutputType.FULL_SPLIT;
import static com.android.build.VariantOutput.OutputType.MAIN;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.LINT;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.android.build.gradle.internal.scope.AnchorOutputType.ALL_CLASSES;
import static com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_MANIFEST;
import static com.android.build.gradle.internal.scope.InternalArtifactType.LINT_JAR;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MANIFEST_MERGE_REPORT;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_MANIFESTS;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.dsl.LintOptions;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.Version;
import com.android.builder.sdk.TargetInfo;
import com.android.sdklib.BuildToolInfo;
import com.android.tools.lint.gradle.api.ReflectiveLintRunner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

public abstract class LintBaseTask extends DefaultTask {
    public static final String LINT_CLASS_PATH = "lintClassPath";

    protected static final Logger LOG = Logging.getLogger(LintBaseTask.class);

    @Nullable FileCollection lintClassPath;
    protected AndroidBuilder androidBuilder;

    /** Lint classpath */
    @InputFiles
    @Nullable
    public FileCollection getLintClassPath() {
        return lintClassPath;
    }

    @Nullable protected LintOptions lintOptions;
    @Nullable protected File sdkHome;
    protected ToolingModelBuilderRegistry toolingRegistry;
    @Nullable protected File reportsDir;

    @Nullable
    public LintOptions getLintOptions() {
        return lintOptions;
    }

    protected void runLint(LintBaseTaskDescriptor descriptor) {
        FileCollection lintClassPath = getLintClassPath();
        if (lintClassPath != null) {
            new ReflectiveLintRunner().runLint(getProject().getGradle(),
                    descriptor, lintClassPath.getFiles());
        }
    }

    @Internal("No influence on output, this is to give access to the build tools")
    private BuildToolInfo getBuildTools() {
        TargetInfo targetInfo = androidBuilder.getTargetInfo();
        Preconditions.checkState(
                targetInfo != null, "androidBuilder.targetInfo required for task '%s'.", getName());
        return targetInfo.getBuildTools();
    }

    protected abstract class LintBaseTaskDescriptor extends
            com.android.tools.lint.gradle.api.LintExecutionRequest {
        @Override
        @Nullable
        public File getSdkHome() {
            return sdkHome;
        }

        @NonNull
        @Override
        public ToolingModelBuilderRegistry getToolingRegistry() {
            return toolingRegistry;
        }

        @Nullable
        @Override
        public LintOptions getLintOptions() {
            return lintOptions;
        }

        @Override
        @Nullable
        public File getReportsDir() {
            return reportsDir;
        }

        @NonNull
        @Override
        public Project getProject() {
            return LintBaseTask.this.getProject();
        }

        @NonNull
        @Override
        public BuildToolInfo getBuildTools() {
            return LintBaseTask.this.getBuildTools();
        }

        @Override
        public void warn(@NonNull String message, @NonNull Object... args) {
            LOG.warn(message, args);
        }

        @NonNull
        @Override
        public String getGradlePluginVersion() {
            return Version.ANDROID_GRADLE_PLUGIN_VERSION;
        }
    }

    public static class VariantInputs implements com.android.tools.lint.gradle.api.VariantInputs {
        @NonNull private final String name;
        @NonNull private final BuildableArtifact localLintJarCollection;
        @NonNull private final FileCollection dependencyLintJarCollection;
        @NonNull private final Provider<? extends FileSystemLocation> mergedManifest;
        @Nullable private final BuildableArtifact mergedManifestReport;
        private List<File> lintRuleJars;

        private final ConfigurableFileCollection allInputs;

        public VariantInputs(@NonNull VariantScope variantScope) {
            name = variantScope.getFullVariantName();
            allInputs = variantScope.getGlobalScope().getProject().files();

            allInputs.from(
                    localLintJarCollection =
                            variantScope
                                    .getGlobalScope()
                                    .getArtifacts()
                                    .getFinalArtifactFiles(LINT_JAR));
            allInputs.from(
                    dependencyLintJarCollection =
                            variantScope.getArtifactFileCollection(RUNTIME_CLASSPATH, ALL, LINT));

            BuildArtifactsHolder artifacts = variantScope.getArtifacts();
            Provider<? extends FileSystemLocation> tmpMergedManifest =
                    artifacts.getFinalProduct(MERGED_MANIFESTS);
            if (!tmpMergedManifest.isPresent()) {
                tmpMergedManifest = artifacts.getFinalProduct(LIBRARY_MANIFEST);
            }
            if (!tmpMergedManifest.isPresent()) {
                throw new RuntimeException(
                        "VariantInputs initialized with no merged manifest on: "
                                + variantScope.getVariantConfiguration().getType());
            }
            mergedManifest = tmpMergedManifest;
            allInputs.from(mergedManifest);

            if (artifacts.hasArtifact(MANIFEST_MERGE_REPORT)) {
                allInputs.from(
                        mergedManifestReport =
                                artifacts.getFinalArtifactFiles(MANIFEST_MERGE_REPORT));
            } else {
                throw new RuntimeException(
                        "VariantInputs initialized with no merged manifest report on: "
                                + variantScope.getVariantConfiguration().getType());
            }

            // these inputs are only there to ensure that the lint task runs after these build
            // intermediates are built.
            allInputs.from(artifacts.getFinalArtifactFiles(ALL_CLASSES));
        }

        @NonNull
        public FileCollection getAllInputs() {
            return allInputs;
        }

        @Override
        @NonNull
        public String getName() {
            return name;
        }

        /** the lint rule jars */
        @Override
        @NonNull
        public List<File> getRuleJars() {
            if (lintRuleJars == null) {
                lintRuleJars =
                        Streams.concat(
                                        dependencyLintJarCollection.getFiles().stream(),
                                        localLintJarCollection.getFiles().stream())
                                .filter(File::isFile)
                                .collect(Collectors.toList());
            }

            return lintRuleJars;
        }

        /** the merged manifest of the current module */
        @Override
        @NonNull
        public File getMergedManifest() {
            File file = mergedManifest.get().getAsFile();
            if (file.isFile()) {
                return file;
            }

            BuildElements manifests =
                    ExistingBuildElements.from(InternalArtifactType.MERGED_MANIFESTS, file);

            if (manifests.isEmpty()) {
                throw new RuntimeException("Can't find any manifest in folder: " + file);
            }

            // first search for a main manifest
            Optional<File> mainManifest =
                    manifests
                            .stream()
                            .filter(buildOutput -> buildOutput.getApkData().getType() == MAIN)
                            .map(BuildOutput::getOutputFile)
                            .findFirst();
            if (mainManifest.isPresent()) {
                return mainManifest.get();
            }

            // else search for a full_split with no filters.
            Optional<File> universalSplit =
                    manifests
                            .stream()
                            .filter(
                                    output ->
                                            output.getApkData().getType() == FULL_SPLIT
                                                    && output.getFilters().isEmpty())
                            .map(BuildOutput::getOutputFile)
                            .findFirst();

            // return the universal Manifest, or a random one if not found.
            return universalSplit.orElseGet(() -> manifests.iterator().next().getOutputFile());
        }

        @Override
        @Nullable
        public File getManifestMergeReport() {
            if (mergedManifestReport == null) {
                return null;
            }

            return Iterables.getOnlyElement(mergedManifestReport);
        }
    }

    public abstract static class BaseCreationAction<T extends LintBaseTask>
            extends TaskCreationAction<T> {

        @NonNull private final GlobalScope globalScope;

        public BaseCreationAction(@NonNull GlobalScope globalScope) {
            this.globalScope = globalScope;
        }

        @NonNull
        protected GlobalScope getGlobalScope() {
            return globalScope;
        }

        @Override
        public void configure(@NonNull T lintTask) {
            lintTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
            lintTask.lintOptions = globalScope.getExtension().getLintOptions();
            File sdkFolder = globalScope.getSdkHandler().getSdkFolder();
            if (sdkFolder != null) {
                lintTask.sdkHome = sdkFolder;
            }

            lintTask.toolingRegistry = globalScope.getToolingRegistry();
            lintTask.reportsDir = globalScope.getReportsDir();
            lintTask.androidBuilder = globalScope.getAndroidBuilder();

            lintTask.lintClassPath = globalScope.getProject().getConfigurations()
                    .getByName(LINT_CLASS_PATH);
        }
    }
}
