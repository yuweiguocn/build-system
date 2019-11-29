/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.tasks.factory;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.android.build.gradle.internal.scope.AnchorOutputType.ALL_CLASSES;

import com.android.annotations.NonNull;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.VariantAwareTask;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.core.VariantType;
import java.io.File;
import java.util.Objects;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.ConfigurableReport;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestTaskReports;

/** Patched version of {@link Test} that we need to use for local unit tests support. */
public class AndroidUnitTest extends Test implements VariantAwareTask {

    private String sdkPlatformDirPath;
    private Provider<Directory> mergedManifest;
    private BuildableArtifact resCollection;
    private BuildableArtifact assetsCollection;
    private String variantName;

    @Internal
    @NonNull
    @Override
    public String getVariantName() {
        return variantName;
    }

    @Override
    public void setVariantName(@NonNull String name) {
        variantName = name;
    }

    @InputFiles
    @Optional
    public BuildableArtifact getResCollection() {
        return resCollection;
    }

    @InputFiles
    @Optional
    public BuildableArtifact getAssetsCollection() {
        return assetsCollection;
    }

    @Input
    public String getSdkPlatformDirPath() {
        return sdkPlatformDirPath;
    }

    @InputFiles
    public Provider<Directory> getMergedManifest() {
        return mergedManifest;
    }

    /** Configuration Action for a JavaCompile task. */
    public static class CreationAction extends VariantTaskCreationAction<AndroidUnitTest> {

        public CreationAction(@NonNull VariantScope scope) {
            super(scope);
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName(VariantType.UNIT_TEST_PREFIX);
        }

        @NonNull
        @Override
        public Class<AndroidUnitTest> getType() {
            return AndroidUnitTest.class;
        }

        @Override
        public void configure(@NonNull AndroidUnitTest task) {
            super.configure(task);
            VariantScope scope = getVariantScope();

            final TestVariantData variantData = (TestVariantData) scope.getVariantData();
            final BaseVariantData testedVariantData =
                    (BaseVariantData) variantData.getTestedVariantData();

            // we run by default in headless mode, so the forked JVM doesn't steal focus.
            task.systemProperty("java.awt.headless", "true");

            task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
            task.setDescription(
                    "Run unit tests for the "
                            + testedVariantData.getVariantConfiguration().getFullName()
                            + " build.");

            task.setTestClassesDirs(scope.getArtifacts().getFinalArtifactFiles(ALL_CLASSES).get());

            boolean includeAndroidResources =
                    scope.getGlobalScope()
                            .getExtension()
                            .getTestOptions()
                            .getUnitTests()
                            .isIncludeAndroidResources();

            task.setClasspath(computeClasspath(includeAndroidResources));
            task.sdkPlatformDirPath =
                    scope.getGlobalScope().getAndroidBuilder().getTarget().getLocation();

            // if android resources are meant to be accessible, then we need to make sure
            // changes to them trigger a new run of the tasks
            VariantScope testedScope = testedVariantData.getScope();
            if (includeAndroidResources) {
                task.assetsCollection =
                        testedScope
                                .getArtifacts()
                                .getFinalArtifactFiles(InternalArtifactType.MERGED_ASSETS);
                boolean enableBinaryResources =
                        scope.getGlobalScope()
                                .getProjectOptions()
                                .get(BooleanOption.ENABLE_UNIT_TEST_BINARY_RESOURCES);
                if (enableBinaryResources) {
                    task.resCollection =
                            scope.getArtifacts()
                                    .getFinalArtifactFiles(InternalArtifactType.APK_FOR_LOCAL_TEST);
                } else {
                    task.resCollection =
                            scope.getArtifacts()
                                    .getFinalArtifactFiles(InternalArtifactType.MERGED_RES);
                }
            }
            task.mergedManifest =
                    testedScope
                            .getArtifacts()
                            .getFinalProduct(InternalArtifactType.MERGED_MANIFESTS);

            // Put the variant name in the report path, so that different testing tasks don't
            // overwrite each other's reports. For component model plugin, the report tasks are not
            // yet configured.  We get a hardcoded value matching Gradle's default. This will
            // eventually be replaced with the new Java plugin.
            TestTaskReports testTaskReports = task.getReports();
            ConfigurableReport xmlReport = testTaskReports.getJunitXml();
            xmlReport.setDestination(
                    new File(scope.getGlobalScope().getTestResultsFolder(), task.getName()));

            ConfigurableReport htmlReport = testTaskReports.getHtml();
            htmlReport.setDestination(
                    new File(scope.getGlobalScope().getTestReportFolder(), task.getName()));

            scope.getGlobalScope()
                    .getExtension()
                    .getTestOptions()
                    .getUnitTests()
                    .applyConfiguration(task);
        }

        @NonNull
        private ConfigurableFileCollection computeClasspath(boolean includeAndroidResources) {
            VariantScope scope = getVariantScope();

            ConfigurableFileCollection collection = scope.getGlobalScope().getProject().files();

            BuildArtifactsHolder artifacts = scope.getArtifacts();
            // the test classpath is made up of:
            // - the config file
            if (includeAndroidResources) {
                collection.from(
                        artifacts.getFinalArtifactFiles(
                                InternalArtifactType.UNIT_TEST_CONFIG_DIRECTORY));
            }
            // - the test component classes and java_res
            collection.from(artifacts.getFinalArtifactFiles(ALL_CLASSES).get());
            // TODO is this the right thing? this doesn't include the res merging via transform AFAIK
            collection.from(artifacts.getFinalArtifactFiles(InternalArtifactType.JAVA_RES));

            // - the runtime dependencies for both CLASSES and JAVA_RES type
            collection.from(
                    scope.getArtifactFileCollection(RUNTIME_CLASSPATH, ALL, CLASSES));
            collection.from(
                    scope.getArtifactFileCollection(
                            RUNTIME_CLASSPATH,
                            ALL,
                            ArtifactType.JAVA_RES));

            // The separately compile R class, if applicable.
            VariantScope testedScope =
                    Objects.requireNonNull(scope.getTestedVariantData()).getScope();
            if (testedScope
                    .getArtifacts()
                    .hasArtifact(InternalArtifactType.COMPILE_ONLY_NOT_NAMESPACED_R_CLASS_JAR)) {
                collection.from(
                        testedScope
                                .getArtifacts()
                                .getFinalArtifactFiles(
                                        InternalArtifactType
                                                .COMPILE_ONLY_NOT_NAMESPACED_R_CLASS_JAR));
            }

            // Any additional or requested optional libraries
            collection.from(
                    scope.getGlobalScope()
                            .getProject()
                            .files(
                                    scope.getGlobalScope()
                                            .getAndroidBuilder()
                                            .computeAdditionalAndRequestedOptionalLibraries()));


            // Mockable JAR is last, to make sure you can shadow the classes with
            // dependencies.
            collection.from(scope.getGlobalScope().getMockableJarArtifact());
            return collection;
        }
    }
}
