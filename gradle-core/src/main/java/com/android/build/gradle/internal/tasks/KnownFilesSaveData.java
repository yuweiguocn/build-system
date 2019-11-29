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

package com.android.build.gradle.internal.tasks;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.builder.files.FileCacheByPath;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.builder.files.RelativeFile;
import com.android.ide.common.resources.FileStatus;
import com.android.tools.build.apkzlib.utils.CachedFileContents;
import com.google.common.base.Functions;
import com.google.common.base.Predicates;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Closer;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

/**
 * Class that keeps track of which files are known in incremental builds. Gradle tells us which
 * files were modified, but doesn't tell us which inputs the files come from so when a file is
 * marked as deleted, we don't know which input set it was deleted from. This class maintains the
 * list of files and their source locations and can be saved to the intermediate directory.
 *
 * <p>File data is loaded on creation and saved on close.
 *
 * <p><i>Implementation note:</i> the actual data is saved in a property file with the file name
 * mapped to the name of the {@link InputSet} enum defining its input set.
 */
public class KnownFilesSaveData {

    /** Name of the file with the save data. */
    private static final String SAVE_DATA_FILE_NAME = "file-input-save-data.txt";

    /** Property with the number of files in the property file. */
    private static final String COUNT_PROPERTY = "count";

    /** Suffix for property with the base file. */
    private static final String BASE_SUFFIX = ".base";

    /** Suffix for property with the relative path. */
    private static final String RELATIVE_PATH_SUFFIX = ".path";

    /** Suffix for property with the input set. */
    private static final String INPUT_SET_SUFFIX = ".set";

    /** Suffix for property with the type of the base file. */
    private static final String BASE_TYPE_SUFFIX = ".baseType";

    /** Cache with all known cached files. */
    private static final Map<File, CachedFileContents<KnownFilesSaveData>> mCache =
            Maps.newHashMap();

    /** File contents cache. */
    @NonNull private final CachedFileContents<KnownFilesSaveData> mFileContentsCache;

    /** Maps all files in the last build to their input set. */
    @NonNull private final Map<RelativeFile, InputSet> mFiles;

    /** Has the data been modified? */
    private boolean mDirty;

    /**
     * Creates a new file save data and reads it if it exists. To create new instances, the factory
     * method {@link #make(File)} should be used.
     *
     * @param cache the cache used
     * @throws IOException failed to read the file (not thrown if the file does not exist)
     */
    @VisibleForTesting
    KnownFilesSaveData(@NonNull CachedFileContents<KnownFilesSaveData> cache)
            throws IOException {
        mFileContentsCache = cache;
        mFiles = Maps.newHashMap();
        if (cache.getFile().isFile()) {
            readCurrentData();
        }

        mDirty = false;
    }

    /**
     * Creates a new {@link KnownFilesSaveData}, or obtains one from cache if there already exists a
     * cached entry.
     *
     * @param intermediateDir the intermediate directory where the cache is stored
     * @return the save data
     * @throws IOException save data file exists but there was an error reading it (not thrown if
     *     the file does not exist)
     */
    @NonNull
    public static synchronized KnownFilesSaveData make(@NonNull File intermediateDir)
            throws IOException {
        File saveFile = computeSaveFile(intermediateDir);
        CachedFileContents<KnownFilesSaveData> cached = mCache.get(saveFile);
        if (cached == null) {
            cached = new CachedFileContents<>(saveFile);
            mCache.put(saveFile, cached);
        }

        KnownFilesSaveData saveData = cached.getCache();
        if (saveData == null) {
            saveData = new KnownFilesSaveData(cached);
            cached.closed(saveData);
        }

        return saveData;
    }

    /**
     * Computes what is the save file for the provided intermediate directory.
     *
     * @param intermediateDir the intermediate directory
     * @return the file
     */
    private static File computeSaveFile(@NonNull File intermediateDir) {
        return new File(intermediateDir, SAVE_DATA_FILE_NAME);
    }

