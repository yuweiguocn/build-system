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

import static com.google.common.base.Preconditions.checkArgument;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.external.cmake.CmakeUtils;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.model.CoreExternalNativeBuild;
import com.android.repository.Revision;
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Shared utility methods for dealing with external native build tasks.
 */
public class ExternalNativeBuildTaskUtils {
    // Forked CMake version is the one we get when we execute "cmake --version" command.
    public static final String CUSTOM_FORK_CMAKE_VERSION = "3.6.0-rc2";

    /**
     * File 'derived' is consider to depend on the contents of file 'source' this function return
     * true if source is more recent than derived.
     *
     * <p>If derived doesn't exist then it is not consider to be up-to-date with respect to source.
     *
     * @param source -- original file (must exist)
     * @param derived -- derived file
     * @return true if derived is more recent than original
     * @throws IOException if there was a problem reading the timestamp of one of the files
     */
    public static boolean fileIsUpToDate(@NonNull File source, @NonNull File derived)
            throws IOException {
        if (!source.exists()) {
            // Generally shouldn't happen but if it does then let's claim that derived is out of
            // date.
            return false;
        }
        if (!derived.exists()) {
            // Derived file doesn't exist so it is not up-to-date with respect to file 1
            return false;
        }
        long sourceTimestamp = Files.getLastModifiedTime(source.toPath()).toMillis();
        long derivedTimestamp = Files.getLastModifiedTime(derived.toPath()).toMillis();
        return sourceTimestamp <= derivedTimestamp;
    }

    /**
     * The json mini-config file contains a subset of the regular json file that is much smaller and
     * less memory-intensive to read.
     */
    @NonNull
    public static File getJsonMiniConfigFile(@NonNull File originalJson) {
        return new File(originalJson.getParent(), "android_gradle_build_mini.json");
    }

    /**
     * Utility function that takes an ABI string and returns the corresponding output folder. Output
     * folder is where build artifacts are placed.
     */
    @NonNull
    static File getOutputFolder(@NonNull File jsonFolder, @NonNull String abi) {
        return new File(jsonFolder, abi);
    }

    /**
     * Utility function that gets the name of the output JSON for a particular ABI.
     */
    @NonNull
    public static File getOutputJson(@NonNull File jsonFolder, @NonNull String abi) {
        return new File(getOutputFolder(jsonFolder, abi), "android_gradle_build.json");
    }

    /** Utility function that gets the name of the output JSON for a particular ABI. */
    @NonNull
    public static File getCompileCommandsJson(@NonNull File jsonFolder, @NonNull String abi) {
        return new File(getOutputFolder(jsonFolder, abi), "compile_commands.json");
    }

    @NonNull
    public static List<File> getOutputJsons(@NonNull File jsonFolder,
            @NonNull Collection<String> abis) {
        List<File> outputs = Lists.newArrayList();
        for (String abi : abis) {
            outputs.add(getOutputJson(jsonFolder, abi));
        }
        return outputs;
    }

    public static boolean isExternalNativeBuildEnabled(@NonNull CoreExternalNativeBuild config) {
        return (config.getNdkBuild().getPath() != null)
                || (config.getCmake().getPath() != null);
    }

    /**
     * Resolve the path of any native build project.
     *
     * @param config -- the AndroidConfig
     * @return Path resolution.
     */
    @NonNull
    public static ExternalNativeBuildProjectPathResolution getProjectPath(
            @NonNull CoreExternalNativeBuild config) {
        // Path discovery logic:
        // If there is exactly 1 path in the DSL, then use it.
        // If there are more than 1, then that is an error. The user has specified both cmake and
        //    ndkBuild in the same project.

        Map<NativeBuildSystem, File> externalProjectPaths = getExternalBuildExplicitPaths(config);
        if (externalProjectPaths.size() > 1) {
            return new ExternalNativeBuildProjectPathResolution(
                    null, null, null, "More than one externalNativeBuild path specified");
        }

        if (externalProjectPaths.isEmpty()) {
            // No external projects present.
            return new ExternalNativeBuildProjectPathResolution(null, null, null, null);
        }

        NativeBuildSystem buildSystem = externalProjectPaths.keySet().iterator().next();
        return new ExternalNativeBuildProjectPathResolution(
                buildSystem,
                externalProjectPaths.get(buildSystem),
                getExternalNativeBuildPath(config).get(buildSystem),
                null);
    }

