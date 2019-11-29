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

import static com.android.builder.dexing.DexArchiveTestUtil.PACKAGE;
import static com.android.testutils.truth.DexSubject.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.fail;
import static org.objectweb.asm.Opcodes.V1_6;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.testutils.TestClassesGenerator;
import com.android.testutils.TestInputsGenerator;
import com.android.testutils.apk.Dex;
import com.android.utils.FileUtils;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/**
 * Testing the dex archive merger. It takes one or more dex archives as input, and outputs one or
 * more DEX files.
 */
@RunWith(Parameterized.class)
public class DexArchiveMergerTest {

    @Parameterized.Parameters(name = "{0}_{1}")
    public static Iterable<Object[]> setup() {
        return ImmutableList.of(
                new Object[] {DexerTool.DX, DexMergerTool.DX},
                new Object[] {DexerTool.D8, DexMergerTool.D8});
    }

    @Parameterized.Parameter(0)
    public DexerTool dexerTool;

    @Parameterized.Parameter(1)
    public DexMergerTool dexMerger;

    @ClassRule public static TemporaryFolder allTestsTemporaryFolder = new TemporaryFolder();

    private static final String BIG_CLASS = "BigClass";
    private static Path bigDexArchive;

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void setUp() throws Exception {
        Path inputRoot = allTestsTemporaryFolder.getRoot().toPath().resolve("big_class");
        DexArchiveTestUtil.createClassWithMethodDescriptors(inputRoot, BIG_CLASS, 65524);

        bigDexArchive = allTestsTemporaryFolder.getRoot().toPath().resolve("big_dex_archive");
        DexArchiveTestUtil.convertClassesToDexArchive(inputRoot, bigDexArchive, DexerTool.D8);
    }

