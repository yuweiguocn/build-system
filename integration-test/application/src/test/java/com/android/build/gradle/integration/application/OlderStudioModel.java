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

package com.android.build.gradle.integration.application;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.builder.model.AndroidProject;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/** Tests for incremental dexing using dex archives. */
public class OlderStudioModel {

    public static final String THIS_GRADLE_PLUGIN_REQUIRES_STUDIO_3_0_MINIMUM =
            "This Gradle plugin requires a newer IDE able to request IDE model level 3. For Android Studio this means version 3.0+";

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Rule public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testModelQueryFromOlderBuild() throws Exception {
        // create the actual cause since the tooling API wraps the thrown exception into
        // something else.
        thrown.expectCause(
                new BaseMatcher<Throwable>() {
                    @Override
                    public boolean matches(Object item) {
                        return item instanceof RuntimeException
                                && ((RuntimeException) item)
                                        .getMessage()
                                        .equals(THIS_GRADLE_PLUGIN_REQUIRES_STUDIO_3_0_MINIMUM);
                    }

                    @Override
                    public void describeMismatch(Object item, Description mismatchDescription) {
                        if (!(item instanceof RuntimeException)) {
                            mismatchDescription
                                    .appendText("class was ")
                                    .appendValue(item.getClass().getName());
                        }

                        final String message = ((RuntimeException) item).getMessage();
                        if (!message.equals(THIS_GRADLE_PLUGIN_REQUIRES_STUDIO_3_0_MINIMUM)) {
                            mismatchDescription.appendText("msg was ").appendValue(message);
                        }
                    }

                    @Override
                    public void describeTo(Description description) {
                        description.appendValue(
                                "RuntimeException("
                                        + THIS_GRADLE_PLUGIN_REQUIRES_STUDIO_3_0_MINIMUM
                                        + ")");
                    }
                });


        // now call the method that will throw
        project.model()
                .level(AndroidProject.MODEL_LEVEL_1_SYNC_ISSUE)
                .fetchAndroidProjects()
                .getOnlyModel();
    }
}
