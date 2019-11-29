package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertWithMessage;

import com.android.annotations.NonNull;
import com.android.build.OutputFile;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.build.gradle.integration.common.utils.VariantOutputUtils;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.android.testutils.TestUtils;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for class densitySplitInL . */
public class DensitySplitInLTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("densitySplitInL").create();

    private static ProjectBuildOutput outputModel;

    @BeforeClass
    public static void setUp() throws Exception {
        outputModel = project.executeAndReturnOutputModel("clean", "assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        outputModel = null;
    }

    @Test
    public void checkSplitOutputs() {
        // build a set of expected outputs
        Set<String> expected = Sets.newHashSetWithExpectedSize(5);
        expected.add(null);
        expected.add("mdpi");
        expected.add("hdpi");
        expected.add("xhdpi");
        expected.add("xxhdpi");

        Collection<? extends OutputFile> outputs = getOutputs(outputModel);
        assertThat(outputs).hasSize(5);
        for (OutputFile outputFile : outputs) {
            String densityFilter = VariantOutputUtils.getFilter(outputFile, OutputFile.DENSITY);
            if (densityFilter == null) {
                assertThat(outputFile.getOutputType()).contains(OutputFile.MAIN);
            } else {
                assertThat(outputFile.getOutputType()).contains(OutputFile.SPLIT);
            }
            expected.remove(densityFilter);
        }

        // this checks we didn't miss any expected output.
        assertThat(expected).isEmpty();
    }

    @Test
    public void checkAddingDensityIncrementally() throws Exception {
        // get the last modified time of the initial APKs so we can make sure incremental build
        // does not rebuild things unnecessarily.
        waitForSystemTicks();
        Map<String, Long> lastModifiedTimePerDensity =
                getApkModifiedTimePerDensity(getOutputs(outputModel));

        TemporaryProjectModification.doTest(
                project,
                modifiedProject -> {
                    modifiedProject.replaceInFile(
                            "build.gradle",
                            "exclude \"ldpi\", \"tvdpi\", \"xxxhdpi\"",
                            "exclude \"ldpi\", \"tvdpi\"");
                    ProjectBuildOutput incrementalModel =
                            project.executeAndReturnOutputModel("assembleDebug");

                    waitForSystemTicks();
                    Collection<? extends OutputFile> outputs = getOutputs(incrementalModel);
                    assertThat(outputs).hasSize(6);

                    List<OutputFile> xxxHDPIOutputs =
                            outputs.stream()
                                    .filter(constainsDensityFilter("^xxxhdpi$"))
                                    .collect(Collectors.toList());
                    assertThat(xxxHDPIOutputs).hasSize(1);

                    Map<String, Long> newTimePerDensity = getApkModifiedTimePerDensity(outputs);
                    newTimePerDensity.remove(
                            getArtifactKeyName(Iterables.getOnlyElement(xxxHDPIOutputs)));
                    assertWithMessage("initial output changed")
                            .that(newTimePerDensity)
                            .containsExactlyEntriesIn(lastModifiedTimePerDensity);
                });
    }

    @Test
    public void checkDeletingDensityIncrementally() throws Exception {
        // get the last modified time of the initial APKs so we can make sure incremental build
        // does not rebuild things unnecessarily.
        waitForSystemTicks();
        Map<String, Long> lastModifiedTimePerDensity =
                getApkModifiedTimePerDensity(getOutputs(outputModel));

        TemporaryProjectModification.doTest(
                project,
                modifiedProject -> {
                    modifiedProject.replaceInFile(
                            "build.gradle",
                            "exclude \"ldpi\", \"tvdpi\", \"xxxhdpi\"",
                            "exclude \"ldpi\", \"tvdpi\", \"xxxhdpi\", \"xxhdpi\"");
                    ProjectBuildOutput incrementalModel =
                            project.executeAndReturnOutputModel("assembleDebug");

                    waitForSystemTicks();
                    Collection<? extends OutputFile> outputs = getOutputs(incrementalModel);
                    assertThat(outputs).hasSize(4);

                    List<OutputFile> xxHDPIOutputs =
                            outputs.stream()
                                    .filter(constainsDensityFilter("^xxhdpi$"))
                                    .collect(Collectors.toList());
                    assertThat(xxHDPIOutputs).isEmpty();

                    Map<String, Long> newTimePerDensity = getApkModifiedTimePerDensity(outputs);
                    // We're removing from the original map, since it shouldn't be present
                    // in the new one.
                    lastModifiedTimePerDensity.remove("SPLITxxhdpi");
                    // We also remove the main APK from both as they are expected to change:
                    lastModifiedTimePerDensity.remove("MAINnull");
                    newTimePerDensity.remove("MAINnull");
                    assertWithMessage("initial output changed")
                            .that(newTimePerDensity)
                            .containsExactlyEntriesIn(lastModifiedTimePerDensity);
                });
    }

    private static Collection<? extends OutputFile> getOutputs(ProjectBuildOutput outputModel) {
        VariantBuildOutput debugOutput =
                ProjectBuildOutputUtils.getDebugVariantBuildOutput(outputModel);

        Collection<OutputFile> outputFiles = debugOutput.getOutputs();

        // with pure splits, all split have the same version code.
        outputFiles.forEach(output -> assertThat(output.getVersionCode()).isEqualTo(12));

        return outputFiles;
    }

    @NonNull
    private static Map<String, Long> getApkModifiedTimePerDensity(
            Collection<? extends OutputFile> outputs) {
        return outputs.stream()
                .collect(
                        Collectors.toMap(
                                o -> getArtifactKeyName(o), o -> o.getOutputFile().lastModified()));
    }

    @NonNull
    private static String getArtifactKeyName(OutputFile outputFile) {
        return outputFile.getOutputType()
                + VariantOutputUtils.getFilter(outputFile, OutputFile.DENSITY);

    }

    private static Predicate<OutputFile> constainsDensityFilter(String filterRegex) {
        return outputFile -> {
            String filter = VariantOutputUtils.getFilter(outputFile, OutputFile.DENSITY);
            return filter != null && filter.matches(filterRegex);
        };
    }

    // b/113323972 - Let's wait for a few more system ticks before trying to read the files
    // metadata. See if this helps with the flakiness of last modified times.
    private static void waitForSystemTicks() {
        for (int i = 0; i < 5; i++) {
            try {
                TestUtils.waitForFileSystemTick();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
