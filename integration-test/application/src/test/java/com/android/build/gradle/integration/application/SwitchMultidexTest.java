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
import static com.android.testutils.truth.MoreTruth.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for multiDex. */
public class SwitchMultidexTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Before
    public void injectMultiDex() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion Integer.parseInt(property('inject.minsdk')) \n"
                        + "        multiDexEnabled Boolean.valueOf(property('inject.multidex'))\n"
                        + "        multiDexKeepFile = project.file('keep.txt')\n"
                        + "    }\n"
                        + "}\n"
                        + "\n");
        Files.write(
                project.file("keep.txt").toPath(),
                ImmutableList.of("com/example/helloworld/HelloWorld.class"));
    }

    @Before
    public void addClasses() throws IOException {
        ImmutableList.Builder<String> methodsBuilder = ImmutableList.builder();
        for (int i = 0; i < 65536 / 2 - 1; i++) {
            methodsBuilder.add("    public void m" + i + "() {}");
        }
        ImmutableList<String> methods = methodsBuilder.build();

        for (int i = 0; i < 2; i++) {
            ImmutableList.Builder<String> lines = ImmutableList.builder();
            lines.add("package com.example.helloworld;", "", "public class A" + i + " {");
            lines.addAll(methods);
            lines.add("}");

            Files.write(
                    project.file("src/main/java/com/example/helloworld/A" + i + ".java").toPath(),
                    lines.build());
        }
    }

    @Test
    public void testSwitchingMultidexModes() throws Exception {
        nativeMultidex();
        legacyMultidex();
        nativeMultidex();
    }

    private void legacyMultidex() throws Exception {
        project.executor()
                .withProperty("inject.minsdk", "19")
                .withProperty("inject.multidex", "true")
                .run("assembleDebug");
        Apk debug = project.getApk("debug");
        assertTrue(debug.getMainDexFile().isPresent());
        assertThat(debug.getMainDexFile().get())
                .containsExactlyClassesIn(
                        ImmutableList.of(
                                "Landroid/support/multidex/MultiDex$V14;",
                                "Landroid/support/multidex/MultiDex$V19;",
                                "Landroid/support/multidex/MultiDex$V4;",
                                "Landroid/support/multidex/MultiDex;",
                                "Landroid/support/multidex/MultiDexApplication;",
                                "Landroid/support/multidex/MultiDexExtractor$1;",
                                "Landroid/support/multidex/MultiDexExtractor$ExtractedDex;",
                                "Landroid/support/multidex/MultiDexExtractor;",
                                "Landroid/support/multidex/ZipUtil$CentralDirectory;",
                                "Landroid/support/multidex/ZipUtil;",
                                "Lcom/example/helloworld/HelloWorld;"));

        Set<String> secondaryClasses = Sets.newHashSet();
        for (Dex dex : debug.getSecondaryDexFiles()) {
            for (String c : dex.getClasses().keySet()) {
                if (!secondaryClasses.add(c)) {
                    fail("Duplicate classes found in secondary dex files.");
                }
            }
        }

        assertThat(secondaryClasses)
                .containsExactly(
                        "Landroid/support/multidex/BuildConfig;",
                        "Landroid/support/multidex/R;",
                        "Lcom/example/helloworld/A0;",
                        "Lcom/example/helloworld/A1;",
                        "Lcom/example/helloworld/BuildConfig;",
                        "Lcom/example/helloworld/R$id;",
                        "Lcom/example/helloworld/R$layout;",
                        "Lcom/example/helloworld/R$string;",
                        "Lcom/example/helloworld/R;");
    }

    private void nativeMultidex() throws IOException, InterruptedException {
        project.executor()
                .withProperty("inject.minsdk", "21")
                .withProperty("inject.multidex", "true")
                .run("assembleDebug");
        Apk debug = project.getApk("debug");
        assertThat(debug.getAllDexes()).hasSize(2);

        Set<String> classes = debug.getAllDexes().get(0).getClasses().keySet();
        Set<String> classes2 = debug.getAllDexes().get(1).getClasses().keySet();
        assertThat(classes2).named("No duplicate class definitions").containsNoneIn(classes);

        assertThat(Sets.union(classes, classes2))
                .containsExactly(
                        "Lcom/example/helloworld/A0;",
                        "Lcom/example/helloworld/A1;",
                        "Lcom/example/helloworld/BuildConfig;",
                        "Lcom/example/helloworld/HelloWorld;",
                        "Lcom/example/helloworld/R$id;",
                        "Lcom/example/helloworld/R$layout;",
                        "Lcom/example/helloworld/R$string;",
                        "Lcom/example/helloworld/R;");
    }
}