    /**
     * @return a map of generate task to path from DSL. Zero entries means there are no paths in the
     *     DSL. Greater than one entries means that multiple paths are specified, this is an error.
     */
    @NonNull
    private static Map<NativeBuildSystem, File> getExternalBuildExplicitPaths(
            @NonNull CoreExternalNativeBuild config) {
        Map<NativeBuildSystem, File> map = new EnumMap<>(NativeBuildSystem.class);
        File cmake = config.getCmake().getPath();
        File ndkBuild = config.getNdkBuild().getPath();

        if (cmake != null) {
            map.put(NativeBuildSystem.CMAKE, cmake);
        }
        if (ndkBuild != null) {
            map.put(NativeBuildSystem.NDK_BUILD, ndkBuild);
        }
        return map;
    }

    @NonNull
    private static Map<NativeBuildSystem, File> getExternalNativeBuildPath(
            @NonNull CoreExternalNativeBuild config) {
        Map<NativeBuildSystem, File> map = new EnumMap<>(NativeBuildSystem.class);
        File cmake = config.getCmake().getBuildStagingDirectory();
        File ndkBuild = config.getNdkBuild().getBuildStagingDirectory();
        if (cmake != null) {
            map.put(NativeBuildSystem.CMAKE, cmake);
        }
        if (ndkBuild != null) {
            map.put(NativeBuildSystem.NDK_BUILD, ndkBuild);
        }

        return map;
    }

    /**
     * Returns the folder with the CMake binary. For more info, check the comments on
     * doFindCmakeExecutableFolder below.
     *
     * @param sdkHandler sdk handler
     * @return Folder with the required CMake binary
     */
    @NonNull
    public static File findCmakeExecutableFolder(
            @NonNull String cmakeVersion, @NonNull SdkHandler sdkHandler) {
        return doFindCmakeExecutableFolder(cmakeVersion, sdkHandler, getEnvironmentPathList());
    }

    /**
     * @return array of folders (as Files) retrieved from PATH environment variable and from Sdk
     *     cmake folder.
     */
    @NonNull
    private static List<File> getEnvironmentPathList() {
        List<File> fileList = new ArrayList<>();
        String envPath = System.getenv("PATH");

        List<String> pathList = new ArrayList<>();
        if (envPath != null) {
            pathList.addAll(Arrays.asList(envPath.split(System.getProperty("path.separator"))));
        }

        for (String path : pathList) {
            fileList.add(new File(path));
        }

        return fileList;
    }

    /**
     * Returns the folder with the CMake binary. There are 3 possible places to find/look the CMake
     * binary path:
     *
     * <p>- First search the path specified in the local properties, return if one is available.
     *
     * <p>- Check the version in externalNativeBuild in app's build.gradle, if one is specified,
     * search for a CMake binary that matches the version specified. Note: the version should be an
     * exact match. Return the path if one is found. Note: If the version matches CMake installed in
     * SDK (forked-cmake or vanilla-cmake), then do not search the path, just look for it within the
     * SDK.
     *
     * <p>- Find CMake in the Sdk folder (or install CMake if it's unavailable) and return the CMake
     * folder.
     *
     * @param sdkHandler sdk handler
     * @param foldersToSearch folders to search if not found specified in local.properties
     * @return Folder with the required CMake binary
     */
    @VisibleForTesting
    @NonNull
    static File doFindCmakeExecutableFolder(
            @Nullable String cmakeVersion,
            @NonNull SdkHandler sdkHandler,
            @NonNull List<File> foldersToSearch) {
        if (sdkHandler.getCmakePathInLocalProp() != null) {
            return sdkHandler.getCmakePathInLocalProp();
        }

        if (cmakeVersion != null && !isDefaultSdkCmakeVersion(cmakeVersion)) {
            // getRequiredCmakeFromFolders will throw a RuntimeException with errors if it is unable
            // to find the required CMake.
            File cmakeFolder =
                    getRequiredCmakeFromFolders(
                            Revision.parseRevision(cmakeVersion), foldersToSearch);
            return new File(cmakeFolder.getParent());
        }

        return getCmakeFolderFromSdkPackage(sdkHandler);
    }

