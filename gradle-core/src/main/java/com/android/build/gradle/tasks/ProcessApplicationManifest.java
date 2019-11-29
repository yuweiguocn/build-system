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
package com.android.build.gradle.tasks;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_APPLICATION_ID_DECLARATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.MANIFEST;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.METADATA_BASE_MODULE_DECLARATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.METADATA_FEATURE_MANIFEST;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.METADATA_VALUES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.api.artifact.BuildableArtifactUtil;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.core.VariantConfiguration;
import com.android.build.gradle.internal.dependency.ArtifactCollectionWithExtraArtifact.ExtraComponentIdentifier;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.ModuleMetadata;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.core.VariantType;
import com.android.builder.dexing.DexingType;
import com.android.builder.model.ApiVersion;
import com.android.manifmerger.ManifestMerger2;
import com.android.manifmerger.ManifestMerger2.Invoker.Feature;
import com.android.manifmerger.ManifestProvider;
import com.android.manifmerger.MergingReport;
import com.android.manifmerger.XmlDocument;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier;

/** A task that processes the manifest */
@CacheableTask
public class ProcessApplicationManifest extends ManifestProcessorTask {

    private Supplier<String> minSdkVersion;
    private Supplier<String> targetSdkVersion;
    private Supplier<Integer> maxSdkVersion;
    private VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor>
            variantConfiguration;
    private ArtifactCollection manifests;
    private BuildableArtifact autoNamespacedManifests;
    private ArtifactCollection featureManifests;
    private FileCollection microApkManifest;
    private BuildableArtifact compatibleScreensManifest;
    private FileCollection packageManifest;
    private BuildableArtifact apkList;
    private Supplier<EnumSet<Feature>> optionalFeatures;
    private OutputScope outputScope;

    // supplier to read the file above to get the feature name for the current project.
    @Nullable private Supplier<String> featureNameSupplier = null;

    @Inject
    public ProcessApplicationManifest(ObjectFactory objectFactory) {
        super(objectFactory);
    }

