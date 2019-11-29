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

import static com.android.build.gradle.internal.cxx.configure.CmakeAndroidGradleBuildExtensionsKt.wrapCmakeListsForCompilerSettingsCaching;
import static com.android.build.gradle.internal.cxx.configure.CmakeAndroidGradleBuildExtensionsKt.writeCompilerSettingsToCache;
import static com.android.build.gradle.internal.cxx.configure.LoggingEnvironmentKt.error;
import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.cxx.configure.JsonGenerationAbiConfiguration;
import com.android.build.gradle.internal.cxx.configure.JsonGenerationVariantConfiguration;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CMake JSON generation logic. This is separated from the corresponding CMake task so that JSON can
 * be generated during configuration.
 */
abstract class CmakeExternalNativeJsonGenerator extends ExternalNativeJsonGenerator {
    private static final Pattern cmakeFileFinder =
            Pattern.compile("^(.*CMake (Error|Warning).* at\\s+)([^:]+)(:.*)$", Pattern.DOTALL);

    @NonNull final File cmakeInstallFolder;

    CmakeExternalNativeJsonGenerator(
            @NonNull JsonGenerationVariantConfiguration config,
            @NonNull Set<String> configurationFailures,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull File cmakeInstallFolder,
            @NonNull GradleBuildVariant.Builder stats) {
        super(config, configurationFailures, androidBuilder, stats);
        this.cmakeInstallFolder = cmakeInstallFolder;
        this.stats.setNativeBuildSystemType(GradleNativeAndroidModule.NativeBuildSystemType.CMAKE);

        // Check some basic requirements. This code executes at sync time but any call to
        // recordConfigurationError will later cause the generation of json to fail.
        File cmakelists = getMakefile();
        if (cmakelists.isDirectory()) {
            error(
                    "Gradle project cmake.path %s is a folder. It must be CMakeLists.txt",
                    cmakelists);
        } else if (cmakelists.isFile()) {
            String filename = cmakelists.getName();
            if (!filename.equals("CMakeLists.txt")) {
                error(
                        "Gradle project cmake.path specifies %s but it must be CMakeLists.txt",
                        filename);
            }
        } else {
            error("Gradle project cmake.path is %s but that file doesn't exist", cmakelists);
        }
    }

    /**
     * Returns the cache arguments for implemented strategy.
     *
     * @return Returns the cache arguments
     */
    @NonNull
    abstract List<String> getCacheArguments(@NonNull JsonGenerationAbiConfiguration abiConfig);

    /**
     * Executes the JSON generation process. Return the combination of STDIO and STDERR from running
     * the process.
     *
     * @return Returns the combination of STDIO and STDERR from running the process.
     */
    @NonNull
    public abstract String executeProcessAndGetOutput(
            @NonNull JsonGenerationAbiConfiguration abiConfig) throws ProcessException, IOException;

    @NonNull
    @Override
    public String executeProcess(@NonNull JsonGenerationAbiConfiguration abiConfig)
            throws ProcessException, IOException {
        String output = executeProcessAndGetOutput(abiConfig);
        return correctMakefilePaths(output, getMakefile().getParentFile());
    }

    @Override
    void processBuildOutput(
            @NonNull String buildOutput, @NonNull JsonGenerationAbiConfiguration abiConfig) {
        if (config.enableCmakeCompilerSettingsCache) {
            writeCompilerSettingsToCache(config.compilerSettingsCacheFolder, abiConfig);
        }
    }

    @NonNull
    @Override
    ProcessInfoBuilder getProcessBuilder(@NonNull JsonGenerationAbiConfiguration abiConfig) {
        ProcessInfoBuilder builder = new ProcessInfoBuilder();

        builder.setExecutable(getCmakeExecutable());
        builder.addArgs(getProcessBuilderArgs(abiConfig));

        return builder;
    }

    /** Returns the list of arguments to be passed to process builder. */
    @VisibleForTesting
    @NonNull
    List<String> getProcessBuilderArgs(@NonNull JsonGenerationAbiConfiguration abiConfig) {
        List<String> processBuilderArgs = Lists.newArrayList();
        // CMake requires a folder. Trim the filename off.
        File cmakeListsFolder = getMakefile().getParentFile();
        processBuilderArgs.add(String.format("-H%s", cmakeListsFolder));
        processBuilderArgs.add(String.format("-B%s", abiConfig.getExternalNativeBuildFolder()));
        processBuilderArgs.addAll(getCacheArguments(abiConfig));

        // Add user provided build arguments
        processBuilderArgs.addAll(getBuildArguments());
        if (config.enableCmakeCompilerSettingsCache) {
            return wrapCmakeListsForCompilerSettingsCaching(
                            config.compilerSettingsCacheFolder,
                            abiConfig,
                            cmakeListsFolder,
                            processBuilderArgs)
                    .getArgs();
        }
        return processBuilderArgs;
    }

