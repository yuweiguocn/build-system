package com.android.build.gradle.integration.testing;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;
import static com.android.builder.core.BuilderConstants.DEBUG;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.VariantUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import java.io.IOException;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for testWithDep that loads the model but doesn't build. */
public class TestWithDepTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("testWithDep").create();

    public static ModelContainer<AndroidProject> model;

    @BeforeClass
    public static void setUp() throws IOException {
        model = project.model().fetchAndroidProjects();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void checkThereIsADepOnTheTestVariant() {
        LibraryGraphHelper helper = new LibraryGraphHelper(model);
        Variant debugVariant = AndroidProjectUtils.getVariantByName(model.getOnlyModel(), DEBUG);

        AndroidArtifact testArtifact = VariantUtils.getAndroidTestArtifact(debugVariant);

        DependencyGraphs graph = testArtifact.getDependencyGraphs();
        assertThat(
                        helper.on(graph)
                                .withType(JAVA)
                                .asList()
                                .stream()
                                .map(graphItem -> graphItem.getArtifactAddress())
                                .map(dependency -> dependency.substring(0, dependency.indexOf(':')))
                                .collect(Collectors.toList()))
                .containsExactly(
                        "com.google.guava",
                        "junit",
                        "org.hamcrest",
                        "com.android.support",
                        "net.sf.kxml");
    }
}
