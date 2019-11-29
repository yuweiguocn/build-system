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

package com.android.build.gradle.integration.common.fixture;

import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.Assert.assertTrue;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.BasePlugin;
import com.android.build.gradle.integration.BazelIntegrationTestsSuite;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.Version;
import com.android.io.StreamException;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectPropertiesWorkingCopy;
import com.android.testutils.OsType;
import com.android.testutils.TestUtils;
import com.android.testutils.apk.Aar;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Zip;
import com.android.utils.FileUtils;
import com.android.utils.Pair;
import com.android.utils.StringHelper;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.util.GradleVersion;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * JUnit4 test rule for integration test.
 *
 * <p>This rule create a gradle project in a temporary directory. It can be use with the @Rule
 * or @ClassRule annotations. Using this class with @Rule will create a gradle project in separate
 * directories for each unit test, whereas using it with @ClassRule creates a single gradle project.
 *
 * <p>The test directory is always deleted if it already exists at the start of the test to ensure a
 * clean environment.
 */
public final class GradleTestProject implements TestRule {
    public static final String ENV_CUSTOM_REPO = "CUSTOM_REPO";


    public static final String DEFAULT_COMPILE_SDK_VERSION;

    public static final String DEFAULT_BUILD_TOOL_VERSION;
    public static final boolean APPLY_DEVICEPOOL_PLUGIN =
            Boolean.parseBoolean(System.getenv().getOrDefault("APPLY_DEVICEPOOL_PLUGIN", "false"));

    public static final boolean USE_LATEST_NIGHTLY_GRADLE_VERSION =
            Boolean.parseBoolean(System.getenv().getOrDefault("USE_GRADLE_NIGHTLY", "false"));
    public static final String GRADLE_TEST_VERSION;

    public static final String ANDROID_GRADLE_PLUGIN_VERSION;

    public static final String DEVICE_TEST_TASK = "deviceCheck";

    private static final int MAX_TEST_NAME_DIR_WINDOWS = 50;

    public static final File BUILD_DIR;
    public static final File OUT_DIR;
    private static final Path GRADLE_USER_HOME;
    public static final File ANDROID_SDK_HOME;

    /**
     * List of Apk file reference that should be closed and deleted once the TestRule is done. This
     * is useful on Windows when Apk will lock the underlying file and most test code do not use
     * try-with-resources nor explicitly call close().
     */
    private static final List<Apk> tmpApkFiles = new ArrayList<>();

    static {
        try {
            if (System.getenv("TEST_TMPDIR") != null) {
                BUILD_DIR = new File(System.getenv("TEST_TMPDIR"));
            } else if (BuildSystem.get() == BuildSystem.IDEA) {
                BUILD_DIR = new File(TestUtils.getWorkspaceRoot(), "out/gradle-integration-tests");
            } else {
                throw new IllegalStateException("unable to determine location for BUILD_DIR");
            }

            OUT_DIR = new File(BUILD_DIR, "tests");
            ANDROID_SDK_HOME = new File(BUILD_DIR, "ANDROID_SDK_HOME");

            GRADLE_USER_HOME = getGradleUserHome(BUILD_DIR);

            if (USE_LATEST_NIGHTLY_GRADLE_VERSION) {
                GRADLE_TEST_VERSION =
                        Preconditions.checkNotNull(
                                getLatestGradleCheckedIn(),
                                "Failed to find latest nightly version.");
            } else {
                GRADLE_TEST_VERSION = BasePlugin.GRADLE_MIN_VERSION.toString();
            }

            // These are some properties that we use in the integration test projects, when generating
            // build.gradle files. In case you would like to change any of the parameters, for instance
            // when testing cross product of versions of buildtools, compile sdks, plugin versions,
            // there are corresponding system environment variable that you are able to set.
            String envBuildToolVersion = Strings.emptyToNull(System.getenv("CUSTOM_BUILDTOOLS"));
            DEFAULT_BUILD_TOOL_VERSION =
                    MoreObjects.firstNonNull(
                            envBuildToolVersion,
                            AndroidBuilder.DEFAULT_BUILD_TOOLS_REVISION.toString());

            String envVersion = Strings.emptyToNull(System.getenv().get("CUSTOM_PLUGIN_VERSION"));
            ANDROID_GRADLE_PLUGIN_VERSION =
                    MoreObjects.firstNonNull(envVersion, Version.ANDROID_GRADLE_PLUGIN_VERSION);

            String envCustomCompileSdk =
                    Strings.emptyToNull(System.getenv().get("CUSTOM_COMPILE_SDK"));
            DEFAULT_COMPILE_SDK_VERSION =
                    MoreObjects.firstNonNull(
                            envCustomCompileSdk,
                            Integer.toString(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API));
        } catch (Throwable t) {
            // Print something to stdout, to give us a chance to debug initialization problems.
            System.out.println(Throwables.getStackTraceAsString(t));
            throw Throwables.propagate(t);
        }
    }

    private static final String COMMON_HEADER = "commonHeader.gradle";
    private static final String COMMON_LOCAL_REPO = "commonLocalRepo.gradle";
    private static final String COMMON_BUILD_SCRIPT = "commonBuildScript.gradle";
    private static final String COMMON_VERSIONS = "commonVersions.gradle";
    private static final String DEFAULT_TEST_PROJECT_NAME = "project";

    private final String name;
    private final boolean withDeviceProvider;
    private final boolean withSdk;
    private final boolean withAndroidGradlePlugin;
    private final boolean withKotlinGradlePlugin;
    @NonNull private final List<String> withIncludedBuilds;
    @Nullable private File testDir;
    private File buildFile;
    private File localProp;
    private final boolean withoutNdk;
    private final boolean withDependencyChecker;
    // Indicates if CMake's directory information needs to be saved in local.properties
    private final boolean withCmakeDirInLocalProp;
    // CMake's version to be used
    @NonNull private final String cmakeVersion;

    private final Collection<String> gradleProperties;

    @Nullable private final TestProject testProject;

    private final String targetGradleVersion;

    @NonNull private final String compileSdkVersion;
    @Nullable private final String buildToolsVersion;

    @Nullable private final Path profileDirectory;

    @NonNull private final GradleTestProjectBuilder.MemoryRequirement heapSize;
    @Nullable private final List<Path> repoDirectories;

