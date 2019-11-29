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

package com.android.build.gradle.internal.transforms;

import static com.android.testutils.TestInputsGenerator.dirWithEmptyClasses;
import static com.android.testutils.TestInputsGenerator.jarWithEmptyClasses;
import static com.android.testutils.truth.MoreTruth.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.transforms.testdata.Animal;
import com.android.build.gradle.internal.transforms.testdata.CarbonForm;
import com.android.build.gradle.internal.transforms.testdata.Dog;
import com.android.build.gradle.internal.transforms.testdata.Toy;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.dexing.DexerTool;
import com.android.builder.utils.FileCache;
import com.android.builder.utils.FileCacheTestUtils;
import com.android.testutils.TestClassesGenerator;
import com.android.testutils.TestInputsGenerator;
import com.android.testutils.apk.Dex;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.gradle.api.Action;
import org.gradle.workers.WorkerConfiguration;
import org.gradle.workers.WorkerExecutionException;
import org.gradle.workers.WorkerExecutor;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** Testing the {@link DexArchiveBuilderTransform} and {@link DexMergerTransform}. */
@RunWith(Parameterized.class)
public class DexArchiveBuilderTransformTest {
    private File cacheDir;
    private FileCache userCache;
    private int expectedCacheEntryCount;
    private int expectedCacheHits;
    private int expectedCacheMisses;



    @Parameterized.Parameters
    public static Collection<Object[]> setups() {
        return ImmutableList.of(new Object[] {DexerTool.DX}, new Object[] {DexerTool.D8});
    }

    @Parameterized.Parameter public DexerTool dexerTool;

    private static final String PACKAGE = "com/example/tools";

    private Context context;
    private TransformOutputProvider outputProvider;
    private Path out;
    @Rule public TemporaryFolder tmpDir = new TemporaryFolder();

    private final WorkerExecutor workerExecutor =
            new WorkerExecutor() {
                @Override
                public void submit(
                        Class<? extends Runnable> aClass,
                        Action<? super WorkerConfiguration> action) {
                    WorkerConfiguration workerConfiguration =
                            Mockito.mock(WorkerConfiguration.class);
                    ArgumentCaptor<DexArchiveBuilderTransform.DexConversionParameters> captor =
                            ArgumentCaptor.forClass(
                                    DexArchiveBuilderTransform.DexConversionParameters.class);
                    action.execute(workerConfiguration);
                    verify(workerConfiguration).setParams(captor.capture());
                    DexArchiveBuilderTransform.DexConversionWorkAction workAction =
                            new DexArchiveBuilderTransform.DexConversionWorkAction(
                                    captor.getValue());
                    workAction.run();
                }

                @Override
                public void await() throws WorkerExecutionException {
                    // do nothing;
                }
            };

    @Before
    public void setUp() throws IOException {
        expectedCacheEntryCount = 0;
        expectedCacheHits = 0;
        expectedCacheMisses = 0;
        cacheDir = FileUtils.join(tmpDir.getRoot(), "cache");
        userCache = FileCache.getInstanceWithMultiProcessLocking(cacheDir);

        context = Mockito.mock(Context.class);
        when(context.getWorkerExecutor()).thenReturn(workerExecutor);

        out = tmpDir.getRoot().toPath().resolve("out");
        Files.createDirectories(out);
        outputProvider = new TestTransformOutputProvider(out);
    }

    @Test
    public void testInitialBuild() throws Exception {
        TransformInput dirInput =
                getDirInput(
                        tmpDir.getRoot().toPath().resolve("dir_input"),
                        ImmutableList.of(PACKAGE + "/A"));
        TransformInput jarInput =
                getJarInput(
                        tmpDir.getRoot().toPath().resolve("input.jar"),
                        ImmutableList.of(PACKAGE + "/B"));
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setContext(context)
                        .setTransformOutputProvider(outputProvider)
                        .setInputs(ImmutableSet.of(dirInput, jarInput))
                        .setIncremental(true)
                        .build();
        getTransform(null).transform(invocation);

        assertThat(FileUtils.find(out.toFile(), Pattern.compile(".*\\.dex"))).hasSize(1);
        List<File> jarDexArchives = FileUtils.find(out.toFile(), Pattern.compile(".*\\.jar"));
        assertThat(jarDexArchives).hasSize(1);
    }

