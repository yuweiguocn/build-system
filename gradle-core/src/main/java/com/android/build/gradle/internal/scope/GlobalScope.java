/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.scope;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ARTIFACT_TYPE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.MOCKABLE_JAR_RETURN_DEFAULT_VALUES;
import static com.android.builder.core.BuilderConstants.FD_REPORTS;
import static com.android.builder.model.AndroidProject.FD_GENERATED;
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;
import static com.android.builder.model.AndroidProject.FD_OUTPUTS;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.FeatureExtension;
import com.android.build.gradle.internal.FeatureModelBuilder;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.api.dsl.DslScope;
import com.android.build.gradle.internal.api.sourcesets.FilesProvider;
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension;
import com.android.build.gradle.internal.errors.SyncIssueHandler;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.OptionalCompilationStep;
import com.android.builder.sdk.TargetInfo;
import com.android.builder.utils.FileCache;
import com.android.ide.common.blame.MessageReceiver;
import com.android.ide.common.process.ProcessExecutor;
import com.google.common.base.Preconditions;
import java.io.File;
import java.util.Set;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** A scope containing data for the Android plugin. */
public class GlobalScope implements TransformGlobalScope {

    @NonNull private final Project project;
    @NonNull private final FilesProvider filesProvider;
    @NonNull private final AndroidBuilder androidBuilder;
    @NonNull private AndroidConfig extension;
    @NonNull private final SdkHandler sdkHandler;
    @NonNull private NdkHandler ndkHandler;
    @NonNull private final ToolingModelBuilderRegistry toolingRegistry;
    @NonNull private final Set<OptionalCompilationStep> optionalCompilationSteps;
    @NonNull private final ProjectOptions projectOptions;
    @Nullable private final FileCache buildCache;

    @NonNull private final DslScope dslScope;

    @NonNull private Configuration lintChecks;
    @NonNull private Configuration lintPublish;

    private Configuration androidJarConfig;

    @Nullable private ConfigurableFileCollection java8LangSupportJar = null;

    @NonNull private final BuildArtifactsHolder globalArtifacts;

    @Nullable private ConfigurableFileCollection bootClasspath = null;

    public GlobalScope(
            @NonNull Project project,
            @NonNull FilesProvider filesProvider,
            @NonNull ProjectOptions projectOptions,
            @NonNull DslScope dslScope,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull SdkHandler sdkHandler,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @Nullable FileCache buildCache) {
        // Attention: remember that this code runs early in the build lifecycle, project may not
        // have been fully configured yet (e.g. buildDir can still change).
        this.project = checkNotNull(project);
        this.dslScope = checkNotNull(dslScope);
        this.filesProvider = filesProvider;
        this.androidBuilder = checkNotNull(androidBuilder);
        this.sdkHandler = checkNotNull(sdkHandler);
        this.toolingRegistry = checkNotNull(toolingRegistry);
        this.optionalCompilationSteps = checkNotNull(projectOptions.getOptionalCompilationSteps());
        this.projectOptions = checkNotNull(projectOptions);
        this.buildCache = buildCache;
        this.globalArtifacts = new GlobalBuildArtifactsHolder(project, this::getBuildDir, dslScope);

        // Create empty configurations before these have been set.
        this.lintChecks = project.getConfigurations().detachedConfiguration();
    }

    public void setExtension(@NonNull AndroidConfig extension) {
        this.extension = checkNotNull(extension);
        ndkHandler = new NdkHandler(extension.getNdkVersion(), project.getRootDir());
    }

    @NonNull
    @Override
    public Project getProject() {
        return project;
    }

    @NonNull
    public FilesProvider getFilesProvider() {
        return filesProvider;
    }

    @NonNull
    public AndroidConfig getExtension() {
        return extension;
    }

    @NonNull
    public AndroidBuilder getAndroidBuilder() {
        return androidBuilder;
    }

    @NonNull
    public TargetInfo getTargetInfo() {
        // Workaround to give access to task that they need without knowing about the
        // androidbuilder which will be removed in the long term.
        return Preconditions.checkNotNull(androidBuilder.getTargetInfo(), "TargetInfo unavailable");
    }

    @NonNull
    public ProcessExecutor getProcessExecutor() {
        // Workaround to give access to task that they need without knowing about the
        // androidbuilder which will be removed in the long term.
        return androidBuilder.getProcessExecutor();
    }

    @NonNull
    public String getProjectBaseName() {
        return (String) project.property("archivesBaseName");
    }

    @NonNull
    public SdkHandler getSdkHandler() {
        return sdkHandler;
    }

    @NonNull
    public NdkHandler getNdkHandler() {
        return ndkHandler;
    }

    @NonNull
    public ToolingModelBuilderRegistry getToolingRegistry() {
        return toolingRegistry;
    }

    @NonNull
    @Override
    public File getBuildDir() {
        return project.getBuildDir();
    }

    @NonNull
    public File getIntermediatesDir() {
        return new File(getBuildDir(), FD_INTERMEDIATES);
    }

    @NonNull
    public File getGeneratedDir() {
        return new File(getBuildDir(), FD_GENERATED);
    }

    @NonNull
    public File getReportsDir() {
        return new File(getBuildDir(), FD_REPORTS);
    }

