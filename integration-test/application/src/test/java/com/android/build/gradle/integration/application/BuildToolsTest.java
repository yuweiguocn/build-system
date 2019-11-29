package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.utils.AssumeUtil.assumeBuildToolsGreaterThan;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests to ensure that changing the build tools version in the build.gradle will trigger
 * re-execution of some tasks even if no source file change was detected.
 */
public class BuildToolsTest {

    private static final List<String> COMMON_TASKS =
            ImmutableList.of(
                    ":compileDebugAidl",
                    ":mergeDebugResources",
                    ":processDebugResources",
                    ":compileReleaseAidl",
                    ":mergeReleaseResources",
                    ":processReleaseResources");

    private static final List<String> JAVAC_TASKS =
            ImmutableList.<String>builder()
                    .addAll(COMMON_TASKS)
                    .add(":transformClassesWithDexBuilderForDebug")
                    .add(":transformClassesWithDexBuilderForRelease")
                    .add(":mergeDexDebug")
                    .add(":mergeExtDexDebug")
                    .add(":mergeDexRelease")
                    .add(":mergeExtDexRelease")
                    .build();

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
                        + "}\n");

        // Add an Aidl file so that it's not skipped due to no-source.
        File aidlDir = project.file("src/main/aidl/com/example/helloworld");
        FileUtils.mkdirs(aidlDir);
        TestFileUtils.appendToFile(
                new File(aidlDir, "MyRect.aidl"),
                "" + "package com.example.helloworld;\n" + "parcelable MyRect;\n");
    }

    @Test
    public void nullBuild() throws IOException, InterruptedException {
        project.executor().run("assemble");
        GradleBuildResult result = project.executor().run("assemble");

        assertThat(result.getUpToDateTasks()).containsAllIn(JAVAC_TASKS);
    }

    @Test
    public void invalidateBuildTools() throws IOException, InterruptedException {
        // We need at least 2 valid versions of the build tools for this test.
        assumeBuildToolsGreaterThan(AndroidBuilder.MIN_BUILD_TOOLS_REV);

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
                        + "}\n");

        project.executor().run("assemble");

        String otherBuildToolsVersion = AndroidBuilder.MIN_BUILD_TOOLS_REV.toString();
        // Sanity check:
        assertThat(otherBuildToolsVersion)
                .isNotEqualTo(GradleTestProject.DEFAULT_BUILD_TOOL_VERSION);

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion '"
                        + otherBuildToolsVersion
                        + "'\n"
                        + "}\n");

        GradleBuildResult result = project.executor().run("assemble");

        assertThat(result.getDidWorkTasks()).containsAllIn(COMMON_TASKS);
    }

    @Test
    public void buildToolsInModel() throws IOException {
        AndroidProject model = project.model().fetchAndroidProjects().getOnlyModel();
        assertThat(model.getBuildToolsVersion())
                .named("Build Tools Version")
                .isEqualTo(GradleTestProject.DEFAULT_BUILD_TOOL_VERSION);
    }

    @Test
    public void buildToolsSyncIssue() throws IOException {
        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "buildToolsVersion '" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "'",
                "buildToolsVersion '24.0.3'");
        AndroidProject model =
                project.model().ignoreSyncIssues().fetchAndroidProjects().getOnlyModel();
        TruthHelper.assertThat(model)
                .hasIssue(SyncIssue.SEVERITY_WARNING, SyncIssue.TYPE_BUILD_TOOLS_TOO_LOW);
    }
}
