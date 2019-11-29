/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.common.primitives.Bytes;
import java.io.File;
import java.io.IOException;

/** Describes a source file (containing source code or resources) for integration tests. */
public final class TestSourceFile {

    @NonNull private final String relativePath;
    @NonNull private final byte[] content;

    public TestSourceFile(@NonNull String relativePath, @NonNull byte[] content) {
        Preconditions.checkArgument(
                !new File(relativePath).isAbsolute(), relativePath + " is not a relative path");
        this.relativePath = relativePath;
        this.content = content;
    }

    public TestSourceFile(@NonNull String relativePath, @NonNull String content) {
        this(relativePath, content.getBytes());
    }

    public TestSourceFile(@NonNull String parent, @NonNull String name, @NonNull String content) {
        this(parent + File.separatorChar + name, content);
    }

    @NonNull
    public String getPath() {
        return relativePath;
    }

    @NonNull
    public String getName() {
        return new File(relativePath).getName();
    }

    @NonNull
    public byte[] getContent() {
        return content;
    }

    public void writeToDir(@NonNull File baseDir) throws IOException {
        File absoluteFile = new File(baseDir, relativePath);
        Files.createParentDirs(absoluteFile);
        Files.write(content, absoluteFile);
    }

    @NonNull
    public TestSourceFile appendContent(@NonNull String additionalContent) {
        return new TestSourceFile(
                relativePath, Bytes.concat(content, additionalContent.getBytes()));
    }
}

