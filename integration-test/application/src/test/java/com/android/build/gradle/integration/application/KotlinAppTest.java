/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.build.gradle.integration.common.category.SmokeTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.builder.model.AndroidProject;
import com.android.testutils.apk.Apk;
import java.io.IOException;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Assemble tests for kotlin. */
@Category(SmokeTests.class)
public class KotlinAppTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("kotlinApp").create();

    @After
    public void cleanUp() {
        project = null;
    }

    @Test
    public void projectModel() throws IOException {
        ModelContainer<AndroidProject> models = project.model().fetchAndroidProjects();

        AndroidProject appModel = models.getOnlyModelMap().get(":app");

        assertThat(appModel.getProjectType())
                .named("Project Type")
                .isEqualTo(AndroidProject.PROJECT_TYPE_APP);
        assertThat(appModel.getCompileTarget())
                .named("Compile Target")
                .isEqualTo(GradleTestProject.getCompileSdkHash());
    }

    @Test
    public void apkContents() throws Exception {
        project.executor().run("clean", "app:assembleDebug");
        Apk apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).isNotNull();
        assertThat(apk).containsResource("layout/activity_layout.xml");
        assertThat(apk).containsResource("layout/lib_activity_layout.xml");
        assertThat(apk).containsMainClass("Lcom/example/android/kotlin/MainActivity;");
        assertThat(apk).containsMainClass("Lcom/example/android/kotlin/LibActivity;");
    }

}
