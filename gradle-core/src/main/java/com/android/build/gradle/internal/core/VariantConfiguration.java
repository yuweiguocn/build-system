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

package com.android.build.gradle.internal.core;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.DefaultApiVersion;
import com.android.builder.core.DefaultManifestParser;
import com.android.builder.core.ManifestAttributeSupplier;
import com.android.builder.core.MergedFlavor;
import com.android.builder.core.VariantAttributesProvider;
import com.android.builder.core.VariantType;
import com.android.builder.dexing.DexingType;
import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.internal.ClassFieldImpl;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.BuildType;
import com.android.builder.model.ClassField;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.SourceProvider;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.AssetMerger;
import com.android.ide.common.resources.AssetSet;
import com.android.ide.common.resources.ResourceMerger;
import com.android.ide.common.resources.ResourceSet;
import com.android.sdklib.AndroidVersion;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A Variant configuration.
 *
 * Variants are made from the combination of:
 *
 * - a build type (base interface BuildType), and its associated sources.
 * - a default configuration (base interface ProductFlavor), and its associated sources.
 * - a optional list of product flavors (base interface ProductFlavor) and their associated sources.
 * - dependencies (both jar and aar).
 *
 * @param <T> the type used for the Build Type.
 * @param <D> the type used for the default config
 * @param <F> the type used for the product flavors.
 */
public class VariantConfiguration<T extends BuildType, D extends ProductFlavor, F extends ProductFlavor> {

    /**
     * Full, unique name of the variant in camel case, including BuildType and Flavors (and Test)
     */
    private String mFullName;
    /**
     * Flavor Name of the variant, including all flavors in camel case (starting with a lower
     * case).
     */
    private String mFlavorName;
    /**
     * Full, unique name of the variant, including BuildType, flavors and test, dash separated.
     * (similar to full name but with dashes)
     */
    private String mBaseName;
    /**
     * Unique directory name (can include multiple folders) for the variant, based on build type,
     * flavor and test.
     * This always uses forward slashes ('/') as separator on all platform.
     *
     */
    private String mDirName;
    private List<String> mDirSegments;

    @NonNull
    private final D mDefaultConfig;
    @NonNull
    private final SourceProvider mDefaultSourceProvider;

    @NonNull
    private final T mBuildType;
    /** SourceProvider for the BuildType. Can be null */
    @Nullable
    private final SourceProvider mBuildTypeSourceProvider;

    private final List<String> mFlavorDimensionNames = Lists.newArrayList();
    private final List<F> mFlavors = Lists.newArrayList();
    private final List<SourceProvider> mFlavorSourceProviders = Lists.newArrayList();

    /** Variant specific source provider, may be null */
    @Nullable
    private SourceProvider mVariantSourceProvider;

    /** MultiFlavors specific source provider, may be null */
    @Nullable
    private SourceProvider mMultiFlavorSourceProvider;

    @NonNull
    private final VariantType mType;

    /**
     * Optional tested config in case this variant is used for testing another variant.
     *
     * @see VariantType#isTestComponent()
     */
    private final VariantConfiguration<T, D, F> mTestedConfig;

    @NonNull
    private ProductFlavor mMergedFlavor;

    /**
     * Variant-specific build Config fields.
     */
    private final Map<String, ClassField> mBuildConfigFields = Maps.newTreeMap();

    /**
     * Variant-specific res values.
     */
    private final Map<String, ClassField> mResValues = Maps.newTreeMap();

    /**
     * Signing Override to be used instead of any signing config provided by Build Type or
     * Product Flavors.
     */
    private final SigningConfig mSigningConfigOverride;

    /**
     * For reading the attributes from the main manifest file in the default source set, combining
     * the results with the current flavor.
     */
    @NonNull private final VariantAttributesProvider mVariantAttributesProvider;

    /** For recording sync issues. */
    @NonNull private final EvalIssueReporter mIssueReporter;


    /**
     * Creates the configuration with the base source sets for a given {@link VariantType}. Meant
     * for non-testing variants.
     *
     * @param defaultConfig the default configuration. Required.
     * @param defaultSourceProvider the default source provider. Required
     * @param buildType the build type for this variant. Required.
     * @param buildTypeSourceProvider the source provider for the build type.
     * @param type the type of the project.
     * @param signingConfigOverride an optional Signing override to be used for signing.
     */
    public VariantConfiguration(
            @NonNull D defaultConfig,
            @NonNull SourceProvider defaultSourceProvider,
            @Nullable ManifestAttributeSupplier mainManifestAttributeSupplier,
            @NonNull T buildType,
            @Nullable SourceProvider buildTypeSourceProvider,
            @NonNull VariantType type,
            @Nullable SigningConfig signingConfigOverride,
            @NonNull EvalIssueReporter issueReporter,
            @NonNull BooleanSupplier isInExecutionPhase) {
        this(
                defaultConfig,
                defaultSourceProvider,
                mainManifestAttributeSupplier,
                buildType,
                buildTypeSourceProvider,
                type,
                null /*testedConfig*/,
                signingConfigOverride,
                issueReporter,
                isInExecutionPhase);
    }

