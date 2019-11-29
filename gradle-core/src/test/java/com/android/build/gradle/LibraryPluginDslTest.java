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

package com.android.build.gradle;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.android.build.gradle.api.TestVariant;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.fixture.BaseTestedVariant;
import com.android.build.gradle.internal.fixture.TestConstants;
import com.android.build.gradle.internal.fixture.TestProjects;
import com.android.build.gradle.internal.fixture.VariantChecker;
import com.android.build.gradle.internal.fixture.VariantCheckers;
import com.android.builder.errors.EvalIssueException;
import com.android.builder.model.SigningConfig;
import java.util.Set;
import org.gradle.api.Project;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for the public DSL of the Lib plugin ('com.android.library') */
public class LibraryPluginDslTest {
    @Rule public TemporaryFolder projectDirectory = new TemporaryFolder();
    private LibraryPlugin plugin;
    private LibraryExtension android;
    private VariantChecker checker;

    @Before
    public void setUp() throws Exception {
        Project project =
                TestProjects.builder(projectDirectory.newFolder("project").toPath())
                        .withPlugin(TestProjects.Plugin.LIBRARY)
                        .build();
        android = project.getExtensions().getByType(LibraryExtension.class);
        android.setCompileSdkVersion(TestConstants.COMPILE_SDK_VERSION);
        android.setBuildToolsVersion(TestConstants.BUILD_TOOL_VERSION);
        plugin = project.getPlugins().getPlugin(LibraryPlugin.class);
        checker = VariantCheckers.createLibraryChecker(android);
    }

    @Test
    public void testBasic() {
        plugin.createAndroidTasks();

        Set<BaseTestedVariant> variants = checker.getVariants();
        assertThat(variants).hasSize(2);

        Set<TestVariant> testVariants = android.getTestVariants();
        assertThat(testVariants).hasSize(1);

        checker.checkTestedVariant("debug", "debugAndroidTest", variants, testVariants);
        checker.checkNonTestedVariant("release", variants);
    }

    @Test
    public void testNewBuildType() {
        android.getBuildTypes().create("custom");
        plugin.createAndroidTasks();

        Set<BaseTestedVariant> variants = checker.getVariants();
        assertThat(variants).hasSize(3);

        Set<TestVariant> testVariants = android.getTestVariants();
        assertThat(testVariants).hasSize(1);

        checker.checkTestedVariant("debug", "debugAndroidTest", variants, testVariants);
        checker.checkNonTestedVariant("release", variants);
        checker.checkNonTestedVariant("custom", variants);
    }

    @Test
    public void testNewBuildType_testBuildType() {
        android.getBuildTypes().create("custom");
        android.setTestBuildType("custom");
        plugin.createAndroidTasks();

        Set<BaseTestedVariant> variants = checker.getVariants();
        assertThat(variants).hasSize(3);

        Set<TestVariant> testVariants = android.getTestVariants();
        assertThat(testVariants).hasSize(1);

        checker.checkTestedVariant("custom", "customAndroidTest", variants, testVariants);
        checker.checkNonTestedVariant("release", variants);
        checker.checkNonTestedVariant("debug", variants);
    }

    /**
     * test that debug build type maps to the SigningConfig object as the signingConfig container
     */
    @Test
    public void testDebugSigningConfig() throws Exception {
        android.getSigningConfigs().getByName("debug", debug -> debug.setStorePassword("foo"));

        SigningConfig signingConfig = android.getBuildTypes().getByName("debug").getSigningConfig();

        assertNotNull(signingConfig);
        assertEquals(android.getSigningConfigs().getByName("debug"), signingConfig);
        assertEquals("foo", signingConfig.getStorePassword());
    }

    @Test
    public void testResourceShrinker() throws Exception {
        BuildType debug = android.getBuildTypes().getByName("debug");
        debug.getPostprocessing().setRemoveUnusedResources(true);
        try {
            plugin.createAndroidTasks();
            fail("Expected resource shrinker error");
        } catch (EvalIssueException e) {
            assertThat(e).hasMessage("Resource shrinker cannot be used for libraries.");
        }

        debug.getPostprocessing().setRemoveUnusedResources(false);
        plugin.createAndroidTasks();
    }
}
