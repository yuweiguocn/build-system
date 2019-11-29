package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Ensures that archivesBaseName setting on android project is used when choosing the apk file names
 */
@RunWith(FilterableParameterized.class)
public class ArchivesBaseNameTest {

    private static final String OLD_NAME = "random_name";
    private static final String NEW_NAME = "changed_name";
    @Rule public GradleTestProject project;
    private String extension;

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
        return ImmutableList.of(
                new Object[] {"com.android.application", "apk"},
                new Object[] {"com.android.library", "aar"});
    }

    public ArchivesBaseNameTest(String plugin, String extension) {
        this.project =
                GradleTestProject.builder().fromTestApp(HelloWorldApp.forPlugin(plugin)).create();
        this.extension = extension;
    }

    @Test
    public void testArtifactName() throws IOException, InterruptedException {
        checkApkName("project", extension);

        TestFileUtils.appendToFile(
                project.getBuildFile(), "\narchivesBaseName = \'" + OLD_NAME + "\'");
        checkApkName(OLD_NAME, extension);

        TestFileUtils.searchAndReplace(project.getBuildFile(), OLD_NAME, NEW_NAME);
        checkApkName(NEW_NAME, extension);
    }

    private void checkApkName(String name, String extension)
            throws IOException, InterruptedException {
        ProjectBuildOutput projectBuildOutput =
                project.executeAndReturnOutputModel("assembleDebug");
        VariantBuildOutput debugBuildOutput =
                ProjectBuildOutputUtils.getDebugVariantBuildOutput(projectBuildOutput);

        // Get the apk file
        assertThat(debugBuildOutput.getOutputs()).hasSize(1);

        File outputFile = debugBuildOutput.getOutputs().iterator().next().getOutputFile();

        assertThat(outputFile.getName()).isEqualTo(name + "-debug." + extension);
        assertThat(outputFile).isFile();
    }
}
