package com.android.build.gradle.integration.application;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DensitySplitWithPublishNonDefaultTest {

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
                        + "    buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "\n"
                        + "    publishNonDefault true\n"
                        + "\n"
                        + "    splits {\n"
                        + "        density {\n"
                        + "            enable true\n"
                        + "            exclude \"ldpi\", \"tvdpi\", \"xxxhdpi\", \"400dpi\", \"560dpi\"\n"
                        + "            compatibleScreens 'small', 'normal', 'large', 'xlarge'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    public void buildAndPublish() throws IOException, InterruptedException {
        // build the release for publication (though debug is published too)
        project.execute("assembleRelease");
    }
}
