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

package com.android.build.gradle.internal.ndk;

import static com.android.SdkConstants.FN_LOCAL_PROPERTIES;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.core.Abi;
import com.android.repository.Revision;
import com.android.sdklib.AndroidVersion;
import com.android.utils.Pair;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Properties;
import org.gradle.api.logging.Logging;

/**
 * Handles NDK related information.
 */
public class NdkHandler {
    @NonNull private final File projectDir;
    @Nullable
    private String compileSdkVersion;
    private File ndkDirectory;
    @Nullable private NdkInfo ndkInfo;
    @Nullable private Revision revision;

    public NdkHandler(@Nullable String ndkVersionFromDsl, @NonNull File projectDir) {
        // TODO: Consume ndkVersionFromDsl here
        this.projectDir = projectDir;
        relocateNdkFolder();
    }

    /** Attempts to search again for the Ndk directory. */
    public void relocateNdkFolder() {
        ndkDirectory = findNdkDirectory(projectDir);

        if (ndkDirectory == null || !ndkDirectory.exists()) {
            ndkInfo = null;
            revision = null;
        } else {
            revision = findRevision(ndkDirectory);
            if (revision == null) {
                ndkInfo = new DefaultNdkInfo(ndkDirectory);
            } else {
                ndkInfo = new NdkR14Info(ndkDirectory);
            }
        }
    }

