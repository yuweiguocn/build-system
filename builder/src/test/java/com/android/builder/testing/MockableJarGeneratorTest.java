/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.builder.testing;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.testutils.TestResources;
import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MockableJarGeneratorTest {

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void testJarRewriting() throws Exception {
        MockableJarGenerator generator = new MockableJarGenerator(true);

        File inputJar =
                TestResources.getFile(
                        MockableJarGenerator.class, "/testData/testing/non-mockable.jar");
        File outputJar = new File(mTemporaryFolder.newFolder(),"mockable.jar");

        generator.createMockableJar(inputJar, outputJar);

        assertTrue(outputJar.exists());

        try (JarFile jarFile = new JarFile(outputJar)) {
            Set<String> names =
                    Collections.list(jarFile.entries())
                            .stream()
                            .map(JarEntry::getName)
                            .collect(Collectors.toSet());
            assertThat(names)
                    .containsExactly(
                            "META-INF/MANIFEST.MF",
                            "SomeResource",
                            "NonFinalClass.class",
                            "FinalClass.class");
        }
    }
}
