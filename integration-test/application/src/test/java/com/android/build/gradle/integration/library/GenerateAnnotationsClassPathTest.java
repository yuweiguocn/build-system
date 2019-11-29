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

package com.android.build.gradle.integration.library;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.File;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class GenerateAnnotationsClassPathTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("extractAnnotations").create();

    @BeforeClass
    public static void setUpProject() throws IOException {
        File use =
                project.file("src/main/java/com/android/tests/extractannotations/HelloWorld.java");

        use.getParentFile().mkdirs();

        TestFileUtils.appendToFile(
                use,
                "\n"
                        + "import com.example.helloworld.GeneratedClass;\n"
                        + "\n"
                        + "public class HelloWorld {\n"
                        + "\n"
                        + "    public void go() {\n"
                        + "        GeneratedClass genC = new GeneratedClass();\n"
                        + "        genC.method();\n"
                        + "    }\n"
                        + "}");

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "import com.google.common.base.Joiner\n"
                        + "\n"
                        + "android.libraryVariants.all { variant ->\n"
                        + "    def outDir = project.file(\"$project.buildDir/generated/source/testplugin/$variant.name\");\n"
                        + "        def task = project.task(\n"
                        + "                \"generateJavaFromPlugin${variant.name.capitalize()}\",\n"
                        + "                dependsOn: [variant.mergeResources],\n"
                        + "                type: JavaGeneratingTask) {\n"
                        + "            outputDirectory = outDir\n"
                        + "        }\n"
                        + "        variant.registerJavaGeneratingTask(task, outDir)\n"
                        + "}\n"
                        + "\n"
                        + "android.testVariants.all { variant ->\n"
                        + "    def outDir = project.file(\"$project.buildDir/generated/source/testplugin/$variant.name\");\n"
                        + "        def task = project.task(\n"
                        + "                \"generateJavaFromPlugin${variant.name.capitalize()}\",\n"
                        + "                type: JavaGeneratingTask) {\n"
                        + "            suffix = \"AndroidTest\"\n"
                        + "            outputDirectory = outDir\n"
                        + "        }\n"
                        + "        variant.registerJavaGeneratingTask(task, outDir)\n"
                        + "}\n"
                        + "\n"
                        + "public class JavaGeneratingTask extends DefaultTask {\n"
                        + "    @Input\n"
                        + "    String suffix = \"\";\n"
                        + "\n"
                        + "    @OutputDirectory\n"
                        + "    File outputDirectory\n"
                        + "\n"
                        + "    @TaskAction\n"
                        + "    void execute(IncrementalTaskInputs inputs) {\n"
                        + "        System.err.println(\"Plugin executed on \" + inputs)\n"
                        + "        File outputFile = new File(outputDirectory, Joiner.on(File.separatorChar).join(\n"
                        + "                \"com\", \"example\", \"helloworld\", \"GeneratedClass${suffix}.java\"))\n"
                        + "        System.err.println(\"creating file \" + outputFile)\n"
                        + "        if (outputFile.exists()) {\n"
                        + "            outputFile.delete()\n"
                        + "        }\n"
                        + "        outputFile.getParentFile().mkdirs()\n"
                        + "\n"
                        + "        outputFile << \"\"\"\n"
                        + "package com.example.helloworld;\n"
                        + "\n"
                        + "public class GeneratedClass${suffix} {\n"
                        + "    public void method() {\n"
                        + "        System.out.println(\"Executed generated method\");\n"
                        + "    }\n"
                        + "}\n"
                        + "    \"\"\"\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "\n"
                        + "");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    /**
     * Check variant.registerJavaGeneratingTask() adds output directory to the class path of the
     * generate annotations task
     */
    @Test
    public void checkJavaGeneratingTaskAddsOutputDirToGenerateAnnotationsClasspath()
            throws IOException, InterruptedException {
        project.execute("clean", "assembleDebug");
        assertFalse(
                "Extract annotation should get generated class on path.",
                project.getBuildResult()
                        .getStdout()
                        .contains("Not extracting annotations (compilation problems encountered)"));
        assertThat(
                        project.file(
                                "build/generated/source/testplugin/debug/com/example/helloworld/GeneratedClass.java"))
                .exists();
    }

    @Test
    public void checkGeneratingJavaClassWorksForTestVariant()
            throws IOException, InterruptedException {
        project.execute("compileDebugAndroidTestSource");
        assertThat(
                        project.file(
                                "build/generated/source/testplugin/debugAndroidTest/com/example/helloworld/GeneratedClassAndroidTest.java"))
                .exists();
    }
}
