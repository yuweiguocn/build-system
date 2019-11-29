package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;
import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static com.android.builder.model.AndroidProject.ARTIFACT_UNIT_TEST;
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.fixture.SourceSetContainerUtils;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ArtifactMetaData;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SourceProviderContainer;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for artifactApi. */
public class ArtifactApiTest {

    private static final int DEFAULT_EXTRA_JAVA_ARTIFACTS = 1;
    private static final String CUSTOM_ARTIFACT_NAME = "__test__";

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("artifactApi").create();

    private static ModelContainer<AndroidProject> model;

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
    public void checkMetadataInfoInModel() {
        // check the Artifact Meta Data
        final AndroidProject androidProject = model.getOnlyModel();
        Collection<ArtifactMetaData> extraArtifacts = androidProject.getExtraArtifacts();
        assertNotNull("Extra artifact collection null-check", extraArtifacts);
        assertThat(extraArtifacts).hasSize((int) DEFAULT_EXTRA_JAVA_ARTIFACTS + 2);

        // query to validate presence
        ArtifactMetaData metaData =
                AndroidProjectUtils.getArtifactMetaData(androidProject, ARTIFACT_ANDROID_TEST);

        // get the custom one.
        ArtifactMetaData extraArtifactMetaData =
                AndroidProjectUtils.getArtifactMetaData(androidProject, CUSTOM_ARTIFACT_NAME);
        assertFalse("custom extra meta data is Test check", extraArtifactMetaData.isTest());
        assertEquals(
                "custom extra meta data type check",
                ArtifactMetaData.TYPE_JAVA,
                extraArtifactMetaData.getType());
    }

    @Test
    public void checkBuildTypesContainExtraSourceProviderArtifacts() {
        final AndroidProject androidProject = model.getOnlyModel();

        // get the tested build types as it impacts the number of sourcesets.
        String testedBuildType = AndroidProjectUtils.findTestedBuildType(androidProject);

        // check the extra source provider on the build Types.
        for (BuildTypeContainer btContainer : androidProject.getBuildTypes()) {
            final String buildTypeName = btContainer.getBuildType().getName();
            String name = "Extra source provider containers for build type: " + buildTypeName;
            Collection<SourceProviderContainer> extraSourceProviderContainers =
                    btContainer.getExtraSourceProviders();

            assertThat(extraSourceProviderContainers).named(name).isNotNull();
            final Set<String> extraSourceProviderNames =
                    extraSourceProviderContainers
                            .stream()
                            .map(SourceProviderContainer::getArtifactName)
                            .collect(Collectors.toSet());

            if (buildTypeName.equals(testedBuildType)) {
                assertThat(extraSourceProviderNames)
                        .named(name)
                        .containsExactly(
                                CUSTOM_ARTIFACT_NAME, ARTIFACT_UNIT_TEST, ARTIFACT_ANDROID_TEST);

            } else {
                assertThat(extraSourceProviderNames)
                        .named(name)
                        .containsExactly(CUSTOM_ARTIFACT_NAME, ARTIFACT_UNIT_TEST);
            }

            SourceProviderContainer extraSourceProvideContainer =
                    SourceSetContainerUtils.getExtraSourceProviderContainer(
                            btContainer, CUSTOM_ARTIFACT_NAME);

            name = "Extra artifact source provider for " + buildTypeName;
            assertThat(extraSourceProvideContainer).named(name).isNotNull();
            assertThat(extraSourceProvideContainer.getSourceProvider().getManifestFile().getPath())
                    .named(name)
                    .isEqualTo("buildType:" + buildTypeName);
        }
    }

    @Test
    public void checkProductFlavorsContainExtraSourceProvider() {
        // check the extra source provider on the product flavors.
        for (ProductFlavorContainer pfContainer : model.getOnlyModel().getProductFlavors()) {
            String name = pfContainer.getProductFlavor().getName();
            Collection<SourceProviderContainer> extraSourceProviderContainers =
                    pfContainer.getExtraSourceProviders();
            assertNotNull(
                    "Extra source provider container for product flavor '" + name + "' null-check",
                    extraSourceProviderContainers);
            assertEquals(
                    "Extra artifact source provider container for product flavor size '"
                            + name
                            + "' check",
                    3,
                    extraSourceProviderContainers.size());

            // query to validate presence
            SourceSetContainerUtils.getExtraSourceProviderContainer(
                    pfContainer, ARTIFACT_ANDROID_TEST);

            SourceProviderContainer sourceProviderContainer =
                    SourceSetContainerUtils.getExtraSourceProviderContainer(
                            pfContainer, CUSTOM_ARTIFACT_NAME);
            assertNotNull(
                    "Custom source provider container for " + name + " null check",
                    sourceProviderContainer);

            assertEquals(
                    "Custom artifact source provider for " + name + " name check",
                    CUSTOM_ARTIFACT_NAME,
                    sourceProviderContainer.getArtifactName());

            assertEquals(
                    "Extra artifact source provider for " + name + " value check",
                    "productFlavor:" + name,
                    sourceProviderContainer.getSourceProvider().getManifestFile().getPath());
        }

    }

    @Test
    public void checkExtraArtifactIsInVariant() {
        LibraryGraphHelper helper = new LibraryGraphHelper(model);

        for (Variant variant : model.getOnlyModel().getVariants()) {
            String name = variant.getName();
            Collection<JavaArtifact> javaArtifacts = variant.getExtraJavaArtifacts();
            assertThat(javaArtifacts).hasSize((int) DEFAULT_EXTRA_JAVA_ARTIFACTS + 1);
            JavaArtifact javaArtifact =
                    javaArtifacts
                            .stream()
                            .filter(e -> e.getName().equals(CUSTOM_ARTIFACT_NAME))
                            .findFirst()
                            .orElseThrow(AssertionError::new);
            assertEquals("assemble:" + name, javaArtifact.getAssembleTaskName());
            assertEquals("compile:" + name, javaArtifact.getCompileTaskName());
            assertEquals(new File("classesFolder:" + name), javaArtifact.getClassesFolder());

            SourceProvider variantSourceProvider = javaArtifact.getVariantSourceProvider();
            assertNotNull(variantSourceProvider);
            assertEquals("provider:" + name, variantSourceProvider.getManifestFile().getPath());

            DependencyGraphs graph = javaArtifact.getDependencyGraphs();
            assertThat(helper.on(graph).withType(JAVA).asList()).isNotEmpty();
        }

    }

    @Test
    public void backwardsCompatible() throws Exception {
        // ATTENTION Author and Reviewers - please make sure required changes to the build file
        // are backwards compatible before updating this test.
        assertThat(TestFileUtils.sha1NormalizedLineEndings(project.file("build.gradle")))
                .isEqualTo("075b7b983ad2d77a378536f181f3cf17a758380c");
    }
}
