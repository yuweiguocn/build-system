/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.common.truth;

import static com.google.common.base.Preconditions.checkArgument;

import com.android.annotations.NonNull;
import com.android.ide.common.process.ProcessException;
import com.android.testutils.apk.AndroidArchive;
import com.android.testutils.truth.AbstractZipSubject;
import com.google.common.truth.FailureStrategy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Base Truth support for android archives (aar and apk) */
@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public abstract class AbstractAndroidSubject<
                S extends AbstractAndroidSubject<S, T>, T extends AndroidArchive>
        extends AbstractZipSubject<S, T> {

    public AbstractAndroidSubject(@NonNull FailureStrategy failureStrategy, @NonNull T subject) {
        super(failureStrategy, subject);
    }

    protected static boolean isClassName(@NonNull String className) {
        return AndroidArchive.CLASS_FORMAT.matcher(className).matches();
    }

    public final void containsClass(@NonNull String className)
            throws IOException, ProcessException {
        if (!getSubject().containsClass(className)) {
            failWithRawMessage(
                    "'%s' does not contain class '%s'.\n", getDisplaySubject(), className);
        }
    }

    public final void containsMainClass(@NonNull String className)
            throws IOException, ProcessException {
        if (!getSubject().containsMainClass(className)) {
            failWithRawMessage(
                    "'%s' does not contain main class '%s'", getDisplaySubject(), className);
        }
    }

    public final void containsSecondaryClass(@NonNull String className)
            throws IOException, ProcessException {
        if (!getSubject().containsSecondaryClass(className)) {
            failWithRawMessage(
                    "'%s' does not contain secondary class '%s'", getDisplaySubject(), className);
        }
    }

    @Override
    public final void contains(@NonNull String path) throws IOException {
        checkArgument(!isClassName(path), "Use containsClass to check for classes.");
        if (getSubject().getEntry(path) == null) {
            failWithRawMessage("'%s' does not contain '%s'", getSubject(), path);
        }
    }

    @Override
    public final void doesNotContain(@NonNull String path) throws IOException {
        checkArgument(!isClassName(path), "Use doesNotContainClass to check for classes.");
        if (getSubject().getEntry(path) != null) {
            failWithRawMessage("'%s' unexpectedly contains '%s'", getSubject(), path);
        }
    }

    public final void containsFile(@NonNull String fileName) throws IOException {
        if (getSubject().getEntries(Pattern.compile("(.*/)?" + fileName)).isEmpty()) {
            failWithRawMessage("'%s' does not contain file '%s'", getSubject(), fileName);
        }
    }

    public final void doesNotContainFile(@NonNull String fileName) throws IOException {
        if (!getSubject().getEntries(Pattern.compile("(.*/)?" + fileName)).isEmpty()) {
            failWithRawMessage("'%s' unexpectedly contains file '%s'", getSubject(), fileName);
        }
    }

    public final void doesNotContainClass(@NonNull String className)
            throws IOException, ProcessException {
        if (getSubject().containsClass(className)) {
            failWithRawMessage(
                    "'%s' unexpectedly contains class '%s'", getDisplaySubject(), className);
        }
    }

    public final void doesNotContainMainClass(@NonNull String className)
            throws IOException, ProcessException {
        if (getSubject().containsMainClass(className)) {
            failWithRawMessage(
                    "'%s' unexpectedly contains main class '%s'", getDisplaySubject(), className);
        }
    }

    public final void doesNotContainSecondaryClass(@NonNull String className)
            throws IOException, ProcessException {
        if (getSubject().containsSecondaryClass(className)) {
            failWithRawMessage(
                    "'%s' unexpectedly contains secondary class '%s'",
                    getDisplaySubject(), className);
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public final void containsResource(@NonNull String name) throws IOException, ProcessException {
        if (getSubject().getResource(name) == null) {
            failWithRawMessage("'%s' does not contain resource '%s'", getDisplaySubject(), name);
        }
    }

    public final void doesNotContainResource(@NonNull String name)
            throws IOException, ProcessException {
        if (getSubject().getResource(name) != null) {
            failWithRawMessage(
                    "'%s' unexpectedly contains resource '%s'", getDisplaySubject(), name);
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public final void containsJavaResource(@NonNull String name)
            throws IOException, ProcessException {
        if (!(getSubject().getJavaResource(name) != null)) {
            failWithRawMessage(
                    "'%s' does not contain Java resource '%s'", getDisplaySubject(), name);
        }
    }

    public final void doesNotContainJavaResource(@NonNull String name)
            throws IOException, ProcessException {
        if (getSubject().getJavaResource(name) != null) {
            failWithRawMessage(
                    "'%s' unexpectedly contains Java resource '%s'", getDisplaySubject(), name);
        }
    }

    /**
     * Asserts the subject contains a java resource at the given path with the specified String
     * content.
     *
     * <p>Content is trimmed when compared.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public final void containsJavaResourceWithContent(
            @NonNull String path, @NonNull String expected) throws IOException, ProcessException {
        Path resource = getSubject().getJavaResource(path);
        if (resource == null) {
            failWithRawMessage("Resource " + path + " does not exist in " + getSubject());
            return;
        }
        String actual = Files.readAllLines(resource).stream().collect(Collectors.joining("\n"));
        if (!expected.equals(actual)) {
            failWithRawMessage(
                    "Resource %s in %s does not have expected contents. Expected '%s' actual '%s'",
                    path, getSubject(), expected, actual);
        }
    }

    /**
     * Asserts the subject contains a java resource at the given path with the specified byte array
     * content.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public final void containsJavaResourceWithContent(
            @NonNull String path, @NonNull byte[] expected) throws IOException, ProcessException {
        Path resource = getSubject().getJavaResource(path);
        if (resource == null) {
            failWithRawMessage("Resource " + path + " does not exist in " + getSubject());
            return;
        }

        byte[] actual = Files.readAllBytes(resource);
        if (!Arrays.equals(expected, actual)) {
            failWithBadResults(
                    "[" + path + "] has contents",
                    Arrays.toString(expected),
                    "contains",
                    Arrays.toString(actual));
        }
    }

    @Override
    protected String getDisplaySubject() {
        String name = (internalCustomName() == null) ? "" : "\"" + internalCustomName() + "\" ";
        return name + "<" + getSubject() + ">";
    }
}
