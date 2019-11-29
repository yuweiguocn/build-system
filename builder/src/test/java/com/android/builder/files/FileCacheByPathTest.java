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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.utils.FileUtils;
import com.google.common.io.Files;
import java.io.File;
import java.util.Arrays;
import java.util.Random;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for {@link FileCacheByPath}.
 */
public class FileCacheByPathTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File cacheDir;

    private FileCacheByPath cache;

    private File randomFilesDir;

    private Random random = new Random();

    @Before
    public void before() throws Exception {
        randomFilesDir = temporaryFolder.newFolder("random-generated");
        cacheDir = temporaryFolder.newFolder("cache");
        cache = new FileCacheByPath(cacheDir);
    }

    private File makeRandomFile() throws Exception {
        String name = "random-file-" + random.nextLong();
        byte[] data = new byte[1 + random.nextInt(1000)];
        random.nextBytes(data);

        File out = new File(randomFilesDir, name);
        Files.write(data, out);
        return out;
    }

    @Test
    public void addAndFindFile() throws Exception {
        File f = makeRandomFile();

        cache.add(f);
        File inCache = cache.get(f);
        assertNotNull(inCache);
        assertFalse(f.equals(inCache));
        assertArrayEquals(Files.toByteArray(f), Files.toByteArray(inCache));
    }

    @Test
    public void getNonAddedFile() throws Exception {
        File f = makeRandomFile();

        File inCache = cache.get(f);
        assertNull(inCache);
    }

    @Test
    public void replaceFile() throws Exception {
        File f1 = makeRandomFile();
        File f2 = makeRandomFile();

        byte[] f1Contents = Files.toByteArray(f1);
        byte[] f2Contents = Files.toByteArray(f2);

        assertFalse(f1.equals(f2));
        cache.add(f1);
        File inCache = cache.get(f1);
        assertNotNull(inCache);
        assertArrayEquals(f1Contents, Files.toByteArray(inCache));

        Files.copy(f2, f1);
        cache.add(f1);
        inCache = cache.get(f1);
        assertNotNull(inCache);
        assertFalse(Arrays.equals(f1Contents, Files.toByteArray(inCache)));
        assertArrayEquals(f2Contents, Files.toByteArray(inCache));
    }

    @Test
    public void addedFileDoesNotRequireOriginal() throws Exception {
        File f = makeRandomFile();

        byte[] fc = Files.toByteArray(f);

        cache.add(f);
        f.delete();

        File inCache = cache.get(f);
        assertNotNull(inCache);
        assertFalse(f.equals(inCache));
        assertArrayEquals(fc, Files.toByteArray(inCache));
    }

    @Test
    public void generateManyRandomFiles() throws Exception {
        for (int i = 0; i < 10; i++) {
            File fi = makeRandomFile();
            cache.add(fi);

            File ci = cache.get(fi);
            assertNotNull(ci);
            assertArrayEquals(Files.toByteArray(fi), Files.toByteArray(ci));

            assertTrue(ci.getName().matches("[a-zA-Z0-9_+=]+"));

            for (int j = 0; j < 10; j++) {
                File fj = makeRandomFile();
                cache.add(fj);

                File cj = cache.get(fj);
                assertNotNull(cj);
                assertArrayEquals(Files.toByteArray(fj), Files.toByteArray(cj));

                assertTrue(cj.getName().matches("[a-zA-Z0-9_+=]+"));
            }

            ci = cache.get(fi);
            assertNotNull(ci);
            assertArrayEquals(Files.toByteArray(fi), Files.toByteArray(ci));
        }
    }

    @Test
    public void clearCache() throws Exception {
        File f = makeRandomFile();
        cache.add(f);

        File ff = cache.get(f);
        assertNotNull(ff);

        cache.clear();
        ff = cache.get(f);
        assertNull(ff);
    }

    @Test
    public void removeCachedContents() throws Exception {
        File f = makeRandomFile();
        cache.add(f);

        File ff = cache.get(f);
        assertNotNull(ff);

        cache.remove(f);
        ff = cache.get(f);
        assertNull(ff);
    }

    @Test
    public void cachedDirectoryDeletedBeforeGet() throws Exception {
        File f = makeRandomFile();
        cache.add(f);

        File ff = cache.get(f);
        assertNotNull(ff);

        FileUtils.deletePath(cacheDir);
        ff = cache.get(ff);
        assertNull(ff);
    }

    @Test
    public void cachedDirectoryDeletedBeforeAdd() throws Exception {
        File f = makeRandomFile();
        FileUtils.deletePath(cacheDir);
        cache.add(f);

        File ff = cache.get(f);
        assertNotNull(ff);
    }
}