    @Override
    protected void doFullTaskAction() throws IOException {
        // read the output of the compatible screen manifest.
        BuildElements compatibleScreenManifests =
                ExistingBuildElements.from(
                        InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST, compatibleScreensManifest);

        ModuleMetadata moduleMetadata = null;
        if (packageManifest != null && !packageManifest.isEmpty()) {
            moduleMetadata = ModuleMetadata.load(packageManifest.getSingleFile());
            boolean isDebuggable = optionalFeatures.get().contains(Feature.DEBUGGABLE);
            if (moduleMetadata.getDebuggable() != isDebuggable) {
                String moduleType =
                        variantConfiguration.getType().isHybrid()
                                ? "Instant App Feature"
                                : "Dynamic Feature";
                String errorMessage =
                        String.format(
                                "%1$s '%2$s' (build type '%3$s') %4$s debuggable,\n"
                                        + "and the corresponding build type in the base "
                                        + "application %5$s debuggable.\n"
                                        + "Recommendation: \n"
                                        + "   in  %6$s\n"
                                        + "   set android.buildTypes.%3$s.debuggable = %7$s",
                                moduleType,
                                getProject().getPath(),
                                variantConfiguration.getBuildType().getName(),
                                isDebuggable ? "is" : "is not",
                                moduleMetadata.getDebuggable() ? "is" : "is not",
                                getProject().getBuildFile(),
                                moduleMetadata.getDebuggable() ? "true" : "false");
                throw new InvalidUserDataException(errorMessage);
            }
        }


        @Nullable BuildOutput compatibleScreenManifestForSplit;

        ImmutableList.Builder<BuildOutput> mergedManifestOutputs = ImmutableList.builder();
        ImmutableList.Builder<BuildOutput> irMergedManifestOutputs = ImmutableList.builder();
        ImmutableList.Builder<BuildOutput> metadataFeatureMergedManifestOutputs =
                ImmutableList.builder();
        ImmutableList.Builder<BuildOutput> bundleManifestOutputs = ImmutableList.builder();
        ImmutableList.Builder<BuildOutput> instantAppManifestOutputs = ImmutableList.builder();

        // FIX ME : multi threading.
        // TODO : LOAD the APK_LIST FILE .....
        for (ApkData apkData : outputScope.getApkDatas()) {

            compatibleScreenManifestForSplit = compatibleScreenManifests.element(apkData);
            File manifestOutputFile =
                    new File(
                            getManifestOutputDirectory().get().getAsFile(),
                            FileUtils.join(
                                    apkData.getDirName(), SdkConstants.ANDROID_MANIFEST_XML));

            File instantRunManifestOutputFile =
                    getInstantRunManifestOutputDirectory().isPresent()
                            ? FileUtils.join(
                                    getInstantRunManifestOutputDirectory().get().getAsFile(),
                                    apkData.getDirName(),
                                    SdkConstants.ANDROID_MANIFEST_XML)
                            : null;

            File metadataFeatureManifestOutputFile =
                    FileUtils.join(
                            getMetadataFeatureManifestOutputDirectory(),
                            apkData.getDirName(),
                            SdkConstants.ANDROID_MANIFEST_XML);

            File bundleManifestOutputFile =
                    FileUtils.join(
                            getBundleManifestOutputDirectory(),
                            apkData.getDirName(),
                            SdkConstants.ANDROID_MANIFEST_XML);

            File instantAppManifestOutputFile =
                    getInstantAppManifestOutputDirectory().isPresent()
                            ? FileUtils.join(
                                    getInstantAppManifestOutputDirectory().get().getAsFile(),
                                    apkData.getDirName(),
                                    SdkConstants.ANDROID_MANIFEST_XML)
                            : null;

            MergingReport mergingReport =
                    getBuilder()
                            .mergeManifestsForApplication(
                                    getMainManifest(),
                                    getManifestOverlays(),
                                    computeFullProviderList(compatibleScreenManifestForSplit),
                                    getNavigationFiles(),
                                    getFeatureName(),
                                    moduleMetadata == null
                                            ? getPackageOverride()
                                            : moduleMetadata.getApplicationId(),
                                    moduleMetadata == null
                                            ? apkData.getVersionCode()
                                            : Integer.parseInt(moduleMetadata.getVersionCode()),
                                    moduleMetadata == null
                                            ? apkData.getVersionName()
                                            : moduleMetadata.getVersionName(),
                                    getMinSdkVersion(),
                                    getTargetSdkVersion(),
                                    getMaxSdkVersion(),
                                    manifestOutputFile.getAbsolutePath(),
                                    // no aapt friendly merged manifest file necessary for applications.
                                    null /* aaptFriendlyManifestOutputFile */,
                                    instantRunManifestOutputFile != null
                                            ? instantRunManifestOutputFile.getAbsolutePath()
                                            : null,
                                    metadataFeatureManifestOutputFile.getAbsolutePath(),
                                    bundleManifestOutputFile.getAbsolutePath(),
                                    instantAppManifestOutputFile != null
                                            ? instantAppManifestOutputFile.getAbsolutePath()
                                            : null,
                                    ManifestMerger2.MergeType.APPLICATION,
                                    variantConfiguration.getManifestPlaceholders(),
                                    getOptionalFeatures(),
                                    getReportFile());

            XmlDocument mergedXmlDocument =
                    mergingReport.getMergedXmlDocument(MergingReport.MergedManifestKind.MERGED);

            ImmutableMap<String, String> properties =
                    mergedXmlDocument != null
                            ? ImmutableMap.of(
                                    "packageId",
                                    mergedXmlDocument.getPackageName(),
                                    "split",
                                    mergedXmlDocument.getSplitName(),
                                    SdkConstants.ATTR_MIN_SDK_VERSION,
                                    mergedXmlDocument.getMinSdkVersion())
                            : ImmutableMap.of();

            mergedManifestOutputs.add(
                    new BuildOutput(
                            InternalArtifactType.MERGED_MANIFESTS,
                            apkData,
                            manifestOutputFile,
                            properties));
            if (instantRunManifestOutputFile != null) {
                irMergedManifestOutputs.add(
                        new BuildOutput(
                                InternalArtifactType.INSTANT_RUN_MERGED_MANIFESTS,
                                apkData,
                                instantRunManifestOutputFile,
                                properties));
            }

            metadataFeatureMergedManifestOutputs.add(
                    new BuildOutput(
                            InternalArtifactType.METADATA_FEATURE_MANIFEST,
                            apkData,
                            metadataFeatureManifestOutputFile));
            bundleManifestOutputs.add(
                    new BuildOutput(
                            InternalArtifactType.BUNDLE_MANIFEST,
                            apkData,
                            bundleManifestOutputFile,
                            properties));
            if (instantAppManifestOutputFile != null) {
                instantAppManifestOutputs.add(
                        new BuildOutput(
                                InternalArtifactType.INSTANT_APP_MANIFEST,
                                apkData,
                                instantAppManifestOutputFile,
                                properties));
            }
        }
        new BuildElements(mergedManifestOutputs.build())
                .save(getManifestOutputDirectory().get().getAsFile());
        if (getInstantRunManifestOutputDirectory().isPresent()) {
            new BuildElements(irMergedManifestOutputs.build())
                    .save(getInstantRunManifestOutputDirectory().get().getAsFile());
        }
        new BuildElements(metadataFeatureMergedManifestOutputs.build())
                .save(getMetadataFeatureManifestOutputDirectory());
        new BuildElements(bundleManifestOutputs.build()).save(getBundleManifestOutputDirectory());

        if (getInstantAppManifestOutputDirectory().isPresent()) {
            new BuildElements(instantAppManifestOutputs.build())
                    .save(getInstantAppManifestOutputDirectory().get().getAsFile());
        }
    }