    /**
     * Returns a list of default cache arguments that the implementations may use.
     *
     * @return list of default cache arguments
     */
    protected List<String> getCommonCacheArguments(
            @NonNull JsonGenerationAbiConfiguration abiConfig) {
        List<String> cacheArguments = Lists.newArrayList();
        cacheArguments.add(String.format("-DANDROID_ABI=%s", abiConfig.getAbiName()));
        cacheArguments.add(
                String.format("-DANDROID_PLATFORM=android-%s", abiConfig.getAbiPlatformVersion()));
        cacheArguments.add(
                String.format(
                        "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=%s",
                        new File(getObjFolder(), abiConfig.getAbiName())));
        cacheArguments.add(
                String.format("-DCMAKE_BUILD_TYPE=%s", isDebuggable() ? "Debug" : "Release"));
        cacheArguments.add(String.format("-DANDROID_NDK=%s", getNdkFolder()));
        if (!getcFlags().isEmpty()) {
            cacheArguments.add(
                    String.format("-DCMAKE_C_FLAGS=%s", Joiner.on(" ").join(getcFlags())));
        }

        if (!getCppFlags().isEmpty()) {
            cacheArguments.add(
                    String.format("-DCMAKE_CXX_FLAGS=%s", Joiner.on(" ").join(getCppFlags())));
        }

        return cacheArguments;
    }

    /** Returns the compile commands json file for the given abi. */
    @NonNull
    public File getCompileCommandsJson(@NonNull String abi) {
        return ExternalNativeBuildTaskUtils.getCompileCommandsJson(getJsonFolder(), abi);
    }

    @NonNull
    @Override
    public NativeBuildSystem getNativeBuildSystem() {
        return NativeBuildSystem.CMAKE;
    }

    @NonNull
    @Override
    Map<Abi, File> getStlSharedObjectFiles() {
        // Search for ANDROID_STL build argument. Process in order / later flags take precedent.
        String stl = null;
        File ndkBasePath = null;
        for (String argument : getBuildArguments()) {
            argument = argument.replace(" ", "");
            switch (argument) {
                case "-DANDROID_STL=stlport_shared":
                    stl = "stlport";
                    ndkBasePath = FileUtils.join(getNdkFolder(), "sources", "cxx-stl", "stlport");
                    break;
                case "-DANDROID_STL=gnustl_shared":
                    stl = "gnustl";
                    ndkBasePath =
                            FileUtils.join(
                                    getNdkFolder(), "sources", "cxx-stl", "gnu-libstdc++", "4.9");
                    break;
                case "-DANDROID_STL=c++_shared":
                    stl = "c++";
                    ndkBasePath =
                            FileUtils.join(getNdkFolder(), "sources", "cxx-stl", "llvm-libc++");
                    break;
            }
        }
        Map<Abi, File> result = Maps.newHashMap();
        if (stl == null) {
            return result;
        }
        for (Abi abi : getAbis()) {
            File file = FileUtils.join(ndkBasePath, "libs", abi.getName(),
                    String.format("lib%s_shared.so", stl));
            checkState(file.isFile(), "Expected NDK STL shared object file at %s", file.toString());
            result.put(abi, file);
        }
        return result;
    }

    @NonNull
    @VisibleForTesting
    static String correctMakefilePaths(@NonNull String input, @NonNull File makeFileDirectory) {
        Matcher cmakeFinderMatcher = cmakeFileFinder.matcher(input);
        if (cmakeFinderMatcher.matches()) {
            // The whole multi-line output could contain multiple warnings/errors
            // so we split it into lines, fix the filenames, then recombine it.
            List<String> corrected = new ArrayList<>();
            for (String entry : input.split(System.lineSeparator())) {
                cmakeFinderMatcher = cmakeFileFinder.matcher(entry);
                if (cmakeFinderMatcher.matches()) {
                    String fileName = cmakeFinderMatcher.group(3);
                    File makeFile = new File(fileName);
                    // No need to update absolute paths.
                    if (makeFile.isAbsolute()) {
                        corrected.add(entry);
                        continue;
                    }

                    // Don't point to a file that doesn't exist.
                    makeFile = new File(makeFileDirectory, fileName);
                    if (!makeFile.exists()) {
                        corrected.add(entry);
                        continue;
                    }

                    // We were able to update the makefile path.
                    corrected.add(
                            cmakeFinderMatcher.group(1)
                                    + makeFile.getAbsolutePath()
                                    + cmakeFinderMatcher.group(4));
                } else {
                    corrected.add(entry);
                }
            }

            return Joiner.on(System.lineSeparator()).join(corrected);
        }

        return input;
    }

    @NonNull
    protected File getToolChainFile() {
        String toolchainFileName = "android.toolchain.cmake";
        File ndkCmakeFolder = new File(new File(getNdkFolder(), "build"), "cmake");
        // Toolchain file should be located at ndk/build/cmake/ for NDK r13+.
        File toolchainFile = new File(ndkCmakeFolder, toolchainFileName);
        if (!toolchainFile.exists()) {
            // Toolchain file for NDK r12 is in the SDK.
            // TODO: remove this when we stop caring about r12.
            toolchainFile = new File(cmakeInstallFolder, toolchainFileName);
        }
        return toolchainFile;
    }

    @NonNull
    protected File getCmakeBinFolder() {
        return new File(cmakeInstallFolder, "bin");
    }

    @NonNull
    protected File getCmakeExecutable() {
        if (isWindows()) {
            return new File(getCmakeBinFolder(), "cmake.exe");
        }
        return new File(getCmakeBinFolder(), "cmake");
    }
}
