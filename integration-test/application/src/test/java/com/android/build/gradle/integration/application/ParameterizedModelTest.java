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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ParameterizedAndroidProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import java.util.stream.Collectors;
import org.gradle.tooling.BuildActionFailureException;
import org.hamcrest.core.IsInstanceOf;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/* Tests for getting builder models with parameters. */
public class ParameterizedModelTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Rule public ExpectedException exception = ExpectedException.none();

    @Test
    public void getVariantModelWithNonParameterizedAPI() throws Exception {
        exception.expect(BuildActionFailureException.class);
        exception.expectCause(IsInstanceOf.instanceOf(RuntimeException.class));
        // Get Variant model with non-parameterized API.
        project.model().fetch(Variant.class);
    }

    @Test
    public void getParameterizedAndroidProject() throws Exception {
        // Get AndroidProject with parameterized API.
        ParameterizedAndroidProject parameterizedAndroidProject =
                project.model().fetch(ParameterizedAndroidProject.class);
        AndroidProject androidProject = parameterizedAndroidProject.getAndroidProject();

        // Verify that AndroidProject model doesn't contain Variant instance.
        assertThat(androidProject.getVariants()).isEmpty();
        // Verify that AndroidProject model contains a list of variant names.
        assertThat(androidProject.getVariantNames()).containsExactly("debug", "release");
        // Verify that Variant models have the correct name.
        assertThat(
                        parameterizedAndroidProject
                                .getVariants()
                                .stream()
                                .map(Variant::getName)
                                .collect(Collectors.toList()))
                .containsExactly("debug", "release");
    }
}
