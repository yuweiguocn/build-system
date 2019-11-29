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

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification;
import com.android.build.gradle.options.BooleanOption;
import com.android.utils.FileUtils;
import org.junit.Rule;
import org.junit.Test;

/** Tests the error message rewriting logic. */
public class MessageRewriteTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("flavored").withoutNdk().create();

    @Test
    public void invalidLayoutFile() throws Exception {
        project.execute("assembleDebug");
        TemporaryProjectModification.doTest(
                project,
                it -> {
                    it.replaceInFile("src/main/res/layout/main.xml", "</LinearLayout>", "");
                    GradleBuildResult result =
                            project.executor()
                                    .with(BooleanOption.IDE_INVOKED_FROM_IDE, true)
                                    .expectFailure()
                                    .run("assembleF1Debug");
                    assertThat(result.getStdout())
                            .contains(FileUtils.join("src", "main", "res", "layout", "main.xml"));
                });

        project.execute("assembleDebug");
    }
}
