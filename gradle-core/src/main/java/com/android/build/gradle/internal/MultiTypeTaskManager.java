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

package com.android.build.gradle.internal;

import static com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.scope.AnchorOutputType;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.core.VariantTypeImpl;
import com.android.builder.profile.Recorder;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** TaskManager for creating tasks in an Android feature project. */
public class MultiTypeTaskManager extends TaskManager {

    @NonNull Map<VariantType, TaskManager> delegates;

    public MultiTypeTaskManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig extension,
            @NonNull SdkHandler sdkHandler,
            @NonNull VariantFactory variantFactory,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder recorder) {
        super(
                globalScope,
                project,
                projectOptions,
                dataBindingBuilder,
                extension,
                sdkHandler,
                variantFactory,
                toolingRegistry,
                recorder);
        FeatureTaskManager featureTaskManager =
                new FeatureTaskManager(
                        globalScope,
                        project,
                        projectOptions,
                        dataBindingBuilder,
                        extension,
                        sdkHandler,
                        variantFactory,
                        toolingRegistry,
                        recorder);
        delegates =
                ImmutableMap.of(
                        VariantTypeImpl.FEATURE, featureTaskManager,
                        VariantTypeImpl.BASE_FEATURE, featureTaskManager,
                        VariantTypeImpl.LIBRARY,
                                new LibraryTaskManager(
                                        globalScope,
                                        project,
                                        projectOptions,
                                        dataBindingBuilder,
                                        extension,
                                        sdkHandler,
                                        variantFactory,
                                        toolingRegistry,
                                        recorder));
    }

    @Override
    public void createTasksForVariantScope(@NonNull VariantScope variantScope) {
        delegates.get(variantScope.getType()).createTasksForVariantScope(variantScope);
    }

    @NonNull
    @Override
    protected Set<? super QualifiedContent.Scope> getResMergingScopes(
            @NonNull VariantScope variantScope) {
        VariantType variantType = variantScope.getType();
        if (variantType.isTestComponent()) {
            variantType = variantScope.getTestedVariantData().getType();
        }
        return delegates.get(variantType).getResMergingScopes(variantScope);
    }

    @Override
    protected void postJavacCreation(@NonNull VariantScope scope) {
        // This task manager is used when creating the unit tests of a variant so we need to do this.
        // FIXME remove when we remove the unit test of a feature (Since the aar variant is already tested with unit tests).
        // create an anchor collection for usage inside the same module (unit tests basically)
        ConfigurableFileCollection files =
                scope.getGlobalScope()
                        .getProject()
                        .files(
                                scope.getArtifacts().getArtifactFiles(JAVAC),
                                scope.getVariantData().getAllPreJavacGeneratedBytecode(),
                                scope.getVariantData().getAllPostJavacGeneratedBytecode());
        scope.getArtifacts().appendArtifact(AnchorOutputType.ALL_CLASSES, files);
    }
}
