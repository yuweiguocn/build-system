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

package com.android.build.gradle.internal.transforms;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.builder.packaging.JarMerger;
import com.android.builder.packaging.TypedefRemover;
import com.android.builder.utils.ZipEntryUtils;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Closer;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * A Transforms that takes the project/project local streams for CLASSES and RESOURCES, and
 * processes and combines them, and put them in the bundle folder.
 *
 * <p>This creates a main jar with the classes from the main scope, and all the java resources, and
 * a set of local jars (which are mostly the same as before except without the resources). This is
 * used to package the AAR.
 *
 * <p>Regarding Streams, this is a no-op transform as it does not write any output to any stream. It
 * uses secondary outputs to write directly into the bundle folder.
 */
public class LibraryAarJarsTransform extends Transform {

    @NonNull protected final File mainClassLocation;
    @Nullable protected final File localJarsLocation;
    @Nullable private String packagePath;
    @NonNull private final Supplier<String> packageNameSupplier;
    protected final boolean packageBuildConfig;
    @Nullable protected final BuildableArtifact typedefRecipe;

    @Nullable protected Supplier<List<String>> excludeListProvider = null;

    public LibraryAarJarsTransform(
            @NonNull File mainClassLocation,
            @NonNull File localJarsLocation,
            @Nullable BuildableArtifact typedefRecipe,
            @NonNull Supplier<String> packageNameSupplier,
            boolean packageBuildConfig) {
        this.mainClassLocation = mainClassLocation;
        this.localJarsLocation = localJarsLocation;
        this.typedefRecipe = typedefRecipe;
        this.packageNameSupplier = packageNameSupplier;
        this.packageBuildConfig = packageBuildConfig;
    }

    @NonNull
    @Override
    public String getName() {
        return "syncLibJars";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_JARS;
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        return TransformManager.EMPTY_SCOPES;
    }

    @NonNull
    @Override
    public Set<? super Scope> getReferencedScopes() {
        return TransformManager.SCOPE_FULL_LIBRARY_WITH_LOCAL_JARS;
    }

