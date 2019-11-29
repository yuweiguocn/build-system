package com.android.build.gradle.integration.application;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ApkHelper;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for @{applicationId} placeholder presence in library manifest files. Such placeholders
 * should be left intact until the library is merged into a consuming application with a known
 * application Id.
 */
public class ApplicationIdInLibsTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("applicationIdInLibsTest").create();

    @Test
    public void testLibPlaceholderSubstitutaionInFinalApk() throws Exception {
        Map<String, ProjectBuildOutput> outputModels =
                project.executeAndReturnOutputMultiModel("clean", "app:assembleDebug");
        assertThat(
                        checkPermissionPresent(
                                outputModels,
                                "'com.example.manifest_merger_example.flavor.permission.C2D_MESSAGE'"))
                .isTrue();

        TestFileUtils.searchAndReplace(
                project.file("app/build.gradle"),
                "manifest_merger_example.flavor",
                "manifest_merger_example.change");

        outputModels = project.executeAndReturnOutputMultiModel("clean", "app:assembleDebug");
        assertThat(
                        checkPermissionPresent(
                                outputModels,
                                "'com.example.manifest_merger_example.flavor.permission.C2D_MESSAGE'"))
                .isFalse();
        assertThat(
                        checkPermissionPresent(
                                outputModels,
                                "'com.example.manifest_merger_example.change.permission.C2D_MESSAGE'"))
                .isTrue();
    }

    private static boolean checkPermissionPresent(
            Map<String, ProjectBuildOutput> outputModels, String permission) {
        assertThat(outputModels).containsKey(":app");
        final ProjectBuildOutput projectBuildOutput = outputModels.get(":app");
        assertThat(projectBuildOutput).isNotNull();

        Collection<VariantBuildOutput> variantBuildOutputs =
                projectBuildOutput.getVariantsBuildOutput();
        assertThat(variantBuildOutputs).hasSize(2);

        // select the debug variant
        VariantBuildOutput debugBuildOutput =
                ProjectBuildOutputUtils.getVariantBuildOutput(projectBuildOutput, "flavorDebug");
        assertThat(debugBuildOutput.getOutputs()).hasSize(1);

        List<String> apkBadging =
                ApkHelper.getApkBadging(
                        Iterables.getOnlyElement(debugBuildOutput.getOutputs()).getOutputFile());

        for (String line : apkBadging) {
            if (line.contains("uses-permission: name=" + permission)) {
                return true;
            }

        }

        return false;
    }
}
