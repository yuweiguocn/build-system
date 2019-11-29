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

package com.android.build.gradle.internal.incremental.fixture;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ClassEnhancementUtil {

    static final Path JAR_DIRECTORY =
            Paths.get("tools/base/build-system/instant-run-instrumentation");
    static final Path INSTRUMENTED_BASE_JAR = JAR_DIRECTORY.resolve("instrumented_base.jar");
    static final Path UNINSTRUMENTED_BASE_JAR = JAR_DIRECTORY.resolve("libbase-test-classes.jar");

    /** Gets the file containing the base version of the class with the specified class name. */
    @Nullable
    public static byte[] getUninstrumentedBaseClass(@NonNull String className) {
        return getClassFile(className, UNINSTRUMENTED_BASE_JAR);
    }

    static Path getUninstrumentedPatchJar(@NonNull String patch) {
        return ClassEnhancementUtil.JAR_DIRECTORY.resolve("lib" + patch + "-test-classes.jar");
    }

    static byte[] getUninstrumentedPatchClass(@NonNull String patch, @NonNull String className) {
        return getClassFile(className, getUninstrumentedPatchJar(patch));
    }

    private static byte[] getClassFile(@NonNull String className, @NonNull Path jar) {
        try (ZipFile zipFile = new ZipFile(jar.toFile())) {
            ZipEntry klass = zipFile.getEntry(className.replace('.', '/') + ".class");
            if (klass == null) {
                return null;
            }
            return ByteStreams.toByteArray(zipFile.getInputStream(klass));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
