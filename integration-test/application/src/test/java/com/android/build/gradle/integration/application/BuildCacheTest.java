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

package com.android.build.gradle.integration.application;
import static com.android.testutils.truth.FileSubject.assertThat;
import static com.google.common.base.Verify.verifyNotNull;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TestVersions;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.StringOption;
import com.android.builder.model.Version;
import com.android.utils.FileUtils;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Integration test for build cache. */
public class BuildCacheTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Before
    public void setUp() throws Exception {
        // Add a dependency on an external library (guava)
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\ndependencies {\n    compile 'com.google.guava:guava:18.0'\n}\n");
    }

    @Test
    public void testBuildCacheEnabled() throws Exception {
        File sharedBuildCacheDir = FileUtils.join(project.getTestDir(), "shared", "build-cache");
        File privateBuildCacheDir =
                new File(sharedBuildCacheDir, Version.ANDROID_GRADLE_PLUGIN_VERSION);

        // Make sure the parent directory of the shared build cache directory does not yet exist.
        // This is to test that the locking mechanism used by the build cache can work with
        // non-existent directories (and parent directories).
        assertThat(sharedBuildCacheDir.getParentFile()).doesNotExist();

        GradleTaskExecutor executor =
                project.executor()
                        .with(BooleanOption.ENABLE_BUILD_CACHE, true)
                        .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM, false)
                        .with(StringOption.BUILD_CACHE_DIR, sharedBuildCacheDir.getAbsolutePath());
        executor.run("clean", "assembleDebug");

        List<File> cachedEntryDirs =
                Arrays.stream(verifyNotNull(privateBuildCacheDir.listFiles()))
                        .filter(File::isDirectory) // Remove the lock files
                        .filter(f -> !containsAapt(f)) // Remove aapt2 cache
                        .collect(Collectors.toList());

        // only guava should be cached
        assertThat(cachedEntryDirs).hasSize(1);

        // Check the timestamps of the guava library's cached file to make
        // sure we actually copied one to the other and did not run pre-dexing twice to create the
        // two files
        File cachedGuavaDexFile = new File(cachedEntryDirs.get(0), "output");
        long cachedGuavaTimestamp = cachedGuavaDexFile.lastModified();

        executor.run("clean", "assembleDebug");

        cachedEntryDirs =
                Arrays.stream(verifyNotNull(privateBuildCacheDir.listFiles()))
                        .filter(File::isDirectory) // Remove the lock files
                        .filter(f -> !containsAapt(f)) // Remove aapt2 cache
                        .collect(Collectors.toList());
        assertThat(cachedEntryDirs).hasSize(1);
        // Assert that the cached file is unchanged
        assertThat(cachedGuavaDexFile).wasModifiedAt(cachedGuavaTimestamp);

        executor.run("cleanBuildCache");
        assertThat(sharedBuildCacheDir).exists();
        assertThat(privateBuildCacheDir).doesNotExist();
    }

    @Test
    public void testBuildCacheDisabled() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\nandroid.defaultConfig.minSdkVersion "
                        + TestVersions.SUPPORT_LIB_MIN_SDK
                        + "\n"
                        + "dependencies {\n"
                        + "    compile \"com.android.support:support-v13:${rootProject.supportLibVersion}\"\n"
                        + "}\n");

        File sharedBuildCacheDir = FileUtils.join(project.getTestDir(), "shared", "build-cache");
        File privateBuildCacheDir =
                new File(sharedBuildCacheDir, Version.ANDROID_GRADLE_PLUGIN_VERSION);
        assertThat(sharedBuildCacheDir.getParentFile()).doesNotExist();

        project.executor()
                .with(BooleanOption.ENABLE_BUILD_CACHE, false)
                .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM, false)
                .with(StringOption.BUILD_CACHE_DIR, sharedBuildCacheDir.getAbsolutePath())
                .run("clean", "assembleDebug");

        assertThat(sharedBuildCacheDir).doesNotExist();
        assertThat(privateBuildCacheDir).doesNotExist();
    }

    private static boolean containsAapt(File dir) {
        if (dir.isFile()) {
            return dir.getName().contains("libaapt2_jni");
        }
        return Arrays.stream(verifyNotNull(dir.listFiles())).anyMatch(BuildCacheTest::containsAapt);
    }
}
