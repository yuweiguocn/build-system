package com.android.build.gradle.integration.application;

import static com.android.SdkConstants.FN_FRAMEWORK_LIBRARY;
import static com.android.build.gradle.integration.common.truth.ModelSubject.assertThat;
import static com.android.testutils.truth.FileSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.targets.AndroidTargetManager;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;

/** Test for the new useLibrary mechanism */
public class OptionalLibraryTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestApp(HelloWorldApp.noBuildFile()).create();

    @Test
    public void testUnknownUseLibraryTriggerSyncIssue() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion '"
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "    useLibrary 'foo'\n"
                        + "}");

        AndroidProject model =
                project.model().ignoreSyncIssues().fetchAndroidProjects().getOnlyModel();

        assertThat(model)
                .hasSingleIssue(
                        SyncIssue.SEVERITY_ERROR, SyncIssue.TYPE_OPTIONAL_LIB_NOT_FOUND, "foo");
    }

    @Test
    public void testUsingOptionalLibrary() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion '"
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "    useLibrary 'org.apache.http.legacy'\n"
                        + "}");

        AndroidProject model = project.model().fetchAndroidProjects().getOnlyModel();

        // get the SDK folder
        File sdkLocation = project.getAndroidHome();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        AndroidTargetManager targetMgr =
                AndroidSdkHandler.getInstance(sdkLocation).getAndroidTargetManager(progress);
        IAndroidTarget target =
                targetMgr.getTargetFromHashString(GradleTestProject.getCompileSdkHash(), progress);

        File targetLocation = new File(target.getLocation());

        // the files that the bootclasspath should contain.
        File androidJar = new File(targetLocation, FN_FRAMEWORK_LIBRARY);
        File httpJar = new File(targetLocation, "optional/org.apache.http.legacy.jar");
        assertThat(model.getBootClasspath())
                .containsExactly(androidJar.getAbsolutePath(), httpJar.getAbsolutePath());

        // for safety, let's make sure these files actually exists.
        assertThat(androidJar).isFile();
        assertThat(httpJar).isFile();
    }

    @Test
    public void testNotUsingOptionalLibrary() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion '"
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "}");

        AndroidProject model = project.model().fetchAndroidProjects().getOnlyModel();

        // get the SDK folder
        File sdkLocation = project.getAndroidHome();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        AndroidTargetManager targetMgr =
                AndroidSdkHandler.getInstance(sdkLocation).getAndroidTargetManager(progress);
        IAndroidTarget target =
                targetMgr.getTargetFromHashString(GradleTestProject.getCompileSdkHash(), progress);

        File targetLocation = new File(target.getLocation());

        assertThat(model.getBootClasspath())
                .containsExactly(new File(targetLocation, FN_FRAMEWORK_LIBRARY).getAbsolutePath());
    }
}
