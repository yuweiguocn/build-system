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

package com.android.build.gradle.internal.tasks;

import static com.google.common.truth.Truth.assertThat;

import com.android.builder.files.RelativeFile;
import com.android.tools.build.apkzlib.utils.CachedFileContents;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class KnownFilesSaveDataTest {

    @Test
    public void readWriteState() throws IOException {
        TemporaryFolder folder = new TemporaryFolder();
        folder.create();
        File cache = new File(folder.getRoot(), "cache");
        CachedFileContents<KnownFilesSaveData> cached = new CachedFileContents<>(cache);

        KnownFilesSaveData data = new KnownFilesSaveData(cached);
        assertThat(data.isDirty()).isFalse();

        RelativeFile rf1 =
                new RelativeFile(folder.newFolder("base1"), "foo", RelativeFile.Type.DIRECTORY);
        RelativeFile rf2 =
                new RelativeFile(folder.newFolder("base2"), "bar", RelativeFile.Type.DIRECTORY);

        data.setInputSet(ImmutableList.of(rf1, rf2), KnownFilesSaveData.InputSet.DEX);
        assertThat(data.isDirty()).isTrue();
        assertThat(data.getFiles()).hasSize(2);

        data.saveCurrentData();
        data.getFiles().clear();
        assertThat(data.getFiles()).isEmpty();

        data.readCurrentData();
        assertThat(data.getFiles()).hasSize(2);
    }
}
