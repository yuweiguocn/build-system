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
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.testutils.apk.Aar;
import com.google.common.truth.Truth;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Integration test for extracting annotations.
 *
 * <p>Tip: To execute just this test after modifying the annotations extraction code:
 *
 * <pre>
 *     $ cd tools
 *     $ ./gradlew :base:build-system:integration-test:application:test -D:base:build-system:integration-test:application:test.single=ExtractAnnotationTest
 * </pre>
 */
public class ExtractAnnotationTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("extractAnnotations").create();

    @Before
    public void setUp() throws Exception {
        project.execute("clean", "assembleDebug");
    }

    @Test
    public void checkExtractAnnotation() throws Exception {
        Aar debugAar = project.getAar("debug");

        //noinspection SpellCheckingInspection
        String expectedContent =
                (""
                        + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<root>\n"
                        + "  <item name=\"com.android.tests.extractannotations.ExtractTest int getVisibility()\">\n"
                        + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                        + "      <val name=\"value\" val=\"{com.android.tests.extractannotations.ExtractTest.VISIBLE, com.android.tests.extractannotations.ExtractTest.INVISIBLE, com.android.tests.extractannotations.ExtractTest.GONE, 5, 17, com.android.tests.extractannotations.Constants.CONSTANT_1}\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "  <item name=\"com.android.tests.extractannotations.ExtractTest java.lang.String getStringMode(int)\">\n"
                        + "    <annotation name=\"android.support.annotation.StringDef\">\n"
                        + "      <val name=\"value\" val=\"{com.android.tests.extractannotations.ExtractTest.STRING_1, com.android.tests.extractannotations.ExtractTest.STRING_2, &quot;literalValue&quot;, &quot;concatenated&quot;}\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "  <item name=\"com.android.tests.extractannotations.ExtractTest java.lang.String getStringMode(int) 0\">\n"
                        + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                        + "      <val name=\"value\" val=\"{com.android.tests.extractannotations.ExtractTest.VISIBLE, com.android.tests.extractannotations.ExtractTest.INVISIBLE, com.android.tests.extractannotations.ExtractTest.GONE, 5, 17, com.android.tests.extractannotations.Constants.CONSTANT_1}\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "  <item name=\"com.android.tests.extractannotations.ExtractTest void checkForeignTypeDef(int) 0\">\n"
                        + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                        + "      <val name=\"flag\" val=\"true\" />\n"
                        + "      <val name=\"value\" val=\"{com.android.tests.extractannotations.Constants.CONSTANT_1, com.android.tests.extractannotations.Constants.CONSTANT_2}\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "  <item name=\"com.android.tests.extractannotations.ExtractTest void testMask(int) 0\">\n"
                        + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                        + "      <val name=\"flag\" val=\"true\" />\n"
                        + "      <val name=\"value\" val=\"{0, com.android.tests.extractannotations.Constants.FLAG_VALUE_1, com.android.tests.extractannotations.Constants.FLAG_VALUE_2}\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "  <item name=\"com.android.tests.extractannotations.ExtractTest void testNonMask(int) 0\">\n"
                        + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                        + "      <val name=\"flag\" val=\"false\" />\n"
                        + "      <val name=\"value\" val=\"{0, com.android.tests.extractannotations.Constants.CONSTANT_1, com.android.tests.extractannotations.Constants.CONSTANT_3}\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "  <item name=\"com.android.tests.extractannotations.ExtractTest.StringMode\">\n"
                        + "    <annotation name=\"android.support.annotation.StringDef\">\n"
                        + "      <val name=\"value\" val=\"{com.android.tests.extractannotations.ExtractTest.STRING_1, com.android.tests.extractannotations.ExtractTest.STRING_2, &quot;literalValue&quot;, &quot;concatenated&quot;}\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "  <item name=\"com.android.tests.extractannotations.ExtractTest.Visibility\">\n"
                        + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                        + "      <val name=\"value\" val=\"{com.android.tests.extractannotations.ExtractTest.VISIBLE, com.android.tests.extractannotations.ExtractTest.INVISIBLE, com.android.tests.extractannotations.ExtractTest.GONE, 5, 17, com.android.tests.extractannotations.Constants.CONSTANT_1}\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "  <item name=\"com.android.tests.extractannotations.TopLevelTypeDef\">\n"
                        + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                        + "      <val name=\"flag\" val=\"true\" />\n"
                        + "      <val name=\"value\" val=\"{com.android.tests.extractannotations.Constants.CONSTANT_1, com.android.tests.extractannotations.Constants.CONSTANT_2}\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "</root>\n");

        // check the resulting .aar file to ensure annotations.zip inclusion.
        assertThat(debugAar).contains("annotations.zip");

        assertThat(debugAar.getEntryAsZip("annotations.zip"))
                .containsFileWithContent(
                        "com/android/tests/extractannotations/annotations.xml", expectedContent);

        // Check typedefs removals:

        // public typedef: should be present
        assertThat(debugAar)
                .containsClass("Lcom/android/tests/extractannotations/ExtractTest$Visibility;");

        // private/protected typedefs: should have been removed
        assertThat(debugAar)
                .doesNotContainClass("Lcom/android/tests/extractannotations/ExtractTest$Mask;");
        assertThat(debugAar)
                .doesNotContainClass(
                        "Lcom/android/tests/extractannotations/ExtractTest$NonMaskType;");

        assertThat(debugAar)
                .containsClass("Lcom/android/tests/extractannotations/ExtractTest$StringMode;");

        // Make sure the NonMask symbol (from a private typedef) is completely gone from the
        // outer class
        assertThat(debugAar.getEntryAsZip("classes.jar"))
                .containsFileWithoutContent(
                        "com/android/tests/extractannotations/ExtractTest.class", "NonMaskType");

        GradleBuildResult result = project.executor().run("assembleDebug");

        Truth.assertThat(result.getDidWorkTasks()).isEmpty();

        // Make sure that annotations.zip contains no timestamps (for making it binary identical on every build)
        assertThat(
                        Files.readAttributes(
                                        debugAar.getEntryAsZip("annotations.zip")
                                                .getEntry(
                                                        "com/android/tests/extractannotations/annotations.xml"),
                                        BasicFileAttributes.class)
                                .lastModifiedTime()
                                .toMillis())
                .isEqualTo(0L);
    }
}
