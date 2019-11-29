/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.configure

import com.android.SdkConstants
import com.android.SdkConstants.FD_CMAKE
import com.android.build.gradle.external.cmake.CmakeUtils
import com.android.build.gradle.internal.SdkHandler
import com.android.repository.Revision
import com.android.repository.api.LocalPackage
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.LoggerProgressIndicatorWrapper
import com.android.utils.ILogger
import java.io.File
import java.io.IOException

/**
 * This is the logic for locating CMake needed for gradle build. This logic searches for CMake in
 * a prescribed order and provides diagnostic information, warnings, and errors useful to the user
 * in understanding how CMake was found or why it wasn't found.
 *
 * There are several pieces of information at play here:
 * (1) The optional CMake version specified in build.gradle under externalNativeBuild.cmake.version.
 * (2) The optional path to CMake folder specified in local.properties file. This file is not meant
 *     to be checked in to version control.
 * (3) The SDK versions that are available in the SDK. Some of these may have been downloaded
 *     already.
 * (4) The system $PATH variable.
 * (5) The version numbers of "fork CMake" which is an older forked version of CMake that has
 *     additional functionality to help with emitting metadata.
 *
 * The version numbers of SDK CMakes are special in that there are two distinct versions for each
 * cmake.exe:
 * (1) A version like "3.6.0-rc2" which is the compiled-in version that is emitted by a call to
 *     cmake --version.
 * (2) A version like "3.6.4111459" which is the SDK's version for the CMake package. In this case,
 *     4111459 is the ADRT "bid" number used for deployment within SDK.
 *
 * The searching algorithm needs to take into account that new SDK versions of CMake will be added
 * in the future. We can't assume we know versions a priori.
 *
 * Only the following CMake versions are supported:
 * (1) A known "fork CMake" from the SDK
 * (2) A version of CMake that supports CMake "server" mode. This mode is supported by CMake 3.7.0
 *     and higher.
 *
 * The search algorithm tries to enforce several invariants:
 * (1) If a cmake is specified in local.properties then that CMake must be used or it's an error.
 * (2) If a CMake version *is* specified in build.gradle externalNativeBuild.cmake.version then
 *     that version must be matched or it's an error.
 * (3) If a CMake version *is not* specified in build.gradle externalNativeBuild.cmake.version then
 *     fork CMake must be used. We won't use a random CMake found on the path.
 * (4) Combining (2) and (3) creates the additional invariant that there is always a specific CMake
 *     version prescribed by a build.gradle. In the case there is no concrete version in
 *     build.gradle there is still a concreted Android Gradle Plugin version which, in turn,
 *     prescribes an exact CMake version.
 *
 * Given these invariants, the algorithm looks in the following locations:
 * (1) SDK
 * (2) $PATH
 * (3) cmake.dir from local.properties
 *
 * Version matching:
 * An incomplete version like "3.12" is allowed in build.gradle. In this case, the version of the
 * found CMake must exactly match what is specified in build.gradle. This means, for example,
 * if build.gradle specifies 3.12 and only version 3.13 is found then 3.13 WON'T be used. User may
 * specify "3" if they want to match the highest version among 3.*.
 *
 * User may also append a "+" to the version. In this case, the highest version is accepted.
 *
 * If multiple are found in PATH then only the first CMake is considered for use.
 *
 * Error Handling:
 * A lot of the logic in this algorithm is dedicated toward giving good error messages in the case
 * that CMake can't be found.
 *
 * - If some CMake versions are found but they don't match the required version then the user is
 *   told what the versions of those CMakes are and where they were found.
 *
 * - If a requested CMake version looks like an SDK version (because it has a bid number in it) then
 *   a specifically constructed error message is emitted that Android Studio can use to
 *   automatically download that version.
 *
 * The various expected error messages are heavily tested in CmakeLocatorTests.kt.
 *
 * Warning:
 * Right now there is only one warning that may be emitted by this algorithm. This is in the case
 * that a CMake.exe is found but it cannot be executed to get its version number. The reason
 * it's not an error is that a matching CMake version may be found in another location. The reason
 * it's not a diagnostic info is that it's an unusual circumstance and the user may end up
 * confused when their expected CMake isn't found.
 *
 */

