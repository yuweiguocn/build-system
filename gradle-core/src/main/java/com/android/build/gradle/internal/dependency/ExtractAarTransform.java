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

package com.android.build.gradle.internal.dependency;

import static com.android.SdkConstants.FD_JARS;
import static com.android.SdkConstants.FN_CLASSES_JAR;

import com.android.annotations.NonNull;
import com.android.builder.aar.AarExtractor;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.inject.Inject;
import org.gradle.api.artifacts.transform.ArtifactTransform;

/** Transform that extracts an AAR file into a directory. */
public class ExtractAarTransform extends ArtifactTransform {

    @Inject
    public ExtractAarTransform() {}

    @NonNull
    @Override
    public List<File> transform(@NonNull File input) {
        File outputDir = getOutputDirectory();
        FileUtils.mkdirs(outputDir);
        AarExtractor aarExtractor = new AarExtractor();
        aarExtractor.extract(input, outputDir);

        // Verify that we have a classes.jar, if we don't just create an empty one.
        File classesJar = new File(new File(outputDir, FD_JARS), FN_CLASSES_JAR);
        if (!classesJar.exists()) {
            try {
                Files.createParentDirs(classesJar);
                try (FileOutputStream out = new FileOutputStream(classesJar)) {
                    // FileOutputStream above is the actual OS resource that will get closed,
                    // JarOutputStream writes the bytes or an empty jar in it.
                    @SuppressWarnings("resource")
                    JarOutputStream jarOutputStream =
                            new JarOutputStream(new BufferedOutputStream(out), new Manifest());
                    jarOutputStream.close();
                }
            } catch (IOException e) {
                throw new RuntimeException("Cannot create missing classes.jar", e);
            }
        }

        return ImmutableList.of(outputDir);
    }
}