    @Test
    public void testCacheUsedForExternalLibOnly() throws Exception {
        File cacheDir = FileUtils.join(tmpDir.getRoot(), "cache");
        FileCache userCache = FileCache.getInstanceWithMultiProcessLocking(cacheDir);

        TransformInput dirInput =
                getDirInput(
                        tmpDir.getRoot().toPath().resolve("dir_input"),
                        ImmutableList.of(PACKAGE + "/A"));
        TransformInput jarInput =
                getJarInput(
                        tmpDir.getRoot().toPath().resolve("input.jar"),
                        ImmutableList.of(PACKAGE + "/B"));
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setContext(context)
                        .setInputs(ImmutableSet.of(dirInput, jarInput))
                        .setTransformOutputProvider(outputProvider)
                        .setIncremental(true)
                        .build();
        DexArchiveBuilderTransform transform = getTransform(userCache);
        transform.transform(invocation);

        assertThat(cacheEntriesCount(cacheDir)).isEqualTo(1);
    }

    @Test
    public void testCacheUsedForLocalJars() throws Exception {
        File cacheDir = FileUtils.join(tmpDir.getRoot(), "cache");
        FileCache cache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);

        Path inputJar = tmpDir.getRoot().toPath().resolve("input.jar");
        TransformInput input = getJarInput(inputJar, ImmutableList.of(PACKAGE + "/A"));

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setContext(context)
                        .setInputs(input)
                        .setTransformOutputProvider(outputProvider)
                        .setIncremental(true)
                        .build();
        DexArchiveBuilderTransform transform = getTransform(cache);
        transform.transform(invocation);

