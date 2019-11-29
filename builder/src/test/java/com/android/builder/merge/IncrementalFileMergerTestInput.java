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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.FileStatus;
import com.google.common.collect.ImmutableSet;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class IncrementalFileMergerTestInput implements IncrementalFileMergerInput {

    @NonNull
    private final List<String> paths;

    @NonNull
    private final Map<String, FileStatus> status;

    @NonNull
    private final Map<String, byte[]> pathData;

    @NonNull
    private final String name;

    private boolean open;

    public IncrementalFileMergerTestInput(@NonNull String name) {
        this.name = name;
        paths = new ArrayList<>();
        status = new HashMap<>();
        pathData = new HashMap<>();
    }

    void add(@NonNull String path) {
        paths.add(path);
    }

    void add(@NonNull String path, @NonNull FileStatus status) {
        this.status.put(path, status);
    }

    void setData(@NonNull String path, @NonNull byte[] data) {
        this.pathData.put(path, data);
    }

    @NonNull
    @Override
    public ImmutableSet<String> getUpdatedPaths() {
        return ImmutableSet.copyOf(status.keySet());
    }

    @NonNull
    @Override
    public ImmutableSet<String> getAllPaths() {
        ImmutableSet.Builder<String> p = ImmutableSet.builder();
        p.addAll(paths);
        p.addAll(status.keySet());
        return p.build();
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @Nullable
    @Override
    public FileStatus getFileStatus(@NonNull String path) {
        return status.get(path);
    }

    @NonNull
    @Override
    public InputStream openPath(@NonNull String path) {
        assertTrue(open);
        byte[] data = pathData.get(path);
        assertNotNull(data);
        return new ByteArrayInputStream(data);
    }

    @Override
    public void open() {
        assertFalse(open);
        open = true;
    }

    @Override
    public void close() {
        assertTrue(open);
        open = false;
    }
}
