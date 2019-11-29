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

package com.android.builder.dexing.r8;

import static com.google.common.truth.Truth.assertThat;

import com.android.builder.dexing.DexArchiveTestUtil;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.origin.PathOrigin;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ClassFileProviderFactoryTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testResourceCachingAndOrdering() throws Exception {
        int nbJars = 10;
        List<Path> classpathEntries = Lists.newArrayListWithExpectedSize(nbJars);

        Collection<String> classes = Arrays.asList("A", "B", "C");
        for (int i = 0; i < nbJars; i++) {
            classpathEntries.add(temporaryFolder.getRoot().toPath().resolve("input" + i + ".jar"));
            if (i < nbJars - 1) {
                DexArchiveTestUtil.createClasses(classpathEntries.get(i), classes);
            } else {
                DexArchiveTestUtil.createClasses(classpathEntries.get(i), ImmutableList.of("D"));
            }
        }

        try (ClassFileProviderFactory factory = new ClassFileProviderFactory(classpathEntries)) {
            assertThat(factory.getOrderedProvider().getClassDescriptors())
                    .hasSize(classes.size() + 1);

            for (String klass : DexArchiveTestUtil.getTestClassesDescriptors(classes)) {
                String desc = "L" + klass + ";";
                ProgramResource fst = factory.getOrderedProvider().getProgramResource(desc);
                ProgramResource snd = factory.getOrderedProvider().getProgramResource(desc);
                assertThat(fst).isSameAs(snd);

                // assert all are loaded from the first jar
                PathOrigin pathOrigin = (PathOrigin) fst.getOrigin().parent();
                assertThat(pathOrigin.getPath().getFileName().toString()).isEqualTo("input0.jar");
            }

            ProgramResource programResource =
                    factory.getOrderedProvider().getProgramResource("Ltest/D;");
            assertThat(programResource).isNotNull();
            PathOrigin parent = (PathOrigin) programResource.getOrigin().parent();
            assertThat(parent.getPath().getFileName().toString())
                    .isEqualTo("input" + (nbJars - 1) + ".jar");
        }
    }
}