    /**
     * Creates the configuration with the base source sets, and an optional tested variant.
     *
     * @param defaultConfig the default configuration. Required.
     * @param defaultSourceProvider the default source provider. Required
     * @param buildType the build type for this variant. Required.
     * @param buildTypeSourceProvider the source provider for the build type.
     * @param type the type of the project.
     * @param testedConfig the reference to the tested project. Required if type is
     *     Type.ANDROID_TEST
     * @param signingConfigOverride an optional Signing override to be used for signing.
     */
    public VariantConfiguration(
            @NonNull D defaultConfig,
            @NonNull SourceProvider defaultSourceProvider,
            @Nullable ManifestAttributeSupplier mainManifestAttributeSupplier,
            @NonNull T buildType,
            @Nullable SourceProvider buildTypeSourceProvider,
            @NonNull VariantType type,
            @Nullable VariantConfiguration<T, D, F> testedConfig,
            @Nullable SigningConfig signingConfigOverride,
            @NonNull EvalIssueReporter issueReporter,
            @NonNull BooleanSupplier isInExecutionPhase) {
        checkNotNull(defaultConfig);
        checkNotNull(defaultSourceProvider);
        checkNotNull(buildType);
        checkNotNull(type);
        checkArgument(
                !type.isTestComponent() || testedConfig != null,
                "You have to specify the tested variant for this variant type.");
        checkArgument(
                type.isTestComponent() || testedConfig == null,
                "This variant type doesn't need a tested variant.");

        mDefaultConfig = checkNotNull(defaultConfig);
        mDefaultSourceProvider = checkNotNull(defaultSourceProvider);
        mBuildType = checkNotNull(buildType);
        mBuildTypeSourceProvider = buildTypeSourceProvider;
        mType = checkNotNull(type);
        mTestedConfig = testedConfig;
        mSigningConfigOverride = signingConfigOverride;
        mIssueReporter = issueReporter;
        mMergedFlavor = MergedFlavor.clone(mDefaultConfig, mIssueReporter);
        ManifestAttributeSupplier manifestParser =
                mainManifestAttributeSupplier != null
                        ? mainManifestAttributeSupplier
                        : new DefaultManifestParser(
                                mDefaultSourceProvider.getManifestFile(),
                                isInExecutionPhase,
                                issueReporter);
        mVariantAttributesProvider =
                new VariantAttributesProvider(
                        mMergedFlavor,
                        mBuildType,
                        mType.isTestComponent(),
                        manifestParser,
                        mDefaultSourceProvider.getManifestFile(),
                        getFullName());
    }

    /**
     * Returns the full, unique name of the variant in camel case (starting with a lower case),
     * including BuildType, Flavors and Test (if applicable).
     *
     * @return the name of the variant
     */
    @NonNull
    public String getFullName() {
        if (mFullName == null) {
            mFullName =
                    computeRegularVariantName(
                            getFlavorName(),
                            mBuildType,
                            mType,
                            mTestedConfig == null ? null : mTestedConfig.getType());
        }

        return mFullName;
    }

    /**
     * Returns the full, unique name of the variant in camel case (starting with a lower case),
     * including BuildType, Flavors and Test (if applicable).
     *
     * <p>This is to be used for the normal variant name. In case of Feature plugin, the library
     * side will be called the same as for library plugins, while the feature side will add
     * 'feature' to the name.
     *
     * <p>Using {@link #computeHybridVariantName()} will always put 'aar' in the library variant
     * names (for feature plugins), making it different from the library plugin.
     *
     * @param flavorName the flavor name, as computed by {@link #computeFlavorName(List)}
     * @param buildType the build type
     * @param type the variant type
     * @return the name of the variant
     */
    @NonNull
    public static <B extends BuildType> String computeRegularVariantName(
            @NonNull String flavorName,
            @NonNull B buildType,
            @NonNull VariantType type,
            @Nullable VariantType testedType) {
        StringBuilder sb = new StringBuilder();

        if (!flavorName.isEmpty()) {
            sb.append(flavorName);
            StringHelper.appendCapitalized(sb, buildType.getName());
        } else {
            sb.append(buildType.getName());
        }

        if (type.isHybrid()) {
            //noinspection ConstantConditions
            StringHelper.appendCapitalized(sb, type.getHybridName());
        }

        if (type.isTestComponent()) {
            if (testedType != null && testedType.isHybrid()) {
                sb.append("Feature");
            }
            sb.append(type.getSuffix());
        }
        return sb.toString();
    }

    /**
     * Returns the full, unique name of the variant in camel case (starting with a lower case),
     * including BuildType, Flavors and Test (if applicable).
     *
     * <p>This is the full hybrid version even for variants that are not hybrid (ie LIBRARY are not
     * considered hybrid but they share with FEATURE, so this will return a different name than
     * {@link #computeRegularVariantName(String, BuildType, VariantType, VariantType)}.)
     *
     * <p>This should only be used to differentiate the assemble task name.
     *
     * @return the name of the variant
     */
    @NonNull
    public String computeHybridVariantName() {
        StringBuilder sb = new StringBuilder();

        String flavorName = getFlavorName();
        if (!flavorName.isEmpty()) {
            sb.append(flavorName);
            StringHelper.appendCapitalized(sb, mBuildType.getName());
        } else {
            sb.append(mBuildType.getName());
        }

        if (mType.isTestComponent()) {
            VariantType testedType = mTestedConfig == null ? null : mTestedConfig.getType();
            if (testedType != null && testedType.isHybrid()) {
                //noinspection ConstantConditions
                StringHelper.appendCapitalized(sb, testedType.getHybridName());
            }
            sb.append(mType.getSuffix());
        } else {
            //noinspection ConstantConditions
            StringHelper.appendCapitalized(sb, mType.getHybridName());
        }
        return sb.toString();
    }


    /**
     * Returns a full name that includes the given splits name.
     * @param splitName the split name
     * @return a unique name made up of the variant and split names.
     */
    @NonNull
    public String computeFullNameWithSplits(@NonNull String splitName) {
        StringBuilder sb = new StringBuilder();
        String flavorName = getFlavorName();
        if (!flavorName.isEmpty()) {
            sb.append(flavorName);
            StringHelper.appendCapitalized(sb, splitName);
        } else {
            sb.append(splitName);
        }

        StringHelper.appendCapitalized(sb, mBuildType.getName());

        if (mType.isTestComponent()) {
            sb.append(mType.getSuffix());
        }

        return sb.toString();
    }

    /**
     * Returns the flavor name of the variant, including all flavors in camel case (starting
     * with a lower case). If the variant has no flavor, then an empty string is returned.
     *
     * @return the flavor name or an empty string.
     */
    @NonNull
    public String getFlavorName() {
        if (mFlavorName == null) {
            mFlavorName = computeFlavorName(mFlavors);
        }

        return mFlavorName;
    }

