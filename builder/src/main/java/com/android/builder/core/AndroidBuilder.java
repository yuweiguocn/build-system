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

package com.android.builder.core;

import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.FD_RES_XML;
import static com.android.builder.core.BuilderConstants.ANDROID_WEAR;
import static com.android.builder.core.BuilderConstants.ANDROID_WEAR_MICRO_APK;
import static com.android.manifmerger.ManifestMerger2.Invoker;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.errors.EvalIssueException;
import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.builder.files.RelativeFile;
import com.android.builder.internal.TestManifestGenerator;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.aapt.BlockingResourceLinker;
import com.android.builder.internal.aapt.v2.Aapt2Exception;
import com.android.builder.internal.aapt.v2.Aapt2InternalException;
import com.android.builder.internal.compiler.DirectoryWalker;
import com.android.builder.internal.compiler.RenderScriptProcessor;
import com.android.builder.internal.compiler.ShaderProcessor;
import com.android.builder.internal.packaging.IncrementalPackager;
import com.android.builder.model.SigningConfig;
import com.android.builder.packaging.PackagerException;
import com.android.builder.sdk.SdkInfo;
import com.android.builder.sdk.TargetInfo;
import com.android.ide.common.blame.MessageReceiver;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessInfo;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.process.ProcessResult;
import com.android.ide.common.resources.FileStatus;
import com.android.ide.common.signing.CertificateInfo;
import com.android.ide.common.signing.KeystoreHelper;
import com.android.ide.common.signing.KeytoolException;
import com.android.ide.common.symbols.RGeneration;
import com.android.ide.common.symbols.SymbolIo;
import com.android.ide.common.symbols.SymbolTable;
import com.android.ide.common.symbols.SymbolUtils;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.manifmerger.ManifestMerger2;
import com.android.manifmerger.ManifestProvider;
import com.android.manifmerger.ManifestSystemProperty;
import com.android.manifmerger.MergingReport;
import com.android.manifmerger.PlaceholderHandler;
import com.android.repository.Revision;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.build.apkzlib.sign.SigningOptions;
import com.android.tools.build.apkzlib.zfile.ApkCreatorFactory;
import com.android.tools.build.apkzlib.zfile.NativeLibrariesPackagingMode;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * This is the main builder class. It is given all the data to process the build (such as {@link
 * DefaultProductFlavor}s, {@link DefaultBuildType} and dependencies) and use them when doing
 * specific build steps.
 *
 * <p>To use: create a builder with {@link #AndroidBuilder(String, String, ProcessExecutor,
 * JavaProcessExecutor, EvalIssueReporter, MessageReceiver, ILogger)}
 *
 * <p>then build steps can be done with:
 *
 * <ol>
 *   <li>{@link #mergeManifestsForApplication }
 *   <li>{@link #mergeManifestsForTestVariant }
 *   <li>{@link #processResources }
 * </ol>
 *
 * <p>Java compilation is not handled but the builder provides the boot classpath with {@link
 * #getBootClasspath(boolean)}.
 */
public class AndroidBuilder {

    /**
     * Minimal supported version of build tools.
     *
     * <p>ATTENTION: When changing this value, make sure to update the release notes
     * (https://developer.android.com/studio/releases/gradle-plugin).
     */
    public static final Revision MIN_BUILD_TOOLS_REV =
            Revision.parseRevision(SdkConstants.CURRENT_BUILD_TOOLS_VERSION);

    /**
     * Default version of build tools that will be used if the user does not specify.
     *
     * <p>ATTENTION: This is usually the same as the minimum build tools version, as documented in
     * {@code com.android.build.gradle.AndroidConfig#getBuildToolsVersion()} and {@code
     * com.android.build.api.dsl.extension.BuildProperties#getBuildToolsVersion()}, and in the
     * release notes (https://developer.android.com/studio/releases/gradle-plugin). If this version
     * is higher than the minimum version, make sure to update those places to document the new
     * behavior.
     */
    public static final Revision DEFAULT_BUILD_TOOLS_REVISION = MIN_BUILD_TOOLS_REV;

    /** API level for split APKs. */
    private static final int API_LEVEL_SPLIT_APK = 21;

    @NonNull
    private final String mProjectId;
    @NonNull
    private final ILogger mLogger;

    @NonNull
    private final ProcessExecutor mProcessExecutor;
    @NonNull
    private final JavaProcessExecutor mJavaProcessExecutor;
    @NonNull private final EvalIssueReporter issueReporter;
    @NonNull private final MessageReceiver messageReceiver;

    @Nullable private String mCreatedBy;


    private Supplier<SdkInfo> mSdkInfoProvider = () -> null;
    private Supplier<TargetInfo> mTargetInfoProvider = () -> null;

    private List<File> mBootClasspathFiltered;
    private List<File> mBootClasspathAll;
    @NonNull
    private List<LibraryRequest> mLibraryRequests = ImmutableList.of();

    /**
     * Creates an AndroidBuilder.
     *
     * <p><var>verboseExec</var> is needed on top of the ILogger due to remote exec tools not being
     * able to output info and verbose messages separately.
     *
     * @param createdBy the createdBy String for the apk manifest.
     * @param logger the Logger
     */
    public AndroidBuilder(
            @NonNull String projectId,
            @Nullable String createdBy,
            @NonNull ProcessExecutor processExecutor,
            @NonNull JavaProcessExecutor javaProcessExecutor,
            @NonNull EvalIssueReporter issueReporter,
            @NonNull MessageReceiver messageReceiver,
            @NonNull ILogger logger) {
        mProjectId = checkNotNull(projectId);
        mCreatedBy = createdBy;
        mProcessExecutor = checkNotNull(processExecutor);
        mJavaProcessExecutor = checkNotNull(javaProcessExecutor);
        this.issueReporter = checkNotNull(issueReporter);
        this.messageReceiver = messageReceiver;
        mLogger = checkNotNull(logger);
    }

    /**
     * Sets the SdkInfo and the targetInfo on the builder. This is required to actually
     * build (some of the steps).
     *
     * @see com.android.builder.sdk.SdkLoader
     */
    public void setTargetInfo(@NonNull TargetInfo targetInfo) {
        mTargetInfoProvider = () -> targetInfo;
    }

    /**
     * Sets the SdkInfo and the targetInfo on the builder. This is required to actually
     * build (some of the steps).
     *
     * @see com.android.builder.sdk.SdkLoader
     */
    public void setTargetInfo(@NonNull Supplier<TargetInfo> targetInfoProvider) {
        mTargetInfoProvider = targetInfoProvider;
    }

    public void setSdkInfo(@NonNull SdkInfo sdkInfo) {
        mSdkInfoProvider = () -> sdkInfo;
    }

    public void setSdkInfoProvider(@NonNull Supplier<SdkInfo> sdkInfoProvider) {
        mSdkInfoProvider = sdkInfoProvider;
    }

    public void setLibraryRequests(@NonNull Collection<LibraryRequest> libraryRequests) {
        mLibraryRequests = ImmutableList.copyOf(libraryRequests);
    }

    /**
     * Returns the SdkInfo, if set.
     */
    @Nullable
    public SdkInfo getSdkInfo() {
        return mSdkInfoProvider.get();
    }

    /**
     * Returns the TargetInfo, if set.
     */
    @Nullable
    public TargetInfo getTargetInfo() {
        TargetInfo targetInfo = mTargetInfoProvider.get();

        if (targetInfo != null && targetInfo.getBuildTools().getRevision().compareTo(MIN_BUILD_TOOLS_REV) < 0) {
            issueReporter.reportError(
                    EvalIssueReporter.Type.BUILD_TOOLS_TOO_LOW,
                    new EvalIssueException(
                            String.format(
                                    "The SDK Build Tools revision (%1$s) is too low for project '%2$s'. "
                                            + "Minimum required is %3$s",
                                    targetInfo.getBuildTools().getRevision(),
                                    mProjectId,
                                    MIN_BUILD_TOOLS_REV),
                            MIN_BUILD_TOOLS_REV.toString()));
        }

        return targetInfo;
    }

    /** Returns the build tools for this builder. */
    @NonNull
    public BuildToolInfo getBuildToolInfo() {
        checkNotNull(
                getTargetInfo(), "Cannot call getBuildToolInfo() before setTargetInfo() is called.");
        return getTargetInfo().getBuildTools();
    }

    @NonNull
    public ILogger getLogger() {
        return mLogger;
    }

    @NonNull
    public EvalIssueReporter getIssueReporter() {
        return issueReporter;
    }

    @NonNull
    public MessageReceiver getMessageReceiver() {
        return messageReceiver;
    }

    /** Returns the compilation target, if set. */
    @NonNull
    public IAndroidTarget getTarget() {
        checkState(getTargetInfo() != null,
                "Cannot call getTarget() before setTargetInfo() is called.");
        return getTargetInfo().getTarget();
    }

    /**
     * Returns whether the compilation target is a preview.
     */
    public boolean isPreviewTarget() {
        checkState(getTargetInfo() != null,
                "Cannot call isTargetAPreview() before setTargetInfo() is called.");
        return getTargetInfo().getTarget().getVersion().isPreview();
    }

    /**
     * Helper method to get the boot classpath to be used during compilation.
     *
     * @param includeOptionalLibraries if true, optional libraries are included even if not
     *                                 required by the project setup.
     */
    @NonNull
    public List<File> getBootClasspath(boolean includeOptionalLibraries) {
        if (includeOptionalLibraries) {
            return computeFullBootClasspath();
        }

        return computeFilteredBootClasspath();
    }

    /**
     * Returns the list of additional and requested optional library jar files
     *
     * @return the list of files from the additional and optional libraries which appear in the
     *     filtered boot classpath
     */
    public List<File> computeAdditionalAndRequestedOptionalLibraries() {
        return BootClasspathBuilder.computeAdditionalAndRequestedOptionalLibraries(
                getTargetInfo().getTarget(), mLibraryRequests, issueReporter);
    }

    private List<File> computeFilteredBootClasspath() {
        // computes and caches the filtered boot classpath.
        // Changes here should be applied to #computeFullClasspath()

        if (mBootClasspathFiltered == null) {
            checkState(getTargetInfo() != null,
                    "Cannot call getBootClasspath() before setTargetInfo() is called.");

            mBootClasspathFiltered =
                    BootClasspathBuilder.computeFilteredClasspath(
                            getTargetInfo().getTarget(),
                            mLibraryRequests,
                            issueReporter,
                            getSdkInfo().getAnnotationsJar());
        }

        return mBootClasspathFiltered;
    }

    @NonNull
    private List<File> computeFullBootClasspath() {
        // computes and caches the full boot classpath.
        // Changes here should be applied to #computeFilteredClasspath()

        if (mBootClasspathAll == null) {
            checkState(getTargetInfo() != null,
                    "Cannot call getBootClasspath() before setTargetInfo() is called.");

            mBootClasspathAll = BootClasspathBuilder.computeFullBootClasspath(
                    getTargetInfo().getTarget(),
                    getSdkInfo().getAnnotationsJar());
        }

        return mBootClasspathAll;
    }

    /**
     * Helper method to get the boot classpath to be used during compilation.
     *
     * @param includeOptionalLibraries if true, optional libraries are included even if not
     *                                 required by the project setup.
     */
    @NonNull
    public List<String> getBootClasspathAsStrings(boolean includeOptionalLibraries) {
        List<File> classpath = getBootClasspath(includeOptionalLibraries);

        // convert to Strings.
        List<String> results = Lists.newArrayListWithCapacity(classpath.size());
        for (File f : classpath) {
            results.add(f.getAbsolutePath());
        }

        return results;
    }

    /**
     * Returns the jar file for the renderscript mode.
     *
     * <p>This may return null if the SDK has not been loaded yet.
     *
     * @param useAndroidX whether to use AndroidX dependencies
     * @return the jar file, or null.
     * @see #setTargetInfo(TargetInfo)
     */
    @Nullable
    public File getRenderScriptSupportJar(boolean useAndroidX) {
        if (getTargetInfo() != null) {
            return RenderScriptProcessor.getSupportJar(
                    getTargetInfo().getBuildTools().getLocation().getAbsolutePath(), useAndroidX);
        }

        return null;
    }

    /**
     * Returns the native lib folder for the renderscript mode.
     *
     * This may return null if the SDK has not been loaded yet.
     *
     * @return the folder, or null.
     *
     * @see #setTargetInfo(TargetInfo)
     */
    @Nullable
    public File getSupportNativeLibFolder() {
        if (getTargetInfo() != null) {
            return RenderScriptProcessor.getSupportNativeLibFolder(
                    getTargetInfo().getBuildTools().getLocation().getAbsolutePath());
        }

        return null;
    }

    /**
     * Returns the BLAS lib folder for renderscript support mode.
     *
     * This may return null if the SDK has not been loaded yet.
     *
     * @return the folder, or null.
     *
     * @see #setTargetInfo(TargetInfo)
     */
    @Nullable
    public File getSupportBlasLibFolder() {
        if (getTargetInfo() != null) {
            return RenderScriptProcessor.getSupportBlasLibFolder(
                    getTargetInfo().getBuildTools().getLocation().getAbsolutePath());
        }

        return null;
    }

    @NonNull
    public ProcessExecutor getProcessExecutor() {
        return mProcessExecutor;
    }

    @NonNull
    public JavaProcessExecutor getJavaProcessExecutor() {
        return mJavaProcessExecutor;
    }

    @NonNull
    public ProcessResult executeProcess(@NonNull ProcessInfo processInfo,
            @NonNull ProcessOutputHandler handler) {
        return mProcessExecutor.execute(processInfo, handler);
    }

    /** Invoke the Manifest Merger version 2. */
    public MergingReport mergeManifestsForApplication(
            @NonNull File mainManifest,
            @NonNull List<File> manifestOverlays,
            @NonNull List<? extends ManifestProvider> dependencies,
            @NonNull List<File> navigationFiles,
            @Nullable String featureName,
            String packageOverride,
            int versionCode,
            String versionName,
            @Nullable String minSdkVersion,
            @Nullable String targetSdkVersion,
            @Nullable Integer maxSdkVersion,
            @NonNull String outManifestLocation,
            @Nullable String outAaptSafeManifestLocation,
            @Nullable String outInstantRunManifestLocation,
            @Nullable String outMetadataFeatureManifestLocation,
            @Nullable String outBundleManifestLocation,
            @Nullable String outInstantAppManifestLocation,
            ManifestMerger2.MergeType mergeType,
            Map<String, Object> placeHolders,
            @NonNull Collection<Invoker.Feature> optionalFeatures,
            @Nullable File reportFile) {

        try {

            Invoker manifestMergerInvoker =
                    ManifestMerger2.newMerger(mainManifest, mLogger, mergeType)
                            .setPlaceHolderValues(placeHolders)
                            .addFlavorAndBuildTypeManifests(manifestOverlays.toArray(new File[0]))
                            .addManifestProviders(dependencies)
                            .addNavigationFiles(navigationFiles)
                            .withFeatures(optionalFeatures.toArray(new Invoker.Feature[0]))
                            .setMergeReportFile(reportFile)
                            .setFeatureName(featureName);

            if (mergeType == ManifestMerger2.MergeType.APPLICATION) {
                manifestMergerInvoker.withFeatures(Invoker.Feature.REMOVE_TOOLS_DECLARATIONS);
            }

            //noinspection VariableNotUsedInsideIf
            if (outAaptSafeManifestLocation != null) {
                manifestMergerInvoker.withFeatures(Invoker.Feature.MAKE_AAPT_SAFE);
            }

            setInjectableValues(manifestMergerInvoker,
                    packageOverride, versionCode, versionName,
                    minSdkVersion, targetSdkVersion, maxSdkVersion);

            MergingReport mergingReport = manifestMergerInvoker.merge();
            mLogger.verbose("Merging result: %1$s", mergingReport.getResult());
            switch (mergingReport.getResult()) {
                case WARNING:
                    mergingReport.log(mLogger);
                    // fall through since these are just warnings.
                case SUCCESS:
                    String xmlDocument =
                            mergingReport.getMergedDocument(
                                    MergingReport.MergedManifestKind.MERGED);
                    String annotatedDocument =
                            mergingReport.getMergedDocument(MergingReport.MergedManifestKind.BLAME);
                    if (annotatedDocument != null) {
                        mLogger.verbose(annotatedDocument);
                    }
                    save(xmlDocument, new File(outManifestLocation));
                    mLogger.verbose("Merged manifest saved to " + outManifestLocation);

                    if (outAaptSafeManifestLocation != null) {
                        save(
                                mergingReport.getMergedDocument(
                                        MergingReport.MergedManifestKind.AAPT_SAFE),
                                new File(outAaptSafeManifestLocation));
                    }

                    if (outInstantRunManifestLocation != null) {
                        String instantRunMergedManifest =
                                mergingReport.getMergedDocument(
                                        MergingReport.MergedManifestKind.INSTANT_RUN);
                        if (instantRunMergedManifest != null) {
                            save(instantRunMergedManifest, new File(outInstantRunManifestLocation));
                        }
                    }

                    if (outMetadataFeatureManifestLocation != null) {
                        // This is the manifest used for merging back to the base. This is created
                        // by both dynamic-features and normal features.
                        String featureManifest =
                                mergingReport.getMergedDocument(
                                        MergingReport.MergedManifestKind.METADATA_FEATURE);
                        if (featureManifest != null) {
                            save(featureManifest, new File(outMetadataFeatureManifestLocation));
                        }
                    }

                    if (outBundleManifestLocation != null) {
                        String bundleMergedManifest =
                                mergingReport.getMergedDocument(
                                        MergingReport.MergedManifestKind.BUNDLE);
                        if (bundleMergedManifest != null) {
                            save(bundleMergedManifest, new File(outBundleManifestLocation));
                        }
                    }

                    if (outInstantAppManifestLocation != null) {
                        String instantAppManifest =
                                mergingReport.getMergedDocument(
                                        MergingReport.MergedManifestKind.INSTANT_APP);
                        if (instantAppManifest != null) {
                            save(instantAppManifest, new File(outInstantAppManifestLocation));
                        }
                    }
                    break;
                case ERROR:
                    mergingReport.log(mLogger);
                    throw new RuntimeException(mergingReport.getReportString());
                default:
                    throw new RuntimeException("Unhandled result type : "
                            + mergingReport.getResult());
            }
            return mergingReport;
        } catch (ManifestMerger2.MergeFailureException e) {
            // TODO: unacceptable.
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets the {@link ManifestSystemProperty} that can be injected
     * in the manifest file.
     */
    private static void setInjectableValues(
            ManifestMerger2.Invoker<?> invoker,
            String packageOverride,
            int versionCode,
            String versionName,
            @Nullable String minSdkVersion,
            @Nullable String targetSdkVersion,
            @Nullable Integer maxSdkVersion) {

        if (!Strings.isNullOrEmpty(packageOverride)) {
            invoker.setOverride(ManifestSystemProperty.PACKAGE, packageOverride);
        }
        if (versionCode > 0) {
            invoker.setOverride(ManifestSystemProperty.VERSION_CODE,
                    String.valueOf(versionCode));
        }
        if (!Strings.isNullOrEmpty(versionName)) {
            invoker.setOverride(ManifestSystemProperty.VERSION_NAME, versionName);
        }
        if (!Strings.isNullOrEmpty(minSdkVersion)) {
            invoker.setOverride(ManifestSystemProperty.MIN_SDK_VERSION, minSdkVersion);
        }
        if (!Strings.isNullOrEmpty(targetSdkVersion)) {
            invoker.setOverride(ManifestSystemProperty.TARGET_SDK_VERSION, targetSdkVersion);
        }
        if (maxSdkVersion != null) {
            invoker.setOverride(ManifestSystemProperty.MAX_SDK_VERSION, maxSdkVersion.toString());
        }
    }

    /**
     * Saves the {@link com.android.manifmerger.XmlDocument} to a file in UTF-8 encoding.
     * @param xmlDocument xml document to save.
     * @param out file to save to.
     */
    private static void save(String xmlDocument, File out) {
        try {
            Files.createParentDirs(out);
            Files.asCharSink(out, Charsets.UTF_8).write(xmlDocument);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates the manifest for a test variant
     *
     * @param testApplicationId the application id of the test application
     * @param minSdkVersion the minSdkVersion of the test application
     * @param targetSdkVersion the targetSdkVersion of the test application
     * @param testedApplicationId the application id of the tested application
     * @param instrumentationRunner the name of the instrumentation runner
     * @param handleProfiling whether or not the Instrumentation object will turn profiling on and off
     * @param functionalTest whether or not the Instrumentation class should run as a functional test
     * @param testLabel the label for the tests
     * @param testManifestFile optionally user provided AndroidManifest.xml for testing application
     * @param manifestProviders the manifest providers
     * @param manifestPlaceholders used placeholders in the manifest
     * @param outManifest the output location for the merged manifest
     * @param tmpDir temporary dir used for processing
     */
    public void mergeManifestsForTestVariant(
            @NonNull String testApplicationId,
            @NonNull String minSdkVersion,
            @NonNull String targetSdkVersion,
            @NonNull String testedApplicationId,
            @NonNull String instrumentationRunner,
            @NonNull Boolean handleProfiling,
            @NonNull Boolean functionalTest,
            @Nullable String testLabel,
            @Nullable File testManifestFile,
            @NonNull List<? extends ManifestProvider> manifestProviders,
            @NonNull Map<String, Object> manifestPlaceholders,
            @NonNull File outManifest,
            @NonNull File tmpDir) {
        checkNotNull(testApplicationId, "testApplicationId cannot be null.");
        checkNotNull(testedApplicationId, "testedApplicationId cannot be null.");
        checkNotNull(instrumentationRunner, "instrumentationRunner cannot be null.");
        checkNotNull(handleProfiling, "handleProfiling cannot be null.");
        checkNotNull(functionalTest, "functionalTest cannot be null.");
        checkNotNull(manifestProviders, "manifestProviders cannot be null.");
        checkNotNull(outManifest, "outManifestLocation cannot be null.");

        // These temp files are only need in the middle of processing manifests; delete
        // them when they're done. We're not relying on File#deleteOnExit for this
        // since in the Gradle daemon for example that would leave the files around much
        // longer than we want.
        File tempFile1 = null;
        File tempFile2 = null;
        try {
            FileUtils.mkdirs(tmpDir);
            File generatedTestManifest = manifestProviders.isEmpty() && testManifestFile == null
                    ? outManifest
                    : (tempFile1 = File.createTempFile("manifestMerger", ".xml", tmpDir));

            // we are generating the manifest and if there is an existing one,
            // it will be overlaid with the generated one
            mLogger.verbose("Generating in %1$s", generatedTestManifest.getAbsolutePath());
            generateTestManifest(
                    testApplicationId,
                    minSdkVersion,
                    targetSdkVersion.equals("-1") ? null : targetSdkVersion,
                    testedApplicationId,
                    instrumentationRunner,
                    handleProfiling,
                    functionalTest,
                    generatedTestManifest);

            if (testManifestFile != null && testManifestFile.exists()) {
                Invoker invoker = ManifestMerger2.newMerger(
                        testManifestFile, mLogger, ManifestMerger2.MergeType.APPLICATION)
                        .setPlaceHolderValues(manifestPlaceholders)
                        .setPlaceHolderValue(PlaceholderHandler.INSTRUMENTATION_RUNNER,
                                instrumentationRunner)
                        .addLibraryManifest(generatedTestManifest);

                // we override these properties
                invoker.setOverride(ManifestSystemProperty.PACKAGE, testApplicationId);
                invoker.setOverride(ManifestSystemProperty.MIN_SDK_VERSION, minSdkVersion);
                invoker.setOverride(ManifestSystemProperty.NAME, instrumentationRunner);
                invoker.setOverride(ManifestSystemProperty.TARGET_PACKAGE, testedApplicationId);
                invoker.setOverride(ManifestSystemProperty.FUNCTIONAL_TEST, functionalTest.toString());
                invoker.setOverride(ManifestSystemProperty.HANDLE_PROFILING, handleProfiling.toString());
                if (testLabel != null) {
                    invoker.setOverride(ManifestSystemProperty.LABEL, testLabel);
                }

                if (!targetSdkVersion.equals("-1")) {
                    invoker.setOverride(ManifestSystemProperty.TARGET_SDK_VERSION, targetSdkVersion);
                }

                MergingReport mergingReport = invoker.merge();
                if (manifestProviders.isEmpty()) {
                    handleMergingResult(mergingReport, outManifest);
                } else {
                    tempFile2 = File.createTempFile("manifestMerger", ".xml", tmpDir);
                    handleMergingResult(mergingReport, tempFile2);
                    generatedTestManifest = tempFile2;
                }
            }

            if (!manifestProviders.isEmpty()) {
                MergingReport mergingReport = ManifestMerger2.newMerger(
                        generatedTestManifest, mLogger, ManifestMerger2.MergeType.APPLICATION)
                        .withFeatures(Invoker.Feature.REMOVE_TOOLS_DECLARATIONS)
                        .setOverride(ManifestSystemProperty.PACKAGE, testApplicationId)
                        .addManifestProviders(manifestProviders)
                        .setPlaceHolderValues(manifestPlaceholders)
                        .merge();

                handleMergingResult(mergingReport, outManifest);
            }
        } catch(IOException e) {
            throw new RuntimeException("Unable to create the temporary file", e);
        } catch (ManifestMerger2.MergeFailureException e) {
            throw new RuntimeException("Manifest merging exception", e);
        } finally {
            try {
                if (tempFile1 != null) {
                    FileUtils.delete(tempFile1);
                }
                if (tempFile2 != null) {
                    FileUtils.delete(tempFile2);
                }
            } catch (IOException e){
                // just log this, so we do not mask the initial exception if there is any
                mLogger.error(e, "Unable to clean up the temporary files.");
            }
        }
    }

    private void handleMergingResult(@NonNull MergingReport mergingReport, @NonNull File outFile) {
        switch (mergingReport.getResult()) {
            case WARNING:
                mergingReport.log(mLogger);
                // fall through since these are just warnings.
            case SUCCESS:
                try {
                    String annotatedDocument = mergingReport.getMergedDocument(
                            MergingReport.MergedManifestKind.BLAME);
                    if (annotatedDocument != null) {
                        mLogger.verbose(annotatedDocument);
                    } else {
                        mLogger.verbose("No blaming records from manifest merger");
                    }
                } catch (Exception e) {
                    mLogger.error(e, "cannot print resulting xml");
                }
                String finalMergedDocument = mergingReport
                        .getMergedDocument(MergingReport.MergedManifestKind.MERGED);
                if (finalMergedDocument == null) {
                    throw new RuntimeException("No result from manifest merger");
                }
                try {
                    Files.asCharSink(outFile, Charsets.UTF_8).write(finalMergedDocument);
                } catch (IOException e) {
                    mLogger.error(e, "Cannot write resulting xml");
                    throw new RuntimeException(e);
                }
                mLogger.verbose("Merged manifest saved to " + outFile);
                break;
            case ERROR:
                mergingReport.log(mLogger);
                throw new RuntimeException(mergingReport.getReportString());
            default:
                throw new RuntimeException("Unhandled result type : "
                        + mergingReport.getResult());
        }
    }

    private static void generateTestManifest(
            @NonNull String testApplicationId,
            @Nullable String minSdkVersion,
            @Nullable String targetSdkVersion,
            @NonNull String testedApplicationId,
            @NonNull String instrumentationRunner,
            @NonNull Boolean handleProfiling,
            @NonNull Boolean functionalTest,
            @NonNull File outManifestLocation) {
        TestManifestGenerator generator = new TestManifestGenerator(
                outManifestLocation,
                testApplicationId,
                minSdkVersion,
                targetSdkVersion,
                testedApplicationId,
                instrumentationRunner,
                handleProfiling,
                functionalTest);
        try {
            generator.generate();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Process the resources and generate R.java and/or the packaged resources.
     *
     * @param aapt the interface to the {@code aapt} tool
     * @param aaptConfigBuilder aapt command invocation parameters; this will receive some
     *     additional data (build tools, Android target and logger) and will be used to request
     *     package invocation in {@code aapt} (see {@link
     *     BlockingResourceLinker#link(AaptPackageConfig, ILogger)})
     * @throws IOException failed
     * @throws ProcessException failed
     */
    public void processResources(
            @NonNull BlockingResourceLinker aapt,
            @NonNull AaptPackageConfig.Builder aaptConfigBuilder)
            throws IOException, ProcessException {
        processResources(aapt, aaptConfigBuilder, getTarget(), mLogger);
    }

    /**
     * Process the resources and generate R.java and/or the packaged resources.
     *
     * @param aapt the interface to the {@code aapt} tool
     * @param aaptConfigBuilder aapt command invocation parameters
     * @param androidTarget the android target used in {@link AaptPackageConfig}
     * @param logger the logger used to request package invocation in {@code aapt} (see {@link
     *     BlockingResourceLinker#link(AaptPackageConfig, ILogger)})
     * @throws IOException failed
     * @throws ProcessException failed
     */
    public static void processResources(
            @NonNull BlockingResourceLinker aapt,
            @NonNull AaptPackageConfig.Builder aaptConfigBuilder,
            @NonNull IAndroidTarget androidTarget,
            @NonNull ILogger logger)
            throws IOException, ProcessException {

        aaptConfigBuilder.setAndroidTarget(androidTarget);

        AaptPackageConfig aaptConfig = aaptConfigBuilder.build();
        processResources(aapt, aaptConfig, logger);
    }

    public static void processResources(
            @NonNull BlockingResourceLinker aapt,
            @NonNull AaptPackageConfig aaptConfig,
            @NonNull ILogger logger)
            throws IOException, ProcessException {

        try {
            aapt.link(aaptConfig, logger);
        } catch (Aapt2Exception | Aapt2InternalException e) {
            throw e;
        } catch (Exception e) {
            throw new ProcessException("Failed to execute aapt", e);
        }

        File sourceOut = aaptConfig.getSourceOutputDir();
        if (sourceOut != null) {
            // Figure out what the main symbol file's package is.
            String mainPackageName = aaptConfig.getCustomPackageForR();
            if (mainPackageName == null) {
                mainPackageName =
                        SymbolUtils.getPackageNameFromManifest(aaptConfig.getManifestFile());
            }

            // Load the main symbol file.
            File mainRTxt = new File(aaptConfig.getSymbolOutputDir(), "R.txt");
            SymbolTable mainSymbols =
                    mainRTxt.isFile()
                            ? SymbolIo.readFromAapt(mainRTxt, mainPackageName)
                            : SymbolTable.builder().tablePackage(mainPackageName).build();

            // For each dependency, load its symbol file.
            Set<SymbolTable> depSymbolTables =
                    SymbolUtils.loadDependenciesSymbolTables(
                            aaptConfig.getLibrarySymbolTableFiles());

            boolean finalIds = true;
            if (aaptConfig.getVariantType().isAar()) {
                finalIds = false;
            }

            RGeneration.generateRForLibraries(mainSymbols, depSymbolTables, sourceOut, finalIds);
        }
    }

    public void generateApkData(
            @NonNull File apkFile,
            @NonNull File outResFolder,
            @NonNull String mainPkgName,
            @NonNull String resName) throws ProcessException, IOException {

        // need to run aapt to get apk information
        BuildToolInfo buildToolInfo = getTargetInfo().getBuildTools();

        String aapt = buildToolInfo.getPath(BuildToolInfo.PathId.AAPT);
        if (aapt == null) {
            throw new IllegalStateException(
                    "Unable to get aapt location from Build Tools " + buildToolInfo.getRevision());
        }

        ApkInfoParser parser = new ApkInfoParser(new File(aapt), mProcessExecutor);
        ApkInfoParser.ApkInfo apkInfo = parser.parseApk(apkFile);

        if (!apkInfo.getPackageName().equals(mainPkgName)) {
            throw new RuntimeException("The main and the micro apps do not have the same package name.");
        }

        String content = String.format(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<wearableApp package=\"%1$s\">\n" +
                        "    <versionCode>%2$s</versionCode>\n" +
                        "    <versionName>%3$s</versionName>\n" +
                        "    <rawPathResId>%4$s</rawPathResId>\n" +
                        "</wearableApp>",
                apkInfo.getPackageName(),
                apkInfo.getVersionCode(),
                apkInfo.getVersionName(),
                resName);

        // xml folder
        File resXmlFile = new File(outResFolder, FD_RES_XML);
        FileUtils.mkdirs(resXmlFile);

        Files.asCharSink(new File(resXmlFile, ANDROID_WEAR_MICRO_APK + DOT_XML), Charsets.UTF_8)
                .write(content);
    }

    public void generateUnbundledWearApkData(
            @NonNull File outResFolder,
            @NonNull String mainPkgName) throws IOException {

        String content = String.format(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<wearableApp package=\"%1$s\">\n" +
                        "    <unbundled />\n" +
                        "</wearableApp>",
                mainPkgName);

        // xml folder
        File resXmlFile = new File(outResFolder, FD_RES_XML);
        FileUtils.mkdirs(resXmlFile);

        Files.asCharSink(new File(resXmlFile, ANDROID_WEAR_MICRO_APK + DOT_XML), Charsets.UTF_8)
                .write(content);
    }

    public static void generateApkDataEntryInManifest(
            int minSdkVersion, int targetSdkVersion, @NonNull File manifestFile)
            throws IOException {

        StringBuilder content = new StringBuilder();
        content.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                .append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n")
                .append("    package=\"${packageName}\">\n")
                .append("    <uses-sdk android:minSdkVersion=\"")
                .append(minSdkVersion)
                .append("\"");
        if (targetSdkVersion != -1) {
            content.append(" android:targetSdkVersion=\"").append(targetSdkVersion).append("\"");
        }
        content.append("/>\n");
        content.append("    <application>\n")
                .append("        <meta-data android:name=\"" + ANDROID_WEAR + "\"\n")
                .append("                   android:resource=\"@xml/" + ANDROID_WEAR_MICRO_APK)
                .append("\" />\n")
                .append("   </application>\n")
                .append("</manifest>\n");

        Files.asCharSink(manifestFile, Charsets.UTF_8).write(content);
    }

    /**
     * Compiles all the shader files found in the given source folders.
     *
     * @param sourceFolder the source folder with the merged shaders
     * @param outputDir the output dir in which to generate the output
     * @throws IOException failed
     */
    public void compileAllShaderFiles(
            @NonNull File sourceFolder,
            @NonNull File outputDir,
            @NonNull List<String> defaultArgs,
            @NonNull Map<String, List<String>> scopedArgs,
            @Nullable File nkdLocation,
            @NonNull ProcessOutputHandler processOutputHandler,
            @NonNull WorkerExecutorFacade workers)
            throws IOException {
        checkNotNull(sourceFolder, "sourceFolder cannot be null.");
        checkNotNull(outputDir, "outputDir cannot be null.");
        checkState(getTargetInfo() != null,
                "Cannot call compileAllShaderFiles() before setTargetInfo() is called.");

        Supplier<ShaderProcessor> processor =
                () ->
                        new ShaderProcessor(
                                nkdLocation,
                                sourceFolder,
                                outputDir,
                                defaultArgs,
                                scopedArgs,
                                mProcessExecutor,
                                processOutputHandler,
                                workers);

        DirectoryWalker.builder()
                .root(sourceFolder.toPath())
                .extensions(
                        ShaderProcessor.EXT_VERT,
                        ShaderProcessor.EXT_TESC,
                        ShaderProcessor.EXT_TESE,
                        ShaderProcessor.EXT_GEOM,
                        ShaderProcessor.EXT_FRAG,
                        ShaderProcessor.EXT_COMP)
                .action(processor)
                .build()
                .walk();
    }

    /**
     * Compiles all the renderscript files found in the given source folders.
     *
     * <p>Right now this is the only way to compile them as the renderscript compiler requires all
     * renderscript files to be passed for all compilation.
     *
     * <p>Therefore whenever a renderscript file or header changes, all must be recompiled.
     *
     * @param sourceFolders all the source folders to find files to compile
     * @param importFolders all the import folders.
     * @param sourceOutputDir the output dir in which to generate the source code
     * @param resOutputDir the output dir in which to generate the bitcode file
     * @param targetApi the target api
     * @param debugBuild whether the build is debug
     * @param optimLevel the optimization level
     * @param ndkMode whether the renderscript code should be compiled to generate C/C++ bindings
     * @param supportMode support mode flag to generate .so files.
     * @param useAndroidX whether to use AndroidX dependencies
     * @param abiFilters ABI filters in case of support mode
     * @throws IOException failed
     * @throws InterruptedException failed
     */
    public void compileAllRenderscriptFiles(
            @NonNull Collection<File> sourceFolders,
            @NonNull Collection<File> importFolders,
            @NonNull File sourceOutputDir,
            @NonNull File resOutputDir,
            @NonNull File objOutputDir,
            @NonNull File libOutputDir,
            int targetApi,
            boolean debugBuild,
            int optimLevel,
            boolean ndkMode,
            boolean supportMode,
            boolean useAndroidX,
            @Nullable Set<String> abiFilters,
            @NonNull ProcessOutputHandler processOutputHandler)
            throws InterruptedException, ProcessException, IOException {
        checkNotNull(sourceFolders, "sourceFolders cannot be null.");
        checkNotNull(importFolders, "importFolders cannot be null.");
        checkNotNull(sourceOutputDir, "sourceOutputDir cannot be null.");
        checkNotNull(resOutputDir, "resOutputDir cannot be null.");
        checkState(getTargetInfo() != null,
                "Cannot call compileAllRenderscriptFiles() before setTargetInfo() is called.");

        BuildToolInfo buildToolInfo = getTargetInfo().getBuildTools();

        String renderscript = buildToolInfo.getPath(BuildToolInfo.PathId.LLVM_RS_CC);
        if (renderscript == null || !new File(renderscript).isFile()) {
            throw new IllegalStateException("llvm-rs-cc is missing");
        }

        RenderScriptProcessor processor =
                new RenderScriptProcessor(
                        sourceFolders,
                        importFolders,
                        sourceOutputDir,
                        resOutputDir,
                        objOutputDir,
                        libOutputDir,
                        buildToolInfo,
                        targetApi,
                        debugBuild,
                        optimLevel,
                        ndkMode,
                        supportMode,
                        useAndroidX,
                        abiFilters,
                        mLogger);
        processor.build(mProcessExecutor, processOutputHandler);
    }

    /**
     * Creates a new split APK containing only code.
     *
     * <p>This is used for instant run cold swaps on N and above.
     */
    public void packageCodeSplitApk(
            @NonNull File androidResPkg,
            @NonNull Set<File> dexFiles,
            @Nullable SigningConfig signingConfig,
            @NonNull File outApkLocation,
            @NonNull File incrementalDir,
            @NonNull ApkCreatorFactory apkCreatorFactory)
            throws KeytoolException, PackagerException, IOException {
        packageCodeSplitApk(
                androidResPkg,
                dexFiles,
                signingConfig,
                outApkLocation,
                incrementalDir,
                apkCreatorFactory,
                mCreatedBy);
    }

    /**
     * Creates a new split APK containing only code.
     *
     * <p>This is used for instant run cold swaps on N and above.
     */
    public static void packageCodeSplitApk(
            @NonNull File androidResPkg,
            @NonNull Set<File> dexFiles,
            @Nullable SigningConfig signingConfig,
            @NonNull File outApkLocation,
            @NonNull File incrementalDir,
            @NonNull ApkCreatorFactory apkCreatorFactory,
            @Nullable String createdBy)
            throws KeytoolException, PackagerException, IOException {

        Optional<SigningOptions> signingOptions;

        if (signingConfig != null && signingConfig.isSigningReady()) {
            CertificateInfo certificateInfo = KeystoreHelper.getCertificateInfo(
                    signingConfig.getStoreType(),
                    Preconditions.checkNotNull(signingConfig.getStoreFile()),
                    Preconditions.checkNotNull(signingConfig.getStorePassword()),
                    Preconditions.checkNotNull(signingConfig.getKeyPassword()),
                    Preconditions.checkNotNull(signingConfig.getKeyAlias()));
            signingOptions =
                    Optional.of(
                            SigningOptions.builder()
                                    .setKey(certificateInfo.getKey())
                                    .setCertificates(certificateInfo.getCertificate())
                                    .setV1SigningEnabled(signingConfig.isV1SigningEnabled())
                                    .setV2SigningEnabled(signingConfig.isV2SigningEnabled())
                                    .setMinSdkVersion(API_LEVEL_SPLIT_APK)
                                    .build());
        } else {
            signingOptions = Optional.absent();
        }

        ApkCreatorFactory.CreationData creationData =
                new ApkCreatorFactory.CreationData(
                        outApkLocation,
                        signingOptions,
                        null,
                        createdBy,
                        NativeLibrariesPackagingMode.COMPRESSED,
                        s -> false);

        try (IncrementalPackager packager = new IncrementalPackager(
                creationData,
                incrementalDir,
                apkCreatorFactory,
                new HashSet<>(),
                true)) {
            ImmutableMap<RelativeFile, FileStatus> androidResources =
                    IncrementalRelativeFileSets.fromZip(androidResPkg);
            packager.updateAndroidResources(androidResources);
            for (File dexFile : dexFiles) {
                RelativeFile dex = new RelativeFile(dexFile.getParentFile(), dexFile);
                packager.updateDex(ImmutableMap.of(dex, FileStatus.NEW));
            }
        }
    }

    /**
     * Obtains the "created by" tag for the packaged manifest.
     *
     * @return the "created by" tag or {@code null} if no tag was defined
     */
    @Nullable
    public String getCreatedBy() {
        return mCreatedBy;
    }
}
