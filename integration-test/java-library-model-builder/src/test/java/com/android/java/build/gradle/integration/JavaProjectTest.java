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

package com.android.java.build.gradle.integration;


import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.MultiModuleJavaLibs;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.java.model.JavaLibrary;
import com.android.java.model.JavaProject;
import com.android.java.model.LibraryVersion;
import com.android.java.model.SourceSet;
import com.android.utils.FileUtils;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test {@link JavaProject} returned from Java Library Plugin
 */
public class JavaProjectTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .withDeviceProvider(false)
                    .withSdk(false)
                    .withAndroidGradlePlugin(false)
                    .fromTestApp(MultiModuleJavaLibs.createWithLibs(2))
                    .create();
    private Map<String, JavaProject> javaModelMap;

    @Before
    public void setUp() throws Exception {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "buildscript {\n"
                        + "    dependencies {\n"
                        + "        classpath \"com.android.java.tools.build:java-lib-model-builder:$rootProject.buildVersion\"\n"
                        + "    }\n"
                        + "}\n\n"
                        + "subprojects {\n"
                        + "    apply from: \"../../commonLocalRepo.gradle\"\n"
                        + "    apply plugin: 'com.android.java'\n"
                        + "}");

        TestFileUtils.appendToFile(
                project.getSubproject(":lib1").getBuildFile(),
                "dependencies {\n"
                        + "    compile fileTree(dir: 'libs', include: ['*.jar'])\n"
                        + "    compile 'com.google.code.findbugs:jsr305:1.3.9'\n"
                        + "}\n"
                        + "sourceCompatibility = \"1.7\"\n"
                        + "targetCompatibility = \"1.7\"\n");

        TestFileUtils.appendToFile(
                project.getSubproject(":lib2").getBuildFile(),
                "sourceSets {\n"
                        + "    foo\n"
                        + "}\n\n"
                        + "dependencies {\n"
                        + "    compile fileTree(include: ['*.jar'], dir: 'libs')\n"
                        + "    compile project(':lib1')\n"
                        + "    compile 'unresolved:no-exit:1.0'\n"
                        + "    compile 'com.google.guava:guava:19.0'\n"
                        + "}\n\n"
                        + "sourceCompatibility = \"1.7\"\n"
                        + "targetCompatibility = \"1.7\"");

        FileUtils.createFile(project.getSubproject(":lib2")
                .file("libs/protobuf-java-3.0.0.jar"), "dummy");
        javaModelMap = project.model().fetchMulti(JavaProject.class);
    }

    @Test
    public void checkJavaModel() throws Exception {
        String javaModule1 = ":lib1";
        String javaModule2 = ":lib2";

        // Check model exists for java modules.
        assertThat(javaModelMap).hasSize(2);
        assertThat(javaModelMap).containsKey(javaModule1);
        assertThat(javaModelMap).containsKey(javaModule2);

        // Check name and java language level.
        JavaProject javaProject = javaModelMap.get(javaModule2);
        assertThat(javaProject.getName()).isEqualTo(javaModule2.substring(1));
        assertThat(javaProject.getJavaLanguageLevel()).isEqualTo("1.7");

        Map<String, SourceSet> sourceSetsByName =
                javaProject.getSourceSets()
                        .stream().collect(Collectors.toMap(SourceSet::getName, p -> p));

        // Check contains default sourceSets, main and test.
        assertThat(sourceSetsByName).containsKey("main");
        assertThat(sourceSetsByName).containsKey("test");

        // Check contains custom defined sourceSets, foo.
        assertThat(sourceSetsByName).containsKey("foo");

        // Check dependencies.
        Collection<JavaLibrary> dependencies = sourceSetsByName.get("main")
                .getCompileClasspathDependencies();
        Set<String> projectDependencies = new HashSet<>();
        Map<String, JavaLibrary> jarDependencies = new HashMap<>();

        for (JavaLibrary javaLibrary : dependencies) {
            if (javaLibrary.getProject() != null) {
                projectDependencies.add(javaLibrary.getName());
            } else {
                jarDependencies.put(javaLibrary.getName(), javaLibrary);
            }
        }
        // Check project dependency.
        assertThat(projectDependencies).contains(javaModule1.substring(1));

        // Check jar dependency from local directory.
        assertThat(jarDependencies).containsKey("local jar - protobuf-java-3.0.0.jar");

        // Check unresolved jar dependency.
        assertThat(jarDependencies).containsKey("unresolved dependency - unresolved no-exit 1.0");

        // Check resolved direct jar dependency.
        assertThat(jarDependencies).containsKey("guava");
        LibraryVersion version = jarDependencies.get("guava").getLibraryVersion();
        assertThat(version).isNotNull();
        assertThat(version.getGroup()).isEqualTo("com.google.guava");
        assertThat(version.getName()).isEqualTo("guava");
        assertThat(version.getVersion()).isEqualTo("19.0");

        // Check resolved transitive jar dependency.
        assertThat(jarDependencies).containsKey("jsr305");
        version = jarDependencies.get("jsr305").getLibraryVersion();
        assertThat(version).isNotNull();
        assertThat(version.getGroup()).isEqualTo("com.google.code.findbugs");
        assertThat(version.getName()).isEqualTo("jsr305");
        assertThat(version.getVersion()).isEqualTo("1.3.9");
    }
}
