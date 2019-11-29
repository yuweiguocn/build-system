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

package com.android.builder.dexing;

import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.dex.Dex;
import com.android.testutils.TestClassesGenerator;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterators;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link ReferenceCountMergingStrategy}. */
public class ReferenceCountMergingStrategyTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void empty() {
        ReferenceCountMergingStrategy strategy = new ReferenceCountMergingStrategy();
        assertThat(strategy.getAllDexToMerge()).isEmpty();
    }

    @Test
    public void testAdding() throws Exception {
        ReferenceCountMergingStrategy strategy = new ReferenceCountMergingStrategy();
        int TO_ADD = 5;
        for (int i = 0; i < TO_ADD; i++) {
            strategy.tryToAddForMerging(generateDex("A" + i, 20, 20));
        }
        assertThat(strategy.getAllDexToMerge()).hasSize(TO_ADD);
    }

    @Test
    public void testFields() throws Exception {
        ReferenceCountMergingStrategy strategy = new ReferenceCountMergingStrategy();
        strategy.tryToAddForMerging(generateDex("A", 65536 / 2, 0));
        strategy.tryToAddForMerging(generateDex("B", 65536 / 2, 0));
        assertThat(strategy.getAllDexToMerge()).hasSize(2);

        Dex duplicateA = generateDex("A", 10, 0);
        Dex duplicateB = generateDex("B", 10, 0);
        strategy.tryToAddForMerging(duplicateA);
        strategy.tryToAddForMerging(duplicateB);
        assertThat(strategy.getAllDexToMerge()).hasSize(4);

        Dex newWithMethods = generateDex("C", 0, 1);
        assertThat(strategy.tryToAddForMerging(newWithMethods)).isTrue();
        Dex newWithFields = generateDex("D", 1, 0);
        assertThat(strategy.tryToAddForMerging(newWithFields)).isFalse();
    }

    @Test
    public void testMethods() throws Exception {
        ReferenceCountMergingStrategy strategy = new ReferenceCountMergingStrategy();
        // methods will contain default constructor and Ljava/lang/Object;.<init>()
        strategy.tryToAddForMerging(generateDex("A", 0, 65536 / 2));
        strategy.tryToAddForMerging(generateDex("B", 0, 65536 / 2 - 3));
        assertThat(strategy.getAllDexToMerge()).hasSize(2);

        Dex duplicateA = generateDex("A", 0, 10);
        Dex duplicateB = generateDex("B", 0, 10);
        strategy.tryToAddForMerging(duplicateA);
        strategy.tryToAddForMerging(duplicateB);
        assertThat(strategy.getAllDexToMerge()).hasSize(4);

        Dex newWithMethods = generateDex("D", 0, 1);
        assertThat(strategy.tryToAddForMerging(newWithMethods)).isFalse();
    }

    @NonNull
    private Dex generateDex(@NonNull String className, int fieldCnt, int methodCnt)
            throws Exception {
        List<String> fields = new ArrayList<>(fieldCnt);
        for (int i = 0; i < fieldCnt; i++) {
            fields.add("field" + i);
        }
        List<String> methods = new ArrayList<>(methodCnt);
        for (int i = 0; i < methodCnt; i++) {
            methods.add("method" + i + ":()V");
        }
        byte[] bytecode =
                TestClassesGenerator.classWithFieldsAndMethods(className, fields, methods);
        Path inputDir = temporaryFolder.newFolder().toPath();
        Path classFile = inputDir.resolve(className + SdkConstants.DOT_CLASS);
        Files.write(classFile, bytecode);

        Path output = temporaryFolder.newFolder().toPath();
        DexArchiveTestUtil.convertClassesToDexArchive(inputDir, output);

        Path dex;
        try (Stream<Path> stream = Files.walk(output)) {
            dex = Iterators.getOnlyElement(
                    stream.filter(Files::isRegularFile).iterator());
        }
        return new Dex(dex.toFile());
    }
}
