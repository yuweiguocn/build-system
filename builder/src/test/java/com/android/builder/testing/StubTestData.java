/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.builder.testing;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.testing.api.DeviceConfigProvider;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.sdklib.AndroidVersion;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/** Simple POJO implementation of {@link TestData}. */
public class StubTestData implements TestData {
    private String applicationId;
    private String testedApplicationId;
    private String instrumentationRunner;
    private String flavorName;
    private File testApk;
    private List<File> testDirectories = new ArrayList<>();
    private List<File> testedApks = new ArrayList<>();
    private Map<String, String> instrumentationRunnerArguments = new HashMap<>();
    private AndroidVersion minSdkVersion;
    private boolean animationsDisabled;
    private boolean testCoverageEnabled;
    private boolean isLibrary;

    public StubTestData(String applicationId, String instrumentationRunner) {
        this.applicationId = applicationId;
        this.instrumentationRunner = instrumentationRunner;
    }

    @Override
    public void loadFromMetadataFile(File metadataFile)
            throws ParserConfigurationException, SAXException, IOException {
        // Do nothing
    }

    @Override
    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    @Override
    public String getTestedApplicationId() {
        return testedApplicationId;
    }

    public void setTestedApplicationId(String testedApplicationId) {
        this.testedApplicationId = testedApplicationId;
    }

    @Override
    public String getInstrumentationRunner() {
        return instrumentationRunner;
    }

    public void setInstrumentationRunner(String instrumentationRunner) {
        this.instrumentationRunner = instrumentationRunner;
    }

    @Override
    public String getFlavorName() {
        return flavorName;
    }

    public void setFlavorName(String flavorName) {
        this.flavorName = flavorName;
    }

    @Override
    public File getTestApk() {
        return testApk;
    }

    public void setTestApk(File testApk) {
        this.testApk = testApk;
    }

    @Override
    public List<File> getTestDirectories() {
        return testDirectories;
    }

    public void setTestDirectories(List<File> testDirectories) {
        this.testDirectories = testDirectories;
    }

    public List<File> getTestedApks() {
        return testedApks;
    }

    public void setTestedApks(List<File> testedApks) {
        this.testedApks = testedApks;
    }

    @Override
    public Map<String, String> getInstrumentationRunnerArguments() {
        return instrumentationRunnerArguments;
    }

    public void setInstrumentationRunnerArguments(
            Map<String, String> instrumentationRunnerArguments) {
        this.instrumentationRunnerArguments = instrumentationRunnerArguments;
    }

    @Override
    public AndroidVersion getMinSdkVersion() {
        return minSdkVersion;
    }

    public void setMinSdkVersion(AndroidVersion minSdkVersion) {
        this.minSdkVersion = minSdkVersion;
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
        return testCoverageEnabled;
    }

    public void setTestCoverageEnabled(boolean testCoverageEnabled) {
        this.testCoverageEnabled = testCoverageEnabled;
    }

    @Override
    public boolean isLibrary() {
        return isLibrary;
    }

    @NonNull
    @Override
    public ImmutableList<File> getTestedApks(
            @NonNull ProcessExecutor processExecutor,
            @Nullable File splitSelectExe,
            @NonNull DeviceConfigProvider deviceConfigProvider,
            ILogger logger)
            throws ProcessException {
        return ImmutableList.copyOf(testedApks);
    }

    public void setLibrary(boolean library) {
        isLibrary = library;
    }
}