    private static Properties readProperties(File file) {
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(fis, Charsets.UTF_8)) {
            properties.load(reader);
        } catch (FileNotFoundException ignored) {
            // ignore since we check up front and we don't want to fail on it anyway
            // in case there's an env var.
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to read %1$s.", file), e);
        }
        return properties;
    }

    @VisibleForTesting
    @Nullable
    public static Revision findRevision(@Nullable File ndkDirectory) {
        if (ndkDirectory == null) {
            return null;
        } else {
            File sourceProperties = new File(ndkDirectory, "source.properties");
            if (!sourceProperties.exists()) {
                // source.properties does not exist.  It's probably r10.  Use the DefaultNdkInfo.
                return null;
            }
            Properties properties = readProperties(sourceProperties);
            String version = properties.getProperty("Pkg.Revision");
            if (version != null) {
                return Revision.parseRevision(version);
            } else {
                return null;
            }
        }
    }


    @Nullable
    public Revision getRevision() {
        return revision;
    }

    @Nullable
    public String getPlatformVersion() {
        assert compileSdkVersion != null;
        assert ndkInfo != null;
        return ndkInfo.findLatestPlatformVersion(compileSdkVersion);
    }

    public void setCompileSdkVersion(@NonNull String compileSdkVersion) {
        this.compileSdkVersion = compileSdkVersion;
    }

    @Nullable
    private static File findNdkDirectory(@NonNull File projectDir) {
        File localProperties = new File(projectDir, FN_LOCAL_PROPERTIES);
        Properties properties = new Properties();
        if (localProperties.isFile()) {
            properties = readProperties(localProperties);
        }

        File ndkDir = findNdkDirectory(properties, projectDir);
        if (ndkDir == null) {
            return null;
        }
        return checkNdkDir(ndkDir) ? ndkDir : null;
    }

    /**
     * Perform basic verification on the NDK directory.
     */
    private static boolean checkNdkDir(File ndkDir) {
        if (!new File(ndkDir, "platforms").isDirectory()) {
            invalidNdkWarning("NDK is missing a \"platforms\" directory.", ndkDir);
            return false;
        }
        if (!new File(ndkDir, "toolchains").isDirectory()) {
            invalidNdkWarning("NDK is missing a \"toolchains\" directory.", ndkDir);
            return false;
        }
        return true;
    }

    private static void invalidNdkWarning(String message, File ndkDir) {
        Logging.getLogger(NdkHandler.class).warn(
                "{}\n"
                        + "If you are using NDK, verify the ndk.dir is set to a valid NDK "
                        + "directory.  It is currently set to {}.\n"
                        + "If you are not using NDK, unset the NDK variable from ANDROID_NDK_HOME "
                        + "or local.properties to remove this warning.\n",
                message,
                ndkDir.getAbsolutePath());
    }

    /**
     * Determine the location of the NDK directory.
     *
     * The NDK directory can be set in the local.properties file, using the ANDROID_NDK_HOME
     * environment variable or come bundled with the SDK.
     *
     * Return null if NDK directory is not found.
     */
    @Nullable
    public static File findNdkDirectory(@NonNull Properties properties, @NonNull File projectDir) {
        String ndkDirProp = properties.getProperty("ndk.dir");
        if (ndkDirProp != null) {
            return new File(ndkDirProp);
        }

        String ndkEnvVar = System.getenv("ANDROID_NDK_HOME");
        if (ndkEnvVar != null) {
            return new File(ndkEnvVar);
        }

        Pair<File, Boolean> sdkLocation = SdkHandler.findSdkLocation(properties, projectDir);
        File sdkFolder = sdkLocation.getFirst();
        if (sdkFolder != null) {
            // Worth checking if the NDK came bundled with the SDK
            File ndkBundle = new File(sdkFolder, SdkConstants.FD_NDK);
            if (ndkBundle.isDirectory()) {
                return ndkBundle;
            }
        }

        return null;
    }

    /**
     * Returns the directory of the NDK.
     */
    @Nullable
    public File getNdkDirectory() {
        return ndkDirectory;
    }

    /**
     * Return true if NDK directory is configured.
     */
    public boolean isConfigured() {
        return ndkDirectory != null && ndkDirectory.isDirectory();
    }

    /**
     * Return true if compiledSdkVersion supports 64 bits ABI.
     */
    private boolean supports64Bits() {
        if (getPlatformVersion() == null) {
            return false;
        }
        String targetString = getPlatformVersion().replace("android-", "");
        try {
            return Integer.parseInt(targetString) >= 20;
        } catch (NumberFormatException ignored) {
            // "android-L" supports 64-bits.
            return true;
        }
    }

    /**
     * Returns a list of all ABI.
     */
    @NonNull
    public static Collection<Abi> getAbiList() {
        return ImmutableList.copyOf(Abi.values());
    }

    /** Returns a list of default ABIs. */
    @NonNull
    public static Collection<Abi> getDefaultAbiList() {
        return Abi.getDefaultValues();
    }

    /**
     * Returns a list of 32-bits ABI.
     */
    @NonNull
    private static Collection<Abi> getAbiList32() {
        ImmutableList.Builder<Abi> builder = ImmutableList.builder();
        for (Abi abi : Abi.values()) {
            if (!abi.supports64Bits()) {
                builder.add(abi);
            }
        }
        return builder.build();
    }

    /**
     * Returns a list of supported ABI.
     */
    @NonNull
    public Collection<Abi> getSupportedAbis() {
        if (ndkInfo != null) {
            return supports64Bits() ? ndkInfo.getSupportedAbis() : ndkInfo.getSupported32BitsAbis();
        }
        return supports64Bits() ? getAbiList() : getAbiList32();
    }

    /** Returns a list of supported ABI. */
    @NonNull
    public Collection<Abi> getDefaultAbis() {
        if (ndkInfo != null) {
            return supports64Bits() ? ndkInfo.getDefaultAbis() : ndkInfo.getDefault32BitsAbis();
        }
        return supports64Bits() ? getAbiList() : getAbiList32();
    }

    /**
     * Return the executable for removing debug symbols from a shared object.
     */
    @NonNull
    public File getStripExecutable(Abi abi) {
        checkNotNull(ndkInfo);
        return ndkInfo.getStripExecutable(abi);
    }

    public int findSuitablePlatformVersion(
            @NonNull String abi,
            @NonNull String variantName,
            @Nullable AndroidVersion androidVersion) {
        checkNotNull(ndkInfo);
        return ndkInfo.findSuitablePlatformVersion(abi, androidVersion);
    }
}
