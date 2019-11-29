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

package com.android.build.gradle.internal.transforms;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/** Transform output provider used for testing. */
public class TestTransformOutputProvider implements TransformOutputProvider {

    @NonNull private Path rootDir;

    public TestTransformOutputProvider(@NonNull Path rootDir) {
        this.rootDir = rootDir;
    }

    @Override
    public void deleteAll() throws IOException {
        FileUtils.deleteDirectoryContents(rootDir.toFile());
    }

    @NonNull
    @Override
    public File getContentLocation(
            @NonNull String name,
            @NonNull Set<QualifiedContent.ContentType> types,
            @NonNull Set<? super QualifiedContent.Scope> scopes,
            @NonNull Format format) {
        return rootDir.resolve(
                        Paths.get(name).getFileName().toString()
                                + (format == Format.JAR ? SdkConstants.DOT_JAR : ""))
                .toFile();
    }
}
