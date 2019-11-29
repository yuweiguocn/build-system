package com.android.build.gradle.integration.testing;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Regression test for a infinite loop error in DependencyManager when importing a project with the
 * same local name as the requester.
 */
public class DuplicateModuleNameImportTest {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("duplicateNameImport").create();

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkBuild() throws Exception {
        // building should generate an exception
        try {
            project.execute("clean", "assemble");
            fail("build succeeded while it should have failed");
        } catch (Exception expected) {
            // look for the root exception which has been thrown by the gradle build process
            Throwable rootException = expected;
            while (rootException.getCause() != null) {
                rootException = rootException.getCause();
            }

            assertThat(rootException.getMessage())
                    .contains(
                            "Your project contains 2 or more modules with the same identification a.b.c:Project\n"
                                    + "at \":A:Project\" and \":B:Project\".\n"
                                    + "You must use different identification (either name or group) for each modules.");
        }
    }
}
