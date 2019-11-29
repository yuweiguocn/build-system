/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.builder.internal.packaging;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.files.NativeLibraryAbiPredicate;
import com.android.builder.files.RelativeFile;
import com.android.builder.packaging.PackagerException;
import com.android.ide.common.resources.FileStatus;
import com.android.tools.build.apkzlib.zfile.ApkCreator;
import com.android.tools.build.apkzlib.zfile.ApkCreatorFactory;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Closer;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Makes the final app package. The packager allows build an APK from:
 * <ul>
 *     <li>The package output from aapt;
 *     <li>Java resource files;
 *     <li>JNI libraries;
 *     <li>Dex files;
 * </ul>
 *
 * <p>The {@code IncrementalPackager} class can create an APK from scratch and can incrementally
 * build and APK. To work incrementally with the {@code IncrementalPackager} it is necessary to
 * provide information on which files were externally created, updated or deleted.
 *
 * <p>The {@code IncrementalPackager} allows working with archives (zip files). After an archive is
 * added to the package, the {@code IncrementalPackager} will keep a copy of the added (or updated)
 * archive to allow incremental updates. The semantics for working with archives are:
 *
 * <ul>
 *     <li>Adding an archive is equivalent to add all files in the archive;</li>
 *     <li>Updating an archive is equivalent to add all files that exist in the new version of the
 *     archive, remove all files that no longer exist in the new version in the archive and to
 *     update all files that have changed (the size and CRC checksum of the files in the archive
 *     can be used for fast detection;
 *     <li>Deleting an archive is equivalent to removing all files that exist in last updated
 *     version of the archive;
 * </ul>
 *
 * <p>File caches should be independent of the produced APKs; each produced APK has its
 * intermediate directory. This is required to avoid incorrect updates of incremental APKs. For
 * example, if archive file <i>A</i> used in both APKs <i>x</i> and <i>y</i>, updating <i>A</i>,
 * updating <i>x</i>, updating <i>A</i> again and then updating <i>y</i> would yield an incorrect
 * incremental update, as the difference between the last stored <i>A</i> and the new <i>A</i>
 * does not correctly reflect the changes need to apply to <i>y</i>.
 *
 * <p>{@code IncrementalPackager} places caches inside a provided <i>intermediate directory</i>.
 * {@code IncrementalPackager} provides two ways to ensure independent caches for different APKs.
 * The first is that the directory used for caches is a subdirectory of the provided intermediate
 * directory named after the APK. So, APKs with different names will always use different
 * caches. Secondly, if multiple APKs can exist with different names, then different intermediate
 * directories should be provided for each.
 */
public class IncrementalPackager implements Closeable {

    /**
     * APK creator. {@code null} if not open.
     */
    @Nullable
    private ApkCreator mApkCreator;

    /**
     * Class that manages the renaming of dex files.
     */
    @NonNull
    private final DexIncrementalRenameManager mDexRenamer;

    /**
     * Predicate to filter native libraries.
     */
    @NonNull
    private final NativeLibraryAbiPredicate mAbiPredicate;


    /**
     * Creates a new instance.
     *
     * <p>This creates a new builder that will create the specified output file.
     *
     * @param creationData APK creation data
     * @param intermediateDir a directory where to store intermediate files
     * @param factory the factory used to create APK creators
     * @param acceptedAbis the set of accepted ABIs; if empty then all ABIs are accepted
     * @param jniDebugMode is JNI debug mode enabled?
     * @throws PackagerException failed to create the initial APK
     * @throws IOException failed to create the APK
     */
    public IncrementalPackager(@NonNull ApkCreatorFactory.CreationData creationData,
            @NonNull File intermediateDir, @NonNull ApkCreatorFactory factory,
            @NonNull Set<String> acceptedAbis, boolean jniDebugMode)
            throws PackagerException, IOException {
        if (!intermediateDir.isDirectory()) {
            throw new IllegalArgumentException(
                    "!intermediateDir.isDirectory(): " + intermediateDir);
        }
        checkOutputFile(creationData.getApkPath());

        mApkCreator = factory.make(creationData);
        mDexRenamer = new DexIncrementalRenameManager(intermediateDir);
        mAbiPredicate = new NativeLibraryAbiPredicate(acceptedAbis, jniDebugMode);
    }

    /**
     * Updates the dex files in the archive.
     *
     * @param files the the dex files
     * @throws IOException failed to update the archive
     */
    public void updateDex(@NonNull ImmutableMap<RelativeFile, FileStatus> files)
            throws IOException {
        updateFiles(mDexRenamer.update(files));
    }

    /**
     * Updates files in the archive.
     *
     * @param updates the updates to perform
     * @throws IOException failed to update the archive
     */
    private void updateFiles(@NonNull Set<PackagedFileUpdate> updates) throws IOException {
        Preconditions.checkNotNull(mApkCreator, "mApkCreator == null");

        Iterable<String> deletedPaths =
                updates.stream()
                        .filter(p -> p.getStatus() == FileStatus.REMOVED)
                        .map(PackagedFileUpdate::getName)
                        .collect(Collectors.toList());

        for (String deletedPath : deletedPaths) {
            mApkCreator.deleteFile(deletedPath);
        }

        Predicate<PackagedFileUpdate> isNewOrChanged =
                pfu -> pfu.getStatus() == FileStatus.NEW || pfu.getStatus() == FileStatus.CHANGED;

        Iterable<PackagedFileUpdate> newOrChangedNonArchiveFiles =
                updates.stream()
                        .filter(
                                pfu ->
                                        pfu.getSource().getType() == RelativeFile.Type.DIRECTORY
                                                && isNewOrChanged.test(pfu))
                        .collect(Collectors.toList());

        for (PackagedFileUpdate rf : newOrChangedNonArchiveFiles) {
            File out = new File(rf.getSource().getBase(), rf.getSource().getRelativePath());
            mApkCreator.writeFile(out, rf.getName());
        }

        Iterable<PackagedFileUpdate> newOrChangedArchiveFiles =
                updates.stream()
                        .filter(
                                pfu ->
                                        pfu.getSource().getType() == RelativeFile.Type.JAR
                                                && isNewOrChanged.test(pfu))
                        .collect(Collectors.toList());

        Set<File> archives =
                StreamSupport.stream(newOrChangedArchiveFiles.spliterator(), false)
                        .map(pfu -> pfu.getSource().getBase())
                        .collect(Collectors.toSet());
        Set<String> names = Sets.newHashSet(
                Iterables.transform(
                        newOrChangedArchiveFiles,
                        PackagedFileUpdate::getName));

        /*
         * Build the name map. The name of the file in the filesystem (or zip file) may not
         * match the name we want to package it as. See PackagedFileUpdate for more information.
         */
        Map<String, String> pathNameMap = Maps.newHashMap();
        for (PackagedFileUpdate archiveUpdate : newOrChangedArchiveFiles) {
            pathNameMap.put(archiveUpdate.getSource().getRelativePath(), archiveUpdate.getName());
        }

        for (File arch : archives) {
            mApkCreator.writeZip(arch, pathNameMap::get, name -> !names.contains(name));
        }
    }

    /**
     * Updates java resources in the archive.
     *
     * <p>The implementation of the transform API means that some streams will contain classes and
     * resources intermingled. This is true for when proguard is used and because annotation
     * processors can generate resources. Therefore this method ignores resources ending with {@code
     * .class}.
     *
     * @param files the resources to update
     * @throws IOException failed to update the archive
     */
    public void updateJavaResources(@NonNull ImmutableMap<RelativeFile, FileStatus> files)
            throws IOException {
        updateFiles(
                PackagedFileUpdates.fromIncrementalRelativeFileSet(
                        Maps.filterKeys(
                                files,
                                rf -> !rf.getRelativePath().endsWith(SdkConstants.DOT_CLASS))));
    }

    /**
     * Updates assets in the archive.
     *
     * @param files the assets to update
     * @throws IOException failed to update the archive
     */
    public void updateAssets(@NonNull ImmutableMap<RelativeFile, FileStatus> files)
            throws IOException {
        updateFiles(
                PackagedFileUpdates.fromIncrementalRelativeFileSet(files).stream()
                        .map(pfu -> new PackagedFileUpdate(
                                pfu.getSource(),
                                "assets/" + pfu.getName(),
                                pfu.getStatus()))
                        .collect(Collectors.toSet()));
    }

    /**
     * Updates Android resources in the archive.
     *
     * @param files the resources to update
     * @throws IOException failed to update the archive
     */
    public void updateAndroidResources(@NonNull ImmutableMap<RelativeFile, FileStatus> files)
            throws IOException {
        updateFiles(PackagedFileUpdates.fromIncrementalRelativeFileSet(files));
    }

    /**
     * Updates native libraries in the archive.
     *
     * @param files the resources to update
     * @throws IOException failed to update the archive
     */
    public void updateNativeLibraries(@NonNull ImmutableMap<RelativeFile, FileStatus> files)
            throws IOException {
        updateFiles(
                PackagedFileUpdates.fromIncrementalRelativeFileSet(
                        Maps.filterKeys(files, rf -> mAbiPredicate.test(rf.getRelativePath()))));
    }

    /**
     * Checks that output path is a valid file. This will generally provide a friendler error
     * message if the file cannot be created.
     *
     * <p>It checks the following:
     * <ul>
     *     <li>The path is not an existing directory;
     *     <li>if the file exists, it is writeable;
     *     <li>if the file doesn't exists, that a new file can be created in its place
     * </ul>
     *
     * @param file the path to check
     * @throws IOException the check failed
     */
    private static void checkOutputFile(@NonNull File file) throws IOException {
        if (file.isDirectory()) {
            throw new IOException(String.format("'%s' is a directory", file.getAbsolutePath()));
        }

        if (file.exists()) { // will be a file in this case.
            if (!file.canWrite()) {
                throw new IOException(
                        String.format("'%s' is not writeable", file.getAbsolutePath()));
            }
        } else {
            try {
                if (!file.createNewFile()) {
                    throw new IOException(String.format("Failed to create '%s'",
                            file.getAbsolutePath()));
                }

                /*
                 * We succeeded at creating the file. Now, delete it because a zero-byte file is
                 * not a valid APK and some ApkCreator implementations (e.g., the ZFile one)
                 * complain if open on top of an invalid zip file.
                 */
                if (!file.delete()) {
                    throw new IOException(String.format("Failed to delete newly created '%s'",
                            file.getAbsolutePath()));
                }
            } catch (IOException e) {
                throw new IOException(String.format("Failed to create '%s'",
                        file.getAbsolutePath()), e);
            }
        }
    }

    public boolean hasPendingChangesWithWait() throws IOException {
        return mApkCreator != null && mApkCreator.hasPendingChangesWithWait();
    }

    @Override
    public void close() throws IOException {
        if (mApkCreator == null) {
            return;
        }

        try (Closer closer = Closer.create()) {
            closer.register(mApkCreator);
            closer.register(mDexRenamer);
            mApkCreator = null;
        }
    }
}
