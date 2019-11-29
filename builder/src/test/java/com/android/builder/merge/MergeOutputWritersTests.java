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

package com.android.builder.merge;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.build.apkzlib.zip.StoredEntry;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.utils.FileUtils;
import com.google.common.io.Files;
import java.io.ByteArrayInputStream;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MergeOutputWritersTests {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void directoryWriterCreateFile() throws Exception {
        File dir = temporaryFolder.newFolder();

        MergeOutputWriter w = MergeOutputWriters.toDirectory(dir);
        w.open();
        w.create("a", new ByteArrayInputStream(new byte[] { 1, 2, 3 }));
        w.create("b/c", new ByteArrayInputStream(new byte[] { 4, 5 }));
        w.create("d e/f g", new ByteArrayInputStream(new byte[] { 6, 7, 8, 9 }));
        w.create("h/i/j", new ByteArrayInputStream(new byte[] { 10 }));
        w.close();

        File a = new File(dir, "a");
        File b = new File(dir, "b");
        File c = new File(b, "c");
        File de = new File(dir, "d e");
        File fg = new File(de, "f g");
        File h = new File(dir, "h");
        File i = new File(h, "i");
        File j = new File(i, "j");

        assertTrue(a.isFile());
        assertTrue(b.isDirectory());
        assertTrue(c.isFile());
        assertTrue(de.isDirectory());
        assertTrue(fg.isFile());
        assertTrue(h.isDirectory());
        assertTrue(i.isDirectory());
        assertTrue(j.isFile());

        assertArrayEquals(new byte[] { 1, 2, 3 }, Files.toByteArray(a));
        assertArrayEquals(new byte[] { 4, 5 }, Files.toByteArray(c));
        assertArrayEquals(new byte[] { 6, 7, 8, 9 }, Files.toByteArray(fg));
        assertArrayEquals(new byte[] { 10 }, Files.toByteArray(j));
    }

    @Test
    public void directoryWriterRemoveFile() throws Exception {
        File dir = temporaryFolder.newFolder();

        File a = new File(dir, "a");
        Files.write(new byte[] { 75 }, a);

        File b = new File(dir, "b");
        FileUtils.mkdirs(b);

        File c = new File(b, "c");
        Files.write(new byte[] { 1, 0, 0, 1 }, c);

        File de = new File(dir, "d e");
        FileUtils.mkdirs(de);

        File fg = new File(de, "f g");
        Files.write(new byte[] { 3, 3, 3 }, fg);

        File h = new File(dir, "h");
        FileUtils.mkdirs(h);

        File i = new File(h, "i");
        FileUtils.mkdirs(i);

        File j = new File(i, "j");
        Files.write(new byte[] { 8, 8 }, j);

        MergeOutputWriter w = MergeOutputWriters.toDirectory(dir);
        w.open();
        w.remove("a");
        w.remove("b/c");
        w.remove("d e/f g");
        w.remove("h/i/j");
        w.close();

        assertFalse(a.exists());
        assertFalse(b.exists());
        assertFalse(c.exists());
        assertFalse(de.exists());
        assertFalse(fg.exists());
        assertFalse(h.exists());
        assertFalse(i.exists());
        assertFalse(j.exists());
    }

    @Test
    public void directoryWriterUpdateFile() throws Exception {
        File dir = temporaryFolder.newFolder();

        File a = new File(dir, "a");
        Files.write(new byte[] { 13, 17 }, a);

        File b = new File(dir, "b");
        FileUtils.mkdirs(b);

        File c = new File(b, "c");
        Files.write(new byte[] { 19, 23 }, c);

        File de = new File(dir, "d e");
        FileUtils.mkdirs(de);

        File fg = new File(de, "f g");
        Files.write(new byte[] { 29, 31 }, fg);

        File h = new File(dir, "h");
        FileUtils.mkdirs(h);

        File i = new File(h, "i");
        FileUtils.mkdirs(i);

        File j = new File(i, "j");
        Files.write(new byte[] { 37 }, j);

        MergeOutputWriter w = MergeOutputWriters.toDirectory(dir);
        w.open();
        w.replace("a", new ByteArrayInputStream(new byte[] { 4, 9 }));
        w.replace("b/c", new ByteArrayInputStream(new byte[] { 16, 25 }));
        w.replace("d e/f g", new ByteArrayInputStream(new byte[] { 36, 49 }));
        w.replace("h/i/j", new ByteArrayInputStream(new byte[] { 64 }));
        w.close();

        assertTrue(a.isFile());
        assertTrue(b.isDirectory());
        assertTrue(c.isFile());
        assertTrue(de.isDirectory());
        assertTrue(fg.isFile());
        assertTrue(h.isDirectory());
        assertTrue(i.isDirectory());
        assertTrue(j.isFile());

        assertArrayEquals(new byte[] { 4, 9 }, Files.toByteArray(a));
        assertArrayEquals(new byte[] { 16, 25 }, Files.toByteArray(c));
        assertArrayEquals(new byte[] { 36, 49 }, Files.toByteArray(fg));
        assertArrayEquals(new byte[] { 64 }, Files.toByteArray(j));
    }

    @Test
    public void zipWriterCreateFile() throws Exception {
        File dir = temporaryFolder.newFolder();
        File zipFile = new File(dir, "test.zip");
        try (ZFile zf = ZFile.openReadWrite(zipFile)) {}

        MergeOutputWriter w = MergeOutputWriters.toZip(zipFile);
        w.open();
        w.create("a", new ByteArrayInputStream(new byte[] { 1, 2, 3 }));
        w.create("b/c", new ByteArrayInputStream(new byte[] { 4, 5 }));
        w.create("d e/f g", new ByteArrayInputStream(new byte[] { 6, 7, 8, 9 }));
        w.close();

        try (ZFile zf = ZFile.openReadOnly(zipFile)) {
            assertEquals(3, zf.entries().size());

            StoredEntry aEntry = zf.get("a");
            assertNotNull(aEntry);

            StoredEntry cEntry = zf.get("b/c");
            assertNotNull(cEntry);

            StoredEntry fgEntry = zf.get("d e/f g");
            assertNotNull(fgEntry);

            assertArrayEquals(new byte[] { 1, 2, 3 }, aEntry.read());
            assertArrayEquals(new byte[] { 4, 5 }, cEntry.read());
            assertArrayEquals(new byte[] { 6, 7, 8, 9 }, fgEntry.read());
        }
    }

    @Test
    public void zipWriterRemoveFile() throws Exception {
        File dir = temporaryFolder.newFolder();
        File zipFile = new File(dir, "test.zip");
        try (ZFile zf = ZFile.openReadWrite(zipFile)) {
            zf.add("a", new ByteArrayInputStream(new byte[] { 75 }));
            zf.add("b/c", new ByteArrayInputStream(new byte[] { 1, 0, 0, 1 }));
            zf.add("d e/f g", new ByteArrayInputStream(new byte[] { 3, 3, 3 }));
        }

        MergeOutputWriter w = MergeOutputWriters.toZip(zipFile);
        w.open();
        w.remove("a");
        w.remove("b/c");
        w.remove("d e/f g");
        w.close();


        try (ZFile zf = ZFile.openReadOnly(zipFile)) {
            assertEquals(0, zf.entries().size());
        }
    }

    @Test
    public void zipWriterUpdateFile() throws Exception {
        File dir = temporaryFolder.newFolder();
        File zipFile = new File(dir, "test.zip");
        try (ZFile zf = ZFile.openReadWrite(zipFile)) {
            zf.add("a", new ByteArrayInputStream(new byte[] { 13, 17 }));
            zf.add("b/c", new ByteArrayInputStream(new byte[] { 19, 23 }));
            zf.add("d e/f g", new ByteArrayInputStream(new byte[] { 29, 31 }));
        }

        MergeOutputWriter w = MergeOutputWriters.toZip(zipFile);
        w.open();
        w.replace("a", new ByteArrayInputStream(new byte[] { 4, 9 }));
        w.replace("b/c", new ByteArrayInputStream(new byte[] { 16, 25 }));
        w.replace("d e/f g", new ByteArrayInputStream(new byte[] { 36, 49 }));
        w.close();

        try (ZFile zf = ZFile.openReadOnly(zipFile)) {
            assertEquals(3, zf.entries().size());

            StoredEntry aEntry = zf.get("a");
            assertNotNull(aEntry);

            StoredEntry cEntry = zf.get("b/c");
            assertNotNull(cEntry);

            StoredEntry fgEntry = zf.get("d e/f g");
            assertNotNull(fgEntry);

            assertArrayEquals(new byte[] { 4, 9 }, aEntry.read());
            assertArrayEquals(new byte[] { 16, 25 }, cEntry.read());
            assertArrayEquals(new byte[] { 36, 49 }, fgEntry.read());
        }
    }
}
