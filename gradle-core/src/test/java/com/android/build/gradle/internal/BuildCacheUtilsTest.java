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

import static com.android.build.gradle.internal.BuildCacheUtils.CACHE_USE_MARKER_FILE_NAME;
import static com.android.testutils.truth.FileSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.builder.model.Version;
import com.android.builder.utils.FileCache;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Unit tests for {@link BuildCacheUtils}. */
@SuppressWarnings("FieldCanBeLocal")
public class BuildCacheUtilsTest {

    @Rule public TemporaryFolder testDir = new TemporaryFolder();

    // Note: Since the tested class uses a global object (BuildSession), we need to use fake plugin
    // versions to avoid potential conflicts with integration tests which could be running in
    // parallel.
    @NonNull private final String fakePluginVersion = "1.2.3";
    @NonNull private final String oldPluginVersion1 = "1.0.1";
    @NonNull private final String oldPluginVersion2 = "1.0.2";
    @NonNull private final String oldPluginVersion3 = "1.0.3";
    @NonNull private final String currentPluginVersion = Version.ANDROID_GRADLE_PLUGIN_VERSION;
    @NonNull private final String futurePluginVersion = "100.0.0";

    @Test
    public void testCreateBuildCache_DirectorySet() throws IOException {
        File buildCacheDir = testDir.newFolder();
        Function<Object, File> pathToFileResolver = (path) -> new File(path.toString());
        Supplier<File> defaultBuildCacheDirSupplier =
                () -> {
                    fail("This should not run");
                    return null;
                };

        FileCache buildCache =
                BuildCacheUtils.createBuildCache(
                        buildCacheDir.getPath(),
                        pathToFileResolver,
                        defaultBuildCacheDirSupplier,
                        fakePluginVersion);
        assertThat(buildCache.getCacheDirectory())
                .isEqualTo(new File(buildCacheDir, fakePluginVersion));
    }

    @Test
    public void testCreateBuildCache_DirectoryNotSet() throws IOException {
        File defaultBuildCacheDir = testDir.newFolder();
        Function<Object, File> pathToFileResolver =
                (path) -> {
                    fail("This should not run");
                    return null;
                };
        Supplier<File> defaultBuildCacheDirSupplier = () -> defaultBuildCacheDir;

        FileCache buildCache =
                BuildCacheUtils.createBuildCache(
                        null, pathToFileResolver, defaultBuildCacheDirSupplier, fakePluginVersion);
        assertThat(buildCache.getCacheDirectory())
                .isEqualTo(new File(defaultBuildCacheDir, fakePluginVersion));
    }

