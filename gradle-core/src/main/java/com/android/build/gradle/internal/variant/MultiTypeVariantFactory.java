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

package com.android.build.gradle.internal.variant;

import static com.android.builder.core.BuilderConstants.DEBUG;
import static com.android.builder.core.BuilderConstants.RELEASE;

import com.android.annotations.NonNull;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.BuildTypeData;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.VariantModel;
import com.android.build.gradle.internal.api.BaseVariantImpl;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.builder.core.VariantType;
import com.android.builder.core.VariantTypeImpl;
import com.android.builder.errors.EvalIssueException;
import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.errors.EvalIssueReporter.Type;
import com.android.builder.profile.Recorder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.Map;
import org.gradle.api.NamedDomainObjectContainer;

public class MultiTypeVariantFactory extends BaseVariantFactory {
    @NonNull private final Map<VariantType, BaseVariantFactory> delegates;

    public MultiTypeVariantFactory(
            @NonNull GlobalScope globalScope,
            @NonNull AndroidConfig extension) {
        super(globalScope, extension);
        delegates =
                ImmutableMap.of(
                        VariantTypeImpl.BASE_FEATURE,
                        new FeatureVariantFactory(
                                globalScope, extension, VariantTypeImpl.BASE_FEATURE),
                        VariantTypeImpl.FEATURE,
                        new FeatureVariantFactory(globalScope, extension, VariantTypeImpl.FEATURE),
                        VariantTypeImpl.LIBRARY,
                        new LibraryVariantFactory(globalScope, extension));
    }

    @NonNull
    @Override
    public BaseVariantData createVariantData(
            @NonNull GradleVariantConfiguration variantConfiguration,
            @NonNull TaskManager taskManager,
            @NonNull Recorder recorder) {
        return delegates
                .get(variantConfiguration.getType())
                .createVariantData(variantConfiguration, taskManager, recorder);
    }

    @NonNull
    @Override
    public Class<? extends BaseVariantImpl> getVariantImplementationClass(
            @NonNull BaseVariantData variantData) {
        return delegates.get(variantData.getType()).getVariantImplementationClass(variantData);
    }

    @NonNull
    @Override
    public Collection<VariantType> getVariantConfigurationTypes() {
        if (extension.getBaseFeature()) {
            return ImmutableList.of(VariantTypeImpl.BASE_FEATURE, VariantTypeImpl.LIBRARY);
        }
        return ImmutableList.of(VariantTypeImpl.FEATURE, VariantTypeImpl.LIBRARY);
    }

    @Override
    public boolean hasTestScope() {
        return true;
    }

    @Override
    public void validateModel(@NonNull VariantModel model) {
        for (BaseVariantFactory variantFactory : delegates.values()) {
            variantFactory.validateModel(model);
        }

        if (getVariantConfigurationTypes().stream().noneMatch(VariantType::isFeatureSplit)) {
            return;
        }

        EvalIssueReporter issueReporter = globalScope.getAndroidBuilder().getIssueReporter();

        for (BuildTypeData buildType : model.getBuildTypes().values()) {
            if (buildType.getBuildType().isMinifyEnabled()) {
                issueReporter.reportError(
                        Type.GENERIC,
                        new EvalIssueException(
                                "Feature modules cannot set minifyEnabled to true. "
                                        + "minifyEnabled is set to true in build type '"
                                        + buildType.getBuildType().getName()
                                        + "'.\nTo enable minification for a feature module, "
                                        + "set minifyEnabled to true in the base module."));
            }
        }
    }

    @Override
    public void createDefaultComponents(
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs) {
        // must create signing config first so that build type 'debug' can be initialized
        // with the debug signing config.
        signingConfigs.create(DEBUG);
        buildTypes.create(DEBUG);
        buildTypes.create(RELEASE);
    }
}
