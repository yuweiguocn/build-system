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

package com.android.build.gradle.tasks;

import static com.android.build.gradle.internal.cxx.configure.JsonGenerationAbiConfigurationKt.createJsonGenerationAbiConfiguration;
import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.cxx.configure.JsonGenerationAbiConfiguration;
import com.android.build.gradle.internal.cxx.configure.JsonGenerationVariantConfiguration;
import com.android.build.gradle.internal.cxx.configure.NativeBuildSystemVariantConfig;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.errors.EvalIssueReporter;
import com.android.repository.Revision;
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.LocalPackage;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.testutils.TestUtils;
import com.android.utils.ILogger;
import com.google.common.collect.Lists;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

public class CmakeAndroidNinjaExternalNativeJsonGeneratorTest {
    File sdkDirectory;
    int minSdkVersion;
    String variantName;
    List<JsonGenerationAbiConfiguration> abis;
    AndroidBuilder androidBuilder;
    ILogger logger;
    EvalIssueReporter issueReporter;
    File sdkFolder;
    File ndkFolder;
    File soFolder;
    File objFolder;
    File jsonFolder;
    File makeFile;
    File cmakeFolder;
    boolean debuggable;
    List<String> buildArguments;
    List<String> cFlags;
    List<String> cppFlags;
    List<File> nativeBuildConfigurationsJsons;
    GradleBuildVariant.Builder stats;


    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        SdkHandler.setTestSdkFolder(TestUtils.getSdk());

        sdkDirectory = TestUtils.getSdk();
        minSdkVersion = 123;
        variantName = "dummy variant name";
        abis = Lists.newArrayList();
        objFolder = tmpFolder.newFolder("obj");
        jsonFolder = tmpFolder.newFolder("json");
        for (Abi abi : Abi.values()) {
            abis.add(
                    createJsonGenerationAbiConfiguration(
                            abi,
                            "debug",
                            jsonFolder,
                            objFolder,
                            NativeBuildSystem.CMAKE,
                            31));
        }
        androidBuilder = Mockito.mock(AndroidBuilder.class);
        logger = Mockito.mock(ILogger.class);
        issueReporter = Mockito.mock(EvalIssueReporter.class);
        sdkFolder = TestUtils.getSdk();
        ndkFolder = TestUtils.getNdk();
        soFolder = Mockito.mock(File.class);
        makeFile = new File(tmpFolder.newFolder("folder"), "CMakeLists.txt");
        stats = GradleBuildVariant.newBuilder();
        AndroidSdkHandler sdk = AndroidSdkHandler.getInstance(sdkDirectory);
        LocalPackage cmakePackage =
                sdk.getLatestLocalPackageForPrefix(
                        SdkConstants.FD_CMAKE, null, true, new ConsoleProgressIndicator());
        if (cmakePackage != null) {
            cmakeFolder = cmakePackage.getLocation();
        }

        debuggable = true;
        buildArguments =
                Arrays.asList("build-argument-foo", "build-argument-bar", "build-argument-baz");
        cFlags = Arrays.asList("c-flags1", "c-flag2");
        cppFlags = Arrays.asList("cpp-flags1", "cpp-flag2");
        nativeBuildConfigurationsJsons = Mockito.mock(List.class);
    }

    @Test
    public void testGetCacheArguments() {
        JsonGenerationVariantConfiguration config =
                new JsonGenerationVariantConfiguration(
                        new File("."),
                        new NativeBuildSystemVariantConfig(
                                new HashSet<>(), new HashSet<>(), buildArguments, cFlags, cppFlags),
                        variantName,
                        makeFile,
                        sdkFolder,
                        ndkFolder,
                        soFolder,
                        objFolder,
                        jsonFolder,
                        debuggable,
                        abis,
                        Revision.parseRevision("15"),
                        nativeBuildConfigurationsJsons,
                        new File("./compiler-settings-cache"),
                        true);
        Mockito.when(androidBuilder.getLogger()).thenReturn(logger);
        Mockito.when(androidBuilder.getIssueReporter()).thenReturn(issueReporter);
        CmakeAndroidNinjaExternalNativeJsonGenerator cmakeAndroidNinjaStrategy =
                new CmakeAndroidNinjaExternalNativeJsonGenerator(
                        config, new HashSet<>(), androidBuilder, cmakeFolder, stats);
        JsonGenerationAbiConfiguration abiConfig =
                createJsonGenerationAbiConfiguration(
                        Abi.X86,
                        "debug",
                        jsonFolder,
                        objFolder,
                        NativeBuildSystem.CMAKE,
                        12);
        List<String> cacheArguments = cmakeAndroidNinjaStrategy.getProcessBuilderArgs(abiConfig);

        assertThat(cacheArguments).isNotEmpty();
        assertThat(cacheArguments)
                .contains(
                        String.format(
                                "-DANDROID_NDK=%s", cmakeAndroidNinjaStrategy.getNdkFolder()));
        assertThat(cacheArguments).contains("-DCMAKE_BUILD_TYPE=Debug");
        assertThat(cacheArguments).contains("-DCMAKE_C_FLAGS=c-flags1 c-flag2");
        assertThat(cacheArguments).contains("-DCMAKE_CXX_FLAGS=cpp-flags1 cpp-flag2");
        assertThat(cacheArguments).contains("-GAndroid Gradle - Ninja");

        assertThat(cacheArguments).contains("build-argument-foo");
        assertThat(cacheArguments).contains("build-argument-bar");
        assertThat(cacheArguments).contains("build-argument-baz");

        // Ensure that the buildArguments (supplied by the user) is added to the end of the argument
        // list.
        // If cacheArguments = 1,2,3,4,a,b,c and buildArguments = a,b,c, we just compare where in
        // the cacheArguments does buildArguments sublist is and verify if it's indeed at the end.
        int indexOfSubset = Collections.indexOfSubList(cacheArguments, buildArguments);
        assertThat(cacheArguments.size() - indexOfSubset).isEqualTo(buildArguments.size());

        // Check statistics carrier
        assertThat(stats.getNativeBuildSystemType())
                .isEqualTo(GradleNativeAndroidModule.NativeBuildSystemType.CMAKE);
    }
}
