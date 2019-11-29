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

package com.android.builder.files;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.ide.common.resources.FileStatus;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.utils.FileUtils;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Closer;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for {@link IncrementalRelativeFileSets}.
 */
public class IncrementalRelativeFileSetsTest {

    /**
     * Temporary folder used for tests.
     */
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void readEmptyDirectory() {
        ImmutableMap<RelativeFile, FileStatus> set =
                IncrementalRelativeFileSets.fromDirectory(temporaryFolder.getRoot());

        assertEquals(0, set.size());
    }

    @Test
    public void readDirectory() throws Exception {
        File a = temporaryFolder.newFolder("a");
        File ab = new File(a, "ab");
        @SuppressWarnings("unused")
        boolean ignored1 = ab.createNewFile();
        File ac = new File(a, "ac");
        @SuppressWarnings("unused")
        boolean ignored2 = ac.createNewFile();
        File d = temporaryFolder.newFile("d");

        RelativeFile expectedB = new RelativeFile(temporaryFolder.getRoot(), ab);
        RelativeFile expectedC = new RelativeFile(temporaryFolder.getRoot(), ac);
        RelativeFile expectedD = new RelativeFile(temporaryFolder.getRoot(), d);

        ImmutableMap<RelativeFile, FileStatus> set =
                IncrementalRelativeFileSets.fromDirectory(temporaryFolder.getRoot());

        assertEquals(3, set.size());
        assertTrue(set.containsKey(expectedB));
        assertTrue(set.containsKey(expectedC));
        assertTrue(set.containsKey(expectedD));
        assertEquals(FileStatus.NEW, set.get(expectedB));
        assertEquals(FileStatus.NEW, set.get(expectedC));
        assertEquals(FileStatus.NEW, set.get(expectedD));
    }

    @Test
    public void readEmptyZip() throws Exception {
        File zipFile = new File(temporaryFolder.getRoot(), "foo");

        Closer closer = Closer.create();
        try {
            ZFile zf = closer.register(ZFile.openReadWrite(zipFile));
            zf.close();
        } catch (Throwable t) {
            throw closer.rethrow(t);
        } finally {
            closer.close();
        }

        ImmutableMap<RelativeFile, FileStatus> set = IncrementalRelativeFileSets.fromZip(zipFile);

        assertEquals(0, set.size());
    }

    @Test
    public void readZip() throws Exception {
        File zipFile = new File(temporaryFolder.getRoot(), "foo");

        RelativeFile expectedB = new RelativeFile(zipFile, "a/b");
        RelativeFile expectedC = new RelativeFile(zipFile, "a/c");
        RelativeFile expectedD = new RelativeFile(zipFile, "d");

        Closer closer = Closer.create();
        try {
            ZFile zf = closer.register(ZFile.openReadWrite(zipFile));
            zf.add("a/", new ByteArrayInputStream(new byte[0]));
            zf.add("a/b", new ByteArrayInputStream(new byte[0]));
            zf.add("a/c", new ByteArrayInputStream(new byte[0]));
            zf.add("d", new ByteArrayInputStream(new byte[0]));
            zf.close();
        } catch (Throwable t) {
            throw closer.rethrow(t);
        } finally {
            closer.close();
        }

        ImmutableMap<RelativeFile, FileStatus> set = IncrementalRelativeFileSets.fromZip(zipFile);

        assertEquals(3, set.size());
        assertTrue(set.containsKey(expectedB));
        assertTrue(set.containsKey(expectedC));
        assertTrue(set.containsKey(expectedD));
        assertEquals(FileStatus.NEW, set.get(expectedB));
        assertEquals(FileStatus.NEW, set.get(expectedC));
        assertEquals(FileStatus.NEW, set.get(expectedD));
    }

    @Test
    public void unionOfEmptySet() throws Exception {
        ImmutableMap<RelativeFile, FileStatus> set =
                IncrementalRelativeFileSets.union(new HashSet<>());
        assertEquals(0, set.size());
    }