    /**
     * Reads the save file data into the in-memory data structures.
     *
     * @throws IOException failed to read the file
     */
    @VisibleForTesting
    void readCurrentData() throws IOException {
        Closer closer = Closer.create();

        File saveFile = mFileContentsCache.getFile();

        Properties properties = new Properties();
        try {
            Reader saveDataReader = closer.register(new FileReader(saveFile));
            properties.load(saveDataReader);
        } catch (Throwable t) {
            throw closer.rethrow(t);
        } finally {
            closer.close();
        }

        String fileCountText = null;
        int fileCount;
        try {
            fileCountText = properties.getProperty(COUNT_PROPERTY);
            if (fileCountText == null) {
                throw new IOException(
                        "Invalid data stored in file '"
                                + saveFile
                                + "' ("
                                + "property '"
                                + COUNT_PROPERTY
                                + "' has no value).");
            }

            fileCount = Integer.parseInt(fileCountText);
            if (fileCount < 0) {
                throw new IOException(
                        "Invalid data stored in file '"
                                + saveFile
                                + "' ("
                                + "property '"
                                + COUNT_PROPERTY
                                + "' has value "
                                + fileCount
                                + ").");
            }
        } catch (NumberFormatException e) {
            throw new IOException(
                    "Invalid data stored in file '"
                            + saveFile
                            + "' ("
                            + "property '"
                            + COUNT_PROPERTY
                            + "' has value '"
                            + fileCountText
                            + "').",
                    e);
        }

        for (int i = 0; i < fileCount; i++) {
            String baseName = properties.getProperty(i + BASE_SUFFIX);
            if (baseName == null) {
                throw new IOException(
                        "Invalid data stored in file '"
                                + saveFile
                                + "' ("
                                + "property '"
                                + i
                                + BASE_SUFFIX
                                + "' has no value).");
            }

            String relativePath = properties.getProperty(i + RELATIVE_PATH_SUFFIX);
            if (relativePath == null) {
                throw new IOException(
                        "Invalid data stored in file '"
                                + saveFile
                                + "' ("
                                + "property '"
                                + i
                                + RELATIVE_PATH_SUFFIX
                                + "' has no value).");
            }

            String inputSetName = properties.getProperty(i + INPUT_SET_SUFFIX);
            if (inputSetName == null) {
                throw new IOException(
                        "Invalid data stored in file '"
                                + saveFile
                                + "' ("
                                + "property '"
                                + i
                                + INPUT_SET_SUFFIX
                                + "' has no value).");
            }

            String baseTypeString = properties.getProperty(i + BASE_TYPE_SUFFIX);
            if (baseTypeString == null) {
                throw new IOException(
                        "Invalid data stored in file '"
                                + saveFile
                                + "' ("
                                + "property '"
                                + i
                                + BASE_TYPE_SUFFIX
                                + "' has no value).");
            }
            RelativeFile.Type baseType;
            try {
                baseType = RelativeFile.Type.valueOf(baseTypeString);
            } catch (IllegalArgumentException e) {
                throw new IOException(
                        "Invalid data stored in file '"
                                + saveFile
                                + "' ("
                                + "property '"
                                + BASE_TYPE_SUFFIX
                                + "' has value '"
                                + baseTypeString
                                + "').",
                        e);
            }

            InputSet is;
            try {
                is = InputSet.valueOf(InputSet.class, inputSetName);
            } catch (IllegalArgumentException e) {
                throw new IOException(
                        "Invalid data stored in file '"
                                + saveFile
                                + "' ("
                                + "property '"
                                + i
                                + INPUT_SET_SUFFIX
                                + "' has invalid value '"
                                + inputSetName
                                + "').");
            }

            mFiles.put(new RelativeFile(new File(baseName), relativePath, baseType), is);
        }
    }

    /**
     * Saves current in-memory data structures to file.
     *
     * @throws IOException failed to save the data
     */
    public void saveCurrentData() throws IOException {
        if (!mDirty) {
            return;
        }

        Closer closer = Closer.create();

        Properties properties = new Properties();
        properties.put(COUNT_PROPERTY, Integer.toString(mFiles.size()));
        int idx = 0;
        for (Map.Entry<RelativeFile, InputSet> e : mFiles.entrySet()) {
            RelativeFile rf = e.getKey();

            String basePath = Verify.verifyNotNull(rf.getBase().getPath());
            Verify.verify(!basePath.isEmpty());

            String relativePath = Verify.verifyNotNull(rf.getRelativePath());
            Verify.verify(!relativePath.isEmpty());

            properties.put(idx + BASE_SUFFIX, basePath);
            properties.put(idx + RELATIVE_PATH_SUFFIX, relativePath);
            properties.put(idx + INPUT_SET_SUFFIX, e.getValue().name());
            properties.put(idx + BASE_TYPE_SUFFIX, rf.getType().name());

            idx++;
        }

        try {
            Writer saveDataWriter = closer.register(new FileWriter(mFileContentsCache.getFile()));
            properties.store(saveDataWriter, "Internal package file, do not edit.");
            mFileContentsCache.closed(this);
        } catch (Throwable t) {
            throw closer.rethrow(t);
        } finally {
            closer.close();
        }
    }

    /**
     * Obtains all relative files stored in the save data that have the provided input set and whose
     * files are included in the provided set of files. This method allows retrieving the original
     * relative files from the files, while filtering for the desired input set.
     *
     * @param files the files to filter
     * @param inputSet the input set to filter
     * @return all saved relative files that have the given input set and whose files exist in the
     *     provided set
     */
    @NonNull
    public ImmutableSet<RelativeFile> find(@NonNull Set<File> files, @NonNull InputSet inputSet) {
        Set<RelativeFile> found = Sets.newHashSet();
        for (RelativeFile rf : Maps.filterValues(mFiles, Predicates.equalTo(inputSet)).keySet()) {
            if (files.contains(new File(rf.getBase(), rf.getRelativePath()))) {
                found.add(rf);
            }
        }

        return ImmutableSet.copyOf(found);
    }

