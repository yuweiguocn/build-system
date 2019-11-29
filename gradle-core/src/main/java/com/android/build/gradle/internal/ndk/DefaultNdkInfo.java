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

package com.android.build.gradle.internal.ndk;

import static com.android.build.gradle.internal.cxx.configure.NdkAbiFileKt.ndkMetaAbisFile;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.cxx.configure.NdkAbiFile;
import com.android.build.gradle.internal.cxx.configure.PlatformConfigurator;
import com.android.repository.Revision;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.utils.FileUtils;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.logging.Logging;

/** Default NdkInfo. Used for r13 and earlier. */
public class DefaultNdkInfo implements NdkInfo {

    private final File root;

    private final PlatformConfigurator platformConfigurator;

    private final List<AbiInfo> abiInfoList;

    private final Map<Abi, String> defaultToolchainVersions = Maps.newHashMap();

    public DefaultNdkInfo(@NonNull File root) {
        this.root = root;
        this.platformConfigurator = new PlatformConfigurator(root);
        this.abiInfoList = new NdkAbiFile(ndkMetaAbisFile(root)).getAbiInfoList();
    }

    @NonNull
    private File getRootDirectory() {
        return root;
    }

    /**
     * Retrieve the newest supported version if it is not the specified version is not supported.
     *
     * An older NDK may not support the specified compiledSdkVersion.  In that case, determine what
     * is the newest supported version and modify compileSdkVersion.
     */
    @Override
    @Nullable
    public String findLatestPlatformVersion(@NonNull String targetPlatformString) {

        AndroidVersion androidVersion = AndroidTargetHash.getVersionFromHash(targetPlatformString);
        int targetVersion;
        if (androidVersion == null) {
            Logging.getLogger(this.getClass()).warn(
                    "Unable to parse NDK platform version.  Try to find the latest instead.");
            targetVersion = Integer.MAX_VALUE;
        } else {
            targetVersion = androidVersion.getFeatureLevel();
        }
        targetVersion = findTargetPlatformVersionOrLower(targetVersion);
        if (targetVersion == 0) {
            return null;
        }
        return "android-" + targetVersion;
    }


    @Override
    public int findSuitablePlatformVersion(
            @NonNull String abiName,
            @Nullable AndroidVersion androidVersion) {
        return platformConfigurator.findSuitablePlatformVersion(abiName, androidVersion);
    }

    // Will return 0 if no platform found
    private int findTargetPlatformVersionOrLower(int targetVersion) {
        File platformDir = new File(root, "/platforms");
        if (new File(platformDir, "android-" + targetVersion).exists()) {
            return targetVersion;
        } else {
            File[] platformSubDirs = platformDir.listFiles(File::isDirectory);
            int highestVersion = 0;
            assert platformSubDirs != null;
            for (File platform : platformSubDirs) {
                if (platform.getName().startsWith("android-")) {
                    try {
                        int version = Integer.parseInt(
                                platform.getName().substring("android-".length()));
                        if (version > highestVersion && version < targetVersion) {
                            highestVersion = version;
                        }
                    } catch (NumberFormatException ignore) {
                        // Ignore unrecognized directories.
                    }
                }
            }
            return highestVersion;
        }
    }

    private static String getToolchainPrefix(Abi abi) {
        return abi.getGccToolchainPrefix();
    }

