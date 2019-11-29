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
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.external.cmake.server.CmakeInputsResult;
import com.android.build.gradle.external.cmake.server.CodeModel;
import com.android.build.gradle.external.cmake.server.CompileCommand;
import com.android.build.gradle.external.cmake.server.Target;
import com.android.build.gradle.external.cmake.server.receiver.InteractiveMessage;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.cxx.configure.JsonGenerationAbiConfiguration;
import com.android.build.gradle.internal.cxx.configure.JsonGenerationVariantConfiguration;
import com.android.build.gradle.internal.cxx.configure.NativeBuildSystemVariantConfig;
import com.android.build.gradle.internal.cxx.json.NativeLibraryValue;
import com.android.build.gradle.internal.cxx.json.NativeSourceFileValue;
import com.android.build.gradle.internal.cxx.json.StringTable;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.errors.EvalIssueReporter;
import com.android.repository.Revision;
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.LocalPackage;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.testutils.TestResources;
import com.android.testutils.TestUtils;
import com.android.utils.ILogger;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

public class CmakeServerExternalNativeJsonGeneratorTest {
    File sdkDirectory;
    NdkHandler ndkHandler;
    int minSdkVersion;
    String variantName;
    List<JsonGenerationAbiConfiguration> abis;
    AndroidBuilder androidBuilder;
    File sdkFolder;
    File ndkFolder;
    File soFolder;
    File objFolder;
    File jsonFolder;
    File makeFile;
    File cmakeFolder;
    File ninjaFolder;
    boolean debuggable;
    List<String> buildArguments;
    List<String> cFlags;
    List<String> cppFlags;
    List nativeBuildConfigurationsJsons;
    GradleBuildVariant.Builder stats;

    @Rule
    public final TemporaryFolder tmpFolder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        SdkHandler.setTestSdkFolder(TestUtils.getSdk());

        sdkDirectory = TestUtils.getSdk();
        ndkHandler = Mockito.mock(NdkHandler.class);
        minSdkVersion = 123;
        variantName = "dummy variant name";
        abis = Lists.newArrayList();
        objFolder = tmpFolder.newFolder("obj");
        File json = tmpFolder.newFile("json");
        for (Abi abi : Abi.values()) {
            abis.add(
                    createJsonGenerationAbiConfiguration(
                            abi,
                            "debug",
                            json,
                            objFolder,
                            NativeBuildSystem.CMAKE,
                            31));
        }
        androidBuilder = Mockito.mock(AndroidBuilder.class);
        sdkFolder = TestUtils.getSdk();
        ndkFolder = TestUtils.getNdk();
        soFolder = Mockito.mock(File.class);
        jsonFolder = getTestJsonFolder(); //Mockito.mock(File.class);
        makeFile = new File(tmpFolder.newFolder("folder"), "CMakeLists.txt");
        stats = GradleBuildVariant.newBuilder();
        AndroidSdkHandler sdk = AndroidSdkHandler.getInstance(sdkDirectory);
        LocalPackage cmakePackage =
                sdk.getLatestLocalPackageForPrefix(
                        SdkConstants.FD_CMAKE, null, true, new ConsoleProgressIndicator());
        if (cmakePackage != null) {
            cmakeFolder = cmakePackage.getLocation();
        }

