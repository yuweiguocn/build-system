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
import com.android.annotations.Nullable;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/** Abstract class implementing AndroidTestModule. */
public abstract class AbstractAndroidTestModule implements AndroidTestModule {

    /** Map from a relative path to the corresponding {@link TestSourceFile} instance. */
    @NonNull private final Map<String, TestSourceFile> sourceFiles = new HashMap<>();

    @Override
    @NonNull
    public TestSourceFile getFile(@NonNull String relativePath) {
        Preconditions.checkState(
                sourceFiles.containsKey(relativePath), relativePath + " does not exist");
        return sourceFiles.get(relativePath);
    }

    @Override
    @NonNull
    public TestSourceFile getFileByName(@NonNull String fileName) {
        List<TestSourceFile> matchedFiles =
                sourceFiles
                        .values()
                        .stream()
                        .filter(it -> it.getName().equals(fileName))
                        .collect(Collectors.toList());
        if (matchedFiles.isEmpty()) {
            throw new NoSuchElementException(
                    String.format("Found no source file named '%s'", fileName));
        } else if (matchedFiles.size() > 1) {
            throw new IllegalArgumentException(
                    String.format(
                            "Found multiple source files named '%1$s':\n%2$s",
                            fileName,
                            Joiner.on('\n')
                                    .join(
                                            matchedFiles
                                                    .stream()
                                                    .map(TestSourceFile::getPath)
                                                    .collect(Collectors.toList()))));
        } else {
            return matchedFiles.get(0);
        }
    }

    @Override
    @NonNull
    public Collection<TestSourceFile> getAllSourceFiles() {
        return sourceFiles.values();
    }

    @Override
    public void addFile(@NonNull TestSourceFile file) {
        Preconditions.checkState(
                !sourceFiles.containsKey(file.getPath()), file.getPath() + " already exists");
        sourceFiles.put(file.getPath(), file);
    }

    protected void addFiles(@NonNull TestSourceFile... files) {
        for (TestSourceFile file : files) {
            addFile(file);
        }
    }

    @Override
    public void removeFile(@NonNull String filePath) {
        Preconditions.checkState(sourceFiles.containsKey(filePath), filePath + " does not exist");
        sourceFiles.remove(filePath);
    }

    @Override
    public void removeFileByName(@NonNull String fileName) {
        removeFile(getFileByName(fileName).getPath());
    }

    @Override
    public void replaceFile(@NonNull TestSourceFile file) {
        sourceFiles.put(file.getPath(), file);
    }

    @Override
    public void write(@NonNull File projectDir, @Nullable String buildScriptContent)
            throws IOException {
        for (TestSourceFile srcFile : getAllSourceFiles()) {
            srcFile.writeToDir(projectDir);
        }

        // Create build.gradle.
        if (buildScriptContent != null) {
            File buildFile = new File(projectDir, "build.gradle");
            String oldContent = buildFile.isFile()
                    ? Files.toString(buildFile, Charset.defaultCharset())
                    : "";

            Files.asCharSink(buildFile, Charset.defaultCharset())
                    .write(buildScriptContent + "\n\n" + oldContent);
        }
    }

    @Override
    public boolean containsFullBuildScript() {
        return false;
    }
}
