package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ApkHelper;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.VariantBuildOutputUtils;
import com.android.build.gradle.integration.common.utils.VariantOutputUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.VariantBuildOutput;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Tests that create pure splits for language but keep drawable resources in the main APK. */
public class CombinedLanguageWithDisabledDensityTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("combinedDensityAndLanguagePureSplits")
                    .create();

    @Before
    public void setup() throws IOException {
        AssumeUtil.assumeBuildToolsAtLeast(21);
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "            splits {\n"
                        + "                density {\n"
                        + "                    enable false\n"
                        + "                }\n"
                        + "            }\n"
                        + "        }\n");

    }

    @Test
    public void testCombinedDensityAndDisabledLanguagePureSplit() throws Exception {
        ProjectBuildOutput projectBuildOutput =
                project.executeAndReturnOutputModel("clean", "assembleDebug");
        VariantBuildOutput debugVariantOutput =
                ProjectBuildOutputUtils.getDebugVariantBuildOutput(projectBuildOutput);

        // build a set of expected outputs
        Set<String> expected = Sets.newHashSetWithExpectedSize(3);
        expected.add("en");
        expected.add("fr,fr-rBE");
        expected.add("fr-rCA");

        Collection<OutputFile> debugOutputs = debugVariantOutput.getOutputs();
        assertEquals(4, debugOutputs.size());
        for (OutputFile outputFile : debugOutputs) {
            String filter = VariantOutputUtils.getFilter(outputFile, VariantOutput.LANGUAGE);
            assertEquals(
                    filter == null ? VariantOutput.MAIN : VariantOutput.SPLIT,
                    outputFile.getOutputType());

            // with pure splits, all split have the same version code.
            assertEquals(12, outputFile.getVersionCode());
            if (filter != null) {
                expected.remove(filter);
            }

        }

        // this checks we didn't miss any expected output.
        assertTrue(expected.isEmpty());

        // check that our density resources are indeed packaged in the main APK.
        List<String> apkDump =
                ApkHelper.getApkBadging(
                        VariantBuildOutputUtils.getMainOutputFile(debugVariantOutput)
                                .getOutputFile());
        assertThat(apkDump).contains("densities: '160' '240' '320' '480'");

        AndroidProject model =
                project.model()
                        .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                        .fetchAndroidProjects()
                        .getOnlyModel();
        assertThat(model.getSyncIssues()).named("Sync Issues").hasSize(1);
        assertThat(Iterables.getOnlyElement(model.getSyncIssues()).getMessage())
                .contains(
                        "Configuration APKs are supported by the Google Play Store only when publishing Android Instant Apps. To instead generate stand-alone APKs for different device configurations, set generatePureSplits=false.");
    }

}
