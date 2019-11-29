/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.android.SdkConstants.CURRENT_PLATFORM;
import static com.android.SdkConstants.PLATFORM_WINDOWS;
import static com.android.build.gradle.internal.cxx.configure.ConstantsKt.CXX_DEFAULT_CONFIGURATION_SUBFOLDER;
import static com.android.build.gradle.internal.cxx.configure.ConstantsKt.CXX_LOCAL_PROPERTIES_CACHE_DIR;
import static com.android.build.gradle.internal.cxx.configure.GradleLocalPropertiesKt.gradleLocalProperties;
import static com.android.build.gradle.internal.cxx.configure.JsonGenerationAbiConfigurationKt.createJsonGenerationAbiConfiguration;
import static com.android.build.gradle.internal.cxx.configure.LoggingEnvironmentKt.error;
import static com.android.build.gradle.internal.cxx.configure.LoggingEnvironmentKt.info;
import static com.android.build.gradle.internal.cxx.configure.LoggingEnvironmentKt.warn;
import static com.android.build.gradle.internal.cxx.configure.NativeBuildSystemVariantConfigurationKt.createNativeBuildSystemVariantConfig;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.external.cmake.CmakeUtils;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.cxx.configure.AbiConfigurator;
import com.android.build.gradle.internal.cxx.configure.CmakeLocatorKt;
import com.android.build.gradle.internal.cxx.configure.GradleSyncLoggingEnvironment;
import com.android.build.gradle.internal.cxx.configure.JsonGenerationAbiConfiguration;
import com.android.build.gradle.internal.cxx.configure.JsonGenerationInvalidationState;
import com.android.build.gradle.internal.cxx.configure.JsonGenerationVariantConfiguration;
import com.android.build.gradle.internal.cxx.configure.NativeBuildSystemVariantConfig;
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons;
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValueMini;
import com.android.build.gradle.internal.cxx.json.NativeLibraryValueMini;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.model.CoreExternalNativeBuild;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.profile.AnalyticsUtil;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.ApiVersion;
import com.android.builder.profile.ProcessProfileWriter;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.repository.Revision;
import com.android.sdklib.AndroidVersion;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFiles;

/**
 * Base class for generation of native JSON.
 */
public abstract class ExternalNativeJsonGenerator {
    @NonNull final JsonGenerationVariantConfiguration config;
    @NonNull final Set<String> configurationFailures;
    @NonNull protected final AndroidBuilder androidBuilder;
    @NonNull protected final GradleBuildVariant.Builder stats;

    ExternalNativeJsonGenerator(
            @NonNull JsonGenerationVariantConfiguration config,
            @NonNull Set<String> configurationFailures,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull GradleBuildVariant.Builder stats) {
        this.config = config;
        this.configurationFailures = configurationFailures;
        this.androidBuilder = androidBuilder;
        this.stats = stats;

        // Check some basic configuration information at sync time.
        if (!getNdkFolder().isDirectory()) {
            error(
                    "NDK not configured (%s).\n"
                            + "Download the NDK from http://developer.android.com/tools/sdk/ndk/."
                            + "Then add ndk.dir=path/to/ndk in local.properties.\n"
                            + "(On Windows, make sure you escape backslashes, "
                            + "e.g. C:\\\\ndk rather than C:\\ndk)",
                    getNdkFolder());
        }
    }

    /**
     * Returns true if platform is windows
     */
    protected static boolean isWindows() {
        return (CURRENT_PLATFORM == PLATFORM_WINDOWS);
    }


    @NonNull
    private List<File> getDependentBuildFiles(@NonNull File json) throws IOException {
        List<File> result = Lists.newArrayList();
        if (!json.exists()) {
            return result;
        }

        // Now check whether the JSON is out-of-date with respect to the build files it declares.
        NativeBuildConfigValueMini config =
                AndroidBuildGradleJsons.getNativeBuildMiniConfig(json, stats);
        return config.buildFiles;
    }

    public void build() throws IOException, ProcessException {
        buildAndPropagateException(false);
    }

    public void build(boolean forceJsonGeneration) {
        try {
            info("building json with force flag %s", forceJsonGeneration);
            buildAndPropagateException(forceJsonGeneration);
        } catch (@NonNull IOException | GradleException e) {
            error("exception while building Json $%s", e.getMessage());
        } catch (ProcessException e) {
            error(
                    "executing external native build for %s %s",
                    getNativeBuildSystem().getName(), config.makefile);
        }
    }

