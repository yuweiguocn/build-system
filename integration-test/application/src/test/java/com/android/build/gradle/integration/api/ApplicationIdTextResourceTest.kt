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

package com.android.build.gradle.integration.api

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Rule
import org.junit.Test

class ApplicationIdTextResourceTest {

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("instantAppSimpleProject")
        .withoutNdk()
        .create()

    @Test
    fun testGetApplicationIdResourceFromBase() {
        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile, """
                    android.defaultConfig.applicationId "newAppId"
                    """
        )
        TestFileUtils.appendToFile(
            project.getSubproject(":base").buildFile, """
                    android {
                        featureVariants.all { variant ->
                            def task = task("appId${"$"}variant.name")
                            task.dependsOn(variant.applicationIdTextResource)
                            task.doLast {
                                assert variant.applicationIdTextResource.asString().equals(
                                    "newAppId")
                            }
                        }
                    }
                    """
        )
        project.execute(":base:appIdDebugFeature")
    }

    @Test
    fun testGetApplicationIdResourceFromFeature() {
        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile, """
                    android.defaultConfig.applicationId "newAppId"
                    """
        )
        TestFileUtils.appendToFile(
            project.getSubproject(":feature").buildFile, """
                    android {
                        featureVariants.all { variant ->
                            def task = task("appId${"$"}variant.name")
                            task.dependsOn(variant.applicationIdTextResource)
                            task.doLast {
                                assert variant.applicationIdTextResource.asString().equals(
                                    "newAppId")
                            }
                        }
                    }
                    """
        )
        project.execute(":feature:appIdDebugFeature")
    }

    @Test
    fun testGetApplicationIdResourceFromApp() {
        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile, """
                    android.defaultConfig.applicationId "newAppId"
                    """
        )
        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile, """
                    android {
                        applicationVariants.all { variant ->
                            def task = task("appId${"$"}variant.name")
                            task.dependsOn(variant.applicationIdTextResource)
                            task.doLast {
                                assert variant.applicationId.equals(
                                    "newAppId")
                                assert variant.applicationIdTextResource.asString().equals(
                                    "newAppId")
                            }
                        }
                    }
                    """
        )
        project.execute(":app:appIdDebug")
    }
}