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

package com.android.build.gradle.integration;

import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.repository.io.FileOpUtils;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.util.InstallerUtil;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

@RunWith(BazelIntegrationTestSuiteRunner.class)
public class BazelIntegrationTestsSuite {

    public static final Path DATA_DIR;
    private static final ImmutableMap<String, Path> MAVEN_REPO_SOURCES;
    public static final ImmutableList<Path> MAVEN_REPOS;
    public static final Path NDK_IN_TMP;
    public static final Path GRADLE_USER_HOME;


    static {
        try {
            DATA_DIR = Files.createTempDirectory("data").toAbsolutePath();
            MAVEN_REPO_SOURCES = mavenRepos(DATA_DIR);
            MAVEN_REPOS = MAVEN_REPO_SOURCES.values().asList();
            NDK_IN_TMP = DATA_DIR.resolve("ndk").toAbsolutePath();
            GRADLE_USER_HOME = Files.createTempDirectory("gradleUserHome");

            System.setProperty("gradle.user.home", GRADLE_USER_HOME.toAbsolutePath().toString());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @BeforeClass
    public static void unzipOfflineRepo() throws Exception {
        for (Map.Entry<String, Path> mavenRepoSource : MAVEN_REPO_SOURCES.entrySet()) {
            unzip(mavenRepoSource.getValue(), mavenRepoSource.getKey());
        }
    }

    /**
     * Copies the NDK to a temporary directory.
     *
     * <p>This is a workaround for the fact that Ninja (used by some native code projects) doesn't
     * support paths longer than 32 segments. When running under Bazel, the temporary directory is a
     * couple of levels higher in the file system than "runfiles".
     */
    @BeforeClass
    public static void symlinkNdkToTmp() throws Exception {
        assertThat(NDK_IN_TMP).doesNotExist();

        try {
            Path ndk = new File(TestUtils.getSdk(), SdkConstants.FD_NDK).toPath();
            if (Files.exists(ndk)) {
                Files.createSymbolicLink(NDK_IN_TMP, ndk);
            }
        } catch (IllegalArgumentException e) {
            // this is thrown when getSdk() calls getWorkspaceFile() with a string that cannot be
            // found in the workspace directory. In this specific instance, we don't care, so don't
            // do anything about it. Some integration tests don't actually depend on the NDK but
            // do depend on this code.
        }
    }

    @AfterClass
    public static void cleanUp() throws Exception {
        DefaultGradleConnector.close();
        FileUtils.deletePath(DATA_DIR.toFile());
        Files.deleteIfExists(NDK_IN_TMP);
    }

    private static void unzip(@NonNull Path repoPath, @NonNull String zipName) throws IOException {
        File offlineRepoZip = TestUtils.getWorkspaceFile(zipName);

        Files.createDirectory(repoPath);

        InstallerUtil.unzip(
                offlineRepoZip,
                repoPath.toFile(),
                FileOpUtils.create(),
                offlineRepoZip.length(),
                new FakeProgressIndicator());
    }

    private static ImmutableSortedMap<String, Path> mavenRepos(Path parentDirectory) {
        ImmutableSortedMap.Builder<String, Path> builder = ImmutableSortedMap.naturalOrder();
        Set<String> shortNames = new HashSet<>();
        String property = System.getProperty("test.android.build.gradle.integration.repos");
        for (String item : Splitter.on(',').split(property)) {
            String shortName = item.substring(item.lastIndexOf('/') + 1, item.lastIndexOf('.'));
            if (!shortNames.add(shortName)) {
                throw new IllegalArgumentException(
                        "Repos should have unique file names. \n"
                                + "Duplicated name is "
                                + shortName
                                + "\n"
                                + "Injected property is "
                                + property
                                + ".");
            }
            builder.put(item, parentDirectory.resolve(shortName));
        }
        return builder.build();
    }
}
