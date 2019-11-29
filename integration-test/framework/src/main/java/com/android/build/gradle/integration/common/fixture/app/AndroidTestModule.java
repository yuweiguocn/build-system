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
import com.android.build.gradle.integration.common.fixture.TestProject;
import java.util.Collection;

/**
 * Interface for a single Android test module.
 *
 * <p>A test module is a collection of source code and resources that may be reused for multiple
 * tests.
 */
public interface AndroidTestModule extends TestProject {

    /** Returns a source file with the specified file path. */
    @NonNull
    TestSourceFile getFile(@NonNull String filePath);

    /** Returns a source file with the specified file name. */
    @NonNull
    TestSourceFile getFileByName(@NonNull String fileName);

    /** Returns all source files. */
    @NonNull
    Collection<TestSourceFile> getAllSourceFiles();

    /** Adds a source file. The file must not yet exist. */
    void addFile(@NonNull TestSourceFile file);

    /** Removes a source file with the specified file path. The file must already exist. */
    void removeFile(@NonNull String filePath);

    /** Removes a source file with the specified file name. The file must already exist. */
    void removeFileByName(@NonNull String fileName);

    /**
     * Replaces a source file at the corresponding file path, or adds it if the file does not yet
     * exist.
     */
    void replaceFile(TestSourceFile file);
}
