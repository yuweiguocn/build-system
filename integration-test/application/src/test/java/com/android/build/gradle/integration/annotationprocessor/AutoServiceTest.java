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

package com.android.build.gradle.integration.annotationprocessor;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AutoServiceTest {

    @Parameterized.Parameters(name = "{0}")
    public static List<String> plugin() {
        return ImmutableList.of("com.android.application", "com.android.library");
    }

    public AutoServiceTest(@NonNull String pluginName) {
        this.pluginName = pluginName;
        project =
                GradleTestProject.builder()
                        .fromTestApp(HelloWorldApp.forPlugin(pluginName))
                        .create();
    }

    @NonNull public final String pluginName;
    @NonNull @Rule public final GradleTestProject project;

    @Before
    public void addAutoService() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "dependencies {\n"
                        + "    provided 'com.google.auto.service:auto-service:1.0-rc2' \n"
                        + "    annotationProcessor 'com.google.auto.service:auto-service:1.0-rc2' \n"
                        + "}\n"
                        + "");

        Files.write(
                project.file("src/main/java/com/example/helloworld/MyService.java").toPath(),
                ImmutableList.of(
                        "package com.example.helloworld;",
                        "",
                        "public interface MyService {",
                        "    void doSomething();",
                        "}",
                        ""),
                StandardCharsets.UTF_8);

        Files.write(
                project.file("src/main/java/com/example/helloworld/MyServiceImpl.java").toPath(),
                ImmutableList.of(
                        "package com.example.helloworld;",
                        "",
                        "@com.google.auto.service.AutoService(MyService.class)",
                        "public class MyServiceImpl implements MyService {",
                        "    public void doSomething() {}",
                        "}",
                        ""),
                StandardCharsets.UTF_8);
    }

    @Test
    public void checkAutoServiceResourceIncluded() throws Exception {
        project.executor().run("assembleDebug");
        if (pluginName.equals("com.android.application")) {
            assertThat(project.getApk(GradleTestProject.ApkType.DEBUG))
                    .containsJavaResource("META-INF/services/com.example.helloworld.MyService");
        } else {
            assertThat(project.getAar("debug"))
                    .containsJavaResource("META-INF/services/com.example.helloworld.MyService");
        }
    }
}
