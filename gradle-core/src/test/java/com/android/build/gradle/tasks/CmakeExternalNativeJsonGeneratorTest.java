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

package com.android.build.gradle.tasks;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CmakeExternalNativeJsonGeneratorTest {
    private static final String RELATIVE_FILE_NAME = "CMakeLists.txt";
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
    private static final String ERROR_STRING =
            "CMake Error at %s:123:456. We had a reactor leak here now. Give us a few minutes to lock it down. Large leak, very dangerous.";

    @Test
    public void testMakefilePathCorrection() throws IOException {
        File makefile = temporaryFolder.newFile(RELATIVE_FILE_NAME);
        String input = String.format(Locale.getDefault(), ERROR_STRING, RELATIVE_FILE_NAME);
        String expected =
                String.format(Locale.getDefault(), ERROR_STRING, makefile.getAbsolutePath());
        String actual =
                CmakeExternalNativeJsonGenerator.correctMakefilePaths(
                        input, makefile.getParentFile());
        assertEquals(expected, actual);
    }

    @Test
    public void testMakefilePathCorrectionForAbsolutePath() throws IOException {
        File makefile = temporaryFolder.newFile(RELATIVE_FILE_NAME);
        String input = String.format(Locale.getDefault(), ERROR_STRING, makefile.getAbsolutePath());
        String actual =
                CmakeExternalNativeJsonGenerator.correctMakefilePaths(
                        input, makefile.getParentFile());
        assertEquals(input, actual);
    }

    @Test
    public void testMakefilePathCorrectionForNonexistentFile() {
        String input = String.format(Locale.getDefault(), ERROR_STRING, RELATIVE_FILE_NAME);
        String actual =
                CmakeExternalNativeJsonGenerator.correctMakefilePaths(
                        input, temporaryFolder.getRoot());
        assertEquals(input, actual);
    }

    @Test
    public void testMakefilePathCorrectionOverMultipleLines() throws IOException {
        File absoluteMakefile = temporaryFolder.newFile(RELATIVE_FILE_NAME);
        String otherErrorFile = "MissingErrorFile.txt";
        String base =
                ERROR_STRING
                        + System.lineSeparator()
                        + "This line should not change at all"
                        + System.lineSeparator()
                        + " "
                        + ERROR_STRING
                        + System.lineSeparator()
                        + System.lineSeparator()
                        + System.lineSeparator()
                        + "Another string that won't match the RegEx.";

        String input =
                String.format(
                        Locale.getDefault(), base, absoluteMakefile.getName(), otherErrorFile);
        String expected =
                String.format(
                        Locale.getDefault(),
                        base,
                        absoluteMakefile.getAbsolutePath(),
                        otherErrorFile);
        String actual =
                CmakeExternalNativeJsonGenerator.correctMakefilePaths(
                        input, absoluteMakefile.getParentFile());
        assertEquals(expected, actual);
    }
}