    /**
     * By default, in SDK we support CMake versions (be it forked-cmake or vanilla-cmake). This
     * function returns true if its one of those versions.
     */
    private static boolean isDefaultSdkCmakeVersion(@NonNull String cmakeVersion) {
        // TODO(kravindran) Add vanilla CMake version information once its in the SDK.
        return (cmakeVersion.equals(CUSTOM_FORK_CMAKE_VERSION));
    }

    /**
     * Returns a CMake folder which has CMake binary that exactly matches the cmake version
     * specified.
     *
     * @param cmakeVersion - cmake binary with the version to search for.
     * @param foldersToSearch folders to search if not found specified in local.properties
     * @return CMake binary folder
     */
    @NonNull
    private static File getRequiredCmakeFromFolders(
            @NonNull Revision cmakeVersion, @NonNull List<File> foldersToSearch) {
        List<File> foldersWithErrors = new ArrayList<>();
        for (File cmakeFolder : foldersToSearch) {
            // Check if cmake executable is present, if not continue searching.
            File cmakeBin;
            if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
                cmakeBin = new File(cmakeFolder, "cmake.exe");
            } else {
                cmakeBin = new File(cmakeFolder, "cmake");
            }

            if (!cmakeBin.exists()) {
                continue;
            }
            try {
                Revision version = CmakeUtils.getVersion(cmakeFolder);
                if (cmakeVersion.equals(version)) {
                    return cmakeFolder;
                }
            } catch (IOException e) {
                // Ignore if we get an exception when trying to find the version. It could be due to
                // corrupt/inaccessible cmake, we'll search other locations instead.
                foldersWithErrors.add(cmakeFolder);
            }
        }

        StringBuilder errorMsg =
                new StringBuilder(
                        String.format(
                                "Unable to find CMake with version: %s within folder: %s\n.",
                                cmakeVersion, foldersToSearch.toString()));

        if (!foldersWithErrors.isEmpty()) {
            errorMsg.append(
                    String.format(
                            "Folders have inaccessible/corrupt CMake: %s",
                            foldersWithErrors.toString()));
        }

        errorMsg.append(
                "Please make sure the folder with the CMake binary is added to the PATH "
                        + "environment variable.");

        throw new RuntimeException(errorMsg.toString());
    }

    /**
     * Returns the CMake folder installed within Sdk folder, if one is not present, install CMake
     * and return the CMake folder.
     */
    @NonNull
    private static File getCmakeFolderFromSdkPackage(@NonNull SdkHandler sdkHandler) {
        ProgressIndicator progress = new ConsoleProgressIndicator();
        AndroidSdkHandler sdk = AndroidSdkHandler.getInstance(sdkHandler.getSdkFolder());
        LocalPackage cmakePackage =
                sdk.getLatestLocalPackageForPrefix(SdkConstants.FD_CMAKE, null, true, progress);
        if (cmakePackage != null) {
            return cmakePackage.getLocation();
        }
        // If CMake package is not found, we install it and try to find it.
        sdkHandler.installCMake("3.6.4111459");
        cmakePackage =
                sdk.getLatestLocalPackageForPrefix(SdkConstants.FD_CMAKE, null, true, progress);
        if (cmakePackage != null) {
            return cmakePackage.getLocation();
        }

        return new File(sdkHandler.getSdkFolder(), SdkConstants.FD_CMAKE);
    }

    public static class ExternalNativeBuildProjectPathResolution {
        @Nullable public final String errorText;
        @Nullable public final NativeBuildSystem buildSystem;
        @Nullable public final File makeFile;
        @Nullable public final File externalNativeBuildDir;

        private ExternalNativeBuildProjectPathResolution(
                @Nullable NativeBuildSystem buildSystem,
                @Nullable File makeFile,
                @Nullable File externalNativeBuildDir,
                @Nullable String errorText) {
            checkArgument(
                    makeFile == null || buildSystem != null,
                    "Expected path and buildSystem together, no taskClass");
            checkArgument(
                    makeFile != null || buildSystem == null,
                    "Expected path and buildSystem together, no path");
            checkArgument(
                    makeFile == null || errorText == null,
                    "Expected path or error but both existed");
            this.buildSystem = buildSystem;
            this.makeFile = makeFile;
            this.externalNativeBuildDir = externalNativeBuildDir;
            this.errorText = errorText;
        }
    }


}
