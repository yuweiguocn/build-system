/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_APPLICATION_ID_DECLARATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.METADATA_BASE_MODULE_DECLARATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.METADATA_VALUES;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.VariantOutput;
import com.android.build.gradle.internal.core.VariantConfiguration;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.dsl.DslAdaptersKt;
import com.android.build.gradle.internal.res.Aapt2MavenUtils;
import com.android.build.gradle.internal.res.Aapt2ProcessResourcesRunnable;
import com.android.build.gradle.internal.res.namespaced.Aapt2DaemonManagerService;
import com.android.build.gradle.internal.res.namespaced.Aapt2ServiceKey;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidBuilderTask;
import com.android.build.gradle.internal.tasks.ModuleMetadata;
import com.android.build.gradle.internal.tasks.Workers;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.utils.FileUtils;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Comparator;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.inject.Inject;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkerExecutor;

/** Generates all metadata (like AndroidManifest.xml) necessary for a ABI dimension split APK. */
public class GenerateSplitAbiRes extends AndroidBuilderTask {

    @NonNull private final WorkerExecutorFacade workers;

    @Inject
    public GenerateSplitAbiRes(@NonNull WorkerExecutor workerExecutor) {
        this.workers = Workers.INSTANCE.getWorker(workerExecutor);
    }

    private Supplier<String> applicationId;
    private String outputBaseName;

    // these are the default values set in the variant's configuration, although they
    // are not directly use in this task, they will be used when versionName and versionCode
    // is not changed by the user's scripts. Therefore, if those values change, this task
    // should be considered out of date.
    private Supplier<String> versionName;
    private IntSupplier versionCode;

    // We use a sorted map so the key set order is consistent since it's considered an input.
    private ImmutableSortedMap<String, ApkData> splits;
    private File outputDirectory;
    private boolean debuggable;
    private AaptOptions aaptOptions;
    private VariantType variantType;
    @VisibleForTesting @Nullable Supplier<String> featureNameSupplier;
    @Nullable private FileCollection applicationIdOverride;
    @Nullable private FileCollection aapt2FromMaven;

    @Input
    public String getApplicationId() {
        return applicationId.get();
    }

    @Input
    public int getVersionCode() {
        return versionCode.getAsInt();
    }

    @Input
    @Optional
    public String getVersionName() {
        return versionName.get();
    }

    @Input
    public String getOutputBaseName() {
        return outputBaseName;
    }

    @Input
    public Set<String> getSplits() {
        return splits.keySet();
    }

    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    @Input
    public boolean isDebuggable() {
        return debuggable;
    }

    @Nested
    public AaptOptions getAaptOptions() {
        return aaptOptions;
    }

    @Input
    @Optional
    @Nullable
    public String getFeatureName() {
        return featureNameSupplier != null ? featureNameSupplier.get() : null;
    }

