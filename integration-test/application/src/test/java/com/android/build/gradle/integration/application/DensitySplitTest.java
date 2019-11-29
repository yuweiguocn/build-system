package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.build.gradle.integration.common.utils.VariantOutputUtils;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.android.testutils.apk.Apk;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for densitySplit. */
public class DensitySplitTest {
    private static ProjectBuildOutput outputModel;

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("densitySplit").create();

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        outputModel = project.executeAndReturnOutputModel("clean", "assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        outputModel = null;
    }

    @Test
    public void testPackaging() throws IOException {
        VariantBuildOutput debugOutput =
                ProjectBuildOutputUtils.getDebugVariantBuildOutput(outputModel);

        Collection<OutputFile> outputFiles = debugOutput.getOutputs();
        assertThat(outputFiles).hasSize(5);

        Apk mdpiApk = project.getApk("mdpi", GradleTestProject.ApkType.DEBUG);
        assertThat(mdpiApk).contains("res/drawable-mdpi-v4/other.png");

    }

    @Test
    public void checkVersionCodeInApk() throws IOException {
        Apk universalApk = project.getApk("universal", GradleTestProject.ApkType.DEBUG);
        assertThat(universalApk).hasVersionCode(112);
        assertThat(universalApk).hasVersionName("version 112");

        Apk mdpiApk = project.getApk("mdpi", GradleTestProject.ApkType.DEBUG);
        assertThat(mdpiApk).hasVersionCode(212);
        assertThat(mdpiApk).hasVersionName("version 212");

        Apk hdpiApk = project.getApk("hdpi", GradleTestProject.ApkType.DEBUG);
        assertThat(hdpiApk).hasVersionCode(312);
        assertThat(hdpiApk).hasVersionName("version 312");

        Apk xhdpiApk = project.getApk("xhdpi", GradleTestProject.ApkType.DEBUG);
        assertThat(xhdpiApk).hasVersionCode(412);
        assertThat(xhdpiApk).hasVersionName("version 412");

        Apk xxhdiApk = project.getApk("xxhdpi", GradleTestProject.ApkType.DEBUG);
        assertThat(xxhdiApk).hasVersionCode(512);
        assertThat(xxhdiApk).hasVersionName("version 512");
    }

    @Test
    public void checkVersionCodeInModel() {
        VariantBuildOutput debugOutput =
                ProjectBuildOutputUtils.getDebugVariantBuildOutput(outputModel);

        Collection<OutputFile> debugOutputs = debugOutput.getOutputs();
        assertEquals(5, debugOutputs.size());

        // build a map of expected outputs and their versionCode
        Map<String, Integer> expected = Maps.newHashMapWithExpectedSize(5);
        expected.put(null, 112);
        expected.put("mdpi", 212);
        expected.put("hdpi", 312);
        expected.put("xhdpi", 412);
        expected.put("xxhdpi", 512);

        assertEquals(5, debugOutputs.size());
        for (OutputFile output : debugOutputs) {
            assertEquals(VariantOutput.FULL_SPLIT, output.getMainOutputFile().getOutputType());
            Collection<? extends OutputFile> outputFiles = output.getOutputs();
            assertEquals(1, outputFiles.size());
            assertNotNull(output.getMainOutputFile());

            String densityFilter =
                    VariantOutputUtils.getFilter(output.getMainOutputFile(), VariantOutput.DENSITY);
            Integer value = expected.get(densityFilter);
            // this checks we're not getting an unexpected output.
            assertNotNull(
                    "Check Valid output: " + (densityFilter == null ? "universal" : densityFilter),
                    value);

            assertEquals(value.intValue(), output.getVersionCode());
            expected.remove(densityFilter);
        }

        // this checks we didn't miss any expected output.
        assertTrue(expected.isEmpty());
    }
}