    /**
     * Returns the flavor name for a variant composed of the given flavors, including all
     * flavor names in camel case (starting with a lower case).
     *
     * If the flavor list is empty, then an empty string is returned.
     *
     * @param flavors the list of flavors
     * @return the flavor name or an empty string.
     */
    public static <F extends ProductFlavor> String computeFlavorName(@NonNull List<F> flavors) {
        if (flavors.isEmpty()) {
            return "";
        } else {
            return StringHelper.combineAsCamelCase(flavors, F::getName);
        }
    }

    /**
     * Returns the full, unique name of the variant, including BuildType, flavors and test,
     * dash separated. (similar to full name but with dashes)
     *
     * @return the name of the variant
     */
    @NonNull
    public String getBaseName() {
        if (mBaseName == null) {
            StringBuilder sb = new StringBuilder();

            if (!mFlavors.isEmpty()) {
                for (ProductFlavor pf : mFlavors) {
                    sb.append(pf.getName()).append('-');
                }
            }

            sb.append(mBuildType.getName());

            if (mType.isTestComponent()) {
                sb.append('-').append(mType.getPrefix());
            }

            mBaseName = sb.toString();
        }

        return mBaseName;
    }

    /**
     * Returns a base name that includes the given splits name.
     * @param splitName the split name
     * @return a unique name made up of the variant and split names.
     */
    @NonNull
    public String computeBaseNameWithSplits(@NonNull String splitName) {
        StringBuilder sb = new StringBuilder();

        if (!mFlavors.isEmpty()) {
            for (ProductFlavor pf : mFlavors) {
                sb.append(pf.getName()).append('-');
            }
        }

        sb.append(splitName).append('-');
        sb.append(mBuildType.getName());

        if (mType.isTestComponent()) {
            sb.append('-').append(mType.getPrefix());
        }

        return sb.toString();
    }

    /**
     * Returns a unique directory name (can include multiple folders) for the variant,
     * based on build type, flavor and test.
     *
     * <p>This always uses forward slashes ('/') as separator on all platform.
     *
     * @return the directory name for the variant
     */
    @NonNull
    public String getDirName() {
        if (mDirName == null) {
            StringBuilder sb = new StringBuilder(mFlavors.size() * 20 + 100);
            if (mType.isHybrid()) {
                sb.append("feature/");
            }

            if (mType.isTestComponent()) {
                sb.append(mType.getPrefix()).append("/");
            }

            if (!mFlavors.isEmpty()) {
                StringHelper.combineAsCamelCase(sb, mFlavors, F::getName);
                sb.append('/').append(mBuildType.getName());

            } else {
                sb.append(mBuildType.getName());
            }

            mDirName = sb.toString();

        }

        return mDirName;
    }

    /**
     * Returns a unique directory name (can include multiple folders) for the variant,
     * based on build type, flavor and test.
     *
     * @return the directory name for the variant
     */
    @NonNull
    public Collection<String> getDirectorySegments() {
        if (mDirSegments == null) {
            ImmutableList.Builder<String> builder = ImmutableList.builder();

            if (mType.isHybrid()) {
                builder.add("feature");
            }

            if (mType.isTestComponent()) {
                builder.add(mType.getPrefix());
            }

            if (!mFlavors.isEmpty()) {
                StringBuilder sb = new StringBuilder(mFlavors.size() * 10);
                for (F flavor : mFlavors) {
                    StringHelper.appendCamelCase(sb, flavor.getName());
                }
                builder.add(sb.toString());

                builder.add(mBuildType.getName());

            } else {
                builder.add(mBuildType.getName());
            }

            mDirSegments = builder.build();
        }

        return mDirSegments;
    }
    /**
     * Returns a unique directory name (can include multiple folders) for the variant,
     * based on build type, flavor and test, and splits.
     *
     * <p>This always uses forward slashes ('/') as separator on all platform.
     *
     * @return the directory name for the variant
     */
    @NonNull
    public String computeDirNameWithSplits(@NonNull String... splitNames) {
        StringBuilder sb = new StringBuilder();

        if (mType.isTestComponent()) {
            sb.append(mType.getPrefix()).append("/");
        }

        if (!mFlavors.isEmpty()) {
            for (F flavor : mFlavors) {
                sb.append(flavor.getName());
            }

            sb.append('/');
        }

        for (String splitName : splitNames) {
            if (splitName != null) {
                sb.append(splitName).append('/');
            }
        }

        sb.append(mBuildType.getName());

        return sb.toString();
    }

