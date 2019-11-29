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

package com.android.builder.packaging;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.PathUtils;
import com.google.common.collect.ImmutableSortedMap;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Jar Merger class. */
public class JarMerger implements Closeable {

    public static final Predicate<String> CLASSES_ONLY =
            archivePath -> archivePath.endsWith(SdkConstants.DOT_CLASS);
    public static final Predicate<String> EXCLUDE_CLASSES =
            archivePath -> !archivePath.endsWith(SdkConstants.DOT_CLASS);

    public static final String MODULE_PATH = "module-path";

    public interface Transformer {
        /**
         * Transforms the given file.
         *
         * @param entryPath the path within the jar file
         * @param input an input stream of the contents of the file
         * @return a new input stream if the file is transformed in some way, the same input stream
         *     if the file is to be kept as is and null if the file should not be packaged.
         */
        @Nullable
        InputStream filter(@NonNull String entryPath, @NonNull InputStream input);
    }

    public interface Relocator {
        @NonNull
        String relocate(@NonNull String entryPath);
    }

    public static final FileTime ZERO_TIME = FileTime.fromMillis(0);

    private final byte[] buffer = new byte[8192];

    @NonNull private final JarOutputStream jarOutputStream;

    @Nullable private final Predicate<String> filter;

    public JarMerger(@NonNull Path jarFile) throws IOException {
        this(jarFile, null);
    }

    public JarMerger(@NonNull Path jarFile, @Nullable Predicate<String> filter) throws IOException {
        this.filter = filter;
        Files.createDirectories(jarFile.getParent());
        jarOutputStream =
                new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(jarFile)));
    }

    public void addDirectory(@NonNull Path directory) throws IOException {
        addDirectory(directory, filter, null, null);
    }

    public void addDirectory(
            @NonNull Path directory,
            @Nullable Predicate<String> filterOverride,
            @Nullable Transformer transformer,
            @Nullable Relocator relocator)
            throws IOException {
        ImmutableSortedMap.Builder<String, Path> candidateFiles = ImmutableSortedMap.naturalOrder();
        Files.walkFileTree(
                directory,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String entryPath =
                                PathUtils.toSystemIndependentPath(directory.relativize(file));
                        if (filterOverride != null && !filterOverride.test(entryPath)) {
                            return FileVisitResult.CONTINUE;
                        }

                        if (relocator != null) {
                            entryPath = relocator.relocate(entryPath);
                        }

                        candidateFiles.put(entryPath, file);
                        return FileVisitResult.CONTINUE;
                    }
                });
        ImmutableSortedMap<String, Path> sortedFiles = candidateFiles.build();
        for (Map.Entry<String, Path> entry : sortedFiles.entrySet()) {
            String entryPath = entry.getKey();
            try (InputStream is = new BufferedInputStream(Files.newInputStream(entry.getValue()))) {
                if (transformer != null) {
                    @Nullable InputStream is2 = transformer.filter(entryPath, is);
                    if (is2 != null) {
                        write(new JarEntry(entryPath), is2);
                    }
                } else {
                    write(new JarEntry(entryPath), is);
                }
            }
        }
    }

    public void addJar(@NonNull Path file) throws IOException {
        addJar(file, filter, null);
    }

    public void addJar(
            @NonNull Path file,
            @Nullable Predicate<String> filterOverride,
            @Nullable Relocator relocator)
            throws IOException {
        try (ZipInputStream zis =
                new ZipInputStream(new BufferedInputStream(Files.newInputStream(file)))) {

            // loop on the entries of the jar file package and put them in the final jar
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // do not take directories
                if (entry.isDirectory()) {
                    continue;
                }

                // Filter out files, e.g. META-INF folder, not classes.
                String name = entry.getName();
                if (filterOverride != null && !filterOverride.test(name)) {
                    continue;
                }

                if (relocator != null) {
                    name = relocator.relocate(name);
                }

                if (name.contains("../")) {
                    throw new InvalidPathException(name, "Entry name contains invalid characters");
                }
                JarEntry newEntry = new JarEntry(name);
                newEntry.setMethod(entry.getMethod());
                if (newEntry.getMethod() == ZipEntry.STORED) {
                    newEntry.setSize(entry.getSize());
                    newEntry.setCompressedSize(entry.getCompressedSize());
                    newEntry.setCrc(entry.getCrc());
                }
                newEntry.setLastModifiedTime(FileTime.fromMillis(0));

                // read the content of the entry from the input stream, and write it into the
                // archive.
                write(newEntry, zis);
            }
        }
    }

    public void addFile(@NonNull String entryPath, @NonNull Path file) throws IOException {
        try (InputStream is = new BufferedInputStream(Files.newInputStream(file))) {
            write(new JarEntry(entryPath), is);
        }
    }

    public void addEntry(@NonNull String entryPath, @NonNull InputStream input) throws IOException {
        try (InputStream is = new BufferedInputStream(input)) {
            write(new JarEntry(entryPath), is);
        }
    }

    @Override
    public void close() throws IOException {
        jarOutputStream.close();
    }

    public void setManifestProperties(Map<String, String> properties) throws IOException {
        Manifest manifest = new Manifest();
        Attributes global = manifest.getMainAttributes();
        global.put(Attributes.Name.MANIFEST_VERSION, "1.0.0");
        properties.forEach(
                (attributeName, attributeValue) ->
                        global.put(new Attributes.Name(attributeName), attributeValue));
        JarEntry manifestEntry = new JarEntry(JarFile.MANIFEST_NAME);
        setEntryAttributes(manifestEntry);
        jarOutputStream.putNextEntry(manifestEntry);
        try {
            manifest.write(jarOutputStream);
        } finally {
            jarOutputStream.closeEntry();
        }
    }

    private void write(@NonNull JarEntry entry, @NonNull InputStream from) throws IOException {
        setEntryAttributes(entry);
        jarOutputStream.putNextEntry(entry);
        int count;
        while ((count = from.read(buffer)) != -1) {
            jarOutputStream.write(buffer, 0, count);
        }
        jarOutputStream.closeEntry();
    }

    private void setEntryAttributes(@NonNull JarEntry entry) {
        entry.setLastModifiedTime(ZERO_TIME);
        entry.setLastAccessTime(ZERO_TIME);
        entry.setCreationTime(ZERO_TIME);
    }
}
