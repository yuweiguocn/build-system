/*
 * Copyright (C) 2017 The Android Open Source Project
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

@file:JvmName("Main")
package com.android.tools.build.debug.model

import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.fixture.ModelBuilder
import com.android.build.gradle.integration.common.fixture.ModelContainer
import com.android.builder.model.AndroidProject
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import java.io.File

/**
 * Command line entry point to debug a model coming from AGP.
 *
 * This does a text output of the model for easy debugging of what's in the model without
 * using the debugger via Studio or an integration test.
 *
 * The project to build the model for can be passed as a command line argument or via the
 * env variable ANDROID_PROJECT_LOC. The latter is easier when running this directly via gradle
 */

fun main(args : Array<String>) {
    val projectLocation = getProjectLocation(args)
    val connection = getProjectConnection(projectLocation)

    try {
        val modelBuilder = ModelBuilder(connection,
                { _ -> },
                projectLocation.toPath(),
                null,
                GradleTestProjectBuilder.MemoryRequirement.useDefault())

        val models: ModelContainer<AndroidProject> = modelBuilder
                .withSdkInLocalProperties()
                .level(AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD)
                //.level(AndroidProject.MODEL_LEVEL_4_NEW_DEP_MODEL)
                .ignoreSyncIssues()
                .fetchAndroidProjects()

        val action: (Map<String, AndroidProject>) -> Unit = { map ->
            map.keys.forEach {
                println("  project: $it")
            }
        }

        // TODO print the model
        models.modelMaps.keys.forEach {
            println("buildId: $it")

            val map = models.modelMaps[it]!!
            action.invoke(map)
        }

        println("rootBuild")
        action.invoke(models.rootBuildModelMap)

    } catch (t: Throwable) {
        t.printStackTrace()

    } finally {
        connection.close()
    }
}

/** Returns a Gradle project Connection  */
private fun getProjectConnection(projectLocation: File): ProjectConnection {
    return GradleConnector.newConnector()
            .forProjectDirectory(projectLocation)
            .connect()
}

private fun getProjectLocation(args: Array<String>): File {
    if (args.isEmpty()) {
        val locStr = System.getenv()["ANDROID_PROJECT_LOC"]
        if (locStr != null) {
            return File(locStr)
        }

        throw RuntimeException("No args and no ANDROID_PROJECT_LOC env var set")
    }

    return File(args[0])
}
