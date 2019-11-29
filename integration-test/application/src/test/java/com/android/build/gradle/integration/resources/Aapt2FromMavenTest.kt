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

package com.android.build.gradle.integration.resources

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.internal.res.getAapt2FromMaven
import com.android.build.gradle.internal.res.namespaced.registerAaptService
import com.android.build.gradle.internal.res.namespaced.useAaptDaemon
import com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry
import com.android.builder.internal.aapt.v2.Aapt2RenamingConventions
import com.android.ide.common.resources.CompileResourceRequest
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.StdLogger
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assume
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.test.assertNotNull

/** Sanity test for consuming AAPT2 from maven. */
class Aapt2FromMavenTest {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    /** Verify that the the artifact provided by the [getAapt2FromMaven] method is usable. */
    // Turned off until Gradle supports released version of Groovy : bug 117293097
    @Test
    @Ignore
    fun sanityTest() {
        // https://issuetracker.google.com/77321151
        Assume.assumeFalse(SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS)

        val project = ProjectBuilder().withProjectDir(temporaryFolder.newFolder()).build()
        val artifact = getAapt2FromMavenForTest(project)

        // There should be an aapt artifact in the resolved file collection.
        val dir = artifact.singleFile.toPath()
        assertThat(dir).isDirectory()
        assertThat(dir.resolve(SdkConstants.FN_AAPT2)).isFile()

        // getAapt2FromMaven should be idempotent
        val dir2 = getAapt2FromMavenForTest(project).singleFile.toPath()
        assertThat(dir2).isEqualTo(dir)

        // The AAPT2 provided should be usable.
        testRunningAapt2FromMaven(artifact)
    }

    private fun getAapt2FromMavenForTest(project: Project): FileCollection {

        val artifact = getAapt2FromMaven(
            project = project
        )

        assertNotNull(artifact, "Artifact view should be created here.")

        // Add repo after calling getAapt2FromMaven to check that it is not resolved early.
        GradleTestProject.getLocalRepositories().forEach { repoPath ->
            project.repositories.add(project.repositories.maven {
                it.url = repoPath.toUri()
            })
        }
        return artifact
    }

    private fun testRunningAapt2FromMaven(artifact: FileCollection) {
        val registry = WorkerActionServiceRegistry()

        val serviceKey =
            registerAaptService(artifact, null, StdLogger(StdLogger.Level.INFO), registry)

        val outDir = temporaryFolder.newFolder()
        val inFile = createFileToCompile()
        useAaptDaemon(serviceKey, registry) { daemon ->
            daemon.compile(
                CompileResourceRequest(
                    inputFile = inFile,
                    outputDirectory = outDir

                ), logger = StdLogger(StdLogger.Level.INFO)
            )
        }

        assertThat(outDir.toPath().resolve(Aapt2RenamingConventions.compilationRename(inFile)))
            .isFile()
    }

    private fun createFileToCompile() =
        temporaryFolder.newFolder().toPath()
            .resolve("values")
            .resolve("strings.xml")
            .apply {
                Files.createDirectories(parent)
                Files.write(this, "<resources></resources>".toByteArray(StandardCharsets.UTF_8))
            }
            .toFile()
}
