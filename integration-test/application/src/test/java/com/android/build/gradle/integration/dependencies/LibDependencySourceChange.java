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

package com.android.build.gradle.integration.dependencies;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Test that change source files in a library and perform incremental builds. */
public class LibDependencySourceChange {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestApp(HelloWorldLibraryApp.create()).create();

    @BeforeClass
    public static void setUp() throws Exception {
        project.execute("clean", "assembleDebug");
    }

    @Test
    public void checkLibDependencyJarIsPackaged() throws Exception {
        replaceActivityClass();
        project.execute("assembleDebug");
        assertThat(project.getBuildResult().getException()).isNull();
    }

    private void replaceActivityClass() throws Exception {
        String javaCompile =
                "package com.example.helloworld;\n"
                        + "import android.app.Activity;\n"
                        + "import android.os.Bundle;\n"
                        + "import java.util.logging.Logger;\n"
                        + "\n"
                        + "import java.util.concurrent.Callable;\n"
                        + "\n"
                        + "public class HelloWorld extends Activity {\n"
                        + "    /** Called when the activity is first created. */\n"
                        + "    @Override\n"
                        + "    public void onCreate(Bundle savedInstanceState) {\n"
                        + "        super.onCreate(savedInstanceState);\n"
                        + "        setContentView(R.layout.main);\n"
                        + "        Callable<Void> callable = new Callable<Void>() {\n"
                        + "            @Override\n"
                        + "            public Void call() throws Exception {\n"
                        + "                Logger.getLogger(\"libDependencySourceChange\")\n"
                        + "                        .warning(\"Hello World !\");"
                        + "                return null;\n"
                        + "            }\n"
                        + "        };\n"
                        + "        try {\n"
                        + "            callable.call();\n"
                        + "        } catch (Exception e) {\n"
                        + "            throw new RuntimeException(e);\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n";
        Files.asCharSink(
                        project.getSubproject(":lib")
                                .file("src/main/java/com/example/helloworld/HelloWorld.java"),
                        Charsets.UTF_8)
                .write(javaCompile);
    }
}
