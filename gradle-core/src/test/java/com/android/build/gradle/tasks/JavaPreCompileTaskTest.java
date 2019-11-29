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

package com.android.build.gradle.tasks;

import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.dsl.AnnotationProcessorOptions;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JavaPreCompileTaskTest {
    @ClassRule public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final String testProcessorName = "com.google.test.MyAnnotationProcessor";

    private static final String dependencyWithProcessorJar = "dependencyWithProcessor.jar";

    private static File nonJarFile;
    private static File jar;
    private static File jarWithAnnotationProcessor;
    private static File directory;
    private static File directoryWithAnnotationProcessor;

    private Project project;
    private Configuration processorConfiguration;
    private Configuration compileConfiguration;
    private JavaPreCompileTask task;
    private File outputFile;

    @BeforeClass
    public static void classSetUp() throws IOException {
        directory = temporaryFolder.newFolder();
        directoryWithAnnotationProcessor = temporaryFolder.newFolder();
        File processorMetaInfFile =
                new File(
                        directoryWithAnnotationProcessor,
                        JavaCompileUtils.ANNOTATION_PROCESSORS_INDICATOR_FILE);
        Files.createParentDirs(processorMetaInfFile);
        assertThat(processorMetaInfFile.createNewFile()).isTrue();

        nonJarFile = temporaryFolder.newFile("notJar.txt");
        Files.asCharSink(nonJarFile, Charsets.UTF_8).write("This is not a jar file");

        jar = temporaryFolder.newFile("dependency.jar");
        ZipOutputStream jarOut = new ZipOutputStream(new FileOutputStream(jar));
        jarOut.close();

        jarWithAnnotationProcessor = temporaryFolder.newFile(dependencyWithProcessorJar);
        try (ZipOutputStream out =
                new ZipOutputStream(new FileOutputStream(jarWithAnnotationProcessor))) {
            out.putNextEntry(new ZipEntry(JavaCompileUtils.ANNOTATION_PROCESSORS_INDICATOR_FILE));
            out.write(testProcessorName.getBytes());
            out.closeEntry();
        }
    }

    @Before
    public void setUp() throws IOException {
        File testDir = temporaryFolder.newFolder();
        outputFile = temporaryFolder.newFile();
        project = ProjectBuilder.builder().withProjectDir(testDir).build();
        task = project.getTasks().create("test", JavaPreCompileTask.class);
        processorConfiguration = project.getConfigurations().create("annotationProcessor");
        compileConfiguration = project.getConfigurations().create("api");
    }

    @Test
    public void checkSuccessForNormalJar() throws IOException {
        project.getDependencies().add("api", project.files(jar, nonJarFile, directory));
        task.init(
                outputFile,
                "annotationProcessor",
                processorConfiguration.getIncoming().getArtifacts(),
                compileConfiguration.getIncoming().getArtifacts(),
                new AnnotationProcessorOptions(),
                false);

        task.preCompile();

        assertThat(getProcessorNames()).isEmpty();
    }

    @Test
    public void checkSuccessWithAnnotationProcessor() throws IOException {
        project.getDependencies()
                .add(
                        "annotationProcessor",
                        project.files(
                                jarWithAnnotationProcessor, directoryWithAnnotationProcessor));
        project.getDependencies()
                .add(
                        "api",
                        project.files(
                                jarWithAnnotationProcessor, directoryWithAnnotationProcessor));
        task.init(
                outputFile,
                "annotationProcessor",
                processorConfiguration.getIncoming().getArtifacts(),
                compileConfiguration.getIncoming().getArtifacts(),
                new AnnotationProcessorOptions(),
                false);

        task.preCompile();

        assertThat(getProcessorNames())
                .containsExactly(
                        jarWithAnnotationProcessor.getName(),
                        directoryWithAnnotationProcessor.getName());
    }

    @Test
    public void checkErrorIsThrownForAnnotationProcessor() throws IOException {
        project.getDependencies()
                .add(
                        "api",
                        project.files(
                                jarWithAnnotationProcessor, directoryWithAnnotationProcessor));
        task.init(
                outputFile,
                "annotationProcessor",
                processorConfiguration.getIncoming().getArtifacts(),
                compileConfiguration.getIncoming().getArtifacts(),
                new AnnotationProcessorOptions(),
                false);
        try {
            task.preCompile();
            fail("Expected to fail");
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains(jarWithAnnotationProcessor.getName());
            assertThat(e.getMessage()).contains(directoryWithAnnotationProcessor.getName());
        }
    }

    @Test
    public void checkSettingIncludeClasspathFalseDisableError() throws IOException {
        project.getDependencies().add("api", project.files(jarWithAnnotationProcessor));
        AnnotationProcessorOptions options = new AnnotationProcessorOptions();
        options.setIncludeCompileClasspath(false);
        task.init(
                outputFile,
                "annotationProcessor",
                processorConfiguration.getIncoming().getArtifacts(),
                compileConfiguration.getIncoming().getArtifacts(),
                options,
                false);

        task.preCompile();

        assertThat(getProcessorNames()).isEmpty();
    }

    @Test
    public void checkSettingIncludeClasspathTrueDisableError() throws IOException {
        project.getDependencies().add("api", project.files(jarWithAnnotationProcessor));
        AnnotationProcessorOptions options = new AnnotationProcessorOptions();
        options.setIncludeCompileClasspath(true);
        task.init(
                outputFile,
                "annotationProcessor",
                processorConfiguration.getIncoming().getArtifacts(),
                compileConfiguration.getIncoming().getArtifacts(),
                options,
                false);

        task.preCompile();

        assertThat(getProcessorNames()).containsExactly(jarWithAnnotationProcessor.getName());
    }

    @Test
    public void checkProcessorConfigurationAddedForMetrics() throws IOException {
        AnnotationProcessorOptions options = new AnnotationProcessorOptions();
        project.getDependencies()
                .add("annotationProcessor", project.files(jarWithAnnotationProcessor));
        task.init(
                outputFile,
                "annotationProcessor",
                processorConfiguration.getIncoming().getArtifacts(),
                compileConfiguration.getIncoming().getArtifacts(),
                options,
                false);
        task.preCompile();

        assertThat(getProcessorNames()).containsExactly(jarWithAnnotationProcessor.getName());
    }

    @Test
    public void checkExplicitProcessorAddedForMetrics() throws IOException {
        AnnotationProcessorOptions options = new AnnotationProcessorOptions();
        options.getClassNames().add(testProcessorName);
        task.init(
                outputFile,
                "annotationProcessor",
                processorConfiguration.getIncoming().getArtifacts(),
                compileConfiguration.getIncoming().getArtifacts(),
                options,
                false);
        task.preCompile();

        assertThat(getProcessorNames()).containsExactly(testProcessorName);
    }

    @Test
    public void checkOnlyExplicitProcessorsAddedForMetrics() throws IOException {
        project.getDependencies().add("api", project.files(jarWithAnnotationProcessor));
        AnnotationProcessorOptions options = new AnnotationProcessorOptions();
        options.setIncludeCompileClasspath(true); // Disable exception.
        options.getClassNames().add(testProcessorName);

        task.init(
                outputFile,
                "annotationProcessor",
                processorConfiguration.getIncoming().getArtifacts(),
                compileConfiguration.getIncoming().getArtifacts(),
                options,
                false);
        task.preCompile();

        // Since the processor name is explicitly specified via
        // AnnotationProcessorOptions.getClassNames(), only the annotation processor with that name
        // will be executed and should be added for metrics
        assertThat(getProcessorNames()).containsExactly(testProcessorName);
    }

    @Test
    public void checkImplicitProcessorsAddedForMetrics() throws IOException {
        project.getDependencies()
                .add("annotationProcessor", project.files(jarWithAnnotationProcessor));
        AnnotationProcessorOptions options = new AnnotationProcessorOptions();

        task.init(
                outputFile,
                "annotationProcessor",
                processorConfiguration.getIncoming().getArtifacts(),
                compileConfiguration.getIncoming().getArtifacts(),
                options,
                false);
        task.preCompile();

        // Since the processor names are not explicitly specified via
        // AnnotationProcessorOptions.getClassNames(), any annotation processors on the annotation
        // processor classpath will be executed and should be added for metrics
        assertThat(getProcessorNames()).containsExactly(dependencyWithProcessorJar);
    }

    @NonNull
    private Set<String> getProcessorNames() {
        assertThat(outputFile).isFile();
        return JavaCompileUtils.readAnnotationProcessorsFromJsonFile(outputFile).keySet();
    }
}