    @Test
    public void testShouldRunCacheEviction() throws Exception {
        File cacheDir = testDir.newFolder();
        FileCache fileCache = FileCache.getInstanceWithMultiProcessLocking(cacheDir);

        // Request cache eviction, it should be allowed to run since this is the first request
        assertThat(BuildCacheUtils.shouldRunCacheEviction(fileCache, Duration.ofMinutes(1)))
                .isTrue();

        // Request cache eviction again, it should not be allowed to run since not enough time has
        // passed since the last request
        assertThat(BuildCacheUtils.shouldRunCacheEviction(fileCache, Duration.ofMinutes(1)))
                .isFalse();

        // Set a short cache eviction interval, wait for that period to pass, and request cache
        // eviction again, this time it should be allowed to run since enough time has passed since
        // the last request
        TestUtils.waitForFileSystemTick();
        assertThat(BuildCacheUtils.shouldRunCacheEviction(fileCache, Duration.ofMillis(1)))
                .isTrue();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void testDeleteOldCacheEntries() throws Exception {
        File cacheDir = testDir.newFolder();
        FileCache fileCache = FileCache.getInstanceWithMultiProcessLocking(cacheDir);
        FileCache.Inputs inputs1 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("input", "foo1")
                        .build();
        FileCache.Inputs inputs2 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("input", "foo2")
                        .build();
        FileCache.Inputs inputs3 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("input", "foo3")
                        .build();

        // Make the first cache entry look as if it was created 60 days ago
        fileCache.createFileInCacheIfAbsent(inputs1, (outputFile) -> {});
        File cacheEntryDir1 = fileCache.getFileInCache(inputs1).getParentFile();
        cacheEntryDir1.setLastModified(System.currentTimeMillis() - Duration.ofDays(60).toMillis());

        // Make the second cache entry look as if it was created 30 days ago
        fileCache.createFileInCacheIfAbsent(inputs2, (outputFile) -> {});
        File cacheEntryDir2 = fileCache.getFileInCache(inputs2).getParentFile();
        cacheEntryDir2.setLastModified(System.currentTimeMillis() - Duration.ofDays(30).toMillis());

        // Make the third cache entry without modifying its timestamp
        fileCache.createFileInCacheIfAbsent(inputs3, (outputFile) -> {});

        // Delete all the cache entries that are older than or as old as 31 days
        BuildCacheUtils.deleteOldCacheEntries(fileCache, Duration.ofDays(31));

        // Check that only the first cache entry is deleted
        assertThat(cacheEntryDir1).doesNotExist();
        assertThat(fileCache.cacheEntryExists(inputs2)).isTrue();
        assertThat(fileCache.cacheEntryExists(inputs3)).isTrue();

        // Delete all the cache entries that are older than or as old as 30 days
        BuildCacheUtils.deleteOldCacheEntries(fileCache, Duration.ofDays(30));

        // Check that only the third cache entry is kept
        assertThat(cacheEntryDir1).doesNotExist();
        assertThat(cacheEntryDir2).doesNotExist();
        assertThat(fileCache.cacheEntryExists(inputs3)).isTrue();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void testDeleteOldCacheDirectories() throws Exception {
        File sharedCacheDir = testDir.newFolder();

        // Make the first cache directory look as if it was created 60 days ago, and not used at all
        FileCache fileCache1 =
                FileCache.getInstanceWithMultiProcessLocking(
                        new File(sharedCacheDir, oldPluginVersion1));
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("input", "foo")
                        .build();
        fileCache1.createFileInCacheIfAbsent(inputs, (outputFile) -> {});
        fileCache1
                .getCacheDirectory()
                .setLastModified(System.currentTimeMillis() - Duration.ofDays(60).toMillis());

        // Make the second cache directory look as if it was created 60 days ago, and last used 30
        // days ago
        FileCache fileCache2 =
                FileCache.getInstanceWithMultiProcessLocking(
                        new File(sharedCacheDir, oldPluginVersion2));
        fileCache2.createFileInCacheIfAbsent(inputs, (outputFile) -> {});
        fileCache2
                .getCacheDirectory()
                .setLastModified(System.currentTimeMillis() - Duration.ofDays(60).toMillis());
        File cacheUseMarkerFile =
                new File(fileCache2.getCacheDirectory(), CACHE_USE_MARKER_FILE_NAME);
        cacheUseMarkerFile.createNewFile();
        cacheUseMarkerFile.setLastModified(
                System.currentTimeMillis() - Duration.ofDays(30).toMillis());

        // Make the third cache directory without modifying its timestamp
        FileCache fileCache3 =
                FileCache.getInstanceWithMultiProcessLocking(
                        new File(sharedCacheDir, oldPluginVersion3));
        fileCache3.createFileInCacheIfAbsent(inputs, (outputFile) -> {});

        // Create some random directory inside the shared cache directory and make sure it won't be
        // deleted
        File notDeletedDir = new File(sharedCacheDir, "foo");
        FileUtils.mkdirs(notDeletedDir);

        // Delete all the cache directories that are older than or as old as 31 days
        BuildCacheUtils.deleteOldCacheDirectories(sharedCacheDir, Duration.ofDays(31));

        // Check that only the first cache directory is deleted
        assertThat(fileCache1.getCacheDirectory()).doesNotExist();
        assertThat(fileCache2.cacheEntryExists(inputs)).isTrue();
        assertThat(fileCache3.cacheEntryExists(inputs)).isTrue();
        assertThat(notDeletedDir).exists();

        // Delete all the cache directories that are older than or as old as 30 days
        BuildCacheUtils.deleteOldCacheDirectories(sharedCacheDir, Duration.ofDays(30));

        // Check that only the third cache directory is kept (plus the not-to-delete directory)
        assertThat(fileCache1.getCacheDirectory()).doesNotExist();
        assertThat(fileCache2.getCacheDirectory()).doesNotExist();
        assertThat(fileCache3.cacheEntryExists(inputs)).isTrue();
        assertThat(notDeletedDir).exists();

        // Make sure the cache directory for the current plugin version won't be deleted even if
        // it has not been used in a long time
        FileCache fileCache4 =
                FileCache.getInstanceWithMultiProcessLocking(
                        new File(sharedCacheDir, currentPluginVersion));
        fileCache4.createFileInCacheIfAbsent(inputs, (outputFile) -> {});
        fileCache4
                .getCacheDirectory()
                .setLastModified(System.currentTimeMillis() - Duration.ofDays(100).toMillis());
        BuildCacheUtils.deleteOldCacheDirectories(sharedCacheDir, Duration.ofDays(30));
        assertThat(fileCache4.cacheEntryExists(inputs)).isTrue();

        // Make sure a cache directory for a (hypothetical) newer plugin version won't be deleted
        // even if it has not been used in a long time
        FileCache fileCache5 =
                FileCache.getInstanceWithMultiProcessLocking(
                        new File(sharedCacheDir, futurePluginVersion));
        fileCache5.createFileInCacheIfAbsent(inputs, (outputFile) -> {});
        fileCache5
                .getCacheDirectory()
                .setLastModified(System.currentTimeMillis() - Duration.ofDays(100).toMillis());
        BuildCacheUtils.deleteOldCacheDirectories(sharedCacheDir, Duration.ofDays(30));
        assertThat(fileCache5.cacheEntryExists(inputs)).isTrue();
    }
}
