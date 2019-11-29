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

package com.android.build.gradle.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.builder.model.Version;
import com.android.utils.JvmWideVariable;
import com.google.common.base.Verify;
import com.google.common.reflect.TypeToken;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.gradle.api.Project;
import org.junit.Test;

/** Test cases for {@link PluginInitializer}. */
public class PluginInitializerTest {

    @Test
    public void testVerifySamePluginVersion() {
        Project project1 = mock(Project.class);
        Project project2 = mock(Project.class);
        Project project3 = mock(Project.class);

        when(project1.getProjectDir()).thenReturn(new File("project1"));
        when(project2.getProjectDir()).thenReturn(new File("project2"));
        when(project3.getProjectDir()).thenReturn(new File("project3"));

        // Initialize the plugin version map once. (NOTE: the group or name of the variable must be
        // different from the one used in PluginInitializer since that variable may currently be
        // used by running integration tests.)
        ConcurrentMap<Object, String> projectToPluginVersionMap =
                Verify.verifyNotNull(
                        new JvmWideVariable<>(
                                        "PLUGIN_VERSION_CHECK_TEST",
                                        "PROJECT_TO_PLUGIN_VERSION",
                                        new TypeToken<ConcurrentMap<Object, String>>() {},
                                        ConcurrentHashMap::new)
                                .get());

        // Simulate loading the plugin
        PluginInitializer.verifySamePluginVersion(
                projectToPluginVersionMap, project1, Version.ANDROID_GRADLE_PLUGIN_VERSION);

        // Load the plugin again in the same project with the same version, expect failure
        try {
            PluginInitializer.verifySamePluginVersion(
                    projectToPluginVersionMap, project1, Version.ANDROID_GRADLE_PLUGIN_VERSION);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage())
                    .contains(
                            Version.ANDROID_GRADLE_PLUGIN_VERSION
                                    + " was already applied to this project");
        }

        // Load the plugin again in the same project with a different version, expect failure
        try {
            PluginInitializer.verifySamePluginVersion(projectToPluginVersionMap, project1, "1.2.3");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage())
                    .contains(
                            Version.ANDROID_GRADLE_PLUGIN_VERSION
                                    + " was already applied to this project");
        }

        // Load the plugin again in a different project with the same version, expect success
        PluginInitializer.verifySamePluginVersion(
                projectToPluginVersionMap, project2, Version.ANDROID_GRADLE_PLUGIN_VERSION);

        // Load the plugin again in a different project with a different version, expect failure
        try {
            PluginInitializer.verifySamePluginVersion(projectToPluginVersionMap, project3, "1.2.3");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage())
                    .contains(
                            "Using multiple versions of the Android Gradle plugin in the same build"
                                    + " is not allowed.");
        }

        // Reset the plugin version map at the end of the build
        projectToPluginVersionMap.clear();

        // Load the plugin again in the same project with the same version, and in a different
        // build, expect success
        simulateRunningBuild(
                projectToPluginVersionMap, project1, Version.ANDROID_GRADLE_PLUGIN_VERSION);
        simulateRunningBuild(
                projectToPluginVersionMap, project1, Version.ANDROID_GRADLE_PLUGIN_VERSION);

        // Load the plugin again in the same project with a different version, and in a different
        // build, expect success
        simulateRunningBuild(
                projectToPluginVersionMap, project1, Version.ANDROID_GRADLE_PLUGIN_VERSION);
        simulateRunningBuild(projectToPluginVersionMap, project1, "1.2.3");

        // Load the plugin again in a different project with the same version, and in a different
        // build, expect success
        simulateRunningBuild(
                projectToPluginVersionMap, project1, Version.ANDROID_GRADLE_PLUGIN_VERSION);
        simulateRunningBuild(
                projectToPluginVersionMap, project2, Version.ANDROID_GRADLE_PLUGIN_VERSION);

        // Load the plugin again in a different project with a different version, and in a different
        // build, expect success
        simulateRunningBuild(
                projectToPluginVersionMap, project1, Version.ANDROID_GRADLE_PLUGIN_VERSION);
        simulateRunningBuild(projectToPluginVersionMap, project2, "1.2.3");

        // Load the plugin twice for different project instances having the same project path,
        // expect success (regression test for bug 65502981)
        Project sameProject1 = mock(Project.class);
        when(sameProject1.getProjectDir()).thenReturn(new File("project1"));
        simulateRunningBuild(
                projectToPluginVersionMap, project1, Version.ANDROID_GRADLE_PLUGIN_VERSION);
        simulateRunningBuild(
                projectToPluginVersionMap, sameProject1, Version.ANDROID_GRADLE_PLUGIN_VERSION);
    }

    private static void simulateRunningBuild(
            @NonNull ConcurrentMap<Object, String> projectToPluginVersionMap,
            @NonNull Project project,
            @NonNull String pluginVersion) {
        PluginInitializer.verifySamePluginVersion(
                projectToPluginVersionMap, project, pluginVersion);
        projectToPluginVersionMap.clear();
    }
}