        ninjaFolder = new File(cmakeFolder, "bin");
        debuggable = true;
        buildArguments =
                Arrays.asList("build-argument-foo", "build-argument-bar", "build-argument-baz");
        cFlags = Arrays.asList("c-flags1", "c-flag2");
        cppFlags = Arrays.asList("cpp-flags1", "cpp-flag2");
        nativeBuildConfigurationsJsons = Mockito.mock(List.class);
        Mockito.when(androidBuilder.getLogger()).thenReturn(Mockito.mock(ILogger.class));
        Mockito.when(androidBuilder.getIssueReporter())
                .thenReturn(Mockito.mock(EvalIssueReporter.class));
    }

    @Test
    public void testGetCacheArguments() throws IOException {
        CmakeServerExternalNativeJsonGenerator cmakeServerStrategy = getCMakeServerGenerator();
        JsonGenerationAbiConfiguration abiConfig =
                createJsonGenerationAbiConfiguration(
                        Abi.X86,
                        "debug",
                        tmpFolder.newFolder("my-json"),
                        tmpFolder.newFolder("my-obj"),
                        NativeBuildSystem.CMAKE,
                        12);

        List<String> cacheArguments = cmakeServerStrategy.getProcessBuilderArgs(abiConfig);

        assertThat(cacheArguments).isNotEmpty();
        assertThat(cacheArguments).contains("-DCMAKE_EXPORT_COMPILE_COMMANDS=ON");
        assertThat(cacheArguments)
                .contains(
                        String.format(
                                "-DCMAKE_ANDROID_NDK=%s", cmakeServerStrategy.getNdkFolder()));
        assertThat(cacheArguments).contains("-DCMAKE_SYSTEM_NAME=Android");
        assertThat(cacheArguments).contains("-DCMAKE_BUILD_TYPE=Debug");
        assertThat(cacheArguments).contains("-DCMAKE_C_FLAGS=c-flags1 c-flag2");
        assertThat(cacheArguments).contains("-DCMAKE_CXX_FLAGS=cpp-flags1 cpp-flag2");
        assertThat(cacheArguments).contains("build-argument-foo");
        assertThat(cacheArguments).contains("build-argument-bar");
        assertThat(cacheArguments).contains("build-argument-baz");
        assertThat(cacheArguments).contains("-G Ninja");

        // Ensure that the buildArguments (supplied by the user) is added to the end of the argument
        // list.
        // If cacheArguments = 1,2,3,4,a,b,c and buildArguments = a,b,c, we just compare where in
        // the cacheArguments does buildArguments sublist is and verify if it's indeed at the end.
        int indexOfSubset = Collections.indexOfSubList(cacheArguments, buildArguments);
        assertThat(cacheArguments.size() - indexOfSubset).isEqualTo(buildArguments.size());
    }

    @Test
    public void testInfoLoggingInteractiveMessage() {
        ILogger mockLogger = Mockito.mock(ILogger.class);

        String message = "CMake random info";
        String infoMessageString1 =
                "{\"cookie\":\"\","
                        + "\"inReplyTo\":\"configure\","
                        + "\"message\":\""
                        + message
                        + "\","
                        + "\"type\":\"message\"}";
        InteractiveMessage interactiveMessage1 =
                getInteractiveMessageFromString(infoMessageString1);

        CmakeServerExternalNativeJsonGenerator.logInteractiveMessage(
                mockLogger, interactiveMessage1, Mockito.mock(File.class));
        Mockito.verify(mockLogger, times(1)).info(message);

        message = "CMake error but should be logged as info";
        String infoMessageString2 =
                "{\"cookie\":\"\","
                        + "\"inReplyTo\":\"configure\","
                        + "\"message\":\""
                        + message
                        + "\","
                        + "\"type\":\"message\"}";
        InteractiveMessage interactiveMessage2 =
                getInteractiveMessageFromString(infoMessageString2);

        CmakeServerExternalNativeJsonGenerator.logInteractiveMessage(
                mockLogger, interactiveMessage2, Mockito.mock(File.class));
        Mockito.verify(mockLogger, times(1)).info(message);

        message = "CMake warning but should be logged as info";
        String infoMessageString3 =
                "{\"cookie\":\"\","
                        + "\"inReplyTo\":\"configure\","
                        + "\"message\":\""
                        + message
                        + "\","
                        + "\"type\":\"message\"}";
        InteractiveMessage interactiveMessage3 =
                getInteractiveMessageFromString(infoMessageString3);

        CmakeServerExternalNativeJsonGenerator.logInteractiveMessage(
                mockLogger, interactiveMessage3, Mockito.mock(File.class));
        Mockito.verify(mockLogger, times(1)).info(message);

        message = "CMake info";
        String infoMessageString4 =
                "{\"cookie\":\"\","
                        + "\"inReplyTo\":\"configure\","
                        + "\"message\":\""
                        + message
                        + "\","
                        + "\"title\":\"Some title\","
                        + "\"type\":\"message\"}";
        InteractiveMessage interactiveMessage4 =
                getInteractiveMessageFromString(infoMessageString4);

        CmakeServerExternalNativeJsonGenerator.logInteractiveMessage(
                mockLogger, interactiveMessage4, Mockito.mock(File.class));
        Mockito.verify(mockLogger, times(1)).info(message);
    }

    @Test
    public void testWarningInMessageLoggingInteractiveMessage() {
        ILogger mockLogger = Mockito.mock(ILogger.class);
        String message = "CMake Warning some random warining :|";

        String warningMessageString =
                "{\"cookie\":\"\","
                        + "\"inReplyTo\":\"configure\","
                        + "\"message\":\""
                        + message
                        + "\","
                        + "\"type\":\"message\"}";
        InteractiveMessage interactiveMessage =
                getInteractiveMessageFromString(warningMessageString);

        CmakeServerExternalNativeJsonGenerator.logInteractiveMessage(
                mockLogger, interactiveMessage, Mockito.mock(File.class));
        Mockito.verify(mockLogger, times(1)).warning(message);
    }

    @Test
    public void testWarningInTitleLoggingInteractiveMessage() {
        ILogger mockLogger = Mockito.mock(ILogger.class);
        String message = "CMake warning some random warning :(";

        String warningMessageString =
                "{\"cookie\":\"\","
                        + "\"inReplyTo\":\"configure\","
                        + "\"message\":\""
                        + message
                        + "\","
                        + "\"title\":\"Warning\","
                        + "\"type\":\"message\"}";
        InteractiveMessage interactiveMessage =
                getInteractiveMessageFromString(warningMessageString);

        CmakeServerExternalNativeJsonGenerator.logInteractiveMessage(
                mockLogger, interactiveMessage, Mockito.mock(File.class));
        Mockito.verify(mockLogger, times(1)).warning(message);
    }

    @Test
    public void testErrorInMessageLoggingInteractiveMessage() {
        ILogger mockLogger = Mockito.mock(ILogger.class);
        String message = "CMake Error some random error :(";

        String errorMessageString =
                "{\"cookie\":\"\","
                        + "\"inReplyTo\":\"configure\","
                        + "\"message\":\""
                        + message
                        + "\","
                        + "\"type\":\"message\"}";
        InteractiveMessage interactiveMessage = getInteractiveMessageFromString(errorMessageString);

        CmakeServerExternalNativeJsonGenerator.logInteractiveMessage(
                mockLogger, interactiveMessage, Mockito.mock(File.class));
        Mockito.verify(mockLogger, times(1)).error(null, message);
    }

    @Test
    public void testErrorInTitleLoggingInteractiveMessage() {
        ILogger mockLogger = Mockito.mock(ILogger.class);
        String message = "CMake error some random error :(";

        String errorMessageString =
                "{\"cookie\":\"\","
                        + "\"inReplyTo\":\"configure\","
                        + "\"message\":\""
                        + message
                        + "\","
                        + "\"title\":\"Error\","
                        + "\"type\":\"message\"}";
        InteractiveMessage interactiveMessage = getInteractiveMessageFromString(errorMessageString);

        CmakeServerExternalNativeJsonGenerator.logInteractiveMessage(
                mockLogger, interactiveMessage, Mockito.mock(File.class));
        Mockito.verify(mockLogger, times(1)).error(null, message);
    }

    /**
     * Parses the compile commands json to create the compilation database given the compile
     * commands json file.
     *
     * @param compileCommandsFile - json file with compile commands info generated by Cmake
     * @return list of compilation database present in the json file
     * @throws IOException I/O failure
     */
    @NonNull
    public static List<CompileCommand> getCompilationDatabase(@NonNull File compileCommandsFile)
            throws IOException, JsonSyntaxException {
        final String text =
                new String(
                        Files.readAllBytes(compileCommandsFile.toPath()), StandardCharsets.UTF_8);
        Gson gson = new GsonBuilder().create();

        return Arrays.asList(gson.fromJson(text, CompileCommand[].class));
    }

    @Test
    public void testParseValidFileFromCompileCommands() throws IOException {
        File compileCommandsTestFile =
                getCompileCommandsTestFile("compile_commands_valid_multiple_compilation.json");
        List<CompileCommand> compileCommands = getCompilationDatabase(compileCommandsTestFile);

        String flags =
                CmakeServerExternalNativeJsonGenerator.getAndroidGradleFileLibFlags(
                        "file.cc", compileCommands);
        assertThat(flags).isNotNull();
        assertThat(flags)
                .isEqualTo("-Irelative -DSOMEDEF=\"With spaces, quotes and \\-es.\" -c -o file.o ");
    }

    @Test
    public void testParseInvalidFileFromCompileCommands() throws IOException {
        File compileCommandsTestFile =
                getCompileCommandsTestFile("compile_commands_valid_multiple_compilation.json");
        List<CompileCommand> compileCommands = getCompilationDatabase(compileCommandsTestFile);

        String flags =
                CmakeServerExternalNativeJsonGenerator.getAndroidGradleFileLibFlags(
                        "invalid-file.cc", compileCommands);
        assertThat(flags).isNull();
    }

    @Test
    public void testGetNativeBuildConfigValue() throws IOException {
        Assume.assumeFalse(SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS);
        CmakeServerExternalNativeJsonGenerator cmakeServerStrategy = getCMakeServerGenerator();
        String targetStr =
                " {  \n"
                        + "     \"artifacts\":[  \n"
                        + "        \"/usr/local/google/home/jomof/AndroidStudioProjects/BugTest/app/build/intermediates/cmake/debug/obj/x86_64/libTest1.so\"\n"
                        + "     ],\n"
                        + "     \"buildDirectory\":\"/usr/local/google/home/jomof/AndroidStudioProjects/BugTest/app/.externalNativeBuild/cmake/debug/x86_64/src/main/test\",\n"
                        + "     \"fileGroups\":[  \n"
                        + "        {  \n"
                        + "           \"compileFlags\":\"-g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -Wa,--noexecstack -Wformat -Werror=format-security  -g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -Wa,--noexecstack -Wformat -Werror=format-security   -O0 -fno-limit-debug-info -O0 -fno-limit-debug-info  -fPIC  \",\n"
                        + "           \"defines\":[  \n"
                        + "              \"Test1_EXPORTS\"\n"
                        + "           ],\n"
                        + "           \"includePath\":[  \n"
                        + "              {  \n"
                        + "                 \"isSystem\":true,\n"
                        + "                 \"path\":\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/include\"\n"
                        + "              },\n"
                        + "              {  \n"
                        + "                 \"isSystem\":true,\n"
                        + "                 \"path\":\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/libs/x86_64/include\"\n"
                        + "              },\n"
                        + "              {  \n"
                        + "                 \"isSystem\":true,\n"
                        + "                 \"path\":\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/include/backward\"\n"
                        + "              }\n"
                        + "           ],\n"
                        + "           \"isGenerated\":false,\n"
                        + "           \"language\":\"CXX\",\n"
                        + "           \"sources\":[  \n"
                        + "              \"Test1a.cpp\",\n"
                        + "              \"../common/Test1b.cpp\",\n"
                        + "              \"/tmp/Test1c.cpp\"\n"
                        + "           ]\n"
                        + "        }\n"
                        + "     ],\n"
                        + "     \"fullName\":\"libTest1.so\",\n"
                        + "     \"linkFlags\":\"-Wl,--build-id -Wl,--warn-shared-textrel -Wl,--fatal-warnings -Wl,--no-undefined -Wl,-z,noexecstack -Qunused-arguments -Wl,-z,relro -Wl,-z,now -Wl,--build-id -Wl,--warn-shared-textrel -Wl,--fatal-warnings -Wl,--no-undefined -Wl,-z,noexecstack -Qunused-arguments -Wl,-z,relro -Wl,-z,now\",\n"
                        + "     \"linkLibraries\":\"-lm \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/libs/x86_64/libgnustl_static.a\\\"\",\n"
                        + "     \"linkerLanguage\":\"CXX\",\n"
                        + "     \"name\":\"Test1\",\n"
                        + "     \"sourceDirectory\":\"/usr/local/google/home/jomof/AndroidStudioProjects/BugTest/app/src/main/test\",\n"
                        + "     \"sysroot\":\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/platforms/android-21/arch-x86_64\",\n"
                        + "     \"type\":\"SHARED_LIBRARY\"\n"
                        + "}";
        Map<Integer, String> table = Maps.newHashMap();
        File workingDirectory =
                new File(
                        "/usr/local/google/home/jomof/AndroidStudioProjects/BugTest/app/.externalNativeBuild/cmake/debug/x86_64");
        NativeLibraryValue nativeLibraryValue =
                cmakeServerStrategy.getNativeLibraryValue(
                        "x86", workingDirectory, getTestTarget(targetStr), new StringTable(table));

        assertThat(nativeLibraryValue.files).isNotNull();
        assertThat(nativeLibraryValue.files).hasSize(3);

        NativeSourceFileValue file0 = Iterables.get(nativeLibraryValue.files, 0);
        NativeSourceFileValue file1 = Iterables.get(nativeLibraryValue.files, 1);
        NativeSourceFileValue file2 = Iterables.get(nativeLibraryValue.files, 2);

        // Verify source paths.
        assertThat(file0.src).isNotNull();
        assertThat(file0.src.getAbsolutePath())
                .isEqualTo(
                        "/usr/local/google/home/jomof/AndroidStudioProjects/BugTest/app/src/main/test/Test1a.cpp");
        assertThat(file1.src).isNotNull();
        assertThat(file1.src.getAbsolutePath())
                .isEqualTo(
                        "/usr/local/google/home/jomof/AndroidStudioProjects/BugTest/app/src/main/common/Test1b.cpp");
        assertThat(file2.src).isNotNull();
        assertThat(file2.src.getAbsolutePath()).isEqualTo("/tmp/Test1c.cpp");

        // Verify working directories.
        assertThat(file0.workingDirectoryOrdinal).isLessThan(table.size());
        assertThat(table.get(file0.workingDirectoryOrdinal)).isEqualTo(workingDirectory.toString());
        assertThat(file1.workingDirectoryOrdinal).isLessThan(table.size());
        assertThat(table.get(file1.workingDirectoryOrdinal)).isEqualTo(workingDirectory.toString());
        assertThat(file2.workingDirectoryOrdinal).isLessThan(table.size());
        assertThat(table.get(file2.workingDirectoryOrdinal)).isEqualTo(workingDirectory.toString());

        // Verify flags.
        String expectedFlags =
                "--target=x86_64-none-linux-android --gcc-toolchain=/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/x86_64-4.9/prebuilt/linux-x86_64 --sysroot=/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/platforms/android-21/arch-x86_64 -DTest1_EXPORTS -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/include -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/libs/x86_64/include -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/include/backward -g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -Wa,--noexecstack -Wformat -Werror=format-security -g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -Wa,--noexecstack -Wformat -Werror=format-security -O0 -fno-limit-debug-info -O0 -fno-limit-debug-info -fPIC";
        assertThat(file0.flagsOrdinal).isLessThan(table.size());
        assertThat(table.get(file0.flagsOrdinal)).isEqualTo(expectedFlags);
        assertThat(file1.flagsOrdinal).isLessThan(table.size());
        assertThat(table.get(file1.flagsOrdinal)).isEqualTo(expectedFlags);
        assertThat(file2.flagsOrdinal).isLessThan(table.size());
        assertThat(table.get(file2.flagsOrdinal)).isEqualTo(expectedFlags);
    }

    // Reference http://b/72065334
    @Test
    public void getNativeLibraryValue_FlagsFromServerModelUsed() throws IOException {
        Assume.assumeFalse(SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS);
        CmakeServerExternalNativeJsonGenerator cmakeServerStrategy = getCMakeServerGenerator();
        String targetStr =
                "{  \n"
                        + "   \"artifacts\":[  \n"
                        + "      \"/usr/local/google/home/jomof/projects/nre-json/Teapots2/choreographer-30fps/.externalNativeBuild/cmake/debug/armeabi-v7a/libnative_app_glue.a\"\n"
                        + "   ],\n"
                        + "   \"buildDirectory\":\"/usr/local/google/home/jomof/projects/nre-json/Teapots2/choreographer-30fps/.externalNativeBuild/cmake/debug/armeabi-v7a\",\n"
                        + "   \"fileGroups\":[  \n"
                        + "      {  \n"
                        + "         \"compileFlags\":\"-isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot/usr/include/arm-linux-androideabi -D__ANDROID_API__=16 -g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16 -fno-integrated-as -mthumb -Wa,--noexecstack -Wformat -Werror=format-security  -O0 -fno-limit-debug-info  -fPIC  \",\n"
                        + "         \"isGenerated\":false,\n"
                        + "         \"language\":\"C\",\n"
                        + "         \"sources\":[  \n"
                        + "            \"../../../../../../../Android/Sdk/ndk-bundle/sources/android/native_app_glue/android_native_app_glue.c\"\n"
                        + "         ]\n"
                        + "      }\n"
                        + "   ],\n"
                        + "   \"fullName\":\"libnative_app_glue.a\",\n"
                        + "   \"linkerLanguage\":\"C\",\n"
                        + "   \"name\":\"native_app_glue\",\n"
                        + "   \"sourceDirectory\":\"/usr/local/google/home/jomof/projects/nre-json/Teapots2/choreographer-30fps/src/main/cpp\",\n"
                        + "   \"sysroot\":\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot\",\n"
                        + "   \"type\":\"STATIC_LIBRARY\"\n"
                        + "}";
        Map<Integer, String> table = Maps.newHashMap();
        File workingDirectory =
                new File(
                        "/usr/local/google/home/jomof/projects/nre-json/Teapots2/choreographer-30fps/.externalNativeBuild/cmake/debug/armeabi-v7a");
        NativeLibraryValue nativeLibraryValue =
                cmakeServerStrategy.getNativeLibraryValue(
                        "x86", workingDirectory, getTestTarget(targetStr), new StringTable(table));

        assertThat(nativeLibraryValue.files).hasSize(1);
        assertThat(table.get(0)).isEqualTo(workingDirectory.toString());
        assertThat(table.get(1))
                .isEqualTo(
                        "--target=x86_64-none-linux-android --gcc-toolchain=/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/x86_64-4.9/prebuilt/linux-x86_64 --sysroot=/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/platforms/android-21/arch-x86_64 -DTest_EXPORTS -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/include -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/libs/x86_64/include -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/include/backward -g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -Wa,--noexecstack -Wformat -Werror=format-security -g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -Wa,--noexecstack -Wformat -Werror=format-security -O0 -fno-limit-debug-info -O0 -fno-limit-debug-info -fPIC");

        NativeSourceFileValue value = nativeLibraryValue.files.stream().findFirst().get();
        assertThat(value.flagsOrdinal).isLessThan(table.size());

        assertThat(value.flagsOrdinal).isEqualTo(4);
        assertThat(table.get(value.flagsOrdinal))
                .isEqualTo(
                        "--target=x86_64-none-linux-android --gcc-toolchain=/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/x86_64-4.9/prebuilt/linux-x86_64 --sysroot=/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/platforms/android-21/arch-x86_64 -g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -Wa,--noexecstack -Wformat -Werror=format-security -g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -Wa,--noexecstack -Wformat -Werror=format-security -O0 -fno-limit-debug-info -O0 -fno-limit-debug-info -fPIC");
    }

    /** Returns InteractiveMessage object from the given message string. */
    private static InteractiveMessage getInteractiveMessageFromString(@NonNull String messageStr) {
        Gson gson = new GsonBuilder().create();
        return gson.fromJson(messageStr, InteractiveMessage.class);
    }

    /** Returns a default CmakeServerExternalNativeJsonGenerator. */
    private CmakeServerExternalNativeJsonGenerator getCMakeServerGenerator() {
        Mockito.when(ndkHandler.getRevision()).thenReturn(new Revision(15));
        Mockito.when(androidBuilder.getLogger()).thenReturn(Mockito.mock(ILogger.class));
        assert ndkHandler.getRevision() != null;
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
                        ndkHandler.getRevision(),
                        nativeBuildConfigurationsJsons,
                        new File("./compiler-settings-cache"),
                        true);
        return new CmakeServerExternalNativeJsonGenerator(
                config, new HashSet<>(), androidBuilder, cmakeFolder, stats);
    }

    /**
     * Returns the test file given the test folder and file name.
     *
     * @param testFileName - test file name
     * @return test file
     */
    private static File getCompileCommandsTestFile(@NonNull String testFileName) {
        final String compileCommandsTestFileDir =
                "/com/android/build/gradle/external/cmake/compile_commands/";
        return TestResources.getFile(
                CmakeServerExternalNativeJsonGeneratorTest.class,
                compileCommandsTestFileDir + testFileName);
    }

    private static CodeModel getTestCodeMode(@NonNull String codeModelStr) {
        Gson gson = new GsonBuilder().create();
        return gson.fromJson(codeModelStr, CodeModel.class);
    }

    private static CmakeInputsResult getTestCmakeInputsResults(@NonNull String cmakeInputsStr) {
        Gson gson = new GsonBuilder().create();
        return gson.fromJson(cmakeInputsStr, CmakeInputsResult.class);
    }

    private static Target getTestTarget(@NonNull String targetStr) {
        Gson gson = new GsonBuilder().create();
        return gson.fromJson(targetStr, Target.class);
    }

    /**
     * Returns the test json folder.
     *
     * @return test json folder
     */
    private File getTestJsonFolder() {
        final String testCompileCommandsPath =
                "/com/android/build/gradle/testJsonFolder/x86/compile_commands.json";
        File compileCommands =
                TestResources.getFile(
                        CmakeServerExternalNativeJsonGeneratorTest.class, testCompileCommandsPath);
        return compileCommands.getParentFile().getParentFile();
    }
}
