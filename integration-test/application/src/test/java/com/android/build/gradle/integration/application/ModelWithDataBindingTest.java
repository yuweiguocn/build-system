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

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;

/** Sanity test that makes sure we add data binding gen sources into the model if it is enabled */
public class ModelWithDataBindingTest {
    private static final String DB_CLASS_PATH =
            "build/generated/data_binding_base_class_source_out"
                    + "/debug/dataBindingGenBaseClassesDebug/out";
    @Rule public GradleTestProject project;

    public ModelWithDataBindingTest() {
        project = GradleTestProject.builder().fromTestProject("databindingIncremental").create();
    }

    @Test
    public void enableV2() throws IOException {
        AndroidArtifact debugArtifact = createArtifact(true);

        assertThat(debugArtifact.getGeneratedSourceFolders()).contains(project.file(DB_CLASS_PATH));
    }

    @Test
    public void disableV2() throws IOException {
        AndroidArtifact debugArtifact = createArtifact(false);

        assertThat(debugArtifact.getGeneratedSourceFolders())
                .doesNotContain(project.file(DB_CLASS_PATH));
    }

    @NonNull
    private AndroidArtifact createArtifact(boolean enableV2) throws IOException {
        AndroidProject model =
                project.model()
                        .with(BooleanOption.ENABLE_DATA_BINDING_V2, enableV2)
                        .ignoreSyncIssues()
                        .fetchAndroidProjects()
                        .getOnlyModel();
        return AndroidProjectUtils.getVariantByName(model, "debug").getMainArtifact();
    }
}