    @Test
    public void unionOfSets() throws Exception {
        File zipFile = new File(temporaryFolder.getRoot(), "foo1");

        RelativeFile expectedB = new RelativeFile(zipFile, "a/b");
        RelativeFile expectedC = new RelativeFile(zipFile, "a/c");
        RelativeFile expectedD = new RelativeFile(zipFile, "d");

        ImmutableMap<RelativeFile, FileStatus> set1;
        ImmutableMap<RelativeFile, FileStatus> set2;

        Closer closer = Closer.create();
        try {
            ZFile zf1 = closer.register(ZFile.openReadWrite(zipFile));
            zf1.add("a/", new ByteArrayInputStream(new byte[0]));
            zf1.add("a/b", new ByteArrayInputStream(new byte[0]));
            zf1.add("d", new ByteArrayInputStream(new byte[0]));
            zf1.close();

            set1 = IncrementalRelativeFileSets.fromZip(zipFile);

            @SuppressWarnings("unused")
            boolean ignored = zipFile.delete();

            ZFile zf2 = closer.register(ZFile.openReadWrite(zipFile));
            zf2.add("a/", new ByteArrayInputStream(new byte[0]));
            zf2.add("a/c", new ByteArrayInputStream(new byte[0]));
            zf2.add("d", new ByteArrayInputStream(new byte[0]));
            zf2.close();

            set2 = IncrementalRelativeFileSets.fromZip(zipFile);
            set2 = ImmutableMap.copyOf(
                    Maps.transformValues(
                            set2,
                            Functions.constant(FileStatus.CHANGED)));
        } catch (Throwable t) {
            throw closer.rethrow(t);
        } finally {
            closer.close();
        }

        @SuppressWarnings("unchecked")
        ImmutableMap<RelativeFile, FileStatus> set =
                IncrementalRelativeFileSets.union(Sets.newHashSet(set1, set2));

        assertEquals(3, set.size());
        assertTrue(set.containsKey(expectedB));
        assertTrue(set.containsKey(expectedC));
        assertTrue(set.containsKey(expectedD));
        assertEquals(FileStatus.NEW, set.get(expectedB));
        assertEquals(FileStatus.CHANGED, set.get(expectedC));
        FileStatus dStatus = set.get(expectedD);
        assertTrue(dStatus == FileStatus.NEW || dStatus == FileStatus.CHANGED);
    }

    @Test
    public void baseDirectoryCountOnEmpty() {
        ImmutableMap<RelativeFile, FileStatus> set = ImmutableMap.copyOf(
                Maps.<RelativeFile, FileStatus>newHashMap());
        assertEquals(0, IncrementalRelativeFileSets.getBaseDirectoryCount(set));
    }

    @Test
    public void baseDirectoryCountOneDirectoryMultipleEntries() throws Exception {
        File dir = temporaryFolder.newFolder("foo");
        File f0 = new File(dir, "f0");
        assertTrue(f0.createNewFile());
        File f1 = new File(dir, "f1");
        assertTrue(f1.createNewFile());

        ImmutableMap<RelativeFile, FileStatus> set = IncrementalRelativeFileSets.fromDirectory(dir);
        assertEquals(1, IncrementalRelativeFileSets.getBaseDirectoryCount(set));
    }

    @Test
    public void baseDirectoryCountTwoDirectoriesTwoZipsMultipleEntries() throws Exception {
        File foo = temporaryFolder.newFolder("foo");
        File f0 = new File(foo, "f0");
        assertTrue(f0.createNewFile());
        File f1 = new File(foo, "f1");
        assertTrue(f1.createNewFile());

        File bar = temporaryFolder.newFolder("bar");
        File b0 = new File(bar, "b0");
        assertTrue(b0.createNewFile());
        File b1 = new File(bar, "b1");
        assertTrue(b1.createNewFile());

        File fooz = new File(temporaryFolder.getRoot(), "fooz");
        File barz = new File(temporaryFolder.getRoot(), "barz");

        Closer closer = Closer.create();

        try {
            ZFile foozZip = closer.register(ZFile.openReadWrite(fooz));
            foozZip.add("f0z", new ByteArrayInputStream(new byte[0]));
            foozZip.close();

            ZFile barzZip = closer.register(ZFile.openReadWrite(barz));
            barzZip.add("f0z", new ByteArrayInputStream(new byte[0]));
            barzZip.close();
        } catch (Throwable t) {
            throw closer.rethrow(t);
        } finally {
            closer.close();
        }

        ImmutableMap<RelativeFile, FileStatus> set1 = IncrementalRelativeFileSets.fromDirectory(bar);
        ImmutableMap<RelativeFile, FileStatus> set2 = IncrementalRelativeFileSets.fromDirectory(foo);
        ImmutableMap<RelativeFile, FileStatus> set3 = IncrementalRelativeFileSets.fromZip(fooz);
        ImmutableMap<RelativeFile, FileStatus> set4 = IncrementalRelativeFileSets.fromZip(barz);
        @SuppressWarnings("unchecked")
        ImmutableMap<RelativeFile, FileStatus> set =
                IncrementalRelativeFileSets.union(Sets.newHashSet(set1, set2, set3, set4));
        assertEquals(2, IncrementalRelativeFileSets.getBaseDirectoryCount(set));
    }