    @Override
    public boolean isIncremental() {
        // TODO make incremental
        return false;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileOutputs() {
        return ImmutableList.of(mainClassLocation);
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        if (typedefRecipe != null) {
            return ImmutableList.of(SecondaryFile.nonIncremental(typedefRecipe));
        } else {
            return ImmutableList.of();
        }
    }

    public void setExcludeListProvider(@NonNull Supplier<List<String>> provider) {
        excludeListProvider = provider;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
        if (localJarsLocation == null) {
            return ImmutableList.of();
        }
        return ImmutableList.of(localJarsLocation);
    }

    @Override
    public void transform(@NonNull TransformInvocation invocation)
            throws IOException, TransformException, InterruptedException {
        // non incremental transform, need to clear out outputs.
        // main class jar will get rewritten, just delete local jar folder content.
        if (localJarsLocation != null) {
            FileUtils.deleteDirectoryContents(localJarsLocation);
        }
        if (typedefRecipe != null && !Iterables.getOnlyElement(typedefRecipe).exists()) {
            throw new IllegalStateException("Type def recipe not found: " + typedefRecipe);
        }

        List<Pattern> patterns = computeExcludeList();

        // first look for what inputs we have. There shouldn't be that many inputs so it should
        // be quick and it'll allow us to minimize jar merging if we don't have to.
        List<QualifiedContent> mainScope = Lists.newArrayList();
        List<QualifiedContent> localJarScope = Lists.newArrayList();

        for (TransformInput input : invocation.getReferencedInputs()) {
            for (QualifiedContent qualifiedContent : Iterables.concat(
                    input.getJarInputs(), input.getDirectoryInputs())) {
                if (qualifiedContent.getScopes().contains(Scope.PROJECT)) {
                    // even if the scope contains both project + local jar, we treat this as main
                    // scope.
                    mainScope.add(qualifiedContent);
                } else {
                    localJarScope.add(qualifiedContent);
                }
            }
        }

        // process main scope.
        if (mainScope.isEmpty()) {
            throw new RuntimeException("Empty Main scope for " + getName());
        }

        mergeInputsToLocation(
                mainScope,
                mainClassLocation,
                archivePath -> checkEntry(patterns, archivePath),
                typedefRecipe != null
                        ? new TypedefRemover()
                                .setTypedefFile(Iterables.getOnlyElement(typedefRecipe))
                        : null);

        // process local scope
        FileUtils.deleteDirectoryContents(localJarsLocation);
        processLocalJars(localJarScope);
    }

    @NonNull
    protected List<Pattern> computeExcludeList() {
        if (packagePath == null) {
            packagePath = packageNameSupplier.get().replace(".", "/");
        }
        List<String> excludes = getDefaultExcludes(packagePath, packageBuildConfig);
        if (excludeListProvider != null) {
            excludes.addAll(excludeListProvider.get());
        }

        // create Pattern Objects.
        return excludes.stream().map(Pattern::compile).collect(Collectors.toList());
    }

    @NonNull
    public static List<String> getDefaultExcludes(
            @NonNull String packagePath, boolean packageBuildConfig) {
        List<String> excludes = Lists.newArrayListWithExpectedSize(5);

        // these must be regexp to match the zip entries
        excludes.add(".*/R.class$");
        excludes.add(".*/R\\$(.*).class$");
        excludes.add(packagePath + "/Manifest.class$");
        excludes.add(packagePath + "/Manifest\\$(.*).class$");
        if (!packageBuildConfig) {
            excludes.add(packagePath + "/BuildConfig.class$");
        }
        return excludes;
    }

    protected void processLocalJars(@NonNull List<QualifiedContent> qualifiedContentList)
            throws IOException {

        // first copy the jars (almost) as is, and remove them from the list.
        // then we'll make a single jars that contains all the folders (though it's unlikely to
        // happen)
        // Note that we do need to remove the resources from the jars since they have been merged
        // somewhere else.
        // TODO: maybe do the folders separately to handle incremental?

        Iterator<QualifiedContent> iterator = qualifiedContentList.iterator();

        while (iterator.hasNext()) {
            QualifiedContent content = iterator.next();
            if (content instanceof JarInput) {
                // we need to copy the jars but only take the class files as the resources have
                // been merged into the main jar.
                copyJarWithContentFilter(
                        content.getFile(),
                        new File(localJarsLocation, content.getFile().getName()),
                        JarMerger.CLASSES_ONLY);
                iterator.remove();
            }
        }

        // now handle the folders.
        if (!qualifiedContentList.isEmpty()) {
            try (JarMerger jarMerger =
                    new JarMerger(
                            new File(localJarsLocation, "otherclasses.jar").toPath(),
                            JarMerger.CLASSES_ONLY)) {
                for (QualifiedContent content : qualifiedContentList) {
                    jarMerger.addDirectory(content.getFile().toPath());
                }
            }
        }
    }

    protected static void copyJarWithContentFilter(
            @NonNull File from, @NonNull File to, @Nullable Predicate<String> filter)
            throws IOException {
        byte[] buffer = new byte[4096];

        try (Closer closer = Closer.create()) {
            FileOutputStream fos = closer.register(new FileOutputStream(to));
            BufferedOutputStream bos = closer.register(new BufferedOutputStream(fos));
            ZipOutputStream zos = closer.register(new ZipOutputStream(bos));

            FileInputStream fis = closer.register(new FileInputStream(from));
            BufferedInputStream bis = closer.register(new BufferedInputStream(fis));
            ZipInputStream zis = closer.register(new ZipInputStream(bis));

            // loop on the entries of the intermediary package and put them in the final package.
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                if (entry.isDirectory() || (filter != null && !filter.test(name))) {
                    continue;
                }

                JarEntry newEntry;

                // Preserve the STORED method of the input entry.
                if (entry.getMethod() == JarEntry.STORED) {
                    newEntry = new JarEntry(entry);
                } else {
                    // Create a new entry so that the compressed len is recomputed.
                    newEntry = new JarEntry(name);
                }

                if (!ZipEntryUtils.isValidZipEntryName(newEntry)) {
                    throw new InvalidPathException(
                            newEntry.getName(), "Entry name contains invalid characters");
                }

                newEntry.setLastModifiedTime(JarMerger.ZERO_TIME);
                newEntry.setLastAccessTime(JarMerger.ZERO_TIME);
                newEntry.setCreationTime(JarMerger.ZERO_TIME);

                // add the entry to the jar archive
                zos.putNextEntry(newEntry);

                // read the content of the entry from the input stream, and write it into the
                // archive.
                int count;
                while ((count = zis.read(buffer)) != -1) {
                    zos.write(buffer, 0, count);
                }

                zos.closeEntry();
                zis.closeEntry();
            }
        }
    }

    protected static boolean checkEntry(@NonNull List<Pattern> patterns, @NonNull String name) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(name).matches()) {
                return false;
            }
        }
        return true;
    }

    protected static void mergeInputsToLocation(
            @NonNull List<QualifiedContent> qualifiedContentList,
            @NonNull File toFile,
            @NonNull final Predicate<String> filter,
            @Nullable final JarMerger.Transformer typedefRemover)
            throws IOException {
        Predicate<String> filterAndOnlyClasses = JarMerger.CLASSES_ONLY.and(filter);

        try (JarMerger jarMerger = new JarMerger(toFile.toPath())) {
            for (QualifiedContent content : qualifiedContentList) {
                // merge only class files if RESOURCES are not in the scope
                boolean hasResources =
                        content.getContentTypes()
                                .contains(QualifiedContent.DefaultContentType.RESOURCES);
                Predicate<String> thisFilter = hasResources ? filter : filterAndOnlyClasses;
                if (content instanceof JarInput) {
                    jarMerger.addJar(content.getFile().toPath(), thisFilter, null);
                } else {
                    jarMerger.addDirectory(
                            content.getFile().toPath(), thisFilter, typedefRemover, null);
                }
            }
        }
    }
}
