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
package com.android.build.gradle.external.gnumake;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValue;
import com.android.build.gradle.internal.cxx.json.NativeLibraryValue;
import com.android.build.gradle.internal.cxx.json.NativeSourceFileValue;
import com.android.build.gradle.truth.NativeBuildConfigValueSubject;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NdkSampleTest {

    // The purpose of this test parameter is to also test Linux synthetic file functions
    // even when running on Linux (and the same for Windows) so that when you're running
    // tests on Linux you can test whether your changes broken the corresponding synthetic
    // test on Windows (and vice-versa).
    @Parameterized.Parameters(name = "forceSyntheticFileFunctions = {0}")
    public static Collection<Object[]> data() {
        List<Object[]> result = new ArrayList<>();
        result.add(new Object[] {false});
        result.add(new Object[] {true});
        return result;
    }

    private boolean forceSyntheticFileFunctions;

    public NdkSampleTest(boolean forceSyntheticFileFunctions) {
        this.forceSyntheticFileFunctions = forceSyntheticFileFunctions;
    }

    // Turn this flag to true to regenerate test baselines in the case that output has intentionally
    // changed. Should never be checked in as 'true'.
    @SuppressWarnings("FieldCanBeLocal")
    private static final boolean REGENERATE_TEST_BASELINES = false;
    // Turn this flag to true to regenerate test JSON from preexisting baselines in the case that
    // output has intentionally changed.
    @SuppressWarnings("FieldCanBeLocal")
    private static final boolean REGENERATE_TEST_JSON_FROM_TEXT = false;
    @NonNull
    private static final String THIS_TEST_FOLDER =
            "src/test/java/com/android/build/gradle/external/gnumake/";

    private static final ImmutableList<CommandClassifier.BuildTool> extraTestClassifiers =
            ImmutableList.of(
                    new NdkBuildWarningBuildTool(),
                    new NoOpBuildTool("bcc_compat"), // Renderscript
                    new NoOpBuildTool("llvm-rs-cc"), // Renderscript
                    new NoOpBuildTool("rm"),
                    new NoOpBuildTool("cd"),
                    new NoOpBuildTool("cp"),
                    new NoOpBuildTool("md"),
                    new NoOpBuildTool("del"),
                    new NoOpBuildTool("echo.exe"),
                    new NoOpBuildTool("mkdir"),
                    new NoOpBuildTool("echo"),
                    new NoOpBuildTool("copy"),
                    new NoOpBuildTool("install"),
                    new NoOpBuildTool("androideabi-strip"),
                    new NoOpBuildTool("android-strip"));

    /**
     * This build tool skips warning that can be emitted by ndk-build during -n -B processing.
     * Example, Android NDK: WARNING: APP_PLATFORM android-19 is larger than android:minSdkVersion
     * 14 in {ndkPath}/samples/HelloComputeNDK/AndroidManifest.xml
     */
    static class NdkBuildWarningBuildTool implements CommandClassifier.BuildTool {
        @NonNull
        @Override
        public BuildStepInfo createCommand(@NonNull CommandLine command) {
            return new BuildStepInfo(command, Lists.newArrayList(), Lists.newArrayList());
        }

        @Override
        public boolean isMatch(@NonNull CommandLine command) {
            return command.executable.equals("Android");
        }
    }

    /**
     * This build tool recognizes a particular command and treats it as a build step with no inputs
     * and no outputs.
     */
    static class NoOpBuildTool implements CommandClassifier.BuildTool {
        @NonNull private final String executable;

        NoOpBuildTool(@NonNull String executable) {
            this.executable = executable;
        }

        @NonNull
        @Override
        public BuildStepInfo createCommand(@NonNull CommandLine command) {
            return new BuildStepInfo(command, Lists.newArrayList(), Lists.newArrayList(), false);
        }

        @Override
        public boolean isMatch(@NonNull CommandLine command) {
            return command.executable.endsWith(executable);
        }
    }


    private static class Spawner {
        private static final int THREAD_JOIN_TIMEOUT_MILLIS = 2000;

        private static Process platformExec(String command) throws IOException {
            if (System.getProperty("os.name").contains("Windows")) {
                return Runtime.getRuntime().exec(new String[]{"cmd", "/C", command});
            } else {
                return Runtime.getRuntime().exec(new String[]{"bash", "-c", command});
            }
        }

        @NonNull
        private static String spawn(String command) throws IOException, InterruptedException {
            Process proc = platformExec(command);

            // any error message?
            StreamReaderThread errorThread = new
                    StreamReaderThread(proc.getErrorStream());

            // any output?
            StreamReaderThread outputThread = new
                    StreamReaderThread(proc.getInputStream());

            // kick them off
            errorThread.start();
            outputThread.start();

            // Wait for process to finish
            proc.waitFor();

            // Wait for output capture threads to finish
            errorThread.join(THREAD_JOIN_TIMEOUT_MILLIS);
            outputThread.join(THREAD_JOIN_TIMEOUT_MILLIS);

            if (proc.exitValue() != 0) {
                System.err.println(errorThread.result());
                throw new RuntimeException(
                        String.format("Spawned process failed with code %s", proc.exitValue()));
            }

            if (errorThread.ioe != null) {
                throw new RuntimeException(
                        String.format("Problem reading stderr: %s", errorThread.ioe));
            }

            if (outputThread.ioe != null) {
                throw new RuntimeException(
                        String.format("Problem reading stdout: %s", outputThread.ioe));
            }

            return outputThread.result();
        }

        /**
         * Read an input stream off of the main thread
         */
        private static class StreamReaderThread extends Thread {
            private final InputStream is;
            @SuppressWarnings("StringBufferField")
            @NonNull
            private final StringBuilder output = new StringBuilder();
            @Nullable
            IOException ioe = null;

            public StreamReaderThread(InputStream is) {
                this.is = is;
            }

            @NonNull
            public String result() {
                return output.toString();
            }

            @Override
            public void run() {
                try {
                    InputStreamReader streamReader = new InputStreamReader(is);
                    try (BufferedReader bufferedReader = new BufferedReader(streamReader)) {
                        String line;
                        while ((line = bufferedReader.readLine()) != null) {
                            output.append(line);
                            output.append("\n");
                        }
                    }
                    //noinspection ThrowFromFinallyBlock

                } catch (IOException ioe) {
                    this.ioe = ioe;
                }
            }
        }
    }

    private static Map<String, String> getVariantConfigs() {
        return ImmutableMap.<String, String>builder()
                .put("debug", "NDK_DEBUG=1")
                .put("release", "NDK_DEBUG=0")
                .build();
    }

    @NonNull
    private static File getVariantBuildOutputFile(
            @NonNull File testPath,
            @NonNull String variant,
            int operatingSystem) {
        return new File(
                THIS_TEST_FOLDER
                        + "support-files/ndk-sample-baselines/"
                        + testPath.getName()
                        + "." + variant + "." + getOsName(operatingSystem) + ".txt");
    }

    @NonNull
    private static String getOsName(int os) {
        switch (os) {
            case SdkConstants.PLATFORM_LINUX:
                return "linux";
            case SdkConstants.PLATFORM_DARWIN:
                return "darwin";
            case SdkConstants.PLATFORM_WINDOWS:
                return "windows";
            default:
                return "unknown";
        }
    }

    @NonNull
    private static File getJsonFile(@NonNull File testPath, int operatingSystem) {
        return new File(
                THIS_TEST_FOLDER + "support-files/ndk-sample-baselines/"
                        + testPath.getName() + "." + getOsName(operatingSystem) + ".json");
    }

    @NonNull
    private static String getNdkResult(
            @NonNull File projectPath, String flags) throws IOException, InterruptedException {

        String command =
                String.format(
                        TestUtils.getNdk() + "/ndk-build -B -n NDK_PROJECT_PATH=%s %s",
                        projectPath.getAbsolutePath(),
                        flags);
        return Spawner.spawn(command);
    }

    private NativeBuildConfigValue checkJson(String path) throws IOException, InterruptedException {
        return checkJson(path, SdkConstants.PLATFORM_LINUX);
    }

    private NativeBuildConfigValue checkJson(String path, int operatingSystem)
            throws IOException, InterruptedException {

        File ndkPath = TestUtils.getNdk();
        File androidMkPath = new File(ndkPath, path);
        Map<String, String> variantConfigs = getVariantConfigs();

        // Get the baseline config
        File baselineJsonFile = getJsonFile(androidMkPath, operatingSystem);

        if (REGENERATE_TEST_BASELINES) {
            File directory = new File(THIS_TEST_FOLDER + "support-files/ndk-sample-baselines");
            if (!directory.exists()) {
                //noinspection ResultOfMethodCallIgnored
                directory.mkdir();
            }

            // Create the output .txt for each variant by running ndk-build
            for (String variantName : variantConfigs.keySet()) {
                String variantBuildOutputText =
                        getNdkResult(androidMkPath, variantConfigs.get(variantName));
                variantBuildOutputText =
                        variantBuildOutputText
                                //   .replace("//", "/")
                                .replace("windows", "{platform}")
                                .replace("linux", "{platform}")
                                .replace("darwin", "{platform}")
                                .replace(THIS_TEST_FOLDER, "{test}");
                File variantBuildOutputFile =
                        getVariantBuildOutputFile(androidMkPath, variantName, operatingSystem);

                Files.asCharSink(variantBuildOutputFile, Charsets.UTF_8)
                        .write(variantBuildOutputText);
            }
        }

        // Build the expected result
        OsFileConventions fileConventions = getPathHandlingPolicy(operatingSystem);
        NativeBuildConfigValueBuilder builder =
                new NativeBuildConfigValueBuilder(
                        androidMkPath, new File("{executeFromHere}"), fileConventions);
        for (String variantName : variantConfigs.keySet()) {
            File variantBuildOutputFile =
                    getVariantBuildOutputFile(androidMkPath, variantName, operatingSystem);
            String variantBuildOutputText = Joiner.on('\n')
                    .join(Files.readLines(variantBuildOutputFile, Charsets.UTF_8));

            builder.addCommands(
                    "echo build command",
                    "echo clean command",
                    variantName,
                    variantBuildOutputText);

            // Add extra command classifiers that are supposed to match all commands in the test.
            // The checks below well see whether there are extra commands we don't know about.
            // If there are unknown commands we need to evaluate whether they should be understood
            // by the parser or just ignored (added to extraTestClassifiers)
            List<CommandClassifier.BuildTool> testClassifiers = Lists.newArrayList();
            testClassifiers.addAll(CommandClassifier.DEFAULT_CLASSIFIERS);
            testClassifiers.addAll(extraTestClassifiers);
            List<CommandLine> commandLines =
                    CommandLineParser.parse(variantBuildOutputText, fileConventions);
            List<BuildStepInfo> recognized =
                    CommandClassifier.classify(
                            variantBuildOutputText, fileConventions, testClassifiers);
            checkAllCommandsRecognized(commandLines, recognized);
            checkExpectedCompilerParserBehavior(commandLines);
        }

        NativeBuildConfigValue actualConfig = builder.build();
        String actualResult = new GsonBuilder()
                .setPrettyPrinting()
                .create()
                .toJson(actualConfig);
        checkOutputsHaveWhitelistedExtensions(actualConfig);

        String testPathString = androidMkPath.toString();
        // actualResults contains JSon as text. JSon escapes back slash with a second backslash.
        // Backslash is also the directory separator on Windows. In order to properly replace
        // {testPath} we must follow the JSon escaping rule.
        testPathString = testPathString.replace("\\", "\\\\");
        actualResult = actualResult.replace(testPathString, "{testPath}");
        actualConfig = new Gson().fromJson(actualResult, NativeBuildConfigValue.class);

        if (REGENERATE_TEST_BASELINES
                || !baselineJsonFile.exists()
                || REGENERATE_TEST_JSON_FROM_TEXT) {
            Files.asCharSink(baselineJsonFile, Charsets.UTF_8).write(actualResult);
        }

        // Build the baseline result.
        String baselineResult = Joiner.on('\n')
                .join(Files.readLines(baselineJsonFile, Charsets.UTF_8));

        NativeBuildConfigValue baselineConfig = new Gson()
                .fromJson(baselineResult, NativeBuildConfigValue.class);
        assertConfig(actualConfig).isEqualTo(baselineConfig);
        assertConfig(actualConfig).hasUniqueLibraryNames();
        return actualConfig;
    }

    @NonNull
    private OsFileConventions getPathHandlingPolicy(int scriptSourceOS) {
        if (scriptSourceOS == SdkConstants.currentPlatform() && !forceSyntheticFileFunctions) {
            // The script was created on the OS that is currently executing.
            // Just use the default path handler which defers to built-in Java file handling
            // functions.
            return AbstractOsFileConventions.createForCurrentHost();
        }
        // Otherwise, create a test-only path handler that will work from the current OS on
        // script -nB output that was produced on another host.
        switch (scriptSourceOS) {
            case SdkConstants.PLATFORM_WINDOWS:
                // Script -nB was produced on Windows but the current OS isn't windows.
                return getSyntheticWindowsPathHandlingPolicy();
            case SdkConstants.PLATFORM_LINUX:
                // Script -nB was produced on linux, but the current host is something else.
                // If the current host is Windows, then produce a handler for that will work
                return getSyntheticLinuxPathHandlingPolicy();
        }

        // If the current host is not Windows or Linux then it isn't a case we currently
        // have scripts for.
        throw new RuntimeException(
                "Need a cross-OS path handling policy for script source OS " + scriptSourceOS);
    }

    @NonNull
    private static OsFileConventions getSyntheticLinuxPathHandlingPolicy() {
        return new PosixFileConventions() {
            @Override
            public boolean isPathAbsolute(@NonNull String file) {
                return file.startsWith("/");
            }

            @NonNull
            @Override
            public String getFileName(@NonNull String filename) {
                int pos = getLastIndexOfAnyFilenameSeparator(filename);
                return filename.substring(pos + 1);
            }

            @NonNull
            @Override
            public String getFileParent(@NonNull String filename) {
                int pos = getLastIndexOfAnyFilenameSeparator(filename);
                return filename.substring(0, pos);
            }
        };
    }

    @NonNull
    private static OsFileConventions getSyntheticWindowsPathHandlingPolicy() {
        return new WindowsFileConventions() {
            @Override
            public boolean isPathAbsolute(@NonNull String file) {
                if (file.length() < 3) {
                    // Not enough space for a drive letter.
                    return false;
                }

                String segment = file.substring(1, 3);
                return segment.equals(":/") || segment.equals(":\\");
            }

            @NonNull
            @Override
            public String getFileName(@NonNull String filename) {
                int pos = getLastIndexOfAnyFilenameSeparator(filename);
                return filename.substring(pos + 1);
            }

            @NonNull
            @Override
            public String getFileParent(@NonNull String filename) {
                int pos = getLastIndexOfAnyFilenameSeparator(filename);
                return filename.substring(0, pos);
            }

            @NonNull
            @Override
            public File toFile(@NonNull String filename) {
                filename = filename.replace('\\', '/');
                return new File(filename);
            }

            @NonNull
            @Override
            public File toFile(@NonNull File parent, @NonNull String child) {
                return new File(parent.toString().replace('\\', '/'), child.replace('\\', '/'));
            }
        };
    }

    private static int getLastIndexOfAnyFilenameSeparator(String filename) {
        return Math.max(filename.lastIndexOf('\\'), filename.lastIndexOf('/'));
    }

    private static void checkOutputsHaveWhitelistedExtensions(
            @NonNull NativeBuildConfigValue config) {
        checkNotNull(config.libraries);
        for (NativeLibraryValue library : config.libraries.values()) {
            // These are the three extensions that should occur. These align with what CMake does.
            checkNotNull(library.output);
            if (library.output.toString().endsWith(".so")) {
                continue;
            }
            if (library.output.toString().endsWith(".a")) {
                continue;
            }
            if (!library.output.toString().contains(".")) {
                continue;
            }
            throw new RuntimeException(
                    String.format("Library output %s had an unexpected extension", library.output));
        }
    }

    private static void checkAllCommandsRecognized(
            @NonNull List<CommandLine> commandLines,
            @NonNull List<BuildStepInfo> recognizedBuildSteps) {

        // Check that outputs occur only once
        Map<String, BuildStepInfo> outputs = Maps.newHashMap();
        for (BuildStepInfo recognizedBuildStep : recognizedBuildSteps) {
            for (String output : recognizedBuildStep.getOutputs()) {
                // Check for duplicate names
                assertThat(outputs.keySet()).doesNotContain(output);
                outputs.put(output, recognizedBuildStep);
            }
        }

        if (commandLines.size() != recognizedBuildSteps.size()) {
            // Build a set of executable commands that were classified.
            Set<String> recognizedCommandLines = Sets.newHashSet();
            for (BuildStepInfo recognizedBuildStep : recognizedBuildSteps) {
                recognizedCommandLines.add(recognizedBuildStep.getCommand().executable);
            }

            assertThat(recognizedCommandLines).containsAllIn(commandLines);
        }
    }

    // Find the compiler commands and check their parse against expected parse.
    private static void checkExpectedCompilerParserBehavior(@NonNull List<CommandLine> commands) {
        for (CommandLine command : commands) {
            if (new CommandClassifier.NativeCompilerBuildTool().isMatch(command)) {
                for (String arg : command.escapedFlags) {
                    if (arg.startsWith("-")) {
                        String trimmed = arg;
                        while (trimmed.startsWith("-")) {
                            trimmed = trimmed.substring(1);
                        }
                        boolean matched = false;
                        for (String withRequiredArgFlag : CompilerParser.WITH_REQUIRED_ARG_FLAGS) {
                            if (trimmed.startsWith(withRequiredArgFlag)) {
                                matched = true;
                            }
                        }

                        for (String withNoArgsFlag : CompilerParser.WITH_NO_ARG_FLAGS) {
                            if (trimmed.equals(withNoArgsFlag)) {
                                matched = true;
                            }
                        }

                        // Recognize -W style flag
                        if (trimmed.startsWith("W")) {
                            matched = true;
                        }

                        if (!matched) {
                            // If you get here, there is a new gcc or clang flag in a baseline test.
                            // For completeness, you should add this flag in CompilerParser.
                            throw new RuntimeException(
                                    "The flag " + arg + " was not a recognized compiler flag");

                        }
                    }
                }
            }
        }
    }

    /*
    Why is NativeBuildConfigValueSubject assertThat here?

        Current state
        -------------
        (1) NativeBuildConfigValue is in gradle-core package
        (2) NativeBuildConfigValueBuilder is in gradle-core package
        (3) therefore NativeBuildConfigValueBuilder tests are in gradle-core-test package
        (4) NativeBuildConfigValueSubject is in gradle-core-tests package
        (5) MoreTruth is in testutils package which does not reference gradle-core
        (6) therefore NativeBuildConfigValueSubject can't be in MoreTruth
        (7) also therefore I can't use MoreTruth from NativeBuildConfigValueBuilder tests

     */
    @NonNull
    public static NativeBuildConfigValueSubject assertConfig(
            @Nullable NativeBuildConfigValue project) {
        return assert_().about(NativeBuildConfigValueSubject.FACTORY).that(project);
    }

    @Test
    public void dontCheckInBaselineUpdaterFlags() {
        assertThat(REGENERATE_TEST_BASELINES).isFalse();
        assertThat(REGENERATE_TEST_JSON_FROM_TEXT).isFalse();
    }

    @Test
    public void syntheticWindowsPathHandlingAbsolutePath() {
        assertThat(getSyntheticWindowsPathHandlingPolicy().isPathAbsolute("C:\\")).isTrue();
        assertThat(getSyntheticWindowsPathHandlingPolicy().isPathAbsolute("C:/")).isTrue();
        assertThat(getSyntheticWindowsPathHandlingPolicy().isPathAbsolute("\\")).isFalse();
        assertThat(getSyntheticWindowsPathHandlingPolicy().isPathAbsolute("/")).isFalse();
        assertThat(getSyntheticWindowsPathHandlingPolicy().isPathAbsolute("\\x")).isFalse();
        assertThat(getSyntheticWindowsPathHandlingPolicy().isPathAbsolute("/x")).isFalse();
        assertThat(getSyntheticWindowsPathHandlingPolicy().isPathAbsolute("C:x")).isFalse();
    }



    // Related to b.android.com/227685 which caused wrong file path in src file when path was
    // relative to folder containing build.gradle. Fix was to make the path absolute by explicitly
    // rooting it under execute path.
    @Test
    public void cocos2d() throws IOException, InterruptedException {
        NativeBuildConfigValue config = checkJson("samples/cocos2d");
        // Expect relative paths to be rooted at execute path
        assertConfig(config)
                .hasSourceFileNames(
                        FileUtils.toSystemDependentPath(
                                "{executeFromHere}/../../../../external/bullet/"
                                        + "BulletMultiThreaded/btThreadSupportInterface.cpp"));
    }

    // Related to issuetracker.google.com/69110338. Covers case where there is a compiler flag
    // with spaces like -DMY_FLAG='my value'
    @Test
    public void singleQuotedDefine() throws IOException, InterruptedException {
        NativeBuildConfigValue config = checkJson("samples/tick-in-define-repro");
        assertConfig(config).hasExactLibrariesNamed("example-debug-armeabi-v7a");
        NativeSourceFileValue file =
                config.libraries.get("example-debug-armeabi-v7a").files.iterator().next();
        // Below is the actual fact we're trying to assert for this bug. We need to preserve
        // the single quotes around hello world
        assertThat(file.flags).contains("-DTOM='hello world'");
    }

    // Related to b.android.com/216676. Same source file name produces same target name.
    @Test
    public void duplicateSourceNames() throws IOException, InterruptedException {
        NativeBuildConfigValue config = checkJson("samples/duplicate-source-names");
        assertConfig(config)
                .hasExactLibrariesNamed(
                        "apple-release-mips64",
                        "apple-debug-mips",
                        "apple-release-armeabi",
                        "banana-release-arm64-v8a",
                        "hello-jni-debug-armeabi",
                        "hello-jni-debug-x86_64",
                        "banana-debug-armeabi",
                        "hello-jni-release-arm64-v8a",
                        "banana-release-x86",
                        "banana-debug-mips64",
                        "banana-release-armeabi-v7a",
                        "hello-jni-debug-x86",
                        "apple-release-armeabi-v7a",
                        "hello-jni-release-mips",
                        "banana-release-armeabi",
                        "hello-jni-debug-mips",
                        "apple-release-mips",
                        "hello-jni-release-mips64",
                        "hello-jni-debug-armeabi-v7a",
                        "banana-debug-x86_64",
                        "apple-debug-arm64-v8a",
                        "apple-release-x86_64",
                        "apple-debug-armeabi",
                        "hello-jni-release-armeabi",
                        "apple-release-x86",
                        "hello-jni-release-x86",
                        "banana-debug-arm64-v8a",
                        "hello-jni-debug-mips64",
                        "hello-jni-release-x86_64",
                        "banana-debug-mips",
                        "apple-debug-mips64",
                        "apple-debug-x86",
                        "apple-release-arm64-v8a",
                        "banana-debug-x86",
                        "banana-release-mips64",
                        "hello-jni-debug-arm64-v8a",
                        "banana-debug-armeabi-v7a",
                        "hello-jni-release-armeabi-v7a",
                        "banana-release-x86_64",
                        "apple-debug-armeabi-v7a",
                        "apple-debug-x86_64",
                        "banana-release-mips");
    }

    // Related to b.android.com/218397. On Windows, the wrong target name was used because it
    // was passed through File class which caused slashes to be normalized to back slash.
    @Test
    public void windowsTargetName() throws IOException, InterruptedException {
        NativeBuildConfigValue config = checkJson("samples/windows-target-name",
                SdkConstants.PLATFORM_WINDOWS);
        assertConfig(config)
                .hasExactLibrariesNamed(
                        "hello-jni-debug-mips",
                        "hello-jni-release-mips64",
                        "hello-jni-debug-armeabi-v7a",
                        "hello-jni-debug-armeabi",
                        "hello-jni-debug-x86_64",
                        "hello-jni-release-arm64-v8a",
                        "hello-jni-release-armeabi",
                        "hello-jni-release-x86",
                        "hello-jni-debug-mips64",
                        "hello-jni-release-x86_64",
                        "hello-jni-debug-arm64-v8a",
                        "hello-jni-debug-x86",
                        "hello-jni-release-armeabi-v7a",
                        "hello-jni-release-mips");
    }

    // Related to b.android.com/214626
    @Test
    public void localModuleFilename() throws IOException, InterruptedException {
        checkJson("samples/LOCAL_MODULE_FILENAME");
    }

    @Test
    public void includeFlag() throws IOException, InterruptedException {
        checkJson("samples/include-flag");
    }

    @Test
    public void clangExample() throws IOException, InterruptedException {
        NativeBuildConfigValue config = checkJson("samples/clang");
        // Assert that full paths coming from the ndk-build output aren't further qualified with
        // executution path.
        assertConfig(config)
                .hasExactSourceFileNames(
                        FileUtils.toSystemDependentPath(
                                "/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/"
                                        + "sources/android/cpufeatures/cpu-features.c"),
                        FileUtils.toSystemDependentPath(
                                "/usr/local/google/home/jomof/projects/"
                                        + "hello-neon1/app/src/main/cpp/helloneon.c"));
    }

    @Test
    public void neonExample() throws IOException, InterruptedException {
        checkJson("samples/neon");
    }

    @Test
    public void ccacheExample() throws IOException, InterruptedException {
        // CCache is turned on in ndk build by setting NDK_CCACHE to a path to ccache
        // executable.
        checkJson("samples/ccache");
    }

    @Test
    public void googleTestExample() throws IOException, InterruptedException {
        NativeBuildConfigValue config = checkJson("samples/google-test-example");
        assertConfig(config)
                .hasExactLibraryOutputs(
                        FileUtils.toSystemDependentPath(
                                "{NDK}/debug/obj/local/arm64-v8a/libgoogletest_static.a"),
                        FileUtils.toSystemDependentPath(
                                "{NDK}/debug/obj/local/arm64-v8a/sample1_unittest"),
                        FileUtils.toSystemDependentPath(
                                "{NDK}/debug/obj/local/arm64-v8a/libsample1.so"),
                        FileUtils.toSystemDependentPath(
                                "{NDK}/debug/obj/local/arm64-v8a/libgoogletest_main.a"));
    }

    @Test
    public void missingIncludeExample() throws IOException, InterruptedException {
        checkJson("samples/missing-include");
    }

    @Test
    public void sanAngelesExample() throws IOException, InterruptedException {
        checkJson("samples/san-angeles", SdkConstants.PLATFORM_LINUX);
    }

    @Test
    public void sanAngelesWindows() throws IOException, InterruptedException {
        checkJson("samples/san-angeles", SdkConstants.PLATFORM_WINDOWS);
    }

    // input: support-files/ndk-sample-baselines/Teapot.json
    @Test
    public void teapot() throws IOException, InterruptedException {
        checkJson("samples/Teapot");
    }

    // input: support-files/ndk-sample-baselines/native-audio.json
    @Test
    public void nativeAudio() throws IOException, InterruptedException {
        checkJson("samples/native-audio");
    }

    // input: support-files/ndk-sample-baselines/native-codec.json
    @Test
    public void nativeCodec() throws IOException, InterruptedException {
        checkJson("samples/native-codec");
    }

    // input: support-files/ndk-sample-baselines/native-media.json
    @Test
    public void nativeMedia() throws IOException, InterruptedException {
        checkJson("samples/native-media");
    }

    // input: support-files/ndk-sample-baselines/native-plasma.json
    @Test
    public void nativePlasma() throws IOException, InterruptedException {
        checkJson("samples/native-plasma");
    }

    // input: support-files/ndk-sample-baselines/bitmap-plasma.json
    @Test
    public void bitmapPlasm() throws IOException, InterruptedException {
        checkJson("samples/bitmap-plasma");
    }

    // input: support-files/ndk-sample-baselines/native-activity.json
    @Test
    public void nativeActivity() throws IOException, InterruptedException {
        checkJson("samples/native-activity");
    }

    // input: support-files/ndk-sample-baselines/HelloComputeNDK.json
    @Test
    public void helloComputeNDK() throws IOException, InterruptedException {
        NativeBuildConfigValue config = checkJson("samples/HelloComputeNDK");
        assertConfig(config)
                .hasExactLibraryOutputs(
                        FileUtils.toSystemDependentPath(
                                "/{ndkPath}/samples/HelloComputeNDK/obj/local/x86/libhellocomputendk.so"),
                        FileUtils.toSystemDependentPath(
                                "/{ndkPath}/samples/HelloComputeNDK/libs/armeabi-v7a/librs.mono.so"),
                        FileUtils.toSystemDependentPath(
                                "/{ndkPath}/samples/HelloComputeNDK/obj/local/mips/libhellocomputendk.so"),
                        FileUtils.toSystemDependentPath(
                                "/{ndkPath}/samples/HelloComputeNDK/libs/mips/librs.mono.so"),
                        FileUtils.toSystemDependentPath(
                                "/{ndkPath}/samples/HelloComputeNDK/libs/x86/librs.mono.so"),
                        FileUtils.toSystemDependentPath(
                                "/{ndkPath}/samples/HelloComputeNDK/obj/local/armeabi-v7a/libhellocomputendk.so"));
    }

    // input: support-files/ndk-sample-baselines/test-libstdc++.json
    @Test
    public void testLibstdcpp() throws IOException, InterruptedException {
        checkJson("samples/test-libstdc++");
    }

    // input: support-files/ndk-sample-baselines/hello-gl2.json
    @Test
    public void helloGl2() throws IOException, InterruptedException {
        checkJson("samples/hello-gl2");
    }

    // input: support-files/ndk-sample-baselines/two-libs.json
    @Test
    public void twoLibs() throws IOException, InterruptedException {
        checkJson("samples/two-libs");
    }

    // input: support-files/ndk-sample-baselines/module-exports.json
    @Test
    public void moduleExports() throws IOException, InterruptedException {
        checkJson("samples/module-exports");
    }
}
