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

package com.android.builder.internal.aapt;

import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.utils.FileUtils;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import org.junit.rules.TemporaryFolder;

/**
 * Utility files for Aapt tests.
 */
public class AaptTestUtils {

    /**
     * Obtains a PNG for testing.
     *
     * @param temporaryFolder the temporary folder where temporary files should be placed
     * @return a file with a PNG in a {@code drawables} folder
     * @throws Exception failed to create the PNG
     */
    @NonNull
    public static File getTestPng(@NonNull TemporaryFolder temporaryFolder) throws Exception {
        return setUpResourceStructure(
                "testData/aapt/lorem-lena.png", temporaryFolder, "drawable", "lena.png");
    }

    /**
     * Obtains a 9 patch PNG for testing.
     *
     * @param temporaryFolder the temporary folder where temporary files should be placed
     * @return a file with a 9 patch PNG in a {@code drawables} folder
     * @throws Exception failed to create the PNG
     */
    @NonNull
    public static File getTest9Patch(@NonNull TemporaryFolder temporaryFolder) throws Exception {
        return setUpResourceStructure(
                "testData/aapt/9patch.9.png", temporaryFolder, "drawable", "9patch.9.png");
    }

    /**
     * Obtains a PNG with a long filename for testing
     *
     * @param temporaryFolder the temporary folder where temporery files should be places
     * @return a file with a PNG with a long filename in a {@code drawables} folder
     * @throws Exception failed to create the PNG
     */
    @NonNull
    public static File getTestPngWithLongFileName(@NonNull TemporaryFolder temporaryFolder)
            throws Exception {
        return setUpResourceStructure(
                "testData/aapt/lena.png",
                temporaryFolder,
                "drawable",
                Strings.repeat("a", 230) + ".png");
    }

    /**
     * Obtains the temporary directory where output files should be placed.
     *
     * @param temporaryFolder the temporary folder where temporary files should be placed
     * @return the output directory
     */
    @NonNull
    public static File getOutputDir(@NonNull TemporaryFolder temporaryFolder) {
        File outputDir = new File(temporaryFolder.getRoot(), "compile-output");
        if (!outputDir.isDirectory()) {
            assertTrue(outputDir.mkdirs());
        }

        return outputDir;
    }

    private static File setUpResourceStructure(
            @NonNull String resourcePath,
            @NonNull TemporaryFolder temporaryFolder,
            @NonNull String directoryName,
            @NonNull String fileName)
            throws IOException {
        File directory = new File(temporaryFolder.getRoot(), directoryName);
        FileUtils.mkdirs(directory);
        File file = new File(directory, fileName);
        Resources.asByteSource(Resources.getResource(resourcePath)).copyTo(Files.asByteSink(file));
        return file;
    }
}
