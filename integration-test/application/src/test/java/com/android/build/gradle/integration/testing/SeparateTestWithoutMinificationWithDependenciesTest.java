package com.android.build.gradle.integration.testing;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.scope.CodeShrinker;
import com.android.build.gradle.options.BooleanOption;
import com.android.ide.common.process.ProcessException;
import com.android.testutils.apk.Apk;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Test separate test module that tests an application with some complicated dependencies : - the
 * app imports a library importing a jar file itself.
 */
@RunWith(FilterableParameterized.class)
public class SeparateTestWithoutMinificationWithDependenciesTest {

    @Parameterized.Parameters(name = "codeShrinker = {0}")
    public static CodeShrinker[] getShrinkers() {
        return new CodeShrinker[] {CodeShrinker.PROGUARD, CodeShrinker.R8};
    }

    @Parameterized.Parameter public CodeShrinker codeShrinker;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("separateTestModuleWithDependencies")
                    .withDependencyChecker(false)
                    .create();

    @Before
    public void setup() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getSubproject("test").getBuildFile(),
                "\n"
                        + "        android {\n"
                        + "            targetVariant 'debug'\n"
                        + "        }\n");
        project.execute("clean");
        project.executor()
                .with(BooleanOption.ENABLE_R8, codeShrinker == CodeShrinker.R8)
                .run("assemble");
    }

    @Test
    public void checkApkContent() throws IOException, ProcessException {
        Apk apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG);
        TruthHelper.assertThatApk(apk)
                .containsClass("Lcom/android/tests/jarDep/JarDependencyUtil;");

        Apk apkTest = project.getSubproject("test").getApk(GradleTestProject.ApkType.DEBUG);
        TruthHelper.assertThatApk(apkTest)
                .doesNotContainClass("Lcom/android/tests/jarDep/JarDependencyUtil;");
    }
}
