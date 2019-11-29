/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.test;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.core.VariantConfiguration;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.builder.model.SourceProvider;
import com.android.builder.testing.TestData;
import com.android.sdklib.AndroidVersion;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.gradle.api.file.FileCollection;

/**
 * Common implementation of {@link TestData} for embedded test projects (in androidTest folder)
 * and separate module test projects.
 */
public abstract class AbstractTestDataImpl implements TestData {

    @NonNull
    private final VariantConfiguration<?, ?, ?> testVariantConfig;

    @NonNull
    private Map<String, String> extraInstrumentationTestRunnerArgs;

    private boolean animationsDisabled;

    @NonNull protected final BuildableArtifact testApkDir;

    @Nullable protected final BuildableArtifact testedApksDir;

    public AbstractTestDataImpl(
            @NonNull VariantConfiguration<?, ?, ?> testVariantConfig,
            @NonNull BuildableArtifact testApkDir,
            @Nullable BuildableArtifact testedApksDir) {
        this.testVariantConfig = checkNotNull(testVariantConfig);
        this.extraInstrumentationTestRunnerArgs = Maps.newHashMap();
        this.testApkDir = testApkDir;
        this.testedApksDir = testedApksDir;
    }

    @NonNull
    @Override
    public String getInstrumentationRunner() {
        return testVariantConfig.getInstrumentationRunner();
    }

    @NonNull
    @Override
    public Map<String, String> getInstrumentationRunnerArguments() {
        return ImmutableMap.<String, String>builder()
                .putAll(testVariantConfig.getInstrumentationRunnerArguments())
                .putAll(extraInstrumentationTestRunnerArgs)
                .build();
    }

    public void setExtraInstrumentationTestRunnerArgs(
            @NonNull Map<String, String> extraInstrumentationTestRunnerArgs) {
        this.extraInstrumentationTestRunnerArgs =
                ImmutableMap.copyOf(extraInstrumentationTestRunnerArgs);
    }

    @Override
    public boolean getAnimationsDisabled() {
        return animationsDisabled;
    }

    public void setAnimationsDisabled(boolean animationsDisabled) {
        this.animationsDisabled = animationsDisabled;
    }

    @Override
    public boolean isTestCoverageEnabled() {
        return testVariantConfig.isTestCoverageEnabled();
    }

    @NonNull
    @Override
    public AndroidVersion getMinSdkVersion() {
        return testVariantConfig.getMinSdkVersion();
    }

    @NonNull
    @Override
    public String getFlavorName() {
        return testVariantConfig.getFlavorName().toUpperCase(Locale.getDefault());
    }

    /**
     * Returns the directory containing the test APK as a {@link BuildableArtifact}.
     *
     * @return the directory containing the test APK
     */
    @NonNull
    public BuildableArtifact getTestApkDir() {
        return testApkDir;
    }

    /**
     * Returns the directory containing the tested APKs as a {@link FileCollection}, or null if the
     * test data is for testing a library.
     *
     * @return the directory containing the tested APKs, or null if the test data is for testing a
     *     library
     */
    @Nullable
    public BuildableArtifact getTestedApksDir() {
        return testedApksDir;
    }

    @Nullable
    public FileCollection getTestedApksFromBundle() {
        return null;
    }

    @NonNull
    @Override
    public final List<File> getTestDirectories() {
        // For now we check if there are any test sources. We could inspect the test classes and
        // apply JUnit logic to see if there's something to run, but that would not catch the case
        // where user makes a typo in a test name or forgets to inherit from a JUnit class
        ImmutableList.Builder<File> javaDirectories = ImmutableList.builder();
        for (SourceProvider sourceProvider : testVariantConfig.getSortedSourceProviders()) {
            javaDirectories.addAll(sourceProvider.getJavaDirectories());
        }
        return javaDirectories.build();
    }

    @NonNull
    @Override
    public File getTestApk() {
        BuildElements testApkOutputs =
                ExistingBuildElements.from(InternalArtifactType.APK, testApkDir);
        if (testApkOutputs.size() != 1) {
            throw new RuntimeException(
                    "Unexpected number of main APKs, expected 1, got  "
                            + testApkOutputs.size()
                            + ":"
                            + Joiner.on(",").join(testApkOutputs));
        }
        return testApkOutputs.iterator().next().getOutputFile();
    }
}