    /**
     * Return the directory containing the toolchain.
     *
     * @param abi target ABI of the toolchains
     * @return a directory that contains the executables.
     */
    @NonNull
    private File getToolchainPath(@NonNull Abi abi) {
        abi = getToolchainAbi(abi);
        String version = getDefaultToolchainVersion(abi);
        version = version.isEmpty() ? "" : "-" + version;  // prepend '-' if non-empty.

        File prebuiltFolder =
                new File(
                        getRootDirectory(),
                        "toolchains/" + getToolchainPrefix(abi) + version + "/prebuilt");

        String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        String hostOs;
        if (osName.contains("windows")) {
            hostOs = "windows";
        } else if (osName.contains("mac")) {
            hostOs = "darwin";
        } else {
            hostOs = "linux";
        }

        // There should only be one directory in the prebuilt folder.  If there are more than one
        // attempt to determine the right one based on the operating system.
        File[] toolchainPaths = prebuiltFolder.listFiles(File::isDirectory);

        if (toolchainPaths == null) {
            throw new InvalidUserDataException("Unable to find toolchain: " + prebuiltFolder);
        }
        if (toolchainPaths.length == 1) {
            return toolchainPaths[0];
        }

        // Use 64-bit toolchain if available.
        File toolchainPath = new File(prebuiltFolder, hostOs + "-x86_64");
        if (toolchainPath.isDirectory()) {
            return toolchainPath;
        }

        // Fallback to 32-bit if we can't find the 64-bit toolchain.
        String osString = (osName.equals("windows")) ? hostOs : hostOs + "-x86";
        toolchainPath = new File(prebuiltFolder, osString);
        if (toolchainPath.isDirectory()) {
            return toolchainPath;
        } else {
            throw new InvalidUserDataException("Unable to find toolchain prebuilt folder in: "
                    + prebuiltFolder);
        }
    }

    @NonNull
    protected Abi getToolchainAbi(@NonNull Abi abi) {
        return abi;
    }

    /** Return the executable for removing debug symbols from a shared object. */
    @Override
    @NonNull
    public File getStripExecutable(Abi abi) {
        abi = getToolchainAbi(abi);
        return FileUtils.join(
                getToolchainPath(abi), "bin", abi.getGccExecutablePrefix() + "-strip");
    }


    /**
     * Return the default version of the specified toolchain for a target abi.
     *
     * <p>The default version is the highest version found in the NDK for the specified toolchain
     * and ABI. The result is cached for performance.
     */
    @NonNull
    private String getDefaultToolchainVersion(@NonNull Abi abi) {
        abi = getToolchainAbi(abi);
        String defaultVersion = defaultToolchainVersions.get(abi);
        if (defaultVersion != null) {
            return defaultVersion;
        }

        final String toolchainPrefix = getToolchainPrefix(abi);
        File toolchains = new File(getRootDirectory(), "toolchains");
        File[] toolchainsForAbi = toolchains.listFiles(
                (dir, filename) -> filename.startsWith(toolchainPrefix));
        if (toolchainsForAbi == null || toolchainsForAbi.length == 0) {
            throw new RuntimeException(
                    "No toolchains found in the NDK toolchains folder for ABI with prefix: "
                            + toolchainPrefix);
        }

        // Once we have a list of toolchains, we look the highest version
        Revision bestRevision = null;
        String bestVersionString = "";
        for (File toolchainFolder : toolchainsForAbi) {
            String folderName = toolchainFolder.getName();

            Revision revision = new Revision(0);
            String versionString = "";
            if (folderName.length() > toolchainPrefix.length() + 1) {
                // Find version if folderName is in the form {prefix}-{version}
                try {
                    versionString = folderName.substring(toolchainPrefix.length() + 1);
                    revision = Revision.parseRevision(versionString);
                } catch (NumberFormatException ignore) {
                }
            }
            if (bestRevision == null || revision.compareTo(bestRevision) > 0) {
                bestRevision = revision;
                bestVersionString = versionString;
            }
        }
        defaultToolchainVersions.put(abi, bestVersionString);
        return bestVersionString;
    }

    @NonNull
    @Override
    public Collection<Abi> getDefault32BitsAbis() {
        return abiInfoList
                .stream()
                .filter(abiInfo -> abiInfo.isDefault() && !abiInfo.isDeprecated())
                .map(AbiInfo::getAbi)
                .filter(abi -> !abi.supports64Bits())
                .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public Collection<Abi> getDefaultAbis() {
        return abiInfoList
                .stream()
                .filter(abiInfo -> abiInfo.isDefault() && !abiInfo.isDeprecated())
                .map(AbiInfo::getAbi)
                .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public Collection<Abi> getSupported32BitsAbis() {
        return abiInfoList
                .stream()
                .map(AbiInfo::getAbi)
                .filter(abi -> !abi.supports64Bits())
                .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public Collection<Abi> getSupportedAbis() {
        return abiInfoList.stream().map(AbiInfo::getAbi).collect(Collectors.toList());
    }
}