    @NonNull private final File androidHome;
    @NonNull private final File androidNdkHome;
    @NonNull private final File gradleDistributionDirectory;
    @Nullable private final File gradleBuildCacheDirectory;
    @NonNull private final String kotlinVersion;

    private GradleBuildResult lastBuildResult;
    private ProjectConnection projectConnection;
    private final GradleTestProject rootProject;
    private final List<ProjectConnection> openConnections;

    /** Whether or not to output the log of the last build result when a test fails. */
    private boolean outputLogOnFailure;

    GradleTestProject(
            @Nullable String name,
            @Nullable TestProject testProject,
            @Nullable String targetGradleVersion,
            boolean withoutNdk,
            boolean withDependencyChecker,
            @NonNull Collection<String> gradleProperties,
            @NonNull GradleTestProjectBuilder.MemoryRequirement heapSize,
            @Nullable String compileSdkVersion,
            @Nullable String buildToolsVersion,
            @Nullable Path profileDirectory,
            @NonNull String cmakeVersion,
            boolean withCmake,
            boolean withDeviceProvider,
            boolean withSdk,
            boolean withAndroidGradlePlugin,
            boolean withKotlinGradlePlugin,
            @NonNull List<String> withIncludedBuilds,
            @Nullable File testDir,
            @Nullable List<Path> repoDirectories,
            @NonNull File androidHome,
            @NonNull File androidNdkHome,
            @NonNull File gradleDistributionDirectory,
            @Nullable File gradleBuildCacheDirectory,
            @NonNull String kotlinVersion,
            boolean outputLogOnFailure) {
        this.withDeviceProvider = withDeviceProvider;
        this.withSdk = withSdk;
        this.withAndroidGradlePlugin = withAndroidGradlePlugin;
        this.withKotlinGradlePlugin = withKotlinGradlePlugin;
        this.withIncludedBuilds = withIncludedBuilds;
        this.buildFile = null;
        this.name = (name == null) ? DEFAULT_TEST_PROJECT_NAME : name;
        this.targetGradleVersion = targetGradleVersion;
        this.testProject = testProject;
        this.withoutNdk = withoutNdk;
        this.withDependencyChecker = withDependencyChecker;
        this.heapSize = heapSize;
        this.gradleProperties = gradleProperties;
        this.buildToolsVersion = buildToolsVersion;
        this.compileSdkVersion =
                compileSdkVersion != null ? compileSdkVersion : DEFAULT_COMPILE_SDK_VERSION;
        this.openConnections = Lists.newArrayList();
        this.rootProject = this;
        this.profileDirectory = profileDirectory;
        this.cmakeVersion = cmakeVersion;
        this.withCmakeDirInLocalProp = withCmake;
        this.testDir = testDir;
        this.repoDirectories = repoDirectories;
        this.androidHome = androidHome;
        this.androidNdkHome = androidNdkHome;
        this.gradleDistributionDirectory = gradleDistributionDirectory;
        this.gradleBuildCacheDirectory = gradleBuildCacheDirectory;
        this.kotlinVersion = kotlinVersion;
        this.outputLogOnFailure = outputLogOnFailure;
    }

    /**
     * Create a GradleTestProject representing a subProject of another GradleTestProject.
     *
     * @param subProject name of the subProject, or the subProject's gradle project path
     * @param rootProject root GradleTestProject.
     */
    private GradleTestProject(@NonNull String subProject, @NonNull GradleTestProject rootProject) {
        name = subProject.substring(subProject.lastIndexOf(':') + 1);

        testDir = new File(rootProject.getTestDir(), subProject.replace(":", "/"));
        assertTrue("No subproject dir at " + getTestDir().toString(), getTestDir().isDirectory());

        buildFile = new File(getTestDir(), "build.gradle");
        withoutNdk = rootProject.withoutNdk;
        withDependencyChecker = rootProject.withDependencyChecker;
        gradleProperties = ImmutableList.of();
        testProject = null;
        targetGradleVersion = rootProject.targetGradleVersion;
        openConnections = null;
        this.compileSdkVersion = rootProject.compileSdkVersion;
        this.buildToolsVersion = rootProject.buildToolsVersion;
        this.rootProject = rootProject;
        this.profileDirectory = rootProject.profileDirectory;
        this.cmakeVersion = rootProject.cmakeVersion;
        this.withDeviceProvider = rootProject.withDeviceProvider;
        this.withSdk = rootProject.withSdk;
        this.withAndroidGradlePlugin = rootProject.withAndroidGradlePlugin;
        this.withKotlinGradlePlugin = rootProject.withKotlinGradlePlugin;
        this.withCmakeDirInLocalProp = rootProject.withCmakeDirInLocalProp;
        this.withIncludedBuilds = ImmutableList.of();
        this.repoDirectories = rootProject.repoDirectories;
        this.androidHome = rootProject.androidHome;
        this.androidNdkHome = rootProject.androidNdkHome;
        this.gradleDistributionDirectory = rootProject.gradleDistributionDirectory;
        this.gradleBuildCacheDirectory = rootProject.gradleBuildCacheDirectory;
        this.kotlinVersion = rootProject.kotlinVersion;
        this.outputLogOnFailure = rootProject.outputLogOnFailure;
        this.heapSize = rootProject.heapSize;
    }

    private static Path getGradleUserHome(File buildDir) {
        if (TestUtils.runningFromBazel()) {
            return BazelIntegrationTestsSuite.GRADLE_USER_HOME;
        }
        // Use a temporary directory, so that shards don't share daemons. Gradle builds are not
        // hermetic anyway and Gradle does not clean up test runfiles, so use the same home
        // across invocations to save disk space.
        Path gradle_user_home = buildDir.toPath().resolve("GRADLE_USER_HOME");
        String worker = System.getProperty("org.gradle.test.worker");
        if (worker != null) {
            gradle_user_home = gradle_user_home.resolve(worker);
        }
        return gradle_user_home;
    }

    public static GradleTestProjectBuilder builder() {
        return new GradleTestProjectBuilder();
    }

    @NonNull
    public String getKotlinVersion() {
        return kotlinVersion;
    }

