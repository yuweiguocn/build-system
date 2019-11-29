package com.android.build.gradle.integration.testing;

import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.COORDINATES;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.fixture.TestVersions;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.ide.common.process.ProcessException;
import com.android.testutils.apk.Apk;
import java.io.IOException;
import java.util.Collection;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Test separate test module testing an app with aar dependencies. */
public class SeparateTestWithAarDependencyTest {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("separateTestModule").create();

    private static ModelContainer<AndroidProject> models;
    private static LibraryGraphHelper helper;

    @BeforeClass
    public static void setup() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion = '"
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion "
                        + TestVersions.SUPPORT_LIB_MIN_SDK
                        + "\n"
                        + "    }\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "    api 'com.android.support:appcompat-v7:"
                        + TestVersions.SUPPORT_LIB_VERSION
                        + "'\n"
                        + "    api 'com.android.support:support-v4:"
                        + TestVersions.SUPPORT_LIB_VERSION
                        + "'\n"
                        + "    api 'com.android.support:support-media-compat:"
                        + TestVersions.SUPPORT_LIB_VERSION
                        + "'\n"
                        + "}\n");

        project.execute("clean", "assemble");
        models = project.model().withFullDependencies().fetchAndroidProjects();
        helper = new LibraryGraphHelper(models);
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        models = null;
        helper = null;
    }

    @Test
    public void checkAppDoesntContainTestAppCode() throws IOException, ProcessException {
        Apk apk = project.getSubproject("test").getApk(GradleTestProject.ApkType.DEBUG);
        TruthHelper.assertThatApk(apk).doesNotContainClass("Lcom/android/tests/basic/Main;");
    }

    @Test
    public void checkAppDoesntContainTestAppLayout() throws IOException, ProcessException {
        Apk apk = project.getSubproject("test").getApk(GradleTestProject.ApkType.DEBUG);
        TruthHelper.assertThatApk(apk).doesNotContainResource("layout/main.xml");
    }

    @Test
    public void checkAppDoesntContainTestAppDependencyLibCode()
            throws IOException, ProcessException {
        Apk apk = project.getSubproject("test").getApk(GradleTestProject.ApkType.DEBUG);
        TruthHelper.assertThatApk(apk).doesNotContainClass("Landroid/support/v7/app/ActionBar;");
    }

    @Test
    public void checkAppDoesNotContainTestAppDependencyLibResources()
            throws IOException, ProcessException {
        Apk apk = project.getSubproject("test").getApk(GradleTestProject.ApkType.DEBUG);
        TruthHelper.assertThatApk(apk)
                .doesNotContainResource("layout/abc_action_bar_title_item.xml");
    }

    @Test
    public void checkTestModelCompileDepsIncludesTheTestedApp() {
        final AndroidProject androidProject = models.getOnlyModelMap().get(":test");

        // get the main artifact of the debug artifact and its dependencies
        Variant variant = AndroidProjectUtils.getVariantByName(androidProject, "debug");
        AndroidArtifact artifact = variant.getMainArtifact();

        DependencyGraphs compileGraph = artifact.getDependencyGraphs();

        // check the app and its children dependencies show up flat in the main
        // dependency list.
        TruthHelper.assertThat(helper.on(compileGraph).mapTo(COORDINATES))
                .containsAllOf(
                        project.getTestDir().getAbsolutePath() + "@@:app::debug",
                        "com.android.support:support-core-ui:"
                                + TestVersions.SUPPORT_LIB_VERSION
                                + "@aar",
                        "com.android.support:support-core-utils:"
                                + TestVersions.SUPPORT_LIB_VERSION
                                + "@aar",
                        "com.android.support:appcompat-v7:"
                                + TestVersions.SUPPORT_LIB_VERSION
                                + "@aar",
                        "com.android.support:support-fragment:"
                                + TestVersions.SUPPORT_LIB_VERSION
                                + "@aar",
                        "com.android.support:support-compat:"
                                + TestVersions.SUPPORT_LIB_VERSION
                                + "@aar",
                        "com.android.support:support-v4:"
                                + TestVersions.SUPPORT_LIB_VERSION
                                + "@aar",
                        "com.android.support:support-annotations:"
                                + TestVersions.SUPPORT_LIB_VERSION
                                + "@jar",
                        "com.android.support:animated-vector-drawable:"
                                + TestVersions.SUPPORT_LIB_VERSION
                                + "@aar",
                        "com.android.support:support-media-compat:"
                                + TestVersions.SUPPORT_LIB_VERSION
                                + "@aar",
                        "com.android.support:support-vector-drawable:"
                                + TestVersions.SUPPORT_LIB_VERSION
                                + "@aar");
    }

    @Test
    public void checkTestModelPackageDepsDoesNotIncludeTheTestedApp() {
        final AndroidProject androidProject = models.getOnlyModelMap().get(":test");
        Collection<Variant> variants = androidProject.getVariants();

        // get the main artifact of the debug artifact and its dependencies
        Variant variant = AndroidProjectUtils.getVariantByName(androidProject, "debug");
        AndroidArtifact artifact = variant.getMainArtifact();

        // verify the same dependencies in package are skipped.
        DependencyGraphs dependencyGraph = artifact.getDependencyGraphs();

        LibraryGraphHelper.Items packageItems = helper.on(dependencyGraph).forPackage();

        // make sure the package does not contain the app or its dependencies
        TruthHelper.assertThat(packageItems.mapTo(COORDINATES))
                .containsNoneOf(
                        project.getTestDir().getAbsolutePath() + "@@:app::debug",
                        "com.android.support:support-core-ui:"
                                + TestVersions.SUPPORT_LIB_VERSION
                                + "@aar",
                        "com.android.support:support-core-utils:"
                                + TestVersions.SUPPORT_LIB_VERSION
                                + "@aar",
                        "com.android.support:appcompat-v7:"
                                + TestVersions.SUPPORT_LIB_VERSION
                                + "@aar",
                        "com.android.support:support-fragment:"
                                + TestVersions.SUPPORT_LIB_VERSION
                                + "@aar",
                        "com.android.support:support-compat:"
                                + TestVersions.SUPPORT_LIB_VERSION
                                + "@aar",
                        "com.android.support:support-v4:"
                                + TestVersions.SUPPORT_LIB_VERSION
                                + "@aar",
                        "com.android.support:animated-vector-drawable:"
                                + TestVersions.SUPPORT_LIB_VERSION
                                + "@aar",
                        "com.android.support:support-media-compat:"
                                + TestVersions.SUPPORT_LIB_VERSION
                                + "@aar",
                        "com.android.support:support-vector-drawable:"
                                + TestVersions.SUPPORT_LIB_VERSION
                                + "@aar");
    }
}