    @Nullable
    @Override
    @Internal
    public File getAaptFriendlyManifestOutputFile() {
        return null;
    }

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public File getMainManifest() {
        return variantConfiguration.getMainManifest();
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public List<File> getManifestOverlays() {
        return variantConfiguration.getManifestOverlays();
    }

    @Input
    @Optional
    public String getPackageOverride() {
        return variantConfiguration.getIdOverride();
    }

    @Input
    public List<Integer> getVersionCodes() {
        return outputScope
                .getApkDatas()
                .stream()
                .map(ApkData::getVersionCode)
                .collect(Collectors.toList());
    }

    @Input
    @Optional
    public List<String> getVersionNames() {
        return outputScope
                .getApkDatas()
                .stream()
                .map(ApkData::getVersionName)
                .collect(Collectors.toList());
    }

    /**
     * Returns a serialized version of our map of key value pairs for placeholder substitution.
     *
     * This serialized form is only used by gradle to compare past and present tasks to determine
     * whether a task need to be re-run or not.
     */
    @Input
    @Optional
    public String getManifestPlaceholders() {
        return serializeMap(variantConfiguration.getManifestPlaceholders());
    }

    private List<ManifestProvider> computeProviders(
            @NonNull Set<ResolvedArtifactResult> artifacts,
            @NonNull InternalArtifactType artifactType) {
        List<ManifestProvider> providers = Lists.newArrayListWithCapacity(artifacts.size());
        for (ResolvedArtifactResult artifact : artifacts) {
            File directory = artifact.getFile();
            BuildElements splitOutputs = ExistingBuildElements.from(artifactType, directory);
            if (splitOutputs.isEmpty()) {
                throw new GradleException("Could not load manifest from " + directory);
            }
            providers.add(
                    new CreationAction.ManifestProviderImpl(
                            splitOutputs.iterator().next().getOutputFile(),
                            getArtifactName(artifact)));
        }

        return providers;
    }

    /**
     * Compute the final list of providers based on the manifest file collection and the other
     * providers.
     *
     * @return the list of providers.
     */
    private List<ManifestProvider> computeFullProviderList(
            @Nullable BuildOutput compatibleScreenManifestForSplit) {
        final Set<ResolvedArtifactResult> artifacts = manifests.getArtifacts();
        List<ManifestProvider> providers = Lists.newArrayListWithCapacity(artifacts.size() + 2);

        for (ResolvedArtifactResult artifact : artifacts) {
            providers.add(
                    new CreationAction.ManifestProviderImpl(
                            artifact.getFile(), getArtifactName(artifact)));
        }

        if (microApkManifest != null) {
            // this is now always present if embedding is enabled, but it doesn't mean
            // anything got embedded so the file may not run (the file path exists and is
            // returned by the FC but the file doesn't exist.
            File microManifest = microApkManifest.getSingleFile();
            if (microManifest.isFile()) {
                providers.add(
                        new CreationAction.ManifestProviderImpl(
                                microManifest, "Wear App sub-manifest"));
            }
        }

        if (compatibleScreenManifestForSplit != null) {
            providers.add(
                    new CreationAction.ManifestProviderImpl(
                            compatibleScreenManifestForSplit.getOutputFile(),
                            "Compatible-Screens sub-manifest"));

        }

        if (autoNamespacedManifests != null) {
            // We do not have resolved artifact results here, we need to find the artifact name
            // based on the file name.
            File directory = BuildableArtifactUtil.singleFile(autoNamespacedManifests);
            Preconditions.checkState(
                    directory.isDirectory(),
                    "Auto namespaced manifests should be a directory.",
                    directory);
            for (File autoNamespacedManifest : Preconditions.checkNotNull(directory.listFiles())) {
                providers.add(
                        new CreationAction.ManifestProviderImpl(
                                autoNamespacedManifest,
                                getNameFromAutoNamespacedManifest(autoNamespacedManifest)));
            }
        }

        if (featureManifests != null) {
            providers.addAll(
                    computeProviders(
                            featureManifests.getArtifacts(),
                            InternalArtifactType.METADATA_FEATURE_MANIFEST));
        }

        return providers;
    }

    @NonNull
    @Internal
    private static String getNameFromAutoNamespacedManifest(@NonNull File manifest) {
        final String manifestSuffix = "_AndroidManifest.xml";
        String fileName = manifest.getName();
        // Get the ID based on the file name generated by the [AutoNamespaceDependenciesTask]. It is
        // the sanitized name, but should be enough.
        if (!fileName.endsWith(manifestSuffix)) {
            throw new RuntimeException(
                    "Invalid auto-namespaced manifest file: " + manifest.getAbsolutePath());
        }
        return fileName.substring(0, fileName.length() - manifestSuffix.length());
    }

    // TODO put somewhere else?
    @NonNull
    @Internal
    public static String getArtifactName(@NonNull ResolvedArtifactResult artifact) {
        ComponentIdentifier id = artifact.getId().getComponentIdentifier();
        if (id instanceof ProjectComponentIdentifier) {
            return ((ProjectComponentIdentifier) id).getProjectPath();

        } else if (id instanceof ModuleComponentIdentifier) {
            ModuleComponentIdentifier mID = (ModuleComponentIdentifier) id;
            return mID.getGroup() + ":" + mID.getModule() + ":" + mID.getVersion();

        } else if (id instanceof OpaqueComponentArtifactIdentifier) {
            // this is the case for local jars.
            // FIXME: use a non internal class.
            return id.getDisplayName();
        } else if (id instanceof ExtraComponentIdentifier) {
            return id.getDisplayName();
        } else {
            throw new RuntimeException("Unsupported type of ComponentIdentifier");
        }
    }

    @Input
    @Optional
    public String getMinSdkVersion() {
        return minSdkVersion.get();
    }

    @Input
    @Optional
    public String getTargetSdkVersion() {
        return targetSdkVersion.get();
    }

    @Input
    @Optional
    public Integer getMaxSdkVersion() {
        return maxSdkVersion.get();
    }

    /** Not an input, see {@link #getOptionalFeaturesString()}. */
    @Internal
    public EnumSet<Feature> getOptionalFeatures() {
        return optionalFeatures.get();
    }

    /** Synthetic input for {@link #getOptionalFeatures()} */
    @Input
    public List<String> getOptionalFeaturesString() {
        return getOptionalFeatures().stream().map(Enum::toString).collect(Collectors.toList());
    }

    @Internal
    public VariantConfiguration getVariantConfiguration() {
        return variantConfiguration;
    }

    public void setVariantConfiguration(
            VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor> variantConfiguration) {
        this.variantConfiguration = variantConfiguration;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getManifests() {
        return manifests.getArtifactFiles();
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public List<File> getNavigationFiles() {
        return variantConfiguration.getNavigationFiles();
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getFeatureManifests() {
        if (featureManifests == null) {
            return null;
        }
        return featureManifests.getArtifactFiles();
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getMicroApkManifest() {
        return microApkManifest;
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public BuildableArtifact getCompatibleScreensManifest() {
        return compatibleScreensManifest;
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getPackageManifest() {
        return packageManifest;
    }

    @Input
    @Optional
    public String getFeatureName() {
        return featureNameSupplier != null ? featureNameSupplier.get() : null;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public BuildableArtifact getApkList() {
        return apkList;
    }

    public static class CreationAction
            extends AnnotationProcessingTaskCreationAction<ProcessApplicationManifest> {

        protected final VariantScope variantScope;
        protected final boolean isAdvancedProfilingOn;
        private File reportFile;
        private File featureManifestOutputDirectory;
        private File bundleManifestOutputDirectory;

        public CreationAction(
                @NonNull VariantScope scope,
                // TODO : remove this variable and find ways to access it from scope.
                boolean isAdvancedProfilingOn) {
            super(
                    scope,
                    scope.getTaskName("process", "Manifest"),
                    ProcessApplicationManifest.class);
            this.variantScope = scope;
            this.isAdvancedProfilingOn = isAdvancedProfilingOn;
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);

            reportFile =
                    FileUtils.join(
                            variantScope.getGlobalScope().getOutputsDir(),
                            "logs",
                            "manifest-merger-"
                                    + variantScope.getVariantConfiguration().getBaseName()
                                    + "-report.txt");

            // set outputs.
            variantScope
                    .getArtifacts()
                    .appendArtifact(
                            InternalArtifactType.MANIFEST_MERGE_REPORT,
                            ImmutableList.of(reportFile),
                            taskName);

            VariantType variantType = variantScope.getType();
            Preconditions.checkState(!variantType.isTestComponent());
            BuildArtifactsHolder artifacts = variantScope.getArtifacts();

            artifacts.republish(
                    InternalArtifactType.MERGED_MANIFESTS, InternalArtifactType.MANIFEST_METADATA);

            featureManifestOutputDirectory =
                    artifacts.appendArtifact(
                            InternalArtifactType.METADATA_FEATURE_MANIFEST,
                            taskName,
                            "metadata-feature");

            bundleManifestOutputDirectory =
                    artifacts.appendArtifact(
                            InternalArtifactType.BUNDLE_MANIFEST, taskName, "bundle-manifest");

        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends ProcessApplicationManifest> taskProvider) {
            super.handleProvider(taskProvider);
            variantScope.getTaskContainer().setProcessManifestTask(taskProvider);

            variantScope
                    .getArtifacts()
                    .registerProducer(
                            InternalArtifactType.MERGED_MANIFESTS,
                            BuildArtifactsHolder.OperationType.INITIAL,
                            taskProvider,
                            taskProvider.map(ManifestProcessorTask::getManifestOutputDirectory),
                            "");

            variantScope
                    .getArtifacts()
                    .registerProducer(
                            InternalArtifactType.INSTANT_RUN_MERGED_MANIFESTS,
                            BuildArtifactsHolder.OperationType.INITIAL,
                            taskProvider,
                            taskProvider.map(
                                    ManifestProcessorTask::getInstantRunManifestOutputDirectory),
                            "");

            variantScope
                    .getArtifacts()
                    .registerProducer(
                            InternalArtifactType.INSTANT_APP_MANIFEST,
                            BuildArtifactsHolder.OperationType.INITIAL,
                            taskProvider,
                            taskProvider.map(
                                    ManifestProcessorTask::getInstantAppManifestOutputDirectory),
                            "");
        }

        @Override
        public void configure(@NonNull ProcessApplicationManifest task) {
            super.configure(task);
            task.checkManifestResult =
                    variantScope
                            .getArtifacts()
                            .getFinalArtifactFilesIfPresent(
                                    InternalArtifactType.CHECK_MANIFEST_RESULT);

            final BaseVariantData variantData = variantScope.getVariantData();
            final GradleVariantConfiguration config = variantData.getVariantConfiguration();
            GlobalScope globalScope = variantScope.getGlobalScope();

            VariantType variantType = variantScope.getType();

            task.outputScope = variantData.getOutputScope();

            task.setVariantConfiguration(config);

            Project project = globalScope.getProject();

            // This includes the dependent libraries.
            task.manifests = variantScope.getArtifactCollection(RUNTIME_CLASSPATH, ALL, MANIFEST);

            // Also include rewritten auto-namespaced manifests if there are any
            if (variantType
                            .isBaseModule() // TODO(b/112251836): Auto namespacing for dynamic features.
                    && variantScope.getGlobalScope().getExtension().getAaptOptions().getNamespaced()
                    && variantScope
                            .getGlobalScope()
                            .getProjectOptions()
                            .get(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES)) {
                task.autoNamespacedManifests =
                        variantScope
                                .getArtifacts()
                                .getFinalArtifactFiles(InternalArtifactType.NAMESPACED_MANIFESTS);
            }

            // optional manifest files too.
            if (variantScope.getTaskContainer().getMicroApkTask() != null
                    && config.getBuildType().isEmbedMicroApp()) {
                task.microApkManifest = project.files(variantScope.getMicroApkManifestFile());
            }
            BuildArtifactsHolder artifacts = variantScope.getArtifacts();
            task.compatibleScreensManifest =
                    artifacts.getFinalArtifactFiles(
                            InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST);

            task.minSdkVersion =
                    TaskInputHelper.memoize(
                            () -> {
                                ApiVersion minSdk = config.getMergedFlavor().getMinSdkVersion();
                                return minSdk == null ? null : minSdk.getApiString();
                            });

            task.targetSdkVersion =
                    TaskInputHelper.memoize(
                            () -> {
                                ApiVersion targetSdk =
                                        config.getMergedFlavor().getTargetSdkVersion();
                                return targetSdk == null ? null : targetSdk.getApiString();
                            });

            task.maxSdkVersion =
                    TaskInputHelper.memoize(config.getMergedFlavor()::getMaxSdkVersion);

            task.setMetadataFeatureManifestOutputDirectory(featureManifestOutputDirectory);
            task.setBundleManifestOutputDirectory(bundleManifestOutputDirectory);

            task.setReportFile(reportFile);
            task.optionalFeatures =
                    TaskInputHelper.memoize(
                            () -> getOptionalFeatures(variantScope, isAdvancedProfilingOn));

            task.apkList = artifacts.getFinalArtifactFiles(InternalArtifactType.APK_LIST);

            // set optional inputs per module type
            if (variantType.isBaseModule()) {
                task.packageManifest =
                        variantScope.getArtifactFileCollection(
                                METADATA_VALUES, MODULE, METADATA_BASE_MODULE_DECLARATION);

                task.featureManifests =
                        variantScope.getArtifactCollection(
                                METADATA_VALUES, MODULE, METADATA_FEATURE_MANIFEST);

            } else if (variantType.isFeatureSplit()) {
                task.featureNameSupplier =
                        FeatureSetMetadata.getInstance()
                                .getFeatureNameSupplierForTask(variantScope, task);

                task.packageManifest =
                        variantScope.getArtifactFileCollection(
                                COMPILE_CLASSPATH, MODULE, FEATURE_APPLICATION_ID_DECLARATION);
            }
        }

        /**
         * Implementation of AndroidBundle that only contains a manifest.
         *
         * This is used to pass to the merger manifest snippet that needs to be added during
         * merge.
         */
        public static class ManifestProviderImpl implements ManifestProvider {

            @NonNull
            private final File manifest;

            @NonNull
            private final String name;

            public ManifestProviderImpl(@NonNull File manifest, @NonNull String name) {
                this.manifest = manifest;
                this.name = name;
            }

            @NonNull
            @Override
            public File getManifest() {
                return manifest;
            }

            @NonNull
            @Override
            public String getName() {
                return name;
            }
        }
    }

    private static EnumSet<Feature> getOptionalFeatures(
            VariantScope variantScope, boolean isAdvancedProfilingOn) {
        List<Feature> features = new ArrayList<>();
        VariantType variantType = variantScope.getType();
        if (variantType.isHybrid()) {
            features.add(Feature.TARGET_SANDBOX_VERSION);
        }

        if (variantType.isFeatureSplit()) {
            features.add(Feature.ADD_FEATURE_SPLIT_ATTRIBUTE);
            features.add(Feature.CREATE_FEATURE_MANIFEST);
        }

        if (variantType.isDynamicFeature()) {
            features.add(Feature.STRIP_MIN_SDK_FROM_FEATURE_MANIFEST);
        }

        if (variantType.isHybrid()) {
            features.add(Feature.ADD_INSTANT_APP_FEATURE_SPLIT_INFO);
        }

        if (!variantType.isHybrid()) {
            features.add(Feature.ADD_INSTANT_APP_MANIFEST);
        }

        if (variantType.isBaseModule() || variantType.isFeatureSplit()) {
            features.add(Feature.CREATE_BUNDLETOOL_MANIFEST);
        }

        if (variantType.isDynamicFeature()) {
            // create it for dynamic-features and base modules that are not hybrid base features.
            // hybrid features already contain the split name.
            features.add(Feature.ADD_SPLIT_NAME_TO_BUNDLETOOL_MANIFEST);
        }

        if (variantScope.isTestOnly()) {
            features.add(Feature.TEST_ONLY);
        }
        if (variantScope.getVariantConfiguration().getBuildType().isDebuggable()) {
            features.add(Feature.DEBUGGABLE);
            if (isAdvancedProfilingOn) {
                features.add(Feature.ADVANCED_PROFILING);
            }
        }
        if (variantScope.getInstantRunBuildContext().isInInstantRunMode()) {
            features.add(Feature.INSTANT_RUN_REPLACEMENT);
        }
        if (variantScope.getVariantConfiguration().getDexingType() == DexingType.LEGACY_MULTIDEX) {
            if (variantScope
                    .getGlobalScope()
                    .getProjectOptions()
                    .get(BooleanOption.USE_ANDROID_X)) {
                features.add(Feature.ADD_ANDROIDX_MULTIDEX_APPLICATION_IF_NO_NAME);
            } else {
                features.add(Feature.ADD_SUPPORT_MULTIDEX_APPLICATION_IF_NO_NAME);
            }
        }

        if (variantScope
                .getGlobalScope()
                .getProjectOptions()
                .get(BooleanOption.ENFORCE_UNIQUE_PACKAGE_NAMES)) {
            features.add(Feature.ENFORCE_UNIQUE_PACKAGE_NAME);
        }

        return features.isEmpty() ? EnumSet.noneOf(Feature.class) : EnumSet.copyOf(features);
    }
}