    @InputFiles
    @Optional
    @Nullable
    public FileCollection getApplicationIdOverride() {
        return applicationIdOverride;
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    @Nullable
    public FileCollection getAapt2FromMaven() {
        return aapt2FromMaven;
    }

    @TaskAction
    protected void doFullTaskAction() throws IOException {

        ImmutableList.Builder<BuildOutput> buildOutputs = ImmutableList.builder();

        try (WorkerExecutorFacade workerExecutor = workers) {
            for (String split : splits.keySet()) {
                File resPackageFile = getOutputFileForSplit(split);
                File manifestFile = generateSplitManifest(split, splits.get(split));

                AndroidBuilder builder = getBuilder();
                AaptPackageConfig aaptConfig =
                        new AaptPackageConfig.Builder()
                                .setManifestFile(manifestFile)
                                .setOptions(DslAdaptersKt.convert(aaptOptions))
                                .setDebuggable(debuggable)
                                .setResourceOutputApk(resPackageFile)
                                .setVariantType(variantType)
                                .setAndroidTarget(builder.getTarget())
                                .build();

                Aapt2ServiceKey aapt2ServiceKey =
                        Aapt2DaemonManagerService.registerAaptService(
                                aapt2FromMaven, builder.getBuildToolInfo(), builder.getLogger());
                Aapt2ProcessResourcesRunnable.Params params =
                        new Aapt2ProcessResourcesRunnable.Params(aapt2ServiceKey, aaptConfig);
                workerExecutor.submit(Aapt2ProcessResourcesRunnable.class, params);

                buildOutputs.add(
                        new BuildOutput(
                                InternalArtifactType.ABI_PROCESSED_SPLIT_RES,
                                splits.get(split),
                                resPackageFile));
            }
        }
        new BuildElements(buildOutputs.build()).save(outputDirectory);
    }

    @VisibleForTesting
    File generateSplitManifest(String split, ApkData apkInfo) throws IOException {
        // Split name can only contains 0-9, a-z, A-Z, '.' and '_'.  Replace all other
        // characters with underscore.
        CharMatcher charMatcher =
                CharMatcher.inRange('0', '9')
                        .or(CharMatcher.inRange('A', 'Z'))
                        .or(CharMatcher.inRange('a', 'z'))
                        .or(CharMatcher.is('_'))
                        .or(CharMatcher.is('.'))
                        .negate();

        String featureName = getFeatureName();

        String encodedSplitName =
                (featureName != null ? featureName + "." : "")
                        + "config."
                        + charMatcher.replaceFrom(split, '_');

        File tmpDirectory = new File(outputDirectory, split);
        FileUtils.mkdirs(tmpDirectory);

        File tmpFile = new File(tmpDirectory, "AndroidManifest.xml");

        String versionNameToUse = apkInfo.getVersionName();
        if (versionNameToUse == null) {
            versionNameToUse = String.valueOf(apkInfo.getVersionCode());
        }

        // Override the applicationId for features.
        String manifestAppId;
        if (applicationIdOverride != null && !applicationIdOverride.isEmpty()) {
            manifestAppId =
                    ModuleMetadata.load(applicationIdOverride.getSingleFile()).getApplicationId();
        } else {
            manifestAppId = applicationId.get();
        }

        try (OutputStreamWriter fileWriter =
                new OutputStreamWriter(
                        new BufferedOutputStream(new FileOutputStream(tmpFile)), "UTF-8")) {

            fileWriter.append(
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "      package=\""
                            + manifestAppId
                            + "\"\n"
                            + "      android:versionCode=\""
                            + apkInfo.getVersionCode()
                            + "\"\n"
                            + "      android:versionName=\""
                            + versionNameToUse
                            + "\"\n");

            if (featureName != null) {
                fileWriter.append("      configForSplit=\"" + featureName + "\"\n");
            }

            fileWriter.append(
                    "      split=\""
                            + encodedSplitName
                            + "\"\n"
                            + "      targetABI=\""
                            + split
                            + "\">\n"
                            + "       <uses-sdk android:minSdkVersion=\"21\"/>\n"
                            + "</manifest> ");
            fileWriter.flush();
        }
        return tmpFile;
    }

    // FIX ME : this calculation should move to SplitScope.Split interface
    private File getOutputFileForSplit(final String split) {
        return new File(outputDirectory, "resources-" + getOutputBaseName() + "-" + split + ".ap_");
    }

    // ----- CreationAction -----

    public static class CreationAction extends VariantTaskCreationAction<GenerateSplitAbiRes> {

        @NonNull private final FeatureSetMetadata.SupplierProvider provider;
        private File outputDirectory;

        public CreationAction(@NonNull VariantScope scope) {
            this(scope, FeatureSetMetadata.getInstance());
        }

        @VisibleForTesting
        CreationAction(
                @NonNull VariantScope scope,
                @NonNull FeatureSetMetadata.SupplierProvider provider) {
            super(scope);
            this.provider = provider;
        }

        @Override
        @NonNull
        public String getName() {
            return getVariantScope().getTaskName("generate", "SplitAbiRes");
        }

        @Override
        @NonNull
        public Class<GenerateSplitAbiRes> getType() {
            return GenerateSplitAbiRes.class;
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);

            outputDirectory =
                    getVariantScope()
                            .getArtifacts()
                            .appendArtifact(
                                    InternalArtifactType.ABI_PROCESSED_SPLIT_RES, taskName, "out");
        }

        @Override
        public void configure(@NonNull GenerateSplitAbiRes task) {
            super.configure(task);
            VariantScope scope = getVariantScope();

            final VariantConfiguration config = scope.getVariantConfiguration();
            VariantType variantType = config.getType();

            if (variantType.isFeatureSplit()) {
                task.featureNameSupplier = provider.getFeatureNameSupplierForTask(scope, task);
            }

            // not used directly, but considered as input for the task.
            task.versionCode = config::getVersionCode;
            task.versionName = config::getVersionName;

            task.variantType = variantType;
            task.outputDirectory = outputDirectory;
            task.splits = getAbiSplitData(scope);
            task.outputBaseName = config.getBaseName();
            task.applicationId = config::getApplicationId;
            task.debuggable = config.getBuildType().isDebuggable();
            task.aaptOptions = scope.getGlobalScope().getExtension().getAaptOptions();
            task.aapt2FromMaven = Aapt2MavenUtils.getAapt2FromMaven(scope.getGlobalScope());

            // if BASE_FEATURE get the app ID from the app module
            if (variantType.isBaseModule() && variantType.isHybrid()) {
                task.applicationIdOverride =
                        scope.getArtifactFileCollection(
                                METADATA_VALUES, MODULE, METADATA_BASE_MODULE_DECLARATION);
            } else if (variantType.isFeatureSplit()) {
                // if feature split, get it from the base module
                task.applicationIdOverride =
                        scope.getArtifactFileCollection(
                                COMPILE_CLASSPATH, MODULE, FEATURE_APPLICATION_ID_DECLARATION);
            }
        }

        private static ImmutableSortedMap<String, ApkData> getAbiSplitData(
                VariantScope variantScope) {
            return variantScope
                    .getOutputScope()
                    .getApkDatas()
                    .stream()
                    .filter(
                            apk ->
                                    apk.isEnabled()
                                            && apk.getFilter(VariantOutput.FilterType.ABI) != null)
                    .collect(
                            ImmutableSortedMap.toImmutableSortedMap(
                                    Comparator.naturalOrder(),
                                    apk ->
                                            apk.getFilter(VariantOutput.FilterType.ABI)
                                                    .getIdentifier(),
                                    apk -> apk));
        }
    }
}
