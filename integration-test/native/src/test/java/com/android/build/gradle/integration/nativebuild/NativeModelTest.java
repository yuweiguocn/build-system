/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.nativebuild;

import static com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp.androidMkC;
import static com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp.androidMkCpp;
import static com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp.androidMkGoogleTest;
import static com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp.applicationMk;
import static com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp.cmakeLists;
import static com.android.build.gradle.integration.common.truth.NativeAndroidProjectSubject.assertThat;
import static com.android.build.gradle.integration.common.truth.NativeSettingsSubject.assertThat;
import static com.android.testutils.truth.FileSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ParameterizedAndroidProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.common.utils.NdkHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValue;
import com.android.build.gradle.internal.cxx.json.NativeLibraryValue;
import com.android.build.gradle.internal.cxx.json.NativeSourceFileValue;
import com.android.build.gradle.tasks.ExternalNativeBuildTaskUtils;
import com.android.build.gradle.tasks.NativeBuildSystem;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeSettings;
import com.android.builder.model.NativeToolchain;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** General Model tests */
@RunWith(Parameterized.class)
@Ignore
public class NativeModelTest {
    private enum Compiler {
        GCC,
        CLANG,
        IRRELEVANT  // indicates if the compiler being used is irrelevant to the test
    }

    // Indicates if we need to add cmake.dir in local.properties
    private enum CmakeInLocalProperties {
        ADD_CMAKE_DIR,
        NO_CMAKE_DIR
    }

    private static int DEFAULT_ABI_COUNT = NdkHelper.getNdkInfo().getDefaultAbis().size();