    @Test
    public void makingFromBaseFilesIgnoresDirectories() throws Exception {
        File foo = temporaryFolder.newFolder("foo");

        File f0 = new File(foo, "f0");
        assertTrue(f0.createNewFile());
        File bar = new File(foo, "bar");
        assertTrue(bar.mkdir());
        File f1 = new File(bar, "f1");
        assertTrue(f1.createNewFile());

        RelativeFile expectedF0 = new RelativeFile(temporaryFolder.getRoot(), f0);
        RelativeFile expectedF1 = new RelativeFile(temporaryFolder.getRoot(), f1);

        FileCacheByPath cache = new FileCacheByPath(temporaryFolder.newFolder());
        ImmutableMap<RelativeFile, FileStatus> set =
                IncrementalRelativeFileSets.makeFromBaseFiles(
                        Collections.singleton(temporaryFolder.getRoot()),
                        ImmutableMap.of(
                                f0, FileStatus.NEW,
                                f1, FileStatus.NEW,
                                bar, FileStatus.NEW),
                        cache,
                        new HashSet<>(),
                        IncrementalRelativeFileSets.FileDeletionPolicy
                                .ASSUME_NO_DELETED_DIRECTORIES);

        assertEquals(2, set.size());
        assertTrue(set.containsKey(expectedF0));
        assertTrue(set.containsKey(expectedF1));
        assertEquals(FileStatus.NEW, set.get(expectedF0));
        assertEquals(FileStatus.NEW, set.get(expectedF1));
    }

    @Test
    public void makingFromBaseFilesRejectsDeletedFiles() throws Exception {
        File foo = temporaryFolder.newFolder("foo");
        File bar = new File(foo, "bar");

        FileCacheByPath cache = new FileCacheByPath(temporaryFolder.newFolder());
        try {
            IncrementalRelativeFileSets.makeFromBaseFiles(
                    Collections.singleton(temporaryFolder.getRoot()),
                    ImmutableMap.of(bar, FileStatus.REMOVED),
                    cache,
                    new HashSet<>(),
                    IncrementalRelativeFileSets.FileDeletionPolicy.DISALLOW_FILE_DELETIONS);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertThat(e)
                    .hasMessage(
                            String.format(
                                    "Changes include a deleted file ('%s'), which is not allowed.",
                                    bar.getAbsolutePath()));
        }
    }

    @Test
    public void makeFromDirectoryIgnoresDirectories() throws Exception {
        File foo = temporaryFolder.newFolder("foo");
        File f0 = new File(foo, "f0");
        assertTrue(f0.createNewFile());
        File bar = new File(foo, "bar");
        assertTrue(bar.mkdir());
        File f1 = new File(bar, "f1");
        assertTrue(f1.createNewFile());

        RelativeFile expectedF0 = new RelativeFile(temporaryFolder.getRoot(), f0);
        RelativeFile expectedF1 = new RelativeFile(temporaryFolder.getRoot(), f1);

        ImmutableMap<RelativeFile, FileStatus> set =
                IncrementalRelativeFileSets.fromDirectory(temporaryFolder.getRoot());

        assertEquals(2, set.size());
        assertTrue(set.containsKey(expectedF0));
        assertTrue(set.containsKey(expectedF1));
        assertEquals(FileStatus.NEW, set.get(expectedF0));
        assertEquals(FileStatus.NEW, set.get(expectedF1));
    }

