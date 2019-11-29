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

package com.android.build.gradle.internal.dependency;

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask;
import com.android.ide.common.symbols.SymbolIo;
import com.android.ide.common.symbols.SymbolTable;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.artifacts.transform.ArtifactTransform;

/**
 * Transform that extracts the package name from the manifest and combines it with the r.txt symbol
 * table.
 *
 * <p>This means that one artifact contains all the information needed to build a {@link
 * SymbolTable} for {@link LinkApplicationAndroidResourcesTask}
 */
public class LibrarySymbolTableTransform extends ArtifactTransform {

    @Inject
    public LibrarySymbolTableTransform() {}

    @Override
    public List<File> transform(File explodedAar) {
        try {
            Path result = transform(explodedAar.toPath(), getOutputDirectory().toPath());
            return result != null ? ImmutableList.of(result.toFile()) : ImmutableList.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Nullable
    private static Path transform(@NonNull Path explodedAar, @NonNull Path outputDirectory)
            throws IOException {
        Path manifest = explodedAar.resolve(FN_ANDROID_MANIFEST_XML);
        if (!Files.exists(manifest)) {
            return null;
        }
        // May not exist in some AARs. e.g. the multidex support library.
        Path rTxt = explodedAar.resolve(FN_RESOURCE_TEXT);
        Files.createDirectories(outputDirectory);
        Path outputFile = outputDirectory.resolve("package-aware-r.txt");
        SymbolIo.writeSymbolListWithPackageName(rTxt, manifest, outputFile);
        return outputFile;
    }
}
