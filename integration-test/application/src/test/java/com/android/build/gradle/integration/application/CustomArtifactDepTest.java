package com.android.build.gradle.integration.application;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import java.io.IOException;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for customArtifactDep. */
public class CustomArtifactDepTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("customArtifactDep").create();

    @Test
    public void testModel() throws IOException {
        ModelContainer<AndroidProject> models = project.model().fetchAndroidProjects();
        AndroidProject appModel = models.getOnlyModelMap().get(":app");
        assertNotNull("Module app null-check", appModel);

        Collection<Variant> variants = appModel.getVariants();
        assertEquals("Variant count", 2, variants.size());

        Variant variant = AndroidProjectUtils.getVariantByName(appModel, "release");

        AndroidArtifact mainInfo = variant.getMainArtifact();
        assertNotNull("Main Artifact null-check", mainInfo);

        DependencyGraphs dependencyGraph = mainInfo.getDependencyGraphs();
        assertNotNull("Dependencies null-check", dependencyGraph);

        assertEquals("jar dep count", 1, dependencyGraph.getCompileDependencies().size());
    }
}
