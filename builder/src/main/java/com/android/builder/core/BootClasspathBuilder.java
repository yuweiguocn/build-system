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

package com.android.builder.core;

import com.android.annotations.NonNull;
import com.android.builder.errors.EvalIssueException;
import com.android.builder.errors.EvalIssueReporter;
import com.android.sdklib.IAndroidTarget;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Utility methods for computing class paths to use for compilation.
 */
public class BootClasspathBuilder {

    @NonNull
    public static ImmutableList<File> computeFullBootClasspath(
            @NonNull IAndroidTarget target,
            @NonNull File annotationsJar) {
        Preconditions.checkNotNull(target);
        Preconditions.checkNotNull(annotationsJar);

        ImmutableList.Builder<File> classpath = ImmutableList.builder();

        for (String p : target.getBootClasspath()) {
            classpath.add(new File(p));
        }

        // add additional libraries if any
        for (IAndroidTarget.OptionalLibrary lib : target.getAdditionalLibraries()) {
            File jar = lib.getJar();
            Verify.verify(jar != null, "Jar missing from additional library %s.", lib.getName());
            classpath.add(jar);
        }

        // add optional libraries if any
        for (IAndroidTarget.OptionalLibrary lib : target.getOptionalLibraries()) {
            File jar = lib.getJar();
            Verify.verify(jar != null, "Jar missing from optional library %s.", lib.getName());
            classpath.add(jar);
        }

        // add annotations.jar if needed.
        if (target.getVersion().getApiLevel() <= 15) {
            classpath.add(annotationsJar);
        }

        return classpath.build();
    }

    @NonNull
    public static ImmutableList<File> computeFilteredClasspath(
            @NonNull IAndroidTarget target,
            @NonNull List<LibraryRequest> libraryRequests,
            @NonNull EvalIssueReporter issueReporter,
            @NonNull File annotationsJar) {
        ImmutableList.Builder<File> classpath = ImmutableList.builder();

        for (String p : target.getBootClasspath()) {
            classpath.add(new File(p));
        }

        // add additional and requested optional libraries if any
        classpath.addAll(
                computeAdditionalAndRequestedOptionalLibraries(
                        target, libraryRequests, issueReporter));

        // add annotations.jar if needed.
        if (target.getVersion().getApiLevel() <= 15) {
            classpath.add(annotationsJar);
        }

        return classpath.build();
    }

    /**
     * Returns the list of additional and requested optional library jar files
     *
     * @param target the Android Target
     * @param libraryRequestsArg the list of requested libraries
     * @param issueReporter the issueReporter which is written to if a requested library is not
     *     found
     * @return the list of additional and requested optional library jar files
     */
    @NonNull
    public static ImmutableList<File> computeAdditionalAndRequestedOptionalLibraries(
            @NonNull IAndroidTarget target,
            @NonNull List<LibraryRequest> libraryRequestsArg,
            @NonNull EvalIssueReporter issueReporter) {
        ImmutableList.Builder<File> classpath = ImmutableList.builder();

        List<LibraryRequest> libraryRequests = Lists.newArrayList(libraryRequestsArg);

        // iterate through additional libraries first, in case they contain a requested library
        for (IAndroidTarget.OptionalLibrary lib : target.getAdditionalLibraries()) {
            // add it always for now
            File jar = lib.getJar();
            Verify.verify(jar != null, "Jar missing from additional library %s.", lib.getName());
            classpath.add(jar);
            // search if requested, and remove from libraryRequests if so
            findMatchingLib(lib.getName(), libraryRequests).ifPresent(libraryRequests::remove);
        }

        // then iterate through optional libraries
        for (IAndroidTarget.OptionalLibrary lib : target.getOptionalLibraries()) {
            // search if requested
            Optional<LibraryRequest> requestedLib = findMatchingLib(lib.getName(), libraryRequests);
            if (requestedLib.isPresent()) {
                // add to jar and remove from requests
                File jar = lib.getJar();
                Verify.verify(jar != null, "Jar missing from optional library %s.", lib.getName());
                classpath.add(jar);
                libraryRequests.remove(requestedLib.get());
            }
        }

        // look for not found requested libraries.
        for (LibraryRequest library : libraryRequests) {
            issueReporter.reportError(
                    EvalIssueReporter.Type.OPTIONAL_LIB_NOT_FOUND,
                    new EvalIssueException(
                            "Unable to find optional library: " + library.getName(),
                            library.getName()));
        }

        return classpath.build();
    }

    @NonNull
    private static Optional<LibraryRequest> findMatchingLib(
            @NonNull String name,
            @NonNull List<LibraryRequest> libraries) {
        return libraries.stream().filter(l -> name.equals(l.getName())).findFirst();
    }
}
