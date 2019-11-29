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

package com.android.build.gradle.internal.dependency;

import com.android.annotations.NonNull;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;

/**
 * Implementation of a {@link ArtifactCollection} on top of two collections, in order to do lazy
 * subtractions.
 *
 * <p>The main use case for this is building an ArtifactCollection that represents the runtime
 * dependencies of a test app, minus the runtime dependencies of the tested app (to avoid duplicated
 * classes during runtime).
 */
public class SubtractingArtifactCollection implements ArtifactCollection {

    @NonNull private final ArtifactCollection mainArtifacts;
    @NonNull private final ArtifactCollection removedArtifacts;
    @NonNull private final FileCollection fileCollection;
    private Set<ResolvedArtifactResult> artifactResults = null;

    public SubtractingArtifactCollection(
            @NonNull ArtifactCollection mainArtifact, @NonNull ArtifactCollection removedArtifact) {
        this.mainArtifacts = mainArtifact;
        this.removedArtifacts = removedArtifact;

        fileCollection = mainArtifact.getArtifactFiles().minus(removedArtifact.getArtifactFiles());
    }

    @Override
    public FileCollection getArtifactFiles() {
        return fileCollection;
    }

    @Override
    public Set<ResolvedArtifactResult> getArtifacts() {
        if (artifactResults == null) {
            // build a set of Files to remove
            Set<File> removedFiles =
                    removedArtifacts
                            .getArtifacts()
                            .stream()
                            .map(ResolvedArtifactResult::getFile)
                            .collect(Collectors.toSet());

            // build the final list from the main one, filtering our the tested IDs.
            artifactResults = Sets.newLinkedHashSet();
            for (ResolvedArtifactResult artifactResult : mainArtifacts.getArtifacts()) {
                if (!removedFiles.contains(artifactResult.getFile())) {
                    artifactResults.add(artifactResult);
                }
            }
        }

        return artifactResults;
    }

    @Override
    public Collection<Throwable> getFailures() {
        ImmutableList.Builder<Throwable> builder = ImmutableList.builder();
        builder.addAll(mainArtifacts.getFailures());
        builder.addAll(removedArtifacts.getFailures());
        return builder.build();
    }

    @NonNull
    @Override
    public Iterator<ResolvedArtifactResult> iterator() {
        return getArtifacts().iterator();
    }

    @Override
    public void forEach(Consumer<? super ResolvedArtifactResult> action) {
        getArtifacts().forEach(action);
    }

    @Override
    public Spliterator<ResolvedArtifactResult> spliterator() {
        return getArtifacts().spliterator();
    }
}
