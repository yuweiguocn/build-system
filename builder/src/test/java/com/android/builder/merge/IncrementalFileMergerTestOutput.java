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
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.utils.Pair;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Output that keeps track of all requested operations. Used for tests.
 */
class IncrementalFileMergerTestOutput implements IncrementalFileMergerOutput {
    boolean open;
    Set<String> removed = new HashSet<>();
    Map<String, ImmutableList<IncrementalFileMergerInput>> created = new HashMap<>();
    Map<String, Pair<ImmutableList<String>, ImmutableList<IncrementalFileMergerInput>>> updated =
            new HashMap<>();

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

    @Override
    public void remove(@NonNull String path) {
        assertTrue(open);
        removed.add(path);
    }

    @Override
    public void create(@NonNull String path, @NonNull List<IncrementalFileMergerInput> inputs) {
        assertTrue(open);
        assertFalse(created.containsKey(path));
        created.put(path, ImmutableList.copyOf(inputs));
    }

    @Override
    public void update(
            @NonNull String path,
            @NonNull List<String> prevInputNames,
            @NonNull List<IncrementalFileMergerInput> inputs) {
        assertTrue(open);
        assertFalse(updated.containsKey(path));
        updated.put(
                path, Pair.of(ImmutableList.copyOf(prevInputNames), ImmutableList.copyOf(inputs)));
    }
}