    private enum Config {
        ANDROID_MK_FILE_C_CLANG(
                "  apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "            android {\n"
                        + "                compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "                externalNativeBuild {\n"
                        + "                    ndkBuild {\n"
                        + "                        path \"src/main/cpp/Android.mk\"\n"
                        + "                        buildStagingDirectory \"relative/path\"\n"
                        + "                    }\n"
                        + "                }\n"
                        + "                defaultConfig {\n"
                        + "                    externalNativeBuild {\n"
                        + "                        ndkBuild {\n"
                        + "                            arguments \"NDK_TOOLCHAIN_VERSION:=clang\"\n"
                        + "                            cFlags \"-DTEST_C_FLAG\"\n"
                        + "                            cppFlags \"-DTEST_CPP_FLAG\"\n"
                        + "                        }\n"
                        + "                    }\n"
                        + "                }\n"
                        + "            }",
                ImmutableList.of(androidMkC("src/main/cpp")),
                false,
                1,
                2,
                DEFAULT_ABI_COUNT,
                Compiler.CLANG,
                NativeBuildSystem.NDK_BUILD,
                DEFAULT_ABI_COUNT * 2,
                "relative/path"),
        NDK_BUILD_JOBS_FLAG(
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "            android {\n"
                        + "                compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "                externalNativeBuild {\n"
                        + "                    ndkBuild {\n"
                        + "                        path \"src/main/cpp/Android.mk\"\n"
                        + "                        buildStagingDirectory \"ABSOLUTE_PATH\"\n"
                        + "                    }\n"
                        + "                }\n"
                        + "                defaultConfig {\n"
                        + "                    externalNativeBuild {\n"
                        + "                        ndkBuild {\n"
                        + "                            arguments \"NDK_TOOLCHAIN_VERSION:=clang\",\n"
                        + "                                \"-j8\",\n"
                        + "                                \"--jobs=8\",\n"
                        + "                                \"-j\", \"8\",\n"
                        + "                                \"--jobs\", \"8\"\n"
                        + "                            cFlags \"-DTEST_C_FLAG\"\n"
                        + "                            cppFlags \"-DTEST_CPP_FLAG\"\n"
                        + "                        }\n"
                        + "                    }\n"
                        + "                }\n"
                        + "            }",
                ImmutableList.of(androidMkC("src/main/cpp")),
                false,
                1,
                2,
                DEFAULT_ABI_COUNT,
                Compiler.CLANG,
                NativeBuildSystem.NDK_BUILD,
                DEFAULT_ABI_COUNT * 2,
                "ABSOLUTE_PATH"),
        ANDROID_MK_FILE_CPP_CLANG(
                " apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "            android {\n"
                        + "                compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "                externalNativeBuild {\n"
                        + "                    ndkBuild {\n"
                        + "                        path file(\"src/main/cpp/Android.mk\")\n"
                        + "                    }\n"
                        + "                }\n"
                        + "                defaultConfig {\n"
                        + "                    externalNativeBuild {\n"
                        + "                        ndkBuild {\n"
                        + "                            arguments \"NDK_TOOLCHAIN_VERSION:=clang\"\n"
                        + "                            cFlags \"-DTEST_C_FLAG\"\n"
                        + "                            cppFlags \"-DTEST_CPP_FLAG\"\n"
                        + "                        }\n"
                        + "                    }\n"
                        + "                }\n"
                        + "            }",
                ImmutableList.of(androidMkCpp("src/main/cpp")),
                true,
                1,
                2,
                DEFAULT_ABI_COUNT,
                Compiler.CLANG,
                NativeBuildSystem.NDK_BUILD,
                DEFAULT_ABI_COUNT * 2,
                ".externalNativeBuild"),
        ANDROID_MK_GOOGLE_TEST(
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    externalNativeBuild {\n"
                        + "        ndkBuild {\n"
                        + "            path file(\"src/main/cpp/Android.mk\")\n"
                        + "        }\n"
                        + "    }\n"
                        + "    defaultConfig {\n"
                        + "        externalNativeBuild {\n"
                        + "            ndkBuild {\n"
                        + "                cFlags \"-DTEST_C_FLAG\"\n"
                        + "                cppFlags \"-DTEST_CPP_FLAG\"\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}",
                ImmutableList.of(
                        androidMkGoogleTest("src/main/cpp"),
                        new TestSourceFile(
                                "src/main/cpp",
                                "hello-jni-unittest.cc",
                                "#include <limits.h>\n"
                                        + "#include \"sample1.h\"\n"
                                        + "#include \"gtest/gtest.h\"\n"
                                        + "TEST(EqualsTest, One) {\n"
                                        + "  EXPECT_EQ(1, 1);\n"
                                        + "}")),
                true,
                4,
                2,
                DEFAULT_ABI_COUNT,
                Compiler.IRRELEVANT,
                NativeBuildSystem.NDK_BUILD,
                0,
                ".externalNativeBuild"),
        ANDROID_MK_FILE_CPP_GCC(
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    externalNativeBuild {\n"
                        + "        ndkBuild {\n"
                        + "            path file(\"src/main/cpp/Android.mk\")\n"
                        + "        }\n"
                        + "    }\n"
                        + "    defaultConfig {\n"
                        + "        externalNativeBuild {\n"
                        + "            ndkBuild {\n"
                        + "                arguments \"NDK_TOOLCHAIN_VERSION:=4.9\"\n"
                        + "                cFlags \"-DTEST_C_FLAG\"\n"
                        + "                cppFlags \"-DTEST_CPP_FLAG\"\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}",
                ImmutableList.of(androidMkCpp("src/main/cpp")),
                true,
                1,
                2,
                DEFAULT_ABI_COUNT,
                Compiler.GCC,
                NativeBuildSystem.NDK_BUILD,
                DEFAULT_ABI_COUNT * 2,
                ".externalNativeBuild"),
        ANDROID_MK_FILE_CPP_GCC_VIA_APPLICATION_MK(
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    externalNativeBuild {\n"
                        + "        ndkBuild {\n"
                        + "            path file(\"src/main/cpp/Android.mk\")\n"
                        + "        }\n"
                        + "    }\n"
                        + "    defaultConfig {\n"
                        + "        externalNativeBuild {\n"
                        + "            ndkBuild {\n"
                        + "                cFlags \"-DTEST_C_FLAG\"\n"
                        + "                cppFlags \"-DTEST_CPP_FLAG\"\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}",
                ImmutableList.of(androidMkCpp("src/main/cpp"), applicationMk("src/main/cpp")),
                true,
                1,
                2,
                DEFAULT_ABI_COUNT,
                Compiler.GCC,
                NativeBuildSystem.NDK_BUILD,
                DEFAULT_ABI_COUNT * 2,
                ".externalNativeBuild"),
        ANDROID_MK_CUSTOM_BUILD_TYPE(
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    externalNativeBuild {\n"
                        + "        ndkBuild {\n"
                        + "            path \"src/main/cpp/Android.mk\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "    defaultConfig {\n"
                        + "        externalNativeBuild {\n"
                        + "            ndkBuild {\n"
                        + "                cFlags \"-DTEST_C_FLAG\"\n"
                        + "                cppFlags \"-DTEST_CPP_FLAG\"\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "    buildTypes {\n"
                        + "        myCustomBuildType {\n"
                        + "             externalNativeBuild {\n"
                        + "              ndkBuild {\n"
                        + "                cppFlags \"-DCUSTOM_BUILD_TYPE\"\n"
                        + "              }\n"
                        + "          }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}",
                ImmutableList.of(androidMkCpp("src/main/cpp")),
                true,
                1,
                3,
                DEFAULT_ABI_COUNT,
                Compiler.IRRELEVANT,
                NativeBuildSystem.NDK_BUILD,
                DEFAULT_ABI_COUNT * 3,
                ".externalNativeBuild"),
        CMAKELISTS_FILE_CPP(
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    externalNativeBuild {\n"
                        + "        cmake {\n"
                        + "            path \"CMakeLists.txt\"\n"
                        + "            buildStagingDirectory \"ABSOLUTE_PATH\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "    defaultConfig {\n"
                        + "        externalNativeBuild {\n"
                        + "            cmake {\n"
                        + "                cFlags \"-DTEST_C_FLAG\"\n"
                        + "                cppFlags \"-DTEST_CPP_FLAG\"\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}",
                ImmutableList.of(cmakeLists(".")),
                true,
                1,
                2,
                DEFAULT_ABI_COUNT,
                Compiler.IRRELEVANT,
                NativeBuildSystem.CMAKE,
                DEFAULT_ABI_COUNT * 2,
                "ABSOLUTE_PATH"),
        CMAKELISTS_ARGUMENTS(
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    externalNativeBuild {\n"
                        + "        cmake {\n"
                        + "            path \"CMakeLists.txt\"\n"
                        + "            buildStagingDirectory \"relative/path\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "    defaultConfig {\n"
                        + "        externalNativeBuild {\n"
                        + "          cmake {\n"
                        + "            arguments \"-DCMAKE_CXX_FLAGS=-DTEST_CPP_FLAG\"\n"
                        + "            cFlags \"-DTEST_C_FLAG\"\n"
                        + "            abiFilters \"armeabi-v7a\", \"x86_64\"\n"
                        + "          }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}",
                ImmutableList.of(cmakeLists(".")),
                true,
                1,
                2,
                2,
                Compiler.IRRELEVANT,
                NativeBuildSystem.CMAKE,
                4,
                "relative/path"),
        CMAKELISTS_FILE_C(
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    externalNativeBuild {\n"
                        + "        cmake {\n"
                        + "            path \"CMakeLists.txt\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "    defaultConfig {\n"
                        + "        externalNativeBuild {\n"
                        + "          cmake {\n"
                        + "            cFlags \"-DTEST_C_FLAG\"\n"
                        + "            cppFlags \"-DTEST_CPP_FLAG\"\n"
                        + "          }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}",
                ImmutableList.of(cmakeLists(".")),
                false,
                1,
                2,
                DEFAULT_ABI_COUNT,
                Compiler.IRRELEVANT,
                NativeBuildSystem.CMAKE,
                DEFAULT_ABI_COUNT * 2,
                ".externalNativeBuild"),
        CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION(
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    externalNativeBuild {\n"
                        + "        cmake {\n"
                        + "            path \"CMakeLists.txt\"\n"
                        + "            buildStagingDirectory \"relative/path\"\n"
                        + "            version \"CMAKE_VERSION\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "    defaultConfig {\n"
                        + "        externalNativeBuild {\n"
                        + "          cmake {\n"
                        + "            arguments \"-DCMAKE_CXX_FLAGS=-DTEST_CPP_FLAG\", \"-DCMAKE_ANDROID_NDK=TEST_NDK_PATH\", \"-DCMAKE_PROGRAM_PATH=TEST_PATH_TO_SEARCH\", \"-DCMAKE_ANDROID_NDK_TOOLCHAIN_VERSION=clang\"\n"
                        + "            cFlags \"-DTEST_C_FLAG\"\n"
                        + "            abiFilters \"armeabi-v7a\", \"x86_64\"\n"
                        + "          }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}",
                ImmutableList.of(cmakeLists(".")),
                true,
                1,
                2,
                2,
                Compiler.IRRELEVANT,
                NativeBuildSystem.CMAKE,
                4,
                "relative/path"),
        ;

        public String buildGradle;
        private final List<TestSourceFile> extraFiles;
        public final boolean isCpp;
        public final int targetCount;
        public final int variantCount;
        public final int abiCount;
        public final Compiler compiler;
        public final NativeBuildSystem buildSystem;
        public final int expectedBuildOutputs;
        public String nativeBuildOutputPath;

        Config(
                String buildGradle,
                List<TestSourceFile> extraFiles,
                boolean isCpp,
                int targetCount,
                int variantCount,
                int abiCount,
                Compiler compiler,
                NativeBuildSystem buildSystem,
                int expectedBuildOutputs,
                String nativeBuildOutputPath) {
            this.buildGradle = buildGradle;
            this.extraFiles = extraFiles;
            this.isCpp = isCpp;
            this.targetCount = targetCount;
            this.variantCount = variantCount;
            this.abiCount = abiCount;
            this.compiler = compiler;
            this.buildSystem = buildSystem;
            this.expectedBuildOutputs = expectedBuildOutputs;
            this.nativeBuildOutputPath = nativeBuildOutputPath;
        }

        public GradleTestProject create(
                @NonNull String cmakeVersionInDsl, @NonNull String cmakeVersionInLocalProperties) {
            if (!cmakeVersionInDsl.isEmpty()) {
                this.buildGradle = this.buildGradle.replace("CMAKE_VERSION", cmakeVersionInDsl);
            }

            this.buildGradle =
                    this.buildGradle.replace("TEST_NDK_PATH",
                            escapeWindowsCharacters(TestUtils.getNdk().getAbsolutePath()));
            // Set the correct CMAKE_PROGRAM_PATH so CMake can look for ninja at the right path.
            if (buildGradle.contains("TEST_PATH_TO_SEARCH")) {
                File cmakeBinFolder =
                        new File(
                                GradleTestProject.getCmakeVersionFolder(
                                        cmakeVersionInLocalProperties),
                                "bin");
                this.buildGradle =
                        this.buildGradle.replace(
                                "TEST_PATH_TO_SEARCH",
                                escapeWindowsCharacters(cmakeBinFolder.getAbsolutePath()));
            }

            GradleTestProject project =
                    GradleTestProject.builder()
                            .fromTestApp(
                                    HelloWorldJniApp.builder()
                                            .withNativeDir("cpp")
                                            .useCppSource(isCpp)
                                            .build())
                            .addFiles(extraFiles)
                            .setCmakeVersion(cmakeVersionInLocalProperties)
                            .setWithCmakeDirInLocalProp(!cmakeVersionInLocalProperties.isEmpty())
                            .create();

            return project;
        }
    }

    @Rule public final GradleTestProject project;

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Parameterized.Parameters(name = "model = {0}")
    public static Object[][] data() {
        return new Object[][] {
            {Config.NDK_BUILD_JOBS_FLAG, "", ""},
            {Config.ANDROID_MK_FILE_C_CLANG, "", ""},
            {Config.ANDROID_MK_FILE_CPP_CLANG, "", ""},
            {Config.ANDROID_MK_GOOGLE_TEST, "", ""},
            {Config.ANDROID_MK_FILE_CPP_GCC, "", ""},
            // disabled due to http://b.android.com/230228
            // {Config.ANDROID_MK_FILE_CPP_GCC_VIA_APPLICATION_MK},
            {Config.ANDROID_MK_CUSTOM_BUILD_TYPE, "", ""},
            {Config.CMAKELISTS_FILE_C, "", ""},
            {Config.CMAKELISTS_FILE_CPP, "", ""},
            {Config.CMAKELISTS_ARGUMENTS, "", ""},
            {Config.CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION, "3.10.2", "3.10.4819442"},
        };
    }

    private final Config config;

    public NativeModelTest(
            Config config,
            @NonNull String cmakeVersionInDsl,
            @NonNull String cmakeVersionInLocalProperties) {
        this.config = config;
        this.project = config.create(cmakeVersionInDsl, cmakeVersionInLocalProperties);
    }

    @Before
    public void setup() throws Exception {
        assumeNotCMakeOnWindows();
        if (config.nativeBuildOutputPath.equals("ABSOLUTE_PATH")) {
            File tempOutputDirectory = temporaryFolder.newFolder("absolute_path");
            config.buildGradle =
                    config.buildGradle.replace(
                            "ABSOLUTE_PATH",
                            escapeWindowsCharacters(tempOutputDirectory.getAbsolutePath()));
            config.nativeBuildOutputPath = tempOutputDirectory.getAbsolutePath();
        }

        TestFileUtils.appendToFile(project.getBuildFile(), config.buildGradle);
    }

    @Test
    public void checkSingleVariantModel() throws Exception {
        assumeNotCMakeOnWindows();
        Map<String, ParameterizedAndroidProject> parameterizedAndroidProject =
                project.model().fetchMulti(ParameterizedAndroidProject.class);

        ParameterizedAndroidProject base = parameterizedAndroidProject.get(":");
        assertThat(base.getNativeAndroidProject()).isNotNull();
        NativeAndroidProject nativeAndroidProject = base.getNativeAndroidProject();
        assert nativeAndroidProject != null;
        // Artifacts is expected to be empty because this is the NativeAndroidProject that returns
        // just the per-variant information that can be cheaply constructed for single-variant sync.
        assertThat(nativeAndroidProject.getArtifacts()).hasSize(0);
        // We should have gotten information about the variants and ABIs
        assertThat(base.getNativeVariantAbis()).isNotEmpty();
    }

    @Test
    public void checkModel() throws Exception {
        assumeNotCMakeOnWindows();
        AndroidProject androidProject = project.model().fetchAndroidProjects().getOnlyModel();
        assertThat(androidProject.getSyncIssues()).hasSize(0);
        NativeAndroidProject model = project.model().fetch(NativeAndroidProject.class);
        assertThat(model).isNotNull();
        assertThat(model.getName()).isEqualTo("project");
        assertThat(model.getArtifacts())
                .hasSize(config.targetCount * config.variantCount * config.abiCount);

        // Settings should be non-empty but the count depends on flags and build system because
        // settings are the distinct set of flags.
        assertThat(model.getSettings()).isNotEmpty();
        assertThat(model.getToolChains()).hasSize(config.variantCount * config.abiCount);
        assertThat(model).hasArtifactGroupsOfSize(config.targetCount * config.abiCount);

        for (File file : model.getBuildFiles()) {
            assertThat(file).isFile();
        }

        for (NativeToolchain nativeToolchain : model.getToolChains()) {
            assertThat(nativeToolchain.getName()).isNotNull();
            boolean compilerExecPresent =
                    (nativeToolchain.getCCompilerExecutable() != null
                            || nativeToolchain.getCppCompilerExecutable() != null);
            assertThat(compilerExecPresent).isTrue();
        }

        for (NativeArtifact artifact : model.getArtifacts()) {
            File parent = artifact.getOutputFile().getParentFile();
            List<String> parents = Lists.newArrayList();
            while (parent != null) {
                parents.add(parent.getName());
                parent = parent.getParentFile();
            }
            assertThat(parents).contains("obj");
            assertThat(parents).doesNotContain("lib");
        }

        if (config.variantCount == 2) {
            checkDefaultVariants(model);
        } else {
            checkCustomVariants(model);
        }

        if (config.isCpp) {
            checkIsCpp(model);
        } else {
            checkIsC(model);
        }

        if (config.compiler == Compiler.GCC) {
            checkGcc(model);
        } else if (config.compiler == Compiler.CLANG) {
            checkClang(model);
        }

        checkProblematicCompilerFlags(model);
        checkNativeBuildOutputPath(config, project);
        checkIncludesPresent(model);
        if (config.buildSystem != NativeBuildSystem.NDK_BUILD) {
            checkTargetTriplePresent(model);
        }
    }

    @Test
    public void checkNativeSourceFileValue() throws Exception {
        // Syncing once, causes the JSON to exist
        project.model().fetch(NativeAndroidProject.class);

        String[] buildTypes = {"debug", "release"};
        for (String buildType : buildTypes) {
            File jsonFile = getJsonFile(buildType, "x86_64");
            NativeBuildConfigValue nativeBuildConfigValue = getNativeBuildConfigValue(jsonFile);
            checkSourceFileValue(nativeBuildConfigValue);
        }
    }

    @Test
    public void checkUpToDate() throws Exception {
        AssumeUtil.assumeNotWindowsBot(); // https://issuetracker.google.com/70931936
        assumeNotCMakeOnWindows();
        File jsonFile = getJsonFile("debug", "x86_64");
        File miniConfigFile = ExternalNativeBuildTaskUtils.getJsonMiniConfigFile(jsonFile);

        // Build should be incremental where it is possible. CMake in particular is supposed to
        // be able to regenerate the ninja build system in-place. Use this file to check whether
        // the build system has deleted the build directory or not.
        // This is related to https://issuetracker.google.com/69408798.
        File incrementalBuildSentinelFile = new File(jsonFile.getParent(), "test_sentinel.txt");

        // Initially, JSON and sentinel files don't exist
        assertThat(incrementalBuildSentinelFile).doesNotExist();

        // Syncing once causes the JSON to exist
        NativeAndroidProject nativeProject = project.model().fetch(NativeAndroidProject.class);
        assertThat(jsonFile).exists();
        long originalTimeStamp = getHighestResolutionTimeStamp(jsonFile);
        assertThat(incrementalBuildSentinelFile).doesNotExist();
        Files.createFile(incrementalBuildSentinelFile.toPath());
        assertThat(miniConfigFile).exists();

        // Syncing again, leaves the JSON unchanged
        nativeProject = project.model().fetch(NativeAndroidProject.class);
        assertThat(originalTimeStamp).isEqualTo(
                getHighestResolutionTimeStamp(jsonFile));
        assertThat(incrementalBuildSentinelFile).exists();
        assertThat(miniConfigFile)
                .isNewerThanOrSameAs(jsonFile); // miniconfig created to read paths of build files

        // Touch a native build file (like CMakeLists.txt) and check that JSON is regenerated in
        // response
        assertThat(nativeProject.getBuildFiles()).isNotEmpty();
        File buildFile = getNativeBuildFile(nativeProject);
        assertThat(buildFile).exists();
        spinTouch(buildFile, originalTimeStamp);
        nativeProject = project.model().fetch(NativeAndroidProject.class);
        long newTimeStamp = getHighestResolutionTimeStamp(jsonFile);
        assertThat(newTimeStamp).isGreaterThan(originalTimeStamp);
        assertThat(ExternalNativeBuildTaskUtils.fileIsUpToDate(jsonFile, miniConfigFile)).isTrue();
        assertThat(miniConfigFile).isNewerThanOrSameAs(jsonFile);

        // No build outputs should exist because we haven't built anything yet.
        assertThat(nativeProject).noBuildOutputsExist();

        // Touching a build file should not cause the incremental build sentinel file to
        // disappear (if would disappear if the folder had been incorrectly deleted)
        assertThat(incrementalBuildSentinelFile).exists();

        // Replace flags in the build file (build.gradle) and check that JSON is regenerated
        String originalFileHash = FileUtils.sha1(jsonFile);
        TestFileUtils.searchAndReplace(project.getBuildFile(), "-DTEST_", "-DTEST_CHANGED_");
        nativeProject = project.model().fetch(NativeAndroidProject.class);
        assertThat(FileUtils.sha1(jsonFile)).isNotEqualTo(originalFileHash);
        assertThat(miniConfigFile).exists();

        // Replacing CMake.exe or ndk-build.cmd call flags should delete the directory and hence
        // the sentinel file.
        assertThat(incrementalBuildSentinelFile).doesNotExist();

        // Recreate the sentinel file.
        assertThat(incrementalBuildSentinelFile).doesNotExist();
        Files.createFile(incrementalBuildSentinelFile.toPath());

        // Check that the newly written flags are there.
        if (config.isCpp) {
            checkIsChangedCpp(nativeProject);
        } else {
            checkIsChangedC(nativeProject);
        }

        // Do a clean and check that the JSON is not regenerated
        originalTimeStamp = getHighestResolutionTimeStamp(jsonFile);
        project.execute("clean");
        nativeProject = project.model().fetch(NativeAndroidProject.class);
        assertThat(originalTimeStamp).isEqualTo(getHighestResolutionTimeStamp(jsonFile));
        assertThat(nativeProject).noBuildOutputsExist();
        assertThat(incrementalBuildSentinelFile).exists();
        assertThat(miniConfigFile).isNewerThanOrSameAs(jsonFile);

        // If there are expected build outputs then build them and check results
        if (config.expectedBuildOutputs > 0) {
            // TODO: Get this code path working again
            //    project.execute("assemble");
            //    assertThat(nativeProject).hasBuildOutputCountEqualTo(config.expectedBuildOutputs);
            //    assertThat(nativeProject).allBuildOutputsExist();
            //    assertThat(incrementalBuildSentinelFile).exists();
            //    assertThat(miniConfigFile).isNewerThanOrSameAs(jsonFile);
            //
            //    // Simulate a project that has created extra so files aside from the normal ones
            //    // created by building.
            //    List<File> additionalSoFiles = createAdditionalSoFiles(nativeProject);
            //    assertThatFilesExist(additionalSoFiles);
            //
            //    // Now touch the CMakeLists.txt (or Android.mk). This should trigger a soft-regenerate.
            //    // In this case, .so files should remain but additional so files should be deleted (in
            //    // case they are obsoleted by the change to CMakeLists.txt).
            //    spinTouch(buildFile, originalTimeStamp);
            //    nativeProject = project.model().fetch(NativeAndroidProject.class);
            //    assertThat(nativeProject).allBuildOutputsExist();
            //    assertThat(miniConfigFile).isNewerThanOrSameAs(jsonFile);
            //    assertThatFilesDontExist(additionalSoFiles);
            //
            //    // Make sure clean removes the known build outputs.
            //    originalTimeStamp = getHighestResolutionTimeStamp(jsonFile);
            //    project.execute("clean");
            //    assertThat(nativeProject).noBuildOutputsExist();
            //    assertThat(incrementalBuildSentinelFile).exists();
            //    assertThatFilesDontExist(additionalSoFiles);
            //    assertThat(miniConfigFile).isNewerThanOrSameAs(jsonFile);
            //
            //    // But clean shouldn't change the JSON because it is outside of the build/ folder.
            //    assertThat(originalTimeStamp).isEqualTo(getHighestResolutionTimeStamp(jsonFile));
        }
    }

    private File getNativeBuildFile(NativeAndroidProject nativeProject) {
        for (File buildFile : nativeProject.getBuildFiles()) {
            // Build files could have more than just CMakeLists.txt which might be read-only (e.g.
            // android.toolchain.cmake) which cannot be edited. So only update CMakeLists.txt
            // file(s).
            if (this.config.buildSystem == NativeBuildSystem.CMAKE
                    && !buildFile.getName().equals("CMakeLists.txt")) {
                continue;
            }
            return buildFile;
        }

        throw new RuntimeException(
                "Expected at least one native build file: " + nativeProject.getBuildFiles());
    }

    private List<File> createAdditionalSoFiles(NativeAndroidProject nativeProject)
            throws IOException {
        List<File> result = Lists.newArrayList();
        for (NativeArtifact artifact : nativeProject.getArtifacts()) {
            result.add(
                    new File(
                            artifact.getOutputFile().getParentFile(),
                            "lib_additional_for_test.so"));
        }
        List<File> additionalSoFiles = result;
        for (File additionalSoFile : additionalSoFiles) {
            additionalSoFile.getParentFile().mkdirs();
            Files.createFile(additionalSoFile.toPath());
        }
        return additionalSoFiles;
    }

    private void assertThatFilesDontExist(List<File> additionalSoFiles) {
        for (File additionalSoFile : additionalSoFiles) {
            assertThat(additionalSoFile).doesNotExist();
        }
    }

    private void assertThatFilesExist(List<File> additionalSoFiles) {
        for (File additionalSoFile : additionalSoFiles) {
            assertThat(additionalSoFile).exists();
        }
    }

    static NativeBuildConfigValue getNativeBuildConfigValue(File json)
            throws IOException {
        Gson gson =
                new GsonBuilder()
                        .registerTypeAdapter(
                                File.class,
                                new TypeAdapter() {

                                    @Override
                                    public void write(JsonWriter jsonWriter, Object o)
                                            throws IOException {}

                                    @Override
                                    public Object read(JsonReader jsonReader) throws IOException {
                                        return new File(jsonReader.nextString());
                                    }
                                })
                        .create();
        try (FileReader fr = new FileReader(json)) {
            return gson.fromJson(fr, NativeBuildConfigValue.class);
        }
    }

    /**
     * Related to b.android.com/214558 Clean commands should never have -j (or --jobs) parameter
     * because gnu make doesn't handle this well.
     */
    @Test
    public void checkNdkBuildCleanHasNoJobsFlags() throws Exception {
        assumeNotCMakeOnWindows();
        if (config.buildSystem == NativeBuildSystem.NDK_BUILD) {
            project.model().fetch(NativeAndroidProject.class);
            NativeBuildConfigValue buildConfig =
                    getNativeBuildConfigValue(getJsonFile("debug", "x86_64"));
            assert buildConfig.cleanCommands != null;
            for (String cleanCommand : buildConfig.cleanCommands) {
                assertThat(cleanCommand).doesNotContain("-j");
            }
        }
    }

    @Test
    public void checkDebugVsRelease() throws Exception {
        assumeNotCMakeOnWindows();
        // Sync
        project.model().fetch(NativeAndroidProject.class);

        File debugJson = getJsonFile("debug", "x86_64");
        File releaseJson = getJsonFile("release", "x86_64");

        NativeBuildConfigValue debug = getNativeBuildConfigValue(debugJson);
        NativeBuildConfigValue release = getNativeBuildConfigValue(releaseJson);

        Set<String> releaseFlags = uniqueFlags(release);
        Set<String> debugFlags = uniqueFlags(debug);

        // Look at flags that are only in release build. Should at least contain -DNDEBUG and -Os
        Set<String> releaseOnlyFlags = Sets.newHashSet(releaseFlags);
        releaseOnlyFlags.removeAll(debugFlags);
        assertThat(releaseOnlyFlags)
                .named("release only build flags")
                .containsAllOf("-DNDEBUG", "-O2");

        // Look at flags that are only in debug build. Should at least contain -O0
        Set<String> debugOnlyFlags = Sets.newHashSet(debugFlags);
        debugOnlyFlags.removeAll(releaseFlags);
        assertThat(debugOnlyFlags).named("debug only build flags").contains("-O0");
    }

    private static Set<String> uniqueFlags(NativeBuildConfigValue config) {
        Set<String> flags = Sets.newHashSet();
        for (NativeLibraryValue library : config.libraries.values()) {
            assert library.files != null;
            for (NativeSourceFileValue file : library.files) {
                String fileFlags;
                if (file.flags == null) {
                    assert config.stringTable != null;
                    fileFlags = config.stringTable.get(file.flagsOrdinal);

                } else {
                    fileFlags = file.flags;
                }
                flags.addAll(StringHelper.tokenizeCommandLineToRaw(fileFlags));
            }
        }
        return flags;
    }

    /*
    The best file system timestamp is millisecond and lower resolution is available depending on
    operating system and Java versions. This implementation of touch makes sure that the new
    timestamp isn't the same as the old timestamp by spinning until the clock increases.
     */
    private static void spinTouch(File file, long lastTimestamp) throws IOException {
        file.setLastModified(System.currentTimeMillis());
        while (getHighestResolutionTimeStamp(file) == lastTimestamp) {
            file.setLastModified(System.currentTimeMillis());
        }
    }

    private static long getHighestResolutionTimeStamp(File file) throws IOException {
        return Files.getLastModifiedTime(
                file.toPath()).toMillis();
    }

    private File getJsonFile(String variantName, String abi) {
        return ExternalNativeBuildTaskUtils.getOutputJson(
                FileUtils.join(
                        buildNativeBuildOutputPath(config, project),
                        config.buildSystem.getName(),
                        variantName),
                abi);
    }

    /** Validates the NativeBuildConfigValue. Note: workingDirectory is optional. */
    private static void checkSourceFileValue(@NonNull NativeBuildConfigValue config) {
        assert config.libraries != null;
        for (NativeLibraryValue library : config.libraries.values()) {
            assert library.files != null;
            for (NativeSourceFileValue nativeSourceFileValue : library.files) {
                assertThat(nativeSourceFileValue.src).exists();
                String workingDirectory;
                if (nativeSourceFileValue.workingDirectoryOrdinal != null) {
                    assertThat(nativeSourceFileValue.flagsOrdinal).isNotNull();
                    assertThat(config.stringTable).isNotNull();
                    workingDirectory =
                            config.stringTable.get(nativeSourceFileValue.workingDirectoryOrdinal);

                } else {
                    assertThat(nativeSourceFileValue.flags).isNotEmpty();
                    if (nativeSourceFileValue.workingDirectory != null) {
                        workingDirectory = nativeSourceFileValue.workingDirectory.toString();
                    } else {
                        workingDirectory = null;
                    }
                }
                if (workingDirectory != null) {
                    assertThat(new File(workingDirectory)).exists();
                }
            }
        }
    }

    private static void checkIncludesPresent(NativeAndroidProject model) {
        for (NativeSettings settings : model.getSettings()) {
            boolean sawInclude = false;
            boolean sawSystemInclude = false;
            for (String flag : settings.getCompilerFlags()) {
                if (flag.startsWith("-I")) {
                    sawInclude = true;
                }
                if (flag.startsWith("-system") || flag.startsWith("--sysroot")) {
                    sawInclude = true;
                    sawSystemInclude = true;
                }
            }
            assertThat(sawInclude).isTrue();
            assertThat(sawSystemInclude).isTrue();
        }
    }

    /**
     * Certain CMake server bugs cause specific C++ flags to disappear, this test tries to catch
     * those cases. Check whether "--target" is present in the flags.
     */
    private static void checkTargetTriplePresent(NativeAndroidProject model) {
        for (NativeSettings settings : model.getSettings()) {
            boolean sawTargetTriple = false;
            for (String flag : settings.getCompilerFlags()) {
                if (flag.startsWith("--target")) {
                    sawTargetTriple = true;
                }
            }
            assertThat(sawTargetTriple).isTrue();
        }
    }

    private static void checkDefaultVariants(NativeAndroidProject model) {
        assertThat(model).hasArtifactGroupsNamed("debug", "release");

        for (NativeSettings settings : model.getSettings()) {
            assertThat(settings).doesNotContainCompilerFlag("-DCUSTOM_BUILD_TYPE");
        }
    }

    private static void checkCustomVariants(NativeAndroidProject model) {
        assertThat(model).hasArtifactGroupsNamed("debug", "release", "myCustomBuildType");

        boolean sawCustomVariantFLag = false;
        for (NativeSettings settings : model.getSettings()) {
            List<String> flags = settings.getCompilerFlags();
            if (flags.contains("-DCUSTOM_BUILD_TYPE")) {
                assertThat(settings).containsCompilerFlag("-DCUSTOM_BUILD_TYPE");
                sawCustomVariantFLag = true;
            }
        }
        assertThat(sawCustomVariantFLag).isTrue();
    }

    private static void checkGcc(NativeAndroidProject model) {
        for (NativeSettings settings : model.getSettings()) {
            assertThat(settings).doesNotContainCompilerFlagStartingWith("-gcc-toolchain");
        }
    }

    private static void checkClang(NativeAndroidProject model) {
        for (NativeSettings settings : model.getSettings()) {
            assertThat(settings).containsCompilerFlagStartingWith("-gcc-toolchain");
        }
    }

    private static void checkProblematicCompilerFlags(NativeAndroidProject model) {
        for (NativeSettings settings : model.getSettings()) {
            // These flags are known to cause problems, see b.android.com/215555 and
            // b.android.com/213429. They should be stripped (or not present) by JSON producer.
            assertThat(settings).doesNotContainCompilerFlag("-MMD");
            assertThat(settings).doesNotContainCompilerFlag("-MP");
            assertThat(settings).doesNotContainCompilerFlag("-MT");
            assertThat(settings).doesNotContainCompilerFlag("-MQ");
            assertThat(settings).doesNotContainCompilerFlag("-MG");
            assertThat(settings).doesNotContainCompilerFlag("-M");
            assertThat(settings).doesNotContainCompilerFlag("-MM");
        }
    }

    private static void checkIsC(NativeAndroidProject model) {
        assertThat(model.getFileExtensions()).containsEntry("c", "c");
        assertThat(model.getFileExtensions()).doesNotContainEntry("cpp", "c++");
        for (NativeSettings settings : model.getSettings()) {
            assertThat(settings).containsCompilerFlag("-DTEST_C_FLAG");
            assertThat(settings).doesNotContainCompilerFlag("-DTEST_CPP_FLAG");
        }
    }

    private static void checkIsCpp(NativeAndroidProject model) {
        assertThat(model.getFileExtensions()).containsEntry("cpp", "c++");
        assertThat(model.getFileExtensions()).doesNotContainEntry("c", "c");
        for (NativeSettings settings : model.getSettings()) {
            assertThat(settings).containsCompilerFlag("-DTEST_CPP_FLAG");
        }
    }

    private static void checkIsChangedC(NativeAndroidProject model) {
        assertThat(model.getFileExtensions()).containsEntry("c", "c");
        for (NativeSettings settings : model.getSettings()) {
            assertThat(settings).doesNotContainCompilerFlag("-DTEST_C_FLAG");
            assertThat(settings).doesNotContainCompilerFlag("-DTEST_CPP_FLAG");
            assertThat(settings).containsCompilerFlag("-DTEST_CHANGED_C_FLAG");
            assertThat(settings).doesNotContainCompilerFlag("-DTEST_CHANGED_CPP_FLAG");
        }
    }

    private static void checkIsChangedCpp(NativeAndroidProject model) {
        assertThat(model.getFileExtensions()).containsEntry("cpp", "c++");
        for (NativeSettings settings : model.getSettings()) {
            assertThat(settings).doesNotContainCompilerFlag("-DTEST_CPP_FLAG");
            assertThat(settings).containsCompilerFlag("-DTEST_CHANGED_CPP_FLAG");
        }
    }

    private static void checkNativeBuildOutputPath(Config config, GradleTestProject project) {
        NativeBuildSystem buildSystem = config.buildSystem;
        File outputDir = buildNativeBuildOutputPath(config, project);

        switch (buildSystem) {
            case CMAKE:
                outputDir = FileUtils.join(outputDir, "cmake");
                break;
            case NDK_BUILD:
                outputDir = FileUtils.join(outputDir, "ndkBuild");
                break;
            default:
                return;
        }

        assertThat(outputDir).exists();
        assertThat(outputDir).isDirectory();
    }

    private static File buildNativeBuildOutputPath(Config config, GradleTestProject project) {
        String nativeBuildOutputPath = config.nativeBuildOutputPath;
        File projectDir = project.getTestDir();

        File outputDir = new File(nativeBuildOutputPath);
        if (!outputDir.isAbsolute()) {
            outputDir = FileUtils.join(projectDir, nativeBuildOutputPath);
        }

        return outputDir;
    }

    private static String escapeWindowsCharacters(String path) {
        return path.replace("\\", "\\\\");
    }

    private void assumeNotCMakeOnWindows() {
        // CMake project generation is too slow on Windows. Disable test there until performance
        // is improved. This leaves the test on for Linux/PSQ
        Assume.assumeTrue(
                SdkConstants.currentPlatform() != SdkConstants.PLATFORM_WINDOWS
                        || this.config.buildSystem != NativeBuildSystem.CMAKE);
    }
}
