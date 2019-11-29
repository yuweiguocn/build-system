/*
* Copyright (C) 2017 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
 copy of the License at
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
import com.android.build.gradle.internal.InstantAppTaskManager;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.VariantManager;
import com.android.build.gradle.internal.api.dsl.extensions.BaseExtension2;
import com.android.build.gradle.internal.dependency.SourceSetManager;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.ide.InstantAppModelBuilder;
import com.android.build.gradle.internal.ide.ModelBuilder;
import com.android.build.gradle.internal.plugin.TypedPluginDelegate;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.variant.InstantAppVariantFactory;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.model.AndroidProject;
import com.android.builder.profile.Recorder;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import javax.inject.Inject;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** Gradle plugin class for 'instantapp' projects. */
public class InstantAppPlugin extends BasePlugin<BaseExtension2> {
    @Inject
    public InstantAppPlugin(ToolingModelBuilderRegistry registry) {
        super(registry);
    }

    @Override
    protected int getProjectType() {
        return AndroidProject.PROJECT_TYPE_INSTANTAPP;
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
                        InstantAppExtension.class,
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
    @Override
    protected GradleBuildProject.PluginType getAnalyticsPluginType() {
        return GradleBuildProject.PluginType.INSTANTAPP;
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
        return new InstantAppTaskManager(
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
    protected void registerModels(
            @NonNull ToolingModelBuilderRegistry registry,
            @NonNull GlobalScope globalScope,
            @NonNull VariantManager variantManager,
            @NonNull AndroidConfig config,
            @NonNull ExtraModelInfo extraModelInfo) {
        InstantAppModelBuilder instantAppModelBuilder =
                new InstantAppModelBuilder(
                        variantManager, config, extraModelInfo, AndroidProject.GENERATION_ORIGINAL);
        registry.register(instantAppModelBuilder);
    }

    @Override
    protected void pluginSpecificApply(@NonNull Project project) {
        // do nothing
    }

    @NonNull
    @Override
    protected InstantAppVariantFactory createVariantFactory(
            @NonNull GlobalScope globalScope,
            @NonNull AndroidConfig androidConfig) {
        return new InstantAppVariantFactory(globalScope, androidConfig);
    }

    @Override
    protected TypedPluginDelegate<BaseExtension2> getTypedDelegate() {
        return null;
    }
}
