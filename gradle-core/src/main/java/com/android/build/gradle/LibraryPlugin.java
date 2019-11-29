/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.LibraryTaskManager;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.api.dsl.extensions.LibraryExtensionImpl;
import com.android.build.gradle.internal.dependency.SourceSetManager;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.plugin.LibPluginDelegate;
import com.android.build.gradle.internal.plugin.TypedPluginDelegate;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.variant.LibraryVariantFactory;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.model.AndroidProject;
import com.android.builder.profile.Recorder;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import javax.inject.Inject;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** Gradle plugin class for 'library' projects. */
public class LibraryPlugin extends BasePlugin<LibraryExtensionImpl> {

    @Inject
    public LibraryPlugin(ToolingModelBuilderRegistry registry) {
        super(registry);
    }

    @NonNull
    @Override
    protected BaseExtension createExtension(
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull GlobalScope globalScope,
            @NonNull SdkHandler sdkHandler,
            @NonNull NamedDomainObjectContainer<BuildType> buildTypeContainer,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavorContainer,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigContainer,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
            @NonNull SourceSetManager sourceSetManager,
            @NonNull ExtraModelInfo extraModelInfo) {
        return project.getExtensions()
                .create(
                        "android",
                        getExtensionClass(),
                        project,
                        projectOptions,
                        globalScope,
                        sdkHandler,
                        buildTypeContainer,
                        productFlavorContainer,
                        signingConfigContainer,
                        buildOutputs,
                        sourceSetManager,
                        extraModelInfo);
    }

    @NonNull
    protected Class<? extends BaseExtension> getExtensionClass() {
        return LibraryExtension.class;
    }

    @NonNull
    @Override
    protected GradleBuildProject.PluginType getAnalyticsPluginType() {
        return GradleBuildProject.PluginType.LIBRARY;
    }

    @NonNull
    @Override
    protected VariantFactory createVariantFactory(
            @NonNull GlobalScope globalScope,
            @NonNull AndroidConfig androidConfig) {
        return new LibraryVariantFactory(globalScope, androidConfig);
    }

    @Override
    protected int getProjectType() {
        return AndroidProject.PROJECT_TYPE_LIBRARY;
    }

    @NonNull
    @Override
    protected TaskManager createTaskManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig androidConfig,
            @NonNull SdkHandler sdkHandler,
            @NonNull VariantFactory variantFactory,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder recorder) {
        return new LibraryTaskManager(
                globalScope,
                project,
                projectOptions,
                dataBindingBuilder,
                androidConfig,
                sdkHandler,
                variantFactory,
                toolingRegistry,
                recorder);
    }

    @Override
    protected void pluginSpecificApply(@NonNull Project project) {
        // Default assemble task for the default-published artifact.
        // This is needed for the prepare task on the consuming project.
        project.getTasks().create("assembleDefault");
    }

    @Override
    protected TypedPluginDelegate<LibraryExtensionImpl> getTypedDelegate() {
        return new LibPluginDelegate();
    }

    @Override
    protected boolean isPackagePublished() {
        return true;
    }
}
