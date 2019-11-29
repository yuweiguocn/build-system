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
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;

public class DexArchivesKotlinTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.application"))
                    .create();

    /** Regression test for http://b/65363841. */
    @Test
    public void testIncrementalDexing() throws IOException, InterruptedException {
        String javaContent = "package com.example.helloworld;\n" + "public class MyClass {}";
        File srcToRemove =
                FileUtils.join(project.getMainSrcDir(), "com/example/helloworld/MyClass.java");
        FileUtils.mkdirs(srcToRemove.getParentFile());
        Files.write(srcToRemove.toPath(), javaContent.getBytes(Charsets.UTF_8));
        project.executor().run("assembleDebug");

        FileUtils.delete(srcToRemove);
        String kotlinContent = "package com.example.helloworld;\n class MyClass";
        File kotlinSrc =
                FileUtils.join(project.getMainSrcDir(), "com/example/helloworld/MyClass.kt");
        Files.write(kotlinSrc.toPath(), kotlinContent.getBytes(Charsets.UTF_8));
        project.executor().run("assembleDebug");
    }
}
