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

package com.android.build.gradle.integration.common.fixture.app;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.pipeline.SubStream;
import com.google.common.collect.Iterables;
import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * Represents the transform output content in {@link
 * com.android.build.gradle.internal.pipeline.IntermediateStream} format.
 *
 * <p>This can be used with the custom truth subject to query the content of the output.
 */
public class TransformOutputContent implements Iterable<SubStream> {

    @NonNull private final File rootFolder;
    private Collection<SubStream> subStreams;

    public TransformOutputContent(@NonNull File rootFolder) {
        this.rootFolder = rootFolder;
    }

    private void init() {
        if (subStreams == null) {
            subStreams = SubStream.loadSubStreams(rootFolder);
        }
    }

    public File getLocation(@NonNull SubStream subStream) {
        return new File(rootFolder, subStream.getFilename());
    }

    public SubStream getSingleStream() {
        init();
        return Iterables.getOnlyElement(subStreams);
    }

    @NonNull
    @Override
    public Iterator<SubStream> iterator() {
        init();
        return subStreams.iterator();
    }

    @Override
    public void forEach(Consumer<? super SubStream> action) {
        init();
        subStreams.forEach(action);
    }

    @Override
    public Spliterator<SubStream> spliterator() {
        init();
        return subStreams.spliterator();
    }
}