    public List<Callable<Void>> parallelBuild(boolean forceJsonGeneration) {
        List<Callable<Void>> buildSteps = new ArrayList<>(config.abiConfigurations.size());
        for (JsonGenerationAbiConfiguration configuration : config.abiConfigurations) {
            buildSteps.add(
                    () ->
                            buildForOneConfigurationConvertExceptions(
                                    forceJsonGeneration, configuration));
        }
        return buildSteps;
    }

    @Nullable
    private Void buildForOneConfigurationConvertExceptions(
            boolean forceJsonGeneration, JsonGenerationAbiConfiguration configuration) {
        try (GradleSyncLoggingEnvironment ignore =
                new GradleSyncLoggingEnvironment(
                        getVariantName(),
                        configuration.getAbiName(),
                        configurationFailures,
                        androidBuilder.getIssueReporter(),
                        androidBuilder.getLogger())) {
            try {
                buildForOneConfiguration(forceJsonGeneration, configuration);
            } catch (@NonNull IOException | GradleException e) {
                error("exception while building Json %s", e.getMessage());
            } catch (ProcessException e) {
                error(
                        "executing external native build for %s %s",
                        getNativeBuildSystem().getName(), config.makefile);
            }
            return null;
        }
    }

    @NonNull
    private static String getPreviousBuildCommand(@NonNull File commandFile) throws IOException {
        if (!commandFile.exists()) {
            return "";
        }
        return new String(Files.readAllBytes(commandFile.toPath()), Charsets.UTF_8);
    }

    private void buildAndPropagateException(boolean forceJsonGeneration)
            throws IOException, ProcessException {
        Exception firstException = null;
        for (JsonGenerationAbiConfiguration configuration : config.abiConfigurations) {
            try {
                buildForOneConfiguration(forceJsonGeneration, configuration);
            } catch (@NonNull GradleException | IOException | ProcessException e) {
                if (firstException == null) {
                    firstException = e;
                }
            }
        }

        if (firstException != null) {
            if (firstException instanceof GradleException) {
                throw (GradleException) firstException;
            }

            if (firstException instanceof IOException) {
                throw (IOException) firstException;
            }

            throw (ProcessException) firstException;
        }
    }

    public void buildForOneAbiName(boolean forceJsonGeneration, String abiName) {
        int built = 0;
        for (JsonGenerationAbiConfiguration configuration : config.abiConfigurations) {
            if (!configuration.getAbi().getName().equals(abiName)) {
                continue;
            }
            built++;
            buildForOneConfigurationConvertExceptions(forceJsonGeneration, configuration);
        }
        assert (built == 1);
    }

    private void checkForConfigurationErrors() {
        if (!configurationFailures.isEmpty()) {
            throw new GradleException(Joiner.on("\r\n").join(configurationFailures));
        }
    }