    /**
     * Obtains a predicate that checks if a file is in an input set.
     *
     * @param inputSet the input set
     * @return the predicate
     */
    @NonNull
    private Function<File, RelativeFile> inInputSet(@NonNull InputSet inputSet) {
        Map<File, RelativeFile> inverseFiltered =
                mFiles.entrySet()
                        .stream()
                        .filter(e -> e.getValue() == inputSet)
                        .map(Map.Entry::getKey)
                        .collect(
                                HashMap::new,
                                (m, rf) -> m.put(new File(rf.getBase(), rf.getRelativePath()), rf),
                                Map::putAll);

        return inverseFiltered::get;
    }

    /**
     * Sets all files in an input set, replacing whatever existed previously.
     *
     * @param files the files
     * @param set the input set
     */
    public void setInputSet(@NonNull Collection<RelativeFile> files, @NonNull InputSet set) {
        for (Iterator<Map.Entry<RelativeFile, InputSet>> it = mFiles.entrySet().iterator();
                it.hasNext();
                ) {
            Map.Entry<RelativeFile, InputSet> next = it.next();
            if (next.getValue() == set && !files.contains(next.getKey())) {
                it.remove();
                mDirty = true;
            }
        }

        files.forEach(
                f -> {
                    if ((!mFiles.containsKey(f)) && f.getType() == RelativeFile.Type.DIRECTORY) {
                        mFiles.put(f, set);
                        mDirty = true;
                    }
                });
    }

    @VisibleForTesting
    boolean isDirty() {
        return mDirty;
    }

    @VisibleForTesting
    Map<RelativeFile, InputSet> getFiles() {
        return mFiles;
    }

    /**
     * Obtains all changed inputs of a given input set. Given a set of files mapped to their changed
     * status, this method returns a list of changes computed as follows:
     *
     * <ol>
     *   <li>Changed inputs are split into deleted and non-deleted inputs. This separation is needed
     *       because deleted inputs may no longer be mappable to any {@link InputSet} just by
     *       looking at the file path, without using {@link KnownFilesSaveData}.
     *   <li>Deleted inputs are filtered through {@link KnownFilesSaveData} to get only those whose
     *       input set matches {@code inputSet}.
     *   <li>Non-deleted inputs are processed through {@link
     *       IncrementalRelativeFileSets#makeFromBaseFiles(Collection, Map, FileCacheByPath, Set,
     *       IncrementalRelativeFileSets.FileDeletionPolicy)} boolean)} to obtain the incremental
     *       file changes.
     *   <li>The results of processed deleted and non-deleted are merged and returned.
     * </ol>
     *
     * @param changedInputs all changed inputs
     * @param saveData the save data with all input sets from last run
     * @param inputSet the input set to filter
     * @param baseFiles the base files of the input set
     * @param cacheByPath where to cache files
     * @param cacheUpdates receives the runnables that will update the cache
     * @return the status of all relative files in the input set
     */
    @NonNull
    public static ImmutableMap<RelativeFile, FileStatus> getChangedInputs(
            @NonNull Map<File, FileStatus> changedInputs,
            @NonNull KnownFilesSaveData saveData,
            @NonNull InputSet inputSet,
            @NonNull Collection<File> baseFiles,
            @NonNull FileCacheByPath cacheByPath,
            @NonNull Set<Runnable> cacheUpdates)
            throws IOException {

        /*
         * Figure out changes to deleted files.
         */
        Set<File> deletedFiles =
                Maps.filterValues(changedInputs, Predicates.equalTo(FileStatus.REMOVED)).keySet();
        Set<RelativeFile> deletedRelativeFiles = saveData.find(deletedFiles, inputSet);

        /*
         * Figure out changes to non-deleted files.
         */
        Map<File, FileStatus> nonDeletedFiles =
                Maps.filterValues(
                        changedInputs, Predicates.not(Predicates.equalTo(FileStatus.REMOVED)));
        Map<RelativeFile, FileStatus> nonDeletedRelativeFiles =
                IncrementalRelativeFileSets.makeFromBaseFiles(
                        baseFiles,
                        nonDeletedFiles,
                        cacheByPath,
                        cacheUpdates,
                        IncrementalRelativeFileSets.FileDeletionPolicy.DISALLOW_FILE_DELETIONS);

        /*
         * Merge everything.
         */
        return new ImmutableMap.Builder<RelativeFile, FileStatus>()
                .putAll(Maps.asMap(deletedRelativeFiles, Functions.constant(FileStatus.REMOVED)))
                .putAll(nonDeletedRelativeFiles)
                .build();
    }

    /** Input sets for files for save data (see {@link KnownFilesSaveData}). */
    public enum InputSet {
        /** File belongs to the dex file set. */
        DEX,

        /** File belongs to the java resources file set. */
        JAVA_RESOURCE,

        /** File belongs to the native resources file set. */
        NATIVE_RESOURCE,

        /** File belongs to the android resources file set. */
        ANDROID_RESOURCE,

        /** File belongs to the assets file set. */
        ASSET
    }
}
