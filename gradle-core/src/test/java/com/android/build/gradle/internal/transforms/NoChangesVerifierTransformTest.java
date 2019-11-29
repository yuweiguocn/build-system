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

package com.android.build.gradle.internal.transforms;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;

import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.build.gradle.internal.pipeline.TransformManager;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Tests for the {@link NoChangesVerifierTransform} */
public class NoChangesVerifierTransformTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule().silent();

    @Mock InstantRunBuildContext buildContext;

    @Test
    public void testNonIncTransformInvocation()
            throws TransformException, InterruptedException, IOException {
        NoChangesVerifierTransform checker = new NoChangesVerifierTransform(
                "name", buildContext,
                TransformManager.CONTENT_CLASS,
                TransformManager.SCOPE_FULL_PROJECT,
                InstantRunVerifierStatus.DEPENDENCY_CHANGED);

        assertThat(checker.isIncremental()).isTrue();
        TransformInvocation transformInvocation =
                TransformTestHelper.invocationBuilder().setIncremental(false).build();

        checker.transform(transformInvocation);

        // make sure the verifier is not set by the constructor.
        ArgumentCaptor<InstantRunVerifierStatus> verifierStatusCaptor =
                ArgumentCaptor.forClass(InstantRunVerifierStatus.class);
        Mockito.verify(buildContext, times(1)).setVerifierStatus(verifierStatusCaptor.capture());

        assertThat(verifierStatusCaptor.getValue()).isEqualTo(
                InstantRunVerifierStatus.DEPENDENCY_CHANGED);
    }

    @Test
    public void testIncTransformInvocation()
            throws TransformException, InterruptedException, IOException {
        NoChangesVerifierTransform checker = new NoChangesVerifierTransform(
                "name", buildContext,
                TransformManager.CONTENT_CLASS,
                TransformManager.SCOPE_FULL_PROJECT,
                InstantRunVerifierStatus.DEPENDENCY_CHANGED);

        assertThat(checker.isIncremental()).isTrue();

        TransformInput jarInput =
                TransformTestHelper.singleJarBuilder(new File(""))
                        .setStatus(Status.ADDED)
                        .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .build();
        TransformInvocation transformInvocation =
                TransformTestHelper.invocationBuilder()
                        .setIncremental(true)
                        .addReferenceInput(jarInput)
                        .build();

        checker.transform(transformInvocation);

        // make sure the verifier is not set by the constructor.
        ArgumentCaptor<InstantRunVerifierStatus> verifierStatusCaptor =
                ArgumentCaptor.forClass(InstantRunVerifierStatus.class);
        Mockito.verify(buildContext, times(1)).setVerifierStatus(verifierStatusCaptor.capture());

        assertThat(verifierStatusCaptor.getValue()).isEqualTo(
                InstantRunVerifierStatus.DEPENDENCY_CHANGED);
    }

    @Test
    public void testIncTransformInvocationNoTypes()
            throws TransformException, InterruptedException, IOException {
        NoChangesVerifierTransform checker =
                new NoChangesVerifierTransform(
                        "name",
                        buildContext,
                        TransformManager.CONTENT_CLASS,
                        TransformManager.SCOPE_FULL_PROJECT,
                        InstantRunVerifierStatus.DEPENDENCY_CHANGED);

        assertThat(checker.isIncremental()).isTrue();

        TransformInput jarInput =
                TransformTestHelper.singleJarBuilder(new File(""))
                        .setContentTypes(QualifiedContent.DefaultContentType.RESOURCES)
                        .setStatus(Status.ADDED)
                        .build();
        TransformInvocation transformInvocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(jarInput)
                        .addReferenceInput(jarInput)
                        .setIncremental(true)
                        .build();

        checker.transform(transformInvocation);

        // make sure the verifier is not set by the constructor.
        ArgumentCaptor<InstantRunVerifierStatus> verifierStatusCaptor =
                ArgumentCaptor.forClass(InstantRunVerifierStatus.class);
        Mockito.verify(buildContext, times(0)).setVerifierStatus(verifierStatusCaptor.capture());
    }
}
