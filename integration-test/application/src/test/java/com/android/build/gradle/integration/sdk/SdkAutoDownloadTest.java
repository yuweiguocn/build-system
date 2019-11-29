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

package com.android.build.gradle.integration.sdk;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelBuilder;
import com.android.build.gradle.integration.common.fixture.TestVersions;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.IntegerOption;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.MavenRepositories;
import com.android.ide.common.repository.SdkMavenRepository;
import com.android.repository.Revision;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

/** Tests for automatic SDK download from Gradle. */
public class SdkAutoDownloadTest {

    private static String cmakeLists = "cmake_minimum_required(VERSION 3.4.1)"
            + System.lineSeparator()
            + "file(GLOB SRC src/main/cpp/hello-jni.cpp)"
            + System.lineSeparator()
            + "set(CMAKE_VERBOSE_MAKEFILE ON)"
            + System.lineSeparator()
            + "add_library(hello-jni SHARED ${SRC})"
            + System.lineSeparator()
            + "target_link_libraries(hello-jni log)";

    private static final String BUILD_TOOLS_VERSION = SdkConstants.CURRENT_BUILD_TOOLS_VERSION;
    private static final String PLATFORM_VERSION =
            TestUtils.getLatestAndroidPlatform().replace("android-", "");

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(
                            HelloWorldJniApp.builder()
                                    .withNativeDir("cpp")
                                    .useCppSource(true)
                                    .build())
                    .addGradleProperties(IntegerOption.ANDROID_SDK_CHANNEL.getPropertyName() + "=3")
                    .withoutNdk()
                    .create();

    private File mSdkHome;
    private File licenseFile;
    private File previewLicenseFile;

