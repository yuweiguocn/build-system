/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.core.VariantConfiguration;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.internal.variant.TestedVariantData;
import com.android.builder.testing.TestData;
import com.android.builder.testing.api.DeviceConfigProvider;
import com.android.ide.common.build.SplitOutputMatcher;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/**
 * Implementation of {@link TestData} on top of a {@link TestVariantData}
 */
public class TestDataImpl extends AbstractTestDataImpl {

    @NonNull
    private final TestVariantData testVariantData;

    @NonNull
    private final VariantConfiguration testVariantConfig;

    public TestDataImpl(
            @NonNull TestVariantData testVariantData,
            @NonNull BuildableArtifact testApkDir,
            @Nullable BuildableArtifact testedApksDir) {
        super(testVariantData.getVariantConfiguration(), testApkDir, testedApksDir);
        this.testVariantData = testVariantData;
        this.testVariantConfig = testVariantData.getVariantConfiguration();
        if (testVariantData
                        .getOutputScope()
                        .getSplitsByType(VariantOutput.OutputType.FULL_SPLIT)
                        .size()
                > 1) {
            throw new RuntimeException("Multi-output in test variant not yet supported");
        }
    }

    @Override
    public void loadFromMetadataFile(File metadataFile)
            throws ParserConfigurationException, SAXException, IOException {
        // do nothing, there is nothing in the metadata file we cannot get from the tested scope.
    }

    @NonNull
    @Override
    public String getApplicationId() {
        return testVariantData.getApplicationId();
    }

    @Nullable
    @Override
    public String getTestedApplicationId() {
        return testVariantConfig.getTestedApplicationId();
    }

    @Override
    public boolean isLibrary() {
        TestedVariantData testedVariantData = testVariantData.getTestedVariantData();
        BaseVariantData testedVariantData2 = (BaseVariantData) testedVariantData;
        return testedVariantData2.getVariantConfiguration().getType().isAar();
    }

    @NonNull
    @Override
    public ImmutableList<File> getTestedApks(
            @NonNull ProcessExecutor processExecutor,
            @Nullable File splitSelectExe,
            @NonNull DeviceConfigProvider deviceConfigProvider,
            @NonNull ILogger logger) throws ProcessException {
        BaseVariantData testedVariantData =
                (BaseVariantData) testVariantData.getTestedVariantData();

        ImmutableList.Builder<File> apks = ImmutableList.builder();
        // FIX ME : there has to be a better way...
        Collection<OutputFile> splitOutputs =
                ImmutableList.copyOf(
                        ExistingBuildElements.from(
                                InternalArtifactType.APK,
                                testedVariantData
                                        .getScope()
                                        .getArtifacts()
                                        .getFinalArtifactFiles(InternalArtifactType.APK)));
        apks.addAll(
                SplitOutputMatcher.computeBestOutput(
                        processExecutor,
                        splitSelectExe,
                        deviceConfigProvider,
                        splitOutputs,
                        testedVariantData.getVariantConfiguration().getSupportedAbis()));
        return apks.build();
    }
}
