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

package com.android.builder.utils;

import static com.android.testutils.truth.FileSubject.assertThat;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.repository.Revision;
import com.android.testutils.concurrency.ConcurrencyTester;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Test cases for {@link FileCache}. */
public class FileCacheTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File cacheDir;
    private File outputDir;

    @Before
    public void setUp() throws IOException {
        cacheDir = temporaryFolder.newFolder();
        outputDir = temporaryFolder.newFolder();
    }

    @Test
    public void testCreateFile_SameInputSameOutput() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();
        File outputFile = new File(outputDir, "output");

        // First access to the cache, expect cache miss
        fileCache.createFile(outputFile, inputs, () -> writeStringToFile("Some text", outputFile));
        assertThat(fileCache.getHits()).isEqualTo(0);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(outputFile).hasContents("Some text");

        FileUtils.delete(outputFile);

        // Second access to the cache, expect cache hit
        fileCache.createFile(
                outputFile,
                inputs,
                () -> {
                    fail("This statement should not be executed");
                    writeStringToFile("This text should not be written", outputFile);
                });
        assertThat(fileCache.getHits()).isEqualTo(1);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(outputFile).hasContents("Some text");
    }

    @Test
    public void testCreateFile_SameInputDifferentOutputs() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();

        // First access to the cache
        File outputFile1 = new File(outputDir, "output1");
        fileCache.createFile(
                outputFile1, inputs, () -> writeStringToFile("Some text", outputFile1));

        // Second access to the cache, expect cache hit
        File outputFile2 = new File(outputDir, "output2");
        fileCache.createFile(
                outputFile2,
                inputs,
                () -> {
                    fail("This statement should not be executed");
                    writeStringToFile("This text should not be written", outputFile2);
                });
        assertThat(fileCache.getHits()).isEqualTo(1);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(outputFile1).hasContents("Some text");
        assertThat(outputFile2).hasContents("Some text");
    }

    @Test
    public void testCreateFile_DifferentInputsSameOutput() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        File outputFile = new File(outputDir, "output");

        // First access to the cache
        FileCache.Inputs inputs1 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input1")
                        .build();
        fileCache.createFile(outputFile, inputs1, () -> writeStringToFile("Some text", outputFile));

        // Second access to the cache, expect cache miss
        FileCache.Inputs inputs2 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input2")
                        .build();
        fileCache.createFile(
                outputFile, inputs2, () -> writeStringToFile("Some other text", outputFile));
        assertThat(fileCache.getHits()).isEqualTo(0);
        assertThat(fileCache.getMisses()).isEqualTo(2);
        assertThat(outputFile).hasContents("Some other text");
    }

    @Test
    public void testCreateFile_DifferentInputsDifferentOutputs() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);

        // First access to the cache
        FileCache.Inputs inputs1 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input1")
                        .build();
        File outputFile1 = new File(outputDir, "output1");
        fileCache.createFile(
                outputFile1, inputs1, () -> writeStringToFile("Some text", outputFile1));

        // Second access to the cache, expect cache miss
        FileCache.Inputs inputs2 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input2")
                        .build();
        File outputFile2 = new File(outputDir, "output2");
        fileCache.createFile(
                outputFile2, inputs2, () -> writeStringToFile("Some other text", outputFile2));
        assertThat(fileCache.getHits()).isEqualTo(0);
        assertThat(fileCache.getMisses()).isEqualTo(2);
        assertThat(outputFile1).hasContents("Some text");
        assertThat(outputFile2).hasContents("Some other text");
    }

    @Test
    public void testCreateFileInCacheIfAbsent_SameInput() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();

        // First access to the cache, expect cache miss
        File cachedFile =
                fileCache
                        .createFileInCacheIfAbsent(
                                inputs, (outputFile) -> writeStringToFile("Some text", outputFile))
                        .getCachedFile();
        assertThat(fileCache.getHits()).isEqualTo(0);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(cachedFile).hasContents("Some text");

        // Second access to the cache, expect cache hit
        File cachedFile2 =
                fileCache
                        .createFileInCacheIfAbsent(
                                inputs,
                                (outputFile) -> {
                                    fail("This statement should not be executed");
                                    writeStringToFile(
                                            "This text should not be written", outputFile);
                                })
                        .getCachedFile();
        assertThat(fileCache.getHits()).isEqualTo(1);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(cachedFile2).isEqualTo(cachedFile);
        assertThat(cachedFile).hasContents("Some text");
    }

    @Test
    public void testCreateFileInCacheIfAbsent_DifferentInputs() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);

        // First access to the cache
        FileCache.Inputs inputs1 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input1")
                        .build();
        File cachedFile1 =
                fileCache
                        .createFileInCacheIfAbsent(
                                inputs1, (outputFile) -> writeStringToFile("Some text", outputFile))
                        .getCachedFile();

        // Second access to the cache, expect cache miss
        FileCache.Inputs inputs2 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input2")
                        .build();
        File cachedFile2 =
                fileCache
                        .createFileInCacheIfAbsent(
                                inputs2,
                                (outputFile) -> writeStringToFile("Some other text", outputFile))
                        .getCachedFile();
        assertThat(fileCache.getHits()).isEqualTo(0);
        assertThat(fileCache.getMisses()).isEqualTo(2);
        assertThat(cachedFile2).isNotEqualTo(cachedFile1);
        assertThat(cachedFile1).hasContents("Some text");
        assertThat(cachedFile2).hasContents("Some other text");
    }

    @Test
    public void testCreateFile_OutputToDirectory() throws Exception {
        // Use same input different outputs
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();

        // First access to the cache
        File outputDir1 = new File(outputDir, "outputDir1");
        fileCache.createFile(
                outputDir1,
                inputs,
                () -> {
                    FileUtils.mkdirs(outputDir1);
                    writeStringToFile("Some text", new File(outputDir1, "fileInOutputDir"));
                });

        // Second access to the cache, expect cache hit
        File outputDir2 = new File(outputDir, "outputDir2");
        fileCache.createFile(
                outputDir2,
                inputs,
                () -> {
                    fail("This statement should not be executed");
                    FileUtils.mkdirs(outputDir2);
                    writeStringToFile(
                            "This text should not be written",
                            new File(outputDir2, "fileInOutputDir"));
                });
        assertThat(fileCache.getHits()).isEqualTo(1);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(outputDir1.list()).hasLength(1);
        assertThat(outputDir2.list()).hasLength(1);
        assertThat(new File(outputDir1, "fileInOutputDir")).hasContents("Some text");
        assertThat(new File(outputDir2, "fileInOutputDir")).hasContents("Some text");
    }

    @Test
    public void testCreateFileInCacheIfAbsent_OutputToDirectory() throws Exception {
        // Use same input
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();

        // First access to the cache
        File cachedDir1 =
                fileCache
                        .createFileInCacheIfAbsent(
                                inputs,
                                (outputDir) -> {
                                    FileUtils.mkdirs(outputDir);
                                    writeStringToFile(
                                            "Some text", new File(outputDir, "fileInOutputDir"));
                                })
                        .getCachedFile();

        // Second access to the cache, expect cache hit
        File cachedDir2 =
                fileCache
                        .createFileInCacheIfAbsent(
                                inputs,
                                (outputDir) -> {
                                    fail("This statement should not be executed");
                                    FileUtils.mkdirs(outputDir);
                                    writeStringToFile(
                                            "This text should not be written",
                                            new File(outputDir, "fileInOutputDir"));
                                })
                        .getCachedFile();
        assertNotNull(cachedDir2);
        assertThat(fileCache.getHits()).isEqualTo(1);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(cachedDir2).isEqualTo(cachedDir1);
        assertThat(cachedDir2.list()).hasLength(1);
        assertThat(new File(cachedDir2, "fileInOutputDir")).hasContents("Some text");
    }

    @Test
    public void testCreateFileThenCreateFileInCacheIfAbsent() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();

        File outputFile = new File(outputDir, "output");
        fileCache.createFile(outputFile, inputs, () -> writeStringToFile("Some text", outputFile));

        File cachedFile =
                fileCache
                        .createFileInCacheIfAbsent(
                                inputs,
                                (aCachedFile) -> {
                                    fail("This statement should not be executed");
                                    writeStringToFile(
                                            "This text should not be written", aCachedFile);
                                })
                        .getCachedFile();

        assertThat(fileCache.getHits()).isEqualTo(1);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(outputFile).hasContents("Some text");
        assertThat(cachedFile).hasContents("Some text");
    }

    @Test
    public void testCreateFileInCacheIfAbsentThenCreateFile() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();

        File cachedFile =
                fileCache
                        .createFileInCacheIfAbsent(
                                inputs,
                                (cachedOutputFile) ->
                                        writeStringToFile("Some text", cachedOutputFile))
                        .getCachedFile();

        File outputFile = new File(outputDir, "output");
        fileCache.createFile(
                outputFile,
                inputs,
                () -> {
                    fail("This statement should not be executed");
                    writeStringToFile("This text should not be written", outputFile);
                });

        assertThat(fileCache.getHits()).isEqualTo(1);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(outputFile).hasContents("Some text");
        assertThat(cachedFile).hasContents("Some text");
    }

    @Test
    public void testInvalidCacheDirectory() throws Exception {
        // Use an invalid cache directory, expect that an exception is thrown not when the cache is
        // created but when it is used
        File invalidCacheDirectory = new File("\0");
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(invalidCacheDirectory);

        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();
        File outputFile = new File(outputDir, "output");
        try {
            fileCache.createFile(
                    outputFile, inputs, () -> writeStringToFile("Some text", outputFile));
            fail("Expected UncheckedIOException");
        } catch (UncheckedIOException e) {
            assertThat(Throwables.getRootCause(e)).hasMessage("Invalid file path");
        }
    }

    @Test
    public void testCacheDirectoryDidNotExist_ExistsAfterCreateFile() throws Exception {
        FileUtils.delete(cacheDir);
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        assertThat(fileCache.getCacheDirectory()).doesNotExist();

        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();
        File outputFile = new File(outputDir, "output");
        fileCache.createFile(outputFile, inputs, () -> writeStringToFile("Some text", outputFile));
        assertThat(fileCache.getCacheDirectory()).exists();
    }

    @Test
    public void testCacheDirectoryDidNotExist_ExistsAfterCreateFileInCacheIfAbsent()
            throws Exception {
        FileUtils.delete(cacheDir);
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        assertThat(fileCache.getCacheDirectory()).doesNotExist();

        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();
        fileCache.createFileInCacheIfAbsent(
                inputs, (outputFile) -> writeStringToFile("Some text", outputFile));
        assertThat(fileCache.getCacheDirectory()).exists();
    }

    @Test
    public void testUnusualInput() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST).putString("file", "").build();

        File outputFile = new File(outputDir, "output");
        fileCache.createFile(outputFile, inputs, () -> writeStringToFile("Some text", outputFile));

        assertThat(fileCache.getHits()).isEqualTo(0);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(outputFile).hasContents("Some text");
    }

    @Test
    public void testCreateFile_InvalidOutputFile() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();

        File outputFile = new File(cacheDir, "output");
        try {
            fileCache.createFile(outputFile, inputs, () -> {});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            assertThat(exception).hasMessage(
                    String.format(
                            "Output file/directory '%1$s' must not be located"
                                    + " in the cache directory '%2$s'",
                            outputFile.getAbsolutePath(),
                            fileCache.getCacheDirectory().getAbsolutePath()));
        }
        assertThat(fileCache.getCacheDirectory().list()).isEmpty();

        outputFile = cacheDir.getParentFile();
        try {
            fileCache.createFile(outputFile, inputs, () -> {});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            assertThat(exception).hasMessage(
                    String.format(
                            "Output directory '%1$s' must not contain the cache directory '%2$s'",
                            outputFile.getAbsolutePath(),
                            fileCache.getCacheDirectory().getAbsolutePath()));
        }
        assertThat(fileCache.getCacheDirectory().list()).isEmpty();

        outputFile = cacheDir;
        try {
            fileCache.createFile(outputFile, inputs, () -> {});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            assertThat(exception).hasMessage(
                    String.format(
                            "Output directory must not be the same as the cache directory '%1$s'",
                            fileCache.getCacheDirectory().getAbsolutePath()));
        }
        assertThat(fileCache.getCacheDirectory().list()).isEmpty();
    }

    @Test
    public void testCreateFile_OutputFileAlreadyExistsAndIsNotCreated() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();

        File outputDir1 = new File(outputDir, "dir1");
        File outputDir2 = new File(outputDir, "dir2");
        FileUtils.mkdirs(outputDir1);
        FileUtils.mkdirs(outputDir2);
        File fileInOutputDir1 = new File(outputDir1, "output1");
        File fileInOutputDir2 = new File(outputDir2, "output2");
        Files.touch(fileInOutputDir1);
        Files.touch(fileInOutputDir2);

        fileCache.createFile(fileInOutputDir1, inputs, () -> {
            // The cache should have deleted the existing output file (but not the parent directory)
            // before calling this callback
            assertThat(fileInOutputDir1).doesNotExist();
            assertThat(fileInOutputDir1.getParentFile()).exists();
        });

        // Since the callback didn't create an output, if the cache is called again, it should
        // delete any existing output files (but not their parent directories)
        fileCache.createFile(fileInOutputDir2, inputs, () -> {});
        assertThat(fileInOutputDir2).doesNotExist();
        assertThat(fileInOutputDir2.getParentFile()).exists();
    }

    @Test
    public void testCreateFile_OutputFileDoesNotAlreadyExistAndIsCreated() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();

        File outputDir1 = new File(outputDir, "dir1");
        File outputDir2 = new File(outputDir, "dir2");
        File fileInOutputDir1 = new File(outputDir1, "output1");
        File fileInOutputDir2 = new File(outputDir2, "output2");

        fileCache.createFile(fileInOutputDir1, inputs, () -> {
            // The cache should have created the parent directory of the output file (but should not
            // have created the output file) before calling this callback
            assertThat(fileInOutputDir1).doesNotExist();
            assertThat(fileInOutputDir1.getParentFile()).exists();
            Files.touch(fileInOutputDir1);
        });

        // Since the callback created an output, if the cache is called again, it should create new
        // output files (together with their parent directories)
        fileCache.createFile(fileInOutputDir2, inputs, () -> {});
        assertThat(fileInOutputDir2).exists();
    }

    @Test
    public void testCreateFile_OutputFileNotLocked() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithMultiProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();

        File outputFile = new File(outputDir, "output");
        fileCache.createFile(outputFile, inputs, () -> writeStringToFile("Some text", outputFile));

        // The cache directory should contain 1 cache entry directory and 1 lock file for that
        // directory (no lock file for the output file)
        assertThat(fileCache.getCacheDirectory().list()).hasLength(2);
    }

    @Test
    public void testCreateFile_FileCreatorIOException() throws IOException {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();
        File outputFile = new File(outputDir, "output");

        try {
            fileCache.createFile(outputFile, inputs, () -> {
                throw new IOException("Some I/O exception");
            });
            fail("expected ExecutionException");
        } catch (ExecutionException exception) {
            assertThat(Throwables.getRootCause(exception)).isInstanceOf(IOException.class);
            assertThat(Throwables.getRootCause(exception)).hasMessage("Some I/O exception");
        }
        assertThat(fileCache.getCacheDirectory().list()).isNotEmpty();
    }

    @Test
    public void testCreateFile_FileCreatorRuntimeException() throws IOException {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();
        File outputFile = new File(outputDir, "output");

        try {
            fileCache.createFile(outputFile, inputs, () -> {
                throw new RuntimeException("Some runtime exception");
            });
            fail("expected ExecutionException");
        } catch (ExecutionException exception) {
            assertThat(Throwables.getRootCause(exception)).isInstanceOf(RuntimeException.class);
            assertThat(Throwables.getRootCause(exception)).hasMessage("Some runtime exception");
        }
        assertThat(fileCache.getCacheDirectory().list()).isNotEmpty();
    }

    @Test
    public void testCreateFile_IOExceptionNotThrownByFileCreator() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();

        // Use an invalid character in the file name
        File outputFile = new File("\0");
        try {
            fileCache.createFile(outputFile, inputs, () -> {});
            fail("expected UncheckedIOException");
        } catch (UncheckedIOException e) {
            assertThat(Throwables.getRootCause(e)).hasMessage("Invalid file path");
        }
        assertThat(fileCache.getCacheDirectory().list()).isEmpty();
    }

    @Test
    public void testCreateFileInCacheIfAbsent_FileCreatorIOException() throws IOException {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();

        try {
            fileCache.createFileInCacheIfAbsent(inputs, (outputFile) -> {
                throw new IOException("Some I/O exception");
            });
            fail("expected ExecutionException");
        } catch (ExecutionException exception) {
            assertThat(Throwables.getRootCause(exception)).isInstanceOf(IOException.class);
            assertThat(Throwables.getRootCause(exception)).hasMessage("Some I/O exception");
        }
        assertThat(fileCache.getCacheDirectory().list()).isNotEmpty();
    }

    @Test
    public void testCreateFileInCacheIfAbsent_FileCreatorRuntimeException() throws IOException {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();

        try {
            fileCache.createFileInCacheIfAbsent(inputs, (outputFile) -> {
                throw new RuntimeException("Some runtime exception");
            });
            fail("expected ExecutionException");
        } catch (ExecutionException exception) {
            assertThat(Throwables.getRootCause(exception)).isInstanceOf(RuntimeException.class);
            assertThat(Throwables.getRootCause(exception)).hasMessage("Some runtime exception");
        }
        assertThat(fileCache.getCacheDirectory().list()).isNotEmpty();
    }

    @Test
    public void testCreateFileInCacheIfAbsent_IOExceptionNotThrownByFileCreator()
            throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();

        try {
            fileCache.createFileInCacheIfAbsent(inputs, (outputFile) -> {
                // Delete the cache entry directory to so that the execution will crash later
                FileUtils.deletePath(outputFile.getParentFile());
            });
            fail("expected IOException");
        } catch (IOException exception) {
            assertThat(Throwables.getRootCause(exception))
                    .isInstanceOf(FileNotFoundException.class);
        }
        assertThat(fileCache.getCacheDirectory().list()).isEmpty();
    }

    @Test
    public void testCacheEntryExists() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();
        File outputFile = new File(outputDir, "output");

        // Case 1: Cache entry does not exist
        assertThat(fileCache.cacheEntryExists(inputs)).isFalse();

        // Case 2: Cache entry exists and is not corrupted
        fileCache.createFile(outputFile, inputs, () -> writeStringToFile("Some text", outputFile));
        assertThat(fileCache.cacheEntryExists(inputs)).isTrue();

        // Case 3: Cache entry exists but is corrupted
        File cachedFile = fileCache.getFileInCache(inputs);
        File inputsFile = new File(cachedFile.getParent(), "inputs");
        FileUtils.delete(inputsFile);

        assertThat(fileCache.cacheEntryExists(inputs)).isFalse();
    }

    @Test
    public void testGetFileInCache() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();

        File cachedFile = fileCache.getFileInCache(inputs);
        assertThat(FileUtils.isFileInDirectory(cachedFile, fileCache.getCacheDirectory())).isTrue();

        File outputFile = new File(outputDir, "output");
        fileCache.createFile(outputFile, inputs, () -> writeStringToFile("Some text", outputFile));
        assertThat(fileCache.getCacheDirectory().list()).hasLength(1);
        assertThat(cachedFile).hasContents("Some text");

        File cachedFile2 =
                fileCache
                        .createFileInCacheIfAbsent(
                                inputs,
                                (anOutputFile) -> assertThat(anOutputFile).isSameAs(cachedFile))
                        .getCachedFile();
        assertThat(fileCache.getCacheDirectory().list()).hasLength(1);
        assertThat(cachedFile2).isEqualTo(cachedFile);
    }

    @Test
    public void testCorruptedCache_InputsFileDoesNotExist() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();
        File outputFile = new File(outputDir, "output");

        FileCache.QueryResult result =
                fileCache.createFile(
                        outputFile, inputs, () -> writeStringToFile("Some text", outputFile));
        assertThat(result.getQueryEvent()).isEqualTo(FileCache.QueryEvent.MISSED);
        assertThat(result.getCauseOfCorruption()).isNull();
        assertThat(result.getCachedFile()).isNull();

        // Delete the inputs file
        File cachedFile = fileCache.getFileInCache(inputs);
        File inputsFile = new File(cachedFile.getParent(), "inputs");
        FileUtils.delete(inputsFile);

        result =
                fileCache.createFile(
                        outputFile, inputs, () -> writeStringToFile("Some text", outputFile));
        assertThat(result.getQueryEvent()).isEqualTo(FileCache.QueryEvent.CORRUPTED);
        assertNotNull(result.getCauseOfCorruption());
        assertThat(result.getCauseOfCorruption().getMessage())
                .isEqualTo(
                        String.format(
                                "Inputs file '%s' does not exist", inputsFile.getAbsolutePath()));
        assertThat(result.getCachedFile()).isNull();
    }

    @Test
    public void testCorruptedCache_InputsFileIsInvalid() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();

        FileCache.QueryResult result =
                fileCache.createFileInCacheIfAbsent(
                        inputs, (outputFile) -> writeStringToFile("Some text", outputFile));
        assertThat(result.getQueryEvent()).isEqualTo(FileCache.QueryEvent.MISSED);
        assertThat(result.getCauseOfCorruption()).isNull();
        assertThat(result.getCachedFile()).isNotNull();

        // Modify the inputs file's contents
        File cachedFile = result.getCachedFile();
        assertNotNull(cachedFile);
        File inputsFile = new File(cachedFile.getParent(), "inputs");
        writeStringToFile("Corrupted inputs", inputsFile);

        result =
                fileCache.createFileInCacheIfAbsent(
                        inputs, (outputFile) -> writeStringToFile("Some text", outputFile));
        assertThat(result.getQueryEvent()).isEqualTo(FileCache.QueryEvent.CORRUPTED);
        assertNotNull(result.getCauseOfCorruption());
        assertThat(result.getCauseOfCorruption().getMessage())
                .contains(
                        String.format(
                                "Expected contents '%s' but found '%s'",
                                inputs.toString(), "Corrupted inputs"));
        assertThat(result.getCachedFile()).isNotNull();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void testDeleteOldCacheEntries() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithMultiProcessLocking(cacheDir);
        FileCache.Inputs inputs1 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("input", "foo1")
                        .build();
        FileCache.Inputs inputs2 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("input", "foo2")
                        .build();
        FileCache.Inputs inputs3 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("input", "foo3")
                        .build();

        // Make the first cache entry look as if it was created 60 days ago
        fileCache.createFileInCacheIfAbsent(inputs1, (outputFile) -> {});
        File cacheEntryDir1 = fileCache.getFileInCache(inputs1).getParentFile();
        File lockFile1 = SynchronizedFile.getLockFile(cacheEntryDir1);
        cacheEntryDir1.setLastModified(System.currentTimeMillis() - Duration.ofDays(60).toMillis());

        // Make the second cache entry look as if it was created 30 days ago
        fileCache.createFileInCacheIfAbsent(inputs2, (outputFile) -> {});
        File cacheEntryDir2 = fileCache.getFileInCache(inputs2).getParentFile();
        File lockFile2 = SynchronizedFile.getLockFile(cacheEntryDir2);
        cacheEntryDir2.setLastModified(System.currentTimeMillis() - Duration.ofDays(30).toMillis());

        // Make the third cache entry without modifying its timestamp
        fileCache.createFileInCacheIfAbsent(inputs3, (outputFile) -> {});
        File cacheEntryDir3 = fileCache.getFileInCache(inputs3).getParentFile();
        File lockFile3 = SynchronizedFile.getLockFile(cacheEntryDir3);

        // Create some random directory inside the cache directory and make sure it won't be deleted
        File notDeletedDir = new File(fileCache.getCacheDirectory(), "foo");
        FileUtils.mkdirs(notDeletedDir);

        // The cache directory should now contains 3 cache entry directories, 3 lock files for those
        // directories, and 1 not-to-delete directory
        assertThat(checkNotNull(cacheDir.listFiles()).length).isEqualTo(7);

        // Delete all the cache entries that are older than or as old as 31 days
        fileCache.deleteOldCacheEntries(
                System.currentTimeMillis() - Duration.ofDays(31).toMillis());

        // Check that only the first cache entry and its lock file are deleted
        assertThat(checkNotNull(cacheDir.listFiles()).length).isEqualTo(5);
        assertThat(cacheEntryDir1).doesNotExist();
        assertThat(lockFile1).doesNotExist();
        assertThat(fileCache.cacheEntryExists(inputs2)).isTrue();
        assertThat(lockFile2).exists();
        assertThat(fileCache.cacheEntryExists(inputs3)).isTrue();
        assertThat(lockFile3).exists();
        assertThat(notDeletedDir).exists();

        // Delete all the cache entries that are older than or as old as the second cache entry
        fileCache.deleteOldCacheEntries(cacheEntryDir2.lastModified());

        // Check that only the third cache entry, its lock file, and the not-to-delete directory are
        // kept
        assertThat(checkNotNull(cacheDir.listFiles()).length).isEqualTo(3);
        assertThat(cacheEntryDir1).doesNotExist();
        assertThat(lockFile1).doesNotExist();
        assertThat(cacheEntryDir2).doesNotExist();
        assertThat(lockFile2).doesNotExist();
        assertThat(fileCache.cacheEntryExists(inputs3)).isTrue();
        assertThat(lockFile3).exists();
        assertThat(notDeletedDir).exists();

        // Check that deleting cache entries in an empty or non-existent cache directory does not
        // throw an exception
        FileUtils.deleteDirectoryContents(cacheDir);
        assertThat(checkNotNull(cacheDir.listFiles()).length).isEqualTo(0);
        fileCache.deleteOldCacheEntries(System.currentTimeMillis());
        assertThat(checkNotNull(cacheDir.listFiles()).length).isEqualTo(0);

        FileUtils.deletePath(cacheDir);
        assertThat(cacheDir).doesNotExist();
        fileCache.deleteOldCacheEntries(System.currentTimeMillis());
        assertThat(cacheDir).doesNotExist();
    }

    @Test
    public void testDeleteFileCache() throws IOException {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        assertThat(fileCache.getCacheDirectory()).exists();

        fileCache.delete();
        assertThat(fileCache.getCacheDirectory()).doesNotExist();
    }

    @Test
    public void testCreateFile_MultiThreads_SingleProcessLocking_SameInputDifferentOutputs()
            throws IOException {
        testCreateFile_MultiThreads_SameCacheSameInputDifferentOutputs(
                FileCache.getInstanceWithSingleProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testCreateFile_MultiThreads_SingleProcessLocking_DifferentInputsDifferentOutputs()
            throws IOException {
        testCreateFile_MultiThreads_SameCacheDifferentInputsDifferentOutputs(
                FileCache.getInstanceWithSingleProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testCreateFile_MultiThreads_SingleProcessLocking_DifferentCaches()
            throws IOException {
        testCreateFile_MultiThreads_DifferentCaches(
                FileCache.getInstanceWithSingleProcessLocking(temporaryFolder.newFolder()),
                FileCache.getInstanceWithSingleProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testCreateFile_MultiThreads_MultiProcessLocking_SameInputDifferentOutputs()
            throws IOException {
        testCreateFile_MultiThreads_SameCacheSameInputDifferentOutputs(
                FileCache.getInstanceWithMultiProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testCreateFile_MultiThreads_MultiProcessLocking_DifferentInputsDifferentOutputs()
            throws IOException {
        testCreateFile_MultiThreads_SameCacheDifferentInputsDifferentOutputs(
                FileCache.getInstanceWithMultiProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testCreateFile_MultiThreads_MultiProcessLocking_DifferentCaches()
            throws IOException {
        testCreateFile_MultiThreads_DifferentCaches(
                FileCache.getInstanceWithMultiProcessLocking(temporaryFolder.newFolder()),
                FileCache.getInstanceWithMultiProcessLocking(temporaryFolder.newFolder()));
    }

    private void testCreateFile_MultiThreads_SameCacheSameInputDifferentOutputs(
            @NonNull FileCache fileCache) throws IOException {
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();
        File[] outputFiles = {new File(outputDir, "output1"), new File(outputDir, "output2")};
        String fileContent = "Some text";

        ConcurrencyTester<File, Void> tester = new ConcurrencyTester<>();
        prepareConcurrencyTestForCreateFile(
                tester,
                new FileCache[] {fileCache, fileCache},
                new FileCache.Inputs[] {inputs, inputs},
                outputFiles,
                new String[] {fileContent, fileContent});

        // Since we use the same input, we expect only one of the actions to be executed
        tester.assertThatOnlyOneActionIsExecuted();

        assertThat(fileCache.getHits()).isEqualTo(1);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(outputFiles[0]).hasContents(fileContent);
        assertThat(outputFiles[1]).hasContents(fileContent);
    }

    private void testCreateFile_MultiThreads_SameCacheDifferentInputsDifferentOutputs(
            @NonNull FileCache fileCache) throws IOException {
        FileCache.Inputs[] inputList = {
            new FileCache.Inputs.Builder(FileCache.Command.TEST)
                    .putString("file1", "input1")
                    .build(),
            new FileCache.Inputs.Builder(FileCache.Command.TEST)
                    .putString("file2", "input2")
                    .build(),
        };
        File[] outputFiles = {new File(outputDir, "output1"), new File(outputDir, "output2")};
        String[] fileContents = {"Foo text", "Bar text"};

        ConcurrencyTester<File, Void> tester = new ConcurrencyTester<>();
        prepareConcurrencyTestForCreateFile(
                tester,
                new FileCache[] {fileCache, fileCache},
                inputList,
                outputFiles,
                fileContents);

        // Since we use different inputs, the actions are allowed to run concurrently
        tester.assertThatActionsCanRunConcurrently();

        assertThat(fileCache.getHits()).isEqualTo(0);
        assertThat(fileCache.getMisses()).isEqualTo(2);
        assertThat(outputFiles[0]).hasContents(fileContents[0]);
        assertThat(outputFiles[1]).hasContents(fileContents[1]);
    }

    private void testCreateFile_MultiThreads_DifferentCaches(
            @NonNull FileCache fileCache1, @NonNull FileCache fileCache2) throws IOException {
        // Use same input different outputs, different caches
        FileCache[] fileCaches = {fileCache1, fileCache2};
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();
        File[] outputFiles = {new File(outputDir, "output1"), new File(outputDir, "output2")};
        String[] fileContents = {"Foo text", "Bar text"};

        ConcurrencyTester<File, Void> tester = new ConcurrencyTester<>();
        prepareConcurrencyTestForCreateFile(
                tester,
                fileCaches,
                new FileCache.Inputs[] {inputs, inputs},
                outputFiles,
                fileContents);

        // Since we use different caches, even though we use the same input, the actions are allowed
        // to run concurrently
        tester.assertThatActionsCanRunConcurrently();

        assertThat(fileCaches[0].getHits()).isEqualTo(0);
        assertThat(fileCaches[0].getMisses()).isEqualTo(1);
        assertThat(fileCaches[1].getHits()).isEqualTo(0);
        assertThat(fileCaches[1].getMisses()).isEqualTo(1);
        assertThat(outputFiles[0]).hasContents(fileContents[0]);
        assertThat(outputFiles[1]).hasContents(fileContents[1]);
    }

    /**
     * Performs a few steps common to the concurrency tests for {@link FileCache#createFile(File,
     * FileCache.Inputs, ExceptionRunnable)}.
     */
    private static void prepareConcurrencyTestForCreateFile(
            @NonNull ConcurrencyTester<File, Void> tester,
            @NonNull FileCache[] fileCaches,
            @NonNull FileCache.Inputs[] inputsList,
            @NonNull File[] outputFiles,
            @NonNull String[] fileContents) {
        for (int i = 0; i < fileCaches.length; i++) {
            FileCache fileCache = fileCaches[i];
            FileCache.Inputs inputs = inputsList[i];
            File outputFile = outputFiles[i];
            String fileContent = fileContents[i];

            Function<File, Void> actionUnderTest =
                    (File file) -> {
                        try {
                            writeStringToFile(fileContent, file);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        return null;
                    };
            tester.addMethodInvocationFromNewThread(
                    (Function<File, Void> anActionUnderTest) -> {
                        try {
                            fileCache.createFile(
                                    outputFile, inputs, () -> anActionUnderTest.apply(outputFile));
                        } catch (ExecutionException | IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    },
                    actionUnderTest);
        }
    }

    @Test
    public void testCreateFileInCacheIfAbsent_MultiThreads_SingleProcessLocking_SameInput()
            throws IOException {
        testCreateFileInCacheIfAbsent_MultiThreads_SameCacheSameInput(
                FileCache.getInstanceWithSingleProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testCreateFileInCacheIfAbsent_MultiThreads_SingleProcessLocking_DifferentInputs()
            throws IOException {
        testCreateFileInCacheIfAbsent_MultiThreads_SameCacheDifferentInputs(
                FileCache.getInstanceWithSingleProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testCreateFileInCacheIfAbsent_MultiThreads_SingleProcessLocking_DifferentCaches()
            throws IOException {
        testCreateFileInCacheIfAbsent_MultiThreads_DifferentCaches(
                FileCache.getInstanceWithSingleProcessLocking(temporaryFolder.newFolder()),
                FileCache.getInstanceWithSingleProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testCreateFileInCacheIfAbsent_MultiThreads_MultiProcessLocking_SameInput()
            throws IOException {
        testCreateFileInCacheIfAbsent_MultiThreads_SameCacheSameInput(
                FileCache.getInstanceWithMultiProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testCreateFileInCacheIfAbsent_MultiThreads_MultiProcessLocking_DifferentInputs()
            throws IOException {
        testCreateFileInCacheIfAbsent_MultiThreads_SameCacheDifferentInputs(
                FileCache.getInstanceWithMultiProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testCreateFileInCacheIfAbsent_MultiThreads_MultiProcessLocking_DifferentCaches()
            throws IOException {
        testCreateFileInCacheIfAbsent_MultiThreads_DifferentCaches(
                FileCache.getInstanceWithMultiProcessLocking(temporaryFolder.newFolder()),
                FileCache.getInstanceWithMultiProcessLocking(temporaryFolder.newFolder()));
    }

    private static void testCreateFileInCacheIfAbsent_MultiThreads_SameCacheSameInput(
            @NonNull FileCache fileCache) throws IOException {
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();
        String fileContent = "Some text";
        File[] cachedFiles = new File[2];

        ConcurrencyTester<File, Void> tester = new ConcurrencyTester<>();
        prepareConcurrencyTestForCreateFileInCacheIfAbsent(
                tester,
                new FileCache[] {fileCache, fileCache},
                new FileCache.Inputs[] {inputs, inputs},
                new String[] {fileContent, fileContent},
                cachedFiles);

        // Since we use the same input, we expect only one of the actions to be executed
        tester.assertThatOnlyOneActionIsExecuted();

        assertThat(fileCache.getHits()).isEqualTo(1);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(cachedFiles[1]).isEqualTo(cachedFiles[0]);
        assertThat(cachedFiles[0]).hasContents(fileContent);
    }

    private static void testCreateFileInCacheIfAbsent_MultiThreads_SameCacheDifferentInputs(
            @NonNull FileCache fileCache) throws IOException {
        FileCache.Inputs[] inputList = {
            new FileCache.Inputs.Builder(FileCache.Command.TEST)
                    .putString("file1", "input1")
                    .build(),
            new FileCache.Inputs.Builder(FileCache.Command.TEST)
                    .putString("file2", "input2")
                    .build(),
        };
        String[] fileContents = {"Foo text", "Bar text"};
        File[] cachedFiles = new File[2];

        ConcurrencyTester<File, Void> tester = new ConcurrencyTester<>();
        prepareConcurrencyTestForCreateFileInCacheIfAbsent(
                tester,
                new FileCache[] {fileCache, fileCache},
                inputList,
                fileContents,
                cachedFiles);

        // Since we use different inputs, the actions are allowed to run concurrently
        tester.assertThatActionsCanRunConcurrently();

        assertThat(fileCache.getHits()).isEqualTo(0);
        assertThat(fileCache.getMisses()).isEqualTo(2);
        assertThat(cachedFiles[1]).isNotEqualTo(cachedFiles[0]);
        assertThat(cachedFiles[0]).hasContents(fileContents[0]);
        assertThat(cachedFiles[1]).hasContents(fileContents[1]);
    }

    private static void testCreateFileInCacheIfAbsent_MultiThreads_DifferentCaches(
            @NonNull FileCache fileCache1, @NonNull FileCache fileCache2) throws IOException {
        // Use same input, different caches
        FileCache[] fileCaches = {fileCache1, fileCache2};
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", "input")
                        .build();
        String[] fileContents = {"Foo text", "Bar text"};
        File[] cachedFiles = new File[2];

        ConcurrencyTester<File, Void> tester = new ConcurrencyTester<>();
        prepareConcurrencyTestForCreateFileInCacheIfAbsent(
                tester,
                fileCaches,
                new FileCache.Inputs[] {inputs, inputs},
                fileContents,
                cachedFiles);

        // Since we use different caches, even though we use the same input, the actions are allowed
        // to run concurrently
        tester.assertThatActionsCanRunConcurrently();

        assertThat(fileCaches[0].getHits()).isEqualTo(0);
        assertThat(fileCaches[0].getMisses()).isEqualTo(1);
        assertThat(fileCaches[1].getHits()).isEqualTo(0);
        assertThat(fileCaches[1].getMisses()).isEqualTo(1);
        assertThat(cachedFiles[1]).isNotEqualTo(cachedFiles[0]);
        assertThat(cachedFiles[0]).hasContents(fileContents[0]);
        assertThat(cachedFiles[1]).hasContents(fileContents[1]);
    }

    /**
     * Performs a few steps common to the concurrency tests for {@link
     * FileCache#createFileInCacheIfAbsent(FileCache.Inputs, ExceptionConsumer)}.
     */
    @SuppressWarnings("ReturnValueIgnored")  // uses dubious type Function<File, Void>
    private static void prepareConcurrencyTestForCreateFileInCacheIfAbsent(
            @NonNull ConcurrencyTester<File, Void> tester,
            @NonNull FileCache[] fileCaches,
            @NonNull FileCache.Inputs[] inputsList,
            @NonNull String[] fileContents,
            @NonNull File[] cachedFiles) {
        for (int i = 0; i < fileCaches.length; i++) {
            FileCache fileCache = fileCaches[i];
            FileCache.Inputs inputs = inputsList[i];
            String fileContent = fileContents[i];
            final int idx = i;

            Function<File, Void> actionUnderTest =
                    (File file) -> {
                        try {
                            writeStringToFile(fileContent, file);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        return null;
                    };
            tester.addMethodInvocationFromNewThread(
                    (Function<File, Void> anActionUnderTest) -> {
                        try {
                            cachedFiles[idx] =
                                    fileCache
                                            .createFileInCacheIfAbsent(
                                                    inputs, anActionUnderTest::apply)
                                            .getCachedFile();
                        } catch (ExecutionException | IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    },
                    actionUnderTest);
        }
    }

    @Test
    public void testCacheInputsGetKey() throws IOException {
        // Test realistic example
        File inputFile =
                new File(
                        "/Users/foo/Android/Sdk/extras/android/m2repository/com/android/support/"
                                + "support-annotations/23.3.0/support-annotations-23.3.0.jar");
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("file", inputFile.getPath())
                        .putString(
                                "buildToolsRevision", Revision.parseRevision("23.0.3").toString())
                        .putBoolean("jumboMode", true)
                        .putBoolean("optimize", false)
                        .putBoolean("multiDex", true)
                        .putString("classpath", "foo")
                        .putLong("cacheVersion", 1)
                        .build();
        assertThat(inputs.toString())
                .isEqualTo(
                        Joiner.on(System.lineSeparator())
                                .join(
                                        "COMMAND=TEST",
                                        "file=" + inputFile.getPath(),
                                        "buildToolsRevision=23.0.3",
                                        "jumboMode=true",
                                        "optimize=false",
                                        "multiDex=true",
                                        "classpath=foo",
                                        "cacheVersion=1"));
        assertThat(inputs.getKey())
                .isEqualTo(Hashing.sha256().hashUnencodedChars(inputs.toString()).toString());

        // Test no input parameters
        try {
            new FileCache.Inputs.Builder(FileCache.Command.TEST).build();
            fail("expected IllegalStateException");
        } catch (IllegalStateException exception) {
            assertThat(exception).hasMessage("Inputs must not be empty.");
        }

        // Test input parameters with duplicate names
        try {
            new FileCache.Inputs.Builder(FileCache.Command.TEST)
                    .putString("arg", "foo")
                    .putBoolean("arg", false)
                    .build();
            fail("expected IllegalStateException");
        } catch (IllegalStateException exception) {
            assertThat(exception).hasMessage("Input parameter arg already exists");
        }

        // Test input parameters with empty strings
        inputs = new FileCache.Inputs.Builder(FileCache.Command.TEST).putString("arg", "").build();
        assertThat(inputs.toString())
                .isEqualTo(Joiner.on(System.lineSeparator()).join("COMMAND=TEST", "arg="));
        assertThat(inputs.getKey())
                .isEqualTo(Hashing.sha256().hashUnencodedChars(inputs.toString()).toString());

        // Test comparing inputs with different sizes
        FileCache.Inputs inputs1 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putBoolean("arg1", true)
                        .putBoolean("arg2", true)
                        .build();
        FileCache.Inputs inputs2 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putBoolean("arg1", true)
                        .putBoolean("arg2", true)
                        .putBoolean("arg3", true)
                        .build();
        assertThat(inputs1.toString()).isNotEqualTo(inputs2.toString());
        assertThat(inputs1.getKey()).isNotEqualTo(inputs2.getKey());

        // Test comparing inputs with different names
        inputs1 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putBoolean("arg1", true)
                        .putBoolean("arg2", true)
                        .build();
        inputs2 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putBoolean("arg1", true)
                        .putBoolean("arg3", true)
                        .build();
        assertThat(inputs1.toString()).isNotEqualTo(inputs2.toString());
        assertThat(inputs1.getKey()).isNotEqualTo(inputs2.getKey());

        // Test comparing inputs with different orders
        inputs1 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("arg1", "foo")
                        .putBoolean("arg2", false)
                        .build();
        inputs2 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putBoolean("arg2", false)
                        .putString("arg1", "foo")
                        .build();
        assertThat(inputs1.toString()).isNotEqualTo(inputs2.toString());
        assertThat(inputs1.getKey()).isNotEqualTo(inputs2.getKey());

        // Test comparing inputs with different values
        inputs1 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putBoolean("arg1", true)
                        .putBoolean("arg2", true)
                        .build();
        inputs2 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putBoolean("arg1", true)
                        .putBoolean("arg2", false)
                        .build();
        assertThat(inputs1.toString()).isNotEqualTo(inputs2.toString());
        assertThat(inputs1.getKey()).isNotEqualTo(inputs2.getKey());

        // Test comparing inputs with same size, names, order, and values
        inputs1 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("arg1", "foo")
                        .putBoolean("arg2", false)
                        .build();
        inputs2 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("arg1", "foo")
                        .putBoolean("arg2", false)
                        .build();
        assertThat(inputs1.toString()).isEqualTo(inputs2.toString());
        assertThat(inputs1.getKey()).isEqualTo(inputs2.getKey());
    }

    @Test
    public void testCacheInputsFileAsInput() throws IOException {
        // Test identifying an input file with FileProperties.HASH
        File inputFile = temporaryFolder.newFile("inputFile");
        writeStringToFile("Some text", inputFile);

        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putFile("fileHash", inputFile, FileCache.FileProperties.HASH)
                        .build();
        assertThat(inputs.toString())
                .isEqualTo(
                        Joiner.on(System.lineSeparator())
                                .join(
                                        "COMMAND=TEST",
                                        "fileHash="
                                                + Hashing.sha256()
                                                        .hashBytes("Some text".getBytes())));

        // Test identifying an input file with FileProperties.PATH_HASH
        inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putFile("file", inputFile, FileCache.FileProperties.PATH_HASH)
                        .build();
        assertThat(inputs.toString())
                .isEqualTo(
                        Joiner.on(System.lineSeparator())
                                .join(
                                        "COMMAND=TEST",
                                        "file.path=" + inputFile.getPath(),
                                        "file.hash="
                                                + Hashing.sha256()
                                                        .hashBytes("Some text".getBytes())));

        // Test identifying an input file with FileProperties.PATH_SIZE_TIMESTAMP
        inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putFile("file", inputFile, FileCache.FileProperties.PATH_SIZE_TIMESTAMP)
                        .build();
        assertThat(inputs.toString())
                .isEqualTo(
                        Joiner.on(System.lineSeparator())
                                .join(
                                        "COMMAND=TEST",
                                        "file.path=" + inputFile.getPath(),
                                        "file.size=" + inputFile.length(),
                                        "file.timestamp=" + inputFile.lastModified()));

        // Test input files with same hash, different paths
        File fooFile = temporaryFolder.newFile("fooFile");
        File barFile = temporaryFolder.newFile("barFile");
        writeStringToFile("Some text", fooFile);
        writeStringToFile("Some text", barFile);
        FileCache.Inputs inputs1 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putFile("fileHash", fooFile, FileCache.FileProperties.HASH)
                        .build();
        FileCache.Inputs inputs2 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putFile("fileHash", barFile, FileCache.FileProperties.HASH)
                        .build();
        assertThat(inputs1.getKey()).isEqualTo(inputs2.getKey());

        // Test input files with different hashes, same path
        inputs1 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putFile("fileHash", fooFile, FileCache.FileProperties.HASH)
                        .build();
        writeStringToFile("Updated text", fooFile);
        inputs2 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putFile("fileHash", fooFile, FileCache.FileProperties.HASH)
                        .build();
        assertThat(inputs1.getKey()).isNotEqualTo(inputs2.getKey());
    }

    @Test
    public void testGetFileHash() throws IOException {
        // Test file with some contents
        File inputFile = temporaryFolder.newFile();
        writeStringToFile("Some text", inputFile);
        assertThat(FileCache.Inputs.Builder.getFileHash(inputFile)).hasLength(64);

        // Test file with empty contents
        File emptyFile = temporaryFolder.newFile();
        assertThat(FileCache.Inputs.Builder.getFileHash(emptyFile))
                .isEqualTo(Hashing.sha256().hashUnencodedChars("").toString());

        /*
         * Test that the hash computation is based on the file's contents
         */
        // Update the file's contents, the hash must change
        writeStringToFile("Foo text", inputFile);
        String hash1 = FileCache.Inputs.Builder.getFileHash(inputFile);
        writeStringToFile("Bar text", inputFile);
        String hash2 = FileCache.Inputs.Builder.getFileHash(inputFile);
        assertThat(hash1).isNotEqualTo(hash2);

        // Two files having the same contents must have the same hash. This test also proves that
        // the hash computation does not consider the path or name of the given file.
        File fooFile = temporaryFolder.newFile("fooFile");
        File barFile = temporaryFolder.newFile("barFile");
        writeStringToFile("Some text", fooFile);
        writeStringToFile("Some text", barFile);
        assertThat(FileCache.Inputs.Builder.getFileHash(fooFile))
                .isEqualTo(FileCache.Inputs.Builder.getFileHash(barFile));
    }

    @Test
    public void testCacheInputsDirectoryAsInput() throws IOException {
        // Test identifying an input directory with DirectoryProperties.HASH
        File inputDir = temporaryFolder.newFolder("inputDir");
        writeSampleContentsToDirectory(inputDir);

        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putDirectory("directoryHash", inputDir, FileCache.DirectoryProperties.HASH)
                        .build();
        assertThat(inputs.toString())
                .contains(Joiner.on(System.lineSeparator()).join("COMMAND=TEST", "directoryHash="));

        // Test identifying an input directory with DirectoryProperties.PATH_HASH
        inputs =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putDirectory(
                                "directory", inputDir, FileCache.DirectoryProperties.PATH_HASH)
                        .build();
        assertThat(inputs.toString())
                .contains(
                        Joiner.on(System.lineSeparator())
                                .join(
                                        "COMMAND=TEST",
                                        "directory.path=" + inputDir.getPath(),
                                        "directory.hash="));

        // Test input directories with same hash, different paths
        File fooDir = temporaryFolder.newFolder("fooDir");
        File barDir = temporaryFolder.newFolder("barDir");
        writeSampleContentsToDirectory(fooDir);
        writeSampleContentsToDirectory(barDir);
        FileCache.Inputs inputs1 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putDirectory("directoryHash", fooDir, FileCache.DirectoryProperties.HASH)
                        .build();
        FileCache.Inputs inputs2 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putDirectory("directoryHash", barDir, FileCache.DirectoryProperties.HASH)
                        .build();
        assertThat(inputs1.getKey()).isEqualTo(inputs2.getKey());

        // Test input directories with different hashes, same path
        inputs1 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putDirectory("directoryHash", fooDir, FileCache.DirectoryProperties.HASH)
                        .build();
        writeStringToFile("Some text", new File(fooDir, "newFile.txt"));
        inputs2 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putDirectory("directoryHash", fooDir, FileCache.DirectoryProperties.HASH)
                        .build();
        assertThat(inputs1.getKey()).isNotEqualTo(inputs2.getKey());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void testGetDirectoryHash() throws Exception {
        // Test directory with some contents
        File inputDir = temporaryFolder.newFolder();
        writeSampleContentsToDirectory(inputDir);
        assertThat(FileCache.Inputs.Builder.getDirectoryHash(inputDir)).hasLength(64);

        // Test directory with empty contents
        File emptyDir = temporaryFolder.newFile();
        assertThat(FileCache.Inputs.Builder.getFileHash(emptyDir))
                .isEqualTo(Hashing.sha256().hashUnencodedChars("").toString());

        /*
         * Test that the hash computation is based on the directory's contents
         */
        // Add a file in the directory, the hash must change
        writeSampleContentsToDirectory(inputDir);
        String hash1 = FileCache.Inputs.Builder.getDirectoryHash(inputDir);
        writeStringToFile("Some text", new File(new File(inputDir, "foo"), "newFile.txt"));
        String hash2 = FileCache.Inputs.Builder.getDirectoryHash(inputDir);
        assertThat(hash1).isNotEqualTo(hash2);

        // Update a file in the directory, the hash must change
        writeSampleContentsToDirectory(inputDir);
        hash1 = FileCache.Inputs.Builder.getDirectoryHash(inputDir);
        writeStringToFile("Updated text", new File(new File(inputDir, "foo"), "foo1.txt"));
        hash2 = FileCache.Inputs.Builder.getDirectoryHash(inputDir);
        assertThat(hash1).isNotEqualTo(hash2);

        // Delete a file in the directory, the hash must change
        writeSampleContentsToDirectory(inputDir);
        hash1 = FileCache.Inputs.Builder.getDirectoryHash(inputDir);
        FileUtils.delete(new File(new File(inputDir, "foo"), "foo1.txt"));
        hash2 = FileCache.Inputs.Builder.getDirectoryHash(inputDir);
        assertThat(hash1).isNotEqualTo(hash2);

        // Rename a file in the directory, the hash must change
        writeSampleContentsToDirectory(inputDir);
        hash1 = FileCache.Inputs.Builder.getDirectoryHash(inputDir);
        new File(new File(inputDir, "foo"), "foo1.txt")
                .renameTo(new File(new File(inputDir, "foo"), "foo3.txt"));
        hash2 = FileCache.Inputs.Builder.getDirectoryHash(inputDir);
        assertThat(hash1).isNotEqualTo(hash2);

        // Move a file in the directory, the hash must change
        writeSampleContentsToDirectory(inputDir);
        hash1 = FileCache.Inputs.Builder.getDirectoryHash(inputDir);
        new File(new File(inputDir, "foo"), "foo1.txt")
                .renameTo(new File(new File(inputDir, "bar"), "foo1.txt"));
        hash2 = FileCache.Inputs.Builder.getDirectoryHash(inputDir);
        assertThat(hash1).isNotEqualTo(hash2);

        // Add a subdirectory in the directory, the hash must change
        writeSampleContentsToDirectory(inputDir);
        hash1 = FileCache.Inputs.Builder.getDirectoryHash(inputDir);
        FileUtils.mkdirs(new File(new File(inputDir, "foo"), "newDir"));
        hash2 = FileCache.Inputs.Builder.getDirectoryHash(inputDir);
        assertThat(hash1).isNotEqualTo(hash2);

        // Delete a subdirectory in the directory, the hash must change
        writeSampleContentsToDirectory(inputDir);
        hash1 = FileCache.Inputs.Builder.getDirectoryHash(inputDir);
        FileUtils.delete(new File(inputDir, "bar"));
        hash2 = FileCache.Inputs.Builder.getDirectoryHash(inputDir);
        assertThat(hash1).isNotEqualTo(hash2);

        // Rename a subdirectory in the directory, the hash must change
        writeSampleContentsToDirectory(inputDir);
        hash1 = FileCache.Inputs.Builder.getDirectoryHash(inputDir);
        new File(inputDir, "foo").renameTo(new File(inputDir, "foo2"));
        hash2 = FileCache.Inputs.Builder.getDirectoryHash(inputDir);
        assertThat(hash1).isNotEqualTo(hash2);

        // Move a subdirectory in the directory, the hash must change
        writeSampleContentsToDirectory(inputDir);
        hash1 = FileCache.Inputs.Builder.getDirectoryHash(inputDir);
        new File(inputDir, "foo").renameTo(new File(new File(inputDir, "bar"), "foo"));
        hash2 = FileCache.Inputs.Builder.getDirectoryHash(inputDir);
        assertThat(hash1).isNotEqualTo(hash2);

        // Two directories having the same contents must have the same hash. This test also proves
        // that the hash computation does not consider the path or name of the given directory.
        File fooDir = temporaryFolder.newFolder("fooDir");
        File barDir = temporaryFolder.newFolder("barDir");
        writeSampleContentsToDirectory(fooDir);
        writeSampleContentsToDirectory(barDir);
        assertThat(FileCache.Inputs.Builder.getDirectoryHash(fooDir))
                .isEqualTo(FileCache.Inputs.Builder.getDirectoryHash(barDir));
    }

    @Test
    public void testCacheSession() throws Exception {
        FileCache.CacheSession session = FileCache.newSession();
        FileCache.CacheSession otherSession = FileCache.newSession();

        // Test file with some contents
        File inputFile = temporaryFolder.newFile();
        writeStringToFile("Some text", inputFile);
        String fileInitialKey =
                new FileCache.Inputs.Builder(FileCache.Command.TEST, session)
                        .putFile("file", inputFile, FileCache.FileProperties.HASH)
                        .build()
                        .getKey();

        // Change file content
        writeStringToFile("Some different text", inputFile);

        // Take key again with different sessions.
        String changedFileKeyWithoutSession =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putFile("file", inputFile, FileCache.FileProperties.HASH)
                        .build()
                        .getKey();
        String changedFileKeyWithSession =
                new FileCache.Inputs.Builder(FileCache.Command.TEST, session)
                        .putFile("file", inputFile, FileCache.FileProperties.HASH)
                        .build()
                        .getKey();
        String changedFileKeyWithOtherSession =
                new FileCache.Inputs.Builder(FileCache.Command.TEST, otherSession)
                        .putFile("file", inputFile, FileCache.FileProperties.HASH)
                        .build()
                        .getKey();

        // File was changed so key must change unless we reuse the same session.
        assertThat(changedFileKeyWithoutSession).isNotEqualTo(fileInitialKey);
        assertThat(changedFileKeyWithSession).isEqualTo(fileInitialKey);
        assertThat(changedFileKeyWithOtherSession).isNotEqualTo(fileInitialKey);

        // reset sessions
        session = FileCache.newSession();
        otherSession = FileCache.newSession();

        // Test directory with some contents
        File inputDir = temporaryFolder.newFolder();
        writeSampleContentsToDirectory(inputDir);
        String dirInitialKey =
                new FileCache.Inputs.Builder(FileCache.Command.TEST, session)
                        .putDirectory("dir", inputDir, FileCache.DirectoryProperties.HASH)
                        .build()
                        .getKey();

        // change directory structure
        java.nio.file.Files.createDirectory(new File(inputDir, "additionalDirectory").toPath());

        String changedDirKeyWithoutSession =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putDirectory("dir", inputDir, FileCache.DirectoryProperties.HASH)
                        .build()
                        .getKey();
        String changedDirKeyWithSession =
                new FileCache.Inputs.Builder(FileCache.Command.TEST, session)
                        .putDirectory("dir", inputDir, FileCache.DirectoryProperties.HASH)
                        .build()
                        .getKey();
        String changedDirKeyWithOtherSession =
                new FileCache.Inputs.Builder(FileCache.Command.TEST, otherSession)
                        .putDirectory("dir", inputDir, FileCache.DirectoryProperties.HASH)
                        .build()
                        .getKey();

        // Directory content was changed so key must change unless we reuse the same session.
        assertThat(changedDirKeyWithoutSession).isNotEqualTo(dirInitialKey);
        assertThat(changedDirKeyWithSession).isEqualTo(dirInitialKey);
        assertThat(changedDirKeyWithOtherSession).isNotEqualTo(dirInitialKey);
    }

    private static void writeStringToFile(@NonNull String content, @NonNull File file)
            throws IOException {
        Files.asCharSink(file, StandardCharsets.UTF_8).write(content);
    }

    private static void writeSampleContentsToDirectory(@NonNull File directory) throws IOException {
        // Create a sample directory with the following structure:
        // |- directory
        //    |- foo
        //       |- foo1.txt
        //       |- foo2.txt
        //    |- bar (empty directory)
        //    |- baz.txt
        File fooDir = new File(directory, "foo");
        File foo1File = new File(fooDir, "foo1.txt");
        File foo2File = new File(fooDir, "foo2.txt");
        File barDir = new File(directory, "bar");
        File bazFile = new File(directory, "baz.txt");

        FileUtils.deletePath(directory);
        FileUtils.mkdirs(fooDir);
        FileUtils.mkdirs(barDir);
        writeStringToFile("foo1", foo1File);
        writeStringToFile("foo2", foo2File);
        writeStringToFile("baz", bazFile);
    }
}
