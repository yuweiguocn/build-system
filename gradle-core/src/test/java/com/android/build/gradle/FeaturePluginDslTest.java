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

package com.android.build.gradle;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.internal.fixture.TestConstants;
import com.android.build.gradle.internal.fixture.TestProjects;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for the public DSL of the Feature plugin ("com.android.feature") */
public class FeaturePluginDslTest {
    @Rule public TemporaryFolder projectDirectory = new TemporaryFolder();

    protected FeaturePlugin plugin;
    protected FeatureExtension android;
    protected Project project;

    @Before
    public void setUp() throws Exception {
        TestProjects.Plugin myPlugin = TestProjects.Plugin.FEATURE;
        project =
                TestProjects.builder(projectDirectory.newFolder("project").toPath())
                        .withPlugin(myPlugin)
                        .build();

        android =
                (FeatureExtension) project.getExtensions().getByType(myPlugin.getExtensionClass());
        android.setCompileSdkVersion(TestConstants.COMPILE_SDK_VERSION);
        android.setBuildToolsVersion(TestConstants.BUILD_TOOL_VERSION);
        plugin = (FeaturePlugin) project.getPlugins().getPlugin(myPlugin.getPluginClass());
    }

    @Test
    public void testTasks() throws Exception {
        plugin.createAndroidTasks();

        SortedSet<String> taskNames = project.getTasks().getNames();

        Set<String> unitTestTasks =
                taskNames.stream().filter(t -> t.startsWith("test")).collect(Collectors.toSet());

        assertThat(unitTestTasks)
                .containsExactly("test", "testDebugUnitTest", "testReleaseUnitTest");

        Set<String> connectedTasks =
                taskNames
                        .stream()
                        .filter(t -> t.startsWith("connected"))
                        .collect(Collectors.toSet());

        assertThat(connectedTasks)
                .containsExactly(
                        "connectedCheck", "connectedAndroidTest", "connectedDebugAndroidTest");
    }
}