    @Test
    public void test_monoDex_twoDexMerging() throws Exception {
        Path fstArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("fst"), "A");
        Path sndArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("snd"), "B");

        Path output = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.mergeMonoDex(
                ImmutableList.of(fstArchive, sndArchive), output, dexMerger);

        Dex outputDex = new Dex(output.resolve("classes.dex"));

        assertThat(outputDex).containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses("A", "B"));
    }

    @Test
    public void test_monoDex_manyDexMerged() throws Exception {
        List<Path> archives = Lists.newArrayList();
        List<String> expectedClasses = Lists.newArrayList();
        for (int i = 0; i < 100; i++) {
            archives.add(
                    DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                            temporaryFolder.getRoot().toPath().resolve("A" + i), "A" + i));
            expectedClasses.add("A" + i);
        }

        Path output = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.mergeMonoDex(archives, output, dexMerger);

        Dex outputDex = new Dex(output.resolve("classes.dex"));
        assertThat(outputDex)
                .containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses(expectedClasses));
    }

    @Test
    public void test_monoDex_exactLimit() throws Exception {
        Path inputRoot = temporaryFolder.getRoot().toPath().resolve("classes");
        DexArchiveTestUtil.createClassWithMethodDescriptors(inputRoot, "A", 9);

        Path dexArchive = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.convertClassesToDexArchive(inputRoot, dexArchive, dexerTool);

        Path outputDex = temporaryFolder.getRoot().toPath().resolve("output_dex");
        DexArchiveTestUtil.mergeMonoDex(
                ImmutableList.of(dexArchive, bigDexArchive), outputDex, dexMerger);

        Dex finalDex = new Dex(outputDex.resolve("classes.dex"));
        assertThat(finalDex)
                .containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses(BIG_CLASS, "A"));
    }

    @Test
    public void test_monoDex_aboveLimit() throws Exception {
        Path inputRoot = temporaryFolder.getRoot().toPath().resolve("classes");
        DexArchiveTestUtil.createClassWithMethodDescriptors(inputRoot, "B", 10);

        Path dexArchive = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.convertClassesToDexArchive(inputRoot, dexArchive, dexerTool);

        Path outputDex = temporaryFolder.getRoot().toPath().resolve("output_dex");
        try {
            DexArchiveTestUtil.mergeMonoDex(
                    ImmutableList.of(dexArchive, bigDexArchive), outputDex, dexMerger);
            fail("Too many methods for mono-dex. Merging should fail.");
        } catch (Exception e) {
            String exepectedMessage;
            if (dexMerger == DexMergerTool.DX) {
                exepectedMessage = "method ID not in";
            } else {
                exepectedMessage =
                        "The number of method references in a .dex file cannot exceed 64K";
            }
            Truth.assertThat(Throwables.getStackTraceAsString(e)).contains(exepectedMessage);
        }
    }

    @Test
    public void test_legacyMultiDex_mergeTwo() throws Exception {
        Path fstArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("fst"), "A");
        Path sndArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("snd"), "B");

        Path output = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.mergeLegacyDex(
                ImmutableList.of(fstArchive, sndArchive),
                output,
                generateMainDexListFile(ImmutableSet.of(PACKAGE + "/A.class")),
                dexMerger);

        Dex outputDex = new Dex(output.resolve("classes.dex"));
        assertThat(outputDex).containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses("A"));

        Dex secondaryDex = new Dex(output.resolve("classes2.dex"));
        assertThat(secondaryDex).containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses("B"));
    }

    @Test
    public void test_legacyMultiDex_allInMainDex() throws Exception {
        Path fstArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("fst"), "A");
        Path sndArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("snd"), "B");

        Path output = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.mergeLegacyDex(
                ImmutableList.of(fstArchive, sndArchive),
                output,
                generateMainDexListFile(
                        ImmutableSet.of(PACKAGE + "/A.class", PACKAGE + "/B.class")),
                dexMerger);

        Dex outputDex = new Dex(output.resolve("classes.dex"));
        assertThat(outputDex).containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses("A", "B"));

        assertThat(output.resolve("classes2.dex")).doesNotExist();
    }

    @Test
    public void test_legacyMultiDex_multipleSecondary() throws Exception {
        Path inputRoot = temporaryFolder.getRoot().toPath().resolve("classes");
        DexArchiveTestUtil.createClassWithMethodDescriptors(inputRoot, "A", 1);
        DexArchiveTestUtil.createClassWithMethodDescriptors(inputRoot, "B", 10);

        Path dexArchive = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.convertClassesToDexArchive(inputRoot, dexArchive, dexerTool);

        Path outputDex = temporaryFolder.getRoot().toPath().resolve("output_dex");
        DexArchiveTestUtil.mergeLegacyDex(
                ImmutableList.of(dexArchive, bigDexArchive),
                outputDex,
                generateMainDexListFile(ImmutableSet.of(PACKAGE + "/A.class")),
                dexMerger);

        Dex primaryDex = new Dex(outputDex.resolve("classes.dex"));
        assertThat(primaryDex).containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses("A"));

        assertThat(outputDex.resolve("classes2.dex")).exists();
        assertThat(outputDex.resolve("classes3.dex")).exists();
        assertThat(outputDex.resolve("classes4.dex")).doesNotExist();
    }

    @Test
    public void test_nativeMultiDex_mergeTwo() throws Exception {
        Path fstArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("fst"), "A");
        Path sndArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("snd"), "B");

        Path output = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.mergeNativeDex(
                ImmutableList.of(fstArchive, sndArchive), output, dexMerger);

        Dex outputDex = new Dex(output.resolve("classes.dex"));
        assertThat(outputDex).containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses("A", "B"));
    }

    @Test
    public void test_nativeMultiDex_multipleDexes() throws Exception {
        Path inputRoot = temporaryFolder.getRoot().toPath().resolve("classes");
        DexArchiveTestUtil.createClassWithMethodDescriptors(inputRoot, "A", 10);

        Path dexArchive = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.convertClassesToDexArchive(inputRoot, dexArchive, dexerTool);

        Path outputDex = temporaryFolder.getRoot().toPath().resolve("output_dex");
        DexArchiveTestUtil.mergeNativeDex(
                ImmutableList.of(dexArchive, bigDexArchive), outputDex, dexMerger);

        assertThat(outputDex.resolve("classes.dex")).exists();
        assertThat(outputDex.resolve("classes2.dex")).exists();
        assertThat(outputDex.resolve("classes3.dex")).doesNotExist();
    }

    @Test
    public void test_nativeMultiDex_empty_sections() throws Exception {
        // regression test for - http://b.android.com/250705
        int NUM_CLASSES = 2;
        Path inputRoot = temporaryFolder.getRoot().toPath().resolve("classes");
        for (int i = 0; i < NUM_CLASSES; i++) {
            Path classFile = inputRoot.resolve(PACKAGE + "/" + "A" + i + SdkConstants.DOT_CLASS);
            Files.createDirectories(classFile.getParent());

            ClassWriter cw = new ClassWriter(0);
            cw.visit(
                    V1_6,
                    Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER,
                    PACKAGE + "/" + "A" + i,
                    null,
                    "java/lang/Object",
                    null);
            cw.visitEnd();

            Files.write(classFile, cw.toByteArray());
        }

        Path dexArchive = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.convertClassesToDexArchive(inputRoot, dexArchive, dexerTool);

        Path outputDex = temporaryFolder.getRoot().toPath().resolve("output_dex");
        DexArchiveTestUtil.mergeNativeDex(ImmutableList.of(dexArchive), outputDex, dexMerger);

        com.android.dex.Dex dexFile =
                new com.android.dex.Dex(outputDex.resolve("classes.dex").toFile());
        Truth.assertThat(dexFile.getTableOfContents().fieldIds.off)
                .named("fields offset")
                .isEqualTo(0);
        Truth.assertThat(dexFile.getTableOfContents().methodIds.off)
                .named("methods offset")
                .isEqualTo(0);
    }

    @Test
    public void test_multidex_orderOfInputsDoesNotChangeOutput() throws Exception {
        List<Path> archives = createArchives(temporaryFolder.getRoot().toPath().resolve("_"), 30);

        Path bigArchive = temporaryFolder.getRoot().toPath().resolve("big_archive");
        FileUtils.copyDirectory(bigDexArchive.toFile(), bigArchive.toFile());
        archives.add(bigArchive);

        Path output = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.mergeNativeDex(archives, output, dexMerger);
        byte[] classesDex = Files.readAllBytes(output.resolve("classes.dex"));
        byte[] classes2Dex = Files.readAllBytes(output.resolve("classes2.dex"));

        for (int i = 0; i < 5; i++) {
            Path newOutput = temporaryFolder.getRoot().toPath().resolve("output" + i);
            DexArchiveTestUtil.mergeNativeDex(archives, newOutput, dexMerger);

            Truth.assertThat(classesDex)
                    .isEqualTo(Files.readAllBytes(output.resolve("classes.dex")));
            Truth.assertThat(classes2Dex)
                    .isEqualTo(Files.readAllBytes(output.resolve("classes2.dex")));

            Collections.shuffle(archives);
        }
    }

    @Test
    public void test_monoDex_orderOfInputsDoesNotChangeOutput() throws Exception {
        List<Path> archives = createArchives(temporaryFolder.getRoot().toPath(), 10);

        Path output = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.mergeMonoDex(archives, output, dexMerger);
        byte[] classesDex = Files.readAllBytes(output.resolve("classes.dex"));

        for (int i = 0; i < 5; i++) {
            Path newOutput = temporaryFolder.getRoot().toPath().resolve("output" + i);
            DexArchiveTestUtil.mergeMonoDex(archives, newOutput, dexMerger);
            Truth.assertThat(classesDex)
                    .isEqualTo(Files.readAllBytes(output.resolve("classes.dex")));

            Collections.shuffle(archives);
        }
    }

    @Test
    public void testWindowsSmokeTest() throws Exception {
        FileSystem fs = Jimfs.newFileSystem(Configuration.windows());

        Set<String> classNames = ImmutableSet.of("A", "B", "C");
        Path classesInput = fs.getPath("tmp\\input_classes");
        DexArchiveTestUtil.createClasses(classesInput, classNames);
        Path dexArchive = fs.getPath("tmp\\dex_archive");
        DexArchiveTestUtil.convertClassesToDexArchive(classesInput, dexArchive, dexerTool);

        Path output = fs.getPath("tmp\\out");
        DexArchiveTestUtil.mergeMonoDex(ImmutableList.of(dexArchive), output, dexMerger);

        Dex outputDex = new Dex(output.resolve("classes.dex"));

        assertThat(outputDex)
                .containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses("A", "B", "C"));
    }

    @Test
    public void testStringsAbove64k() throws Exception {
        int numClasses = 16;
        Path inputRoot = temporaryFolder.getRoot().toPath().resolve("classes");
        for (int i = 0; i < numClasses; i++) {
            String className = PACKAGE + "/" + "A" + i;
            Path classFile = inputRoot.resolve(className + SdkConstants.DOT_CLASS);
            Files.createDirectories(classFile.getParent());

            Files.write(
                    classFile,
                    TestClassesGenerator.classWithStrings(className, (65536 / numClasses) + 1));
        }

        Path dexArchive = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.convertClassesToDexArchive(inputRoot, dexArchive, dexerTool);

        Path outputDex = temporaryFolder.getRoot().toPath().resolve("output_dex");
        DexArchiveTestUtil.mergeNativeDex(ImmutableList.of(dexArchive), outputDex, dexMerger);
    }

    @Test
    public void testD8DuplicateClassError() throws Exception {
        // create 2 classes with the same name and package
        int numClasses = 2;
        List<Path> dexArchives = new ArrayList<>();
        for (int i = 0; i < numClasses; i++) {
            Path classes = temporaryFolder.newFolder("classes" + i).toPath();
            TestInputsGenerator.dirWithEmptyClasses(classes, Collections.singletonList("test/A"));
            dexArchives.add(temporaryFolder.getRoot().toPath().resolve("output" + i));
            DexArchiveTestUtil.convertClassesToDexArchive(classes, dexArchives.get(i), dexerTool);
        }

        Path outputDex = temporaryFolder.getRoot().toPath().resolve("output_dex");

        try {
            DexArchiveTestUtil.mergeMonoDex(dexArchives, outputDex, dexMerger);
            fail("dex merging should fail when there are classes with same name and package");
        } catch (DexArchiveMergerException e) {
            Truth.assertThat(e.getMessage()).contains("Program type already present");
        } catch (Exception e) {
            Truth.assertThat(Throwables.getStackTraceAsString(e))
                    .contains("Multiple dex files define");
        }
    }

    @NonNull
    private List<Path> createArchives(@NonNull Path archivePath, int numClasses) throws Exception {
        List<Path> archives = Lists.newArrayList();
        for (int i = 0; i < numClasses; i++) {
            archives.add(
                    DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                            archivePath.resolve("A" + i), "A" + i));
        }
        return archives;
    }

    @NonNull
    private Path generateMainDexListFile(@NonNull Set<String> mainDexClasses) throws IOException {
        return Files.write(temporaryFolder.newFile().toPath(), mainDexClasses);
    }
}
