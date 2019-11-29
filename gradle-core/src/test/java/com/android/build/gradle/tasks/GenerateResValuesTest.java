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

package com.android.build.gradle.tasks;


import static com.android.testutils.truth.FileSubject.assertThat;

import com.android.builder.internal.ClassFieldImpl;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit test for GenerateResValues
 */
public class GenerateResValuesTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void test () throws IOException, ParserConfigurationException {
        File testDir = temporaryFolder.newFolder();
        Project project = ProjectBuilder.builder().withProjectDir(testDir).build();

        GenerateResValues task =  project.getTasks().create("test", GenerateResValues.class);
        task.setItems(ImmutableList.of(new ClassFieldImpl("string", "VALUE_DEFAULT", "1")));
        task.setResOutputDir(testDir);

        task.generate();

        File output = new File(testDir, "values/generated.xml");
        assertThat(output).contentWithUnixLineSeparatorsIsExactly(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "\n"
                        + "    <!-- Automatically generated file. DO NOT MODIFY -->\n"
                        + "\n"
                        + "    <string name=\"VALUE_DEFAULT\" translatable=\"false\">1</string>\n"
                + "\n"
                + "</resources>");
    }
}
