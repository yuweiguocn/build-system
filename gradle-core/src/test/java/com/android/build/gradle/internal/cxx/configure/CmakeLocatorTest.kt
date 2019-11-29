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

import com.android.repository.Revision
import com.android.repository.api.LocalPackage
import com.android.repository.testframework.FakePackage
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.io.IOException

class CmakeLocatorTest {
    private val newline = System.lineSeparator()
    private val slash = File.separator

    private fun fakeLocalPackageOf(path: String, revision: String): FakePackage.FakeLocalPackage {
        // path is like p;1.1
        val result = FakePackage.FakeLocalPackage(path)
        result.setRevision(Revision.parseRevision(revision))
        return result
    }

    data class FindCmakeEncounter(
        val errors: MutableList<String> = mutableListOf(),
        val warnings: MutableList<String> = mutableListOf(),
        val info: MutableList<String> = mutableListOf(),
        var environmentPathsRetrieved: Boolean = false,
        var sdkPackagesRetrieved: Boolean = false,
        var downloadRemote: Boolean = false,
        var result: String? = null,
        var downloadAttempts: Int = 0
    )

    private fun findCmakePath(
        cmakeVersionFromDsl: String?,
        environmentPaths: () -> List<File> = { listOf() },
        cmakePathFromLocalProperties: File? = null,
        cmakeVersion: (File) -> Revision? = { _ -> null },
        repositoryPackages: () -> List<LocalPackage> = { listOf() },
        downloader: () -> Unit = {}
    ): FindCmakeEncounter {
        val encounter = FindCmakeEncounter()
        RecordingLoggingEnvironment().use { logger ->
            val fileResult = findCmakePathLogic(
                cmakeVersionFromDsl = cmakeVersionFromDsl,
                cmakePathFromLocalProperties = cmakePathFromLocalProperties,

                environmentPaths = {
                    encounter.environmentPathsRetrieved = true
                    environmentPaths()
                },
                canarySdkPaths = { listOf() },
                cmakeVersion = cmakeVersion,
                repositoryPackages = {
                    encounter.sdkPackagesRetrieved = true
                    repositoryPackages()
                },
                downloader = {
                    encounter.downloadAttempts = encounter.downloadAttempts + 1
                    downloader()
                }
            )
            if (fileResult != null) {
                encounter.result = fileResult.toString().replace("\\", "/")
            }
            if (encounter.result != null) {
                // Should be the cmake install folder without the "bin"
                assertThat(encounter.result!!.endsWith("bin")).isFalse()
            }
            encounter.errors += logger.errors
            encounter.warnings += logger.warnings
            encounter.info += logger.infos
        }

        return encounter
    }

    private fun expectException(message: String, action: () -> Unit) {
        try {
            action()
            throw RuntimeException("expected exception")
        } catch (e: Throwable) {
            if (message != e.message) {
                println("Expected: $message")
                println("Actual: ${e.message}")
            }
            assertThat(e).hasMessageThat().isEqualTo(message)
        }
    }