    private void buildForOneConfiguration(
            boolean forceJsonGeneration, JsonGenerationAbiConfiguration configuration)
            throws GradleException, IOException, ProcessException {
        checkForConfigurationErrors();

        GradleBuildVariant.NativeBuildConfigInfo.Builder variantStats =
                GradleBuildVariant.NativeBuildConfigInfo.newBuilder();
        variantStats.setAbi(AnalyticsUtil.getAbi(configuration.getAbiName()));
        variantStats.setDebuggable(config.debuggable);
        long startTime = System.currentTimeMillis();
        variantStats.setGenerationStartMs(startTime);
        try {
            info(
                    "Start JSON generation. Platform version: %s min SDK version: %s",
                    configuration.getAbiPlatformVersion(),
                    configuration.getAbiName(),
                    configuration.getAbiPlatformVersion());
            ProcessInfoBuilder processBuilder = getProcessBuilder(configuration);

            // See whether the current build command matches a previously written build command.
            String currentBuildCommand = processBuilder.toString();

            JsonGenerationInvalidationState invalidationState =
                    new JsonGenerationInvalidationState(
                            forceJsonGeneration,
                            configuration.getJsonFile(),
                            configuration.getBuildCommandFile(),
                            currentBuildCommand,
                            getPreviousBuildCommand(configuration.getBuildCommandFile()),
                            getDependentBuildFiles(configuration.getJsonFile()));

            if (invalidationState.getRebuild()) {
                info("rebuilding JSON %s due to:", configuration.getJsonFile());
                for (String reason : invalidationState.getRebuildReasons()) {
                    info(reason);
                }

                // Related to https://issuetracker.google.com/69408798
                // Something has changed so we need to clean up some build intermediates and
                // outputs.
                // - If only a build file has changed then we try to keep .o files and,
                // in the case of CMake, the generated Ninja project. In this case we must
                // remove .so files because they are automatically packaged in the APK on a
                // *.so basis.
                // - If there is some other cause to recreate the JSon, such as command-line
                // changed then wipe out the whole JSon folder.
                if (config.jsonFolder.exists()) {
                    if (invalidationState.getSoftRegeneration()) {
                        info(
                                "keeping json folder '%s' but regenerating project",
                                configuration.getExternalNativeBuildFolder());

                    } else {
                        info(
                                "removing stale contents from '%s'",
                                configuration.getExternalNativeBuildFolder());
                        FileUtils.deletePath(configuration.getExternalNativeBuildFolder());
                    }
                }

                if (configuration.getExternalNativeBuildFolder().mkdirs()) {
                    info("created folder '%s'", configuration.getExternalNativeBuildFolder());
                }

                info("executing %s %s", getNativeBuildSystem().getName(), processBuilder);
                String buildOutput = executeProcess(configuration);
                info("done executing %s", getNativeBuildSystem().getName());

                // Write the captured process output to a file for diagnostic purposes.
                info("write build output %s", configuration.getBuildOutputFile().getAbsolutePath());
                Files.write(
                        configuration.getBuildOutputFile().toPath(),
                        buildOutput.getBytes(Charsets.UTF_8));
                processBuildOutput(buildOutput, configuration);

                if (!configuration.getJsonFile().exists()) {
                    throw new GradleException(
                            String.format(
                                    "Expected json generation to create '%s' but it didn't",
                                    configuration.getJsonFile()));
                }

                synchronized (stats) {
                    // Related to https://issuetracker.google.com/69408798
                    // Targets may have been removed or there could be other orphaned extra .so
                    // files. Remove these and rely on the build step to replace them if they are
                    // legitimate. This is to prevent unexpected .so files from being packaged in
                    // the APK.
                    removeUnexpectedSoFiles(
                            configuration.getObjFolder(),
                            AndroidBuildGradleJsons.getNativeBuildMiniConfig(
                                    configuration.getJsonFile(), stats));
                }

                // Write the ProcessInfo to a file, this has all the flags used to generate the
                // JSON. If any of these change later the JSON will be regenerated.
                info(
                        "write command file %s",
                        configuration.getBuildCommandFile().getAbsolutePath());
                Files.write(
                        configuration.getBuildCommandFile().toPath(),
                        currentBuildCommand.getBytes(Charsets.UTF_8));

                // Record the outcome. JSON was built.
                variantStats.setOutcome(
                        GradleBuildVariant.NativeBuildConfigInfo.GenerationOutcome.SUCCESS_BUILT);
            } else {
                info("JSON '%s' was up-to-date", configuration.getJsonFile());
                variantStats.setOutcome(
                        GradleBuildVariant.NativeBuildConfigInfo.GenerationOutcome
                                .SUCCESS_UP_TO_DATE);
            }
            info("JSON generation completed without problems");
        } catch (@NonNull GradleException | IOException | ProcessException e) {
            variantStats.setOutcome(
                    GradleBuildVariant.NativeBuildConfigInfo.GenerationOutcome.FAILED);
            info("JSON generation completed with problem. Exception: " + e.toString());
            throw e;
        } finally {
            variantStats.setGenerationDurationMs(System.currentTimeMillis() - startTime);
            synchronized (stats) {
                stats.addNativeBuildConfig(variantStats);
            }
        }
    }

