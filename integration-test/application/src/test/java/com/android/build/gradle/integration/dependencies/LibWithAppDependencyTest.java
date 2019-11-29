package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

/** Check that we fail when a library depends on an app. */
public class LibWithAppDependencyTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("projectWithModules").create();

    @BeforeClass
    public static void setUp() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8)
                .write("include 'app', 'library'");

        final GradleTestProject libraryProject = project.getSubproject("library");

        appendToFile(
                libraryProject.getBuildFile(),
                "\n" + "dependencies {\n" + "    compile project(\":app\")\n" + "}\n");

        File mainDir = libraryProject.getMainSrcDir().getParentFile();
        File assetFile = FileUtils.join(mainDir, "java", "com", "example", "library", "Lib.java");
        FileUtils.mkdirs(assetFile.getParentFile());
        appendToFile(
                assetFile,
                "package com.example.library;\n"
                        + "import com.example.app.App;\n"
                        + "public class Lib {\n"
                        + "    public Lib() {\n"
                        + "        App app = new App();\n"
                        + "    }\n"
                        + "}");

        final GradleTestProject appProject = project.getSubproject("app");

        mainDir = appProject.getMainSrcDir().getParentFile();
        assetFile = FileUtils.join(mainDir, "java", "com", "example", "app", "App.java");
        FileUtils.mkdirs(assetFile.getParentFile());
        appendToFile(
                assetFile,
                "package com.example.app;\n"
                        + "public class App {\n"
                        + "    public App() {\n"
                        + "    }\n"
                        + "}");
    }

    @Test
    @Ignore("triggers a weird resolution error. Waiting for fix in Gradle.")
    public void build() throws IOException, InterruptedException {
        GradleBuildResult result =
                project.executor().expectFailure().run("clean", ":library:assembleDebug");

        // Gradle detects this for us. Unfortunately there's no mention of ":lib" in the error message.
        assertThat(result.getStdout())
                .contains(
                        "library/src/main/java/com/example/library/Lib.java:3: error: package com.example.app does not exist");
    }
}
