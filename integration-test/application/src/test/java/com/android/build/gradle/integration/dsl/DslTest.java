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

package com.android.build.gradle.integration.dsl;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.utils.XmlUtils;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;
import org.w3c.dom.Document;

/** General DSL tests */
public class DslTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Test
    public void versionNameSuffix() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    defaultConfig {\n"
                        + "        versionName 'foo'\n"
                        + "    }\n"
                        + "\n"
                        + "    buildTypes {\n"
                        + "        debug {\n"
                        + "            versionNameSuffix '-suffix'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        // no need to do a full build. Let's just run the manifest task.
        project.execute("processDebugManifest");

        File manifestFile =
                project.file("build/intermediates/merged_manifests/debug/AndroidManifest.xml");

        Document document =
                XmlUtils.parseDocument(
                        Files.asCharSource(manifestFile, StandardCharsets.UTF_8).read(), false);

        String versionName =
                document.getFirstChild()
                        .getAttributes()
                        .getNamedItem("android:versionName")
                        .getNodeValue();
        assertThat(versionName).named("version name in the manifest").isEqualTo("foo-suffix");
    }

    @Test
    public void extraPropTest() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    buildTypes {\n"
                        + "        debug {\n"
                        + "            ext.foo = \"bar\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    applicationVariants.all { variant ->\n"
                        + "        if (variant.buildType.name == \"debug\") {\n"
                        + "            def foo = variant.buildType.foo\n"
                        + "            if (!foo.equals(\"bar\")) {\n"
                        + "                throw new RuntimeException(\"direct access to dynamic property failed, got \" + foo)\n"
                        + "            }\n"
                        + "            def hasProperty = variant.buildType.hasProperty(\"foo\")\n"
                        + "            if (!hasProperty) {\n"
                        + "                throw new RuntimeException(\"hasProperty not returning property value, got \" + hasProperty)\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        // no need to do a full build. Let's just run the tasks.
        project.execute("tasks");
    }

    @Test
    public void buildConfigEncoding() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "  defaultConfig {\n"
                        + "    buildConfigField 'String', 'test2', '\"\\u0105\"'\n"
                        + "  }\n"
                        + "}\n");

        project.execute("generateDebugBuildConfig");

        String expected =
                "/**\n"
                        + " * Automatically generated file. DO NOT MODIFY\n"
                        + " */\n"
                        + "package com.example.helloworld;\n"
                        + "\n"
                        + "public final class BuildConfig {\n"
                        + "  public static final boolean DEBUG = Boolean.parseBoolean(\"true\");\n"
                        + "  public static final String APPLICATION_ID = \"com.example.helloworld\";\n"
                        + "  public static final String BUILD_TYPE = \"debug\";\n"
                        + "  public static final String FLAVOR = \"\";\n"
                        + "  public static final int VERSION_CODE = 1;\n"
                        + "  public static final String VERSION_NAME = \"1.0\";\n"
                        + "  // Fields from default config.\n"
                        + "  public static final String test2 = \"ą\";\n"
                        + "}\n";

        String actual =
                Files.asCharSource(
                                project.file(
                                        "build/generated/source/buildConfig/debug/com/example/helloworld/BuildConfig.java"),
                                StandardCharsets.UTF_8)
                        .read();

        assertThat(actual).named("BuildConfig.java").isEqualTo(expected);
    }

    @Test
    public void testValidateVersionCodes() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    defaultConfig {\n"
                        + "        versionCode 0\n"
                        + "    }\n"
                        + "}\n");
        final ModelContainer<AndroidProject> model =
                project.model().ignoreSyncIssues().fetchAndroidProjects();
        Collection<SyncIssue> syncIssues = model.getOnlyModel().getSyncIssues();
        assertThat(syncIssues).hasSize(1);
        assertThat(Iterables.getOnlyElement(syncIssues).getMessage())
                .contains("android.defaultConfig.versionCode is set to 0");

        TestFileUtils.searchAndReplace(project.getBuildFile(), "versionCode 0", "versionCode 1");
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    flavorDimensions \"color\"\n"
                        + "    productFlavors {\n"
                        + "        red {\n"
                        + "            versionCode = -1\n"
                        + "            dimension \"color\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        final ModelContainer<AndroidProject> flavoredModel =
                project.model().ignoreSyncIssues().fetchAndroidProjects();
        Collection<SyncIssue> flavoredSyncIssues = flavoredModel.getOnlyModel().getSyncIssues();
        assertThat(flavoredSyncIssues).hasSize(1);
        assertThat(Iterables.getOnlyElement(flavoredSyncIssues).getMessage())
                .contains("versionCode is set to -1 in product flavor red");
    }

    @Test
    public void testProjectConfigurationWithFlavorDimensionsButNoFlavors() throws Exception {
        // Regression test for http://issuetracker.google.com/117751390
        TestFileUtils.appendToFile(project.getBuildFile(), "android.flavorDimensions 'foo'");

        // Configuration phase should complete successfully
        project.executor().run("help");
    }
}