    @Test
    public void makingFromCacheNewZip() throws Exception {
        File cacheDir = temporaryFolder.newFolder();
        FileCacheByPath cache = new FileCacheByPath(cacheDir);

        File foo = new File(temporaryFolder.getRoot(), "foo");
        try (ZFile zffooz = ZFile.openReadWrite(foo)) {
            zffooz.add("f0z", new ByteArrayInputStream(new byte[0]));
            zffooz.add("f1z", new ByteArrayInputStream(new byte[0]));
        }

        Set<Runnable> updates = new HashSet<>();
        ImmutableMap<RelativeFile, FileStatus> m =
                IncrementalRelativeFileSets.fromZip(foo, cache, updates);
        assertEquals(2, m.size());

        RelativeFile f0z = new RelativeFile(foo, "f0z");
        assertTrue(m.containsKey(f0z));
        assertEquals(m.get(f0z), FileStatus.NEW);

        RelativeFile f1z = new RelativeFile(foo, "f1z");
        assertTrue(m.containsKey(f1z));
        assertEquals(m.get(f1z), FileStatus.NEW);

        updates.forEach(Runnable::run);
        m = IncrementalRelativeFileSets.fromZip(foo, cache, updates);
        assertEquals(0, m.size());
    }

    @Test
    public void makingFromCacheDeletedZip() throws Exception {
        File cacheDir = temporaryFolder.newFolder();
        FileCacheByPath cache = new FileCacheByPath(cacheDir);

        File foo = new File(temporaryFolder.getRoot(), "foo");
        try (ZFile zffooz = ZFile.openReadWrite(foo)) {
            zffooz.add("f0z", new ByteArrayInputStream(new byte[0]));
            zffooz.add("f1z", new ByteArrayInputStream(new byte[0]));
        }

        cache.add(foo);
        FileUtils.delete(foo);

        Set<Runnable> updates = new HashSet<>();
        ImmutableMap<RelativeFile, FileStatus> m =
                IncrementalRelativeFileSets.fromZip(foo, cache, updates);
        assertEquals(2, m.size());

        RelativeFile f0z = new RelativeFile(foo, "f0z");
        assertTrue(m.containsKey(f0z));
        assertEquals(m.get(f0z), FileStatus.REMOVED);

        RelativeFile f1z = new RelativeFile(foo, "f1z");
        assertTrue(m.containsKey(f1z));
        assertEquals(m.get(f1z), FileStatus.REMOVED);

        updates.forEach(Runnable::run);
        m = IncrementalRelativeFileSets.fromZip(foo, cache, updates);
        assertEquals(0, m.size());
    }

    @Test
    public void makingFromCacheUpdatedZip() throws Exception {
        File cacheDir = temporaryFolder.newFolder();
        FileCacheByPath cache = new FileCacheByPath(cacheDir);

        File foo = new File(temporaryFolder.getRoot(), "foo");
        try (ZFile zffooz = ZFile.openReadWrite(foo)) {
            zffooz.add("f0z/", new ByteArrayInputStream(new byte[0]));
            zffooz.add("f0z/a", new ByteArrayInputStream(new byte[0]));
            zffooz.add("f1z", new ByteArrayInputStream(new byte[0]));
        }

        cache.add(foo);

        try (ZFile zffooz = ZFile.openReadWrite(foo)) {
            zffooz.add("f0z/a", new ByteArrayInputStream(new byte[] {1, 2, 3}));
        }

        Set<Runnable> updates = new HashSet<>();
        ImmutableMap<RelativeFile, FileStatus> m =
                IncrementalRelativeFileSets.fromZip(foo, cache, updates);
        assertEquals(1, m.size());

        RelativeFile f0z = new RelativeFile(foo, "f0z/a");
        assertTrue(m.containsKey(f0z));
        assertEquals(m.get(f0z), FileStatus.CHANGED);

        updates.forEach(Runnable::run);
        m = IncrementalRelativeFileSets.fromZip(foo, cache, updates);
        assertEquals(0, m.size());
    }
}
