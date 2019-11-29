/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.utils.FileUtils.mkdirs;

import com.android.annotations.NonNull;
import com.android.builder.testing.MockableJarGenerator;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.artifacts.transform.ArtifactTransform;

/** Transform that converts an Android JAR file into a Mockable Android JAR file. */
public class MockableJarTransform extends ArtifactTransform {
    private final boolean returnDefaultValues;

    @Inject
    public MockableJarTransform(boolean returnDefaultValues) {
        this.returnDefaultValues = returnDefaultValues;
    }

    @Override
    @NonNull
    public List<File> transform(@NonNull File input) {
        File outputDir = getOutputDirectory();

        mkdirs(outputDir);
        File outputFile = new File(outputDir, input.getName());
        System.out.println(
                "Calling mockable JAR artifact transform to create file: "
                        + outputFile.getAbsolutePath()
                        + " with input "
                        + input.getAbsolutePath());

        MockableJarGenerator generator = new MockableJarGenerator(returnDefaultValues);
        try {
            generator.createMockableJar(input, outputFile);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create mockable android.jar", e);
        }

        return ImmutableList.of(outputFile);
    }
}