    public File getTestResultsFolder() {
        return new File(getBuildDir(), "test-results");
    }

    public File getTestReportFolder() {
        return new File(getBuildDir(), "reports/tests");
    }

    @NonNull
    public File getTmpFolder() {
        return new File(getIntermediatesDir(), "tmp");
    }

    @NonNull
    public File getOutputsDir() {
        return new File(getBuildDir(), FD_OUTPUTS);
    }

    @Override
    public boolean isActive(OptionalCompilationStep step) {
        return optionalCompilationSteps.contains(step);
    }

    @NonNull
    public String getArchivesBaseName() {
        return (String)getProject().getProperties().get("archivesBaseName");
    }

    @NonNull
    public File getJacocoAgentOutputDirectory() {
        return new File(getIntermediatesDir(), "jacoco");
    }

    @NonNull
    public File getJacocoAgent() {
        return new File(getJacocoAgentOutputDirectory(), "jacocoagent.jar");
    }

    @NonNull
    @Override
    public ProjectOptions getProjectOptions() {
        return projectOptions;
    }

    @Nullable
    @Override
    public FileCache getBuildCache() {
        return buildCache;
    }

    public void setLintChecks(@NonNull Configuration lintChecks) {
        this.lintChecks = lintChecks;
    }

    public void setLintPublish(@NonNull Configuration lintPublish) {
        this.lintPublish = lintPublish;
    }

    public void setAndroidJarConfig(@NonNull Configuration androidJarConfig) {
        this.androidJarConfig = androidJarConfig;
    }

    @NonNull
    public FileCollection getMockableJarArtifact() {
        return getMockableJarArtifact(
                getExtension().getTestOptions().getUnitTests().isReturnDefaultValues());
    }

    @NonNull
    public FileCollection getMockableJarArtifact(boolean returnDefaultValues) {
        Preconditions.checkNotNull(androidJarConfig);
        Action<AttributeContainer> attributes =
                container ->
                        container
                                .attribute(ARTIFACT_TYPE, AndroidArtifacts.TYPE_MOCKABLE_JAR)
                                .attribute(MOCKABLE_JAR_RETURN_DEFAULT_VALUES, returnDefaultValues);

        return androidJarConfig
                .getIncoming()
                .artifactView(config -> config.attributes(attributes))
                .getArtifacts()
                .getArtifactFiles();
    }

    @NonNull
    public FileCollection getPlatformAttrs() {
        Preconditions.checkNotNull(androidJarConfig);
        Action<AttributeContainer> attributes =
                container ->
                        container.attribute(ARTIFACT_TYPE, AndroidArtifacts.TYPE_PLATFORM_ATTR);

        return androidJarConfig
                .getIncoming()
                .artifactView(config -> config.attributes(attributes))
                .getArtifacts()
                .getArtifactFiles();
    }

    @NonNull
    public SyncIssueHandler getErrorHandler() {
        return (SyncIssueHandler) androidBuilder.getIssueReporter();
    }

    @NonNull
    public DslScope getDslScope() {
        return dslScope;
    }

    @NonNull
    public MessageReceiver getMessageReceiver() {
        return androidBuilder.getMessageReceiver();
    }

    /**
     * Gets the lint JAR from the lint checking configuration.
     *
     * @return the resolved lint.jar ArtifactFile from the lint checking configuration
     */
    @NonNull
    public FileCollection getLocalCustomLintChecks() {
        // Query for JAR instead of PROCESSED_JAR as we want to get the original lint.jar
        Action<AttributeContainer> attributes =
                container ->
                        container.attribute(
                                ARTIFACT_TYPE, AndroidArtifacts.ArtifactType.JAR.getType());

        return lintChecks
                .getIncoming()
                .artifactView(config -> config.attributes(attributes))
                .getArtifacts()
                .getArtifactFiles();
    }

    /**
     * Gets the lint JAR from the lint publishing configuration.
     *
     * @return the resolved lint.jar ArtifactFile from the lint publishing configuration
     */
    @NonNull
    public FileCollection getPublishedCustomLintChecks() {
        // Query for JAR instead of PROCESSED_JAR as we want to get the original lint.jar
        Action<AttributeContainer> attributes =
                container ->
                        container.attribute(
                                ARTIFACT_TYPE, AndroidArtifacts.ArtifactType.JAR.getType());

        return lintPublish
                .getIncoming()
                .artifactView(config -> config.attributes(attributes))
                .getArtifacts()
                .getArtifactFiles();
    }

    @NonNull
    public BuildArtifactsHolder getArtifacts() {
        return globalArtifacts;
    }

    public boolean hasDynamicFeatures() {
        final AndroidConfig extension = getExtension();
        if (extension instanceof BaseAppModuleExtension) {
            return !((BaseAppModuleExtension) extension).getDynamicFeatures().isEmpty();
        }
        if (extension instanceof FeatureExtension) {
            return !FeatureModelBuilder.getDynamicFeatures(this).isEmpty();
        }
        return false;
    }

    @NonNull
    public FileCollection getBootClasspath() {
        if (bootClasspath == null) {
            bootClasspath = project.files(extension.getBootClasspath());
        }

        return bootClasspath;
    }
}
