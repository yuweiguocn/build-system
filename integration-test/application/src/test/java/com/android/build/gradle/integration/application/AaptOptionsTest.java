package com.android.build.gradle.integration.application;

import static com.android.testutils.truth.FileSubject.assertThat;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.scope.ArtifactTypeUtil;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Tests for DSL AAPT options. */
public class AaptOptionsTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Before
    public void setUp() throws IOException {
        FileUtils.createFile(project.file("src/main/res/raw/ignored"), "ignored");
        FileUtils.createFile(project.file("src/main/res/raw/kept"), "kept");
    }

    @Test
    public void testAaptOptionsFlagsWithAapt2() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "  aaptOptions {\n"
                        + "    additionalParameters \"--extra-packages\", \"com.boop.beep\"\n"
                        + "  }\n"
                        + "}\n");

        project.executor().run("clean", "assembleDebug");

        Joiner joiner = Joiner.on(File.separator);
        File extraR =
                new File(
                        joiner.join(
                                ArtifactTypeUtil.getOutputDir(
                                        InternalArtifactType.NOT_NAMESPACED_R_CLASS_SOURCES,
                                        project.getOutputDir().getParentFile()),
                                "debug",
                                "processDebugResources",
                                SdkConstants.FD_RES_CLASS,
                                "com",
                                "boop",
                                "beep",
                                "R.java"));

        // Check that the extra R.java file was generated in the specified package.
        assertThat(extraR).exists();
        assertThat(extraR).contains("package com.boop.beep");

        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "additionalParameters \"--extra-packages\", \"com.boop.beep\"",
                "");

        project.executor().run("assembleDebug");

        // Check that the extra R.java file is not generated if the extra options aren't present.
        assertThat(extraR).doesNotExist();
    }

    @Test
    public void emptyNoCompressList() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "  aaptOptions {\n"
                        + "    noCompress \"\"\n"
                        + "  }\n"
                        + "}\n");

        // Should execute without failure.
        project.executor().run("clean", "assembleDebug");
    }
}
