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

package com.android.builder.merge;

import static com.google.common.truth.TruthJUnit.assume;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.files.FileCacheByPath;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.ide.common.resources.FileStatus;
import com.android.tools.build.apkzlib.zip.StoredEntry;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.utils.FileUtils;
import com.android.utils.Pair;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Closer;
import com.google.common.io.Files;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LazyIncrementalFileMergerInputTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Parameterized.Parameter(0)
    public boolean zipInputs;

    @Parameterized.Parameter(1)
    public int baseDirectoryCount;

    @SuppressWarnings("unused")
    @Parameterized.Parameter(2)
    public String testName;

    private Random random = new Random();

    @Parameterized.Parameters(name = "{2} ({0})")
    public static List<Object[]> getParameters() {
        return Arrays.asList(new Object[][] {
                { false, 1, "1 directory" },
                { false, 2, "2 directories" },
                { true, 1, "1 zip" },
                { true, 2, "2 zips" }
        });
    }

    /**
     * Obtains the directory input corresponding to file {@code f}. If {@code f} is a directory,
     * then {@code f} is returned, otherwise, {@code f} should be a zip file and the corresponding
     * directory is returned.
     *
     * @param f the directory or zip
     * @return {@code f} if it is a directory; the directory corresponding to the zip file if
     * {@code f} is a zip
     */
    @NonNull
    private File inputDirectory(@NonNull File f) {
        if (f.isDirectory()) {
            return f;
        }

        assertTrue(f.isFile());

        String fName = f.getName();
        String fNameNonZip = fName.substring(0, fName.length() - ".zip".length());
        return new File(f.getParent(), fNameNonZip);
    }

    /**
     * Picks a random file from a list, ignoring some files.
     *
     * @param files a file to pick randomly from
     * @param avoid files whose names are in this set are never picked
     * @return the file picked, or {@code null} if there are no files in {@code files} that are
     * not in {@code avoid}
     */
    @Nullable
    private File pick(@NonNull List<File> files, @NonNull Set<String> avoid) {
        List<File> remaining =
                files.stream()
                        .filter(f -> !avoid.contains(f.getName()))
                        .collect(Collectors.toList());
        if (remaining.isEmpty()) {
            return null;
        }

        int idx = random.nextInt(remaining.size());
        return remaining.get(idx);
    }

    /**
     * Writes random content in a file.
     *
     * @param f the file
     */
    private void writeRandomContent(@NonNull File f) {
        byte[] data = new byte[10];
        random.nextBytes(data);

        try {
            Files.write(data, f);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Obtains the list of files in a directory.
     *
     * @param dir the directory
     * @return the list of files; an empty list if the directory is empty
     */
    @NonNull
    private List<File> directoryContents(@NonNull File dir) {
        assertTrue(dir.isDirectory());
        File[] files = dir.listFiles();
        assertNotNull(files);
        return Arrays.asList(files);
    }

    /**
     * Creates a new random file in the provided directory.
     *
     * @param dir the directory
     * @param avoid names of files to avoid
     * @return the file created
     */
    @NonNull
    private File makeRandomFile(@NonNull File dir, @Nullable Set<String> avoid) {
        File randomFile;

        do {
            String name;

            do {
                name = "f" + random.nextLong();
            } while (avoid != null && avoid.contains(name));

            randomFile = new File(dir, name);
        } while (randomFile.exists());

        writeRandomContent(randomFile);
        return randomFile;
    }

    /**
     * Creates {@code count} input files spread along {@link #baseDirectoryCount} and returns the
     * directories. If {@link #zipInputs} is {@code true}, then the directories are zipped and the
     * actual zips are returned.
     *
     * @param count total files to write
     * @return the directories or zips
     * @throws IOException I/O error
     */
    private Set<File> makeInputs(int count) throws IOException {
        List<File> baseDirs = new ArrayList<>();
        for (int i = 0; i < baseDirectoryCount; i++) {
            baseDirs.add(temporaryFolder.newFolder());
        }

        for (int j = 0; j < count; j++) {
            File randomDir = baseDirs.get(random.nextInt(baseDirectoryCount));
            makeRandomFile(randomDir, null);
        }

        if (zipInputs) {
            for (int i = 0; i < baseDirectoryCount; i++) {
                File dir = baseDirs.get(i);
                File z = new File(dir.getParent(), dir.getName() + ".zip");
                try (Closer closer = Closer.create()) {
                    ZFile zf = closer.register(ZFile.openReadWrite(z));

                    File[] files = dir.listFiles();
                    assertNotNull(files);
                    for (File f : files) {
                        FileInputStream fis = closer.register(new FileInputStream(f));
                        zf.add(f.getName(), fis);
                    }

                    zf.update();
                }

                baseDirs.set(i, z);
            }
        }

        return new HashSet<>(baseDirs);
    }

    /**
     * Makes random changes to inputs.
     *
     * @param baseFiles the base files where inputs are; these can be directories or zip files
     * @return two maps: the first maps files to changes, the second maps the expected changes to
     * output paths
     * @throws IOException I/O error
     */
    @NonNull
    private Pair<Map<File, FileStatus>, Map<String, FileStatus>> makeChanges(
            @NonNull Set<File> baseFiles) throws IOException {
        Map<File, FileStatus> changes = new HashMap<>();

        /*
         * As we do changes, we keep track the the potential output for each change. Note that
         * changes stored here are on an individual file basis. If some input directories are
         * zipped, then the changes need to be reported in the zip as a whole in 'changes'.
         * Still, here we discuss updating 'potentialChanges' on a file-by-file basis.
         *
         * 'potentialChanges' keeps track of all changes on files. Files that are mapped to an
         * empty optional are files that have not been changed. As we progress through all files in
         * each base directory (or zip file), we keep this map updated.
         *
         * The 'updatedPotentialChanges' bi-consumer updates this map when a file is created,
         * updated or deleted reflecting the result in the end file.
         */
        Map<String, Optional<FileStatus>> potentialChanges = new HashMap<>();

        BiConsumer<File, Optional<FileStatus>> updatedPotentialChanges = (f, up) -> {
            Optional<FileStatus> existing = potentialChanges.get(f.getName());
            if (existing == null) {
                potentialChanges.put(f.getName(), up);
            } else if (existing.orElse(null) != up.orElse(null)) {
                /*
                 * Any two different changes, or a change and a non-change means the resulting file
                 * will be updated.
                 */
                potentialChanges.put(f.getName(), Optional.of(FileStatus.CHANGED));
            }
        };

        /*
         * Make changes to each base file.
         */
        for (File f : baseFiles) {
            File fDir = inputDirectory(f);

            ZFile zfile;
            if (f.isFile()) {
                zfile = ZFile.openReadWrite(f);
            } else {
                zfile = null;
            }

            /*
             * Do some random changes. Keep track of files already changed so we don't do two
             * changes on the same file.
             */
            Set<String> avoid = new HashSet<>();
            for (int i = 0; i < 10; i++) {
                double r = random.nextDouble();
                if (r < 0.2) {
                    /*
                     * Add a new file with 20% probability.
                     */
                    File newFile = makeRandomFile(fDir, avoid);
                    updatedPotentialChanges.accept(newFile, Optional.of(FileStatus.NEW));
                    avoid.add(newFile.getName());

                    if (zfile == null) {
                        changes.put(newFile, FileStatus.NEW);
                    } else {
                        try (FileInputStream fis = new FileInputStream(newFile)) {
                            zfile.add(newFile.getName(), fis);
                        }

                        changes.put(f, FileStatus.CHANGED);
                    }
                } else if (r < 0.4) {
                    /*
                     * Delete a file with 20% probability, if there are files that can be deleted.
                     */
                    File toDelete = pick(directoryContents(fDir), avoid);
                    if (toDelete != null) {
                        FileUtils.delete(toDelete);
                        updatedPotentialChanges.accept(toDelete, Optional.of(FileStatus.REMOVED));
                        avoid.add(toDelete.getName());

                        if (zfile == null) {
                            changes.put(toDelete, FileStatus.REMOVED);
                        } else {
                            StoredEntry entry = zfile.get(toDelete.getName());
                            assertNotNull(entry);
                            entry.delete();
                            changes.put(f, FileStatus.CHANGED);
                        }
                    }
                } else {
                    /*
                     * Make a change with 20% probability, if there are any files in the directory.
                     */
                    File toChange = pick(directoryContents(fDir), avoid);
                    if (toChange != null) {
                        writeRandomContent(toChange);
                        updatedPotentialChanges.accept(toChange, Optional.of(FileStatus.REMOVED));
                        avoid.add(toChange.getName());

                        if (zfile == null) {
                            changes.put(toChange, FileStatus.CHANGED);
                        } else {
                            try (FileInputStream fis = new FileInputStream(toChange)) {
                                zfile.add(toChange.getName(), fis);
                            }

                            changes.put(f, FileStatus.CHANGED);
                        }
                    }
                }
            }

            /*
             * Mark all remaining files as unchanged.
             */
            directoryContents(fDir).stream()
                    .filter(file -> !avoid.contains(file.getName()))
                    .forEach(file -> updatedPotentialChanges.accept(file, Optional.empty()));

            if (zfile != null) {
                zfile.close();
            }
        }

        /*
         * Build the maps with the expected output changes.
         */
        Map<String, FileStatus> expectedOutputChanges = new HashMap<>();
        for (Map.Entry<String, Optional<FileStatus>> e : potentialChanges.entrySet()) {
            if (e.getValue().isPresent()) {
                expectedOutputChanges.put(e.getKey(), e.getValue().get());
            }
        }

        return Pair.of(changes, expectedOutputChanges);
    }

    @Test
    public void nameCheck() throws Exception {
        LazyIncrementalFileMergerInput input =
                LazyIncrementalFileMergerInputs.fromNew("foo", ImmutableSet.of());
        assertEquals("foo", input.getName());
    }

    @Test
    public void emptyFullInputs() throws Exception {
        Set<File> inputs = makeInputs(0);

        LazyIncrementalFileMergerInput input =
                LazyIncrementalFileMergerInputs.fromNew("foo", ImmutableSet.copyOf(inputs));
        assertEquals(0, input.getAllPaths().size());
        assertEquals(0, input.getUpdatedPaths().size());
    }

    @Test
    public void nonEmptyFullInputs() throws Exception {
        Set<File> inputs = makeInputs(50);
        LazyIncrementalFileMergerInput input =
                LazyIncrementalFileMergerInputs.fromNew("foo", ImmutableSet.copyOf(inputs));
        assertEquals(50, input.getAllPaths().size());
        assertEquals(50, input.getUpdatedPaths().size());
    }

    @Test
    public void incrementalInputs() throws Exception {
        FileCacheByPath cache = new FileCacheByPath(temporaryFolder.newFolder());
        Set<File> inputs = makeInputs(50);

        for (File f : inputs) {
            if (f.isFile()) {
                cache.add(f);
            }
        }

        /*
         * Do several incremental changes and check the number of reported files is correct.
         */
        for (int i = 0; i < 20; i++) {
            Pair<Map<File, FileStatus>, Map<String, FileStatus>> expected = makeChanges(inputs);
            LazyIncrementalFileMergerInput input =
                    LazyIncrementalFileMergerInputs.fromUpdates(
                            "foo",
                            ImmutableSet.copyOf(inputs),
                            expected.getFirst(),
                            cache,
                            IncrementalRelativeFileSets.FileDeletionPolicy
                                    .ASSUME_NO_DELETED_DIRECTORIES);

            assertEquals(expected.getSecond().size(), input.getUpdatedPaths().size());

            int fcount = 0;
            for (File f : inputs) {
                File dir = inputDirectory(f);
                fcount += directoryContents(dir).size();
            }

            assertEquals(fcount, input.getAllPaths().size());

            for (File f : inputs) {
                if (f.isFile()) {
                    cache.add(f);
                }
            }
        }
    }

    @Test
    public void findFileInNonIncrementalLoad() throws Exception {
        Set<File> inputs = makeInputs(50);
        LazyIncrementalFileMergerInput input =
                LazyIncrementalFileMergerInputs.fromNew("foo", ImmutableSet.copyOf(inputs));

        for (File in : inputs) {
            File inDir = inputDirectory(in);
            File[] files = inDir.listFiles();
            assertNotNull(files);

            for (File f : files) {
                assertEquals(FileStatus.NEW, input.getFileStatus(f.getName()));
                assertNull(input.getFileStatus(f.getName() + "/impossible"));
            }
        }
    }

    @Test
    public void cannotHaveSameRelativePathInDifferentBaseFiles() throws Exception {
        assume().that(baseDirectoryCount).isAtLeast(2);

        List<File> baseDirs = new ArrayList<>();
        for (int i = 0; i < baseDirectoryCount; i++) {
            File dir = temporaryFolder.newFolder();

            if (zipInputs) {
                File zfile = new File(dir, "z.zip");
                try (ZFile zf = ZFile.openReadWrite(zfile)) {
                    zf.add("foo", new ByteArrayInputStream(new byte[] { 1, 2 }));
                }

                baseDirs.add(zfile);
            } else {
                Files.write(new byte[] { 1, 2 }, new File(dir, "foo"));
                baseDirs.add(dir);
            }
        }

        try {
            LazyIncrementalFileMergerInput input =
                    LazyIncrementalFileMergerInputs.fromNew("foo", ImmutableSet.copyOf(baseDirs));
            input.getAllPaths();
            input.getUpdatedPaths();
            fail();
        } catch (DuplicatePathInIncrementalInputException e) {
            /*
             * Expected.
             */
        }
    }

    @Test
    public void openFile() throws Exception {
        Set<File> inputs = makeInputs(50);

        LazyIncrementalFileMergerInput input =
                LazyIncrementalFileMergerInputs.fromNew("foo", ImmutableSet.copyOf(inputs));

        input.open();

        for (String p : input.getAllPaths()) {
            try (InputStream is = input.openPath(p)) {
                // All files we generate have 10 bytes...
                assertEquals(10, is.read(new byte[100]));
            }
        }

        input.close();
    }
}