    @Before
    public void setUp() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator() + "apply plugin: 'com.android.application'");

        mSdkHome = project.file("local-sdk-for-test");
        FileUtils.mkdirs(mSdkHome);

        File licensesFolder = new File(mSdkHome, "licenses");
        FileUtils.mkdirs(licensesFolder);
        licenseFile = new File(licensesFolder, "android-sdk-license");
        previewLicenseFile = new File(licensesFolder, "android-sdk-preview-license");

        // noinspection SpellCheckingInspection SHAs.
        String licensesHash =
                String.format(
                        "e6b7c2ab7fa2298c15165e9583d0acf0b04a2232%n"
                                + "8933bad161af4178b1185d1a37fbf41ea5269c55%n"
                                + "d56f5187479451eabf01fb78af6dfcb131a6481e%n");

        String previewLicenseHash =
                String.format(
                        "84831b9409646a918e30573bab4c9c91346d8abd%n"
                                + "79120722343a6f314e0719f863036c702b0e6b2a%n");

        Files.write(licenseFile.toPath(), licensesHash.getBytes(StandardCharsets.UTF_8));
        Files.write(
                previewLicenseFile.toPath(), previewLicenseHash.getBytes(StandardCharsets.UTF_8));
        TestFileUtils.appendToFile(
                project.getLocalProp(),
                System.lineSeparator()
                        + SdkConstants.SDK_DIR_PROPERTY
                        + " = "
                        + mSdkHome.getAbsolutePath().replace("\\", "\\\\"));


        TestFileUtils.appendToFile(
                project.getBuildFile(), "android.defaultConfig.minSdkVersion = 19");
    }

    private void installPlatforms() throws IOException {
        FileUtils.copyDirectoryToDirectory(
                TestUtils.getSdk()
                        .toPath()
                        .resolve(SdkConstants.FD_PLATFORMS)
                        .resolve("android-" + PLATFORM_VERSION)
                        .toFile(),
                FileUtils.join(mSdkHome, SdkConstants.FD_PLATFORMS));
    }

    private void installBuildTools() throws IOException {
        FileUtils.copyDirectoryToDirectory(
                TestUtils.getSdk()
                        .toPath()
                        .resolve(SdkConstants.FD_BUILD_TOOLS)
                        .resolve(BUILD_TOOLS_VERSION)
                        .toFile(),
                FileUtils.join(mSdkHome, SdkConstants.FD_BUILD_TOOLS));
    }

    private void installPlatformTools() throws IOException {
        FileUtils.copyDirectoryToDirectory(
                FileUtils.join(
                        TestUtils.getSdk().toPath().toFile(), SdkConstants.FD_PLATFORM_TOOLS),
                FileUtils.join(mSdkHome));
    }

    private void installNdk() throws IOException {
        FileUtils.copyDirectoryToDirectory(
                FileUtils.join(TestUtils.getSdk().toPath().toFile(), SdkConstants.FD_NDK),
                FileUtils.join(mSdkHome));
    }

    /** Tests that the compile SDK target and build tools are automatically downloaded. */
    @Test
    public void sanityTest() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\""
                        + System.lineSeparator()
                        // Tests that calling getBootClasspath() doesn't break auto-download.
                        + "println(android.bootClasspath)");

        File platformTarget = getPlatformFolder();
        File buildTools =
                FileUtils.join(mSdkHome, SdkConstants.FD_BUILD_TOOLS, BUILD_TOOLS_VERSION);

        // Not installed.
        assertThat(buildTools).doesNotExist();
        assertThat(platformTarget).doesNotExist();

        // ---------- Build ----------
        getExecutor().run("assembleDebug");

        // Installed platform
        assertThat(platformTarget).isDirectory();
        File androidJarFile = FileUtils.join(getPlatformFolder(), "android.jar");
        assertThat(androidJarFile).exists();

        // Installed build tools.
        assertThat(buildTools).isDirectory();
        File dxFile =
                FileUtils.join(
                        mSdkHome,
                        SdkConstants.FD_BUILD_TOOLS,
                        BUILD_TOOLS_VERSION,
                        SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS
                                ? "dx.bat"
                                : "dx");
        assertThat(dxFile).exists();
    }

    /**
     * Tests that the compile SDK target was automatically downloaded in the case that the target
     * was an addon target. It also checks that the platform that the addon is dependent on was
     * downloaded.
     */
    @Test
    @Ignore("b/65237460")
    public void checkCompileSdkAddonDownloading() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion \"Google Inc.:Google APIs:24\""
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        getExecutor().run("assembleDebug");

        File platformBase = getPlatformFolder();
        assertThat(platformBase).isDirectory();

        File addonTarget =
                FileUtils.join(mSdkHome, SdkConstants.FD_ADDONS, "addon-google_apis-google-24");
        assertThat(addonTarget).isDirectory();
    }

    /** Tests that we don't crash when a codename is used for the compile SDK level. */
    @Test
    public void checkCompileSdkCodename() throws Exception {
        installBuildTools();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion 'MadeUp'"
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");
        assertThat(result.getFailureMessage())
                .contains("Failed to find target with hash string 'MadeUp'");
    }

    /**
     * Tests that missing platform tools don't break the build, and that the platform tools were
     * automatically downloaded, when they weren't already installed.
     */
    @Test
    public void checkPlatformToolsDownloading() throws Exception {
        installPlatforms();
        installBuildTools();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        File platformTools = FileUtils.join(mSdkHome, SdkConstants.FD_PLATFORM_TOOLS);

        getOfflineExecutor().run("assembleDebug");
        assertThat(platformTools).doesNotExist();

        getExecutor().run("assembleDebug");
        assertThat(platformTools).isDirectory();
    }

    @Test
    public void checkCmakeDownloading() throws Exception {
        installPlatforms();
        installBuildTools();
        installNdk();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\""
                        + System.lineSeparator()
                        + "android.externalNativeBuild.cmake.path \"CMakeLists.txt\"");

        Files.write(project.file("CMakeLists.txt").toPath(),
                cmakeLists.getBytes(StandardCharsets.UTF_8));

        // TODO: This should be changed to assembleDebug once b/116539441 is fixed.
        // Currently assembleDebug causes ninja to its path component limit.
        // See https://github.com/ninja-build/ninja/issues/1161
        getExecutor().run("clean");

        File cmakeDirectory = FileUtils.join(mSdkHome, SdkConstants.FD_CMAKE);
        assertThat(cmakeDirectory).isDirectory();
        File ndkDirectory = FileUtils.join(mSdkHome, SdkConstants.FD_NDK);
        assertThat(ndkDirectory).isDirectory();
    }

    @Test
    public void checkCmakeMissingLicense() throws Exception {
        installPlatforms();
        installBuildTools();
        installPlatformTools();
        installNdk();
        FileUtils.delete(previewLicenseFile);
        deleteLicense();

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\""
                        + System.lineSeparator()
                        + "android.externalNativeBuild.cmake.path \"CMakeLists.txt\"");

        Files.write(project.file("CMakeLists.txt").toPath(),
                cmakeLists.getBytes(StandardCharsets.UTF_8));

        GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");

        assertThat(result.getFailureMessage())
                .contains(
                        "Failed to install the following Android SDK packages as some licences have not been accepted");
        assertThat(result.getStdout()).contains("CMake");
    }

    @Test
    public void checkNdkDownloading() throws Exception {
        installPlatforms();
        installBuildTools();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\""
                        + System.lineSeparator()
                        + "android.externalNativeBuild.cmake.path \"CMakeLists.txt\"");

        Files.write(
                project.file("CMakeLists.txt").toPath(),
                cmakeLists.getBytes(StandardCharsets.UTF_8));

        // TODO: This should be changed to assembleDebug once b/116539441 is fixed.
        // Currently assembleDebug causes ninja to its path component limit.
        // See https://github.com/ninja-build/ninja/issues/1161
        getExecutor().run("clean");

        File ndkDirectory = FileUtils.join(mSdkHome, SdkConstants.FD_NDK);
        assertThat(ndkDirectory).isDirectory();
    }

    @Test
    public void checkNdkMissingLicense() throws Exception {
        installPlatforms();
        installBuildTools();
        installPlatformTools();
        FileUtils.delete(previewLicenseFile);
        deleteLicense();

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\""
                        + System.lineSeparator()
                        + "android.externalNativeBuild.cmake.path \"CMakeLists.txt\"");

        Files.write(
                project.file("CMakeLists.txt").toPath(),
                cmakeLists.getBytes(StandardCharsets.UTF_8));

        GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");

        assertThat(result.getFailureMessage())
                .contains(
                        "Failed to install the following Android SDK packages as some licences have not been accepted");
        assertThat(result.getFailureMessage()).contains("ndk-bundle");
    }

    @Test
    @Ignore("https://issuetracker.google.com/issues/65237460")
    public void checkDependencies_androidRepository() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\""
                        + System.lineSeparator()
                        + "dependencies { compile 'com.android.support:support-v4:"
                        + TestVersions.SUPPORT_LIB_VERSION
                        + "' }");

        getExecutor().run("assembleDebug");

        checkForLibrary(SdkMavenRepository.ANDROID, "com.android.support", "support-v4", "23.0.0");

        // Check that the Google repo is not automatically installed if an Android library is
        // missing.
        assertThat(SdkMavenRepository.GOOGLE.isInstalled(mSdkHome, FileOpUtils.create())).isFalse();
    }

    @Test
    @Ignore("https://issuetracker.google.com/issues/65237460")
    public void checkDependencies_googleRepository() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\""
                        + System.lineSeparator()
                        + "android.defaultConfig.multiDexEnabled true"
                        + System.lineSeparator()
                        + "dependencies { compile 'com.google.android.gms:play-services:"
                        + TestVersions.PLAY_SERVICES_VERSION
                        + "' }");

        getExecutor().run("assembleDebug");

        checkForLibrary(
                SdkMavenRepository.GOOGLE,
                "com.google.android.gms",
                "play-services",
                TestVersions.PLAY_SERVICES_VERSION);
    }

    @Test
    @Ignore("https://issuetracker.google.com/issues/65237460")
    public void checkDependencies_individualRepository() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\""
                        + System.lineSeparator()
                        + "dependencies { compile 'com.android.support.constraint:constraint-layout-solver:1.0.0-alpha4' }");

        getExecutor().run("assembleDebug");

        checkForLibrary(
                SdkMavenRepository.ANDROID,
                "com.android.support.constraint",
                "constraint-layout-solver",
                "1.0.0-alpha4");

        assertThat(SdkMavenRepository.GOOGLE.isInstalled(mSdkHome, FileOpUtils.create())).isFalse();
        assertThat(SdkMavenRepository.ANDROID.isInstalled(mSdkHome, FileOpUtils.create()))
                .isFalse();
    }

    @NonNull
    private GradleTaskExecutor getExecutor() {
        return getOfflineExecutor().withoutOfflineFlag();
    }

    private GradleTaskExecutor getOfflineExecutor() {
        return project.executor()
                .withSdkAutoDownload()
                .withArgument(
                        String.format(
                                "-D%1$s=file:///%2$s/",
                                AndroidSdkHandler.SDK_TEST_BASE_URL_PROPERTY,
                                TestUtils.getRemoteSdk()));
    }

    @NonNull
    private ModelBuilder getModel() {
        return project.model()
                .withSdkAutoDownload()
                .withArgument(
                        String.format(
                                "-D%1$s=file:///%2$s/",
                                AndroidSdkHandler.SDK_TEST_BASE_URL_PROPERTY,
                                TestUtils.getRemoteSdk()))
                .withoutOfflineFlag();
    }

    private void checkForLibrary(
            @NonNull SdkMavenRepository oldRepository,
            @NonNull String groupId,
            @NonNull String artifactId,
            @NonNull String version) {
        FileOp fileOp = FileOpUtils.create();
        GradleCoordinate coordinate =
                new GradleCoordinate(
                        groupId, artifactId, new GradleCoordinate.StringComponent(version));

        // Try the new repository first.
        File repositoryLocation =
                FileUtils.join(mSdkHome, SdkConstants.FD_EXTRAS, SdkConstants.FD_M2_REPOSITORY);

        File artifactDirectory =
                MavenRepositories.getArtifactDirectory(repositoryLocation, coordinate);

        if (!artifactDirectory.exists()) {
            // Try the old repository it's supposed to be in.
            repositoryLocation = oldRepository.getRepositoryLocation(mSdkHome, true, fileOp);
            assertNotNull(repositoryLocation);
            artifactDirectory =
                    MavenRepositories.getArtifactDirectory(repositoryLocation, coordinate);
            assertThat(artifactDirectory).exists();
        }
    }

    @Test
    public void checkDependencies_invalidDependency() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\""
                        + System.lineSeparator()
                        + "dependencies { compile 'foo:bar:baz' }");

        GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");
        assertNotNull(result.getException());

        // Make sure the standard gradle error message is what the user sees.
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .startsWith("Could not find foo:bar:baz.");
    }

    @Test
    public void checkNoLicenseError_PlatformTarget() throws Exception {
        deleteLicense();

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");
        assertNotNull(result.getException());

        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("Android SDK Platform " + PLATFORM_VERSION);
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("missing components");
    }

    private void deleteLicense() throws Exception {
        FileUtils.delete(licenseFile);
    }

    @Test
    @Ignore("b/65237460")
    public void checkNoLicenseError_AddonTarget() throws Exception {
        deleteLicense();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion \"Google Inc.:Google APIs:24\""
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");
        assertNotNull(result.getException());

        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("Google APIs");
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("missing components");
    }

    @Test
    public void checkNoLicenseError_BuildTools() throws Exception {
        deleteLicense();

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");
        assertNotNull(result.getException());

        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains(
                        "Build-Tools "
                                + Revision.parseRevision(BUILD_TOOLS_VERSION).toShortString());
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("missing components");

        // Check that
        AndroidProject model = getModel().ignoreSyncIssues().fetchAndroidProjects().getOnlyModel();
        List<SyncIssue> syncErrors =
                model.getSyncIssues()
                        .stream()
                        .filter(issue -> issue.getSeverity() == SyncIssue.SEVERITY_ERROR)
                        .collect(Collectors.toList());
        assertThat(syncErrors).hasSize(1);

        assertThat(syncErrors.get(0)).hasType(SyncIssue.TYPE_MISSING_SDK_PACKAGE);
        String data = syncErrors.get(0).getData();
        assertNotNull(data);
        assertThat(Splitter.on(' ').split(data))
                .containsExactly(
                        "platforms;android-" + PLATFORM_VERSION,
                        "build-tools;" + BUILD_TOOLS_VERSION);
    }

    @Test
    @Ignore("b/65237460")
    public void checkNoLicenseError_MultiplePackages() throws Exception {
        deleteLicense();

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion \"Google Inc.:Google APIs:24\""
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");
        assertNotNull(result.getException());

        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("missing components");
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("Build-Tools " + AndroidBuilder.MIN_BUILD_TOOLS_REV.toShortString());
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("Android SDK Platform 23");
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("Google APIs");
    }

    @Test
    public void checkPermissions_BuildTools() throws Exception {
        Assume.assumeFalse(SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS);

        // Change the permissions.
        Path sdkHomePath = mSdkHome.toPath();
        Set<PosixFilePermission> readOnlyDir =
                ImmutableSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE);

        Files.walk(sdkHomePath).forEach(path -> {
            try {
                Files.setPosixFilePermissions(path, readOnlyDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        try {
            // Request a new version of build tools.
            TestFileUtils.appendToFile(
                    project.getBuildFile(),
                    System.lineSeparator()
                            + "android.compileSdkVersion "
                            + PLATFORM_VERSION
                            + System.lineSeparator()
                            + "android.buildToolsVersion \""
                            + BUILD_TOOLS_VERSION
                            + "\"");

            GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");
            assertNotNull(result.getException());

            assertThat(Throwables.getRootCause(result.getException()).getMessage())
                    .contains(
                            "Build-Tools "
                                    + Revision.parseRevision(BUILD_TOOLS_VERSION).toShortString());
            assertThat(Throwables.getRootCause(result.getException()).getMessage())
                    .contains("not writable");
        } finally {
            Set<PosixFilePermission> readWriteDir =
                    ImmutableSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE);

            //noinspection ThrowFromFinallyBlock
            Files.walk(sdkHomePath).forEach(path -> {
                try {
                    Files.setPosixFilePermissions(path, readWriteDir);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private File getPlatformFolder() {
        return FileUtils.join(mSdkHome, SdkConstants.FD_PLATFORMS, "android-" + PLATFORM_VERSION);
    }

}