    /**
     * Return the names of the applied flavors.
     *
     * The list contains the dimension names as well.
     *
     * @return the list, possibly empty if there are no flavors.
     */
    @NonNull
    public List<String> getFlavorNamesWithDimensionNames() {
        if (mFlavors.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> names;
        int count = mFlavors.size();

        if (count > 1) {
            names = Lists.newArrayListWithCapacity(count * 2);

            for (int i = 0 ; i < count ; i++) {
                names.add(mFlavors.get(i).getName());
                names.add(mFlavorDimensionNames.get(i));
            }

        } else {
            names = Collections.singletonList(mFlavors.get(0).getName());
        }

        return names;
    }


    /**
     * Add a new configured ProductFlavor.
     *
     * If multiple flavors are added, the priority follows the order they are added when it
     * comes to resolving Android resources overlays (ie earlier added flavors supersedes
     * latter added ones).
     *
     * @param productFlavor the configured product flavor
     * @param sourceProvider the source provider for the product flavor
     * @param dimensionName the name of the dimension associated with the flavor
     *
     * @return the config object
     */
    @NonNull
    public VariantConfiguration addProductFlavor(
            @NonNull F productFlavor,
            @NonNull SourceProvider sourceProvider,
            @NonNull String dimensionName) {

        mFlavors.add(productFlavor);
        mFlavorSourceProviders.add(sourceProvider);
        mFlavorDimensionNames.add(dimensionName);
        mMergedFlavor = MergedFlavor.mergeFlavors(mDefaultConfig, mFlavors, mIssueReporter);
        mVariantAttributesProvider.setMergedFlavor(mMergedFlavor);
        // reset computed names to null so it will be recomputed.
        mFlavorName = null;
        mFullName = null;
        mVariantAttributesProvider.setFullName(getFullName());

        return this;
    }

    /**
     * Sets the variant-specific source provider.
     * @param sourceProvider the source provider for the product flavor
     *
     * @return the config object
     */
    public VariantConfiguration setVariantSourceProvider(@Nullable SourceProvider sourceProvider) {
        mVariantSourceProvider = sourceProvider;
        return this;
    }

    /**
     * Sets the variant-specific source provider.
     * @param sourceProvider the source provider for the product flavor
     *
     * @return the config object
     */
    public VariantConfiguration setMultiFlavorSourceProvider(@Nullable SourceProvider sourceProvider) {
        mMultiFlavorSourceProvider = sourceProvider;
        return this;
    }

    /**
     * Returns the variant specific source provider
     * @return the source provider or null if none has been provided.
     */
    @Nullable
    public SourceProvider getVariantSourceProvider() {
        return mVariantSourceProvider;
    }

    @Nullable
    public SourceProvider getMultiFlavorSourceProvider() {
        return mMultiFlavorSourceProvider;
    }

    @NonNull
    public D getDefaultConfig() {
        return mDefaultConfig;
    }

    @NonNull
    public SourceProvider getDefaultSourceSet() {
        return mDefaultSourceProvider;
    }

    @NonNull
    public ProductFlavor getMergedFlavor() {
        return mMergedFlavor;
    }

    @NonNull
    public T getBuildType() {
        return mBuildType;
    }

    /**
     * The SourceProvider for the BuildType. Can be null.
     */
    @Nullable
    public SourceProvider getBuildTypeSourceSet() {
        return mBuildTypeSourceProvider;
    }

    public boolean hasFlavors() {
        return !mFlavors.isEmpty();
    }

    /**
     * Returns the product flavors. Items earlier in the list override later items.
     */
    @NonNull
    public List<F> getProductFlavors() {
        return mFlavors;
    }

    /**
     * Returns the list of SourceProviders for the flavors.
     *
     * The list is ordered from higher priority to lower priority.
     *
     * @return the list of Source Providers for the flavors. Never null.
     */
    @NonNull
    public List<SourceProvider> getFlavorSourceProviders() {
        return mFlavorSourceProviders;
    }

    @NonNull
    public VariantType getType() {
        return mType;
    }

    @Nullable
    public VariantConfiguration getTestedConfig() {
        return mTestedConfig;
    }

    private String getTestedPackage() {
        return mTestedConfig != null ? mTestedConfig.getApplicationId() : "";
    }

    /**
     * Returns the original application ID before any overrides from flavors. If the variant is a
     * test variant, then the application ID is the one coming from the configuration of the tested
     * variant, and this call is similar to {@link #getApplicationId()}
     *
     * @return the original application ID
     */
    @NonNull
    public String getOriginalApplicationId() {
        return mVariantAttributesProvider.getOriginalApplicationId(getTestedPackage());
    }

    /**
     * Returns the application ID for this variant. This could be coming from the manifest or
     * could be overridden through the product flavors and/or the build type.
     * @return the application ID
     */
    @NonNull
    public String getApplicationId() {
        return mVariantAttributesProvider.getApplicationId(getTestedPackage());
    }

    @NonNull
    public String getTestApplicationId(){
        return mVariantAttributesProvider.getTestApplicationId(getTestedPackage());
    }

    @Nullable
    public String getTestedApplicationId() {
        if (mType.isTestComponent()) {
            checkState(mTestedConfig != null);
            if (mTestedConfig.mType.isAar()) {
                return getApplicationId();
            } else {
                return mTestedConfig.getApplicationId();
            }
        }

        return null;
    }

    /**
     * Returns the application id override value coming from the Product Flavor and/or the
     * Build Type. If the package/id is not overridden then this returns null.
     *
     * @return the id override or null
     */
    @Nullable
    public String getIdOverride() {
        return mVariantAttributesProvider.getIdOverride();
    }

    /**
     * Returns the version name for this variant. This could be coming from the manifest or
     * could be overridden through the product flavors, and can have a suffix specified by
     * the build type.
     *
     * @return the version name
     */
    @Nullable
    public String getVersionName() {
        return mVariantAttributesProvider.getVersionName();
    }

    /**
     * Returns the version code for this variant. This could be coming from the manifest or
     * could be overridden through the product flavors, and can have a suffix specified by
     * the build type.
     *
     * @return the version code or -1 if there was non defined.
     */
    public int getVersionCode() {
        return mVariantAttributesProvider.getVersionCode();
    }

    public Supplier<String> getVersionNameSerializableSupplier() {
        return mVariantAttributesProvider.getVersionNameSerializableSupplier();
    }

    public IntSupplier getVersionCodeSerializableSupplier() {
        return mVariantAttributesProvider.getVersionCodeSerializableSupplier();
    }

    private static final String DEFAULT_TEST_RUNNER = "android.test.InstrumentationTestRunner";
    private static final String MULTIDEX_TEST_RUNNER = "com.android.test.runner.MultiDexTestRunner";
    private static final Boolean DEFAULT_HANDLE_PROFILING = false;
    private static final Boolean DEFAULT_FUNCTIONAL_TEST = false;

    /**
     * Returns the instrumentationRunner to use to test this variant, or if the
     * variant is a test, the one to use to test the tested variant.
     * @return the instrumentation test runner name
     */
    @NonNull
    public String getInstrumentationRunner() {
        VariantConfiguration config = this;
        if (mType.isTestComponent()) {
            config = getTestedConfig();
            checkState(config != null);
        }
        String runner = config.mVariantAttributesProvider.getInstrumentationRunner();
        if (runner != null) {
            return runner;
        }

        if (isLegacyMultiDexMode()) {
            return MULTIDEX_TEST_RUNNER;
        }

        return DEFAULT_TEST_RUNNER;
    }

    /**
     * Returns the instrumentationRunner arguments to use to test this variant, or if the
     * variant is a test, the ones to use to test the tested variant
     */
    @NonNull
    public Map<String, String> getInstrumentationRunnerArguments() {
        VariantConfiguration config = this;
        if (mType.isTestComponent()) {
            config = getTestedConfig();
            checkState(config != null);
        }
        return config.mMergedFlavor.getTestInstrumentationRunnerArguments();
    }

    /**
     * Returns handleProfiling value to use to test this variant, or if the
     * variant is a test, the one to use to test the tested variant.
     *
     * @return the handleProfiling value
     */
    @NonNull
    public Boolean getHandleProfiling() {
        VariantConfiguration config = this;
        if (mType.isTestComponent()) {
            config = getTestedConfig();
            checkState(config != null);
        }
        Boolean handleProfiling = config.mVariantAttributesProvider.getHandleProfiling();
        return handleProfiling != null ? handleProfiling : DEFAULT_HANDLE_PROFILING;
    }

    /**
     * Returns functionalTest value to use to test this variant, or if the
     * variant is a test, the one to use to test the tested variant.
     *
     * @return the functionalTest value
     */
    @NonNull
    public Boolean getFunctionalTest() {
        VariantConfiguration config = this;
        if (mType.isTestComponent()) {
            config = getTestedConfig();
            checkState(config != null);
        }
        Boolean functionalTest = config.mVariantAttributesProvider.getFunctionalTest();

        return functionalTest != null ? functionalTest : DEFAULT_FUNCTIONAL_TEST;
    }

    /** Gets the test label for this variant */
    @Nullable
    public String getTestLabel(){
        return mVariantAttributesProvider.getTestLabel();
    }

    /** Reads the package name from the manifest. This is unmodified by the build type. */
    @NonNull
    public String getPackageFromManifest() {
        return mVariantAttributesProvider.getPackageName();
    }

    /**
     * Return the minSdkVersion for this variant.
     *
     * <p>This uses both the value from the manifest (if present), and the override coming from the
     * flavor(s) (if present).
     *
     * @return the minSdkVersion
     */
    @NonNull
    public AndroidVersion getMinSdkVersion() {
        if (mTestedConfig != null) {
            return mTestedConfig.getMinSdkVersion();
        }

        ApiVersion minSdkVersion = mMergedFlavor.getMinSdkVersion();
        if (minSdkVersion == null) {
            // default to 1 for minSdkVersion.
            minSdkVersion = DefaultApiVersion.create(new Integer(1));
        }

        return new AndroidVersion(minSdkVersion.getApiLevel(), minSdkVersion.getCodename());
    }

    /** Returns the minSdkVersion as integer. */
    public int getMinSdkVersionValue() {
        return getMinSdkVersion().getFeatureLevel();
    }

    /**
     * Return the targetSdkVersion for this variant.
     *
     * <p>This uses both the value from the manifest (if present), and the override coming from the
     * flavor(s) (if present).
     *
     * @return the targetSdkVersion
     */
    @NonNull
    public ApiVersion getTargetSdkVersion() {
        if (mTestedConfig != null) {
            return mTestedConfig.getTargetSdkVersion();
        }
        ApiVersion targetSdkVersion = mMergedFlavor.getTargetSdkVersion();
        if (targetSdkVersion == null) {
            // default to -1 if not in build.gradle file.
            targetSdkVersion = DefaultApiVersion.create(new Integer(-1));
        }

        return targetSdkVersion;
    }

    @Nullable
    public File getMainManifest() {
        File defaultManifest = mDefaultSourceProvider.getManifestFile();

        // this could not exist in a test project.
        if (defaultManifest.isFile()) {
            return defaultManifest;
        }

        return null;
    }

    /**
     * Returns a list of sorted SourceProvider in ascending order of importance. This means that
     * items toward the end of the list take precedence over those toward the start of the list.
     *
     * @return a list of source provider
     */
    @NonNull
    public List<SourceProvider> getSortedSourceProviders() {
        List<SourceProvider> providers =
                Lists.newArrayListWithExpectedSize(mFlavorSourceProviders.size() + 4);

        // first the default source provider
        providers.add(mDefaultSourceProvider);

        // the list of flavor must be reversed to use the right overlay order.
        for (int n = mFlavorSourceProviders.size() - 1; n >= 0 ; n--) {
            providers.add(mFlavorSourceProviders.get(n));
        }

        // multiflavor specific overrides flavor
        if (mMultiFlavorSourceProvider != null) {
            providers.add(mMultiFlavorSourceProvider);
        }

        // build type overrides flavors
        if (mBuildTypeSourceProvider != null) {
            providers.add(mBuildTypeSourceProvider);
        }

        // variant specific overrides all
        if (mVariantSourceProvider != null) {
            providers.add(mVariantSourceProvider);
        }

        return providers;
    }

    @NonNull
    public List<File> getManifestOverlays() {
        List<File> inputs = Lists.newArrayList();

        if (mVariantSourceProvider != null) {
            File variantLocation = mVariantSourceProvider.getManifestFile();
            if (variantLocation.isFile()) {
                inputs.add(variantLocation);
            }
        }

        if (mBuildTypeSourceProvider != null) {
            File typeLocation = mBuildTypeSourceProvider.getManifestFile();
            if (typeLocation.isFile()) {
                inputs.add(typeLocation);
            }
        }

        if (mMultiFlavorSourceProvider != null) {
            File variantLocation = mMultiFlavorSourceProvider.getManifestFile();
            if (variantLocation.isFile()) {
                inputs.add(variantLocation);
            }
        }

        for (SourceProvider sourceProvider : mFlavorSourceProviders) {
            File f = sourceProvider.getManifestFile();
            if (f.isFile()) {
                inputs.add(f);
            }
        }

        return inputs;
    }

    /**
     * Returns a list of sorted navigation files, with files toward the start of the list taking
     * precedence over those toward the end of the list.
     *
     * @return a list of navigation files
     */
    @NonNull
    public List<File> getNavigationFiles() {

        ImmutableList.Builder<File> builder = ImmutableList.builder();

        List<SourceProvider> sourceProviders = getSortedSourceProviders();
        // iterate over sourceProviders in reverse order to match order of getManifestOverlays()
        for (int n = sourceProviders.size() - 1; n >= 0; n--) {
            Collection<File> resDirs = sourceProviders.get(n).getResDirectories();
            for (File resDir : resDirs) {
                if (resDir == null) {
                    continue;
                }
                File navigationDir = new File(resDir.getPath(), SdkConstants.FD_RES_NAVIGATION);
                File[] navigationFiles = navigationDir.listFiles();
                if (navigationFiles == null) {
                    continue;
                }
                for (File navigationFile : navigationFiles) {
                    if (navigationFile != null && navigationFile.isFile()) {
                        builder.add(navigationFile);
                    }
                }
            }
        }

        return builder.build();
    }

    @NonNull
    public Set<File> getSourceFiles(@NonNull Function<SourceProvider, Collection<File>> f) {
        Collection<SourceProvider> providers = getSortedSourceProviders();
        Collection<Collection<File>> fileLists =
                Lists.newArrayListWithExpectedSize(providers.size());
        providers.forEach(p -> fileLists.add(f.apply(p)));

        int numFiles = 0;
        for (Collection<File> files : fileLists) {
            numFiles += files.size();
        }

        Set<File> fileSet = Sets.newHashSetWithExpectedSize(numFiles);
        fileLists.forEach(fileSet::addAll);
        return fileSet;
    }

    /**
     * Returns the dynamic list of {@link ResourceSet} for the source folders only.
     *
     * <p>The list is ordered in ascending order of importance, meaning the first set is meant to be
     * overridden by the 2nd one and so on. This is meant to facilitate usage of the list in a
     * {@link ResourceMerger}.
     *
     * @return a list ResourceSet.
     */
    @NonNull
    public List<ResourceSet> getResourceSets(boolean validateEnabled) {
        List<ResourceSet> resourceSets = Lists.newArrayList();
        Collection<File> mainResDirs = mDefaultSourceProvider.getResDirectories();

        // the main + generated res folders are in the same ResourceSet
        ResourceSet resourceSet =
                new ResourceSet(
                        BuilderConstants.MAIN, ResourceNamespace.RES_AUTO, null, validateEnabled);
        resourceSet.addSources(mainResDirs);
        resourceSets.add(resourceSet);

        // the list of flavor must be reversed to use the right overlay order.
        for (int n = mFlavorSourceProviders.size() - 1; n >= 0 ; n--) {
            SourceProvider sourceProvider = mFlavorSourceProviders.get(n);

            Collection<File> flavorResDirs = sourceProvider.getResDirectories();
            // we need the same of the flavor config, but it's in a different list.
            // This is fine as both list are parallel collections with the same number of items.
            resourceSet =
                    new ResourceSet(
                            sourceProvider.getName(),
                            ResourceNamespace.RES_AUTO,
                            null,
                            validateEnabled);
            resourceSet.addSources(flavorResDirs);
            resourceSets.add(resourceSet);
        }

        // multiflavor specific overrides flavor
        if (mMultiFlavorSourceProvider != null) {
            Collection<File> variantResDirs = mMultiFlavorSourceProvider.getResDirectories();
            resourceSet =
                    new ResourceSet(
                            getFlavorName(), ResourceNamespace.RES_AUTO, null, validateEnabled);
            resourceSet.addSources(variantResDirs);
            resourceSets.add(resourceSet);
        }

        // build type overrides the flavors
        if (mBuildTypeSourceProvider != null) {
            Collection<File> typeResDirs = mBuildTypeSourceProvider.getResDirectories();
            resourceSet =
                    new ResourceSet(
                            mBuildType.getName(),
                            ResourceNamespace.RES_AUTO,
                            null,
                            validateEnabled);
            resourceSet.addSources(typeResDirs);
            resourceSets.add(resourceSet);
        }

        // variant specific overrides all
        if (mVariantSourceProvider != null) {
            Collection<File> variantResDirs = mVariantSourceProvider.getResDirectories();
            resourceSet =
                    new ResourceSet(
                            getFullName(), ResourceNamespace.RES_AUTO, null, validateEnabled);
            resourceSet.addSources(variantResDirs);
            resourceSets.add(resourceSet);
        }

        return resourceSets;
    }

    /**
     * Returns the dynamic list of {@link AssetSet} based on the configuration, for a particular
     * property of {@link SourceProvider}.
     *
     * <p>The list is ordered in ascending order of importance, meaning the first set is meant to be
     * overridden by the 2nd one and so on. This is meant to facilitate usage of the list in a
     * {@link AssetMerger}.
     *
     * @param function the function that return a collection of file based on the SourceProvider.
     *     this is usually a method referenceo on SourceProvider
     * @return a list ResourceSet.
     */
    @NonNull
    public List<AssetSet> getSourceFilesAsAssetSets(
            @NonNull Function<SourceProvider, Collection<File>> function) {
        List<AssetSet> assetSets = Lists.newArrayList();

        Collection<File> mainResDirs = function.apply(mDefaultSourceProvider);

        // the main + generated asset folders are in the same AssetSet
        AssetSet assetSet = new AssetSet(BuilderConstants.MAIN);
        assetSet.addSources(mainResDirs);
        assetSets.add(assetSet);

        // the list of flavor must be reversed to use the right overlay order.
        for (int n = mFlavorSourceProviders.size() - 1; n >= 0 ; n--) {
            SourceProvider sourceProvider = mFlavorSourceProviders.get(n);

            Collection<File> flavorResDirs = function.apply(sourceProvider);
            // we need the same of the flavor config, but it's in a different list.
            // This is fine as both list are parallel collections with the same number of items.
            assetSet = new AssetSet(mFlavors.get(n).getName());
            assetSet.addSources(flavorResDirs);
            assetSets.add(assetSet);
        }

        // multiflavor specific overrides flavor
        if (mMultiFlavorSourceProvider != null) {
            Collection<File> variantResDirs = function.apply(mMultiFlavorSourceProvider);
            assetSet = new AssetSet(getFlavorName());
            assetSet.addSources(variantResDirs);
            assetSets.add(assetSet);
        }

        // build type overrides flavors
        if (mBuildTypeSourceProvider != null) {
            Collection<File> typeResDirs = function.apply(mBuildTypeSourceProvider);
            assetSet = new AssetSet(mBuildType.getName());
            assetSet.addSources(typeResDirs);
            assetSets.add(assetSet);
        }

        // variant specific overrides all
        if (mVariantSourceProvider != null) {
            Collection<File> variantResDirs = function.apply(mVariantSourceProvider);
            assetSet = new AssetSet(getFullName());
            assetSet.addSources(variantResDirs);
            assetSets.add(assetSet);
        }

        return assetSets;
    }

    public int getRenderscriptTarget() {
        ProductFlavor mergedFlavor = getMergedFlavor();

        int targetApi = mergedFlavor.getRenderscriptTargetApi() != null ?
                mergedFlavor.getRenderscriptTargetApi() : -1;
        int minSdk = getMinSdkVersionValue();

        return targetApi > minSdk ? targetApi : minSdk;
    }

    /**
     * Returns all the renderscript source folder from the main config, the flavors and the build
     * type.
     *
     * @return a list of folders.
     */
    @NonNull
    public Collection<File> getRenderscriptSourceList() {
        return getSourceFiles(SourceProvider::getRenderscriptDirectories);
    }

    @NonNull
    public Collection<File> getAidlSourceList() {
        return getSourceFiles(SourceProvider::getAidlDirectories);
    }

    @NonNull
    public Collection<File> getJniSourceList() {
        return getSourceFiles(SourceProvider::getCDirectories);
    }

    /**
     * Adds a variant-specific BuildConfig field.
     * @param type the type of the field
     * @param name the name of the field
     * @param value the value of the field
     */
    public void addBuildConfigField(@NonNull String type, @NonNull String name, @NonNull String value) {
        ClassField classField = new ClassFieldImpl(type, name, value);
        mBuildConfigFields.put(name, classField);
    }

    /**
     * Adds a variant-specific res value.
     * @param type the type of the field
     * @param name the name of the field
     * @param value the value of the field
     */
    public void addResValue(@NonNull String type, @NonNull String name, @NonNull String value) {
        ClassField classField = new ClassFieldImpl(type, name, value);
        mResValues.put(name, classField);
    }

    /**
     * Returns a list of items for the BuildConfig class.
     *
     * Items can be either fields (instance of {@link com.android.builder.model.ClassField})
     * or comments (instance of String).
     *
     * @return a list of items.
     */
    @NonNull
    public List<Object> getBuildConfigItems() {
        List<Object> fullList = Lists.newArrayList();

        // keep track of the names already added. This is because we show where the items
        // come from so we cannot just put everything a map and let the new ones override the
        // old ones.
        Set<String> usedFieldNames = Sets.newHashSet();

        Collection<ClassField> list = mBuildConfigFields.values();
        if (!list.isEmpty()) {
            fullList.add("Fields from the variant");
            fillFieldList(fullList, usedFieldNames, list);
        }

        list = mBuildType.getBuildConfigFields().values();
        if (!list.isEmpty()) {
            fullList.add("Fields from build type: " + mBuildType.getName());
            fillFieldList(fullList, usedFieldNames, list);
        }

        for (F flavor : mFlavors) {
            list = flavor.getBuildConfigFields().values();
            if (!list.isEmpty()) {
                fullList.add("Fields from product flavor: " + flavor.getName());
                fillFieldList(fullList, usedFieldNames, list);
            }
        }

        list = mDefaultConfig.getBuildConfigFields().values();
        if (!list.isEmpty()) {
            fullList.add("Fields from default config.");
            fillFieldList(fullList, usedFieldNames, list);
        }

        return fullList;
    }

    /**
     * Return the merged build config fields for the variant.
     *
     * This is made of of the variant-specific fields overlaid on top of the build type ones,
     * the flavors ones, and the default config ones.
     *
     * @return a map of merged fields
     */
    @NonNull
    public Map<String, ClassField> getMergedBuildConfigFields() {
        Map<String, ClassField> mergedMap = Maps.newHashMap();

        // start from the lowest priority and just add it all. Higher priority fields
        // will replace lower priority ones.

        mergedMap.putAll(mDefaultConfig.getBuildConfigFields());
        for (int i = mFlavors.size() - 1; i >= 0 ; i--) {
            mergedMap.putAll(mFlavors.get(i).getBuildConfigFields());
        }

        mergedMap.putAll(mBuildType.getBuildConfigFields());
        mergedMap.putAll(mBuildConfigFields);

        return mergedMap;
    }

    /**
     * Return the merged res values for the variant.
     *
     * This is made of of the variant-specific fields overlaid on top of the build type ones,
     * the flavors ones, and the default config ones.
     *
     * @return a map of merged fields
     */
    @NonNull
    public Map<String, ClassField> getMergedResValues() {
        Map<String, ClassField> mergedMap = Maps.newHashMap();

        // start from the lowest priority and just add it all. Higher priority fields
        // will replace lower priority ones.

        mergedMap.putAll(mDefaultConfig.getResValues());
        for (int i = mFlavors.size() - 1; i >= 0 ; i--) {
            mergedMap.putAll(mFlavors.get(i).getResValues());
        }

        mergedMap.putAll(mBuildType.getResValues());
        mergedMap.putAll(mResValues);

        return mergedMap;
    }

    /**
     * Fills a list of Object from a given list of ClassField only if the name isn't in a set.
     * Each new item added adds its name to the list.
     * @param outList the out list
     * @param usedFieldNames the list of field names already in the list
     * @param list the list to copy items from
     */
    private static void fillFieldList(
            @NonNull List<Object> outList,
            @NonNull Set<String> usedFieldNames,
            @NonNull Collection<ClassField> list) {
        for (ClassField f : list) {
            String name = f.getName();
            if (!usedFieldNames.contains(name)) {
                usedFieldNames.add(f.getName());
                outList.add(f);
            }
        }
    }

    /**
     * Returns a list of generated resource values.
     *
     * Items can be either fields (instance of {@link com.android.builder.model.ClassField})
     * or comments (instance of String).
     *
     * @return a list of items.
     */
    @NonNull
    public List<Object> getResValues() {
        List<Object> fullList = Lists.newArrayList();

        // keep track of the names already added. This is because we show where the items
        // come from so we cannot just put everything a map and let the new ones override the
        // old ones.
        Set<String> usedFieldNames = Sets.newHashSet();

        Collection<ClassField> list = mResValues.values();
        if (!list.isEmpty()) {
            fullList.add("Values from the variant");
            fillFieldList(fullList, usedFieldNames, list);
        }

        list = mBuildType.getResValues().values();
        if (!list.isEmpty()) {
            fullList.add("Values from build type: " + mBuildType.getName());
            fillFieldList(fullList, usedFieldNames, list);
        }

        for (F flavor : mFlavors) {
            list = flavor.getResValues().values();
            if (!list.isEmpty()) {
                fullList.add("Values from product flavor: " + flavor.getName());
                fillFieldList(fullList, usedFieldNames, list);
            }
        }

        list = mDefaultConfig.getResValues().values();
        if (!list.isEmpty()) {
            fullList.add("Values from default config.");
            fillFieldList(fullList, usedFieldNames, list);
        }

        return fullList;
    }

    @Nullable
    public SigningConfig getSigningConfig() {
        if (mType.isFeatureSplit()) {
            return null;
        }
        if (mSigningConfigOverride != null) {
            return mSigningConfigOverride;
        }

        SigningConfig signingConfig = mBuildType.getSigningConfig();
        if (signingConfig != null) {
            return signingConfig;
        }
        return mMergedFlavor.getSigningConfig();
    }

    public boolean isSigningReady() {
        SigningConfig signingConfig = getSigningConfig();
        return signingConfig != null && signingConfig.isSigningReady();
    }

    public boolean isTestCoverageEnabled() {
        return mBuildType.isTestCoverageEnabled();
    }

    /**
     * Returns the merged manifest placeholders. All product flavors are merged first, then build
     * type specific placeholders are added and potentially overrides product flavors values.
     * @return the merged manifest placeholders for a build variant.
     */
    @NonNull
    public Map<String, Object> getManifestPlaceholders() {
        Map<String, Object> mergedFlavorsPlaceholders = mMergedFlavor.getManifestPlaceholders();
        // so far, blindly override the build type placeholders
        mergedFlavorsPlaceholders.putAll(mBuildType.getManifestPlaceholders());
        return mergedFlavorsPlaceholders;
    }

    public boolean isMultiDexEnabled() {
        Boolean value = mBuildType.getMultiDexEnabled();
        if (value != null) {
            return value;
        }

        value = mMergedFlavor.getMultiDexEnabled();
        if (value != null) {
            return value;
        }

        // Only require specific multidex opt-in for legacy multidex.
        return getMinSdkVersion().getFeatureLevel() >= 21;
    }

    public File getMultiDexKeepFile() {
        File value = mBuildType.getMultiDexKeepFile();
        if (value != null) {
            return value;
        }

        value = mMergedFlavor.getMultiDexKeepFile();
        if (value != null) {
            return value;
        }

        return null;
    }

    public File getMultiDexKeepProguard() {
        File value = mBuildType.getMultiDexKeepProguard();
        if (value != null) {
            return value;
        }

        value = mMergedFlavor.getMultiDexKeepProguard();
        if (value != null) {
            return value;
        }

        return null;
    }

    public boolean isLegacyMultiDexMode() {
       return getDexingType() == DexingType.LEGACY_MULTIDEX;
    }

    @NonNull
    public DexingType getDexingType() {
        if (getType().isDynamicFeature()) {
            if (getBuildType().getMultiDexEnabled() != null
                    || getMergedFlavor().getMultiDexEnabled() != null) {
                getIssueReporter()
                        .reportWarning(
                                EvalIssueReporter.Type.GENERIC,
                                "Native multidex is always used for dynamic features. Please "
                                        + "remove 'multiDexEnabled true|false' from your "
                                        + "build.gradle file.");
            }

            // dynamic features can always be build in native multidex mode
            return DexingType.NATIVE_MULTIDEX;
        } else if (isMultiDexEnabled()) {
            return getMinSdkVersion().getFeatureLevel() < 21
                    ? DexingType.LEGACY_MULTIDEX
                    : DexingType.NATIVE_MULTIDEX;
        } else {
            return DexingType.MONO_DEX;
        }
    }

    /**
     * Returns the renderscript support mode.
     */
    public boolean getRenderscriptSupportModeEnabled() {
        Boolean value = mMergedFlavor.getRenderscriptSupportModeEnabled();
        if (value != null) {
            return value;
        }

        // default is false.
        return false;
    }

    /**
     * Returns the renderscript BLAS support mode.
     */
    public boolean getRenderscriptSupportModeBlasEnabled() {
        Boolean value = mMergedFlavor.getRenderscriptSupportModeBlasEnabled();
        if (value != null) {
            return value;
        }

        // default is false.
        return false;
    }

    /**
     * Returns the renderscript NDK mode.
     */
    public boolean getRenderscriptNdkModeEnabled() {
        Boolean value = mMergedFlavor.getRenderscriptNdkModeEnabled();
        if (value != null) {
            return value;
        }

        // default is false.
        return false;
    }

    /**
     * Returns true if the variant output is a bundle.
     */
    public boolean isBundled() {
        return mType.isAar();
    }

    @NonNull
    protected EvalIssueReporter getIssueReporter() {
        return mIssueReporter;
    }
}
