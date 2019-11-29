package com.android.build.gradle.integration.application;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Assemble tests for dependencies. */
public class DependenciesConnectedTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("dependencies").create();

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() throws IOException, InterruptedException {
        project.executeConnectedCheck();
    }
}
