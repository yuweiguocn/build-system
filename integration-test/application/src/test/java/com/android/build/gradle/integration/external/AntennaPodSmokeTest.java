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

package com.android.build.gradle.integration.external;


import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.ModelContainerUtils;
import com.android.build.gradle.integration.common.utils.PerformanceTestProjects;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.AndroidProject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

// b/117786329
@Ignore // Disable until discussed at gradle-team meeting.
@RunWith(FilterableParameterized.class)
public class AntennaPodSmokeTest {

    @Parameterized.Parameter public VariantScope.Java8LangSupport java8LangSupport;

    @Parameterized.Parameters(name = "{0}")
    public static List<VariantScope.Java8LangSupport> getJava8LangSupport() {
        return ImmutableList.of(
                //VariantScope.Java8LangSupport.RETROLAMBDA, // issuetracker.google.com/63940887
                VariantScope.Java8LangSupport.DESUGAR);
    }

    @Rule
    public GradleTestProject mainProject =
            GradleTestProject.builder().fromExternalProject("AntennaPod").create();

    private GradleTestProject project;

    @Before
    public void setUp() throws IOException {
        project = mainProject.getSubproject("AntennaPod");
        PerformanceTestProjects.initializeAntennaPod(mainProject);
        if (java8LangSupport == VariantScope.Java8LangSupport.RETROLAMBDA) {
            PerformanceTestProjects.antennaPodSetRetrolambdaEnabled(mainProject, true);
        }
    }

    @Test
    @Ignore("b/117306698")
    public void buildAntennaPod() throws Exception {
        ModelContainer<AndroidProject> modelContainer =
                project.model().ignoreSyncIssues().fetchAndroidProjects();
        Map<String, AndroidProject> models = modelContainer.getOnlyModelMap();
        PerformanceTestProjects.assertNoSyncErrors(models);

        project.executor().run("clean");

        project.executor()
                .with(BooleanOption.IDE_GENERATE_SOURCES_ONLY, true)
                .run(ModelContainerUtils.getDebugGenerateSourcesCommands(modelContainer));

        if (java8LangSupport == VariantScope.Java8LangSupport.RETROLAMBDA) {
            project.executor()
                    .with(BooleanOption.ENABLE_EXTRACT_ANNOTATIONS, false)
                    .run(":app:assembleDebug");
        } else {
            project.executor().run(":app:assembleDebug");
            // Retrolambda is currently broken when building tests from clean
            project.executor().run("clean");
            project.executor().run(":app:assembleDebugAndroidTest");
        }

    }

}
