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

import static com.android.testutils.truth.PathSubject.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.InternalScope;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.merge.DuplicateRelativeFileException;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.google.common.collect.ImmutableSet;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Test cases for {@link MergeJavaResourcesTransform}. */
public class MergeJavaResourcesTransformTest {

    @Rule public TemporaryFolder tmpDir = new TemporaryFolder();

    @Mock VariantScope variantScope;

    private File outputDir;

    private TestTransformOutputProvider outputProvider;

    @Before
    public void setUpMock() throws IOException {
        MockitoAnnotations.initMocks(this);

        File incrementalDir = tmpDir.newFolder("incremental");
        when(variantScope.getIncrementalDir(any())).thenReturn(incrementalDir);
        outputDir = tmpDir.newFolder("outputDir");
        outputProvider = new TestTransformOutputProvider(outputDir.toPath());
    }

    @Test
    public void testMergeResources() throws Exception {
        // Create a jar file containing resources
        File jarFile = new File(tmpDir.getRoot(), "foo.jar");
        try (ZFile zf = ZFile.openReadWrite(jarFile)) {
            zf.add("fileEndingWithDot.", new ByteArrayInputStream(new byte[0]));
            zf.add("fileNotEndingWithDot", new ByteArrayInputStream(new byte[0]));
        }

        MergeJavaResourcesTransform transform =
                new MergeJavaResourcesTransform(
                        new PackagingOptions(),
                        TransformManager.SCOPE_FULL_PROJECT,
                        QualifiedContent.DefaultContentType.RESOURCES,
                        "mergeJavaRes",
                        variantScope);
        JarInput jarInput =
                TransformTestHelper.jarBuilder(jarFile)
                        .setStatus(Status.ADDED)
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .build();
        TransformInput jarTransformInput =
                TransformTestHelper.inputBuilder().addInput(jarInput).build();
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setInputs(ImmutableSet.of(jarTransformInput))
                        .addReferenceInput(jarTransformInput)
                        .setTransformOutputProvider(outputProvider)
                        .build();

        transform.transform(invocation);

        // Make sure the output is a jar file, not the contents of the jar file
        assertThat(new File(outputDir, "resources.jar")).exists();
        assertThat(new File(outputDir, "resources/fileEndingWithDot.")).doesNotExist();
        assertThat(new File(outputDir, "resources/fileNotEndingWithDot")).doesNotExist();
    }

    @Test(expected = DuplicateRelativeFileException.class)
    public void testErrorWhenDuplicateJavaResInFeature() throws Exception {
        // Create a jar file containing resources
        File jarFile = new File(tmpDir.getRoot(), "foo.jar");
        try (ZFile zf = ZFile.openReadWrite(jarFile)) {
            zf.add("foo.txt", new ByteArrayInputStream(new byte[0]));
        }
        MergeJavaResourcesTransform transform =
                new MergeJavaResourcesTransform(
                        new PackagingOptions(),
                        TransformManager.SCOPE_FULL_WITH_FEATURES,
                        QualifiedContent.DefaultContentType.RESOURCES,
                        "mergeJavaRes",
                        variantScope);
        JarInput baseJarInput =
                TransformTestHelper.jarBuilder(jarFile)
                        .setStatus(Status.ADDED)
                        .setScopes(QualifiedContent.Scope.PROJECT)
                        .build();
        JarInput featureJarInput =
                TransformTestHelper.jarBuilder(jarFile)
                        .setStatus(Status.ADDED)
                        .setScopes(InternalScope.FEATURES)
                        .build();
        TransformInput jarTransformInput =
                TransformTestHelper.inputBuilder()
                        .addInput(baseJarInput)
                        .addInput(featureJarInput)
                        .build();
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setInputs(ImmutableSet.of(jarTransformInput))
                        .addReferenceInput(jarTransformInput)
                        .setTransformOutputProvider(outputProvider)
                        .build();
        transform.transform(invocation);
    }
}
