package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_BUILD_TOOL_VERSION;
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_COMPILE_SDK_VERSION;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.scope.ArtifactTypeUtil;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Check resources in androidTest are available in the generated R.java. */
public class AndroidTestResourcesTest {

    @ClassRule
    public static GradleTestProject appProject =
            GradleTestProject.builder()
                    .withName("application")
                    .fromTestApp(HelloWorldApp.noBuildFile())
                    .create();

    @ClassRule
    public static GradleTestProject libProject =
            GradleTestProject.builder()
                    .withName("library")
                    .fromTestApp(HelloWorldApp.noBuildFile())
                    .create();

    /** Use the test app to create an application and a library project. */
    @BeforeClass
    public static void setUp() throws IOException {
        setUpProject(appProject);
        TestFileUtils.appendToFile(
                appProject.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion '"
                        + DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion rootProject.supportLibMinSdk\n"
                        + "        testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'\n"
                        + "    }\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "    androidTestImplementation \"com.android.support.test:runner:${project.testSupportLibVersion}\"\n"
                        + "    androidTestImplementation \"com.android.support.test:rules:${project.testSupportLibVersion}\"\n"
                        + "}\n");

        setUpProject(libProject);
        TestFileUtils.appendToFile(
                libProject.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.library'\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion '"
                        + DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion rootProject.supportLibMinSdk\n"
                        + "        testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'\n"
                        + "    }\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "    androidTestImplementation \"com.android.support.test:runner:${project.testSupportLibVersion}\"\n"
                        + "    androidTestImplementation \"com.android.support.test:rules:${project.testSupportLibVersion}\"\n"
                        + "}\n");
    }

    @AfterClass
    public static void cleanUp() {
        appProject = null;
        libProject = null;
    }

    @Test
    public void checkTestLayoutFileListedInTestR() throws IOException, InterruptedException {
        doTest(appProject);
    }

    @Test
    public void checkTestLayoutFileListedWhenCompiledAsLibrary()
            throws IOException, InterruptedException {
        doTest(libProject);
    }

    private static void doTest(GradleTestProject project) throws IOException, InterruptedException {
        project.execute("assembleDebugAndroidTest");
        Assert.assertTrue(checkLayoutInR(project, 1, 1));
        Assert.assertFalse(checkLayoutInR(project, 2, 2));

        FileUtils.createFile(
                project.file("src/androidTest/res/layout/test_layout_2.xml"), testLayout(2));

        project.execute("assembleDebugAndroidTest");
        Assert.assertTrue(checkLayoutInR(project, 1, 1));
        Assert.assertTrue(checkLayoutInR(project, 2, 2));

        TestFileUtils.searchAndReplace(
                project.file("src/androidTest/res/layout/test_layout_2.xml"),
                "test_layout_2_textview",
                "test_layout_3_textview");

        project.execute("assembleDebugAndroidTest");
        Assert.assertTrue(checkLayoutInR(project, 1, 1));
        Assert.assertFalse(checkLayoutInR(project, 2, 2));
        Assert.assertTrue(checkLayoutInR(project, 2, 3));

        FileUtils.delete(project.file("src/androidTest/res/layout/test_layout_2.xml"));

        project.execute("assembleDebugAndroidTest");
        Assert.assertTrue(checkLayoutInR(project, 1, 1));
        Assert.assertFalse(checkLayoutInR(project, 2, 2));
        Assert.assertFalse(checkLayoutInR(project, 2, 3));
    }

    private static boolean checkLayoutInR(
            GradleTestProject fixture, final int layout, final int textView) throws IOException {
        File rFile =
                FileUtils.join(
                        ArtifactTypeUtil.getOutputDir(
                                InternalArtifactType.NOT_NAMESPACED_R_CLASS_SOURCES,
                                new File(fixture.getTestDir(), "build")),
                        "debugAndroidTest",
                        "processDebugAndroidTestResources",
                        SdkConstants.FD_RES_CLASS,
                        "com",
                        "example",
                        "helloworld",
                        "test",
                        "R.java");
        Assert.assertTrue("Should have generated R file", rFile.exists());
        List<String> lines = Files.readAllLines(rFile.toPath(), Charsets.UTF_8);
        String rFileContents = lines.stream().collect(Collectors.joining(System.lineSeparator()));

        return (rFileContents.contains("test_layout_" + String.valueOf(layout))
                && rFileContents.contains("test_layout_" + String.valueOf(textView) + "_textview"));
    }

    private static void setUpProject(GradleTestProject project) throws IOException {
        Path layout =
                project.getTestDir()
                        .toPath()
                        .resolve("src/androidTest/res/layout/test_layout_1.xml");
        Files.createDirectories(layout.getParent());
        Files.write(layout, testLayout(1).getBytes());

        // This class exists to prevent the resource from being automatically removed,
        // if we start filtering test resources by default.
        Path resourcesTest =
                project.getTestDir()
                        .toPath()
                        .resolve(
                                "src/androidTest/java/com/example/helloworld/HelloWorldResourceTest.java");
        Files.createDirectories(resourcesTest.getParent());
        String sourcesTestContent =
                "package com.example.helloworld;\n"
                        + "                import android.support.test.filters.MediumTest;\n"
                        + "                import android.support.test.rule.ActivityTestRule;\n"
                        + "                import android.support.test.runner.AndroidJUnit4;\n"
                        + "                import android.widget.TextView;\n"
                        + "                import org.junit.Assert;\n"
                        + "                import org.junit.Before;\n"
                        + "                import org.junit.Rule;\n"
                        + "                import org.junit.Test;\n"
                        + "                import org.junit.runner.RunWith;\n"
                        + "\n"
                        + "                @RunWith(AndroidJUnit4.class)\n"
                        + "                public class HelloWorldResourceTest {\n"
                        + "                    @Rule public ActivityTestRule<HelloWorld> rule = new ActivityTestRule<>(HelloWorld.class);\n"
                        + "                    private TextView mainAppTextView;\n"
                        + "                    private Object testLayout;\n"
                        + "\n"
                        + "\n                  @Before\n"
                        + "                    public void setUp() {\n"
                        + "                        final HelloWorld a = rule.getActivity();\n"
                        + "                        mainAppTextView = (TextView) a.findViewById(\n"
                        + "                                com.example.helloworld.R.id.text);\n"
                        + "                        testLayout = rule.getActivity().getResources()\n"
                        + "                                .getLayout(com.example.helloworld.test.R.layout.test_layout_1);\n"
                        + "                    }\n"
                        + "\n"
                        + "                    @Test\n"
                        + "                    @MediumTest\n"
                        + "                    public void testPreconditions() {\n"
                        + "                        Assert.assertNotNull(\"Should find test test_layout_1.\", testLayout);\n"
                        + "                        Assert.assertNotNull(\"Should find main app text view.\", mainAppTextView);\n"
                        + "                    }\n"
                        + "                }\n"
                        + "                ";
        Files.write(resourcesTest, sourcesTestContent.getBytes());
    }

    private static String testLayout(int i) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"match_parent\"\n"
                + "        android:orientation=\"vertical\" >\n"
                + "    <TextView android:id=\"@+id/test_layout_"
                + String.valueOf(i)
                + "_textview\"\n"
                + "            android:layout_width=\"wrap_content\"\n"
                + "            android:layout_height=\"wrap_content\"\n"
                + "            android:text=\"Hello, I am a TextView\" />\n"
                + "</LinearLayout>\n";
    }
}