/**
 * This is the default CMake version to use for this Android Gradle Plugin.
 */
private const val FORK_CMAKE_SDK_VERSION = "3.6.4111459"
private val forkCmakeSdkVersionRevision = Revision.parseRevision(FORK_CMAKE_SDK_VERSION)

/**
 * List of as-yet unshipped canaries that may appear in prebuilts. The purpose of this is to
 * allow testing of SDK cmake code paths without actually publishing that CMake to the SDK.
 */
private val canarySdkPaths = listOf("3.10.4819442")

/**
 *  This is the base version that forked CMake (which has SDK version 3.6.4111459) reports
 *  when cmake --version is called. For backward compatibility we locate 3.6.4111459
 *  when this version is requested.
 *
 *  Note: cmake --version actually returns "3.6.0-rc2" rather than "3.6.0". Use the less precise
 *  version to avoid giving the impression that fork CMake is not a shipping version in Android
 *  Studio.
 */
private val forkCmakeReportedVersion = Revision.parseRevision("3.6.0")

private val newline = System.lineSeparator()

/**
 * Accumulated information about that is common to all search methodologies.
 */
private class CmakeSearchContext(
    val cmakeVersionFromDsl: String?) {
    var resultCmakeInstallFolder: File? = null
    var resultCmakeVersion: Revision? = null

    lateinit var requestedCmakeVersion: Revision
    var firstError: String? = null
    val unsuitableCmakeReasons = mutableListOf<String>()
    var requestDownloadFromAndroidStudio = false

    /**
     * Issue an error. Processing continues so also capture the firstError since this should
     * be the most interesting.
     */
    val error = { message: String ->
        if (firstError == null) {
            firstError = message
        }
        error(message)
    }

    private fun cmakeVersionFromDslNoPlus(): String? {
        if (cmakeVersionFromDsl == null) {
            return null
        }
        return cmakeVersionFromDsl.trimEnd('+')
    }

    private fun dslVersionHasPlus(): Boolean {
        if (cmakeVersionFromDsl == null) {
            return false
        }
        return cmakeVersionFromDsl.endsWith('+')
    }

    /**
     * @return true if left is equal to right, ignoring the preview component.
     */
    private fun versionEquals(left: Revision, right: Revision) =
        left.compareTo(right, Revision.PreviewComparison.IGNORE) == 0

    /**
     * @return true if left is greater than right, ignoring the preview component.
     */
    private fun versionGreaterThan(left: Revision, right: Revision) =
        left.compareTo(right, Revision.PreviewComparison.IGNORE) > 0

    fun tryAcceptFoundCmake(
        candidateCmakeInstallFolder: File,
        candidateVersion: Revision,
        locationTag: String
    ) {
        if (dslVersionHasPlus()) {
            if (versionGreaterThan(requestedCmakeVersion, candidateVersion)) {
                recordUnsuitableCmakeMessage(
                    "CMake '$candidateVersion' found " +
                            "$locationTag could not satisfy requested version " +
                            "'$requestedCmakeVersion' because it was lower."
                )
                return
            }
        } else {
            if (!versionEquals(requestedCmakeVersion, candidateVersion)) {
                recordUnsuitableCmakeMessage(
                    "CMake '$candidateVersion' found " +
                            "$locationTag did not match requested version " +
                            "'$requestedCmakeVersion'."
                )
                return
            }
        }

        // If the candidate matches the requested version exactly, then the highest version is taken.
        when {
            resultCmakeVersion == null -> {
                // There was no prior match so this one is automatically taken
                info(
                    "- CMake found $locationTag at '$candidateCmakeInstallFolder' had " +
                            "version '$candidateVersion'"
                )
                resultCmakeVersion = candidateVersion
                resultCmakeInstallFolder = candidateCmakeInstallFolder
            }
            candidateVersion > resultCmakeVersion!! -> {
                info(
                    "- CMake found $locationTag at '$candidateCmakeInstallFolder' had " +
                            "version '$candidateVersion' and replaces version '$resultCmakeVersion' " +
                            "found earlier"
                )
                resultCmakeVersion = candidateVersion
                resultCmakeInstallFolder = candidateCmakeInstallFolder
            }
            else -> info(
                "- CMake found and skipped $locationTag '$candidateCmakeInstallFolder' which had " +
                        "version '$candidateVersion' which was lower than or equal to a " +
                        "version found earlier."
            )
        }
    }

    /**
     * A CMake was found but it was unsuitable because the version didn't match what the user
     * requested. This function records a message about this to tell the user where CMake is
     * available.
     */
    fun recordUnsuitableCmakeMessage(message: String) {
        info(message)
        unsuitableCmakeReasons += "- $message"
    }

    /**
     * If the user hasn't specified a version of CMake in their build.gradle then choose a default
     * version for them.
     */
    internal fun useDefaultCmakeVersionIfNecessary(): CmakeSearchContext {
        val cmakeVersionFromDslNoPlus = cmakeVersionFromDslNoPlus()
        requestedCmakeVersion = if (cmakeVersionFromDslNoPlus == null) {
            info("No CMake version was specified in build.gradle. Choosing a suitable version.")
            forkCmakeReportedVersion
        } else {
            try {
                Revision.parseRevision(cmakeVersionFromDslNoPlus)
            } catch (e: NumberFormatException) {
                error("CMake version '$cmakeVersionFromDsl' is not formatted correctly.")
                forkCmakeReportedVersion
            }
        }
        return this
    }

    /**
     * If the CMake version is too low then it won't have features we need for processing.
     * In this case issue an error and recover by using forkCmakeSdkVersion.
     */
    internal fun checkForCmakeVersionTooLow(): CmakeSearchContext {
        if (requestedCmakeVersion.major < 3 ||
            (requestedCmakeVersion.major == 3 && requestedCmakeVersion.minor < 6)
        ) {
            // Fork CMake version 3.6.4111459 is lower than this message indicates because
            // it is a special case. Since we're trying to retire our fork the version
            // in the error message indicates the lowest non-deprecated version we can
            // work with.
            error("CMake version '$requestedCmakeVersion' is too low. Use 3.7.0 or higher.")
            requestedCmakeVersion = forkCmakeReportedVersion
        }
        return this
    }

    /**
     * If the CMake version does not contain minor/micro versions, then issue an error and recover.
     */
    internal fun checkForCmakeVersionAdequatePrecision(): CmakeSearchContext {
        if (requestedCmakeVersion.toIntArray(true).size < 3) {
            error("CMake version '$requestedCmakeVersion' does not have enough precision. Use major.minor.micro in version.")
            requestedCmakeVersion = forkCmakeReportedVersion
        }
        return this
    }

    /**
     * If there is a cmake.dir path in the user's local.properties then use it. If the version number
     * from build.gradle doesn't agree then that is an error.
     */
    internal fun tryPathFromLocalProperties(
        cmakeVersionGetter: (File) -> Revision?,
        pathFromLocalProperties: File?
    ): CmakeSearchContext {
        assert(resultCmakeInstallFolder == null)
        if (pathFromLocalProperties == null) {
            return this
        }
        val binDir = File(pathFromLocalProperties, "bin")
        val version = cmakeVersionGetter(binDir)
        if (version == null) {
            error("Could not get version from cmake.dir path '$pathFromLocalProperties'.")
            return this
        }

        if (cmakeVersionFromDsl == null) {
            info("- Found CMake '$version' via cmake.dir='$pathFromLocalProperties'.")
            resultCmakeInstallFolder = pathFromLocalProperties
            resultCmakeVersion = version
        } else {
            tryAcceptFoundCmake(pathFromLocalProperties, version, "from cmake.dir")
            if (resultCmakeInstallFolder == null) {
                error(
                    "CMake '$version' found via cmake.dir='$pathFromLocalProperties' does not match " +
                            "requested version '$requestedCmakeVersion'."
                )
            }
        }

        return this
    }

    /**
     * Search within the already-download SDK packages.
     */
    internal fun tryLocalRepositoryPackages(
        downloader: (String) -> Unit,
        repositoryPackages: () -> List<LocalPackage>
    ): CmakeSearchContext {
        if (resultCmakeInstallFolder != null) {
            return this
        }
        info("Trying to locate CMake in local SDK repository.")

        // Iterate over the local packages and to identify the best match.
        repositoryPackages().onEach { pkg ->
            tryAcceptFoundCmake(
                pkg.location,
                convertSdkVersionToCmakeVersion(pkg.version),
                "in SDK"
            )
        }
        if (resultCmakeInstallFolder != null) {
            return this
        }

        // Cmake was not found in local packages. If the user asked the fork Cmake version, auto-download it.
        if (versionEquals(requestedCmakeVersion, forkCmakeReportedVersion)) {
            // The version is exactly the default version. Download it if possible.
            info("- Downloading '$forkCmakeSdkVersionRevision'.")
            downloader(FORK_CMAKE_SDK_VERSION)

            val res = repositoryPackages().find { versionEquals(it.version, forkCmakeSdkVersionRevision) }
            if (res != null) {
                tryAcceptFoundCmake(
                    res.location,
                    convertSdkVersionToCmakeVersion(res.version),
                    "in SDK"
                )
            }

            if (resultCmakeInstallFolder == null) {
                requestDownloadFromAndroidStudio = true
            }
            return this
        }

        return this
    }

    /**
     * Recognizes the special fork SDK version and converts to "3.6.0"
     */
    private fun convertSdkVersionToCmakeVersion(version: Revision): Revision {
        if (version == forkCmakeSdkVersionRevision) {
            return forkCmakeReportedVersion
        }
        return version
    }

    /**
     * Look in $PATH for CMakes to use. If there is a version number in build.gradle then that
     * version will be used if possible. If there is no version in build.gradle then we take the
     * highest version from the set of CMakes found.
     */
    internal fun tryFindInPath(
        cmakeVersionGetter: (File) -> Revision?,
        environmentPaths: () -> List<File>,
        tag : String
    ): CmakeSearchContext {
        if (resultCmakeInstallFolder != null) {
            return this
        }
        info("Trying to locate CMake $tag.")
        var found = false
        for (cmakeFolder in environmentPaths()) {
            try {
                val version = cmakeVersionGetter(cmakeFolder) ?: continue
                if (found) {
                    // Found a cmake.exe later in the path. Irrespective of whether it is a better match, or a total mismatch,
                    // we ignore it but issue a message.
                    info(
                        "- CMake $version was found $tag at $cmakeFolder after" +
                                " another version. Ignoring it."
                    )
                } else {
                    val cmakeInstallPath = cmakeFolder.parentFile
                    tryAcceptFoundCmake(cmakeInstallPath, version, tag)

                    // At this point, we found a cmake.exe on the PATH. We only look the first one.
                    // Any cmake.exe further down the path is ignored.
                    found = true
                }
            } catch (e: IOException) {
                warn("Could not execute cmake at '$cmakeFolder' to get version. Skipping.")
            }
        }
        return this
    }

    /**
     * If no suitable CMake was found then issue a diagnostic error. If the requested CMake version
     * looks like an SDK version of CMake then issue a specifically constructed message that Android
     * Studio can recognize and prompt the user to download.
     */
    internal fun issueVersionNotFoundError(): File? {
        if (resultCmakeInstallFolder != null && !requestDownloadFromAndroidStudio) {
            return resultCmakeInstallFolder
        }

        val unsuitableCMakes = if (unsuitableCmakeReasons.isEmpty()) ""
        else newline + unsuitableCmakeReasons.joinToString(newline)

        if (firstError != null) {
            // Throw an exception to trigger Android Studio to consider the error message for the
            // purposes of downloading CMake from the SDK.
            throw RuntimeException("$firstError$unsuitableCMakes")
        }


        if (dslVersionHasPlus()) {
            throw RuntimeException(
                "CMake '$requestedCmakeVersion' or higher was not found in " +
                        "PATH or by cmake.dir property.$unsuitableCMakes"
            )
        } else {
            throw RuntimeException(
                "CMake '$requestedCmakeVersion' was not found in " +
                        "PATH or by cmake.dir property.$unsuitableCMakes"
            )
        }
    }

}

