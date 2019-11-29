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

import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.utils.FileUtils;
import com.google.common.io.Files;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RelativeFilesTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void readFullDirectory() throws Exception {
        File parent = temporaryFolder.newFolder();
        File file1 = new File(parent, "file1");
        Files.write(new byte[0], file1);
        File child = new File(parent, "child");
        FileUtils.mkdirs(child);
        File file2 = new File(child, "file2");
        Files.write(new byte[0], file2);

        Set<RelativeFile> relativeFiles = RelativeFiles.fromDirectory(parent);
        assertEquals(2, relativeFiles.size());
        assertThat(relativeFiles).contains(new RelativeFile(parent, file1));
        assertThat(relativeFiles).contains(new RelativeFile(parent, file2));
    }

    @Test
    public void readFullZip() throws Exception {
        File zfile = new File(temporaryFolder.getRoot(), "zf.zip");

        try (ZFile zf = ZFile.openReadWrite(zfile)) {
            zf.add("file1", new ByteArrayInputStream(new byte[0]));
            zf.add("dir/", new ByteArrayInputStream(new byte[0]));
            zf.add("dir/file2", new ByteArrayInputStream(new byte[0]));
        }

        Set<RelativeFile> relativeFiles = RelativeFiles.fromZip(zfile);
        assertEquals(2, relativeFiles.size());
        assertThat(relativeFiles).contains(new RelativeFile(zfile, "file1"));
        assertThat(relativeFiles).contains(new RelativeFile(zfile, "dir/file2"));
    }
}
