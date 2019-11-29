package com.android.build.gradle.integration.application;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Test BaseExtension.getBootClasspath can be use before afterEvaluate. */
public class BootClasspathTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestApp(HelloWorldApp.noBuildFile()).create();

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion '"
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "}\n"
                        + "\n"
                        + "task checkBootClasspath {\n"
                        + "    assert android.getBootClasspath() != null\n"
                        + "}\n");
    }

    @Test
    public void checkBootClasspathCanBeCalled() throws IOException, InterruptedException {
        project.execute("checkBootClasspath");
    }
}
