package com.android.build.gradle.integration.library;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestModule;
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.scope.CodeShrinker;
import com.android.build.gradle.options.BooleanOption;
import com.android.ide.common.process.ProcessException;
import com.android.testutils.apk.Aar;
import com.google.common.base.Joiner;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration test to check that libraries included directly as jar files are correctly handled
 * when using proguard or R8.
 */
@RunWith(FilterableParameterized.class)
public class ProguardAarPackagingTest {

    public static AndroidTestModule testApp = HelloWorldApp.noBuildFile();
    public static AndroidTestModule libraryInJar = new EmptyAndroidTestApp();

    static {
        TestSourceFile oldHelloWorld = testApp.getFileByName("HelloWorld.java");
        testApp.replaceFile(
                new TestSourceFile(
                        oldHelloWorld.getPath(),
                        "package com.example.helloworld;\n"
                                + "\n"
                                + "import com.example.libinjar.LibInJar;\n"
                                + "\n"
                                + "import android.app.Activity;\n"
                                + "import android.os.Bundle;\n"
                                + "\n"
                                + "public class HelloWorld extends Activity {\n"
                                + "    /** Called when the activity is first created. */\n"
                                + "    @Override\n"
                                + "    public void onCreate(Bundle savedInstanceState) {\n"
                                + "        super.onCreate(savedInstanceState);\n"
                                + "        setContentView(R.layout.main);\n"
                                + "        LibInJar.method();\n"
                                + "    }\n"
                                + "}\n"));

        testApp.addFile(new TestSourceFile("config.pro", "-keeppackagenames **"));

        // Create simple library jar.
        libraryInJar.addFile(
                new TestSourceFile(
                        "src/main/java/com/example/libinjar",
                        "LibInJar.java",
                        "package com.example.libinjar;\n"
                                + "\n"
                                + "public class LibInJar {\n"
                                + "    public static void method() {\n"
                                + "        throw new UnsupportedOperationException(\"Not implemented\");\n"
                                + "    }\n"
                                + "}\n"));
    }

    @ClassRule
    public static GradleTestProject androidProject =
            GradleTestProject.builder().withName("mainProject").fromTestApp(testApp).create();

    @ClassRule
    public static GradleTestProject libraryInJarProject =
            GradleTestProject.builder().withName("libInJar").fromTestApp(libraryInJar).create();

    @Parameterized.Parameter public CodeShrinker shrinker;

    @Parameterized.Parameters(name = "shrinker={0}")
    public static CodeShrinker[] getSetups() {
        return CodeShrinker.values();
    }

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        // Create android test application
        TestFileUtils.appendToFile(
                androidProject.getBuildFile(),
                "apply plugin: 'com.android.library'\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile fileTree(dir: 'libs', include: '*.jar')\n"
                        + "}\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "\n"
                        + "    buildTypes {\n"
                        + "        release {\n"
                        + "            minifyEnabled true\n"
                        + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'config.pro'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}");

        TestFileUtils.appendToFile(libraryInJarProject.getBuildFile(), "apply plugin: 'java'");
        libraryInJarProject.execute("assemble");

        // Copy the generated jar into the android project.
        androidProject.file("libs").mkdirs();
        String libInJarName =
                Joiner.on(File.separatorChar)
                        .join(
                                "build",
                                "libs",
                                libraryInJarProject.getName() + SdkConstants.DOT_JAR);
        FileUtils.copyFile(
                libraryInJarProject.file(libInJarName), androidProject.file("libs/libinjar.jar"));
    }

    @AfterClass
    public static void cleanUp() {
        androidProject = null;
        libraryInJarProject = null;
    }

    @Test
    public void checkDebugAarPackaging()
            throws IOException, InterruptedException, ProcessException {
        androidProject
                .executor()
                .with(BooleanOption.ENABLE_R8, shrinker == CodeShrinker.R8)
                .run("assembleDebug");

        Aar debug = androidProject.getAar("debug");

        // check that the classes from the local jars are still in a local jar
        assertThat(debug).containsSecondaryClass("Lcom/example/libinjar/LibInJar;");

        // check that it's not in the main class file.
        assertThat(debug).doesNotContainMainClass("Lcom/example/libinjar/LibInJar;");
    }

    @Test
    public void checkReleaseAarPackaging()
            throws IOException, InterruptedException, ProcessException {
        androidProject
                .executor()
                .with(BooleanOption.ENABLE_R8, shrinker == CodeShrinker.R8)
                .run("assembleRelease");

        Aar release = androidProject.getAar("release");

        // check that the classes from the local jars are in the main class file
        assertThat(release).containsMainClass("Lcom/example/libinjar/a;");

        // check that it's not in any local jar
        assertThat(release).doesNotContainSecondaryClass("Lcom/example/libinjar/LibInJar;");
    }
}