    /** Crawls the tools/external/gradle dir, and gets the latest gradle binary. */
    @Nullable
    public static String getLatestGradleCheckedIn() {
        File gradleDir = TestUtils.getWorkspaceFile("tools/external/gradle");

        // should match gradle-3.4-201612071523+0000-bin.zip, and gradle-3.2-bin.zip
        Pattern gradleVersion = Pattern.compile("^gradle-(\\d+.\\d+)(-.+)?-bin\\.zip$");

        Comparator<Pair<String, String>> revisionsCmp =
                Comparator.nullsFirst(
                        Comparator.comparing(
                                (Pair<String, String> versionTimestamp) ->
                                        GradleVersion.version(versionTimestamp.getFirst()))
                                .thenComparing(Pair::getSecond));

        Pair<String, String> highestRevision = null;
        //noinspection ConstantConditions
        for (File f : gradleDir.listFiles()) {
            Matcher matcher = gradleVersion.matcher(f.getName());
            if (matcher.matches()) {
                Pair<String, String> current =
                        Pair.of(matcher.group(1), Strings.nullToEmpty(matcher.group(2)));

                if (revisionsCmp.compare(highestRevision, current) < 0) {
                    highestRevision = current;
                }
            }
        }

        if (highestRevision == null) {
            return null;
        } else {
            return highestRevision.getFirst() + highestRevision.getSecond();
        }
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        if (rootProject != this) {
            return rootProject.apply(base, description);
        }

        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                if (testDir == null) {
                    testDir =
                            computeTestDir(
                                    description.getTestClass(), description.getMethodName(), name);
                }
                populateTestDirectory();

                boolean testFailed = false;
                try {
                    base.evaluate();
                } catch (Throwable e) {
                    testFailed = true;
                    throw e;
                } finally {
                    for (Apk tmpApkFile : tmpApkFiles) {
                        try {
                            tmpApkFile.close();
                        } catch (Exception e) {
                            System.err.println("Error while closing APK file : " + e.getMessage());
                        }
                        File tmpFile = tmpApkFile.getFile().toFile();
                        if (tmpFile.exists() && !tmpFile.delete()) {
                            System.err.println(
                                    "Cannot delete temporary file " + tmpApkFile.getFile());
                        }
                    }
                    openConnections.forEach(ProjectConnection::close);
                    if (outputLogOnFailure && testFailed && lastBuildResult != null) {
                        System.err.println("==============================================");
                        System.err.println("= Test " + description + " failed. Last build:");
                        System.err.println("==============================================");
                        System.err.println("=================== Stderr ===================");
                        // All output produced during build execution is written to the standard
                        // output file handle since Gradle 4.7. This should be empty.
                        System.err.print(lastBuildResult.getStderr());
                        System.err.println("=================== Stdout ===================");
                        System.err.print(lastBuildResult.getStdout());
                        System.err.println("==============================================");
                        System.err.println("=============== End last build ===============");
                        System.err.println("==============================================");
                    }
                }
            }
        };
    }

    private static File computeTestDir(Class<?> testClass, String methodName, String projectName) {
        // On Windows machines, make sure the test directory's path is short enough to avoid running
        // into path too long exceptions. Typically, on local Windows machines, OUT_DIR's path is
        // long, whereas on Windows build bots, OUT_DIR's path is already short (see
        // https://issuetracker.google.com/69271554). In the first case, let's move the test
        // directory close to root (user home), and in the second case, let's use OUT_DIR directly.
        File testDir =
                SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS
                                && System.getenv("BUILDBOT_BUILDERNAME") == null
                        ? new File(new File(System.getProperty("user.home")), "android-tests")
                        : OUT_DIR;

        String classDir = testClass.getSimpleName();
        String methodDir = null;

        // Create separate directory based on test method name if @Rule is used.
        // getMethodName() is null if this rule is used as a @ClassRule.
        if (methodName != null) {
            methodDir = methodName.replaceAll("[^a-zA-Z0-9_]", "_");
        }

        // In Windows, make sure we do not exceed the limit for test class / name size.
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
            int totalLen = classDir.length();
            if (methodDir != null) {
                totalLen += methodDir.length();
            }

            if (totalLen > MAX_TEST_NAME_DIR_WINDOWS) {
                String hash =
                        Hashing.sha1()
                                .hashString(classDir + methodDir, Charsets.US_ASCII)
                                .toString();
                // take the first 10 characters of the method name, hopefully it will be enough
                // to disambiguate tests, and hash the rest.
                String testIdentifier = methodDir != null ? methodDir : classDir;
                classDir =
                        testIdentifier.substring(0, Math.min(testIdentifier.length(), 10))
                                + hash.substring(
                                        0, Math.min(hash.length(), MAX_TEST_NAME_DIR_WINDOWS - 10));
                methodDir = null;
            }
        }

        testDir = new File(testDir, classDir);
        if (methodDir != null) {
            testDir = new File(testDir, methodDir);
        }

        return new File(testDir, projectName);
    }

    private void populateTestDirectory() throws IOException, StreamException {
        if (testDir == null) {
            throw new IllegalStateException(
                    "populateTestDirectory() called while testDir is null, either set testDir in "
                            + "GradleTestProjectBuilder.withTestDir or call "
                            + "populateTestDirectory(Class<?>, String)");
        }

        buildFile = new File(testDir, "build.gradle");

        FileUtils.deleteRecursivelyIfExists(testDir);
        FileUtils.mkdirs(testDir);

        Files.asCharSink(new File(testDir.getParent(), COMMON_VERSIONS), StandardCharsets.UTF_8)
                .write(generateVersions());
        Files.asCharSink(new File(testDir.getParent(), COMMON_LOCAL_REPO), StandardCharsets.UTF_8)
                .write(generateProjectRepoScript());
        Files.asCharSink(new File(testDir.getParent(), COMMON_HEADER), StandardCharsets.UTF_8)
                .write(generateCommonHeader());
        Files.asCharSink(new File(testDir.getParent(), COMMON_BUILD_SCRIPT), StandardCharsets.UTF_8)
                .write(generateCommonBuildScript());

        if (testProject != null) {
            testProject.write(
                    testDir, testProject.containsFullBuildScript() ? "" : getGradleBuildscript());
        } else {
            Files.asCharSink(buildFile, Charsets.UTF_8).write(getGradleBuildscript());
        }

        createSettingsFile();

        localProp = createLocalProp();
        createGradleProp();
    }

    @NonNull
    public List<Path> getRepoDirectories() {
        if (repoDirectories != null) {
            return repoDirectories;
        } else {
            return getLocalRepositories();
        }
    }

    public Map<BooleanOption, Boolean> getBooleanOptions() {
        ImmutableMap.Builder<BooleanOption, Boolean> builder = ImmutableMap.builder();
        builder.put(
                BooleanOption.DISALLOW_DEPENDENCY_RESOLUTION_AT_CONFIGURATION,
                withDependencyChecker);
        builder.put(BooleanOption.ENABLE_SDK_DOWNLOAD, false); // Not enabled in tests
        return builder.build();
    }

    @NonNull
    public String generateProjectRepoScript() {
        return generateRepoScript(getRepoDirectories());
    }

    @NonNull
    private String generateCommonHeader() {
        String result =
                String.format(
                        "\n"
                                + "ext {\n"
                                + "    buildToolsVersion = '%1$s'\n"
                                + "    latestCompileSdk = %2$s\n"
                                + "    kotlinVersion = '%4$s'\n"
                                + "}\n"
                                + "allprojects {\n"
                                + "    "
                                + generateProjectRepoScript()
                                + "\n"
                                + "}\n"
                                + "",
                        DEFAULT_BUILD_TOOL_VERSION,
                        compileSdkVersion,
                        false,
                        kotlinVersion);

        if (APPLY_DEVICEPOOL_PLUGIN) {
            result +=
                    "\n"
                            + "allprojects { proj ->\n"
                            + "    proj.plugins.withId('com.android.application') {\n"
                            + "        proj.apply plugin: 'devicepool'\n"
                            + "    }\n"
                            + "    proj.plugins.withId('com.android.library') {\n"
                            + "        proj.apply plugin: 'devicepool'\n"
                            + "    }\n"
                            + "    proj.plugins.withId('com.android.model.application') {\n"
                            + "        proj.apply plugin: 'devicepool'\n"
                            + "    }\n"
                            + "    proj.plugins.withId('com.android.model.library') {\n"
                            + "        proj.apply plugin: 'devicepool'\n"
                            + "    }\n"
                            + "}\n";
        }

        return result;
    }

    @NonNull
    private static String generateRepoScript() {
        return generateRepoScript(getLocalRepositories());
    }

    @NonNull
    private static String generateRepoScript(List<Path> repositories) {
        StringBuilder script = new StringBuilder();
        script.append("repositories {\n");
        for (Path repo : repositories) {
            script.append(mavenSnippet(repo));
        }
        script.append("}\n");

        return script.toString();
    }

    @NonNull
    public static String mavenSnippet(@NonNull Path repo) {
        return String.format("maven { url '%s' }\n", repo.toUri().toString());
    }

    @NonNull
    public static List<Path> getLocalRepositories() {
        return BuildSystem.get().getLocalRepositories();
    }

    /**
     * Returns the prebuilts CMake folder for the requested version of CMake. Note: This function
     * returns a path within the Android SDK which is expected to be used in cmake.dir.
     */
    @NonNull
    public static File getCmakeVersionFolder(@NonNull String cmakeVersion) {
        File cmakeVersionFolderInSdk =
                new File(TestUtils.getSdk(), String.format("cmake/%s", cmakeVersion));
        if (!cmakeVersionFolderInSdk.isDirectory()) {
            throw new RuntimeException(
                    String.format("Could not find CMake in %s", cmakeVersionFolderInSdk));
        }

        return cmakeVersionFolderInSdk;
    }


    /**
     * The ninja in 3.6 cmake folder does not support long file paths. This function returns the
     * version that does handle them.
     */
    @NonNull
    public static File getPreferredNinja() {
        File cmakeFolder = getCmakeVersionFolder("3.10.4819442");
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
            return new File(cmakeFolder, "bin/ninja.exe");
        } else {
            return new File(cmakeFolder, "bin/ninja");
        }
    }

    public String generateCommonBuildScript() {
        return BuildSystem.get()
                .getCommonBuildScriptContent(
                        withAndroidGradlePlugin, withKotlinGradlePlugin, withDeviceProvider);
    }

    @NonNull
    private static String generateVersions() {
        return String.format(
                "// Generated by GradleTestProject::generateVersions%n"
                        + "buildVersion = '%s'%n"
                        + "baseVersion = '%s'%n"
                        + "supportLibVersion = '%s'%n"
                        + "testSupportLibVersion = '%s'%n"
                        + "playServicesVersion = '%s'%n"
                        + "supportLibMinSdk = %d%n"
                        + "constraintLayoutVersion = '%s'%n",
                Version.ANDROID_GRADLE_PLUGIN_VERSION,
                Version.ANDROID_TOOLS_BASE_VERSION,
                TestVersions.SUPPORT_LIB_VERSION,
                TestVersions.TEST_SUPPORT_LIB_VERSION,
                TestVersions.PLAY_SERVICES_VERSION,
                TestVersions.SUPPORT_LIB_MIN_SDK,
                SdkConstants.LATEST_CONSTRAINT_LAYOUT_VERSION);
    }

    /**
     * Create a GradleTestProject representing a subproject.
     *
     * @param name name of the subProject, or the subProject's gradle project path
     */
    public GradleTestProject getSubproject(String name) {
        return new GradleTestProject(name, rootProject);
    }

    /** Return the name of the test project. */
    public String getName() {
        return name;
    }

    /** Return the directory containing the test project. */
    @NonNull
    public File getTestDir() {
        Preconditions.checkState(
                testDir != null, "getTestDir called before the project was properly initialized.");
        return testDir;
    }

    /** Return the path to the default Java main source dir. */
    public File getMainSrcDir() {
        return getMainSrcDir("java");
    }

    /** Return the path to the default Java main source dir. */
    public File getMainSrcDir(@NonNull String language) {
        return FileUtils.join(getTestDir(), "src", "main", language);
    }

    /** Return the build.gradle of the test project. */
    public File getSettingsFile() {
        return new File(getTestDir(), "settings.gradle");
    }

    /** Return the gradle.properties file of the test project. */
    @NonNull
    public File getGradlePropertiesFile() {
        return new File(getTestDir(), "gradle.properties");
    }

    /** Return the build.gradle of the test project. */
    public File getBuildFile() {
        return buildFile;
    }

    /** Change the build file used for execute. Should be run after @Before/@BeforeClass. */
    public void setBuildFile(@Nullable String buildFileName) {
        checkNotNull(buildFile, "Cannot call selectBuildFile before test directory is created.");
        if (buildFileName == null) {
            buildFileName = "build.gradle";
        }
        buildFile = new File(getTestDir(), buildFileName);
        assertThat(buildFile).exists();
    }

    public File getBuildDir() {
        return FileUtils.join(getTestDir(), "build");
    }

    /** Return the output directory from Android plugins. */
    public File getOutputDir() {
        return FileUtils.join(getTestDir(), "build", AndroidProject.FD_OUTPUTS);
    }

    /** Return the output directory from Android plugins. */
    public File getIntermediatesDir() {
        return FileUtils.join(getTestDir(), "build", AndroidProject.FD_INTERMEDIATES);
    }

    /** Return a File under the output directory from Android plugins. */
    public File getOutputFile(String... paths) {
        return FileUtils.join(getOutputDir(), paths);
    }

    /** Return a File under the intermediates directory from Android plugins. */
    public File getIntermediateFile(String... paths) {
        return FileUtils.join(getIntermediatesDir(), paths);
    }

    /** Returns a File under the generated folder. */
    public File getGeneratedSourceFile(String... paths) {
        return FileUtils.join(getGeneratedDir(), paths);
    }

    public File getGeneratedDir() {
        return FileUtils.join(getTestDir(), "build", AndroidProject.FD_GENERATED);
    }

    /**
     * Returns the directory in which profiles will be generated. A null value indicates that
     * profiles may not be generated, though setting {@link
     * com.android.build.gradle.options.StringOption#PROFILE_OUTPUT_DIR} in gradle.properties will
     * induce profile generation without affecting this return value
     */
    @Nullable
    public Path getProfileDirectory() {
        if (profileDirectory == null || profileDirectory.isAbsolute()) {
            return profileDirectory;
        } else {
            return rootProject.getTestDir().toPath().resolve(profileDirectory);
        }
    }

    /**
     * Return the output apk File from the application plugin for the given dimension.
     *
     * <p>Expected dimensions orders are: - product flavors - build type - other modifiers (e.g.
     * "unsigned", "aligned")
     *
     * @deprecated Use {@link #getApk(ApkType, String...)} or {@link #getApk(String, ApkType,
     *     String...)}
     */
    @NonNull
    @Deprecated
    public Apk getApk(String... dimensions) throws IOException {
        List<String> dimensionList = Lists.newArrayListWithExpectedSize(1 + dimensions.length);
        dimensionList.add(getName());
        dimensionList.addAll(Arrays.asList(dimensions));
        // FIX ME : "debug" should be an explicit variant name rather than mixed in dimensions.
        List<String> flavorDimensionList =
                Arrays.stream(dimensions)
                        .filter(dimension -> !dimension.equals("unsigned"))
                        .collect(Collectors.toList());
        File apkFile =
                getOutputFile(
                        "apk"
                                + File.separatorChar
                                + Joiner.on(File.separatorChar).join(flavorDimensionList)
                                + File.separatorChar
                                + Joiner.on("-").join(dimensionList)
                                + SdkConstants.DOT_ANDROID_PACKAGE);
        return _getApk(apkFile);
    }

    /**
     * Internal Apk construction facility that will copy the file first on Windows to avoid locking
     * the underlying file.
     *
     * @param apkFile the file handle to create the APK from.
     * @return the Apk object.
     */
    private Apk _getApk(File apkFile) throws IOException {
        Apk apk;
        if (OsType.getHostOs() == OsType.WINDOWS && apkFile.exists()) {
            File copy = File.createTempFile("tmp", ".apk");
            FileUtils.copyFile(apkFile, copy);
            apk = new Apk(copy) {
                @NonNull
                @Override
                public Path getFile() {
                    return apkFile.toPath();
                }
            };
            tmpApkFiles.add(apk);
        } else {
            // the IDE erroneously indicate to use try-with-resources because APK is a autocloseable
            // but nothing is opened here.
            //noinspection resource
            apk = new Apk(apkFile);
        }
        return apk;
    }

    public interface ApkType {
        ApkType DEBUG = of("debug", true);
        ApkType RELEASE = of("release", false);
        ApkType RELEASE_SIGNED = of("release", true);
        ApkType ANDROIDTEST_DEBUG = of("debug", "androidTest", true);

        @NonNull
        String getBuildType();

        @Nullable
        String getTestName();

        boolean isSigned();

        static ApkType of(String name, boolean isSigned) {
            return new ApkType() {

                @NonNull
                @Override
                public String getBuildType() {
                    return name;
                }

                @Override
                public String getTestName() {
                    return null;
                }

                @Override
                public boolean isSigned() {
                    return isSigned;
                }

                @Override
                public String toString() {
                    return MoreObjects.toStringHelper(this)
                            .add("getBuildType", getBuildType())
                            .add("getTestName", getTestName())
                            .add("isSigned", isSigned())
                            .toString();
                }
            };
        }

        static ApkType of(String name, String testName, boolean isSigned) {
            return new ApkType() {

                @NonNull
                @Override
                public String getBuildType() {
                    return name;
                }

                @Nullable
                @Override
                public String getTestName() {
                    return testName;
                }

                @Override
                public boolean isSigned() {
                    return isSigned;
                }

                @Override
                public String toString() {
                    return MoreObjects.toStringHelper(this)
                            .add("getBuildType", getBuildType())
                            .add("getTestName", getTestName())
                            .add("isSigned", isSigned())
                            .toString();
                }
            };
        }


    }

    /**
     * Return the output apk File from the application plugin for the given dimension.
     *
     * <p>Expected dimensions orders are: - product flavors -
     */
    @NonNull
    public Apk getApk(ApkType apk, String... dimensions) throws IOException {
        return getApk(null /* filterName */, apk, dimensions);
    }

    /**
     * Return the bundle universal output apk File from the application plugin for the given
     * dimension.
     *
     * <p>Expected dimensions orders are: - product flavors -
     */
    @NonNull
    public Apk getBundleUniversalApk(@NonNull ApkType apk) throws IOException {
        return getOutputApk("universal_apk", null, apk, ImmutableList.of(), "universal");
    }

    /**
     * Return the output full split apk File from the application plugin for the given dimension.
     *
     * <p>Expected dimensions orders are: - product flavors -
     */
    @NonNull
    public Apk getApk(@Nullable String filterName, ApkType apkType, String... dimensions)
            throws IOException {
        return getOutputApk("apk", filterName, apkType, ImmutableList.copyOf(dimensions), null);
    }

    @NonNull
    private Apk getOutputApk(
            @NonNull String pathPrefix,
            @Nullable String filterName,
            @NonNull ApkType apkType,
            @NonNull ImmutableList<String> dimensions,
            @Nullable String suffix)
            throws IOException {
        return _getApk(
                getOutputFile(
                        pathPrefix
                                + (apkType.getTestName() != null
                                        ? File.separatorChar + apkType.getTestName()
                                        : "")
                                + File.separatorChar
                                + StringHelper.combineAsCamelCase(dimensions)
                                + File.separatorChar
                                + apkType.getBuildType()
                                + File.separatorChar
                                + mangleApkName(apkType, filterName, dimensions, suffix)
                                + (apkType.isSigned()
                                        ? SdkConstants.DOT_ANDROID_PACKAGE
                                        : "-unsigned" + SdkConstants.DOT_ANDROID_PACKAGE)));
    }

    /** Returns the APK given its file name. */
    @NonNull
    public Apk getApkByFileName(@NonNull ApkType apkType, @NonNull String apkFileName)
            throws IOException {
        return _getApk(
                getOutputFile(
                        "apk"
                                + (apkType.getTestName() != null
                                        ? File.separatorChar + apkType.getTestName()
                                        : "")
                                + File.separatorChar
                                + apkType.getBuildType()
                                + File.separatorChar
                                + apkFileName));
    }

    /**
     * Return the output apk File from the feature plugin for the given dimension.
     *
     * <p>Expected dimensions orders are: - product flavors -
     */
    @NonNull
    public Apk getFeatureApk(ApkType apk, String... dimensions) throws IOException {
        return getFeatureApk(null /* filterName */, apk, dimensions);
    }

    /**
     * Return the output full split apk File from the feature plugin for the given dimension.
     *
     * <p>Expected dimensions orders are: - product flavors -
     */
    @NonNull
    public Apk getFeatureApk(@Nullable String filterName, ApkType apkType, String... dimensions)
            throws IOException {
        return _getApk(
                getOutputFile(
                        "apk"
                                + (apkType.getTestName() != null
                                        ? File.separatorChar + apkType.getTestName()
                                        : "")
                                + File.separatorChar
                                + "feature"
                                + File.separatorChar
                                + StringHelper.combineAsCamelCase(ImmutableList.copyOf(dimensions))
                                + File.separatorChar
                                + apkType.getBuildType()
                                + File.separatorChar
                                + mangleApkName(
                                        apkType, filterName, ImmutableList.copyOf(dimensions), null)
                                + (apkType.isSigned()
                                        ? SdkConstants.DOT_ANDROID_PACKAGE
                                        : "-unsigned" + SdkConstants.DOT_ANDROID_PACKAGE)));
    }

    private String mangleApkName(
            @NonNull ApkType apkType,
            @Nullable String filterName,
            List<String> dimensions,
            @Nullable String suffix) {
        List<String> dimensionList = Lists.newArrayListWithExpectedSize(1 + dimensions.size());
        dimensionList.add(getName());
        dimensionList.addAll(dimensions);
        if (!Strings.isNullOrEmpty(filterName)) {
            dimensionList.add(filterName);
        }
        if (!Strings.isNullOrEmpty(apkType.getBuildType())) {
            dimensionList.add(apkType.getBuildType());
        }
        if (!Strings.isNullOrEmpty(apkType.getTestName())) {
            dimensionList.add(apkType.getTestName());
        }
        if (suffix != null) {
            dimensionList.add(suffix);
        }
        return Joiner.on("-").join(dimensionList);
    }

    @NonNull
    public Apk getTestApk() throws IOException {
        return getApk(ApkType.ANDROIDTEST_DEBUG);
    }

    @NonNull
    public Apk getTestApk(String... dimensions) throws IOException {
        return getApk(ApkType.ANDROIDTEST_DEBUG, dimensions);
    }

    /**
     * Return the output aar File from the library plugin for the given dimension.
     *
     * <p>Expected dimensions orders are: - product flavors - build type - other modifiers (e.g.
     * "unsigned", "aligned")
     */
    public Aar getAar(String... dimensions) throws IOException {
        List<String> dimensionList = Lists.newArrayListWithExpectedSize(1 + dimensions.length);
        dimensionList.add(getName());
        dimensionList.addAll(Arrays.asList(dimensions));
        return new Aar(
                getOutputFile("aar", Joiner.on("-").join(dimensionList) + SdkConstants.DOT_AAR));
    }

    /**
     * Returns the output bundle file from the instantapp plugin for the given dimension.
     *
     * <p>Expected dimensions orders are: - product flavors - build type
     */
    public Zip getInstantAppBundle(String... dimensions) throws IOException {
        List<String> dimensionList = Lists.newArrayListWithExpectedSize(1 + dimensions.length);
        dimensionList.add(getName());
        dimensionList.addAll(Arrays.asList(dimensions));
        return new Zip(
                getOutputFile(
                        "apk",
                        StringHelper.combineAsCamelCase(ImmutableList.copyOf(dimensions)),
                        Joiner.on("-").join(dimensionList) + SdkConstants.DOT_ZIP));
    }

    /** Returns a string that contains the gradle buildscript content */
    public static String getGradleBuildscript() {
        return "apply from: \"../commonHeader.gradle\"\n"
                + "buildscript { apply from: \"../commonBuildScript.gradle\" }\n"
                + "\n"
                + "apply from: \"../commonLocalRepo.gradle\"\n";
    }

    /** Fluent method to run a build. */
    public GradleTaskExecutor executor() {
        return applyOptions(new GradleTaskExecutor(this, getProjectConnection()));
    }

    /** Fluent method to get the model. */
    @NonNull
    public ModelBuilder model() {
        return applyOptions(new ModelBuilder(this, getProjectConnection()));
    }

    private <T extends BaseGradleExecutor<T>> T applyOptions(T executor) {
        Map<BooleanOption, Boolean> booleanOptions = getBooleanOptions();
        booleanOptions.forEach(executor::with);
        booleanOptions.keySet().forEach(executor::suppressOptionWarning);
        return executor;
    }

    /**
     * Runs gradle on the project. Throws exception on failure.
     *
     * @param tasks Variadic list of tasks to execute.
     */
    public void execute(@NonNull String... tasks) throws IOException, InterruptedException {
        lastBuildResult = executor().run(tasks);
    }

    public void execute(@NonNull List<String> arguments, @NonNull String... tasks)
            throws IOException, InterruptedException {
        lastBuildResult = executor().withArguments(arguments).run(tasks);
    }

    public GradleConnectionException executeExpectingFailure(@NonNull String... tasks)
            throws IOException, InterruptedException {
        lastBuildResult = executor().expectFailure().run(tasks);
        return lastBuildResult.getException();
    }

    public void executeConnectedCheck() throws IOException, InterruptedException {
        lastBuildResult = executor().executeConnectedCheck();
    }

    public void executeConnectedCheck(@NonNull List<String> arguments)
            throws IOException, InterruptedException {
        lastBuildResult = executor().withArguments(arguments).executeConnectedCheck();
    }

    /**
     * Runs gradle on the project, and returns the project model. Throws exception on failure.
     *
     * @param tasks Variadic list of tasks to execute.
     * @return the AndroidProject model for the project.
     */
    @NonNull
    public ModelContainer<AndroidProject> executeAndReturnModel(@NonNull String... tasks)
            throws IOException, InterruptedException {
        lastBuildResult = executor().run(tasks);
        return model().fetchAndroidProjects();
    }

    /**
     * Runs gradle on the project, and returns the model of the specified type. Throws exception on
     * failure.
     *
     * @param modelClass Class of the model to return
     * @param tasks Variadic list of tasks to execute.
     * @return the model for the project with the specified type.
     */
    @NonNull
    public <T> T executeAndReturnModel(Class<T> modelClass, String... tasks)
            throws IOException, InterruptedException {
        lastBuildResult = executor().run(tasks);
        return model().fetch(modelClass);
    }

    /**
     * Runs gradle on the project, and returns the (minimal) output model. Throws exception on
     * failure.
     *
     * @param tasks Variadic list of tasks to execute.
     * @return the output model for the project
     */
    @NonNull
    public ProjectBuildOutput executeAndReturnOutputModel(String... tasks)
            throws IOException, InterruptedException {
        ModelContainer<ProjectBuildOutput> buildOutputContainer =
                executor().withOutputModelQuery().run(tasks).getBuildOutputContainer();
        Preconditions.checkNotNull(
                buildOutputContainer, "Build output model not found after build.");
        return buildOutputContainer.getOnlyModel();
    }

    /**
     * Runs gradle on the project, and returns a project model for each sub-project. Throws
     * exception on failure.
     *
     * @param tasks Variadic list of tasks to execute.
     * @return the AndroidProject model for the project.
     */
    @NonNull
    public ModelContainer<AndroidProject> executeAndReturnMultiModel(String... tasks)
            throws IOException, InterruptedException {
        lastBuildResult = executor().run(tasks);
        return model().fetchAndroidProjects();
    }

    /**
     * Runs gradle on the project, and returns the model of the specified type for each sub-project.
     * Throws exception on failure.
     *
     * @param modelClass Class of the model to return
     * @param tasks Variadic list of tasks to execute.
     * @return map of project names to output models
     */
    @NonNull
    public <T> Map<String, T> executeAndReturnMultiModel(Class<T> modelClass, String... tasks)
            throws IOException, InterruptedException {
        lastBuildResult = executor().run(tasks);
        return model().fetchMulti(modelClass);
    }

    /**
     * Runs gradle on the project, and returns the output model of the specified type for each
     * sub-project. Throws exception on failure.
     *
     * @param tasks Variadic list of tasks to execute.
     * @return map of project names to output models
     */
    @NonNull
    public Map<String, ProjectBuildOutput> executeAndReturnOutputMultiModel(String... tasks)
            throws IOException, InterruptedException {
        ModelContainer<ProjectBuildOutput> buildOutputContainer =
                executor().withOutputModelQuery().run(tasks).getBuildOutputContainer();
        Preconditions.checkNotNull(
                buildOutputContainer, "Build output model not found after build.");
        return buildOutputContainer.getOnlyModelMap();
    }

    /** Returns the latest build result. */
    public GradleBuildResult getBuildResult() {
        return lastBuildResult;
    }

    public void setLastBuildResult(GradleBuildResult lastBuildResult) {
        this.lastBuildResult = lastBuildResult;
    }

    /**
     * Create a File object. getTestDir will be the base directory if a relative path is supplied.
     *
     * @param path Full path of the file. May be a relative path.
     */
    public File file(String path) {
        File result = new File(FileUtils.toSystemDependentPath(path));
        if (result.isAbsolute()) {
            return result;
        } else {
            return new File(getTestDir(), path);
        }
    }

    /** Returns a Gradle project Connection */
    @NonNull
    private ProjectConnection getProjectConnection() {
        if (projectConnection != null) {
            return projectConnection;
        }
        GradleConnector connector = GradleConnector.newConnector();

        // Limit daemon idle time for tests. 10 seconds is enough for another test
        // to start and reuse the daemon.
        ((DefaultGradleConnector) connector).daemonMaxIdleTime(10, TimeUnit.SECONDS);

        String distributionName = String.format("gradle-%s-bin.zip", targetGradleVersion);
        File distributionZip = new File(gradleDistributionDirectory, distributionName);
        assertThat(distributionZip).isFile();

        projectConnection =
                connector
                        .useDistribution(distributionZip.toURI())
                        .useGradleUserHomeDir(GRADLE_USER_HOME.toFile())
                        .forProjectDirectory(getTestDir())
                        .connect();

        rootProject.openConnections.add(projectConnection);

        return projectConnection;
    }

    private File createLocalProp() throws IOException, StreamException {
        checkNotNull(testDir, "project location is null");

        File mainLocalProp = createLocalProp(testDir);

        for (String includedBuild : withIncludedBuilds) {
            createLocalProp(new File(testDir, includedBuild));
        }

        return mainLocalProp;
    }

    private File createLocalProp(File destDir) throws IOException, StreamException {
        ProjectPropertiesWorkingCopy localProp =
                ProjectProperties.create(
                        destDir.getAbsolutePath(), ProjectProperties.PropertyType.LOCAL);

        if (withSdk) {
            localProp.setProperty(
                    ProjectProperties.PROPERTY_SDK,
                    Objects.requireNonNull(getAndroidHome()).getAbsolutePath());
        }
        if (!withoutNdk) {
            localProp.setProperty(
                    ProjectProperties.PROPERTY_NDK, getAndroidNdkHome().getAbsolutePath());
        }

        if (withCmakeDirInLocalProp && cmakeVersion != null && !cmakeVersion.isEmpty()) {
            localProp.setProperty(
                    ProjectProperties.PROPERTY_CMAKE,
                    getCmakeVersionFolder(cmakeVersion).getAbsolutePath());
        }

        localProp.save();
        return (File) localProp.getFile();
    }

    private enum BuildSystem {
        GRADLE {
            @NonNull
            @Override
            List<Path> getLocalRepositories() {
                String customRepo = System.getenv(ENV_CUSTOM_REPO);
                // TODO: support USE_EXTERNAL_REPO
                ImmutableList.Builder<Path> repos = ImmutableList.builder();
                for (String path : Splitter.on(File.pathSeparatorChar).split(customRepo)) {
                    repos.add(Paths.get(path));
                }
                return repos.build();
            }
        },
        BAZEL {
            @NonNull
            @Override
            List<Path> getLocalRepositories() {
                return BazelIntegrationTestsSuite.MAVEN_REPOS;
            }
        },
        IDEA {
            @NonNull
            @Override
            List<Path> getLocalRepositories() {
                return ImmutableList.of(
                        TestUtils.getWorkspaceFile("prebuilts/tools/common/m2/repository")
                                .toPath());
                // The classes build by idea and jars are added separately.
            }

            @Override
            String getCommonBuildScriptContent(
                    boolean withAndroidGradlePlugin,
                    boolean withKotlinGradlePlugin,
                    boolean withDeviceProvider) {
                StringBuilder buildScript =
                        new StringBuilder(
                                "\n"
                                        + "def commonScriptFolder = buildscript.sourceFile.parent\n"
                                        + "apply from: \"$commonScriptFolder/commonVersions.gradle\", to: rootProject.ext\n"
                                        + "\n"
                                        + "project.buildscript { buildscript ->\n"
                                        + "    apply from: \"$commonScriptFolder/commonLocalRepo.gradle\", to:buildscript\n"
                                        + "    dependencies {"
                                        + "        classpath files(");

                for (URL url :
                        ((URLClassLoader) GradleTestProject.class.getClassLoader()).getURLs()) {
                    buildScript.append("                '").append(url.getFile()).append("',\n");
                }
                buildScript.append("        )\n" + "    }\n" + "}");
                return buildScript.toString();
            }
        },
        ;

        static BuildSystem get() {
            if (TestUtils.runningFromBazel()) {
                return BAZEL;
            } else if (System.getenv(ENV_CUSTOM_REPO) != null) {
                return GRADLE;
            } else {
                return IDEA;
            }
        }

        @NonNull
        abstract List<Path> getLocalRepositories();

        String getCommonBuildScriptContent(
                boolean withAndroidGradlePlugin,
                boolean withKotlinGradlePlugin,
                boolean withDeviceProvider) {
            StringBuilder script = new StringBuilder();
            script.append("def commonScriptFolder = buildscript.sourceFile.parent\n");
            script.append(
                    "apply from: \"$commonScriptFolder/commonVersions.gradle\", to: rootProject.ext\n\n");
            script.append("project.buildscript { buildscript ->\n");
            script.append(
                    "    apply from: \"$commonScriptFolder/commonLocalRepo.gradle\", to:buildscript\n");
            if (withKotlinGradlePlugin) {
                // To get the Kotlin version
                script.append("    apply from: '../commonHeader.gradle'\n");
            }

            script.append("    dependencies {\n");
            if (withAndroidGradlePlugin) {
                script.append(
                        "        classpath \"com.android.tools.build:gradle:$rootProject.buildVersion\"\n");
            }
            if (withKotlinGradlePlugin) {
                script.append(
                        "        classpath \"org.jetbrains.kotlin:kotlin-gradle-plugin:$rootProject.kotlinVersion\"\n");
            }
            if (withDeviceProvider) {
                script.append(
                        "        classpath 'com.android.tools.internal.build.test:devicepool:0.1'\n");
            }
            script.append("    }\n");

            script.append("}");
            return script.toString();
        }
    }

    private void createSettingsFile() throws IOException {
        if (gradleBuildCacheDirectory != null) {
            File absoluteFile =
                    gradleBuildCacheDirectory.isAbsolute()
                            ? gradleBuildCacheDirectory
                            : new File(testDir, gradleBuildCacheDirectory.getPath());
            TestFileUtils.appendToFile(
                    getSettingsFile(),
                    "buildCache {\n"
                            + "    local(DirectoryBuildCache) {\n"
                            + "        directory = \""
                            + absoluteFile.getPath().replace("\\", "\\\\")
                            + "\"\n"
                            + "    }\n"
                            + "}");
        }
    }

    private void createGradleProp() throws IOException {
        if (gradleProperties.isEmpty()) {
            return;
        }
        Files.asCharSink(getGradlePropertiesFile(), Charset.defaultCharset())
                .write(Joiner.on(System.lineSeparator()).join(gradleProperties));
    }

    @NonNull
    GradleTestProjectBuilder.MemoryRequirement getHeapSize() {
        return heapSize;
    }

    public File getLocalProp() {
        return localProp;
    }

    @Nullable
    public String getBuildToolsVersion() {
        return buildToolsVersion;
    }

    @NonNull
    public static String getCompileSdkHash() {
        String compileTarget =
                GradleTestProject.DEFAULT_COMPILE_SDK_VERSION.replaceAll("[\"']", "");
        if (!compileTarget.startsWith("android-")) {
            compileTarget = "android-" + compileTarget;
        }
        return compileTarget;
    }

    @NonNull
    public File getAndroidHome() {
        return androidHome;
    }

    @NonNull
    public File getAndroidNdkHome() {
        return androidNdkHome;
    }
}
