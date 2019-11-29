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

import static com.android.build.api.transform.Status.CHANGED;
import static com.android.build.api.transform.Status.REMOVED;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.transforms.testdata.Animal;
import com.android.build.gradle.internal.transforms.testdata.CarbonForm;
import com.android.build.gradle.internal.transforms.testdata.Cat;
import com.android.build.gradle.internal.transforms.testdata.Tiger;
import com.android.build.gradle.internal.transforms.testdata.Toy;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.dexing.DexerTool;
import com.android.builder.utils.FileCache;
import com.android.testutils.TestInputsGenerator;
import com.android.testutils.TestUtils;
import com.android.testutils.truth.MoreTruth;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DexArchiveBuilderTransformDesugaringTest {

    private TransformOutputProvider outputProvider;
    private Path out;
    @Rule public TemporaryFolder tmpDir = new TemporaryFolder();

    @Before
    public void setUp() throws IOException {
        out = tmpDir.getRoot().toPath().resolve("out");
        Files.createDirectories(out);
        outputProvider = new TestTransformOutputProvider(out);
    }

    @Test
    public void testLambdas() throws Exception {
        Path input = tmpDir.getRoot().toPath().resolve("input");
        TestInputsGenerator.pathWithClasses(
                input, ImmutableSet.of(CarbonForm.class, Animal.class, Cat.class, Toy.class));

        TransformInput dirInput = TransformTestHelper.directoryBuilder(input.toFile()).build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setTransformOutputProvider(outputProvider)
                        .addInput(dirInput)
                        .setIncremental(false)
                        .build();
        getTransform(null, 15, true, false).transform(invocation);

        // it should contain Cat and synthesized lambda class
        MoreTruth.assertThatDex(getDex(Cat.class)).hasClassesCount(2);
    }

    interface WithDefault {
        default void foo() {}
    }

    interface WithStatic {
        static void bar() {}
    }

    static class ImplementsWithDefault implements WithDefault {}

    static class InvokesDefault {
        public static void main(String[] args) {
            new ImplementsWithDefault().foo();
        }
    }

    @Test
    public void testDefaultMethods_minApiBelow24() throws Exception {
        Path input = tmpDir.getRoot().toPath().resolve("input");
        TestInputsGenerator.pathWithClasses(
                input, ImmutableSet.of(WithDefault.class, WithStatic.class));

        TransformInput dirInput = TransformTestHelper.directoryBuilder(input.toFile()).build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setTransformOutputProvider(outputProvider)
                        .addInput(dirInput)
                        .setIncremental(false)
                        .build();
        getTransform(null, 23, true, false).transform(invocation);

        // it contains both original and synthesized
        MoreTruth.assertThatDex(getDex(WithDefault.class)).hasClassesCount(2);
        MoreTruth.assertThatDex(getDex(WithStatic.class)).hasClassesCount(2);
    }

    @Test
    public void testDefaultMethods_minApiAbove24() throws Exception {
        Path input = tmpDir.getRoot().toPath().resolve("input");
        TestInputsGenerator.pathWithClasses(
                input, ImmutableSet.of(WithDefault.class, WithStatic.class));

        TransformInput dirInput = TransformTestHelper.directoryBuilder(input.toFile()).build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setTransformOutputProvider(outputProvider)
                        .addInput(dirInput)
                        .setIncremental(false)
                        .build();
        getTransform(null, 24, true, false).transform(invocation);

        // it contains only the original class
        MoreTruth.assertThatDex(getDex(WithDefault.class)).hasClassesCount(1);
        MoreTruth.assertThatDex(getDex(WithStatic.class)).hasClassesCount(1);
    }

    @Test
    public void testIncremental_lambdaClass() throws Exception {
        Path input = tmpDir.getRoot().toPath().resolve("input");
        TestInputsGenerator.pathWithClasses(
                input, ImmutableSet.of(CarbonForm.class, Animal.class, Cat.class, Toy.class));

        TransformInput dirInput = TransformTestHelper.directoryBuilder(input.toFile()).build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setTransformOutputProvider(outputProvider)
                        .addInput(dirInput)
                        .setIncremental(false)
                        .build();
        getTransform(null).transform(invocation);

        File catDex = getDex(Cat.class);
        File animalDex = getDex(Animal.class);
        long catTimestamp = catDex.lastModified();
        long animalTimestamp = animalDex.lastModified();

        TestUtils.waitForFileSystemTick();

        dirInput =
                TransformTestHelper.directoryBuilder(input.toFile())
                        .putChangedFiles(getChangedStatusMap(input, CHANGED, Toy.class))
                        .build();
        invocation =
                TransformTestHelper.invocationBuilder()
                        .setIncremental(true)
                        .setTransformOutputProvider(outputProvider)
                        .addInput(dirInput)
                        .build();

        getTransform(null).transform(invocation);
        catDex = getDex(Cat.class);
        animalDex = getDex(Animal.class);
        assertThat(catTimestamp).isLessThan(catDex.lastModified());
        assertThat(animalTimestamp).isEqualTo(animalDex.lastModified());
    }

    @Test
    public void testIncremental_lambdaClass_removed() throws Exception {
        Path input = tmpDir.getRoot().toPath().resolve("input");
        TestInputsGenerator.pathWithClasses(
                input, ImmutableSet.of(CarbonForm.class, Animal.class, Cat.class, Toy.class));

        TransformInput dirInput = TransformTestHelper.directoryBuilder(input.toFile()).build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setTransformOutputProvider(outputProvider)
                        .addInput(dirInput)
                        .setIncremental(false)
                        .build();
        getTransform(null).transform(invocation);

        File catDex = getDex(Cat.class);
        File animalDex = getDex(Animal.class);
        long catTimestamp = catDex.lastModified();
        long animalTimestamp = animalDex.lastModified();

        TestUtils.waitForFileSystemTick();

        dirInput =
                TransformTestHelper.directoryBuilder(input.toFile())
                        .putChangedFiles(getChangedStatusMap(input, REMOVED, Toy.class))
                        .build();
        invocation =
                TransformTestHelper.invocationBuilder()
                        .setIncremental(true)
                        .setTransformOutputProvider(outputProvider)
                        .addInput(dirInput)
                        .build();

        File toyDex = getDex(Toy.class);
        getTransform(null).transform(invocation);
        catDex = getDex(Cat.class);
        animalDex = getDex(Animal.class);
        assertThat(catTimestamp).isLessThan(catDex.lastModified());
        assertThat(animalTimestamp).isEqualTo(animalDex.lastModified());

        assertThat(toyDex).doesNotExist();
    }

    @Test
    public void testIncremental_changeSuperTypes() throws Exception {
        Path input = tmpDir.getRoot().toPath().resolve("input");
        TestInputsGenerator.pathWithClasses(
                input,
                ImmutableSet.of(CarbonForm.class, Animal.class, Cat.class, Tiger.class, Toy.class));

        TransformInput dirInput = TransformTestHelper.directoryBuilder(input.toFile()).build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setTransformOutputProvider(outputProvider)
                        .addInput(dirInput)
                        .setIncremental(false)
                        .build();
        getTransform(null).transform(invocation);

        File tigerDex = getDex(Tiger.class);
        File carbonFormDex = getDex(CarbonForm.class);
        long tigerTimestamp = tigerDex.lastModified();
        long carbonFormTimestamp = carbonFormDex.lastModified();

        dirInput =
                TransformTestHelper.directoryBuilder(input.toFile())
                        .putChangedFiles(getChangedStatusMap(input, CHANGED, Animal.class))
                        .build();
        invocation =
                TransformTestHelper.invocationBuilder()
                        .setIncremental(true)
                        .setTransformOutputProvider(outputProvider)
                        .addInput(dirInput)
                        .build();

        TestUtils.waitForFileSystemTick();
        getTransform(null).transform(invocation);
        tigerDex = getDex(Tiger.class);
        carbonFormDex = getDex(CarbonForm.class);
        assertThat(tigerTimestamp).isLessThan(tigerDex.lastModified());
        assertThat(carbonFormTimestamp).isEqualTo(carbonFormDex.lastModified());

        dirInput =
                TransformTestHelper.directoryBuilder(input.toFile())
                        .putChangedFiles(getChangedStatusMap(input, CHANGED, Cat.class))
                        .build();
        invocation =
                TransformTestHelper.invocationBuilder()
                        .setIncremental(true)
                        .setTransformOutputProvider(outputProvider)
                        .addInput(dirInput)
                        .build();

        TestUtils.waitForFileSystemTick();
        getTransform(null).transform(invocation);
        tigerDex = getDex(Tiger.class);
        carbonFormDex = getDex(CarbonForm.class);
        assertThat(tigerTimestamp).isLessThan(tigerDex.lastModified());
        assertThat(carbonFormTimestamp).isEqualTo(carbonFormDex.lastModified());
    }

    @Test
    public void test_incremental_full_incremental()
            throws IOException, TransformException, InterruptedException {
        Path input = tmpDir.getRoot().toPath().resolve("input");
        TestInputsGenerator.pathWithClasses(input, ImmutableSet.of(CarbonForm.class, Animal.class));

        TransformInput dirInput =
                TransformTestHelper.directoryBuilder(input.toFile())
                        .putChangedFiles(
                                getChangedStatusMap(
                                        input, Status.ADDED, CarbonForm.class, Animal.class))
                        .build();
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setTransformOutputProvider(outputProvider)
                        .addInput(dirInput)
                        .setIncremental(true)
                        .build();
        getTransform(null).transform(invocation);
        File animalDex = getDex(Animal.class);

        dirInput = TransformTestHelper.directoryBuilder(input.toFile()).build();
        invocation =
                TransformTestHelper.invocationBuilder()
                        .setTransformOutputProvider(outputProvider)
                        .addInput(dirInput)
                        .setIncremental(false)
                        .build();
        getTransform(null).transform(invocation);

        dirInput =
                TransformTestHelper.directoryBuilder(input.toFile())
                        .putChangedFiles(getChangedStatusMap(input, Status.REMOVED, Animal.class))
                        .build();
        invocation =
                TransformTestHelper.invocationBuilder()
                        .setTransformOutputProvider(outputProvider)
                        .addInput(dirInput)
                        .setIncremental(true)
                        .build();
        getTransform(null).transform(invocation);
        assertThat(getDex(CarbonForm.class)).exists();
        assertThat(animalDex).doesNotExist();
    }

    @Test
    public void test_incremental_jarAndDir()
            throws IOException, TransformException, InterruptedException {
        Path jar = tmpDir.getRoot().toPath().resolve("input.jar");
        Path input = tmpDir.getRoot().toPath().resolve("input");
        TestInputsGenerator.pathWithClasses(jar, ImmutableSet.of(CarbonForm.class, Animal.class));
        TestInputsGenerator.pathWithClasses(
                input, ImmutableSet.of(Toy.class, Cat.class, Tiger.class));

        TransformInput dirInput = TransformTestHelper.directoryBuilder(input.toFile()).build();
        TransformInput jarInput = TransformTestHelper.singleJarBuilder(jar.toFile()).build();
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setTransformOutputProvider(outputProvider)
                        .addInput(dirInput)
                        .addInput(jarInput)
                        .setIncremental(false)
                        .build();
        getTransform(null).transform(invocation);
        long catTimestamp = getDex(Cat.class).lastModified();
        long toyTimestamp = getDex(Toy.class).lastModified();

        jarInput =
                TransformTestHelper.singleJarBuilder(jar.toFile())
                        .setStatus(Status.CHANGED)
                        .build();
        invocation =
                TransformTestHelper.invocationBuilder()
                        .setTransformOutputProvider(outputProvider)
                        .addInput(dirInput)
                        .addInput(jarInput)
                        .setIncremental(true)
                        .build();
        TestUtils.waitForFileSystemTick();
        getTransform(null).transform(invocation);

        assertThat(catTimestamp).isLessThan(getDex(Cat.class).lastModified());
        assertThat(toyTimestamp).isEqualTo(getDex(Toy.class).lastModified());
    }

    /** Regression test to make sure we do not add unchanged files to cache. */
    @Test
    public void test_incremental_notChangedNotAddedToCache() throws Exception {
        Path jar = tmpDir.getRoot().toPath().resolve("input.jar");
        TestInputsGenerator.pathWithClasses(jar, ImmutableSet.of(CarbonForm.class, Animal.class));

        TransformInput jarInput =
                TransformTestHelper.singleJarBuilder(jar.toFile())
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .build();
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setTransformOutputProvider(outputProvider)
                        .addInput(jarInput)
                        .setIncremental(false)
                        .build();
        FileCache cache = FileCache.getInstanceWithSingleProcessLocking(tmpDir.newFolder());
        getTransform(cache).transform(invocation);
        String[] numEntries = Objects.requireNonNull(cache.getCacheDirectory().list());

        File referencedJar = tmpDir.newFile("referenced.jar");
        TestInputsGenerator.jarWithEmptyClasses(referencedJar.toPath(), ImmutableList.of("A"));
        TransformInput referencedInput =
                TransformTestHelper.singleJarBuilder(referencedJar).build();

        jarInput =
                TransformTestHelper.singleJarBuilder(jar.toFile())
                        .setStatus(Status.NOTCHANGED)
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .build();
        invocation =
                TransformTestHelper.invocationBuilder()
                        .setTransformOutputProvider(outputProvider)
                        .addInput(jarInput)
                        .addReferenceInput(referencedInput)
                        .setIncremental(true)
                        .build();
        getTransform(cache).transform(invocation);
        assertThat(cache.getCacheDirectory().list()).named("cache entries").isEqualTo(numEntries);
    }

    @Test
    public void test_duplicateClasspathEntries() throws Exception {

        Path lib1 = tmpDir.getRoot().toPath().resolve("lib1.jar");
        TestInputsGenerator.pathWithClasses(
                lib1, ImmutableSet.of(ImplementsWithDefault.class, WithDefault.class));
        Path lib2 = tmpDir.getRoot().toPath().resolve("lib2.jar");
        TestInputsGenerator.pathWithClasses(lib2, ImmutableSet.of(WithDefault.class));
        Path app = tmpDir.getRoot().toPath().resolve("app");
        TestInputsGenerator.pathWithClasses(app, ImmutableSet.of(InvokesDefault.class));

        TransformInput lib1Input =
                TransformTestHelper.singleJarBuilder(lib1.toFile())
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .build();
        TransformInput lib2Input =
                TransformTestHelper.singleJarBuilder(lib2.toFile())
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .build();
        TransformInput appInput = TransformTestHelper.directoryBuilder(app.toFile()).build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setTransformOutputProvider(outputProvider)
                        .addInput(lib1Input)
                        .addInput(lib2Input)
                        .addInput(appInput)
                        .setIncremental(false)
                        .build();
        getTransform(null, 15, true, true).transform(invocation);

        MoreTruth.assertThatDex(getDex(InvokesDefault.class)).hasClassesCount(1);
    }

    /** Regression test for b/117062425. */
    @Test
    public void test_incrementalDesugaringWithCaching() throws Exception {
        Path lib1 = tmpDir.getRoot().toPath().resolve("lib1.jar");
        TestInputsGenerator.pathWithClasses(lib1, ImmutableSet.of(ImplementsWithDefault.class));
        Path lib2 = tmpDir.getRoot().toPath().resolve("lib2.jar");
        TestInputsGenerator.pathWithClasses(lib2, ImmutableSet.of(WithDefault.class));

        TransformInput lib1Input =
                TransformTestHelper.singleJarBuilder(lib1.toFile())
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .build();
        // Mimics dex that from cache for lib1.jar. Transform invocation should remove it.
        Files.createFile(out.resolve("lib1.jar.jar"));

        TransformInput lib2Input =
                TransformTestHelper.singleJarBuilder(lib2.toFile())
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .setStatus(Status.CHANGED)
                        .build();
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setTransformOutputProvider(outputProvider)
                        .addInput(lib1Input)
                        .addInput(lib2Input)
                        .setIncremental(true)
                        .build();
        getTransform(null, 15, true, true).transform(invocation);
        List<Path> lib1DexOutputs =
                Files.list(out)
                        .filter(p -> p.getFileName().toString().startsWith("lib1.jar"))
                        .collect(Collectors.toList());
        assertThat(lib1DexOutputs).hasSize(1);
    }

    @NonNull
    private DexArchiveBuilderTransform getTransform(
            @Nullable FileCache userCache,
            int minSdkVersion,
            boolean isDebuggable,
            boolean includeAndroidJar) {
        List<File> androidJasClasspath =
                includeAndroidJar
                        ? ImmutableList.of(TestUtils.getPlatformFile("android.jar"))
                        : ImmutableList.of();
        return new DexArchiveBuilderTransformBuilder()
                .setAndroidJarClasspath(() -> androidJasClasspath)
                .setDexOptions(new DefaultDexOptions())
                .setMessageReceiver(new NoOpMessageReceiver())
                .setUserLevelCache(userCache)
                .setMinSdkVersion(minSdkVersion)
                .setDexer(DexerTool.D8)
                .setUseGradleWorkers(false)
                .setInBufferSize(10)
                .setOutBufferSize(10)
                .setIsDebuggable(isDebuggable)
                .setJava8LangSupportType(VariantScope.Java8LangSupport.D8)
                .setProjectVariant("myVariant")
                .setIncludeFeaturesInScope(false)
                .setNumberOfBuckets(2)
                .createDexArchiveBuilderTransform();
    }

    @NonNull
    private DexArchiveBuilderTransform getTransform(@Nullable FileCache userCache) {
        return getTransform(userCache, 15, true, false);
    }

    @NonNull
    private File getDex(@NonNull Class<?> clazz) {
        return Iterables.getOnlyElement(
                FileUtils.find(
                        out.toFile(), Pattern.compile(".*" + clazz.getSimpleName() + "\\.dex")));
    }

    @NonNull
    private Map<File, Status> getChangedStatusMap(
            @NonNull Path root, @NonNull Status status, @NonNull Class<?>... classes) {
        Map<File, Status> statusMap = new HashMap<>();
        for (Class<?> clazz : classes) {
            statusMap.put(root.resolve(TestInputsGenerator.getPath(clazz)).toFile(), status);
        }
        return statusMap;
    }
}