/**
 * @return list of folders (as Files) retrieved from PATH environment variable and from Sdk
 * cmake folder.
 */
private fun getEnvironmentPaths(): List<File> {
    val envPath = System.getenv("PATH") ?: ""
    val pathSeparator = System.getProperty("path.separator").toRegex()
    return envPath
        .split(pathSeparator)
        .asSequence()
        .filter { it.isNotEmpty() }
        .map { File(it) }
        .toList()
}

/**
 * @return list of folders (as Files) based on the specific known canary versions of CMake.
 */
private fun getCanarySdkPaths(sdkRoot : File?) : List<File> {
    if (sdkRoot == null) {
        return listOf()
    }
    return canarySdkPaths
        .asSequence()
        .map { version -> File(File(File(sdkRoot, "cmake"), version), "bin") }
        .toList()
}

private fun getSdkCmakePackages(
    sdkHandler: SdkHandler,
    logger: ILogger
): List<LocalPackage> {
    val androidSdkHandler = AndroidSdkHandler.getInstance(sdkHandler.sdkFolder)
    val sdkManager = androidSdkHandler.getSdkManager(LoggerProgressIndicatorWrapper(logger))
    val packages = sdkManager.packages
    return packages.getLocalPackagesForPrefix(FD_CMAKE).toList()
}

