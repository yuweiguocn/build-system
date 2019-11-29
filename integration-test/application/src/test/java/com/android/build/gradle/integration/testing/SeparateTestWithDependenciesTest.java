package com.android.build.gradle.integration.testing;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.testutils.apk.Apk;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Test separate test module that tests an application with some complicated dependencies : - the
 * app imports a library importing a jar file itself. - use minification.
 */
public class SeparateTestWithDependenciesTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("separateTestModuleWithDependencies")
                    .create();

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkAppContainsAllDependentClasses()
            throws Exception {
        project.execute("clean", "assemble");
        try (Apk apk = project.getSubproject("app").getApk("debug")) {
            TruthHelper.assertThatApk(apk)
                    .containsClass("Lcom/android/tests/jarDep/JarDependencyUtil;");
        }

        try (Apk apk = project.getSubproject("app").getApk("minified")) {
            TruthHelper.assertThatApk(apk)
                    .doesNotContainClass("Lcom/android/tests/jarDep/JarDependencyUtil;");
        }
    }

    @Test
    public void checkTestAppDoesNotContainAnyMinifiedApplicationDependentClasses()
            throws Exception {
        project.execute("clean", ":test:assemble");
        try (Apk apk = project.getSubproject("test").getApk("debug")) {
            TruthHelper.assertThatApk(apk)
                    .doesNotContainClass("Lcom/android/tests/jarDep/JarDependencyUtil;");
        }

        try (Apk apk = project.getSubproject("test").getApk("minified")) {
            TruthHelper.assertThatApk(apk)
                    .doesNotContainClass("Lcom/android/tests/jarDep/JarDependencyUtil;");
        }
    }
}
