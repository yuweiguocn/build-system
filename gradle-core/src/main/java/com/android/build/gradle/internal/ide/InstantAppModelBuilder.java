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

package com.android.build.gradle.internal.ide;

import static com.android.builder.model.AndroidProject.ARTIFACT_MAIN;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_INSTANTAPP;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.BuildTypeData;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.ProductFlavorData;
import com.android.build.gradle.internal.VariantManager;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.LintOptions;
import com.android.build.gradle.internal.ide.dependencies.BuildMappingUtils;
import com.android.build.gradle.internal.incremental.BuildInfoWriterTask;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.InstantAppOutputScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.SyncOptions;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.Dependencies;
import com.android.builder.model.InstantAppProjectBuildOutput;
import com.android.builder.model.InstantAppVariantBuildOutput;
import com.android.builder.model.ModelBuilderParameter;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Variant;
import com.android.builder.model.Version;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.sdklib.SdkVersionInfo;
import com.android.utils.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder;

/** Builder for the custom instantApp model. */
public class InstantAppModelBuilder
        implements ParameterizedToolingModelBuilder<ModelBuilderParameter> {
    private int modelLevel = AndroidProject.MODEL_LEVEL_0_ORIGINAL;

    @NonNull private final AndroidConfig config;
    @NonNull private final ExtraModelInfo extraModelInfo;
    @NonNull private final VariantManager variantManager;
    private final int generation;
    private boolean modelWithFullDependency = false;
    private Set<SyncIssue> syncIssues = Sets.newLinkedHashSet();

    public InstantAppModelBuilder(
            @NonNull VariantManager variantManager,
            @NonNull AndroidConfig config,
            @NonNull ExtraModelInfo extraModelInfo,
            int generation) {
        this.config = config;
        this.extraModelInfo = extraModelInfo;
        this.variantManager = variantManager;
        this.generation = generation;
    }

    @Override
    public boolean canBuild(@NonNull String modelName) {
        // FIXME: We should not return an AndroidProject here.
        return modelName.equals(AndroidProject.class.getName())
                || modelName.equals(InstantAppProjectBuildOutput.class.getName())
                || modelName.equals(Variant.class.getName());
    }

    @NonNull
    @Override
    public Object buildAll(@NonNull String modelName, @NonNull Project project) {
        if (modelName.equals(AndroidProject.class.getName())) {
            return buildAndroidProject(project, true);
        }
        if (modelName.equals(Variant.class.getName())) {
            throw new RuntimeException(
                    "Please use parameterized tooling API to obtain Variant model.");
        }
        return buildNonParameterizedModels(modelName);
    }

    // Build parameterized model.
    @NonNull
    @Override
    public Object buildAll(
            @NonNull String modelName,
            @NonNull ModelBuilderParameter parameter,
            @NonNull Project project) {
        if (modelName.equals(AndroidProject.class.getName())) {
            return buildAndroidProject(project, parameter.getShouldBuildVariant());
        }
        if (modelName.equals(Variant.class.getName())) {
            return buildVariant(parameter.getVariantName());
        }
        return buildNonParameterizedModels(modelName);
    }

    @NonNull
    private Object buildNonParameterizedModels(@NonNull String modelName) {
        if (modelName.equals(InstantAppProjectBuildOutput.class.getName())) {
            return buildMinimalisticModel();
        }
        // should not happen based on canBuild
        throw new RuntimeException("Cannot build model " + modelName);
    }

    @NonNull
    @Override
    public Class<ModelBuilderParameter> getParameterType() {
        return ModelBuilderParameter.class;
    }

    private Object buildAndroidProject(Project project, boolean shouldBuildVariant) {
        // Cannot be injected, as the project might not be the same as the project used to construct
        // the model builder e.g. when lint explicitly builds the model.
        ProjectOptions projectOptions = new ProjectOptions(project);
        Integer modelLevelInt = SyncOptions.buildModelOnlyVersion(projectOptions);
        if (modelLevelInt != null) {
            modelLevel = modelLevelInt;
        }

        if (modelLevel < AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD) {
            throw new RuntimeException(
                    "This Gradle plugin requires a newer IDE able to request IDE model level 3. For Android Studio this means version 3.0+");
        }

        modelWithFullDependency =
                projectOptions.get(BooleanOption.IDE_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES);

        ProductFlavorContainer defaultConfig =
                ProductFlavorContainerImpl.createProductFlavorContainer(
                        variantManager.getDefaultConfig(),
                        extraModelInfo.getExtraFlavorSourceProviders(
                                variantManager.getDefaultConfig().getProductFlavor().getName()));

        syncIssues.addAll(extraModelInfo.getSyncIssueHandler().getSyncIssues());

        List<String> flavorDimensionList =
                config.getFlavorDimensionList() != null
                        ? config.getFlavorDimensionList()
                        : Lists.newArrayList();

        Collection<BuildTypeContainer> buildTypes = Lists.newArrayList();
        Collection<ProductFlavorContainer> productFlavors = Lists.newArrayList();
        Collection<Variant> variants = Lists.newArrayList();
        Collection<String> variantNames = Lists.newArrayList();

        for (BuildTypeData btData : variantManager.getBuildTypes().values()) {
            buildTypes.add(
                    BuildTypeContainerImpl.create(
                            btData,
                            extraModelInfo.getExtraBuildTypeSourceProviders(
                                    btData.getBuildType().getName())));
        }
        for (ProductFlavorData pfData : variantManager.getProductFlavors().values()) {
            productFlavors.add(
                    ProductFlavorContainerImpl.createProductFlavorContainer(
                            pfData,
                            extraModelInfo.getExtraFlavorSourceProviders(
                                    pfData.getProductFlavor().getName())));
        }

        for (VariantScope variantScope : variantManager.getVariantScopes()) {
            if (!variantScope.getVariantData().getType().isTestComponent()) {
                variantNames.add(variantScope.getFullVariantName());
                if (shouldBuildVariant) {
                    variants.add(createVariant(variantScope.getVariantData()));
                }
            }
        }

        return new DefaultAndroidProject(
                project.getName(),
                defaultConfig,
                flavorDimensionList,
                buildTypes,
                productFlavors,
                variants,
                variantNames,
                "android-" + SdkVersionInfo.HIGHEST_KNOWN_STABLE_API,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                AaptOptionsImpl.createDummy(),
                Collections.emptyList(),
                syncIssues,
                new CompileOptions(),
                new LintOptions(),
                project.getBuildDir(),
                "",
                Collections.emptyList(),
                "",
                PROJECT_TYPE_INSTANTAPP,
                Version.BUILDER_MODEL_API_VERSION,
                generation,
                false,
                ImmutableList.of());
    }

    private Object buildMinimalisticModel() {
        ImmutableList.Builder<InstantAppVariantBuildOutput> variantsOutput =
                ImmutableList.builder();

        for (VariantScope variantScope : variantManager.getVariantScopes()) {
            InstantAppOutputScope instantAppOutputScope = null;
            try {
                instantAppOutputScope = InstantAppOutputScope.load(variantScope.getApkLocation());
            } catch (IOException e) {
                Logger.getAnonymousLogger().log(
                        Level.SEVERE, "Error while loading output.json", e);
            }
            if (instantAppOutputScope != null) {
                variantsOutput.add(
                        new DefaultInstantAppVariantBuildOutput(
                                variantScope.getFullVariantName(),
                                instantAppOutputScope.getApplicationId(),
                                new BuildOutput(
                                        InternalArtifactType.INSTANTAPP_BUNDLE,
                                        ApkData.of(
                                                VariantOutput.OutputType.MAIN,
                                                ImmutableList.of(),
                                                0),
                                        instantAppOutputScope.getInstantAppBundle()),
                                new BuildOutputsSupplier(
                                                ImmutableList.of(
                                                        InternalArtifactType.APK,
                                                        InternalArtifactType.ABI_PACKAGED_SPLIT,
                                                        InternalArtifactType
                                                                .DENSITY_OR_LANGUAGE_PACKAGED_SPLIT),
                                                instantAppOutputScope.getApkDirectories())
                                        .get()));
            }
        }

        return new DefaultInstantAppProjectBuildOutput(variantsOutput.build());
    }

    @NonNull
    private VariantImpl buildVariant(@Nullable String variantName) {
        if (variantName == null) {
            throw new IllegalArgumentException("Variant name cannot be null.");
        }
        for (VariantScope variantScope : variantManager.getVariantScopes()) {
            if (!variantScope.getVariantData().getType().isTestComponent()
                    && variantScope.getFullVariantName().equals(variantName)) {
                return createVariant(variantScope.getVariantData());
            }
        }
        throw new IllegalArgumentException(
                String.format("Variant with name '%s' doesn't exist.", variantName));
    }

    @NonNull
    private VariantImpl createVariant(@NonNull BaseVariantData variantData) {
        VariantScope variantScope = variantData.getScope();
        ImmutableMap<String, String> buildMapping =
                BuildMappingUtils.computeBuildMapping(
                        variantScope.getGlobalScope().getProject().getGradle());
        GradleVariantConfiguration variantConfiguration = variantData.getVariantConfiguration();
        Pair<Dependencies, DependencyGraphs> dependencies =
                ModelBuilder.getDependencies(
                        variantScope,
                        buildMapping,
                        extraModelInfo,
                        syncIssues,
                        modelLevel,
                        modelWithFullDependency);

        File outputLocation = variantScope.getApkLocation();
        String baseName =
                variantScope.getGlobalScope().getProjectBaseName()
                        + "-"
                        + variantConfiguration.getBaseName();

        AndroidArtifact mainArtifact =
                new AndroidArtifactImpl(
                        ARTIFACT_MAIN,
                        baseName,
                        variantScope.getTaskContainer().getAssembleTask().getName(),
                        false,
                        null,
                        "unused",
                        variantScope.getTaskName("dummy"),
                        variantScope.getTaskName("dummy"),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        new File(""),
                        Collections.emptySet(),
                        new File(""),
                        dependencies.getFirst(),
                        dependencies.getSecond(),
                        Collections.emptyList(),
                        null,
                        null,
                        null,
                        variantConfiguration.getMergedBuildConfigFields(),
                        variantConfiguration.getMergedResValues(),
                        new InstantRunImpl(
                                BuildInfoWriterTask.CreationAction.getBuildInfoFile(variantScope),
                                variantConfiguration.getInstantRunSupportStatus(
                                        variantScope.getGlobalScope())),
                        (BuildOutputSupplier<Collection<EarlySyncBuildOutput>>)
                                () ->
                                        ImmutableList.of(
                                                new EarlySyncBuildOutput(
                                                        InternalArtifactType.INSTANTAPP_BUNDLE,
                                                        OutputFile.OutputType.MAIN,
                                                        ImmutableList.of(),
                                                        -1,
                                                        new File(
                                                                outputLocation,
                                                                baseName + SdkConstants.DOT_ZIP))),
                        new BuildOutputsSupplier(ImmutableList.of(), ImmutableList.of()),
                        null,
                        null,
                        null,
                        null);

        return new VariantImpl(
                variantConfiguration.getFullName(),
                variantConfiguration.getBaseName(),
                variantConfiguration.getBuildType().getName(),
                variantData
                        .getVariantConfiguration()
                        .getProductFlavors()
                        .stream()
                        .map(ProductFlavor::getName)
                        .collect(Collectors.toList()),
                new ProductFlavorImpl(variantConfiguration.getMergedFlavor()),
                mainArtifact,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                false);
        // InstantAppCompatible property is false for legacy feature module projects
    }
}
