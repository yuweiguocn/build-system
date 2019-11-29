package com.android.build.gradle.integration.testing;

import static com.android.testutils.truth.FileSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TestVersions;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.TestedTargetVariant;
import com.android.builder.model.Variant;
import com.android.utils.FileUtils;
import com.google.common.collect.Iterables;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test for setup with 2 modules: app and test-app Checking the manifest merging for the test
 * modules.
 */
public class SeparateTestModuleTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("separateTestModule").create();

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getSubproject("test").getBuildFile(),
                "\n"
                        + "android {\n"
                        + "  defaultConfig {\n"
                        + "    testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'\n"
                        + "    minSdkVersion 16\n"
                        + "    targetSdkVersion 16\n"
                        + "  }\n"
                        + "  dependencies {\n"
                        + "    implementation ('com.android.support.test:runner:"
                        + TestVersions.TEST_SUPPORT_LIB_VERSION
                        + "', {\n"
                        + "      exclude group: 'com.android.support', module: 'support-annotations'\n"
                        + "    })\n"
                        + "  }\n"
                        + "}\n");
    }

    @Test
    public void checkDependenciesBetweenTasks() throws Exception {
        // Check :test:assembleDebug succeeds on its own, i.e. compiles the app module.
        project.execute("clean", ":test:assembleDebug", ":test:checkDependencies");
    }

    @Test
    public void checkInstrumentationReadFromBuildFile() throws Exception {
        GradleTestProject testProject = project.getSubproject("test");
        addInstrumentationToManifest();
        project.execute("clean", ":test:assembleDebug");

        assertThat(
                        testProject.file(
                                "build/intermediates/merged_manifests/debug/AndroidManifest.xml"))
                .containsAllOf(
                        "package=\"com.example.android.testing.blueprint.test\"",
                        "android:name=\"android.support.test.runner.AndroidJUnitRunner\"",
                        "android:targetPackage=\"com.android.tests.basic\"");
    }

    @Test
    public void checkInstrumentationAdded() throws Exception {
        GradleTestProject testProject = project.getSubproject("test");
        project.execute("clean", ":test:assembleDebug");

        assertThat(
                        testProject.file(
                                "build/intermediates/merged_manifests/debug/AndroidManifest.xml"))
                .containsAllOf(
                        "package=\"com.example.android.testing.blueprint.test\"",
                        "<instrumentation",
                        "android:name=\"android.support.test.runner.AndroidJUnitRunner\"",
                        "android:targetPackage=\"com.android.tests.basic\"");
    }

    @Test
    public void checkModelContainsTestedApksToInstall() throws Exception {
        Variant variant =
                Iterables.getFirst(
                        project.executeAndReturnMultiModel("clean")
                                .getOnlyModelMap()
                                .get(":test")
                                .getVariants(),
                        null);
        Truth.assertThat(variant).isNotNull();
        Collection<TestedTargetVariant> toInstall = variant.getTestedTargetVariants();

        assertThat(toInstall).hasSize(1);
        assertThat(Iterables.getOnlyElement(toInstall).getTargetProjectPath()).isEqualTo(":app");
        // FIXME we can't know the variant yet because it's not passed through the new dependency
        // scheme.
        // assertThat(toInstall.first().getTargetVariant()).isEqualTo("debug")
    }

    private void addInstrumentationToManifest() throws IOException {
        GradleTestProject testProject = project.getSubproject("test");
        FileUtils.deleteIfExists(testProject.file("src/main/AndroidManifest.xml"));
        String manifestContent =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "      package=\"com.android.tests.basic.test\">\n"
                        + "      <instrumentation android:name=\"android.test.InstrumentationTestRunner\"\n"
                        + "                       android:targetPackage=\"com.android.tests.basic\"\n"
                        + "                       android:handleProfiling=\"false\"\n"
                        + "                       android:functionalTest=\"false\"\n"
                        + "                       android:label=\"Tests for com.android.tests.basic\"/>\n"
                        + "</manifest>\n";
        Files.write(
                testProject.file("src/main/AndroidManifest.xml").toPath(),
                manifestContent.getBytes());
    }
}