private fun getCmakeRevisionFromExecutable(cmakeFolder: File): Revision? {
    if (!cmakeFolder.exists()) {
        return null
    }
    val cmakeExecutableName = if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
        "cmake.exe"
    } else {
        "cmake"
    }
    val cmakeExecutable = File(cmakeFolder, cmakeExecutableName)
    if (!cmakeExecutable.exists()) {
        return null
    }
    return CmakeUtils.getVersion(cmakeFolder)
}



/**
 * This is the correct find-path logic that has callback for external dependencies like errors,
 * version of CMake file, and so forth. This is the entry-point for unit testing.
 */
fun findCmakePathLogic(
    cmakeVersionFromDsl: String?,
    cmakePathFromLocalProperties: File?,
    downloader: (String) -> Unit,
    environmentPaths: () -> List<File>,
    canarySdkPaths: () -> List<File>,
    cmakeVersion: (File) -> Revision?,
    repositoryPackages: () -> List<LocalPackage>
): File? {
    return CmakeSearchContext(cmakeVersionFromDsl)
        .useDefaultCmakeVersionIfNecessary()
        .checkForCmakeVersionTooLow()
        .checkForCmakeVersionAdequatePrecision()
        .tryPathFromLocalProperties(cmakeVersion, cmakePathFromLocalProperties)
        .tryLocalRepositoryPackages(downloader, repositoryPackages)
        .tryFindInPath(cmakeVersion, environmentPaths, "in PATH")
        .tryFindInPath(cmakeVersion, canarySdkPaths, "in SDK canaries")
        .issueVersionNotFoundError()
}

/**
 * Locate CMake cmake path for the given build configuration.
 *
 * cmakeVersionFromDsl is the, possibly null, CMake version from the user's build.gradle.
 *   If it is null then a default version will be chosen.
 */
fun findCmakePath(
    cmakeVersionFromDsl: String?,
    sdkHandler: SdkHandler,
    logger: ILogger) : File? {
    return findCmakePathLogic(
            cmakeVersionFromDsl,
            sdkHandler.cmakePathInLocalProp,
            { version -> sdkHandler.installCMake(version) },
            { getEnvironmentPaths() },
            { getCanarySdkPaths(sdkHandler.sdkFolder) },
            { folder -> getCmakeRevisionFromExecutable(folder) },
            { getSdkCmakePackages(sdkHandler, logger) })
}