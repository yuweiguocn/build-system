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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for filteredOutBuildType. */
public class FilteredOutBuildTypeTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("filteredOutBuildType").create();

    @Test
    public void checkFilteredOutVariantIsNotInModel() throws Exception {
        AndroidProject model = project.executeAndReturnModel("clean", "assemble").getOnlyModel();
        assertThat(project.model().fetchTaskList()).doesNotContain("assembleDebug");
        // Load the custom model for the project
        assertEquals("Variant Count", 1, model.getVariants().size());
        Variant variant = model.getVariants().iterator().next();
        assertEquals("Variant name", "release", variant.getBuildType());
    }
}