    /**
     * User request: "3.bob" or "+"
     * Candidates from Local Repository: "3.6.4111459"
     * Result: Invalid revision error is issued. Default version "3.6.4111459" is selected.
     */
    private fun dslVersionInvalidTestCase(cmakeVersion: String) {
        val localCmake = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = cmakeVersion,
            repositoryPackages = { listOf(localCmake) })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/sdk/cmake/3.6.4111459"
        )  // This is a fallback.
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors.single()).isEqualTo(
            "CMake version '$cmakeVersion' is not formatted correctly."
        )
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    @Test
    fun dslVersionInvalid1() {
        dslVersionInvalidTestCase("3.bob")
    }

    @Test
    fun dslVersionInvalid2() {
        dslVersionInvalidTestCase("+")
    }

    /**
     * Verifies that versions below 3.7 issue an error.
     */
    private fun dslVersionNumberTooLowTestCase(cmakeVersion: String) {
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.2")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = cmakeVersion,
            repositoryPackages = { listOf(threeTen, threeSix) })
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors.single()).isEqualTo(
            "CMake version '" + cmakeVersion.removeSuffix("+") + "' is too low. Use 3.7.0 or higher."
        )
        assertThat(encounter.result).isNotNull()  // Falls back to either 3.6 or 3.10.
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    @Test
    fun dslVersionNumberTooLow1() {
        dslVersionNumberTooLowTestCase("3.2")
    }

    @Test
    fun dslVersionNumberTooLow2() {

        dslVersionNumberTooLowTestCase("2.2")
    }

    @Test
    fun dslVersionNumberTooLow3() {
        dslVersionNumberTooLowTestCase("3.2+")
    }

    @Test
    fun dslVersionNumberTooLow4() {
        dslVersionNumberTooLowTestCase("2.2+")
    }

    /**
     * Verifies that versions without micro precision issue an error.
     */
    private fun dslVersionTooFewPartsTestCase(cmakeVersion: String) {
        val cmakeVersionWithoutPlus = cmakeVersion.removeSuffix("+")
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.2")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = cmakeVersion,
            repositoryPackages = { listOf(threeTen, threeSix) }
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors.single()).isEqualTo(
            "CMake version '$cmakeVersionWithoutPlus' does not have enough precision. Use major.minor.micro in version."
        )
        assertThat(encounter.result).isNotNull()  // This is a fallback
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    @Test
    fun dslVersionTooFewParts1() {
        dslVersionTooFewPartsTestCase("4")
    }

    @Test
    fun dslVersionTooFewParts2() {
        dslVersionTooFewPartsTestCase("4.12")
    }

    @Test
    fun dslVersionTooFewParts3() {
        dslVersionTooFewPartsTestCase("4.12+")
    }

    @Test
    fun dslVersionTooFewParts4() {
        dslVersionTooFewPartsTestCase("4.12+")
    }

    /**
     * User request: "3.12.0"
     * Candidates from Local Properties: "3.12.0"
     * Result: "3.12.0" from local properties is selected.
     */
    private fun findByCmakeDirTestCase(cmakeVersion: String) {
        val encounter = findCmakePath(
            cmakeVersionFromDsl = cmakeVersion,
            cmakePathFromLocalProperties = File("/a/b/c/cmake"),
            environmentPaths = { listOf(File("/d/e/f")) },
            cmakeVersion = { folder ->
                if (folder.toString().replace("\\", "/") == "/a/b/c/cmake/bin") {
                    Revision.parseRevision("3.12.0")
                } else {
                    null
                }
            })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/a/b/c/cmake"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    @Test
    fun findByCmakeDir1() {
        findByCmakeDirTestCase("3.12.0")
    }

    @Test
    fun findByCmakeDir2() {
        findByCmakeDirTestCase("3.11.0+")
    }

    /**
     * User request: "3.13.0"
     * Candidates from Local Properties: "3.12.0"
     * Result: Error. No matches found.
     */
    @Test
    fun findByCmakeDirWrongVersion() {
        expectException(
            "CMake '3.12.0' found via cmake.dir='${slash}a${slash}b${slash}c${slash}cmake' does not match " +
                    "requested version '3.13.0'.$newline" +
                    "- CMake '3.12.0' found from cmake.dir did not match requested version '3.13.0'."
        ) {
            findCmakePath(
                cmakeVersionFromDsl = "3.13.0",
                cmakePathFromLocalProperties = File("/a/b/c/cmake"),
                cmakeVersion = { folder ->
                    if (folder.toString().replace("\\", "/") == "/a/b/c/cmake/bin") {
                        Revision.parseRevision("3.12.0")
                    } else {
                        null
                    }
                })
        }
    }

    /**
     * User request: default
     * Candidates from Local Properties: "3.12"
     * Candidates from Local Repository: "3.6.4111459"
     * Result: The "3.12" version from cmake.dir is prioritized over the default.
     */
    @Test
    fun findByCmakeDirNoVersionInBuildGradle() {
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = null,
            cmakePathFromLocalProperties = File("/a/b/c/cmake"),
            environmentPaths = { listOf(File("/d/e/f")) },
            repositoryPackages = { listOf(threeSix) },
            cmakeVersion = { folder ->
                if (folder.toString().replace("\\", "/") == "/a/b/c/cmake/bin") {
                    Revision.parseRevision("3.12")
                } else {
                    null
                }
            })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/a/b/c/cmake"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * User request: default
     * Candidates from Local Properties: "not a valid cmake directory"
     * Candidates from Local Repository: "3.6.4111459"
     * Result: 3.6.4111459 is selected from local repository.
     *
     * In this scenario, user specified path in cmake.dir, but the directory does not contain a
     * valid cmake version, so the cmake.dir input is ignored.
     */
    @Test
    fun invalidCmakeDir() {
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = null,
            cmakePathFromLocalProperties = File("/a/b/c/cmake/bin-mistake"),
            repositoryPackages = { listOf(threeSix) },
            cmakeVersion = { folder ->
                if (folder.toString() == "/a/b/c/cmake/bin") {
                    Revision.parseRevision("3.12.0")
                } else {
                    null
                }
            })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!).isEqualTo(
            "/sdk/cmake/3.6.4111459"
        ) // This is a fallback
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).containsExactly(
            "Could not get version from " +
                    "cmake.dir path '${slash}a${slash}b${slash}c${slash}cmake${slash}bin-mistake'."
        )
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * User request: null, "3.6.4111459" or "3.6.0-rc1"
     * Candidates from Local Repository: "3.6.4111459", "3.10.2"
     * Result: "3.6.4111459" from local repository is selected. Higher version 3.10.2 is ignored.
     */
    private fun sdkCmakeExistsLocallyRequestLowerVersionTestCase(cmakeVersion: String?) {
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.2", "3.10.2")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = cmakeVersion,
            repositoryPackages = { listOf(threeSix, threeTen) })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/sdk/cmake/3.6.4111459"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    @Test
    fun sdkCmakeExistsLocallyRequestLowerVersion1() {
        sdkCmakeExistsLocallyRequestLowerVersionTestCase(null)
    }

    @Test
    fun sdkCmakeExistsLocallyRequestLowerVersion2() {

        sdkCmakeExistsLocallyRequestLowerVersionTestCase("3.6.0")
    }

    @Test
    fun sdkCmakeExistsLocallyRequestLowerVersion3() {
        sdkCmakeExistsLocallyRequestLowerVersionTestCase("3.6.0-rc1")
    }

    /**
     * User request: "3.10.2" or "3.10.2-rc1"
     * Candidates from Local Repository: "3.6.4111459", "3.10.2"
     * Result: "3.10.2" from local repository is selected. Lower version 3.6.4111459 is ignored.
     */
    private fun sdkCmakeExistsLocallyRequestHigherVersionTestCase(cmakeVersion: String?) {
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.2", "3.10.2")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = cmakeVersion,
            repositoryPackages = { listOf(threeSix, threeTen) })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/sdk/cmake/3.10.2"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    @Test
    fun sdkCmakeExistsLocallyRequestHigherVersion1() {
        sdkCmakeExistsLocallyRequestHigherVersionTestCase("3.10.2")
    }

    @Test
    fun sdkCmakeExistsLocallyRequestHigherVersion2() {
        sdkCmakeExistsLocallyRequestHigherVersionTestCase("3.10.2-rc1")
    }

    /**
     * User request: "3.10.2"
     * Candidates from Local Repository: "3.8.0"
     * Result: No matches.
     */
    private fun sdkCmakeNoExactMatchTestCase(cmakeVersion: String) {
        val localCmake = fakeLocalPackageOf("cmake;3.8.0", "3.8.0")
        expectException(
            "CMake '$cmakeVersion' was not found in PATH or by cmake.dir property.$newline" +
                    "- CMake '3.8.0' found in SDK did not match requested version '$cmakeVersion'."
        )
        {
            findCmakePath(
                cmakeVersionFromDsl = cmakeVersion,
                repositoryPackages = { listOf(localCmake) })
        }
    }

    @Test
    fun sdkCmakeNoExactMatch1() {
        sdkCmakeNoExactMatchTestCase("3.10.2")
    }

    @Test
    fun sdkCmakeNoExactMatch2() {
        sdkCmakeNoExactMatchTestCase("3.7.0")
    }

    /**
     * User request: default, "3.6.0", or "3.6.0-rc1"
     * Candidates from Local Repository: "3.10.2"
     * Downloader: Null downloader that does not download anything.
     * Result: Default version not found. Download attempt from SDK failed. Higher version 3.10.2 ignored.
     */
    private fun sdkCmakeAutoInstallFailsTestCase(cmakeVersion: String?) {
        val localCmake = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.2")
        val cmakeVersionOrDefault = cmakeVersion ?: "3.6.0"
        expectException(
            "CMake '$cmakeVersionOrDefault' was not found in PATH or by cmake.dir property.$newline" +
                    "- CMake '3.10.2' found in SDK did not match requested version '$cmakeVersionOrDefault'."
        ) {
            findCmakePath(
                cmakeVersionFromDsl = cmakeVersion,
                repositoryPackages = { listOf(localCmake) })
        }
    }

    @Test
    fun sdkCmakeAutoInstallFails1() {
        sdkCmakeAutoInstallFailsTestCase(null)
    }

    @Test
    fun sdkCmakeAutoInstallFails2() {
        sdkCmakeAutoInstallFailsTestCase("3.6.0")
    }

    @Test
    fun sdkCmakeAutoInstallFails3() {
        sdkCmakeAutoInstallFailsTestCase("3.6.0-rc1")
    }

    /**
     * User request: default, "3.6.0", or "3.6.0-rc1"
     * Candidates from Local Repository: "3.10.2"
     * Candidates from Path: "3.12.0", "3.13.0"
     * Downloader: Successfully downloads "3.6.0-rc2" from SDK.
     * Result: Default version not found. Download attempt from SDK succeeded. Version "3.6.4111459"
     * from local repository is selected. Higher version 3.10.2 is ignored. Versions from path are ignored.
     */
    private fun sdkCmakeAutoInstallSuccessTestCase(cmakeVersion: String?) {
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.2")
        val repositoryPackages = listOf(threeTen).toMutableList()
        val encounter = findCmakePath(
            cmakeVersionFromDsl = cmakeVersion,
            repositoryPackages = { repositoryPackages },
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/bin"),
                    File("/d/e/f/cmake/bin")
                )
            },
            cmakeVersion = { folder ->
                val folderPath = folder.toString().replace("\\", "/")
                when (folderPath) {
                    "/a/b/c/cmake/bin" ->
                        Revision.parseRevision("3.12.0")
                    "/d/e/f/cmake/bin" ->
                        Revision.parseRevision("3.13.0")
                    else -> null
                }
            },
            downloader = {
                repositoryPackages.add(
                    fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
                )
            }
        )
        assertThat(encounter.downloadAttempts).isEqualTo(1)
    }

    @Test
    fun sdkCmakeAutoInstallSuccess1() {
        sdkCmakeAutoInstallSuccessTestCase(null)
    }

    @Test
    fun sdkCmakeAutoInstallSuccess2() {
        sdkCmakeAutoInstallSuccessTestCase("3.6.0")
    }

    @Test
    fun sdkCmakeAutoInstallSuccess3() {
        sdkCmakeAutoInstallSuccessTestCase("3.6.0-rc1")
    }

    /**
     * User request: "3.6.4111459"
     * Candidates from Local Repository:  "3.6.0"
     * Result: An error is reported even though "3.6.4111459" is the SDK version of 3.6.0-rc2
     */
    @Test
    fun sdkCmakeInternalCmakeVersionRejected() {
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        expectException(
            "CMake '3.6.4111459' was not found in PATH or by cmake.dir property.$newline" +
                    "- CMake '3.6.0' found in SDK did not match requested version '3.6.4111459'."
        ) {
            findCmakePath(
                cmakeVersionFromDsl = "3.6.4111459",
                repositoryPackages = { listOf(threeSix) })
        }
    }

    /**
     * User request: "3.7.0+", "3.7.0-rc1+", "3.10.2+", "3.10.2-rc1+"
     * Canidates from Local Repository: 3.6.411459, 3.10.2
     * Result: 3.10.2 is selected, because it is higher and -rc1 is ignored.
     */
    private fun sdkCmakeWithPlusTestCase(cmakeVersion: String) {
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.411459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.2")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = cmakeVersion,
            repositoryPackages = { listOf(threeSix, threeTen) })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!).isEqualTo(
            "/sdk/cmake/3.10.4111459"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    @Test
    fun sdkCmakeWithPlus1() {
        sdkCmakeWithPlusTestCase("3.7.0+")
    }

    @Test
    fun sdkCmakeWithPlus2() {
        sdkCmakeWithPlusTestCase("3.7.0-rc1+")
    }

    @Test
    fun sdkCmakeWithPlus3() {
        sdkCmakeWithPlusTestCase("3.10.2+")
    }

    @Test
    fun sdkCmakeWithPlus4() {
        sdkCmakeWithPlusTestCase("3.10.2-rc1+")
    }

    /**
     * User request: "3.10.2+"
     * Candidates from Local Repository: "3.8.0"
     * Result: No matches.
     */
    @Test
    fun sdkCmakeWithPlusNoMatch() {
        val cmakeVersionWithPlus = "3.10.2+"
        val cmakeVersion = "3.10.2"
        val localCmake = fakeLocalPackageOf("cmake;3.8.0", "3.8.0")
        expectException(
            "CMake '$cmakeVersion' or higher was not found in PATH or by cmake.dir property.$newline" +
                    "- CMake '3.8.0' found in SDK could not satisfy requested version '$cmakeVersion' because it was lower."
        )
        {
            findCmakePath(
                cmakeVersionFromDsl = cmakeVersionWithPlus,
                repositoryPackages = { listOf(localCmake) })
        }
    }

    /**
     * User request: "3.12.0" or "3.12.0-rc1"
     * Candidates from path: "3.12.0-a" and "3.12.0-b"
     * Result: "3.12.0-a" from path is selected because it is the first on path, and "-rc1" is ignored.
     */
    private fun findByPathTestCase(cmakeVersion: String) {
        val encounter = findCmakePath(
            cmakeVersionFromDsl = cmakeVersion,
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/3.12.0-a/bin"),
                    File("/a/b/c/cmake/3.12.0-b/bin"),
                    File("/d/e/f")
                )
            },
            cmakeVersion = { folder ->
                when (folder.toString().replace("\\", "/")) {
                    "/a/b/c/cmake/3.12.0-a/bin" -> Revision.parseRevision("3.12.0")
                    "/a/b/c/cmake/3.12.0-b/bin" -> Revision.parseRevision("3.12.0")
                    else -> null
                }
            })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/a/b/c/cmake/3.12.0-a"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    @Test
    fun findByPath1() {
        findByPathTestCase("3.12.0")
    }

    @Test
    fun findByPath2() {
        findByPathTestCase("3.12.0-rc1")
    }

    /**
     * User request: "3.12.0"
     * Candidates from Path: "reading version throws IOException", "3.12.0"
     * Result: The first candidate on path is skipped. Selects "3.12.0" from path.
     */
    @Test
    fun findByPathCmakeExeThrowsException() {
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.12.0",
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/bin"),
                    File("/d/e/f/cmake/bin")
                )
            },
            cmakeVersion = { folder ->
                if (folder.toString().replace("\\", "/") == "/d/e/f/cmake/bin") {
                    Revision.parseRevision("3.12.0")
                } else {
                    throw IOException("Problem executing CMake.exe")
                }
            })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/d/e/f/cmake"
        )
        assertThat(encounter.warnings).containsExactly(
            "Could not execute cmake at " +
                    "'${slash}a${slash}b${slash}c${slash}cmake${slash}bin' to get version. Skipping."
        )
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }
}