        assertThat(cacheDir.listFiles(File::isDirectory)).hasLength(1);
    }

    @Test
    public void testEntryRemovedFromTheArchive() throws Exception {
        Path inputDir = tmpDir.getRoot().toPath().resolve("dir_input");
        Path inputJar = tmpDir.getRoot().toPath().resolve("input.jar");

        TransformInput dirTransformInput =
                getDirInput(inputDir, ImmutableList.of(PACKAGE + "/A", PACKAGE + "/B"));
        TransformInput jarTransformInput = getJarInput(inputJar, ImmutableList.of(PACKAGE + "/C"));

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setContext(context)
                        .setInputs(dirTransformInput, jarTransformInput)
                        .setTransformOutputProvider(outputProvider)
                        .setIncremental(true)
                        .build();
        getTransform(null).transform(invocation);
        assertThat(FileUtils.find(out.toFile(), "B.dex").orNull()).isFile();

        // remove the class file
        TransformInput deletedDirInput =
                TransformTestHelper.directoryBuilder(inputDir.toFile())
                        .putChangedFiles(
                                ImmutableMap.of(
                                        inputDir.resolve(PACKAGE + "/B.class").toFile(),
                                        Status.REMOVED))
                        .setScope(QualifiedContent.Scope.PROJECT)
                        .build();

        TransformInput unchangedJarInput =
                TransformTestHelper.singleJarBuilder(inputJar.toFile())
                        .setStatus(Status.NOTCHANGED)
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .build();
        TransformInvocation secondInvocation =
                TransformTestHelper.invocationBuilder()
                        .setContext(context)
                        .setInputs(deletedDirInput, unchangedJarInput)
                        .setTransformOutputProvider(outputProvider)
                        .setIncremental(true)
                        .build();
        getTransform(null).transform(secondInvocation);
        assertThat(FileUtils.find(out.toFile(), "B.dex").orNull()).isNull();
        assertThat(FileUtils.find(out.toFile(), "A.dex").orNull()).isFile();
    }

    @Test
    public void testNonIncremental() throws Exception {
        TransformInput dirInput =
                getDirInput(
                        tmpDir.getRoot().toPath().resolve("dir_input"),
                        ImmutableList.of(PACKAGE + "/A"));

        TransformInput jarInput =
                getJarInput(
                        tmpDir.getRoot().toPath().resolve("input.jar"),
                        ImmutableList.of(PACKAGE + "/B"));
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setContext(context)
                        .setInputs(dirInput, jarInput)
                        .setIncremental(true)
                        .setTransformOutputProvider(outputProvider)
                        .build();
        getTransform(null).transform(invocation);

        TransformInput dir2Input =
                getDirInput(
                        tmpDir.getRoot().toPath().resolve("dir_2_input"),
                        ImmutableList.of(PACKAGE + "/C"));
        TransformInput jar2Input =
                getJarInput(
                        tmpDir.getRoot().toPath().resolve("input.jar"),
                        ImmutableList.of(PACKAGE + "/B"));
        TransformInvocation invocation2 =
                TransformTestHelper.invocationBuilder()
                        .setContext(context)
                        .setInputs(dir2Input, jar2Input)
                        .setIncremental(false)
                        .setTransformOutputProvider(outputProvider)
                        .build();
        getTransform(null).transform(invocation2);
        assertThat(FileUtils.find(out.toFile(), "A.dex").orNull()).isNull();
    }

    @Test
    public void testCacheKeyInputsChanges() throws Exception {
        File cacheDir = FileUtils.join(tmpDir.getRoot(), "cache");
        FileCache userCache = FileCache.getInstanceWithMultiProcessLocking(cacheDir);

        Path inputJar = tmpDir.getRoot().toPath().resolve("input.jar");
        TransformInput jarInput = getJarInput(inputJar, ImmutableList.of());
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(jarInput)
                        .setTransformOutputProvider(outputProvider)
                        .setContext(context)
                        .build();

        DexArchiveBuilderTransform transform = getTransform(userCache, 19, true);
        transform.transform(invocation);
        assertThat(cacheEntriesCount(cacheDir)).isEqualTo(1);

        DexArchiveBuilderTransform minChangedTransform = getTransform(userCache, 20, true);
        minChangedTransform.transform(invocation);
        assertThat(cacheEntriesCount(cacheDir)).isEqualTo(2);

        DexArchiveBuilderTransform debuggableChangedTransform = getTransform(userCache, 19, false);
        debuggableChangedTransform.transform(invocation);
        assertThat(cacheEntriesCount(cacheDir)).isEqualTo(3);

        DexArchiveBuilderTransform minAndDebuggableChangedTransform =
                getTransform(userCache, 20, false);
        minAndDebuggableChangedTransform.transform(invocation);
        assertThat(cacheEntriesCount(cacheDir)).isEqualTo(4);

        DexArchiveBuilderTransform useDifferentDexerTransform =
                new DexArchiveBuilderTransformBuilder()
                        .setAndroidJarClasspath(() -> Collections.emptyList())
                        .setDexOptions(new DefaultDexOptions())
                        .setMessageReceiver(new NoOpMessageReceiver())
                        .setUserLevelCache(userCache)
                        .setMinSdkVersion(20)
                        .setDexer(dexerTool == DexerTool.DX ? DexerTool.D8 : DexerTool.DX)
                        .setUseGradleWorkers(true)
                        .setInBufferSize(10)
                        .setOutBufferSize(10)
                        .setIsDebuggable(false)
                        .setJava8LangSupportType(VariantScope.Java8LangSupport.UNUSED)
                        .setProjectVariant("myVariant")
                        .setIncludeFeaturesInScope(false)
                        .createDexArchiveBuilderTransform();
        useDifferentDexerTransform.transform(invocation);
        assertThat(cacheEntriesCount(cacheDir)).isEqualTo(5);
    }

    @Test
    public void testD8DesugaringCacheKeys() throws Exception {

        // Only for D8, Ignore DX
        Assume.assumeTrue(dexerTool == DexerTool.D8);

        Path inputJar = tmpDir.getRoot().toPath().resolve("input.jar");
        Path emptyLibDir = tmpDir.getRoot().toPath().resolve("emptylibDir");
        Path emptyLibJar = tmpDir.getRoot().toPath().resolve("emptylib.jar");
        Path carbonFormLibJar = tmpDir.getRoot().toPath().resolve("carbonFormlib.jar");
        Path carbonFormLibJar2 = tmpDir.getRoot().toPath().resolve("carbonFormlib2.jar");
        Path animalLibDir = tmpDir.getRoot().toPath().resolve("animalLibDir");
        Path animalLibJar = tmpDir.getRoot().toPath().resolve("animalLib.jar");
        TestInputsGenerator.pathWithClasses(carbonFormLibJar, ImmutableList.of(CarbonForm.class));
        TestInputsGenerator.pathWithClasses(
                carbonFormLibJar2, ImmutableList.of(CarbonForm.class, Toy.class));
        TestInputsGenerator.pathWithClasses(animalLibDir, ImmutableList.of(Animal.class));
        TestInputsGenerator.pathWithClasses(animalLibJar, ImmutableList.of(Animal.class));
        TestInputsGenerator.pathWithClasses(inputJar, ImmutableList.of(Dog.class));
        TransformInput jarInput = getJarInput(inputJar);
        TransformInput emptyLibDirInput = getDirInput(emptyLibDir, ImmutableList.of());
        TransformInput emptyLibJarInput = getDirInput(emptyLibJar, ImmutableList.of());
        TransformInput carbonFormLibJarInput = getJarInput(carbonFormLibJar);
        TransformInput carbonFormLibJar2Input = getJarInput(carbonFormLibJar2);
        TransformInput animalLibJarInput = getJarInput(animalLibJar);
        TransformInput animalLibDirInput = getJarInput(animalLibDir);

        // Initial compilation: no lib
        TransformInvocation inintialInvocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(jarInput)
                        .setTransformOutputProvider(outputProvider)
                        .setContext(context)
                        .build();

        getTransform(userCache, 19, true, VariantScope.Java8LangSupport.D8)
                .transform(inintialInvocation);
        // Cache was empty so it's a miss and result was cached.
        expectedCacheEntryCount++;
        expectedCacheMisses++;
        checkCache();

        // With a dependency to a class file in a directory
        TransformInvocation invocation01 =
                TransformTestHelper.invocationBuilder()
                        .addInput(jarInput)
                        .setTransformOutputProvider(outputProvider)
                        .setContext(context)
                        .addReferenceInput(animalLibDirInput)
                        .addReferenceInput(carbonFormLibJarInput)
                        .build();
        getTransform(userCache, 19, true, VariantScope.Java8LangSupport.D8).transform(invocation01);
        // The directory dependency should disable caching
        checkCache();

        // Rerun initial invocation with D8 desugaring
        getTransform(userCache, 19, true, VariantScope.Java8LangSupport.D8)
                .transform(inintialInvocation);
        // Exact same run as inintialInvocation: should be a hit
        expectedCacheHits++;
        checkCache();

        // With the dependencies as jar and an empty directory
        TransformInvocation invocation02 =
                TransformTestHelper.invocationBuilder()
                        .addInput(jarInput)
                        .setTransformOutputProvider(outputProvider)
                        .setContext(context)
                        .addReferenceInput(animalLibJarInput)
                        .addReferenceInput(carbonFormLibJarInput)
                        .addReferenceInput(emptyLibDirInput)
                        .build();
        getTransform(userCache, 19, true, VariantScope.Java8LangSupport.D8).transform(invocation02);
        // The dir without dependency doesn't prevent caching, presence of the dependencies
        // changes the cache key
        expectedCacheMisses++;
        expectedCacheEntryCount++;
        checkCache();

        // Same as invocation02 without the empty directory
        TransformInvocation invocation03 =
                TransformTestHelper.invocationBuilder()
                        .addInput(jarInput)
                        .setTransformOutputProvider(outputProvider)
                        .setContext(context)
                        .addReferenceInput(animalLibJarInput)
                        .addReferenceInput(carbonFormLibJarInput)
                        .build();
        getTransform(userCache, 19, true, VariantScope.Java8LangSupport.D8).transform(invocation03);
        // Removing the empty directory doesn't change the cache key
        expectedCacheHits++;
        checkCache();

        // Same as invocation03 with empty jar
        TransformInvocation invocation04 =
                TransformTestHelper.invocationBuilder()
                        .addInput(jarInput)
                        .setTransformOutputProvider(outputProvider)
                        .setContext(context)
                        .addReferenceInput(animalLibJarInput)
                        .addReferenceInput(carbonFormLibJarInput)
                        .addReferenceInput(emptyLibJarInput)
                        .build();
        getTransform(userCache, 19, true, VariantScope.Java8LangSupport.D8).transform(invocation04);
        // Adding the empty jar doesn't change the cache key
        expectedCacheHits++;
        checkCache();

        // Same as invocation03 without Animal
        TransformInvocation invocation05 =
                TransformTestHelper.invocationBuilder()
                        .addInput(jarInput)
                        .setTransformOutputProvider(outputProvider)
                        .setContext(context)
                        .addReferenceInput(carbonFormLibJarInput)
                        .build();
        getTransform(userCache, 19, true, VariantScope.Java8LangSupport.D8).transform(invocation05);
        // Without Animal we can not see the dependency to animalLibJarInput so it's a hit
        // on "initial invocation with D8 desugaring"
        expectedCacheHits++;
        checkCache();

        // Same as invocation03 without CarbonForm
        TransformInvocation invocation06 =
                TransformTestHelper.invocationBuilder()
                        .addInput(jarInput)
                        .setTransformOutputProvider(outputProvider)
                        .setContext(context)
                        .addReferenceInput(animalLibJarInput)
                        .build();
        getTransform(userCache, 19, true, VariantScope.Java8LangSupport.D8).transform(invocation06);
        // Even with incomplete hierarchy we should still be able to identify the dependency to the
        // one available classpath entry.
        expectedCacheMisses++;
        expectedCacheEntryCount++;
        checkCache();

        /* TODO Enable this test once D8 supports the case
        // Same as invocation03 with additional version of CarbonForm
        TransformInvocation invocation07 =
                TransformTestHelper.invocationBuilder()
                        .addInput(jarInput)
                        .setTransformOutputProvider(outputProvider)
                        .setContext(context)
                        .addReferenceInput(animalLibJarInput)
                        .addReferenceInput(carbonFormLibJarInput)
                        .addReferenceInput(carbonFormLibJar2Input)
                        .build();
        getTransform(userCache, 19, true, VariantScope.Java8LangSupport.D8)
                .transform(invocation07);
        // As long as we do not handle lib order for dependency tracking, the second version of
        // CarbonForm is supposed to be an additional dependency.
        expectedCacheMisses++;
        expectedCacheEntryCount++;
        checkCache();
        */
    }

    @Test
    public void testIncrementalUnchangedDirInput() throws Exception {
        Path input = tmpDir.newFolder("classes").toPath();
        dirWithEmptyClasses(input, ImmutableList.of("test/A", "test/B"));

        TransformInput dirInput =
                TransformTestHelper.directoryBuilder(input.toFile())
                        .putChangedFiles(ImmutableMap.of())
                        .build();
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setInputs(ImmutableSet.of(dirInput))
                        .setIncremental(true)
                        .setTransformOutputProvider(outputProvider)
                        .setContext(context)
                        .build();
        getTransform(null, 21, true).transform(invocation);
        Truth.assertThat(FileUtils.getAllFiles(out.toFile())).isEmpty();
    }

    /** Regression test for b/65241720. */
    @Test
    public void testIncrementalWithSharding() throws Exception {
        File cacheDir = FileUtils.join(tmpDir.getRoot(), "cache");
        FileCache userCache = FileCache.getInstanceWithMultiProcessLocking(cacheDir);
        Path input = tmpDir.getRoot().toPath().resolve("classes.jar");
        jarWithEmptyClasses(input, ImmutableList.of("test/A", "test/B"));

        TransformInput jarInput =
                TransformTestHelper.singleJarBuilder(input.toFile())
                        .setStatus(Status.ADDED)
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .build();

        TransformInvocation noCacheInvocation =
                TransformTestHelper.invocationBuilder()
                        .setInputs(jarInput)
                        .setIncremental(false)
                        .setTransformOutputProvider(outputProvider)
                        .setContext(context)
                        .build();

        DexArchiveBuilderTransform noCacheTransform = getTransform(userCache);
        noCacheTransform.transform(noCacheInvocation);
        assertThat(out.resolve("classes.jar.jar")).doesNotExist();

        // clean the output of the previous transform
        FileUtils.cleanOutputDir(out.toFile());

        TransformInvocation fromCacheInvocation =
                TransformTestHelper.invocationBuilder()
                        .setInputs(jarInput)
                        .setIncremental(true)
                        .setTransformOutputProvider(outputProvider)
                        .setContext(context)
                        .build();
        DexArchiveBuilderTransform fromCacheTransform = getTransform(userCache);
        fromCacheTransform.transform(fromCacheInvocation);
        assertThat(FileUtils.getAllFiles(out.toFile())).hasSize(1);
        assertThat(out.resolve("classes.jar.jar")).exists();

        // modify the file so it is not a build cache hit any more
        Files.deleteIfExists(input);
        jarWithEmptyClasses(input, ImmutableList.of("test/C"));

        TransformInput changedInput =
                TransformTestHelper.singleJarBuilder(input.toFile())
                        .setStatus(Status.CHANGED)
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .build();
        TransformInvocation changedInputInvocation =
                TransformTestHelper.invocationBuilder()
                        .setInputs(changedInput)
                        .setIncremental(true)
                        .setTransformOutputProvider(outputProvider)
                        .setContext(context)
                        .build();
        DexArchiveBuilderTransform changedInputTransform = getTransform(userCache);
        changedInputTransform.transform(changedInputInvocation);
        assertThat(out.resolve("classes.jar.jar")).doesNotExist();
    }

    /** Regression test for b/65241720. */
    @Test
    public void testDirectoryRemovedInIncrementalBuild() throws Exception {
        Path input = tmpDir.getRoot().toPath().resolve("classes");
        Path nestedDir = input.resolve("nested_dir");
        Files.createDirectories(nestedDir);
        Path nestedDirOutput = out.resolve("classes/nested_dir");
        Files.createDirectories(nestedDirOutput);

        TransformInput dirInput =
                TransformTestHelper.directoryBuilder(input.toFile())
                        .putChangedFiles(ImmutableMap.of(nestedDir.toFile(), Status.REMOVED))
                        .build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setInputs(dirInput)
                        .setIncremental(true)
                        .setTransformOutputProvider(outputProvider)
                        .setContext(context)
                        .build();
        DexArchiveBuilderTransform noCacheTransform = getTransform(null);
        noCacheTransform.transform(invocation);
        assertThat(nestedDirOutput).doesNotExist();
    }

    @Test
    public void testMultiReleaseJar() throws Exception {
        Path input = tmpDir.getRoot().toPath().resolve("classes.jar");
        try (ZipOutputStream stream = new ZipOutputStream(Files.newOutputStream(input))) {
            stream.putNextEntry(new ZipEntry("test/A.class"));
            stream.write(TestClassesGenerator.emptyClass("test", "A"));
            stream.closeEntry();
            stream.putNextEntry(new ZipEntry("module-info.class"));
            stream.write(new byte[] {0x1});
            stream.closeEntry();
            stream.putNextEntry(new ZipEntry("META-INF/9/test/B.class"));
            stream.write(TestClassesGenerator.emptyClass("test", "B"));
            stream.closeEntry();
            stream.putNextEntry(new ZipEntry("/META-INF/9/test/C.class"));
            stream.write(TestClassesGenerator.emptyClass("test", "C"));
            stream.closeEntry();
        }

        TransformInput dirInput = TransformTestHelper.singleJarBuilder(input.toFile()).build();
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setInputs(ImmutableSet.of(dirInput))
                        .setIncremental(false)
                        .setTransformOutputProvider(outputProvider)
                        .setContext(context)
                        .build();
        getTransform(null, 21, true).transform(invocation);

        // verify output contains only test/A
        File jarWithDex = Iterables.getOnlyElement(FileUtils.getAllFiles(out.toFile()));
        try (ZipFile zipFile = new ZipFile(jarWithDex)) {
            assertThat(zipFile.size()).isEqualTo(1);
            InputStream inputStream = zipFile.getInputStream(zipFile.entries().nextElement());

            Dex dex = new Dex(ByteStreams.toByteArray(inputStream), "unknown");
            assertThat(dex).containsExactlyClassesIn(ImmutableList.of("Ltest/A;"));
        }
    }

    @Test
    public void testIrSlicingPerPackage() throws Exception {
        Path folder = tmpDir.getRoot().toPath().resolve("dir_input");
        dirWithEmptyClasses(
                folder, ImmutableList.of(PACKAGE + "/A", PACKAGE + "/B", PACKAGE + "/C"));
        TransformInput dirInput =
                TransformTestHelper.directoryBuilder(folder.toFile())
                        .setScope(QualifiedContent.Scope.PROJECT)
                        .build();
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setContext(context)
                        .setInputs(ImmutableSet.of(dirInput))
                        .setTransformOutputProvider(outputProvider)
                        .build();
        DexArchiveBuilderTransform transform =
                new DexArchiveBuilderTransformBuilder()
                        .setAndroidJarClasspath(Collections::emptyList)
                        .setDexOptions(new DefaultDexOptions())
                        .setMessageReceiver(new NoOpMessageReceiver())
                        .setMinSdkVersion(21)
                        .setDexer(dexerTool)
                        .setIsDebuggable(true)
                        .setJava8LangSupportType(VariantScope.Java8LangSupport.UNUSED)
                        .setProjectVariant("myVariant")
                        .setIsInstantRun(true)
                        .createDexArchiveBuilderTransform();
        transform.transform(invocation);

        File dexA = Objects.requireNonNull(FileUtils.find(out.toFile(), "A.dex").orNull());
        assertThat(dexA.toString()).contains("slice_");
        assertThat(dexA.toPath().resolveSibling("B.dex")).exists();
        assertThat(dexA.toPath().resolveSibling("C.dex")).exists();
    }

    @Test
    public void testIrRemovedClasses() throws Exception {
        Path folder = tmpDir.getRoot().toPath().resolve("dir_input");
        dirWithEmptyClasses(
                folder, ImmutableList.of(PACKAGE + "/A", PACKAGE + "/B", PACKAGE + "/C"));
        TransformInput dirInput =
                TransformTestHelper.directoryBuilder(folder.toFile())
                        .setScope(QualifiedContent.Scope.PROJECT)
                        .build();
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setContext(context)
                        .setInputs(ImmutableSet.of(dirInput))
                        .setTransformOutputProvider(outputProvider)
                        .build();
        DexArchiveBuilderTransform transform =
                new DexArchiveBuilderTransformBuilder()
                        .setAndroidJarClasspath(Collections::emptyList)
                        .setDexOptions(new DefaultDexOptions())
                        .setMessageReceiver(new NoOpMessageReceiver())
                        .setMinSdkVersion(21)
                        .setDexer(dexerTool)
                        .setIsDebuggable(true)
                        .setJava8LangSupportType(VariantScope.Java8LangSupport.UNUSED)
                        .setProjectVariant("myVariant")
                        .setIsInstantRun(true)
                        .createDexArchiveBuilderTransform();
        transform.transform(invocation);

        dirInput =
                TransformTestHelper.directoryBuilder(folder.toFile())
                        .setScope(QualifiedContent.Scope.PROJECT)
                        .putChangedFiles(
                                ImmutableMap.of(
                                        folder.resolve(PACKAGE + "/A.class").toFile(),
                                        Status.REMOVED))
                        .build();
        invocation =
                TransformTestHelper.invocationBuilder()
                        .setContext(context)
                        .setInputs(ImmutableSet.of(dirInput))
                        .setTransformOutputProvider(outputProvider)
                        .setIncremental(true)
                        .build();
        transform.transform(invocation);

        File dexA = Objects.requireNonNull(FileUtils.find(out.toFile(), "B.dex").orNull());
        assertThat(dexA.toPath().resolveSibling("A.dex")).doesNotExist();
        assertThat(dexA.toPath().resolveSibling("C.dex")).exists();
    }

    @Test
    public void testChangingStreamName() throws Exception {
        // make output provider that outputs based on name
        outputProvider =
                new TestTransformOutputProvider(out) {
                    @NonNull
                    @Override
                    public File getContentLocation(
                            @NonNull String name,
                            @NonNull Set<QualifiedContent.ContentType> types,
                            @NonNull Set<? super QualifiedContent.Scope> scopes,
                            @NonNull Format format) {
                        return out.resolve(Long.toString(name.hashCode())).toFile();
                    }
                };

        Path folder = tmpDir.getRoot().toPath().resolve("dir_input");
        dirWithEmptyClasses(
                folder, ImmutableList.of(PACKAGE + "/A", PACKAGE + "/B", PACKAGE + "/C"));
        TransformInput dirInput =
                TransformTestHelper.directoryBuilder(folder.toFile())
                        .setName("first-run")
                        .setScope(QualifiedContent.Scope.PROJECT)
                        .build();
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setContext(context)
                        .setInputs(ImmutableSet.of(dirInput))
                        .setTransformOutputProvider(outputProvider)
                        .build();
        DexArchiveBuilderTransform transform =
                new DexArchiveBuilderTransformBuilder()
                        .setAndroidJarClasspath(Collections::emptyList)
                        .setDexOptions(new DefaultDexOptions())
                        .setMessageReceiver(new NoOpMessageReceiver())
                        .setMinSdkVersion(21)
                        .setDexer(dexerTool)
                        .setIsDebuggable(true)
                        .setJava8LangSupportType(VariantScope.Java8LangSupport.UNUSED)
                        .setProjectVariant("myVariant")
                        .createDexArchiveBuilderTransform();
        transform.transform(invocation);

        dirInput =
                TransformTestHelper.directoryBuilder(folder.toFile())
                        .setName("second-run")
                        .setScope(QualifiedContent.Scope.PROJECT)
                        .putChangedFiles(
                                ImmutableMap.of(
                                        folder.resolve(PACKAGE + "/A.class").toFile(),
                                        Status.REMOVED))
                        .build();
        invocation =
                TransformTestHelper.invocationBuilder()
                        .setContext(context)
                        .setInputs(ImmutableSet.of(dirInput))
                        .setTransformOutputProvider(outputProvider)
                        .setIncremental(true)
                        .build();
        transform.transform(invocation);

        File dexA = Objects.requireNonNull(FileUtils.find(out.toFile(), "B.dex").orNull());
        assertThat(dexA.toPath().resolveSibling("A.dex")).doesNotExist();
        assertThat(dexA.toPath().resolveSibling("C.dex")).exists();
    }

    @Test
    public void testDexingArtifactTransformOnlyProjectDexed() throws Exception {
        Path folder = tmpDir.getRoot().toPath().resolve("dir_input");
        dirWithEmptyClasses(folder, ImmutableList.of(PACKAGE + "/A"));
        TransformInput projectInput =
                TransformTestHelper.directoryBuilder(folder.toFile())
                        .setScope(QualifiedContent.Scope.PROJECT)
                        .build();
        Path folderExternal = tmpDir.getRoot().toPath().resolve("external");
        dirWithEmptyClasses(folderExternal, ImmutableList.of(PACKAGE + "/B"));
        TransformInput externalInput =
                TransformTestHelper.directoryBuilder(folderExternal.toFile())
                        .setScope(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setContext(context)
                        .setTransformOutputProvider(outputProvider)
                        .setInputs(ImmutableSet.of(projectInput, externalInput))
                        .setIncremental(false)
                        .build();

        DexArchiveBuilderTransform transform =
                new DexArchiveBuilderTransformBuilder()
                        .setAndroidJarClasspath(Collections::emptyList)
                        .setDexOptions(new DefaultDexOptions())
                        .setMessageReceiver(new NoOpMessageReceiver())
                        .setUserLevelCache(userCache)
                        .setMinSdkVersion(21)
                        .setDexer(dexerTool)
                        .setUseGradleWorkers(false)
                        .setInBufferSize(10)
                        .setOutBufferSize(10)
                        .setIsDebuggable(true)
                        .setJava8LangSupportType(VariantScope.Java8LangSupport.UNUSED)
                        .setProjectVariant("myVariant")
                        .setIncludeFeaturesInScope(false)
                        .setEnableDexingArtifactTransform(true)
                        .createDexArchiveBuilderTransform();

        transform.transform(invocation);

        File dex = Objects.requireNonNull(FileUtils.find(out.toFile(), "A.dex").orNull());
        assertThat(dex.toPath().resolveSibling("A.dex")).exists();
        assertThat(dex.toPath().resolveSibling("B.dex")).doesNotExist();
    }

    @NonNull
    private DexArchiveBuilderTransform getTransform(
            @Nullable FileCache userCache, int minSdkVersion, boolean isDebuggable) {
        return getTransform(
                userCache, minSdkVersion, isDebuggable, VariantScope.Java8LangSupport.UNUSED);
    }

    @NonNull
    private DexArchiveBuilderTransform getTransform(
            @Nullable FileCache userCache,
            int minSdkVersion,
            boolean isDebuggable,
            @NonNull VariantScope.Java8LangSupport java8Support) {

        return new DexArchiveBuilderTransformBuilder()
                .setAndroidJarClasspath(Collections::emptyList)
                .setDexOptions(new DefaultDexOptions())
                .setMessageReceiver(new NoOpMessageReceiver())
                .setUserLevelCache(userCache)
                .setMinSdkVersion(minSdkVersion)
                .setDexer(dexerTool)
                .setUseGradleWorkers(true)
                .setInBufferSize(10)
                .setOutBufferSize(10)
                .setIsDebuggable(isDebuggable)
                .setJava8LangSupportType(java8Support)
                .setProjectVariant("myVariant")
                .setIncludeFeaturesInScope(false)
                .createDexArchiveBuilderTransform();
    }

    @NonNull
    private DexArchiveBuilderTransform getTransform(@Nullable FileCache userCache) {
        return getTransform(userCache, 1, true);
    }

    private int cacheEntriesCount(@NonNull File cacheDir) {
        File[] files = cacheDir.listFiles(File::isDirectory);
        assertThat(files).isNotNull();
        return files.length;

    }

    @NonNull
    private TransformInput getDirInput(@NonNull Path path, @NonNull Collection<String> classes)
            throws Exception {
        dirWithEmptyClasses(path, classes);
        return getDirInput(path);
    }

    @NonNull
    private TransformInput getDirInput(@NonNull Path path) throws IOException {
        return TransformTestHelper.directoryBuilder(path.toFile())
                .setContentType(QualifiedContent.DefaultContentType.CLASSES)
                .setScope(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                .putChangedFiles(
                        Files.walk(path)
                                .filter(
                                        entry ->
                                                Files.isRegularFile(entry)
                                                        && entry.toString()
                                                                .endsWith(SdkConstants.DOT_CLASS))
                                .collect(
                                        Collectors.toMap(
                                                entry -> entry.toFile(), entry -> Status.ADDED)))
                .build();
    }

    @NonNull
    private TransformInput getJarInput(@NonNull Path path, @NonNull Collection<String> classes)
            throws Exception {
        jarWithEmptyClasses(path, classes);
        return getJarInput(path);
    }

    @NonNull
    private TransformInput getJarInput(@NonNull Path path) throws Exception {
        return TransformTestHelper.singleJarBuilder(path.toFile())
                .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                .setStatus(Status.ADDED)
                .build();
    }

    private void checkCache() {
        int entriesCount = cacheEntriesCount(userCache.getCacheDirectory());
        assertThat(entriesCount).named("Cache entry count").isEqualTo(expectedCacheEntryCount);
        /* TODO change usage of FileCache to allow recording of hits.
        assertThat(FileCacheTestUtils.getHits(userCache)).named("Cache hits")
                .isEqualTo(expectedCacheHits);
        */
        // Misses occurs when filling the cache
        assertThat(FileCacheTestUtils.getMisses(userCache))
                .named("Cache misses")
                .isEqualTo(expectedCacheMisses);
    }

}