    /**
     * This function removes unexpected so files from disk. Unexpected means they exist on disk but
     * are not present as a build output from the json.
     *
     * <p>It is generally valid for there to be extra .so files because the build system may copy
     * libraries to the output folder. This function is meant to be used in cases where we suspect
     * the .so may have been orphaned by the build system due to a change in build files.
     *
     * @param expectedOutputFolder the expected location of output .so files
     * @param config the existing miniconfig
     * @throws IOException in the case that json is missing or can't be read or some other IO
     *     problem.
     */
    private static void removeUnexpectedSoFiles(
            @NonNull File expectedOutputFolder, @NonNull NativeBuildConfigValueMini config)
            throws IOException {
        if (!expectedOutputFolder.isDirectory()) {
            // Nothing to clean
            return;
        }

        // Gather all expected build outputs
        List<Path> expectedSoFiles = Lists.newArrayList();
        for (NativeLibraryValueMini library : config.libraries.values()) {
            assert library.output != null;
            expectedSoFiles.add(library.output.toPath());
        }

        try (Stream<Path> paths = Files.walk(expectedOutputFolder.toPath())) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".so"))
                    .filter(path -> !expectedSoFiles.contains(path))
                    .forEach(
                            path -> {
                                if (path.toFile().delete()) {
                                    info(
                                            "deleted unexpected build output %s in incremental "
                                                    + "regenerate",
                                            path);
                                }
                            });
        }
    }

    /**
     * Derived class implements this method to post-process build output. Ndk-build uses this to
     * capture and analyze the compile and link commands that were written to stdout.
     */
    abstract void processBuildOutput(
            @NonNull String buildOutput, @NonNull JsonGenerationAbiConfiguration abiConfig)
            throws IOException;

    @NonNull
    abstract ProcessInfoBuilder getProcessBuilder(
            @NonNull JsonGenerationAbiConfiguration abiConfig);

    /**
     * Executes the JSON generation process. Return the combination of STDIO and STDERR from running
     * the process.
     *
     * @return Returns the combination of STDIO and STDERR from running the process.
     */
    abstract String executeProcess(@NonNull JsonGenerationAbiConfiguration abiConfig)
            throws ProcessException, IOException;

    /**
     * @return the native build system that is used to generate the JSON.
     */
    @NonNull
    public abstract NativeBuildSystem getNativeBuildSystem();

    /**
     * @return a map of Abi to STL shared object (.so files) that should be copied.
     */
    @NonNull
    abstract Map<Abi, File> getStlSharedObjectFiles();

    /** @return the variant name for this generator */
    @NonNull
    public String getVariantName() {
        return config.variantName;
    }

    @NonNull
    public static ExternalNativeJsonGenerator create(
            @NonNull File rootBuildGradlePath,
            @NonNull String projectPath,
            @NonNull File projectDir,
            @NonNull File buildDir,
            @Nullable File externalNativeBuildDir,
            @NonNull NativeBuildSystem buildSystem,
            @NonNull File makefile,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull SdkHandler sdkHandler,
            @NonNull VariantScope scope) {
        Set<String> configurationFailures = new HashSet<>();
        try (GradleSyncLoggingEnvironment ignore =
                new GradleSyncLoggingEnvironment(
                        scope.getFullVariantName(),
                        "native",
                        configurationFailures,
                        androidBuilder.getIssueReporter(),
                        androidBuilder.getLogger())) {
            return createImpl(
                    configurationFailures,
                    rootBuildGradlePath,
                    projectPath,
                    projectDir,
                    buildDir,
                    externalNativeBuildDir,
                    buildSystem,
                    makefile,
                    androidBuilder,
                    sdkHandler,
                    scope);
        }
    }

    @NonNull
    public static ExternalNativeJsonGenerator createImpl(
            @NonNull Set<String> configurationFailures,
            @NonNull File rootBuildGradlePath,
            @NonNull String projectPath,
            @NonNull File projectDir,
            @NonNull File buildDir,
            @Nullable File externalNativeBuildDir,
            @NonNull NativeBuildSystem buildSystem,
            @NonNull File makefile,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull SdkHandler sdkHandler,
            @NonNull VariantScope scope) {
        checkNotNull(sdkHandler.getSdkFolder(), "No Android SDK folder found");
        GlobalScope globalScope = scope.getGlobalScope();
        NdkHandler ndkHandler = globalScope.getNdkHandler();
        File ndkFolder = ndkHandler.getNdkDirectory();
        if (ndkFolder == null || !ndkFolder.exists() || !ndkFolder.isDirectory()) {
            sdkHandler.installNdk(ndkHandler);
            ndkFolder = sdkHandler.getNdkFolder();
            if (ndkFolder == null || !ndkFolder.exists() || !ndkFolder.isDirectory()) {
                throw new InvalidUserDataException(
                        String.format(
                                "NDK not configured. %s\n" + "Download it with SDK manager.",
                                ndkFolder == null ? "" : ndkFolder));
            }
        }
        BaseVariantData variantData = scope.getVariantData();
        GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
        GradleBuildVariant.Builder stats =
                ProcessProfileWriter.getOrCreateVariant(projectPath, scope.getFullVariantName());
        File intermediates =
                FileUtils.join(
                        globalScope.getIntermediatesDir(),
                        buildSystem.getName(),
                        variantData.getVariantConfiguration().getDirName());

        File soFolder = new File(intermediates, "lib");
        File externalNativeBuildFolder =
                findExternalNativeBuildFolder(
                        projectDir,
                        buildSystem,
                        variantData.getName(),
                        buildDir,
                        externalNativeBuildDir);

        File objFolder = new File(intermediates, "obj");

        // Get the highest platform version below compileSdkVersion

        ApiVersion minSdkVersion =
                variantData.getVariantConfiguration().getMergedFlavor().getMinSdkVersion();
        NativeBuildSystemVariantConfig nativeBuildVariantConfig =
                createNativeBuildSystemVariantConfig(
                        buildSystem, variantData.getVariantConfiguration());

        Splits splits = globalScope.getExtension().getSplits();

        if (globalScope.getExtension().getGeneratePureSplits()
                && splits.getAbi().isUniversalApk()) {
            warn(
                    "ABI based configuration splits"
                            + " and universal APK cannot be both set, universal APK will not be build.");
        }

        ProjectOptions projectOptions = globalScope.getProjectOptions();
        AbiConfigurator abiConfigurator =
                new AbiConfigurator(
                        ndkHandler.getSupportedAbis(),
                        ndkHandler.getDefaultAbis(),
                        nativeBuildVariantConfig.externalNativeBuildAbiFilters,
                        nativeBuildVariantConfig.ndkAbiFilters,
                        splits.getAbiFilters(),
                        projectOptions.get(BooleanOption.BUILD_ONLY_TARGET_ABI),
                        projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI));

        // These are ABIs that are available on the current platform
        Collection<Abi> validAbis = abiConfigurator.getValidAbis();

        // Produce the list of expected JSON files. This list includes possibly invalid ABIs
        // so that generator can create fallback JSON for them.
        List<File> expectedJsons =
                ExternalNativeBuildTaskUtils.getOutputJsons(
                        externalNativeBuildFolder, abiConfigurator.getAllAbis());

        // Set up per-abi configuration constants
        if (buildSystem == NativeBuildSystem.NDK_BUILD) {
            // ndk-build create libraries in a "local" subfolder.
            objFolder = new File(objFolder, "local");
        }
        List<JsonGenerationAbiConfiguration> abiConfigurations = Lists.newArrayList();
        for (Abi abi : validAbis) {
            AndroidVersion version =
                    minSdkVersion == null
                            ? null
                            : new AndroidVersion(
                                    minSdkVersion.getApiLevel(), minSdkVersion.getCodename());

            abiConfigurations.add(
                    createJsonGenerationAbiConfiguration(
                            abi,
                            variantData.getName(),
                            externalNativeBuildFolder.getParentFile().getParentFile(),
                            objFolder,
                            buildSystem,
                            ndkHandler.findSuitablePlatformVersion(
                                    abi.getName(), variantData.getName(), version)));
        }

        assert ndkHandler.getRevision() != null;
        File cacheFolder = new File(rootBuildGradlePath, CXX_DEFAULT_CONFIGURATION_SUBFOLDER);
        Properties localProperties = gradleLocalProperties(rootBuildGradlePath);
        String userSpecifiedCxxCacheFolder =
                localProperties.getProperty(CXX_LOCAL_PROPERTIES_CACHE_DIR);
        if (userSpecifiedCxxCacheFolder != null) {
            cacheFolder = new File(userSpecifiedCxxCacheFolder);
        }

        JsonGenerationVariantConfiguration config =
                new JsonGenerationVariantConfiguration(
                        rootBuildGradlePath,
                        nativeBuildVariantConfig,
                        variantData.getName(),
                        makefile,
                        sdkHandler.getSdkFolder(),
                        sdkHandler.getNdkFolder(),
                        soFolder,
                        objFolder,
                        externalNativeBuildFolder,
                        variantConfig.getBuildType().isDebuggable(),
                        abiConfigurations,
                        ndkHandler.getRevision(),
                        expectedJsons,
                        cacheFolder,
                        globalScope
                                .getProjectOptions()
                                .get(BooleanOption.ENABLE_NATIVE_COMPILER_SETTINGS_CACHE));

        switch (buildSystem) {
            case NDK_BUILD:
                return new NdkBuildExternalNativeJsonGenerator(
                        config, configurationFailures, androidBuilder, projectDir, stats);
            case CMAKE:
                return createCmakeExternalNativeJsonGenerator(
                        config,
                        configurationFailures,
                        variantData,
                        sdkHandler,
                        androidBuilder,
                        stats);
            default:
                throw new IllegalArgumentException("Unknown ExternalNativeJsonGenerator type");
        }
    }


    /**
     * @return creates an instance of CmakeExternalNativeJsonGenerator (server or android-ninja)
     *     based on the version of the cmake.
     */
    private static ExternalNativeJsonGenerator createCmakeExternalNativeJsonGenerator(
            @NonNull JsonGenerationVariantConfiguration config,
            @NonNull Set<String> configurationFailures,
            @NonNull BaseVariantData variantData,
            @NonNull SdkHandler sdkHandler,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull GradleBuildVariant.Builder stats) {
        AndroidConfig extension = variantData.getScope().getGlobalScope().getExtension();
        CoreExternalNativeBuild externalNativeBuild = extension.getExternalNativeBuild();

        File cmakeFolder;
        if (variantData
                .getScope()
                .getGlobalScope()
                .getProjectOptions()
                .get(BooleanOption.ENABLE_SIDE_BY_SIDE_CMAKE)) {
            cmakeFolder =
                    CmakeLocatorKt.findCmakePath(
                            externalNativeBuild.getCmake().getVersion(),
                            sdkHandler,
                            androidBuilder.getLogger());

        } else {
            cmakeFolder =
                    ExternalNativeBuildTaskUtils.findCmakeExecutableFolder(
                            Objects.requireNonNull(externalNativeBuild.getCmake().getVersion()),
                            sdkHandler);
        }

        Revision cmakeVersion;
        try {
            cmakeVersion = CmakeUtils.getVersion(new File(cmakeFolder, "bin"));
        } catch (IOException e) {
            // For pre-ENABLE_SIDE_BY_SIDE_CMAKE case, the text of this message triggers
            // Android Studio to prompt for download.
            // Post-ENABLE_SIDE_BY_SIDE different messages may be thrown from
            // CmakeLocatorKt.findCmakePath to trigger download of particular versions of
            // CMake from the SDK.
            throw new RuntimeException(
                    "Unable to get the CMake version located at: "
                            + (new File(cmakeFolder, "bin")).getAbsolutePath());
        }

        return CmakeExternalNativeJsonGeneratorFactory.createCmakeStrategy(
                config,
                configurationFailures,
                cmakeVersion,
                androidBuilder,
                Objects.requireNonNull(cmakeFolder),
                stats);
    }

    /**
     * Find's the location of the build-system output folder. For example,
     * .externalNativeBuild/cmake/debug/x86/
     *
     * <p>If user specific externalNativeBuild.cmake.buildStagingFolder = 'xyz' then that folder
     * will be used instead of the default of app/.externalNativeBuild.
     *
     * <p>If the resulting build output folder would be inside of app/build then issue an error
     * because app/build will be deleted when the user does clean and that will lead to undefined
     * behavior.
     *
     * @param projectDir folder of app/build.gradle
     * @param buildSystem cmake or ndk-build
     * @param variantName the name of the variant like 'debug'
     * @param buildDir the folder of app/build which holds build outputs
     * @param externalNativeBuildDir the user-defined substitute folder
     * @return path to the folder to hold native build outputs
     */
    private static File findExternalNativeBuildFolder(
            @NonNull File projectDir,
            @NonNull NativeBuildSystem buildSystem,
            @NonNull String variantName,
            @NonNull File buildDir,
            @Nullable File externalNativeBuildDir) {
        File externalNativeBuildPath;
        if (externalNativeBuildDir == null) {
            return FileUtils.join(
                    projectDir, ".externalNativeBuild", buildSystem.getName(), variantName);
        }

        externalNativeBuildPath =
                FileUtils.join(externalNativeBuildDir, buildSystem.getName(), variantName);

        if (FileUtils.isFileInDirectory(externalNativeBuildPath, buildDir)) {
            File invalidPath = externalNativeBuildPath;
            externalNativeBuildPath =
                    FileUtils.join(
                            projectDir, ".externalNativeBuild", buildSystem.getName(), variantName);
            error(
                    "The build staging directory you specified ('%s')"
                            + " is a subdirectory of your project's temporary build directory ('%s')."
                            + "Files in this directory do not persist through clean builds.\n"
                            + "Either use the default build staging directory ('%s'),"
                            + "or specify a path outside the temporary build directory.",
                    invalidPath.getAbsolutePath(),
                    buildDir.getAbsolutePath(),
                    externalNativeBuildPath.getAbsolutePath());
        }

        return externalNativeBuildPath;
    }

    public void forEachNativeBuildConfiguration(@NonNull Consumer<JsonReader> callback)
            throws IOException {
        try (GradleSyncLoggingEnvironment ignore =
                new GradleSyncLoggingEnvironment(
                        getVariantName(),
                        "native",
                        configurationFailures,
                        androidBuilder.getIssueReporter(),
                        androidBuilder.getLogger())) {
            List<File> files = getNativeBuildConfigurationsJsons();
            info("streaming %s JSON files", files.size());
            for (File file : getNativeBuildConfigurationsJsons()) {
                if (file.exists()) {
                    info("string JSON file %s", file.getAbsolutePath());
                    try (JsonReader reader = new JsonReader(new FileReader(file))) {
                        callback.accept(reader);
                    } catch (Throwable e) {
                        info(
                                "Error parsing: %s",
                                String.join("\r\n", Files.readAllLines(file.toPath())));
                        throw e;
                    }
                } else {
                    // If the tool didn't create the JSON file then create fallback with the
                    // information we have so the user can see partial information in the UI.
                    info("streaming fallback JSON for %s", file.getAbsolutePath());
                    NativeBuildConfigValueMini fallback = new NativeBuildConfigValueMini();
                    fallback.buildFiles = Lists.newArrayList(config.makefile);
                    try (JsonReader reader =
                            new JsonReader(new StringReader(new Gson().toJson(fallback)))) {
                        callback.accept(reader);
                    }
                }
            }
        }
    }

    @NonNull
    public JsonGenerationVariantConfiguration getConfig() {
        return this.config;
    }

    @NonNull
    @InputFile
    public File getMakefile() {
        return config.makefile;
    }

    @NonNull
    @Input // We don't need contents of the files in the generated JSON, just the path.
    public File getObjFolder() {
        return config.objFolder;
    }

    @NonNull
    // This should not be annotated with @OutputDirectory because getNativeBuildConfigurationsJsons
    // is already annotated with @OutputFiles
    public File getJsonFolder() {
        return config.jsonFolder;
    }

    @NonNull
    @Input // We don't need contents of the files in the generated JSON, just the path.
    public File getNdkFolder() {
        return config.ndkFolder;
    }

    @Input
    public boolean isDebuggable() {
        return config.debuggable;
    }

    @NonNull
    @Optional
    @Input
    public List<String> getBuildArguments() {
        return config.buildSystem.arguments;
    }

    @NonNull
    @Optional
    @Input
    public List<String> getcFlags() {
        return config.buildSystem.cFlags;
    }

    @NonNull
    @Optional
    @Input
    public List<String> getCppFlags() {
        return config.buildSystem.cppFlags;
    }

    @NonNull
    @OutputFiles
    public List<File> getNativeBuildConfigurationsJsons() {
        return config.generatedJsonFiles;
    }

    @NonNull
    @Input // We don't need contents of the files in the generated JSON, just the path.
    public File getSoFolder() {
        return config.soFolder;
    }

    @NonNull
    @Input // We don't need contents of the files in the generated JSON, just the path.
    public File getSdkFolder() {
        return config.sdkFolder;
    }

    @Input
    @NonNull
    public Collection<Abi> getAbis() {
        List<Abi> result = Lists.newArrayList();
        for (JsonGenerationAbiConfiguration configuration : config.abiConfigurations) {
            result.add(configuration.getAbi());
        }
        return result;
    }

}
