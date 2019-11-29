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

package com.android.build.gradle.integration.databinding;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.testutils.truth.DexSubject;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(FilterableParameterized.class)
public class DataBindingWithDaggerTest {
    private static final String MAIN_ACTIVITY_BINDING_CLASS =
            "Landroid/databinding/testapp/databinding/ActivityMainBinding;";
    private static final String DAGGER_APP_COMPONENT =
            "Landroid/databinding/testapp/DaggerAppComponent;";

    @Rule
    public GradleTestProject project;

    private final String buildSuffix;

    @Parameterized.Parameters(name = "specify_processor_class_{0}")
    public static List<Object[]> parameters() {
        return Arrays.asList(
                new Object[][] {
                    {false}, {true},
                });
    }

    public DataBindingWithDaggerTest(boolean specifyProcessor) {
        if (specifyProcessor) {
            buildSuffix = ".specifyprocessor.gradle";
        } else {
            buildSuffix = ".gradle";
        }

        project = GradleTestProject.builder()
                .fromTestProject("databindingAndDagger")
                .create();
    }

    @Test
    public void testApp() throws Exception {
        project.setBuildFile("build" + buildSuffix);
        project.execute("assembleDebug");

        DexSubject mainDex = assertThat(project.getApk("debug")).hasMainDexFile().that();
        mainDex.containsClass(MAIN_ACTIVITY_BINDING_CLASS);
        mainDex.containsClass(DAGGER_APP_COMPONENT);
    }
}
