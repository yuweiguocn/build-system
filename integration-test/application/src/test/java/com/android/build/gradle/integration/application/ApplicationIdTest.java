package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Test setting applicationId and applicationIdSuffix. */
public class ApplicationIdTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestApp(HelloWorldApp.noBuildFile()).create();

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: \"com.android.application\"\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "\n"
                        + "    defaultConfig {\n"
                        + "        applicationId \"com.example.applicationidtest\"\n"
                        + "        applicationIdSuffix \"default\"\n"
                        + "    }\n"
                        + "\n"
                        + "    buildTypes {\n"
                        + "        debug {\n"
                        + "            applicationIdSuffix \".debug\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    flavorDimensions 'foo'\n"
                        + "    productFlavors {\n"
                        + "        f1 {\n"
                        + "            applicationIdSuffix \"f1\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    public void checkApplicationId() throws IOException, InterruptedException {
        project.execute("assembleF1");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "f1"))
                .hasPackageName("com.example.applicationidtest.default.f1.debug");
        assertThat(project.getApk(GradleTestProject.ApkType.RELEASE, "f1"))
                .hasPackageName("com.example.applicationidtest.default.f1");

        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "applicationIdSuffix \".debug\"",
                "applicationIdSuffix \".foo\"");

        project.execute("assembleF1");

        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "f1"))
                .hasPackageName("com.example.applicationidtest.default.f1.foo");
        assertThat(project.getApk(GradleTestProject.ApkType.RELEASE, "f1"))
                .hasPackageName("com.example.applicationidtest.default.f1");
    }
}
