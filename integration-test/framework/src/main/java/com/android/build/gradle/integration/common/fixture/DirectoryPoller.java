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

package com.android.build.gradle.integration.common.fixture;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Helper class to poll for changes in the contents of a directory.
 *
 * <p>In its current form, it only supports finding files that have a different name to all previous
 * files found in a directory. If a file is deleted and re-created with the same name, it will not
 * be counted as a new file by this class.
 */
@NotThreadSafe
public final class DirectoryPoller {
    @NonNull private final Path directory;
    @Nullable private final Predicate<Path> filter;
    @NonNull private final Set<Path> known = new HashSet<>();

    public DirectoryPoller(@NonNull Path directory, @Nullable String extension) throws IOException {
        this.directory = directory;

        Predicate<Path> tmp = Files::isRegularFile;
        if (extension != null) {
            tmp = tmp.and((path -> path.getFileName().toString().endsWith(extension)));
        }
        this.filter = tmp;

        poll();
    }

    /** Get new files since the last time poll() was called. */
    @NonNull
    public Collection<Path> poll() throws IOException {
        if (!Files.exists(directory)) {
            return Collections.emptySet();
        }

        Set<Path> present;
        try (Stream<Path> paths = Files.walk(directory)) {
            present = paths.filter(filter).collect(Collectors.toSet());
        }
        Set<Path> diff = ImmutableSet.copyOf(Sets.difference(present, known));

        known.addAll(diff);
        return diff;
    }
}
